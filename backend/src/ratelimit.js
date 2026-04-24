// KV-backed tiered rate limiter. Per-IP for public routes, per-user for authed routes.
// Cloudflare KV is eventually consistent; for higher-accuracy use a Durable Object.
// This implementation is "good enough" for abuse prevention at small scale.

const TIERS = {
  auth:      { limit: 10,  windowSec: 60 },   // /auth/*
  scan:      { limit: 10,  windowSec: 60 },   // expensive: Anthropic vision
  scanDay:   { limit: 30,  windowSec: 86400 }, // per-user daily scan quota (free tier)
  scanDayPro:{ limit: 200, windowSec: 86400 },
  billing:   { limit: 10,  windowSec: 60 },
  rtdn:      { limit: 120, windowSec: 60 },   // Pub/Sub can burst
  write:     { limit: 30,  windowSec: 60 },
  read:      { limit: 120, windowSec: 60 },
  seed:      { limit: 5000, windowSec: 3600 },  // bulk ingest — SEED_KEY header is the real gate
  report:    { limit: 10,  windowSec: 3600 },
  follow:    { limit: 60,  windowSec: 3600 },
  beta_feedback: { limit: 20,  windowSec: 3600 },
  events:        { limit: 240, windowSec: 60 },
  mealprep:      { limit: 10,  windowSec: 3600 },
};

export function clientIp(request) {
  return request.headers.get('cf-connecting-ip')
    || request.headers.get('x-forwarded-for')?.split(',')[0]?.trim()
    || 'unknown';
}

/**
 * Enforce a rate limit. Returns null when OK, or a 429 Response when exceeded.
 */
export async function enforce(env, tier, identity) {
  if (!env.RATE_LIMIT_KV) return null;
  const cfg = TIERS[tier];
  if (!cfg) return null;
  try {
    const bucket = Math.floor(Date.now() / 1000 / cfg.windowSec);
    const key = `rl:${tier}:${identity}:${bucket}`;
    const current = parseInt((await env.RATE_LIMIT_KV.get(key)) || '0', 10);
    if (current >= cfg.limit) {
      return new Response(JSON.stringify({ ok: false, error: 'rate limited', retryAfter: cfg.windowSec }), {
        status: 429,
        headers: { 'Content-Type': 'application/json', 'Retry-After': String(cfg.windowSec) },
      });
    }
    // TTL = windowSec + small buffer to cover clock skew
    await env.RATE_LIMIT_KV.put(key, String(current + 1), { expirationTtl: cfg.windowSec + 60 });
    return null;
  } catch (e) {
    // KV quota exhausted or transient failure — fail open so app stays usable.
    // Log once for observability; never cascade to a 500.
    console.warn('ratelimit kv failed (fail-open):', tier, e?.message);
    return null;
  }
}

export async function peek(env, tier, identity) {
  if (!env.RATE_LIMIT_KV) return { count: 0, limit: TIERS[tier]?.limit ?? 0 };
  const cfg = TIERS[tier];
  if (!cfg) return { count: 0, limit: 0 };
  const bucket = Math.floor(Date.now() / 1000 / cfg.windowSec);
  const key = `rl:${tier}:${identity}:${bucket}`;
  const current = parseInt((await env.RATE_LIMIT_KV.get(key)) || '0', 10);
  return { count: current, limit: cfg.limit, windowSec: cfg.windowSec };
}

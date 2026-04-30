// Link-import flow: user pastes 1-10 TikTok/YouTube URLs → Worker fans out to
// the parser box on Oracle Cloud → user reviews + edits → submits to
// recipe_submission like any other Pro user-generated recipe.
//
// Architecture lives in backend/ingest/link-parser/. The Worker is a thin
// orchestration layer:
//   - rate-limit + Pro-tier gate
//   - persist import_job + link_jobs
//   - HMAC-sign and POST to PARSER_BOX_URL/webhook/job
//   - receive callback at /import/callback → store envelopes
//   - serve job status + envelopes back to the Android client
//
// Required env vars:
//   PARSER_BOX_URL                   e.g. https://parser.brimmapp.com
//   BRIMM_PARSER_SHARED_SECRET       same secret as on the parser box

import { json, err, readJson, uid, validString, validArray, sha256Hex } from './util.js';
import { enforce } from './ratelimit.js';
import { parseUrlInline } from './parser-stub.js';

// Pro link-import quota: 20 URLs / 24-hour rolling window per user. Caps worst-case
// per-user Claude spend at ~$0.12/day = ~$3.60/month if a user maxes every single
// day. Free tier never reaches this code (the Pro gate above rejects with 402 +
// upsell). Overage on Pro requires a credit-pack top-up — separate billing wiring.
const PRO_IMPORTS_DAILY = 20;

// ---------- Pro gate (duplicated tiny helper — submissions.js's isUserPro is
// not exported. If you'd rather de-dupe, hoist it into util.js.) ----------
async function isUserPro(env, userId) {
  try {
    const envName = (env.ENVIRONMENT || 'prod').toLowerCase();
    if (envName === 'dev') {
      const u = await env.DB.prepare('SELECT email FROM user WHERE id = ?').bind(userId).first();
      if (/\.test$/i.test(u?.email || '')) return true;
    }
    const ent = await env.DB.prepare(
      'SELECT expires_at FROM entitlement WHERE user_id = ? AND expires_at > ?'
    ).bind(userId, Date.now()).first();
    return !!ent;
  } catch { return false; }
}

// ---------- URL validation ----------
const ALLOWED_HOSTS = new Set([
  'tiktok.com', 'www.tiktok.com', 'vm.tiktok.com', 'm.tiktok.com',
  'youtube.com', 'www.youtube.com', 'm.youtube.com', 'youtu.be',
]);
function validateUrl(raw) {
  if (typeof raw !== 'string' || raw.length > 500) return null;
  let u;
  try { u = new URL(raw.trim()); } catch { return null; }
  if (u.protocol !== 'https:' && u.protocol !== 'http:') return null;
  if (!ALLOWED_HOSTS.has(u.hostname.toLowerCase())) return null;
  return u.toString();
}

async function hmacSign(secret, body) {
  const key = await crypto.subtle.importKey(
    'raw', new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']
  );
  const sig = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(body));
  return [...new Uint8Array(sig)].map(b => b.toString(16).padStart(2, '0')).join('');
}

async function hmacVerify(secret, body, hex) {
  if (typeof hex !== 'string' || hex.length !== 64) return false;
  const expect = await hmacSign(secret, body);
  // Constant-time compare
  if (expect.length !== hex.length) return false;
  let diff = 0;
  for (let i = 0; i < expect.length; i++) diff |= expect.charCodeAt(i) ^ hex.charCodeAt(i);
  return diff === 0;
}

export const handleImport = {
  /**
   * POST /api/import/links
   * Body: { urls: [string, ...] }   (1-10)
   * Returns: { ok: true, job_id, total_count }
   */
  async create(request, userId, env, ctx) {
    const rl = await enforce(env, 'write', userId);
    if (rl) return rl;

    if (!await isUserPro(env, userId)) {
      return err(402, 'Recipe URL import is a Brimm Pro feature.', { upsell: true });
    }
    // Two execution modes:
    //   A. Oracle parser box configured → fan out via HMAC-signed webhook (full pipeline:
    //      yt-dlp + Whisper + OCR + Claude). Real-time async via /api/import/callback.
    //   B. Stub mode → no parser box, but ANTHROPIC_API_KEY available. Process inline
    //      via Worker fetch+Claude, kick off in ctx.waitUntil so we respond fast and
    //      the Android client polls /jobs/:id for results. Handles 60-70% of links
    //      where the recipe is in the description / captions.
    const useStub = !env.PARSER_BOX_URL || !env.BRIMM_PARSER_SHARED_SECRET;
    if (useStub && !env.ANTHROPIC_API_KEY) {
      return err(503, 'link-import not configured');
    }

    const p = await readJson(request, 8_000);
    if (p.error) return p.error;
    const b = p.value;

    if (!validArray(b.urls, 10) || (b.urls || []).length === 0) {
      return err(400, 'urls: 1-10 items');
    }

    const validUrls = [];
    const invalid = [];
    for (const raw of b.urls) {
      const v = validateUrl(raw);
      if (v) validUrls.push(v); else invalid.push(String(raw).slice(0, 80));
    }
    if (validUrls.length === 0) {
      return err(400, 'no valid TikTok/YouTube URLs', { invalid });
    }

    // Daily Pro quota — count link_job rows queued by this user in the last 24h
    // rolling window. Reject if (used + this_request) would cross PRO_IMPORTS_DAILY.
    // 429 response sets creditPackUpsell:true so the Android client knows to show
    // the credit-pack purchase sheet (vs a generic rate-limit error).
    const windowStartMs = Date.now() - 86400_000;
    const usedRow = await env.DB.prepare(
      'SELECT COUNT(*) AS n FROM link_job WHERE user_id = ? AND created_at >= ?'
    ).bind(userId, windowStartMs).first();
    const usedToday = usedRow?.n || 0;
    if (usedToday + validUrls.length > PRO_IMPORTS_DAILY) {
      const remaining = Math.max(0, PRO_IMPORTS_DAILY - usedToday);
      // Refill happens when the oldest in-window job ages out past 24h.
      const oldest = await env.DB.prepare(
        'SELECT MIN(created_at) AS t FROM link_job WHERE user_id = ? AND created_at >= ?'
      ).bind(userId, windowStartMs).first();
      const refillAt = oldest?.t ? oldest.t + 86400_000 : null;
      const retryAfterSec = refillAt ? Math.max(60, Math.floor((refillAt - Date.now()) / 1000)) : null;
      return err(429,
        remaining === 0
          ? `Daily import limit reached (${PRO_IMPORTS_DAILY}). Buy a credit pack to keep going, or wait until your oldest import ages out.`
          : `Only ${remaining} import${remaining === 1 ? '' : 's'} left today. Reduce the batch or buy a credit pack.`,
        { retryAfter: retryAfterSec, remaining, dailyLimit: PRO_IMPORTS_DAILY, creditPackUpsell: true }
      );
    }

    const jobId = uid();
    const now = Date.now();
    const callbackSecretHash = useStub
      ? 'stub-mode'
      : await sha256Hex(jobId + env.BRIMM_PARSER_SHARED_SECRET);

    // Insert job + link_jobs in one batch. Both modes share this schema; stub
    // mode just fills the envelopes itself instead of waiting on a callback.
    const stmts = [
      env.DB.prepare(
        `INSERT INTO import_job (id, user_id, status, total_count, callback_secret_hash, created_at, updated_at)
         VALUES (?, ?, 'running', ?, ?, ?, ?)`
      ).bind(jobId, userId, validUrls.length, callbackSecretHash, now, now),
    ];
    const linkIds = [];
    for (let i = 0; i < validUrls.length; i++) {
      const lid = uid();
      linkIds.push(lid);
      stmts.push(env.DB.prepare(
        `INSERT INTO link_job (id, job_id, user_id, url, seq, status, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, 'running', ?, ?)`
      ).bind(lid, jobId, userId, validUrls[i], i, now, now));
    }
    await env.DB.batch(stmts);

    if (useStub) {
      // Background-process each URL via the inline stub. The Android client
      // polls /api/import/jobs/:id every 3s and gets envelopes as they land.
      // ctx.waitUntil keeps the runtime alive past the immediate 201 response.
      if (ctx?.waitUntil) {
        ctx.waitUntil(processStubJob(jobId, linkIds, validUrls, env));
      } else {
        // Fallback for tests without ctx — process inline (slower response).
        await processStubJob(jobId, linkIds, validUrls, env);
      }
    } else {
      // Real parser box: HMAC-signed POST, parser calls back to /api/import/callback.
      const callbackUrl = new URL('/api/import/callback', request.url).toString();
      const payload = JSON.stringify({ job_id: jobId, urls: validUrls, callback_url: callbackUrl });
      const sig = await hmacSign(env.BRIMM_PARSER_SHARED_SECRET, payload);
      try {
        const r = await fetch(`${env.PARSER_BOX_URL.replace(/\/$/, '')}/webhook/job`, {
          method: 'POST',
          headers: { 'content-type': 'application/json', 'x-brimm-signature': sig },
          body: payload,
        });
        if (!r.ok && r.status !== 202) {
          await env.DB.prepare(
            `UPDATE import_job SET status='failed', updated_at=? WHERE id=?`
          ).bind(Date.now(), jobId).run();
          return err(502, `parser box returned ${r.status}`);
        }
      } catch (e) {
        await env.DB.prepare(
          `UPDATE import_job SET status='failed', updated_at=? WHERE id=?`
        ).bind(Date.now(), jobId).run();
        return err(502, `parser box unreachable: ${e.message}`);
      }
    }

    return json({
      ok: true,
      job_id: jobId,
      total_count: validUrls.length,
      invalid_count: invalid.length,
      invalid,
      mode: useStub ? 'stub' : 'parser_box',
    }, 201, request, env);
  },

  /**
   * GET /api/import/jobs/:id
   * Returns: { ok, job: {...}, links: [ { id, url, seq, status, envelope } ] }
   */
  async get(jobId, request, userId, env) {
    const rl = await enforce(env, 'read', userId);
    if (rl) return rl;

    const job = await env.DB.prepare(
      `SELECT id, status, total_count, done_count, fail_count, created_at, updated_at
         FROM import_job WHERE id = ? AND user_id = ?`
    ).bind(jobId, userId).first();
    if (!job) return err(404, 'job not found');

    const linksRes = await env.DB.prepare(
      `SELECT id, url, seq, status, envelope_json, submission_id, created_at, updated_at
         FROM link_job WHERE job_id = ? ORDER BY seq ASC`
    ).bind(jobId).all();
    const links = (linksRes.results || []).map(l => ({
      id: l.id,
      url: l.url,
      seq: l.seq,
      status: l.status,
      submission_id: l.submission_id,
      envelope: l.envelope_json ? JSON.parse(l.envelope_json) : null,
      created_at: l.created_at,
      updated_at: l.updated_at,
    }));

    return json({ ok: true, job, links }, 200, request, env);
  },

  /**
   * POST /api/import/callback
   * Called by the parser box when a job finishes. HMAC-signed.
   * Body: { job_id, results: [envelope, ...] }   (envelope shape per parser schema.js)
   */
  async callback(request, env) {
    if (!env.BRIMM_PARSER_SHARED_SECRET) return err(503, 'callback not configured');
    const sig = request.headers.get('x-brimm-signature') || '';
    const raw = await request.text();
    if (raw.length > 200_000) return err(413, 'callback too large');
    const ok = await hmacVerify(env.BRIMM_PARSER_SHARED_SECRET, raw, sig);
    if (!ok) return err(401, 'bad signature');

    let body;
    try { body = JSON.parse(raw); } catch { return err(400, 'bad json'); }
    const { job_id, results } = body || {};
    if (!job_id || !Array.isArray(results)) return err(400, 'job_id + results required');

    const job = await env.DB.prepare(
      `SELECT user_id, total_count FROM import_job WHERE id = ?`
    ).bind(job_id).first();
    if (!job) return err(404, 'job not found');

    // Update each link_job row by URL match (parser preserves URL).
    const now = Date.now();
    let done = 0, failed = 0;
    for (const env_ of results) {
      const urlKey = env_?.url;
      if (!urlKey) continue;
      const linkOk = env_.ok === true;
      if (linkOk) done++; else failed++;
      await env.DB.prepare(
        `UPDATE link_job
            SET status = ?, envelope_json = ?, updated_at = ?
          WHERE job_id = ? AND url = ?`
      ).bind(
        linkOk ? 'done' : 'failed',
        JSON.stringify(env_),
        now, job_id, urlKey
      ).run();
    }

    const finalStatus = (done + failed) >= job.total_count ? 'done' : 'running';
    await env.DB.prepare(
      `UPDATE import_job
          SET status = ?, done_count = ?, fail_count = ?, updated_at = ?
        WHERE id = ?`
    ).bind(finalStatus, done, failed, now, job_id).run();

    return json({ ok: true }, 200, request, env);
  },

  /**
   * POST /api/import/links/:linkId/submit
   * Promote a parsed link_job into recipe_submission. Body may include user
   * edits (title, ingredients, steps, etc.) — those override the parser
   * envelope's values.
   */
  async submit(linkId, request, userId, env) {
    const rl = await enforce(env, 'write', userId);
    if (rl) return rl;

    if (!await isUserPro(env, userId)) {
      return err(402, 'Recipe URL import is a Brimm Pro feature.', { upsell: true });
    }

    const link = await env.DB.prepare(
      `SELECT * FROM link_job WHERE id = ? AND user_id = ?`
    ).bind(linkId, userId).first();
    if (!link) return err(404, 'link not found');
    if (link.status !== 'done') return err(409, `link not ready (status=${link.status})`);
    if (link.submission_id) return err(409, 'already submitted', { submission_id: link.submission_id });

    const env_ = JSON.parse(link.envelope_json || '{}');
    if (!env_.recipe) return err(400, 'no recipe in envelope');

    const p = await readJson(request, 60_000);
    if (p.error) return p.error;
    const edits = p.value || {};

    const r = env_.recipe;
    const title = validString(edits.title, { min: 5, max: 120 }) ? edits.title : r.title;
    const cuisine = edits.cuisine ?? r.cuisine ?? null;
    const description = edits.description ?? r.description ?? null;
    const servings = edits.servings ?? r.servings ?? null;
    const prepMin = edits.prep_minutes ?? r.prep_minutes ?? null;
    const cookMin = edits.cook_minutes ?? r.cook_minutes ?? null;
    const ingredients = Array.isArray(edits.ingredients) ? edits.ingredients : (r.ingredients || []);
    const steps = Array.isArray(edits.steps) ? edits.steps : (r.steps || []);
    // recipe_submission.image_url is NOT NULL — fall back to a sentinel placeholder
    // (matching the convention in submissions.js). Admin can fill at approval.
    const imageUrl = r.image_url || (env.PHOTOS_PUBLIC_BASE
      ? `${env.PHOTOS_PUBLIC_BASE.replace(/\/$/, '')}/_placeholder.jpg`
      : 'placeholder://no-image');

    const subId = uid();
    const now = Date.now();

    await env.DB.batch([
      env.DB.prepare(
        `INSERT INTO recipe_submission
           (id, user_id, title, cuisine, description, prep_minutes, cook_minutes, servings,
            ingredients_json, steps_json, image_url, status, source, source_url, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', 'link-import', ?, ?)`
      ).bind(
        subId, userId, title, cuisine, description, prepMin, cookMin, servings,
        JSON.stringify(ingredients), JSON.stringify(steps), imageUrl,
        env_.url || link.url, now
      ),
      env.DB.prepare(
        `UPDATE link_job SET status='submitted', submission_id=?, updated_at=? WHERE id=?`
      ).bind(subId, now, linkId),
    ]);

    return json({ ok: true, submission_id: subId }, 201, request, env);
  },

  /**
   * POST /api/import/links/:linkId/reject
   * User looked at the parsed result and rejected it.
   */
  async reject(linkId, request, userId, env) {
    const rl = await enforce(env, 'write', userId);
    if (rl) return rl;
    const r = await env.DB.prepare(
      `UPDATE link_job SET status='rejected', updated_at=? WHERE id=? AND user_id=?`
    ).bind(Date.now(), linkId, userId).run();
    if (r.meta.changes === 0) return err(404, 'link not found');
    return json({ ok: true }, 200, request, env);
  },
};

// Stub-mode job processor. Iterates the URLs sequentially, runs the inline parser,
// writes envelope_json (or status=failed + error) for each link_job, then marks
// the parent import_job done. Sequential rather than parallel to avoid hitting
// Anthropic concurrency / rate caps — each parse is ~5-8s so 10 URLs ≈ 60-80s.
// ctx.waitUntil keeps us alive past the immediate 201 response to the client.
async function processStubJob(jobId, linkIds, urls, env) {
  let doneCount = 0;
  let failCount = 0;
  for (let i = 0; i < urls.length; i++) {
    const linkId = linkIds[i];
    const url = urls[i];
    let result;
    try {
      result = await parseUrlInline(url, env);
    } catch (e) {
      result = { ok: false, error: `unhandled: ${e.message}` };
    }
    if (result.ok && result.envelope) {
      await env.DB.prepare(
        `UPDATE link_job SET status='done', envelope_json=?, updated_at=? WHERE id=?`
      ).bind(JSON.stringify(result.envelope), Date.now(), linkId).run();
      doneCount++;
    } else {
      // Schema has no error column — stash failure detail inside envelope_json
      // as { ok:false, error, signals_used } so the review screen can render it.
      const errEnvelope = JSON.stringify({
        ok: false,
        error: String(result.error || 'unknown').slice(0, 500),
        signals_used: result.signalsUsed || [],
      });
      await env.DB.prepare(
        `UPDATE link_job SET status='failed', envelope_json=?, updated_at=? WHERE id=?`
      ).bind(errEnvelope, Date.now(), linkId).run();
      failCount++;
    }
    // Update parent counts after each link so the polling client sees progress.
    await env.DB.prepare(
      `UPDATE import_job SET done_count=?, fail_count=?, updated_at=? WHERE id=?`
    ).bind(doneCount, failCount, Date.now(), jobId).run();
  }
  // Mark the job done. We use 'done' even if some links failed — the per-link
  // status tells the user which ones succeeded.
  await env.DB.prepare(
    `UPDATE import_job SET status='done', updated_at=? WHERE id=?`
  ).bind(Date.now(), jobId).run();
}

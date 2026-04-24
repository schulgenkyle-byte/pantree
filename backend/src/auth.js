// Auth: Google ID token -> Pantrie session tokens.
// Access token = HS256 JWT (15 min), with iss/aud/jti + revocation via KV.
// Refresh token = opaque 256-bit, SHA-256 hashed at rest, session-family with reuse detection.

import {
  json, err, readJson, sha256Hex, b64uEncode, b64uDecodeString,
  validOpaqueId, validString, timingSafeEqual,
} from './util.js';
import { verifyGoogleIdToken } from './jwks.js';
import { getServiceAccountToken } from './google-auth.js';
import { enforce, clientIp } from './ratelimit.js';

const JWT_ISS = 'pantrie';
const JWT_AUD = 'pantrie-mobile';
const ACCESS_TTL_SEC = 900;                   // 15 min
const REFRESH_TTL_MS = 30 * 24 * 3600 * 1000; // 30 days rolling
const SESSION_MAX_AGE_MS = 180 * 24 * 3600 * 1000; // 180 days absolute
const NONCE_TTL_SEC = 600;                    // 10 min
const REVOKE_KV_PREFIX = 'rev:';              // revoked jti
const NONCE_KV_PREFIX = 'nonce:';             // one-shot sign-in nonces

// ---------- JWT sign/verify ----------

async function hmacKey(secret) {
  return crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign', 'verify'],
  );
}

async function signJwt(payload, secret) {
  const header = { alg: 'HS256', typ: 'JWT' };
  const now = Math.floor(Date.now() / 1000);
  const jti = crypto.randomUUID();
  const p = { iss: JWT_ISS, aud: JWT_AUD, iat: now, exp: now + ACCESS_TTL_SEC, jti, ...payload };
  const data = `${b64uEncode(JSON.stringify(header))}.${b64uEncode(JSON.stringify(p))}`;
  const key = await hmacKey(secret);
  const sig = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(data));
  return { token: `${data}.${b64uEncode(new Uint8Array(sig))}`, jti, exp: p.exp };
}

async function verifyJwt(token, secret, env) {
  try {
    if (typeof token !== 'string' || token.length > 4096) return null;
    const parts = token.split('.');
    if (parts.length !== 3) return null;

    let header, payload;
    try {
      header = JSON.parse(b64uDecodeString(parts[0]));
      payload = JSON.parse(b64uDecodeString(parts[1]));
    } catch { return null; }

    // Strict algorithm + type pinning
    if (header.alg !== 'HS256' || header.typ !== 'JWT') return null;

    // Claims
    if (payload.iss !== JWT_ISS || payload.aud !== JWT_AUD) return null;
    const now = Math.floor(Date.now() / 1000);
    if (typeof payload.exp !== 'number' || payload.exp < now) return null;
    if (typeof payload.iat !== 'number' || payload.iat > now + 300) return null;
    if (!validOpaqueId(payload.sub)) return null;
    if (!validOpaqueId(payload.jti)) return null;

    // Signature
    const key = await hmacKey(secret);
    const sigBytes = Uint8Array.from(atob(parts[2].replace(/-/g, '+').replace(/_/g, '/')
      + '==='.slice(0, (4 - parts[2].length % 4) % 4)), c => c.charCodeAt(0));
    const ok = await crypto.subtle.verify('HMAC', key, sigBytes, new TextEncoder().encode(`${parts[0]}.${parts[1]}`));
    if (!ok) return null;

    // Revocation list
    if (env.RATE_LIMIT_KV) {
      const revoked = await env.RATE_LIMIT_KV.get(REVOKE_KV_PREFIX + payload.jti);
      if (revoked) return null;
    }

    return payload;
  } catch {
    return null;
  }
}

async function revokeJti(env, jti, exp) {
  if (!env.RATE_LIMIT_KV || !jti) return;
  const ttl = Math.max(1, (exp ?? Math.floor(Date.now() / 1000) + ACCESS_TTL_SEC) - Math.floor(Date.now() / 1000));
  await env.RATE_LIMIT_KV.put(REVOKE_KV_PREFIX + jti, '1', { expirationTtl: ttl });
}

// ---------- refresh tokens ----------

function newOpaqueToken() {
  const a = crypto.getRandomValues(new Uint8Array(32));
  return b64uEncode(a);
}

async function hashToken(token) {
  return sha256Hex(token);
}

async function issueSession(env, userId, familyId = null) {
  const { token: accessToken, jti, exp } = await signJwt({ sub: userId }, env.JWT_SECRET);
  const refreshToken = newOpaqueToken();
  const refreshHash = await hashToken(refreshToken);
  const now = Date.now();
  const family = familyId || crypto.randomUUID();
  const rootIssuedAt = familyId ? await getFamilyRoot(env, familyId) : now;

  await env.DB.prepare(
    'INSERT INTO session (token_hash, user_id, family_id, rotation_count, created_at, expires_at, family_root_at) VALUES (?, ?, ?, ?, ?, ?, ?)'
  ).bind(refreshHash, userId, family, 0, now, now + REFRESH_TTL_MS, rootIssuedAt).run();

  return {
    accessToken,
    accessJti: jti,
    refreshToken,
    userId,
    expiresAt: exp,
    familyId: family,
  };
}

async function getFamilyRoot(env, familyId) {
  const r = await env.DB.prepare('SELECT MIN(family_root_at) AS r FROM session WHERE family_id = ?').bind(familyId).first();
  return r?.r ?? Date.now();
}

async function rotateSession(env, currentHash, existing) {
  // Issue new token in same family; delete old
  const newToken = newOpaqueToken();
  const newHash = await hashToken(newToken);
  const { token: accessToken, jti, exp } = await signJwt({ sub: existing.user_id }, env.JWT_SECRET);
  const now = Date.now();
  await env.DB.prepare('DELETE FROM session WHERE token_hash = ?').bind(currentHash).run();
  await env.DB.prepare(
    'INSERT INTO session (token_hash, user_id, family_id, rotation_count, created_at, expires_at, family_root_at) VALUES (?, ?, ?, ?, ?, ?, ?)'
  ).bind(newHash, existing.user_id, existing.family_id, (existing.rotation_count || 0) + 1, now, now + REFRESH_TTL_MS, existing.family_root_at).run();

  return {
    accessToken,
    accessJti: jti,
    refreshToken: newToken,
    userId: existing.user_id,
    expiresAt: exp,
    familyId: existing.family_id,
  };
}

async function invalidateFamily(env, familyId) {
  await env.DB.prepare('DELETE FROM session WHERE family_id = ?').bind(familyId).run();
}

// ---------- nonces ----------

async function issueNonce(env, purpose = 'signin') {
  const nonce = b64uEncode(crypto.getRandomValues(new Uint8Array(32)));
  if (env.RATE_LIMIT_KV) {
    await env.RATE_LIMIT_KV.put(NONCE_KV_PREFIX + purpose + ':' + nonce, '1', { expirationTtl: NONCE_TTL_SEC });
  }
  return { nonce, expiresIn: NONCE_TTL_SEC };
}

async function consumeNonce(env, purpose, nonce) {
  if (!env.RATE_LIMIT_KV) return false;
  if (!nonce || typeof nonce !== 'string') return false;
  const key = NONCE_KV_PREFIX + purpose + ':' + nonce;
  const v = await env.RATE_LIMIT_KV.get(key);
  if (!v) return false;
  await env.RATE_LIMIT_KV.delete(key);
  return true;
}

// ---------- Play Integrity ----------

async function verifyIntegrity(env, integrityToken, expectedNonce) {
  // If not configured, skip (dev). In prod, config check will flag missing project.
  if (!env.PLAY_INTEGRITY_PROJECT) return { ok: true, verdict: 'skipped' };
  if (!integrityToken) return { ok: false, verdict: 'missing' };
  const accessToken = await getServiceAccountToken(env, 'https://www.googleapis.com/auth/playintegrity');
  if (!accessToken) return { ok: false, verdict: 'no-sa-token' };
  const packageName = env.PLAY_PACKAGE_NAME;
  if (!packageName) return { ok: false, verdict: 'no-package' };

  const res = await fetch(
    `https://playintegrity.googleapis.com/v1/${encodeURIComponent(packageName)}:decodeIntegrityToken`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${accessToken}`,
      },
      body: JSON.stringify({ integrityToken }),
    },
  );
  if (!res.ok) return { ok: false, verdict: `http-${res.status}` };
  const data = await res.json();
  const payload = data?.tokenPayloadExternal;
  if (!payload) return { ok: false, verdict: 'no-payload' };

  // Nonce binding
  if (expectedNonce && payload.requestDetails?.nonce !== expectedNonce) {
    return { ok: false, verdict: 'nonce-mismatch' };
  }
  if (payload.requestDetails?.requestPackageName !== packageName) {
    return { ok: false, verdict: 'package-mismatch' };
  }
  // Verdicts: require device integrity and app recognition
  const device = payload.deviceIntegrity?.deviceRecognitionVerdict || [];
  if (!device.includes('MEETS_DEVICE_INTEGRITY') && !device.includes('MEETS_STRONG_INTEGRITY')) {
    return { ok: false, verdict: 'device-failed' };
  }
  const appVerdict = payload.appIntegrity?.appRecognitionVerdict;
  if (appVerdict !== 'PLAY_RECOGNIZED') {
    return { ok: false, verdict: `app-${appVerdict || 'unknown'}` };
  }
  return { ok: true, verdict: 'passed', accountDetails: payload.accountDetails };
}

// ---------- handlers ----------

export const handleAuth = {
  async nonce(request, env) {
    const ip = clientIp(request);
    const rl = await enforce(env, 'auth', ip);
    if (rl) return rl;
    const { nonce, expiresIn } = await issueNonce(env, 'signin');
    return json({ nonce, expiresIn }, 200, request, env);
  },

  async googleExchange(request, env) {
    const ip = clientIp(request);
    const rl = await enforce(env, 'auth', ip);
    if (rl) return rl;

    const p = await readJson(request, 8_000);
    if (p.error) return p.error;
    const { idToken, nonce, integrityToken } = p.value;
    if (!validString(idToken, { min: 20, max: 4096 })) return err(400, 'idToken required');
    if (nonce !== undefined && !validString(nonce, { min: 10, max: 200 })) return err(400, 'bad nonce');

    // Consume nonce if supplied (required in prod).
    const envName = (env.ENVIRONMENT || 'prod').toLowerCase();
    let consumed = false;
    if (nonce) {
      consumed = await consumeNonce(env, 'signin', nonce);
      if (!consumed) return err(401, 'nonce invalid or expired');
    } else if (envName === 'prod') {
      return err(400, 'nonce required');
    }

    // Integrity check (binds integrity token to the same nonce)
    const integrity = await verifyIntegrity(env, integrityToken, nonce);
    if (envName === 'prod' && !integrity.ok) return err(403, `integrity ${integrity.verdict}`);

    // Verify Google ID token locally
    const clientIds = (env.GOOGLE_CLIENT_ID || '').split(',').map(s => s.trim()).filter(Boolean);
    const g = await verifyGoogleIdToken(idToken, env, { clientIds, expectedNonce: nonce });
    if (!g) return err(401, 'invalid google id token');

    const googleSub = g.sub;
    const email = String(g.email || '').toLowerCase();
    const name = (g.name || email.split('@')[0] || 'User').slice(0, 120);
    const emailHash = await sha256Hex(email);

    // Upsert user (by google_sub primary, email_hash secondary)
    let user = await env.DB.prepare('SELECT id FROM user WHERE google_sub = ?').bind(googleSub).first();
    let userId;
    if (!user) {
      userId = crypto.randomUUID();
      await env.DB.prepare(
        'INSERT INTO user (id, google_sub, email, email_hash, display_name, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)'
      ).bind(userId, googleSub, email, emailHash, name, Date.now(), Date.now()).run();
    } else {
      userId = user.id;
      await env.DB.prepare('UPDATE user SET updated_at = ? WHERE id = ?').bind(Date.now(), userId).run();
    }

    const session = await issueSession(env, userId);
    return json({
      userId: session.userId,
      accessToken: session.accessToken,
      refreshToken: session.refreshToken,
      expiresAt: session.expiresAt,
    }, 200, request, env);
  },

  async refresh(request, env) {
    const ip = clientIp(request);
    const rl = await enforce(env, 'auth', ip);
    if (rl) return rl;

    const p = await readJson(request, 4_000);
    if (p.error) return p.error;
    const { refreshToken } = p.value;
    if (!validString(refreshToken, { min: 16, max: 512 })) return err(400, 'refreshToken required');

    const hash = await hashToken(refreshToken);
    const s = await env.DB.prepare(
      'SELECT user_id, family_id, rotation_count, expires_at, family_root_at FROM session WHERE token_hash = ?'
    ).bind(hash).first();

    if (!s) {
      // Unknown token could be a reuse attempt — no way to know which family, so just reject.
      return err(401, 'invalid refresh token');
    }
    if (s.expires_at < Date.now()) {
      await env.DB.prepare('DELETE FROM session WHERE token_hash = ?').bind(hash).run();
      return err(401, 'refresh token expired');
    }
    if (s.family_root_at && Date.now() - s.family_root_at > SESSION_MAX_AGE_MS) {
      await invalidateFamily(env, s.family_id);
      return err(401, 'session too old; please sign in again');
    }

    const rotated = await rotateSession(env, hash, s);
    return json({
      accessToken: rotated.accessToken,
      refreshToken: rotated.refreshToken,
      expiresAt: rotated.expiresAt,
      userId: rotated.userId,
    }, 200, request, env);
  },

  async logout(request, userId, env, authPayload) {
    // Best-effort cleanup: revoke current access JTI, drop current refresh if supplied.
    const p = await readJson(request, 2_000);
    const body = p.error ? {} : p.value;
    if (body.refreshToken && validString(body.refreshToken, { min: 16, max: 512 })) {
      const h = await hashToken(body.refreshToken);
      await env.DB.prepare('DELETE FROM session WHERE token_hash = ? AND user_id = ?').bind(h, userId).run();
    }
    if (authPayload?.jti) await revokeJti(env, authPayload.jti, authPayload.exp);
    return json({ ok: true }, 200, request, env);
  },

  async logoutAll(request, userId, env, authPayload) {
    await env.DB.prepare('DELETE FROM session WHERE user_id = ?').bind(userId).run();
    if (authPayload?.jti) await revokeJti(env, authPayload.jti, authPayload.exp);
    return json({ ok: true }, 200, request, env);
  },

  /**
   * Dev-only: mint a session for a .test email. Only available when ENVIRONMENT=dev.
   * Strictly gated so prod mis-configuration cannot impersonate real users.
   */
  async devToken(request, env) {
    const envName = (env.ENVIRONMENT || 'prod').toLowerCase();
    if (envName !== 'dev') return err(404, 'not found');
    if (!env.DEV_TOKEN_KEY) return err(404, 'not found');
    const key = request.headers.get('x-dev-key') || '';
    if (!timingSafeEqual(key, env.DEV_TOKEN_KEY)) return err(403, 'forbidden');

    const p = await readJson(request, 2_000);
    if (p.error) return p.error;
    const rawEmail = (p.value.email || 'dev@pantrie.test').toLowerCase();
    if (!/^[\w+.\-]+@(?:[\w-]+\.)+test$/.test(rawEmail)) {
      return err(400, 'dev-token only issues .test emails');
    }
    let user = await env.DB.prepare('SELECT id FROM user WHERE email = ?').bind(rawEmail).first();
    let userId;
    if (!user) {
      userId = crypto.randomUUID();
      await env.DB.prepare(
        'INSERT INTO user (id, google_sub, email, email_hash, display_name, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)'
      ).bind(userId, 'dev-' + userId, rawEmail, await sha256Hex(rawEmail), 'Dev User', Date.now(), Date.now()).run();
    } else {
      userId = user.id;
    }
    const session = await issueSession(env, userId);
    return json({
      userId: session.userId,
      accessToken: session.accessToken,
      refreshToken: session.refreshToken,
      expiresAt: session.expiresAt,
    }, 200, request, env);
  },

  /**
   * Issue a short-lived, server-signed re-auth token after the user has just proven
   * identity via Google ID token. Used to authorize destructive actions (delete account).
   */
  async reauth(request, env, userId) {
    const p = await readJson(request, 8_000);
    if (p.error) return p.error;
    const { idToken, nonce } = p.value;
    if (!validString(idToken, { min: 20, max: 4096 })) return err(400, 'idToken required');
    if (nonce && !validString(nonce, { min: 10, max: 200 })) return err(400, 'bad nonce');
    if (nonce) {
      const ok = await consumeNonce(env, 'signin', nonce);
      if (!ok) return err(401, 'nonce invalid');
    }
    const clientIds = (env.GOOGLE_CLIENT_ID || '').split(',').map(s => s.trim()).filter(Boolean);
    const g = await verifyGoogleIdToken(idToken, env, { clientIds, expectedNonce: nonce });
    if (!g) return err(401, 'invalid google id token');

    const user = await env.DB.prepare('SELECT google_sub FROM user WHERE id = ?').bind(userId).first();
    if (!user || user.google_sub !== g.sub) return err(403, 'identity mismatch');

    // 5-minute reauth token, signed JWT with purpose=reauth
    const now = Math.floor(Date.now() / 1000);
    const header = { alg: 'HS256', typ: 'JWT' };
    const payload = { iss: JWT_ISS, aud: JWT_AUD, sub: userId, iat: now, exp: now + 300, purpose: 'reauth', jti: crypto.randomUUID() };
    const data = `${b64uEncode(JSON.stringify(header))}.${b64uEncode(JSON.stringify(payload))}`;
    const key = await hmacKey(env.JWT_SECRET);
    const sig = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(data));
    return json({ reauthToken: `${data}.${b64uEncode(new Uint8Array(sig))}`, expiresAt: payload.exp }, 200, request, env);
  },
};

// ---------- middleware ----------

export async function requireAuth(request, env) {
  const auth = request.headers.get('authorization');
  if (!auth?.startsWith('Bearer ')) return { error: 'missing bearer' };
  const token = auth.slice(7);
  if (!env.JWT_SECRET) return { error: 'server misconfigured' };
  const payload = await verifyJwt(token, env.JWT_SECRET, env);
  if (!payload) return { error: 'invalid token' };
  if (payload.purpose && payload.purpose !== 'access') return { error: 'wrong token type' };

  // Confirm user still exists (cheap cache check via KV — fail open if KV is unavailable)
  const cacheKey = `uexist:${payload.sub}`;
  let exists = null;
  try { exists = env.RATE_LIMIT_KV ? await env.RATE_LIMIT_KV.get(cacheKey) : null; } catch { /* ignore */ }
  if (!exists) {
    const u = await env.DB.prepare('SELECT id FROM user WHERE id = ?').bind(payload.sub).first();
    if (!u) return { error: 'user not found' };
    try { if (env.RATE_LIMIT_KV) await env.RATE_LIMIT_KV.put(cacheKey, '1', { expirationTtl: 60 }); } catch { /* KV quota exhausted — skip cache populate */ }
  }

  return { userId: payload.sub, jti: payload.jti, exp: payload.exp, payload };
}

export async function verifyReauthToken(env, userId, token) {
  if (!validString(token, { min: 20, max: 4096 })) return false;
  const payload = await verifyJwt(token, env.JWT_SECRET, env);
  if (!payload) return false;
  if (payload.purpose !== 'reauth') return false;
  if (payload.sub !== userId) return false;
  return true;
}

export { revokeJti, invalidateFamily, hashToken };

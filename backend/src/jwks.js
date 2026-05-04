// Google ID token verification via JWKs. Local RS256 verification, KV-cached certs.
// Replaces the deprecated /tokeninfo endpoint.

import { b64uDecodeBytes, b64uDecodeString } from './util.js';

const GOOGLE_JWKS_URL = 'https://www.googleapis.com/oauth2/v3/certs';
const GOOGLE_ISSUERS = new Set(['https://accounts.google.com', 'accounts.google.com']);
const JWKS_KV_KEY = 'jwks:google';
const JWKS_CACHE_TTL = 3600; // 1 hour; Google rotates ~weekly

async function fetchGoogleJwks(env) {
  if (env.RATE_LIMIT_KV) {
    const cached = await env.RATE_LIMIT_KV.get(JWKS_KV_KEY);
    if (cached) {
      try { return JSON.parse(cached); } catch { /* fall through */ }
    }
  }
  const res = await fetch(GOOGLE_JWKS_URL);
  if (!res.ok) throw new Error(`jwks fetch failed ${res.status}`);
  const data = await res.json();
  if (env.RATE_LIMIT_KV) {
    try { await env.RATE_LIMIT_KV.put(JWKS_KV_KEY, JSON.stringify(data), { expirationTtl: JWKS_CACHE_TTL }); }
    catch (e) { console.warn('jwks cache put failed (fail-open):', e?.message); }
  }
  return data;
}

async function importRsaJwk(jwk) {
  return crypto.subtle.importKey(
    'jwk',
    { kty: jwk.kty, n: jwk.n, e: jwk.e, alg: 'RS256', ext: true },
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['verify'],
  );
}

/**
 * Verify a Google ID token. Returns the parsed payload on success, null on any failure.
 * Strict checks: RS256 signature, issuer, audience, azp, exp, iat, email_verified.
 *
 * Options:
 *   clientIds:   array of allowed `aud`/`azp` values (your Android + Web OAuth client IDs)
 *   expectedNonce: if set, payload.nonce must match
 */
export async function verifyGoogleIdToken(idToken, env, options) {
  try {
    if (typeof idToken !== 'string' || idToken.length > 4096) return null;
    const parts = idToken.split('.');
    if (parts.length !== 3) return null;

    let header, payload;
    try {
      header = JSON.parse(b64uDecodeString(parts[0]));
      payload = JSON.parse(b64uDecodeString(parts[1]));
    } catch { return null; }

    if (header.alg !== 'RS256' || header.typ !== 'JWT') return null;
    if (!header.kid) return null;

    // issuer
    if (!GOOGLE_ISSUERS.has(payload.iss)) return null;
    // audience + azp: both must be in the allowed client IDs
    const allowed = options.clientIds || [];
    if (!allowed.includes(payload.aud)) return null;
    if (payload.azp && !allowed.includes(payload.azp)) return null;
    // expiry / issued-at validation
    const now = Math.floor(Date.now() / 1000);
    if (typeof payload.exp !== 'number' || payload.exp < now) return null;
    if (typeof payload.iat !== 'number' || payload.iat > now + 300) return null; // clock skew
    // email verified
    if (payload.email_verified !== true) return null;
    // optional nonce check
    if (options.expectedNonce && payload.nonce !== options.expectedNonce) return null;

    // signature
    const jwks = await fetchGoogleJwks(env);
    const jwk = (jwks.keys || []).find(k => k.kid === header.kid);
    if (!jwk) return null;

    const key = await importRsaJwk(jwk);
    const signingInput = new TextEncoder().encode(`${parts[0]}.${parts[1]}`);
    const sig = b64uDecodeBytes(parts[2]);
    const ok = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, sig, signingInput);
    if (!ok) return null;

    return payload;
  } catch {
    return null;
  }
}

/**
 * Verify an arbitrary Google-signed JWT (e.g. Pub/Sub OIDC push token).
 * Enforces RS256 + issuer + audience + email (if provided).
 */
export async function verifyGoogleSignedJwt(token, env, { audience, email, issuers } = {}) {
  try {
    if (typeof token !== 'string' || token.length > 4096) return null;
    const parts = token.split('.');
    if (parts.length !== 3) return null;
    const header = JSON.parse(b64uDecodeString(parts[0]));
    const payload = JSON.parse(b64uDecodeString(parts[1]));
    if (header.alg !== 'RS256' || header.typ !== 'JWT') return null;

    const okIss = issuers
      ? issuers.includes(payload.iss)
      : GOOGLE_ISSUERS.has(payload.iss);
    if (!okIss) return null;
    if (audience && payload.aud !== audience) return null;
    if (email && payload.email !== email) return null;

    const now = Math.floor(Date.now() / 1000);
    if (typeof payload.exp !== 'number' || payload.exp < now) return null;

    const jwks = await fetchGoogleJwks(env);
    const jwk = (jwks.keys || []).find(k => k.kid === header.kid);
    if (!jwk) return null;
    const key = await importRsaJwk(jwk);
    const signingInput = new TextEncoder().encode(`${parts[0]}.${parts[1]}`);
    const sig = b64uDecodeBytes(parts[2]);
    const ok = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, sig, signingInput);
    if (!ok) return null;

    return payload;
  } catch {
    return null;
  }
}

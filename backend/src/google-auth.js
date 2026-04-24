// Google service-account JWT -> OAuth2 access token. Used for Play Developer API calls.
// The service-account JSON is kept as a single secret (env.PLAY_SERVICE_ACCOUNT_JSON).

import { b64uEncode } from './util.js';

const TOKEN_KV_KEY = 'svc:google-access-token';

function pemToDerBytes(pem) {
  const body = pem
    .replace(/-----BEGIN [^-]+-----/g, '')
    .replace(/-----END [^-]+-----/g, '')
    .replace(/\s+/g, '');
  const bin = atob(body);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out.buffer;
}

async function importServiceAccountPrivateKey(pkcs8Pem) {
  const der = pemToDerBytes(pkcs8Pem);
  return crypto.subtle.importKey(
    'pkcs8',
    der,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign'],
  );
}

async function signSaJwt(sa, scope) {
  const header = { alg: 'RS256', typ: 'JWT', kid: sa.private_key_id };
  const now = Math.floor(Date.now() / 1000);
  const payload = {
    iss: sa.client_email,
    scope,
    aud: 'https://oauth2.googleapis.com/token',
    exp: now + 3600,
    iat: now,
  };
  const headerB64 = b64uEncode(JSON.stringify(header));
  const payloadB64 = b64uEncode(JSON.stringify(payload));
  const signingInput = `${headerB64}.${payloadB64}`;
  const key = await importServiceAccountPrivateKey(sa.private_key);
  const sig = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(signingInput),
  );
  return `${signingInput}.${b64uEncode(new Uint8Array(sig))}`;
}

/**
 * Get an OAuth2 access token for the Google service account, caching in KV.
 * Returns null if not configured or on failure.
 */
export async function getServiceAccountToken(env, scope) {
  if (!env.PLAY_SERVICE_ACCOUNT_JSON) return null;
  const cacheKey = `${TOKEN_KV_KEY}:${scope}`;
  if (env.RATE_LIMIT_KV) {
    const cached = await env.RATE_LIMIT_KV.get(cacheKey);
    if (cached) {
      try {
        const { token, exp } = JSON.parse(cached);
        if (Math.floor(Date.now() / 1000) < exp - 60) return token;
      } catch { /* fall through */ }
    }
  }
  let sa;
  try { sa = JSON.parse(env.PLAY_SERVICE_ACCOUNT_JSON); }
  catch { return null; }
  if (!sa.client_email || !sa.private_key) return null;

  const jwt = await signSaJwt(sa, scope);
  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `grant_type=${encodeURIComponent('urn:ietf:params:oauth:grant-type:jwt-bearer')}&assertion=${jwt}`,
  });
  if (!res.ok) return null;
  const data = await res.json();
  if (!data.access_token) return null;
  const exp = Math.floor(Date.now() / 1000) + (data.expires_in || 3600);
  if (env.RATE_LIMIT_KV) {
    await env.RATE_LIMIT_KV.put(cacheKey, JSON.stringify({ token: data.access_token, exp }), {
      expirationTtl: Math.max(60, (data.expires_in || 3600) - 60),
    });
  }
  return data.access_token;
}

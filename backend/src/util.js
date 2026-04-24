// Shared helpers: CORS, security headers, JSON IO with size caps, validators.

const SECURITY_HEADERS = {
  'Content-Type': 'application/json',
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'no-referrer',
  'Strict-Transport-Security': 'max-age=63072000; includeSubDomains; preload',
  'Content-Security-Policy': "default-src 'none'; frame-ancestors 'none'",
  'X-Frame-Options': 'DENY',
  'Cache-Control': 'no-store',
  'Vary': 'Origin',
};

function corsHeadersFor(request, env) {
  const origin = request?.headers?.get?.('origin') || null;
  const allowed = (env?.ALLOWED_ORIGIN || '').split(',').map(s => s.trim()).filter(Boolean);
  // Native Android apps send no Origin; we accept that. Browsers must match ALLOWED_ORIGIN.
  let allow = '';
  if (!origin) {
    allow = allowed[0] || '';
  } else if (allowed.includes(origin)) {
    allow = origin;
  } else {
    allow = ''; // explicit refusal; header omitted = browser rejects
  }
  const h = {
    'Access-Control-Allow-Methods': 'GET, POST, PATCH, DELETE, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Dev-Key, X-Seed-Key',
    'Access-Control-Max-Age': '86400',
  };
  if (allow) h['Access-Control-Allow-Origin'] = allow;
  return h;
}

export function cors(request, env) {
  return new Response(null, { status: 204, headers: corsHeadersFor(request, env) });
}

export function json(body, status = 200, request = null, env = null) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...SECURITY_HEADERS, ...corsHeadersFor(request, env) },
  });
}

export function err(status, message, extra) {
  return json({ ok: false, error: message, ...(extra || {}) }, status);
}

export function uid() {
  return crypto.randomUUID();
}

// ---------- base64url ----------
export function b64uEncode(input) {
  const bytes = typeof input === 'string' ? new TextEncoder().encode(input) : new Uint8Array(input);
  let s = '';
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
  return btoa(s).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
}

export function b64uDecodeBytes(s) {
  if (typeof s !== 'string') throw new Error('b64u: not a string');
  // reject disallowed chars early
  if (!/^[A-Za-z0-9_-]*$/.test(s)) throw new Error('b64u: bad chars');
  const pad = (4 - (s.length % 4)) % 4;
  const b64 = s.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat(pad);
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export function b64uDecodeString(s) {
  return new TextDecoder().decode(b64uDecodeBytes(s));
}

// ---------- hashing ----------
export async function sha256Hex(input) {
  const bytes = typeof input === 'string' ? new TextEncoder().encode(input) : input;
  const buf = await crypto.subtle.digest('SHA-256', bytes);
  return [...new Uint8Array(buf)].map(b => b.toString(16).padStart(2, '0')).join('');
}

// ---------- body reading with size cap ----------
export async function readJson(request, maxBytes = 200_000) {
  const cl = parseInt(request.headers.get('content-length') || '0', 10);
  if (cl > maxBytes) return { error: err(413, 'payload too large') };
  let text;
  try {
    // Fallback stream read — trust content-length above but also cap stream length
    const buf = new Uint8Array(await request.arrayBuffer());
    if (buf.byteLength > maxBytes) return { error: err(413, 'payload too large') };
    text = new TextDecoder().decode(buf);
  } catch {
    return { error: err(400, 'bad body') };
  }
  if (!text) return { value: {} };
  let value;
  try { value = JSON.parse(text); }
  catch { return { error: err(400, 'bad json') }; }
  if (value === null || typeof value !== 'object' || Array.isArray(value)) {
    return { error: err(400, 'expected json object') };
  }
  return { value };
}

// ---------- validators ----------
export function isNonEmptyString(v, maxLen = 256) {
  return typeof v === 'string' && v.length > 0 && v.length <= maxLen;
}

export function validString(v, { min = 0, max = 256 } = {}) {
  return typeof v === 'string' && v.length >= min && v.length <= max;
}

export function validStringOrNull(v, opts) {
  return v == null || validString(v, opts);
}

export function validInt(v, { min = -2147483648, max = 2147483647 } = {}) {
  return typeof v === 'number' && Number.isInteger(v) && v >= min && v <= max;
}

export function validIntOrNull(v, opts) {
  return v == null || validInt(v, opts);
}

export function validFiniteNumber(v, { min = 0, max = 1e9 } = {}) {
  return typeof v === 'number' && Number.isFinite(v) && v >= min && v <= max;
}

export function validBoolean(v) {
  return typeof v === 'boolean';
}

export function validArray(v, maxLen = 100) {
  return Array.isArray(v) && v.length <= maxLen;
}

export function validHttpsUrl(v, allowedHosts = []) {
  if (typeof v !== 'string' || v.length > 2000) return false;
  try {
    const u = new URL(v);
    if (u.protocol !== 'https:') return false;
    if (allowedHosts.length && !allowedHosts.includes(u.host)) return false;
    return true;
  } catch {
    return false;
  }
}

// ID tokens from clients: short UUID-like tokens only (UUIDs, hex, or dotted UUID pairs)
export function validOpaqueId(v, max = 128) {
  return typeof v === 'string' && v.length > 0 && v.length <= max && /^[\w.\-]+$/.test(v);
}

// Google purchase tokens are long base64url-ish strings
export function validPurchaseToken(v) {
  return typeof v === 'string' && v.length >= 16 && v.length <= 1024 && /^[A-Za-z0-9_\-.=]+$/.test(v);
}

// ---------- timing-safe string compare ----------
export function timingSafeEqual(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') return false;
  if (a.length !== b.length) return false;
  let x = 0;
  for (let i = 0; i < a.length; i++) x |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return x === 0;
}

// ---------- boot-time config check ----------
export function configProblems(env) {
  const problems = [];
  const placeholder = (v) => typeof v === 'string' && v.startsWith('REPLACE_WITH_');
  if (!env.JWT_SECRET || env.JWT_SECRET.length < 32) problems.push('JWT_SECRET missing or too short');
  if (!env.GOOGLE_CLIENT_ID || placeholder(env.GOOGLE_CLIENT_ID)) problems.push('GOOGLE_CLIENT_ID missing or placeholder');
  if (!env.DB) problems.push('DB binding missing');
  if (!env.RATE_LIMIT_KV) problems.push('RATE_LIMIT_KV binding missing');
  const envName = (env.ENVIRONMENT || 'prod').toLowerCase();
  if (envName === 'prod') {
    if (env.DEV_TOKEN_KEY) problems.push('DEV_TOKEN_KEY set in prod — delete before launch');
    if (!env.PLAY_SERVICE_ACCOUNT_JSON) problems.push('PLAY_SERVICE_ACCOUNT_JSON missing — billing verify disabled');
    if (!env.PLAY_PACKAGE_NAME) problems.push('PLAY_PACKAGE_NAME missing');
    if (!env.PLAY_INTEGRITY_PROJECT) problems.push('PLAY_INTEGRITY_PROJECT missing');
    if (!env.ALLOWED_ORIGIN) problems.push('ALLOWED_ORIGIN missing (set to your web domain)');
  }
  return problems;
}

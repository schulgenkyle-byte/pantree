// User-submitted recipes. Pipeline:
//   1. Client POSTs image (base64 data URL) to /submissions/photo → stored in R2 → returns public URL
//   2. Client POSTs full submission to /submissions/recipe with that URL → row in recipe_submission
//   3. Admin reviews via /admin/submissions → approves (promotes to recipe) or rejects
//
// Photos are mandatory. Dedup at review time = title similarity + ingredient overlap.

import { json, err, readJson, uid, validString, validStringOrNull, validInt, validIntOrNull, validArray, b64uDecodeBytes, timingSafeEqual } from './util.js';
import { enforce } from './ratelimit.js';
import { canonicalize } from './canonicalize.js';

// ---------- upload helpers ----------

const MAX_PHOTO_BYTES = 6 * 1024 * 1024; // 6 MB, resized-client-side should be well under
const ALLOWED_MIMES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const SIG_JPEG = [0xFF, 0xD8, 0xFF];
const SIG_PNG  = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A];
const SIG_WEBP_RIFF = [0x52, 0x49, 0x46, 0x46]; // "RIFF"
const SIG_WEBP_WEBP = [0x57, 0x45, 0x42, 0x50]; // "WEBP" at offset 8

function detectMime(bytes) {
  if (bytes.length < 12) return null;
  if (SIG_JPEG.every((b, i) => bytes[i] === b)) return 'image/jpeg';
  if (SIG_PNG.every((b, i) => bytes[i] === b)) return 'image/png';
  const riff = SIG_WEBP_RIFF.every((b, i) => bytes[i] === b);
  const webp = SIG_WEBP_WEBP.every((b, i) => bytes[i + 8] === b);
  if (riff && webp) return 'image/webp';
  return null;
}

function extFor(mime) {
  if (mime === 'image/jpeg') return 'jpg';
  if (mime === 'image/png') return 'png';
  if (mime === 'image/webp') return 'webp';
  return 'bin';
}

// ---------- dedup ----------

function normalizeTitle(t) {
  return String(t || '').toLowerCase().replace(/[^a-z0-9 ]+/g, ' ').replace(/\s+/g, ' ').trim();
}

function titleSimilarity(a, b) {
  const ta = new Set(normalizeTitle(a).split(' ').filter(Boolean));
  const tb = new Set(normalizeTitle(b).split(' ').filter(Boolean));
  if (!ta.size || !tb.size) return 0;
  let shared = 0;
  for (const w of ta) if (tb.has(w)) shared++;
  return shared / Math.max(ta.size, tb.size);
}

async function findDuplicate(env, title, ingredients) {
  // Narrow by token overlap first to keep the scan cheap.
  const titleWords = normalizeTitle(title).split(' ').filter(w => w.length > 2).slice(0, 3);
  if (!titleWords.length) return null;
  const likes = titleWords.map(() => 'LOWER(title) LIKE ?').join(' OR ');
  const binds = titleWords.map(w => `%${w}%`);
  const { results: candidates } = await env.DB.prepare(
    `SELECT id, title FROM recipe WHERE ${likes} LIMIT 20`
  ).bind(...binds).all();
  if (!candidates?.length) return null;

  const submissionIngs = new Set((ingredients || []).map(i => canonicalize(i.name || '')).filter(Boolean));
  for (const c of candidates) {
    const tSim = titleSimilarity(c.title, title);
    if (tSim < 0.5) continue;
    const { results: ings } = await env.DB.prepare(
      'SELECT canonical_name, name FROM recipe_ingredient WHERE recipe_id = ?'
    ).bind(c.id).all();
    const existing = new Set((ings || []).map(i => (i.canonical_name || canonicalize(i.name || '')) || '').filter(Boolean));
    if (!existing.size || !submissionIngs.size) continue;
    let shared = 0;
    for (const x of submissionIngs) if (existing.has(x)) shared++;
    const overlap = shared / Math.max(submissionIngs.size, existing.size);
    // Strong signal = title + ingredient overlap both high
    if (tSim >= 0.7 && overlap >= 0.6) return { id: c.id, title: c.title, titleSim: tSim, ingOverlap: overlap };
  }
  return null;
}

// ---------- admin auth ----------

function adminAuthed(request, env) {
  const key = request.headers.get('x-admin-key') || new URL(request.url).searchParams.get('key') || '';
  if (!env.ADMIN_KEY) return false;
  if (key.length !== env.ADMIN_KEY.length) return false;
  let x = 0;
  for (let i = 0; i < key.length; i++) x |= key.charCodeAt(i) ^ env.ADMIN_KEY.charCodeAt(i);
  return x === 0;
}

// ============================================================
// USER HANDLERS
// ============================================================
export const handleSubmissions = {
  /** POST /submissions/photo — base64 data URL → R2 → returns public URL. */
  async uploadPhoto(request, userId, env) {
    const rl = await enforce(env, 'write', userId);
    if (rl) return rl;
    if (!env.PHOTOS_BUCKET) return err(503, 'photo uploads unavailable (no R2 bucket bound)');
    if (!env.PHOTOS_PUBLIC_BASE) return err(503, 'photo upload misconfigured (PHOTOS_PUBLIC_BASE missing)');

    const p = await readJson(request, 9_000_000);
    if (p.error) return p.error;
    const img = p.value.image;
    if (typeof img !== 'string') return err(400, 'image required as data URL string');
    const m = img.match(/^data:(image\/(?:jpeg|png|webp));base64,([A-Za-z0-9+/=_-]+)$/);
    if (!m) return err(400, 'image must be data:image/{jpeg,png,webp};base64,...');
    const claimedMime = m[1];
    const b64 = m[2];
    let bytes;
    try { bytes = b64uDecodeBytes(b64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')); }
    catch { return err(400, 'bad base64'); }
    if (bytes.byteLength > MAX_PHOTO_BYTES) return err(413, 'image too large (max 6MB)');
    if (bytes.byteLength < 256) return err(400, 'image too small');
    const actual = detectMime(bytes);
    if (!actual || !ALLOWED_MIMES.has(actual)) return err(400, 'not a recognizable image');
    if (actual !== claimedMime) return err(400, 'mime/content mismatch');

    const id = uid();
    const key = `submissions/${userId}/${id}.${extFor(actual)}`;
    try {
      await env.PHOTOS_BUCKET.put(key, bytes, {
        httpMetadata: { contentType: actual, cacheControl: 'public, max-age=31536000, immutable' },
        customMetadata: { uploaded_by: userId, uploaded_at: String(Date.now()) },
      });
    } catch (e) {
      console.error('R2 put failed', e?.message);
      return err(500, 'upload failed');
    }
    const url = `${env.PHOTOS_PUBLIC_BASE.replace(/\/$/, '')}/${key}`;
    return json({ ok: true, url, key }, 200, request, env);
  },

  /** POST /submissions/recipe — full submission. Photo URL must come from /submissions/photo. */
  async submitRecipe(request, userId, env) {
    const rl = await enforce(env, 'write', userId);
    if (rl) return rl;

    const p = await readJson(request, 80_000);
    if (p.error) return p.error;
    const b = p.value;

    if (!validString(b.title, { min: 3, max: 100 })) return err(400, 'title: 3-100 chars');
    if (!validStringOrNull(b.cuisine, { max: 40 })) return err(400, 'cuisine too long');
    if (!validStringOrNull(b.description, { max: 400 })) return err(400, 'description too long');
    if (!validIntOrNull(b.prepMinutes, { min: 0, max: 600 })) return err(400, 'prepMinutes out of range');
    if (!validIntOrNull(b.cookMinutes, { min: 0, max: 600 })) return err(400, 'cookMinutes out of range');
    if (!validIntOrNull(b.servings,     { min: 1, max: 20 })) return err(400, 'servings: 1-20');
    if (!validArray(b.ingredients, 40) || (b.ingredients || []).length < 2) return err(400, 'ingredients: 2-40 items');
    if (!validArray(b.steps, 30) || (b.steps || []).length < 1) return err(400, 'steps: 1-30 items');
    if (!validString(b.imageUrl, { min: 10, max: 500 })) return err(400, 'imageUrl required — upload photo first via /submissions/photo');

    // PHOTO is mandatory (per product spec)
    if (!/^https:\/\//.test(b.imageUrl)) return err(400, 'imageUrl must be https');

    // Validate each ingredient + step shape
    const ings = [];
    for (const i of b.ingredients) {
      if (!i || typeof i !== 'object' || !validString(i.name, { min: 1, max: 80 })) return err(400, 'bad ingredient');
      ings.push({
        name: i.name.trim().slice(0, 80),
        quantity: typeof i.quantity === 'number' && i.quantity >= 0 ? i.quantity : null,
        unit: validStringOrNull(i.unit, { max: 20 }) ? (i.unit || null) : null,
        aisle: validStringOrNull(i.aisle, { max: 20 }) ? (i.aisle || null) : null,
      });
    }
    const steps = [];
    for (const s of b.steps) {
      if (!s || typeof s !== 'object' || !validString(s.text, { min: 4, max: 500 })) return err(400, 'bad step');
      steps.push({
        text: s.text.trim().slice(0, 500),
        timer_seconds: validIntOrNull(s.timer_seconds, { min: 0, max: 14400 }) ? (s.timer_seconds ?? null) : null,
      });
    }

    // Dedup check — flag likely dupes but still save for admin to review
    const dup = await findDuplicate(env, b.title, ings);

    const id = uid();
    const now = Date.now();
    const status = dup ? 'duplicate' : 'pending';
    await env.DB.prepare(
      `INSERT INTO recipe_submission
        (id, user_id, title, cuisine, description, prep_minutes, cook_minutes, servings,
         ingredients_json, steps_json, image_url, status, dup_of_recipe_id, created_at)
       VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)`
    ).bind(
      id, userId,
      b.title.trim(),
      b.cuisine || null,
      b.description || null,
      b.prepMinutes ?? null,
      b.cookMinutes ?? null,
      b.servings ?? null,
      JSON.stringify(ings),
      JSON.stringify(steps),
      b.imageUrl,
      status,
      dup?.id || null,
      now,
    ).run();

    return json({
      ok: true, id, status,
      dupOf: dup ? { id: dup.id, title: dup.title } : null,
    }, 201, request, env);
  },

  /** GET /submissions/mine — user's own submissions with status. */
  async mine(userId, env, request) {
    const rl = await enforce(env, 'read', userId);
    if (rl) return rl;
    const { results } = await env.DB.prepare(
      `SELECT id, title, cuisine, image_url, status, reject_reason, dup_of_recipe_id, approved_as, created_at, reviewed_at
         FROM recipe_submission WHERE user_id = ? ORDER BY created_at DESC LIMIT 100`
    ).bind(userId).all();
    return json({ submissions: results || [] }, 200, request, env);
  },
};

// ============================================================
// ADMIN HANDLERS
// ============================================================
export const handleSubmissionsAdmin = {
  async list(request, env) {
    if (!adminAuthed(request, env)) return err(401, 'bad admin key');
    const url = new URL(request.url);
    const status = url.searchParams.get('status') || 'pending';
    const valid = ['pending', 'approved', 'rejected', 'duplicate', 'all'];
    if (!valid.includes(status)) return err(400, 'bad status');
    const query = status === 'all'
      ? `SELECT s.*, u.email AS submitter_email FROM recipe_submission s LEFT JOIN user u ON u.id = s.user_id ORDER BY s.created_at DESC LIMIT 100`
      : `SELECT s.*, u.email AS submitter_email FROM recipe_submission s LEFT JOIN user u ON u.id = s.user_id WHERE s.status = ? ORDER BY s.created_at DESC LIMIT 100`;
    const stmt = status === 'all' ? env.DB.prepare(query) : env.DB.prepare(query).bind(status);
    const { results } = await stmt.all();
    return json({ submissions: results || [] }, 200, request, env);
  },

  async approve(request, id, env) {
    if (!adminAuthed(request, env)) return err(401, 'bad admin key');
    const sub = await env.DB.prepare('SELECT * FROM recipe_submission WHERE id = ?').bind(id).first();
    if (!sub) return err(404, 'submission not found');
    if (sub.status === 'approved') return err(400, 'already approved');

    const ings = JSON.parse(sub.ingredients_json || '[]');
    const steps = JSON.parse(sub.steps_json || '[]');
    const recipeId = `ugc-${sub.id}`;
    const now = Date.now();

    const stmts = [
      env.DB.prepare(
        `INSERT OR REPLACE INTO recipe
          (id, title, cuisine, description, prep_minutes, cook_minutes, servings,
           avg_rating, total_ratings, cook_count, image_url)
         VALUES (?,?,?,?,?,?,?, 0, 0, 0, ?)`
      ).bind(
        recipeId, sub.title, sub.cuisine || null, sub.description || null,
        sub.prep_minutes, sub.cook_minutes, sub.servings, sub.image_url,
      ),
      env.DB.prepare('DELETE FROM recipe_ingredient WHERE recipe_id = ?').bind(recipeId),
      env.DB.prepare('DELETE FROM recipe_step WHERE recipe_id = ?').bind(recipeId),
    ];
    ings.forEach((i, idx) => {
      stmts.push(env.DB.prepare(
        `INSERT INTO recipe_ingredient (recipe_id, seq, name, canonical_name, quantity, unit, aisle)
         VALUES (?, ?, ?, ?, ?, ?, ?)`
      ).bind(recipeId, idx, i.name, canonicalize(i.name), i.quantity ?? null, i.unit ?? null, i.aisle ?? null));
    });
    steps.forEach((s, idx) => {
      stmts.push(env.DB.prepare(
        `INSERT INTO recipe_step (recipe_id, seq, text, timer_seconds) VALUES (?, ?, ?, ?)`
      ).bind(recipeId, idx, s.text, s.timer_seconds ?? null));
    });
    stmts.push(env.DB.prepare(
      `UPDATE recipe_submission SET status = 'approved', approved_as = ?, reviewed_at = ? WHERE id = ?`
    ).bind(recipeId, now, id));

    await env.DB.batch(stmts);
    return json({ ok: true, recipeId }, 200, request, env);
  },

  async reject(request, id, env) {
    if (!adminAuthed(request, env)) return err(401, 'bad admin key');
    const p = await readJson(request, 2_000);
    if (p.error) return p.error;
    const reason = validStringOrNull(p.value?.reason, { max: 300 }) ? (p.value.reason || 'rejected by admin') : 'rejected by admin';
    await env.DB.prepare(
      `UPDATE recipe_submission SET status = 'rejected', reject_reason = ?, reviewed_at = ? WHERE id = ?`
    ).bind(reason, Date.now(), id).run();
    return json({ ok: true }, 200, request, env);
  },
};

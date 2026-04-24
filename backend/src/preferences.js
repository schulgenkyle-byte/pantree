// Pantrie user preferences + taste profile.
// Preferences = explicit picks (cuisines, avoid, diet, allergens, heat, adventure, onboarded flag).
// Taste profile = aggregate counts derived from interactions + reviews over last 90 days.
//
// Taste profile is computed on demand and cached in KV for 30 min under `taste:<userId>`.
// No new DB table — profile is pure derivation. Preferences live in `user_preferences`.

import {
  json, err, readJson, validString, validArray, validInt, validBoolean,
} from './util.js';
import { enforce } from './ratelimit.js';

const DIET_ALLOW = new Set([
  'none', 'vegetarian', 'vegan', 'pescatarian', 'keto', 'paleo', 'gluten-free', 'dairy-free',
]);
const MAX_CUISINES = 20;
const MAX_ALLERGENS = 20;
const MAX_AVOID = 40;
const TASTE_CACHE_TTL_S = 30 * 60;          // 30 minutes
const TASTE_WINDOW_MS   = 90 * 86400_000;   // 90 days

// ---------- hash ----------
// Short stable hash for the prefs object. Used to bust deck cache when prefs change.
export function prefHash(prefs) {
  const canon = JSON.stringify({
    c: [...(prefs.cuisines || [])].sort(),
    a: [...(prefs.avoid    || [])].sort(),
    d: prefs.diet || '',
    g: [...(prefs.allergens || [])].sort(),
    h: prefs.heat ?? 1,
    v: prefs.adventure ?? 1,
  });
  let h = 5381;
  for (let i = 0; i < canon.length; i++) h = (((h << 5) + h) ^ canon.charCodeAt(i)) >>> 0;
  return h.toString(36);
}

// ---------- DB load (internal) ----------
export async function loadPreferencesRow(userId, env) {
  const row = await env.DB.prepare(
    'SELECT cuisines_json, avoid_json, diet, allergens_json, heat, adventure, onboarded, updated_at FROM user_preferences WHERE user_id = ?'
  ).bind(userId).first();
  return row || null;
}

function rowToPrefs(row) {
  if (!row) {
    return {
      cuisines: [], avoid: [], diet: 'none', allergens: [],
      heat: 1, adventure: 1, onboarded: false, updatedAt: 0,
    };
  }
  const parse = (s) => { try { return s ? JSON.parse(s) : []; } catch { return []; } };
  return {
    cuisines: parse(row.cuisines_json),
    avoid: parse(row.avoid_json),
    diet: row.diet || 'none',
    allergens: parse(row.allergens_json),
    heat: Number.isFinite(row.heat) ? row.heat : 1,
    adventure: Number.isFinite(row.adventure) ? row.adventure : 1,
    onboarded: !!row.onboarded,
    updatedAt: row.updated_at || 0,
  };
}

// Exposed for recipes.js — fetches prefs as plain object, no HTTP wrapping.
export async function getPreferencesFor(userId, env) {
  return rowToPrefs(await loadPreferencesRow(userId, env));
}

// ---------- taste profile (computed, cached) ----------
/**
 * Pure-counting taste profile. Signals:
 *   - save      = +2 cuisine affinity, +1 per ingredient
 *   - cook      = +4 cuisine affinity, +2 per ingredient
 *   - dismiss   = -1 cuisine affinity, -0.5 per ingredient
 *   - review ≥4 = +3 cuisine bonus
 *   - review ≤2 = -2 cuisine penalty
 *
 * Complexity tolerance: average total prep+cook minutes of cooked recipes.
 */
export async function computeTasteProfile(userId, env) {
  const since = Date.now() - TASTE_WINDOW_MS;

  const { results: interactions } = await env.DB.prepare(
    'SELECT recipe_id, status FROM interaction WHERE user_id = ? AND created_at >= ?'
  ).bind(userId, since).all();

  const { results: reviews } = await env.DB.prepare(
    'SELECT recipe_id, rating_pots FROM review WHERE user_id = ? AND created_at >= ?'
  ).bind(userId, since).all();

  const recipeIds = new Set();
  for (const i of interactions || []) recipeIds.add(i.recipe_id);
  for (const r of reviews      || []) recipeIds.add(r.recipe_id);

  if (recipeIds.size === 0) {
    return {
      totalSignals: 0,
      topCuisines: [],
      topIngredients: [],
      avoidedIngredients: [],
      complexityAvgMinutes: 0,
      savedCount: 0,
      cookedCount: 0,
      dismissedCount: 0,
    };
  }

  // Pull recipe meta + ingredients in chunked IN queries.
  const ids = [...recipeIds];
  const recipeMeta = new Map();    // id -> { cuisine, totalMin }
  const recipeIngs = new Map();    // id -> [canonical ingredient names]
  for (let i = 0; i < ids.length; i += 80) {
    const chunk = ids.slice(i, i + 80);
    const ph = chunk.map(() => '?').join(',');
    const { results: metas } = await env.DB.prepare(
      `SELECT id, cuisine, prep_minutes, cook_minutes FROM recipe WHERE id IN (${ph})`
    ).bind(...chunk).all();
    for (const m of metas || []) {
      recipeMeta.set(m.id, {
        cuisine: (m.cuisine || '').trim().toLowerCase(),
        totalMin: (m.prep_minutes || 0) + (m.cook_minutes || 0),
      });
    }
    const { results: ings } = await env.DB.prepare(
      `SELECT recipe_id, name, canonical_name FROM recipe_ingredient WHERE recipe_id IN (${ph})`
    ).bind(...chunk).all();
    for (const row of ings || []) {
      const key = (row.canonical_name || row.name || '').trim().toLowerCase();
      if (!key) continue;
      if (!recipeIngs.has(row.recipe_id)) recipeIngs.set(row.recipe_id, []);
      recipeIngs.get(row.recipe_id).push(key);
    }
  }

  const cuisineAffinity   = new Map();
  const ingredientAffinity= new Map();
  const avoidedIng        = new Map();
  let cookedMinutesSum = 0, cookedCount = 0;
  let savedCount = 0, dismissedCount = 0;

  const bump = (m, k, delta) => { if (!k) return; m.set(k, (m.get(k) || 0) + delta); };

  for (const i of interactions || []) {
    const meta = recipeMeta.get(i.recipe_id);
    const ings = recipeIngs.get(i.recipe_id) || [];
    if (!meta) continue;
    if (i.status === 'saved') {
      savedCount++;
      bump(cuisineAffinity, meta.cuisine, 2);
      for (const ing of ings) bump(ingredientAffinity, ing, 1);
    } else if (i.status === 'cooked') {
      cookedCount++;
      cookedMinutesSum += meta.totalMin;
      bump(cuisineAffinity, meta.cuisine, 4);
      for (const ing of ings) bump(ingredientAffinity, ing, 2);
    } else if (i.status === 'dismissed') {
      dismissedCount++;
      bump(cuisineAffinity, meta.cuisine, -1);
      for (const ing of ings) {
        bump(ingredientAffinity, ing, -0.5);
        bump(avoidedIng, ing, 1);
      }
    }
  }

  for (const r of reviews || []) {
    const meta = recipeMeta.get(r.recipe_id);
    if (!meta) continue;
    if (r.rating_pots >= 4) bump(cuisineAffinity, meta.cuisine, 3);
    else if (r.rating_pots <= 2) bump(cuisineAffinity, meta.cuisine, -2);
  }

  const topEntries = (m, limit, minScore = 0.5) =>
    [...m.entries()]
      .filter(([k, v]) => k && v >= minScore)
      .sort((a, b) => b[1] - a[1])
      .slice(0, limit)
      .map(([name, score]) => ({ name, score: Math.round(score * 10) / 10 }));

  return {
    totalSignals: (interactions?.length || 0) + (reviews?.length || 0),
    topCuisines: topEntries(cuisineAffinity, 5),
    topIngredients: topEntries(ingredientAffinity, 10),
    avoidedIngredients: topEntries(avoidedIng, 5, 1),
    complexityAvgMinutes: cookedCount > 0 ? Math.round(cookedMinutesSum / cookedCount) : 0,
    savedCount, cookedCount, dismissedCount,
  };
}

export async function getTasteProfileCached(userId, env) {
  const key = `taste:${userId}`;
  if (env.RATE_LIMIT_KV) {
    const hit = await env.RATE_LIMIT_KV.get(key);
    if (hit) {
      try { return JSON.parse(hit); } catch { /* fall through */ }
    }
  }
  const profile = await computeTasteProfile(userId, env);
  if (env.RATE_LIMIT_KV) {
    try { await env.RATE_LIMIT_KV.put(key, JSON.stringify(profile), { expirationTtl: TASTE_CACHE_TTL_S }); }
    catch (e) { console.warn('taste cache put failed (fail-open):', e?.message); }
  }
  return profile;
}

// ---------- HTTP handlers ----------
export const handlePreferences = {
  async get(userId, env, request) {
    const rl = await enforce(env, 'read', userId);
    if (rl) return rl;
    const prefs = await getPreferencesFor(userId, env);
    return json(prefs, 200, request, env);
  },

  async put(request, userId, env) {
    const rl = await enforce(env, 'write', userId);
    if (rl) return rl;

    const p = await readJson(request, 8_000);
    if (p.error) return p.error;
    const body = p.value;

    const cuisines  = body.cuisines  ?? [];
    const avoid     = body.avoid     ?? [];
    const diet      = (body.diet ?? 'none').toString().toLowerCase();
    const allergens = body.allergens ?? [];
    const heat      = body.heat      ?? 1;
    const adventure = body.adventure ?? 1;
    const onboarded = body.onboarded === true;

    if (!validArray(cuisines, MAX_CUISINES))   return err(400, `cuisines: array up to ${MAX_CUISINES}`);
    if (!validArray(avoid, MAX_AVOID))         return err(400, `avoid: array up to ${MAX_AVOID}`);
    if (!validArray(allergens, MAX_ALLERGENS)) return err(400, `allergens: array up to ${MAX_ALLERGENS}`);
    for (const c of cuisines)  if (!validString(c, { min: 1, max: 40 })) return err(400, 'cuisine: 1-40 chars');
    for (const a of avoid)     if (!validString(a, { min: 1, max: 40 })) return err(400, 'avoid: 1-40 chars');
    for (const g of allergens) if (!validString(g, { min: 1, max: 40 })) return err(400, 'allergen: 1-40 chars');
    if (!DIET_ALLOW.has(diet)) return err(400, 'diet: unknown');
    if (!validInt(heat, { min: 0, max: 3 }))      return err(400, 'heat: 0-3');
    if (!validInt(adventure, { min: 0, max: 3 })) return err(400, 'adventure: 0-3');
    if (!validBoolean(onboarded))                 return err(400, 'onboarded: boolean');

    // Normalize to lowercased trimmed strings for stable matching.
    const norm = (a) => [...new Set(a.map(s => String(s).trim().toLowerCase()).filter(Boolean))];
    const cuisinesN  = norm(cuisines);
    const avoidN     = norm(avoid);
    const allergensN = norm(allergens);

    const now = Date.now();
    await env.DB.prepare(
      `INSERT INTO user_preferences
         (user_id, cuisines_json, avoid_json, diet, allergens_json, heat, adventure, onboarded, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(user_id) DO UPDATE SET
         cuisines_json  = excluded.cuisines_json,
         avoid_json     = excluded.avoid_json,
         diet           = excluded.diet,
         allergens_json = excluded.allergens_json,
         heat           = excluded.heat,
         adventure      = excluded.adventure,
         onboarded      = excluded.onboarded,
         updated_at     = excluded.updated_at`
    ).bind(
      userId,
      JSON.stringify(cuisinesN),
      JSON.stringify(avoidN),
      diet,
      JSON.stringify(allergensN),
      heat, adventure, onboarded ? 1 : 0, now,
    ).run();

    // Bust all related caches so next read reflects new prefs.
    if (env.RATE_LIMIT_KV) {
      await env.RATE_LIMIT_KV.delete(`taste:${userId}`);
      // Deck cache keys are `deck:<userId>:<prefHash>` — old hash will just age out via TTL.
    }

    return json({
      ok: true,
      cuisines: cuisinesN, avoid: avoidN, diet, allergens: allergensN,
      heat, adventure, onboarded, updatedAt: now,
    }, 200, request, env);
  },

  async taste(userId, env, request) {
    const rl = await enforce(env, 'read', userId);
    if (rl) return rl;
    const profile = await getTasteProfileCached(userId, env);
    return json(profile, 200, request, env);
  },
};

import {
  json, err, readJson, uid, validOpaqueId, validArray, validString,
} from './util.js';
import { enforce } from './ratelimit.js';
import { buildPantryIndex, indexMatch, isStaple } from './ingredient-match.js';
import { SHELF_STABLE_THRESHOLD_DAYS } from './expiry.js';

const MAX_PLAN_RECIPES = 21; // 3 meals x 7 days

export const handlePlans = {
  /**
   * "Plan it for me" — propose N dinner recipes for the week, prioritizing:
   *   1. Uses expiring pantry items
   *   2. High pantry match
   *   3. Cuisine diversity
   * Does NOT persist; client confirms by posting a full plan.
   */
  async propose(userId, env, request) {
    const rl = await enforce(env, 'read', userId);
    if (rl) return rl;
    const url = new URL(request.url);
    const slots = Math.max(1, Math.min(14, parseInt(url.searchParams.get('slots') || '7', 10)));

    const now = Date.now();
    const EXPIRING_MS = 5 * 86400_000;
    const { results: pantry } = await env.DB.prepare(
      'SELECT name, canonical_name, expires_at, original_shelf_days FROM pantry_item WHERE user_id = ?'
    ).bind(userId).all();
    const pantryNames = [];
    const expiringNames = [];
    for (const p of pantry || []) {
      const key = (p.canonical_name || p.name || '').trim();
      if (!key) continue;
      pantryNames.push(key);
      // Shelf-stable items (salt, flour, oil, spices) never count as "expiring" —
      // their expires_at is years away and triggering a plan reason on them is
      // nonsensical (the bug that motivated this whole pass).
      const shelfDays = Number(p.original_shelf_days);
      if (Number.isFinite(shelfDays) && shelfDays > SHELF_STABLE_THRESHOLD_DAYS) continue;
      if (p.expires_at) {
        const ts = /^\d{10,}$/.test(p.expires_at) ? parseInt(p.expires_at, 10) : Date.parse(p.expires_at);
        if (Number.isFinite(ts) && ts - now < EXPIRING_MS) expiringNames.push(key);
      }
    }
    const pantryIdx = buildPantryIndex(pantryNames);
    const expiringIdx = buildPantryIndex(expiringNames);

    // Require a real time estimate — Wikibooks recipes often have NULL prep/cook minutes,
    // which renders as "0 min" in the plan card. Filter those out so the week plan only
    // surfaces recipes with honest time info.
    // FOOD ONLY — cocktails / mocktails don't belong in a weekly meal plan. Drinks get
    // their own deck (Mixology); the meal plan is for actual meals.
    const { results: recipes } = await env.DB.prepare(
      `SELECT id, title, cuisine, prep_minutes, cook_minutes, servings, avg_rating
         FROM recipe
        WHERE (COALESCE(prep_minutes, 0) + COALESCE(cook_minutes, 0)) > 0
          AND COALESCE(content_type, 'food') = 'food'
        ORDER BY RANDOM() LIMIT 150`
    ).all();
    const ids = (recipes || []).map(r => r.id);
    if (!ids.length) return json({ proposals: [] }, 200, request, env);

    // Batch fetch ingredients
    const ingsByRecipe = new Map();
    for (let i = 0; i < ids.length; i += 80) {
      const chunk = ids.slice(i, i + 80);
      const ph = chunk.map(() => '?').join(',');
      const { results: ings } = await env.DB.prepare(
        `SELECT recipe_id, name, canonical_name FROM recipe_ingredient WHERE recipe_id IN (${ph})`
      ).bind(...chunk).all();
      for (const x of ings || []) {
        if (!ingsByRecipe.has(x.recipe_id)) ingsByRecipe.set(x.recipe_id, []);
        ingsByRecipe.get(x.recipe_id).push((x.canonical_name || x.name || '').toLowerCase().trim());
      }
    }

    const scored = [];
    for (const r of recipes || []) {
      const ingList = ingsByRecipe.get(r.id) || [];
      if (ingList.length < 2) continue;
      const usesExpiring = ingList.filter(n => indexMatch(n, expiringIdx) !== null);
      const matched = ingList.filter(n => indexMatch(n, pantryIdx) !== null).length;
      const percent = Math.round((matched / ingList.length) * 100);
      const score = percent + usesExpiring.length * 30 + (r.avg_rating || 0) * 3;
      const reason = usesExpiring.length > 0
        ? `Uses your expiring ${usesExpiring.slice(0, 2).join(', ')}`
        : percent >= 70
          ? `${percent}% pantry match`
          : `Try something new (${r.cuisine || 'recipe'})`;
      scored.push({ score, cuisine: r.cuisine, proposal: {
        id: r.id, title: r.title, cuisine: r.cuisine,
        prepMinutes: r.prep_minutes, cookMinutes: r.cook_minutes, servings: r.servings,
        pantryMatchPercent: percent, usesExpiring, reason,
      }});
    }
    scored.sort((a, b) => b.score - a.score);

    // Cuisine diversity — greedy: take top N ensuring no cuisine repeats more than 2×
    const cuisineCount = new Map();
    const proposals = [];
    for (const s of scored) {
      if (proposals.length >= slots) break;
      const cnt = cuisineCount.get(s.cuisine || '') || 0;
      if (cnt >= 2) continue;
      cuisineCount.set(s.cuisine || '', cnt + 1);
      proposals.push(s.proposal);
    }
    // If we still need more (small cuisine variety), fall back to top remaining
    if (proposals.length < slots) {
      const have = new Set(proposals.map(p => p.id));
      for (const s of scored) {
        if (proposals.length >= slots) break;
        if (!have.has(s.proposal.id)) proposals.push(s.proposal);
      }
    }
    return json({ proposals }, 200, request, env);
  },

  async list(userId, env, request) {
    const rl = await enforce(env, 'read', userId);
    if (rl) return rl;
    const { results } = await env.DB.prepare(
      'SELECT id, name, recipe_ids, created_at FROM plan WHERE user_id = ? ORDER BY created_at DESC'
    ).bind(userId).all();
    // Android Plan DTO requires camelCase `recipeIds: List<String>` (no default) —
    // raw rows made the whole plans list fail to deserialize on the client.
    const plans = (results || []).map(r => {
      let recipeIds = [];
      try { const v = JSON.parse(r.recipe_ids || '[]'); if (Array.isArray(v)) recipeIds = v.filter(x => typeof x === 'string'); } catch { /* keep [] */ }
      return { id: r.id, name: r.name, recipeIds, createdAt: r.created_at || 0 };
    });
    return json({ plans }, 200, request, env);
  },

  /** Return 3 alternative proposals similar to a displaced recipe — same cuisine bucket,
   * similar match %, adjacent cook time. Doesn't cost a swipe. */
  async alternatives(request, userId, env) {
    const rl = await enforce(env, 'read', userId);
    if (rl) return rl;
    const p = await readJson(request, 2_000);
    if (p.error) return p.error;
    const { recipeId, excludeIds } = p.value;
    if (!validOpaqueId(recipeId)) return err(400, 'recipeId required');
    const exclude = new Set([recipeId, ...(Array.isArray(excludeIds) ? excludeIds.filter(v => typeof v === 'string') : [])]);

    const seed = await env.DB.prepare(
      'SELECT cuisine, cook_minutes, prep_minutes FROM recipe WHERE id = ?'
    ).bind(recipeId).first();
    if (!seed) return err(404, 'recipe not found');

    // Same cuisine bucket, recipes-with-photos first, within ±15 min total time
    const seedTotal = (seed.prep_minutes || 0) + (seed.cook_minutes || 0);
    const { results } = await env.DB.prepare(
      `SELECT id, title, cuisine, prep_minutes, cook_minutes, servings, image_url, avg_rating, total_ratings, cook_count
         FROM recipe
        WHERE COALESCE(cuisine,'') = COALESCE(?, '')
          AND image_url IS NOT NULL
          AND (COALESCE(prep_minutes,0) + COALESCE(cook_minutes,0)) BETWEEN ? AND ?
        ORDER BY RANDOM() LIMIT 40`
    ).bind(seed.cuisine || '', Math.max(0, seedTotal - 15), seedTotal + 15).all();

    const { results: pantry } = await env.DB.prepare(
      'SELECT canonical_name, name FROM pantry_item WHERE user_id = ?'
    ).bind(userId).all();
    const pantrySet = new Set((pantry || []).map(p => (p.canonical_name || p.name || '').toLowerCase().trim()).filter(Boolean));

    const picks = [];
    for (const r of results || []) {
      if (exclude.has(r.id)) continue;
      // Lightweight match %: count canonical overlap against this recipe's ingredients
      const { results: ings } = await env.DB.prepare(
        'SELECT name, canonical_name FROM recipe_ingredient WHERE recipe_id = ? LIMIT 20'
      ).bind(r.id).all();
      if (!ings?.length) continue;
      let matched = 0;
      for (const i of ings) {
        const k = (i.canonical_name || i.name || '').toLowerCase().trim();
        if (k && pantrySet.has(k)) matched++;
      }
      const percent = Math.round((matched / ings.length) * 100);
      picks.push({
        id: r.id, title: r.title, cuisine: r.cuisine,
        prepMinutes: r.prep_minutes, cookMinutes: r.cook_minutes,
        servings: r.servings, imageUrl: r.image_url,
        pantryMatchPercent: percent,
        cookCount: r.cook_count || 0,
      });
      if (picks.length >= 3) break;
    }

    return json({ alternatives: picks }, 200, request, env);
  },

  async create(request, userId, env) {
    const rl = await enforce(env, 'write', userId);
    if (rl) return rl;

    const p = await readJson(request, 16_000);
    if (p.error) return p.error;
    const { name, recipeIds } = p.value;
    if (!validArray(recipeIds, MAX_PLAN_RECIPES) || recipeIds.length === 0) {
      return err(400, `recipeIds: 1-${MAX_PLAN_RECIPES} items`);
    }
    for (const rid of recipeIds) if (!validOpaqueId(rid)) return err(400, 'recipeIds: bad id');
    const planName = name == null ? 'This week' : String(name).slice(0, 80);
    if (!validString(planName, { max: 80 })) return err(400, 'name: <=80 chars');

    // Validate all recipes exist in a single query
    const placeholders = recipeIds.map(() => '?').join(',');
    const { results: found } = await env.DB.prepare(
      `SELECT id FROM recipe WHERE id IN (${placeholders})`
    ).bind(...recipeIds).all();
    const foundSet = new Set((found || []).map(r => r.id));
    for (const rid of recipeIds) if (!foundSet.has(rid)) return err(400, `recipe not found: ${rid}`);

    const id = uid();
    const now = Date.now();

    await env.DB.prepare(
      'INSERT INTO plan (id, user_id, name, recipe_ids, created_at) VALUES (?, ?, ?, ?, ?)'
    ).bind(id, userId, planName, JSON.stringify(recipeIds), now).run();

    for (const rid of recipeIds) {
      await env.DB.prepare(
        'INSERT OR REPLACE INTO interaction (id, user_id, recipe_id, status, created_at) VALUES (?, ?, ?, ?, ?)'
      ).bind(uid(), userId, rid, 'planned', now).run();
    }

    // Auto-shopping: ingredients user doesn't already have
    const { results: pantry } = await env.DB.prepare('SELECT name FROM pantry_item WHERE user_id = ?').bind(userId).all();
    const have = new Set((pantry || []).map(p => String(p.name || '').toLowerCase()));

    const { results: allIngs } = await env.DB.prepare(
      `SELECT recipe_id, name, quantity, unit, aisle FROM recipe_ingredient WHERE recipe_id IN (${placeholders})`
    ).bind(...recipeIds).all();

    const needed = new Map();
    for (const i of allIngs || []) {
      const key = String(i.name || '').toLowerCase();
      if (!key || have.has(key)) continue;
      if (isStaple(i.name) || isStaple(key)) continue;  // skip salt/pepper/water
      if (!needed.has(key)) {
        needed.set(key, { name: i.name, aisle: i.aisle || 'other', unit: i.unit || null, quantity: Number(i.quantity) || 0 });
      } else {
        const entry = needed.get(key);
        if (entry.unit === i.unit && Number.isFinite(Number(i.quantity))) entry.quantity += Number(i.quantity);
      }
    }

    for (const item of needed.values()) {
      await env.DB.prepare(
        'INSERT INTO shopping_item (id, user_id, name, quantity, unit, aisle, checked, source, source_plan_id, created_at) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?)'
      ).bind(
        uid(), userId,
        String(item.name).slice(0, 80),
        item.quantity > 0 ? item.quantity : 1,
        item.unit, item.aisle,
        'plan', id, now,
      ).run();
    }

    return json({
      ok: true,
      addedToList: needed.size,
      plan: { id, name: planName, recipeIds, createdAt: now },
    }, 200, request, env);
  },

  async delete(id, userId, env, request) {
    if (!validOpaqueId(id)) return err(400, 'id invalid');
    const plan = await env.DB.prepare('SELECT recipe_ids FROM plan WHERE id = ? AND user_id = ?').bind(id, userId).first();
    if (!plan) return err(404, 'plan not found');

    let rids = [];
    try { rids = JSON.parse(plan.recipe_ids || '[]'); } catch { rids = []; }
    for (const rid of rids) {
      await env.DB.prepare("DELETE FROM interaction WHERE user_id = ? AND recipe_id = ? AND status = 'planned'")
        .bind(userId, rid).run();
    }
    // Only drop unchecked shopping items that belong to THIS plan
    await env.DB.prepare(
      'DELETE FROM shopping_item WHERE user_id = ? AND source_plan_id = ? AND checked = 0'
    ).bind(userId, id).run();
    await env.DB.prepare('DELETE FROM plan WHERE id = ? AND user_id = ?').bind(id, userId).run();
    return json({ ok: true }, 200, request, env);
  },
};

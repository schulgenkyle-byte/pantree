import {
  json, err, readJson, uid, validOpaqueId, validString, validStringOrNull,
  validFiniteNumber, validBoolean,
} from './util.js';
import { enforce } from './ratelimit.js';
import { isStaple } from './ingredient-match.js';

const AISLE_ALLOW = new Set(['produce','protein','dairy','grain','pantry','spice','condiment','frozen','beverage','bakery','deli','other']);
const SOURCE_ALLOW = new Set(['manual','plan','scan','restock','unlock','mealprep','reshop','saved-recipe']);
const UNIT_MAX = 20;

// Typical store walk order: perimeter first (fresh), then aisles by common layout.
// Users can finish their trip linearly without backtracking.
const AISLE_WALK_ORDER = {
  produce: 1, bakery: 2, deli: 3, protein: 4, dairy: 5,
  frozen: 6, grain: 7, pantry: 8, spice: 9, condiment: 10,
  beverage: 11, other: 99,
};

export const handleShopping = {
  /**
   * Smart shopping view: expiring items, "unlock more recipes" suggestions,
   * regular list grouped by aisle in store-walk order. One endpoint = one round trip.
   */
  async smart(userId, env, request) {
    const rl = await enforce(env, 'read', userId);
    if (rl) return rl;
    const now = Date.now();

    // 1) Normal list — DB sort then re-sort in memory by store walk order
    const { results: listRaw } = await env.DB.prepare(
      'SELECT id, name, quantity, unit, aisle, checked, source, source_plan_id, created_at FROM shopping_item WHERE user_id = ? ORDER BY created_at'
    ).bind(userId).all();
    const list = (listRaw || [])
      .map(r => ({ ...r, checked: !!r.checked }))       // SQLite stores 0/1; Kotlin DTO needs a real Boolean
      .sort((a, b) => {
        const wa = AISLE_WALK_ORDER[(a.aisle || 'other').toLowerCase()] ?? 99;
        const wb = AISLE_WALK_ORDER[(b.aisle || 'other').toLowerCase()] ?? 99;
        if (wa !== wb) return wa - wb;
        // Checked items sink within each aisle
        if (a.checked !== b.checked) return (a.checked ? 1 : 0) - (b.checked ? 1 : 0);
        return (a.created_at || 0) - (b.created_at || 0);
      });

    // 2) Expiring pantry items (<= 3 days)
    const { results: pantry } = await env.DB.prepare(
      'SELECT id, name, category, quantity, unit, expires_at FROM pantry_item WHERE user_id = ?'
    ).bind(userId).all();
    const expiringItems = [];
    const pantryNames = new Set();
    for (const p of pantry || []) {
      const nm = String(p.name || '').toLowerCase().trim();
      pantryNames.add(nm);
      if (!p.expires_at) continue;
      const ts = /^\d{10,}$/.test(p.expires_at) ? parseInt(p.expires_at, 10) : Date.parse(p.expires_at);
      if (!Number.isFinite(ts)) continue;
      const daysLeft = Math.floor((ts - now) / 86400_000);
      if (daysLeft <= 3) {
        expiringItems.push({
          id: p.id, name: p.name, category: p.category,
          quantity: p.quantity, unit: p.unit, daysLeft,
        });
      }
    }
    expiringItems.sort((a, b) => a.daysLeft - b.daysLeft);

    // 3) "Unlock more recipes" — only suggest HIGH-VALUE ingredients.
    // Rules:
    //   - Pull top 600 food recipes (skip cocktails — they shouldn't suggest spirits to a cooking shopper)
    //   - Skip junk: undefined, empty, stopwords, single-letter, "1/2", numerals
    //   - Skip universal staples (water/salt/pepper) — already always-have
    //   - Min unlock count = 5 recipes (no more "flavored water → 1 new recipe" garbage)
    const JUNK = new Set([
      'undefined', 'null', '', 'water', 'salt', 'pepper', 'ice',
      'oil', 'cooking oil', 'vegetable oil', 'olive oil',
      'salt and pepper', 'pinch of salt', 'pinch of pepper',
      'taste', 'to taste', 'as needed', 'optional',
      'garnish', 'for garnish', 'for serving', 'for dusting',
      'flavored water', 'sparkling water', 'tap water',
      'half', 'whole', 'small', 'large', 'medium', 'fresh',
    ]);
    const isJunk = n => {
      const t = n.trim();
      if (!t) return true;
      if (JUNK.has(t)) return true;
      if (t.length < 3) return true;             // "a", "1/2"
      if (/^\d/.test(t)) return true;            // "1/2 cup"
      if (/^[\d\s./,&-]+$/.test(t)) return true; // pure numbers/punct
      return false;
    };
    const { results: recipes } = await env.DB.prepare(
      "SELECT id, title, cuisine FROM recipe WHERE (content_type = 'food' OR content_type IS NULL) ORDER BY avg_rating DESC, cook_count DESC, ROWID LIMIT 600"
    ).all();
    const recipeIds = (recipes || []).map(r => r.id);
    const unlockMap = new Map();
    if (recipeIds.length) {
      for (let i = 0; i < recipeIds.length; i += 80) {
        const chunk = recipeIds.slice(i, i + 80);
        const ph = chunk.map(() => '?').join(',');
        const { results: ings } = await env.DB.prepare(
          `SELECT recipe_id, name FROM recipe_ingredient WHERE recipe_id IN (${ph})`
        ).bind(...chunk).all();
        const byRecipe = new Map();
        for (const x of ings || []) {
          const norm = String(x.name || '').toLowerCase().trim();
          if (isJunk(norm)) continue;
          if (!byRecipe.has(x.recipe_id)) byRecipe.set(x.recipe_id, []);
          byRecipe.get(x.recipe_id).push(norm);
        }
        for (const rid of chunk) {
          const recipeIngs = byRecipe.get(rid) || [];
          if (recipeIngs.length < 3) continue;
          const missing = recipeIngs.filter(n => !pantryNames.has(n));
          if (missing.length === 0 || missing.length > 3) continue;
          for (const m of missing) {
            const rec = recipes.find(r => r.id === rid);
            if (!unlockMap.has(m)) unlockMap.set(m, { ingredient: m, count: 0, recipes: [] });
            const entry = unlockMap.get(m);
            entry.count++;
            if (entry.recipes.length < 3 && rec) entry.recipes.push({ id: rec.id, title: rec.title });
          }
        }
      }
    }
    const MIN_UNLOCK_COUNT = 5;
    const unlocks = [...unlockMap.values()]
      .filter(u => u.count >= MIN_UNLOCK_COUNT)   // no more 1-recipe junk suggestions
      .sort((a, b) => b.count - a.count)
      .slice(0, 5);

    return json({
      items: list || [],
      expiring: expiringItems,
      unlocks,
    }, 200, request, env);
  },

  /** Bulk delete — ?onlyChecked=1 clears only done items, otherwise wipes the whole list. */
  async clear(userId, env, request) {
    const rl = await enforce(env, 'write', userId);
    if (rl) return rl;
    const url = new URL(request.url);
    const onlyChecked = url.searchParams.get('onlyChecked') === '1';
    const res = onlyChecked
      ? await env.DB.prepare('DELETE FROM shopping_item WHERE user_id = ? AND checked = 1').bind(userId).run()
      : await env.DB.prepare('DELETE FROM shopping_item WHERE user_id = ?').bind(userId).run();
    return json({ ok: true, deleted: res?.meta?.changes || 0 }, 200, request, env);
  },

  async list(userId, env, request) {
    const rl = await enforce(env, 'read', userId);
    if (rl) return rl;
    const { results } = await env.DB.prepare(
      'SELECT id, name, quantity, unit, aisle, checked, source, source_plan_id, created_at FROM shopping_item WHERE user_id = ? ORDER BY aisle, created_at'
    ).bind(userId).all();
    const items = (results || []).map(r => ({ ...r, checked: !!r.checked }));
    return json({ items }, 200, request, env);
  },

  async add(request, userId, env) {
    const rl = await enforce(env, 'write', userId);
    if (rl) return rl;
    const p = await readJson(request, 4_000);
    if (p.error) return p.error;
    const { name, quantity, unit, aisle, source } = p.value;

    if (!validString(name, { min: 1, max: 80 })) return err(400, 'name: 1-80 chars');
    if (quantity != null && !validFiniteNumber(quantity, { min: 0, max: 10_000 })) return err(400, 'quantity: 0-10000');
    if (unit != null && !validString(unit, { min: 1, max: UNIT_MAX })) return err(400, `unit: <=${UNIT_MAX} chars`);
    if (aisle != null && !AISLE_ALLOW.has(aisle)) return err(400, 'aisle: unknown value');
    if (source != null && !SOURCE_ALLOW.has(source)) return err(400, 'source: unknown value');

    // Silently drop universal staples (salt/pepper/water). Every kitchen has
    // these — adding them to shopping is noise.
    if (isStaple(name)) return json({ ok: true, skipped: 'staple' }, 200, request, env);
    // Reject obvious garbage names (undefined / empty / pure numerals / single chars).
    const cleaned = String(name).toLowerCase().trim();
    if (!cleaned || cleaned === 'undefined' || cleaned === 'null' || cleaned.length < 2 || /^[\d\s./,&-]+$/.test(cleaned)) {
      return json({ ok: true, skipped: 'junk' }, 200, request, env);
    }

    const id = uid();
    await env.DB.prepare(
      'INSERT INTO shopping_item (id, user_id, name, quantity, unit, aisle, checked, source, created_at) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)'
    ).bind(id, userId, name, quantity ?? null, unit ?? null, aisle || 'other', source || 'manual', Date.now()).run();
    return json({ ok: true, id }, 200, request, env);
  },

  async update(id, request, userId, env) {
    if (!validOpaqueId(id)) return err(400, 'id invalid');
    const rl = await enforce(env, 'write', userId);
    if (rl) return rl;
    const p = await readJson(request, 2_000);
    if (p.error) return p.error;
    const body = p.value;

    const updates = [];
    if ('checked' in body) {
      if (!validBoolean(body.checked)) return err(400, 'checked: boolean');
      updates.push(['checked', body.checked ? 1 : 0]);
    }
    if ('name' in body) {
      if (!validString(body.name, { min: 1, max: 80 })) return err(400, 'name: 1-80 chars');
      updates.push(['name', body.name]);
    }
    if ('quantity' in body) {
      if (body.quantity != null && !validFiniteNumber(body.quantity, { min: 0, max: 10_000 })) return err(400, 'quantity: 0-10000');
      updates.push(['quantity', body.quantity ?? null]);
    }
    if ('unit' in body) {
      if (body.unit != null && !validString(body.unit, { min: 1, max: UNIT_MAX })) return err(400, 'unit invalid');
      updates.push(['unit', body.unit ?? null]);
    }
    if ('aisle' in body) {
      if (body.aisle != null && !AISLE_ALLOW.has(body.aisle)) return err(400, 'aisle: unknown');
      updates.push(['aisle', body.aisle ?? null]);
    }
    if (!updates.length) return err(400, 'nothing to update');

    const setClause = updates.map(([k]) => `${k} = ?`).join(', ');
    const values = updates.map(([, v]) => v);
    await env.DB.prepare(`UPDATE shopping_item SET ${setClause} WHERE id = ? AND user_id = ?`)
      .bind(...values, id, userId).run();
    return json({ ok: true }, 200, request, env);
  },

  async delete(id, userId, env, request) {
    if (!validOpaqueId(id)) return err(400, 'id invalid');
    await env.DB.prepare('DELETE FROM shopping_item WHERE id = ? AND user_id = ?').bind(id, userId).run();
    return json({ ok: true }, 200, request, env);
  },
};

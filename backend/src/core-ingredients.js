// CORE ingredients algorithm.
// Identify items a household consistently keeps in their pantry over multiple weeks.
// Auto-suggest restock to shopping list when a CORE item runs low or expires.
//
// Logic: every time we see an item in a user's pantry, stamp a weekly "seen" counter.
// If an item is seen ≥3 distinct weeks in the last 6 weeks, mark it CORE.

import { json, err } from './util.js';
import { enforce } from './ratelimit.js';

const WEEK_MS = 7 * 86400_000;
const LOOKBACK_WEEKS = 6;
const CORE_THRESHOLD = 3;

/** Stamp the current week for each pantry item name. Called on pantry list or scan. */
export async function stampPantryWeek(env, userId) {
  if (!env.DB) return;
  const { results: pantry } = await env.DB.prepare(
    'SELECT name FROM pantry_item WHERE user_id = ?'
  ).bind(userId).all();
  if (!pantry?.length) return;
  const weekId = Math.floor(Date.now() / WEEK_MS);
  for (const p of pantry) {
    const name = String(p.name || '').toLowerCase().trim();
    if (!name) continue;
    await env.DB.prepare(
      'INSERT OR IGNORE INTO pantry_week_seen (user_id, ingredient, week_id, created_at) VALUES (?, ?, ?, ?)'
    ).bind(userId, name, weekId, Date.now()).run();
  }
}

/** Compute CORE ingredients + suggest restocks. */
export const handleCoreIngredients = {
  async get(userId, env, request) {
    const rl = await enforce(env, 'read', userId);
    if (rl) return rl;

    const nowWeek = Math.floor(Date.now() / WEEK_MS);
    const earliestWeek = nowWeek - LOOKBACK_WEEKS;

    // Count distinct weeks each ingredient appeared
    const { results } = await env.DB.prepare(
      `SELECT ingredient, COUNT(DISTINCT week_id) AS weeks
       FROM pantry_week_seen
       WHERE user_id = ? AND week_id > ?
       GROUP BY ingredient
       HAVING weeks >= ?
       ORDER BY weeks DESC, ingredient ASC`
    ).bind(userId, earliestWeek, CORE_THRESHOLD).all();

    const coreIngredients = (results || []).map(r => r.ingredient);

    // Which CORE ingredients are NOT currently in pantry or running low?
    const { results: currentPantry } = await env.DB.prepare(
      'SELECT name, quantity, expires_at FROM pantry_item WHERE user_id = ?'
    ).bind(userId).all();
    const now = Date.now();
    const pantryByName = new Map();
    for (const p of currentPantry || []) {
      pantryByName.set(String(p.name || '').toLowerCase().trim(), p);
    }

    const restockSuggestions = [];
    for (const ing of coreIngredients) {
      const p = pantryByName.get(ing);
      if (!p) {
        restockSuggestions.push({ ingredient: ing, reason: 'missing' });
        continue;
      }
      if (p.quantity != null && Number(p.quantity) <= 0.5) {
        restockSuggestions.push({ ingredient: ing, reason: 'low' });
        continue;
      }
      if (p.expires_at) {
        const ts = /^\d{10,}$/.test(p.expires_at) ? parseInt(p.expires_at, 10) : Date.parse(p.expires_at);
        if (Number.isFinite(ts) && ts - now < 3 * 86400_000) {
          restockSuggestions.push({ ingredient: ing, reason: 'expiring' });
        }
      }
    }

    // Skip items already on the shopping list (unchecked)
    if (restockSuggestions.length) {
      const names = restockSuggestions.map(s => s.ingredient);
      const ph = names.map(() => '?').join(',');
      const { results: onList } = await env.DB.prepare(
        `SELECT LOWER(name) AS n FROM shopping_item WHERE user_id = ? AND checked = 0 AND LOWER(name) IN (${ph})`
      ).bind(userId, ...names).all();
      const onListSet = new Set((onList || []).map(x => x.n));
      for (const s of restockSuggestions) s.alreadyOnList = onListSet.has(s.ingredient);
    }

    return json({
      coreIngredients,
      restockSuggestions: restockSuggestions.filter(s => !s.alreadyOnList).slice(0, 10),
      analyzedWeeks: LOOKBACK_WEEKS,
      threshold: CORE_THRESHOLD,
    }, 200, request, env);
  },
};

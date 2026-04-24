// Waste tracker + $ saved summary. "Cooked in time" counts as savings; "wasted" as loss.
// Price estimation is conservative (under-promises) using the expiry.js price map.

import { json, err, readJson, uid, validString, validFiniteNumber, validOpaqueId } from './util.js';
import { enforce } from './ratelimit.js';
import { estimatePriceUsd } from './expiry.js';

const ACTION_ALLOW = new Set(['cooked','wasted','expired','consumed','donated']);
const CATEGORY_ALLOW = new Set(['produce','protein','dairy','grain','pantry','spice','condiment','frozen','beverage','bakery','deli','other']);

export const handleWaste = {
  /** POST /waste/log  { itemId?, name, category, quantity, unit?, action } */
  async log(request, userId, env) {
    const rl = await enforce(env, 'write', userId);
    if (rl) return rl;
    const p = await readJson(request, 2_000);
    if (p.error) return p.error;
    const b = p.value;

    if (b.itemId && !validOpaqueId(b.itemId)) return err(400, 'itemId invalid');
    if (!validString(b.name, { min: 1, max: 80 })) return err(400, 'name: 1-80');
    if (!CATEGORY_ALLOW.has(b.category)) return err(400, 'category: unknown');
    if (b.quantity != null && !validFiniteNumber(b.quantity, { min: 0, max: 10_000 })) return err(400, 'quantity: 0-10000');
    if (!ACTION_ALLOW.has(b.action)) return err(400, 'action: unknown');

    const id = uid();
    const estCost = estimatePriceUsd(b.name, b.category, b.quantity ?? 1);

    await env.DB.prepare(
      'INSERT INTO waste_event (id, user_id, item_id, name, category, quantity, unit, action, est_cost_usd, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'
    ).bind(
      id, userId, b.itemId ?? null,
      b.name, b.category,
      b.quantity ?? null,
      typeof b.unit === 'string' ? b.unit.slice(0, 20) : null,
      b.action,
      estCost, Date.now(),
    ).run();

    // Remove from pantry if the event "consumes" the item.
    if (b.itemId && ['cooked','consumed','donated','wasted','expired'].includes(b.action)) {
      await env.DB.prepare('DELETE FROM pantry_item WHERE id = ? AND user_id = ?').bind(b.itemId, userId).run();
    }

    return json({ ok: true, id, estCostUsd: estCost }, 200, request, env);
  },

  /** GET /waste/summary?range=week|month */
  async summary(request, userId, env) {
    const rl = await enforce(env, 'read', userId);
    if (rl) return rl;
    const url = new URL(request.url);
    const range = url.searchParams.get('range') === 'month' ? 'month' : 'week';
    const cutoff = range === 'month' ? Date.now() - 30 * 86400_000 : Date.now() - 7 * 86400_000;

    const { results } = await env.DB.prepare(
      'SELECT action, est_cost_usd, category FROM waste_event WHERE user_id = ? AND created_at >= ?'
    ).bind(userId, cutoff).all();

    let saved = 0, wasted = 0, cookedCount = 0, wastedCount = 0;
    const byCategory = {};
    for (const r of results || []) {
      const cost = Number(r.est_cost_usd) || 0;
      if (r.action === 'cooked' || r.action === 'consumed' || r.action === 'donated') {
        saved += cost; cookedCount++;
      } else if (r.action === 'wasted' || r.action === 'expired') {
        wasted += cost; wastedCount++;
      }
      byCategory[r.category] = (byCategory[r.category] || 0) + cost;
    }
    return json({
      range,
      savedUsd: Math.round(saved * 100) / 100,
      wastedUsd: Math.round(wasted * 100) / 100,
      netUsd: Math.round((saved - wasted) * 100) / 100,
      cookedCount,
      wastedCount,
      byCategory,
      since: cutoff,
    }, 200, request, env);
  },

  /** GET /waste/history (paginated) */
  async history(request, userId, env) {
    const rl = await enforce(env, 'read', userId);
    if (rl) return rl;
    const url = new URL(request.url);
    const limit = Math.min(100, Math.max(1, parseInt(url.searchParams.get('limit') || '50', 10)));
    const { results } = await env.DB.prepare(
      'SELECT id, name, category, quantity, unit, action, est_cost_usd, created_at FROM waste_event WHERE user_id = ? ORDER BY created_at DESC LIMIT ?'
    ).bind(userId, limit).all();
    return json({ events: results || [] }, 200, request, env);
  },
};

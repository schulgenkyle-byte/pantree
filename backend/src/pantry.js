import {
  json, err, readJson, uid, validOpaqueId, validString, validStringOrNull,
  validFiniteNumber, validArray,
} from './util.js';
import { enforce } from './ratelimit.js';
import { estimateExpiryDays, suggestExpiresAt } from './expiry.js';
import { stampPantryWeek } from './core-ingredients.js';
import { canonicalize } from './canonicalize.js';
import { getPreferencesFor, prefHash } from './preferences.js';

const CATEGORY_ALLOW = new Set(['produce','protein','dairy','grain','pantry','spice','condiment','frozen','beverage','bakery','deli','other']);
const UNIT_ALLOW = new Set(['count','lb','oz','g','kg','ml','l','bunch','head','can','bottle','jar','bag','pack','box','cup','tbsp','tsp']);

function validUnit(v) { return v == null || (typeof v === 'string' && UNIT_ALLOW.has(v)); }
function validExpiry(v) {
  if (v == null) return true;
  if (typeof v !== 'string' || v.length > 32) return false;
  // ISO-ish YYYY-MM-DD or an integer millis timestamp as string
  return /^\d{4}-\d{2}-\d{2}(T.*)?$/.test(v) || /^\d{10,13}$/.test(v);
}

export const handlePantry = {
  async list(userId, env, request) {
    const rl = await enforce(env, 'read', userId);
    if (rl) return rl;
    const { results } = await env.DB.prepare(
      'SELECT id, name, category, quantity, unit, expires_at, created_at FROM pantry_item WHERE user_id = ? ORDER BY created_at DESC'
    ).bind(userId).all();
    // Stamp week-seen for CORE-ingredient analysis. Non-blocking, best-effort.
    stampPantryWeek(env, userId).catch(() => {});
    return json({ items: results || [] }, 200, request, env);
  },

  /** Clear per-user deck cache so the next /recipes/deck picks up new pantry state.
   * Cache key shape: `deck:<userId>:<prefHash>:<day>`. We also delete the legacy
   * `deck-cache:<userId>:<day>` key for older deploys. Fail-open on KV errors. */
  async _bustDeckCache(env, userId) {
    if (!env.RATE_LIMIT_KV) return;
    const day = Math.floor(Date.now() / 86400_000);
    try {
      const prefs = await getPreferencesFor(userId, env).catch(() => null);
      const prefsKey = prefs ? prefHash(prefs) : 'np';
      await env.RATE_LIMIT_KV.delete(`deck:${userId}:${prefsKey}:${day}`);
      await env.RATE_LIMIT_KV.delete(`deck-cache:${userId}:${day}`);
    } catch (e) {
      console.warn('deck cache bust failed (ignored):', e?.message);
    }
  },

  async add(request, userId, env) {
    const rl = await enforce(env, 'write', userId);
    if (rl) return rl;
    const p = await readJson(request, 8_000);
    if (p.error) return p.error;
    const b = p.value;

    if (!validString(b.name, { min: 1, max: 80 })) return err(400, 'name: 1-80 chars');
    if (b.category != null && !CATEGORY_ALLOW.has(b.category)) return err(400, 'category: unknown');
    if (b.quantity != null && !validFiniteNumber(b.quantity, { min: 0, max: 10_000 })) return err(400, 'quantity: 0-10000');
    if (!validUnit(b.unit)) return err(400, 'unit: unknown');
    if (!validExpiry(b.expiresAt)) return err(400, 'expiresAt: ISO date or epoch ms string');

    const category = b.category || 'other';
    const expiresAt = b.expiresAt ?? new Date(suggestExpiresAt(b.name, category)).toISOString().slice(0, 10);

    const id = uid();
    await env.DB.prepare(
      'INSERT INTO pantry_item (id, user_id, name, canonical_name, category, quantity, unit, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)'
    ).bind(id, userId, b.name, canonicalize(b.name), category, b.quantity ?? null, b.unit ?? null, expiresAt, Date.now()).run();
    await handlePantry._bustDeckCache(env, userId);
    return json({ ok: true, id, expiresAt }, 200, request, env);
  },

  async addBulk(request, userId, env) {
    const rl = await enforce(env, 'write', userId);
    if (rl) return rl;
    const p = await readJson(request, 256_000);
    if (p.error) return p.error;
    if (!validArray(p.value.items, 500)) return err(400, 'items: 1-500 per batch');
    const now = Date.now();
    const added = [];
    for (const b of p.value.items) {
      if (!validString(b.name, { min: 1, max: 80 })) continue;
      if (b.category != null && !CATEGORY_ALLOW.has(b.category)) continue;
      if (b.quantity != null && !validFiniteNumber(b.quantity, { min: 0, max: 10_000 })) continue;
      if (!validUnit(b.unit)) continue;
      if (!validExpiry(b.expiresAt)) continue;
      const category = b.category || 'other';
      const expiresAt = b.expiresAt ?? new Date(suggestExpiresAt(b.name, category)).toISOString().slice(0, 10);
      const id = uid();
      await env.DB.prepare(
        'INSERT INTO pantry_item (id, user_id, name, canonical_name, category, quantity, unit, expires_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)'
      ).bind(id, userId, b.name, canonicalize(b.name), category, b.quantity ?? null, b.unit ?? null, expiresAt, now).run();
      added.push({ id, name: b.name, expiresAt });
    }
    await handlePantry._bustDeckCache(env, userId);
    return json({ ok: true, added }, 200, request, env);
  },

  async update(id, request, userId, env) {
    if (!validOpaqueId(id)) return err(400, 'id invalid');
    const rl = await enforce(env, 'write', userId);
    if (rl) return rl;
    const p = await readJson(request, 2_000);
    if (p.error) return p.error;
    const b = p.value;

    const updates = [];
    if ('name' in b) {
      if (!validString(b.name, { min: 1, max: 80 })) return err(400, 'name: 1-80 chars');
      updates.push(['name', b.name]);
    }
    if ('category' in b) {
      if (b.category != null && !CATEGORY_ALLOW.has(b.category)) return err(400, 'category: unknown');
      updates.push(['category', b.category ?? null]);
    }
    if ('quantity' in b) {
      if (b.quantity != null && !validFiniteNumber(b.quantity, { min: 0, max: 10_000 })) return err(400, 'quantity: 0-10000');
      updates.push(['quantity', b.quantity ?? null]);
    }
    if ('unit' in b) {
      if (!validUnit(b.unit)) return err(400, 'unit: unknown');
      updates.push(['unit', b.unit ?? null]);
    }
    if ('expiresAt' in b) {
      if (!validExpiry(b.expiresAt)) return err(400, 'expiresAt invalid');
      updates.push(['expires_at', b.expiresAt ?? null]);
    }
    if (!updates.length) return err(400, 'nothing to update');

    const setClause = updates.map(([k]) => `${k} = ?`).join(', ');
    const values = updates.map(([, v]) => v);
    await env.DB.prepare(`UPDATE pantry_item SET ${setClause} WHERE id = ? AND user_id = ?`)
      .bind(...values, id, userId).run();
    return json({ ok: true }, 200, request, env);
  },

  async delete(id, userId, env, request) {
    if (!validOpaqueId(id)) return err(400, 'id invalid');
    await env.DB.prepare('DELETE FROM pantry_item WHERE id = ? AND user_id = ?').bind(id, userId).run();
    return json({ ok: true }, 200, request, env);
  },
};

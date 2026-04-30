import {
  json, err, readJson, validString, validStringOrNull, validArray, validOpaqueId,
} from './util.js';
import { verifyReauthToken, revokeJti } from './auth.js';
import { enforce } from './ratelimit.js';

const DIET_ALLOW = new Set(['None','Vegetarian','Vegan','Pescatarian','Keto','Paleo','Low-FODMAP','Halal','Kosher','Gluten-Free','Dairy-Free']);
const SKILL_ALLOW = new Set(['beginner','intermediate','advanced']);
// Short, structured allergen slugs only. Keeps DB small and avoids junk input.
const ALLERGEN_PATTERN = /^[a-z][a-z0-9_-]{0,31}$/;

export const handleUsers = {
  async me(userId, env, request) {
    const u = await env.DB.prepare(
      'SELECT id, email, display_name, bio, diet, skill_level, created_at FROM user WHERE id = ?'
    ).bind(userId).first();
    if (!u) return err(404, 'user not found');
    const allergies = await env.DB.prepare('SELECT allergen FROM user_allergy WHERE user_id = ?').bind(userId).all();
    return json({
      id: u.id,
      email: u.email,
      displayName: u.display_name,
      bio: u.bio,
      diet: u.diet,
      skillLevel: u.skill_level,
      allergies: (allergies.results || []).map(r => r.allergen),
    }, 200, request, env);
  },

  async update(request, userId, env) {
    const rl = await enforce(env, 'write', userId);
    if (rl) return rl;
    const p = await readJson(request, 10_000);
    if (p.error) return p.error;
    const body = p.value;

    const updates = [];
    if ('display_name' in body || 'displayName' in body) {
      const v = body.display_name ?? body.displayName;
      if (!validString(v, { min: 1, max: 60 })) return err(400, 'displayName: 1-60 chars');
      updates.push(['display_name', v]);
    }
    if ('bio' in body) {
      if (!validStringOrNull(body.bio, { max: 280 })) return err(400, 'bio: <=280 chars');
      updates.push(['bio', body.bio]);
    }
    if ('diet' in body) {
      if (!DIET_ALLOW.has(body.diet)) return err(400, 'diet: unknown value');
      updates.push(['diet', body.diet]);
    }
    if ('skill_level' in body || 'skillLevel' in body) {
      const v = body.skill_level ?? body.skillLevel;
      if (!SKILL_ALLOW.has(v)) return err(400, 'skillLevel: unknown value');
      updates.push(['skill_level', v]);
    }

    let didUpdate = false;
    if (updates.length) {
      const setClause = updates.map(([k]) => `${k} = ?`).join(', ');
      const values = updates.map(([, v]) => v);
      await env.DB.prepare(`UPDATE user SET ${setClause}, updated_at = ? WHERE id = ?`).bind(...values, Date.now(), userId).run();
      didUpdate = true;
    }

    if (Array.isArray(body.allergies)) {
      if (!validArray(body.allergies, 20)) return err(400, 'allergies: max 20');
      for (const a of body.allergies) {
        if (typeof a !== 'string' || !ALLERGEN_PATTERN.test(a)) {
          return err(400, 'allergen: lowercase slug 1-32 chars');
        }
      }
      await env.DB.prepare('DELETE FROM user_allergy WHERE user_id = ?').bind(userId).run();
      for (const a of body.allergies) {
        await env.DB.prepare('INSERT OR IGNORE INTO user_allergy (user_id, allergen) VALUES (?, ?)').bind(userId, a).run();
      }
      didUpdate = true;
    }

    if (!didUpdate) return err(400, 'nothing to update');
    return json({ ok: true }, 200, request, env);
  },

  async delete(request, userId, env, authPayload) {
    const rl = await enforce(env, 'write', userId);
    if (rl) return rl;

    const p = await readJson(request, 4_000);
    if (p.error) return p.error;
    const { reauthToken } = p.value;
    if (!validString(reauthToken, { min: 20, max: 4096 })) {
      return err(403, 're-auth required');
    }
    const ok = await verifyReauthToken(env, userId, reauthToken);
    if (!ok) return err(403, 're-auth required');

    // Drop all sessions and revoke current access JTI so tokens can't continue to authorize.
    await env.DB.prepare('DELETE FROM session WHERE user_id = ?').bind(userId).run();
    if (authPayload?.jti) await revokeJti(env, authPayload.jti, authPayload.exp);

    // R2 SWEEP: collect every photo URL this user owns BEFORE we drop the rows that
    // reference them (otherwise we lose the keys). Public-bucket photos linger forever
    // unless we explicitly delete them — GDPR/CCPA right-to-erasure.
    const photoKeys = new Set();
    const publicBase = (env.PHOTOS_PUBLIC_BASE || '').replace(/\/$/, '');
    const collectFromUrl = (raw) => {
      if (!raw || typeof raw !== 'string') return;
      // Trust the per-user prefix — we only ever wrote keys under submissions/<userId>/
      // so a key extracted from a URL we own is safe to delete.
      if (publicBase && raw.startsWith(publicBase + '/')) {
        photoKeys.add(raw.slice(publicBase.length + 1));
      }
    };
    try {
      const subs = await env.DB.prepare('SELECT image_url FROM recipe_submission WHERE user_id = ?').bind(userId).all();
      for (const r of subs?.results || []) collectFromUrl(r.image_url);
    } catch { /* table may not exist yet on fresh deploy */ }
    try {
      const reviewPhotos = await env.DB.prepare(
        'SELECT photo_url FROM review_photo rp JOIN review r ON rp.review_id = r.id WHERE r.user_id = ?'
      ).bind(userId).all();
      for (const r of reviewPhotos?.results || []) collectFromUrl(r.photo_url);
    } catch { /* schema may differ — fail-open, photos can be reaped manually */ }

    if (env.PHOTOS_BUCKET && photoKeys.size > 0) {
      // R2 has a delete-many API but the bindings vary by runtime. Loop with catch — one
      // bad key shouldn't block the user-delete; the row drop is the primary erasure.
      for (const k of photoKeys) {
        await env.PHOTOS_BUCKET.delete(k).catch((e) => console.warn('R2 delete failed', k, e?.message));
      }
    }

    // Then cascade user data. Schema defines ON DELETE CASCADE for FK-bound tables;
    // we still explicitly delete for safety in case FKs aren't enforced.
    const tables = [
      'user_allergy',
      'pantry_item',
      'interaction',
      'review',
      'review_photo',
      'report',
      'shopping_item',
      'plan',
      'follow',
      'block',
      'scan_history',
      'entitlement',
      'recipe_submission',
    ];
    for (const t of tables) {
      await env.DB.prepare(`DELETE FROM ${t} WHERE user_id = ?`).bind(userId).run().catch(() => {});
    }
    // Also follows where this user is the target
    await env.DB.prepare('DELETE FROM follow WHERE followed_user_id = ?').bind(userId).run().catch(() => {});
    await env.DB.prepare('DELETE FROM block WHERE blocked_user_id = ?').bind(userId).run().catch(() => {});

    // Library cascade — explicit delete in dependency order. FKs declare ON DELETE
    // CASCADE but the rest of this function intentionally doesn't trust FKs (see
    // comment around line 132). GDPR right-to-erasure: every Library Book the user
    // owned, including public/unlisted ones that surfaced on speakeater.com/b/<id>,
    // is destroyed — recipe references on community forks survive only as orphan
    // copies in the forker's library, not as anything traceable to this account.
    await env.DB.prepare(
      `DELETE FROM chapter_recipe WHERE chapter_id IN (
         SELECT c.id FROM chapter c JOIN book b ON b.id = c.book_id
         WHERE b.library_user_id = ?
       )`
    ).bind(userId).run().catch(() => {});
    await env.DB.prepare(
      `DELETE FROM book_export_log WHERE book_id IN (
         SELECT id FROM book WHERE library_user_id = ?
       )`
    ).bind(userId).run().catch(() => {});
    await env.DB.prepare(
      `DELETE FROM chapter WHERE book_id IN (
         SELECT id FROM book WHERE library_user_id = ?
       )`
    ).bind(userId).run().catch(() => {});
    await env.DB.prepare('DELETE FROM book WHERE library_user_id = ?').bind(userId).run().catch(() => {});
    await env.DB.prepare('DELETE FROM library WHERE user_id = ?').bind(userId).run().catch(() => {});

    await env.DB.prepare('DELETE FROM user WHERE id = ?').bind(userId).run();
    return json({ ok: true, deleted: true }, 200, request, env);
  },

  /**
   * Lightweight home-screen summary: last scan age, expiring-soon counts,
   * savings-this-week, Pro tier flag. One trip to the DB, drives the nudge banner.
   */
  async home(userId, env, request) {
    const rl = await enforce(env, 'read', userId);
    if (rl) return rl;

    const lastScan = await env.DB.prepare(
      'SELECT created_at FROM scan_history WHERE user_id = ? ORDER BY created_at DESC LIMIT 1'
    ).bind(userId).first();

    const { results: pantry } = await env.DB.prepare(
      'SELECT expires_at FROM pantry_item WHERE user_id = ?'
    ).bind(userId).all();
    const now = Date.now();
    let pantryCount = 0, expiringSoon = 0, expired = 0;
    for (const p of pantry || []) {
      pantryCount++;
      if (!p.expires_at) continue;
      const ts = /^\d{10,}$/.test(p.expires_at) ? parseInt(p.expires_at, 10) : Date.parse(p.expires_at);
      if (!Number.isFinite(ts)) continue;
      const delta = ts - now;
      if (delta < 0) expired++;
      else if (delta < 3 * 86400_000) expiringSoon++;
    }

    const weekAgo = now - 7 * 86400_000;
    const savedRow = await env.DB.prepare(
      "SELECT COALESCE(SUM(est_cost_usd), 0) AS saved FROM waste_event WHERE user_id = ? AND action IN ('cooked','consumed','donated') AND created_at >= ?"
    ).bind(userId, weekAgo).first();

    const ent = await env.DB.prepare(
      'SELECT sku, expires_at FROM entitlement WHERE user_id = ? AND expires_at > ?'
    ).bind(userId, now).first();

    const daysSinceScan = lastScan ? Math.floor((now - lastScan.created_at) / 86400_000) : null;
    return json({
      pantryCount,
      expiringSoon,
      expired,
      daysSinceScan,
      savedThisWeekUsd: Math.round((savedRow?.saved || 0) * 100) / 100,
      tier: ent ? 'pro' : 'free',
      scanNudge: daysSinceScan == null
        ? { show: true, message: 'Snap your shelves to get started.' }
        : (daysSinceScan >= 5
          ? { show: true, message: `It's been ${daysSinceScan} days — re-scan your fridge?` }
          : { show: false }),
      expiringNudge: expiringSoon > 0
        ? { show: true, message: `${expiringSoon} item${expiringSoon === 1 ? '' : 's'} expiring in 3 days.` }
        : { show: false },
    }, 200, request, env);
  },

  async export(userId, env, request) {
    const rl = await enforce(env, 'read', userId);
    if (rl) return rl;

    // Curated column lists — never SELECT * in exports.
    const user = await env.DB.prepare(
      'SELECT id, email, display_name, bio, diet, skill_level, created_at, updated_at FROM user WHERE id = ?'
    ).bind(userId).first();
    const pantry = await env.DB.prepare(
      'SELECT id, name, category, quantity, unit, expires_at, created_at FROM pantry_item WHERE user_id = ?'
    ).bind(userId).all();
    const interactions = await env.DB.prepare(
      'SELECT recipe_id, status, dismiss_reason, created_at FROM interaction WHERE user_id = ?'
    ).bind(userId).all();
    const reviews = await env.DB.prepare(
      'SELECT id, recipe_id, rating_pots, rating_taste, rating_ease, notes, cook_again, is_public, created_at FROM review WHERE user_id = ?'
    ).bind(userId).all();
    const plans = await env.DB.prepare(
      'SELECT id, name, recipe_ids, created_at FROM plan WHERE user_id = ?'
    ).bind(userId).all();
    const shopping = await env.DB.prepare(
      'SELECT id, name, quantity, unit, aisle, checked, source, created_at FROM shopping_item WHERE user_id = ?'
    ).bind(userId).all();
    const follows = await env.DB.prepare(
      'SELECT followed_user_id, created_at FROM follow WHERE user_id = ?'
    ).bind(userId).all();
    const allergies = await env.DB.prepare(
      'SELECT allergen FROM user_allergy WHERE user_id = ?'
    ).bind(userId).all();

    return json({
      exportedAt: Date.now(),
      user,
      allergies: (allergies.results || []).map(r => r.allergen),
      pantry: pantry.results,
      interactions: interactions.results,
      reviews: reviews.results,
      plans: plans.results,
      shopping: shopping.results,
      follows: follows.results,
    }, 200, request, env);
  },
};

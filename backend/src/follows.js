import { json, err, readJson, uid, validOpaqueId } from './util.js';
import { enforce } from './ratelimit.js';

export const handleFollows = {
  async follow(targetId, userId, env, request) {
    if (!validOpaqueId(targetId)) return err(400, 'target invalid');
    if (targetId === userId) return err(400, 'cannot follow self');

    // Anti-mass-follow: hourly cap
    const rl = await enforce(env, 'follow', userId);
    if (rl) return rl;

    // Target user must exist
    const target = await env.DB.prepare('SELECT id FROM user WHERE id = ?').bind(targetId).first();
    if (!target) return err(404, 'target not found');

    // Respect blocks both directions
    const blocked = await env.DB.prepare(
      'SELECT 1 FROM block WHERE (user_id = ? AND blocked_user_id = ?) OR (user_id = ? AND blocked_user_id = ?)'
    ).bind(userId, targetId, targetId, userId).first();
    if (blocked) return err(403, 'blocked');

    await env.DB.prepare(
      'INSERT OR IGNORE INTO follow (id, user_id, followed_user_id, created_at) VALUES (?, ?, ?, ?)'
    ).bind(uid(), userId, targetId, Date.now()).run();
    return json({ ok: true }, 200, request, env);
  },

  async unfollow(targetId, userId, env, request) {
    if (!validOpaqueId(targetId)) return err(400, 'target invalid');
    await env.DB.prepare('DELETE FROM follow WHERE user_id = ? AND followed_user_id = ?').bind(userId, targetId).run();
    return json({ ok: true }, 200, request, env);
  },

  async block(targetId, userId, env, request) {
    if (!validOpaqueId(targetId)) return err(400, 'target invalid');
    if (targetId === userId) return err(400, 'cannot block self');
    await env.DB.prepare('INSERT OR IGNORE INTO block (id, user_id, blocked_user_id, created_at) VALUES (?, ?, ?, ?)')
      .bind(uid(), userId, targetId, Date.now()).run();
    // Drop follow edges both ways when a block is created
    await env.DB.prepare('DELETE FROM follow WHERE (user_id = ? AND followed_user_id = ?) OR (user_id = ? AND followed_user_id = ?)')
      .bind(userId, targetId, targetId, userId).run();
    return json({ ok: true }, 200, request, env);
  },

  async unblock(targetId, userId, env, request) {
    if (!validOpaqueId(targetId)) return err(400, 'target invalid');
    await env.DB.prepare('DELETE FROM block WHERE user_id = ? AND blocked_user_id = ?').bind(userId, targetId).run();
    return json({ ok: true }, 200, request, env);
  },
};

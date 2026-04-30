// Beta signup capture — public POST endpoint that brimmapp.com can hit anonymously.
// Stores email + source so we can copy/paste into Play Console Internal Testing.

import { json, err } from './util.js';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/i;

function adminAuthed(request, env) {
  const url = new URL(request.url);
  const key = url.searchParams.get('key') || '';
  if (!env.ADMIN_KEY || !key || key.length !== env.ADMIN_KEY.length) return false;
  let x = 0;
  for (let i = 0; i < key.length; i++) x |= key.charCodeAt(i) ^ env.ADMIN_KEY.charCodeAt(i);
  return x === 0;
}

async function ensureTable(env) {
  await env.DB.prepare(
    `CREATE TABLE IF NOT EXISTS beta_signup (
       id INTEGER PRIMARY KEY AUTOINCREMENT,
       email TEXT NOT NULL UNIQUE COLLATE NOCASE,
       source TEXT NOT NULL DEFAULT 'landing',
       intent TEXT NOT NULL DEFAULT 'beta',
       added_to_play INTEGER NOT NULL DEFAULT 0,
       created_at INTEGER NOT NULL,
       ip_hash TEXT,
       user_agent TEXT
     )`
  ).run();
  await env.DB.prepare(
    `CREATE INDEX IF NOT EXISTS idx_beta_signup_added ON beta_signup (added_to_play, created_at)`
  ).run();
}

export const handleSignups = {
  async create(request, env) {
    await ensureTable(env);
    let body;
    try {
      body = await request.json();
    } catch {
      return err(400, 'invalid json');
    }
    const email = String(body.email || '').trim().toLowerCase();
    const intent = body.intent === 'notify' ? 'notify' : 'beta';
    const source = String(body.source || 'landing').slice(0, 60);

    if (!email || !EMAIL_RE.test(email) || email.length > 200) {
      return err(400, 'valid email required');
    }

    const ua = (request.headers.get('user-agent') || '').slice(0, 200);
    const ipRaw = request.headers.get('cf-connecting-ip') || '';
    let ipHash = null;
    if (ipRaw) {
      const enc = new TextEncoder().encode(ipRaw + (env.JWT_SECRET || ''));
      const hash = await crypto.subtle.digest('SHA-256', enc);
      ipHash = Array.from(new Uint8Array(hash)).slice(0, 8).map(b => b.toString(16).padStart(2, '0')).join('');
    }

    try {
      await env.DB.prepare(
        `INSERT INTO beta_signup (email, source, intent, created_at, ip_hash, user_agent)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6)
         ON CONFLICT(email) DO UPDATE SET
           intent = CASE WHEN excluded.intent = 'beta' THEN 'beta' ELSE beta_signup.intent END,
           source = excluded.source`
      ).bind(email, source, intent, Date.now(), ipHash, ua).run();
    } catch (e) {
      console.error('signup insert failed', e);
      return err(500, 'storage failed');
    }

    return json({ ok: true, intent, message: intent === 'beta' ? 'You are on the beta list. Watch your email for the invite.' : 'You are on the launch list. We will email you on May 10.' }, 200, request, env);
  },

  async list(request, env) {
    if (!adminAuthed(request, env)) return err(401, 'bad admin key');
    await ensureTable(env);
    const rows = await env.DB.prepare(
      `SELECT id, email, source, intent, added_to_play, created_at FROM beta_signup ORDER BY created_at DESC LIMIT 500`
    ).all();
    const stats = await env.DB.prepare(
      `SELECT
         COUNT(*) AS total,
         SUM(CASE WHEN intent='beta' THEN 1 ELSE 0 END) AS beta_total,
         SUM(CASE WHEN intent='notify' THEN 1 ELSE 0 END) AS notify_total,
         SUM(CASE WHEN intent='beta' AND added_to_play=0 THEN 1 ELSE 0 END) AS beta_pending
       FROM beta_signup`
    ).first();
    return json({ ok: true, stats, signups: rows.results || [] }, 200, request, env);
  },

  async markAdded(request, env) {
    if (!adminAuthed(request, env)) return err(401, 'bad admin key');
    let body;
    try { body = await request.json(); } catch { return err(400, 'invalid json'); }
    const ids = Array.isArray(body.ids) ? body.ids.filter(Number.isFinite) : [];
    if (ids.length === 0) return err(400, 'ids[] required');
    const placeholders = ids.map(() => '?').join(',');
    await env.DB.prepare(`UPDATE beta_signup SET added_to_play=1 WHERE id IN (${placeholders})`).bind(...ids).run();
    return json({ ok: true, updated: ids.length }, 200, request, env);
  },
};

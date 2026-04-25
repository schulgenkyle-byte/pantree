// Beta testing: feedback ingest + anonymized activity ticker.
// Feedback writes to D1 `beta_feedback` and fires a Discord webhook (if BETA_DISCORD_WEBHOOK is set).
// Activity reads recent cook/save interactions, anonymized.

import { json, err, readJson, uid, validString, validStringOrNull, isNonEmptyString } from './util.js';
import { enforce } from './ratelimit.js';

const ALLOWED_KINDS = new Set(['bug', 'idea', 'praise', 'other']);
const ALLOWED_SEVERITY = new Set(['low', 'med', 'high', 'crash']);

async function postDiscord(env, payload) {
  const url = env.BETA_DISCORD_WEBHOOK;
  if (!url) return;
  try {
    // Fire-and-forget with a short timeout so the request path isn't blocked.
    const ctrl = new AbortController();
    const t = setTimeout(() => ctrl.abort(), 2500);
    await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      signal: ctrl.signal,
    });
    clearTimeout(t);
  } catch (e) {
    console.warn('discord webhook failed', e?.message);
  }
}

function truncate(s, n) {
  if (typeof s !== 'string') return s;
  return s.length > n ? s.slice(0, n) + '…' : s;
}

export const handleBeta = {
  async feedback(request, userId, env) {
    const limited = await enforce(env, 'beta_feedback', userId);
    if (limited) return limited;

    const { value, error } = await readJson(request, 32_000);
    if (error) return error;

    const kind = (value.kind || 'other').toString().toLowerCase();
    if (!ALLOWED_KINDS.has(kind)) return err(400, 'bad kind');
    if (!isNonEmptyString(value.title, 120)) return err(400, 'title required (<=120)');
    if (!validStringOrNull(value.body, { max: 4000 })) return err(400, 'body too long');
    if (!validStringOrNull(value.route, { max: 64 })) return err(400, 'route too long');
    if (!validStringOrNull(value.appVersion, { max: 32 })) return err(400, 'appVersion too long');
    if (!validStringOrNull(value.device, { max: 128 })) return err(400, 'device too long');
    if (!validStringOrNull(value.logs, { max: 8000 })) return err(400, 'logs too long');
    const severity = value.severity ? value.severity.toString() : null;
    if (severity && !ALLOWED_SEVERITY.has(severity)) return err(400, 'bad severity');

    const id = uid();
    const now = Date.now();
    await env.DB.prepare(
      `INSERT INTO beta_feedback (id,user_id,kind,title,body,route,app_version,device,logs,severity,status,created_at)
       VALUES (?,?,?,?,?,?,?,?,?,?, 'open', ?)`
    ).bind(
      id, userId, kind, value.title.trim(),
      value.body ? value.body.toString().trim() : null,
      value.route || null,
      value.appVersion || null,
      value.device || null,
      value.logs ? truncate(value.logs.toString(), 8000) : null,
      severity,
      now,
    ).run();

    // Fire Discord webhook — anonymized user id (last 4 of uuid).
    const short = userId ? userId.slice(-4) : '----';
    const emoji = kind === 'bug' ? '🐛' : kind === 'idea' ? '💡' : kind === 'praise' ? '💚' : '📝';
    const sevTag = severity ? ` · ${severity.toUpperCase()}` : '';
    await postDiscord(env, {
      content: null,
      embeds: [{
        title: `${emoji} ${kind.toUpperCase()}${sevTag}: ${truncate(value.title.trim(), 180)}`,
        description: truncate(value.body?.toString() || '_no details_', 1800),
        color: kind === 'bug' ? 0xB04A3C : kind === 'idea' ? 0x7A8450 : 0x5865F2,
        fields: [
          { name: 'Route', value: value.route || 'unknown', inline: true },
          { name: 'Version', value: value.appVersion || 'unknown', inline: true },
          { name: 'User', value: `…${short}`, inline: true },
          { name: 'Device', value: truncate(value.device || 'unknown', 200), inline: false },
        ],
        footer: { text: `feedback id ${id}` },
        timestamp: new Date(now).toISOString(),
      }],
    });

    return json({ ok: true, id }, 201, request, env);
  },

  async events(request, userId, env) {
    // Batch event ingest. Keep it cheap — reject huge payloads, no heavy validation.
    const limited = await enforce(env, 'events', userId);
    if (limited) return limited;

    const { value, error } = await readJson(request, 64_000);
    if (error) return error;
    const events = Array.isArray(value.events) ? value.events : [];
    if (events.length === 0) return json({ ok: true, accepted: 0 }, 200, request, env);
    if (events.length > 50) return err(413, 'too many events');

    const now = Date.now();
    const sessionId = typeof value.sessionId === 'string' ? value.sessionId.slice(0, 64) : null;
    const appVersion = typeof value.appVersion === 'string' ? value.appVersion.slice(0, 32) : null;

    const stmts = [];
    for (const e of events) {
      if (!e || typeof e !== 'object') continue;
      if (!isNonEmptyString(e.name, 64)) continue;
      const propsStr = e.props ? JSON.stringify(e.props).slice(0, 2000) : null;
      const route = (typeof e.route === 'string' && e.route.length > 0 && e.route.length <= 64) ? e.route : null;
      const ts = typeof e.ts === 'number' && e.ts > 0 ? Math.min(e.ts, now) : now;
      stmts.push(env.DB.prepare(
        `INSERT INTO event (id,user_id,name,props,route,session_id,app_version,ts) VALUES (?,?,?,?,?,?,?,?)`
      ).bind(uid(), userId || null, e.name, propsStr, route, sessionId, appVersion, ts));
    }
    if (stmts.length) await env.DB.batch(stmts);
    return json({ ok: true, accepted: stmts.length }, 200, request, env);
  },

  /** Blog-style review feed for the Community tab. Pulls recent public reviews joined
   * with recipe title + photo + reviewer handle. Testers write reviews via existing /reviews. */
  async reviewFeed(_request, userId, env) {
    const rows = await env.DB.prepare(
      `SELECT rv.id, rv.rating_pots, rv.notes, rv.photo_url, rv.created_at,
              rv.user_id AS reviewer_id,
              r.id AS recipe_id, r.title AS recipe_title, r.cuisine, r.image_url AS recipe_image,
              u.display_name, u.email
         FROM review rv
         JOIN recipe r ON r.id = rv.recipe_id
         LEFT JOIN user u ON u.id = rv.user_id
        WHERE rv.is_public = 1 AND rv.moderation = 'approved'
          AND COALESCE(rv.notes,'') != ''
        ORDER BY rv.created_at DESC LIMIT 50`
    ).all();
    const reviews = (rows?.results || []).map(r => ({
      id: r.id,
      ratingPots: r.rating_pots,
      notes: r.notes,
      photoUrl: r.photo_url,
      createdAt: r.created_at,
      isOwn: r.reviewer_id === userId,
      author: r.display_name || (r.email ? `@${r.email.split('@')[0].slice(0, 16)}` : 'anonymous'),
      recipeId: r.recipe_id,
      recipeTitle: r.recipe_title,
      recipeCuisine: r.cuisine,
      recipeImage: r.recipe_image,
    }));
    return json({ reviews }, 200, _request, env);
  },

  async activity(_request, userId, env) {
    // Anonymized feed: recent cooks + saves across all beta users.
    const rows = await env.DB.prepare(
      `SELECT i.status, i.created_at, r.id AS recipe_id, r.title, r.cuisine
         FROM interaction i
         JOIN recipe r ON r.id = i.recipe_id
        WHERE i.status IN ('cooked','saved')
          AND i.user_id != ?
        ORDER BY i.created_at DESC
        LIMIT 40`
    ).bind(userId || '').all();

    const items = (rows?.results || []).map(r => ({
      kind: r.status,               // 'cooked' | 'saved'
      recipeId: r.recipe_id,
      title: r.title,
      cuisine: r.cuisine,
      at: r.created_at,
    }));

    // Aggregate: top 5 most-cooked in last 7 days.
    const weekAgo = Date.now() - 7 * 86400 * 1000;
    const trendingRows = await env.DB.prepare(
      `SELECT r.id, r.title, r.cuisine, COUNT(*) AS cooks
         FROM interaction i JOIN recipe r ON r.id = i.recipe_id
        WHERE i.status = 'cooked' AND i.created_at >= ?
        GROUP BY r.id
        ORDER BY cooks DESC
        LIMIT 5`
    ).bind(weekAgo).all();
    const trending = (trendingRows?.results || []).map(r => ({
      recipeId: r.id, title: r.title, cuisine: r.cuisine, cooks: r.cooks,
    }));

    return json({ items, trending }, 200, null, env);
  },
};

// ---------- ADMIN (key-gated) ----------

function adminAuthed(request, env) {
  const key = request.headers.get('x-admin-key') || new URL(request.url).searchParams.get('key') || '';
  if (!env.ADMIN_KEY) return false;
  if (key.length !== env.ADMIN_KEY.length) return false;
  let x = 0;
  for (let i = 0; i < key.length; i++) x |= key.charCodeAt(i) ^ env.ADMIN_KEY.charCodeAt(i);
  return x === 0;
}

export const handleAdmin = {
  async stats(request, env) {
    if (!adminAuthed(request, env)) return err(401, 'bad admin key');
    const now = Date.now();
    const day = 86400 * 1000;
    const [users, dau, wau, scans7, cooks7, saves7, dismisses7, feedback] = await Promise.all([
      env.DB.prepare(`SELECT COUNT(*) AS n FROM user`).first(),
      env.DB.prepare(`SELECT COUNT(DISTINCT user_id) AS n FROM event WHERE ts >= ?`).bind(now - day).first(),
      env.DB.prepare(`SELECT COUNT(DISTINCT user_id) AS n FROM event WHERE ts >= ?`).bind(now - 7 * day).first(),
      env.DB.prepare(`SELECT COUNT(*) AS n FROM scan_history WHERE created_at >= ?`).bind(now - 7 * day).first(),
      env.DB.prepare(`SELECT COUNT(*) AS n FROM interaction WHERE status='cooked' AND created_at >= ?`).bind(now - 7 * day).first(),
      env.DB.prepare(`SELECT COUNT(*) AS n FROM interaction WHERE status='saved'  AND created_at >= ?`).bind(now - 7 * day).first(),
      env.DB.prepare(`SELECT COUNT(*) AS n FROM interaction WHERE status='dismissed' AND created_at >= ?`).bind(now - 7 * day).first(),
      env.DB.prepare(`SELECT kind, severity, status, COUNT(*) AS n FROM beta_feedback GROUP BY kind, severity, status`).all(),
    ]);
    const savesN = saves7?.n || 0;
    const dismissesN = dismisses7?.n || 0;
    const swipeTotal = savesN + dismissesN;
    const saveRate = swipeTotal > 0 ? Math.round((savesN / swipeTotal) * 100) : 0;

    const topEvents = await env.DB.prepare(
      `SELECT name, COUNT(*) AS n FROM event WHERE ts >= ? GROUP BY name ORDER BY n DESC LIMIT 20`
    ).bind(now - 7 * day).all();

    const topRecipes = await env.DB.prepare(
      `SELECT r.id, r.title, r.cuisine, COUNT(*) AS cooks
         FROM interaction i JOIN recipe r ON r.id = i.recipe_id
        WHERE i.status='cooked' AND i.created_at >= ?
        GROUP BY r.id ORDER BY cooks DESC LIMIT 10`
    ).bind(now - 7 * day).all();

    const recentFeedback = await env.DB.prepare(
      `SELECT id, user_id, kind, title, body, route, app_version, device, severity, status, created_at
         FROM beta_feedback ORDER BY created_at DESC LIMIT 50`
    ).all();

    // Catalog breakdown: total recipe count per content_type, with alcoholic split for cocktails.
    const catalog = await env.DB.prepare(
      `SELECT content_type, COALESCE(is_alcoholic, 0) AS alcoholic, COUNT(*) AS n
         FROM recipe
        GROUP BY content_type, alcoholic
        ORDER BY content_type, alcoholic`
    ).all();

    return json({
      users: users?.n || 0,
      dau: dau?.n || 0,
      wau: wau?.n || 0,
      scans7d: scans7?.n || 0,
      cooks7d: cooks7?.n || 0,
      saves7d: savesN,
      dismisses7d: dismissesN,
      saveRate7d: saveRate,
      feedbackBreakdown: feedback?.results || [],
      topEvents7d: topEvents?.results || [],
      topRecipes7d: topRecipes?.results || [],
      recentFeedback: recentFeedback?.results || [],
      catalogBreakdown: catalog?.results || [],
      generatedAt: now,
    }, 200, request, env);
  },

  async feedbackUpdate(request, id, env) {
    if (!adminAuthed(request, env)) return err(401, 'bad admin key');
    const { value, error } = await readJson(request, 2000);
    if (error) return error;
    const status = value.status;
    if (!['open', 'triaged', 'fixed', 'wontfix', 'duplicate'].includes(status)) return err(400, 'bad status');
    await env.DB.prepare(`UPDATE beta_feedback SET status = ? WHERE id = ?`).bind(status, id).run();
    return json({ ok: true }, 200, request, env);
  },

  async dashboard(request, env) {
    if (!adminAuthed(request, env)) return new Response('unauthorized', { status: 401 });
    const photosBase = (env.PHOTOS_PUBLIC_BASE || '').toString();
    const html = DASHBOARD_HTML
      .replace('__ADMIN_KEY__', env.ADMIN_KEY || '')
      .replace('__PHOTOS_PUBLIC_BASE__', photosBase);
    return new Response(html, {
      status: 200,
      headers: {
        'Content-Type': 'text/html; charset=utf-8',
        'X-Content-Type-Options': 'nosniff',
        'Cache-Control': 'no-store',
        'Content-Security-Policy':
          "default-src 'self'; img-src https: data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'",
        'Referrer-Policy': 'no-referrer',
      },
    });
  },
};

const DASHBOARD_HTML = `<!doctype html>
<html><head><meta charset="utf-8"><title>Pantrie Beta</title>
<style>
  :root { --ink:#1c1b18; --muted:#6a6864; --paper:#faf7f2; --line:#e6e2da; --red:#b04a3c; --olive:#7a8450; }
  body{font-family:-apple-system,system-ui,sans-serif;background:var(--paper);color:var(--ink);margin:0;padding:24px;max-width:1200px;margin:0 auto}
  h1{margin:0 0 4px 0;font-weight:500} .sub{color:var(--muted);margin-bottom:28px}
  .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:16px;margin-bottom:28px}
  .card{background:white;border:1px solid var(--line);border-radius:10px;padding:16px}
  .kpi{font-size:32px;font-weight:600;line-height:1.1} .label{color:var(--muted);font-size:13px;margin-top:4px}
  section{margin-bottom:32px} h2{font-size:18px;font-weight:500;border-bottom:1px solid var(--line);padding-bottom:8px}
  table{width:100%;border-collapse:collapse;font-size:14px} th,td{text-align:left;padding:8px 10px;border-bottom:1px solid var(--line);vertical-align:top}
  th{color:var(--muted);font-weight:500} .tag{display:inline-block;padding:2px 8px;border-radius:4px;font-size:12px;font-weight:500}
  .tag.bug{background:#fde6e2;color:var(--red)} .tag.idea{background:#eaf0da;color:var(--olive)} .tag.praise{background:#e5efe0;color:var(--olive)} .tag.other{background:#eee;color:var(--muted)}
  .status{font-size:12px;color:var(--muted)} button{border:1px solid var(--line);background:white;padding:4px 10px;border-radius:4px;font-size:12px;cursor:pointer}
  .body{color:var(--muted);margin-top:4px;white-space:pre-wrap}
  code{background:#eee;padding:1px 4px;border-radius:3px;font-size:12px}
</style>
</head><body>
<h1>Pantrie Beta</h1>
<div class="sub" id="gen">loading…</div>

<div class="grid" id="kpis"></div>

<section><h2>Top events (7d)</h2><table id="events"><thead><tr><th>Event</th><th>Count</th></tr></thead><tbody></tbody></table></section>

<section><h2>Top recipes cooked (7d)</h2><table id="recipes"><thead><tr><th>Title</th><th>Cuisine</th><th>Cooks</th></tr></thead><tbody></tbody></table></section>

<section><h2>Recipe submissions — pending review</h2>
  <div style="margin-bottom:12px">
    <button onclick="loadSubs('pending')">Pending</button>
    <button onclick="loadSubs('duplicate')">Flagged duplicates</button>
    <button onclick="loadSubs('approved')">Approved</button>
    <button onclick="loadSubs('rejected')">Rejected</button>
    <button onclick="loadSubs('all')">All</button>
  </div>
  <div id="subs"></div>
</section>

<section><h2>Recent feedback</h2><table id="feedback"><thead><tr><th>When</th><th>Kind</th><th>Title / Body</th><th>Route</th><th>Ver</th><th>Status</th></tr></thead><tbody></tbody></table></section>

<script>
const KEY = "__ADMIN_KEY__";
const PHOTOS_PUBLIC_BASE = "__PHOTOS_PUBLIC_BASE__";

// ---------- safe DOM helpers ----------
function el(tag, opts) {
  const n = document.createElement(tag);
  if (opts) {
    if (opts.className) n.className = opts.className;
    if (opts.text != null) n.textContent = String(opts.text);
    if (opts.style) n.setAttribute('style', opts.style);
    if (opts.attrs) for (const k in opts.attrs) n.setAttribute(k, opts.attrs[k]);
    if (opts.children) for (const c of opts.children) if (c != null) n.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
    if (opts.onClick) n.addEventListener('click', opts.onClick);
  }
  return n;
}
function clear(node) { while (node.firstChild) node.removeChild(node.firstChild); }

// Only allow image URLs from known-good hosts. Returns the URL string if OK, else null.
function safeImageUrl(raw) {
  if (typeof raw !== 'string' || raw.length === 0) return null;
  let u;
  try { u = new URL(raw); } catch (_) { return null; }
  if (u.protocol !== 'https:') return null;
  const host = u.hostname.toLowerCase();
  if (host.endsWith('.r2.dev')) return u.toString();
  if (host.endsWith('.r2.cloudflarestorage.com')) return u.toString();
  if (PHOTOS_PUBLIC_BASE) {
    try {
      const base = new URL(PHOTOS_PUBLIC_BASE);
      if (base.hostname && host === base.hostname.toLowerCase()) return u.toString();
    } catch (_) { /* ignore bad base */ }
  }
  return null;
}

// ---------- main loaders ----------
async function load() {
  const r = await fetch('/admin/stats?key=' + encodeURIComponent(KEY));
  const d = await r.json();
  document.getElementById('gen').textContent = 'generated ' + new Date(d.generatedAt).toLocaleString();

  // KPIs
  const kpiBox = document.getElementById('kpis');
  clear(kpiBox);
  const kpis = [
    ['Users', d.users], ['DAU', d.dau], ['WAU', d.wau],
    ['Scans (7d)', d.scans7d], ['Cooks (7d)', d.cooks7d],
    ['Saves (7d)', d.saves7d], ['Dismissed (7d)', d.dismisses7d],
    ['Save rate (7d)', d.saveRate7d == null ? '' : d.saveRate7d + '%'],
  ];
  for (const [l, v] of kpis) {
    const card = el('div', { className: 'card' });
    card.appendChild(el('div', { className: 'kpi', text: v == null ? '' : v }));
    card.appendChild(el('div', { className: 'label', text: l }));
    kpiBox.appendChild(card);
  }

  // Events table
  const evTbody = document.querySelector('#events tbody');
  clear(evTbody);
  for (const row of (d.topEvents7d || [])) {
    const tr = el('tr');
    const td1 = el('td');
    td1.appendChild(el('code', { text: row.name || '' }));
    tr.appendChild(td1);
    tr.appendChild(el('td', { text: row.n == null ? '' : row.n }));
    evTbody.appendChild(tr);
  }

  // Recipes table
  const rcTbody = document.querySelector('#recipes tbody');
  clear(rcTbody);
  for (const row of (d.topRecipes7d || [])) {
    const tr = el('tr');
    tr.appendChild(el('td', { text: row.title || '' }));
    tr.appendChild(el('td', { text: row.cuisine || '' }));
    tr.appendChild(el('td', { text: row.cooks == null ? '' : row.cooks }));
    rcTbody.appendChild(tr);
  }

  // Feedback table
  const fbTbody = document.querySelector('#feedback tbody');
  clear(fbTbody);
  for (const f of (d.recentFeedback || [])) {
    const tr = el('tr');
    tr.appendChild(el('td', { text: new Date(f.created_at).toLocaleString() }));

    const tdKind = el('td');
    const kindVal = typeof f.kind === 'string' ? f.kind : 'other';
    const kindClass = /^(bug|idea|praise|other)$/.test(kindVal) ? kindVal : 'other';
    tdKind.appendChild(el('span', { className: 'tag ' + kindClass, text: kindVal }));
    tr.appendChild(tdKind);

    const tdTitle = el('td');
    tdTitle.appendChild(el('b', { text: f.title || '' }));
    tdTitle.appendChild(el('div', { className: 'body', text: f.body || '' }));
    tr.appendChild(tdTitle);

    const tdRoute = el('td');
    tdRoute.appendChild(el('code', { text: f.route || '' }));
    tr.appendChild(tdRoute);

    tr.appendChild(el('td', { text: f.app_version || '' }));

    const tdStatus = el('td');
    tdStatus.appendChild(el('span', { className: 'status', text: f.status || 'open' }));
    tdStatus.appendChild(document.createTextNode(' '));
    const fid = f.id;
    const triageBtn = el('button', { text: 'triage' });
    triageBtn.addEventListener('click', () => setStatus(fid, 'triaged'));
    tdStatus.appendChild(triageBtn);
    tdStatus.appendChild(document.createTextNode(' '));
    const fixedBtn = el('button', { text: 'fixed' });
    fixedBtn.addEventListener('click', () => setStatus(fid, 'fixed'));
    tdStatus.appendChild(fixedBtn);
    tr.appendChild(tdStatus);

    fbTbody.appendChild(tr);
  }

  loadSubs('pending');
}

async function setStatus(id, status) {
  await fetch('/admin/feedback/' + encodeURIComponent(id), {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', 'X-Admin-Key': KEY },
    body: JSON.stringify({ status }),
  });
  load();
}

async function loadSubs(status) {
  const r = await fetch('/admin/submissions?status=' + encodeURIComponent(status) + '&key=' + encodeURIComponent(KEY));
  const d = await r.json();
  const subs = d.submissions || [];
  const host = document.getElementById('subs');
  clear(host);
  if (!subs.length) {
    host.appendChild(el('p', { style: 'color:var(--muted)', text: 'No submissions.' }));
    return;
  }

  for (const s of subs) {
    let ings = [];
    let steps = [];
    try { ings = JSON.parse(s.ingredients_json || '[]'); } catch (_) { ings = []; }
    try { steps = JSON.parse(s.steps_json || '[]'); } catch (_) { steps = []; }
    if (!Array.isArray(ings)) ings = [];
    if (!Array.isArray(steps)) steps = [];

    const card = el('div', { className: 'card', style: 'margin-bottom:14px' });

    // Image (validated) or warning text.
    if (s.image_url) {
      const safeUrl = safeImageUrl(s.image_url);
      if (safeUrl) {
        const img = el('img', {
          style: 'width:140px;height:140px;object-fit:cover;border-radius:8px;float:right;margin-left:12px',
        });
        img.setAttribute('src', safeUrl);
        img.setAttribute('alt', '');
        img.setAttribute('referrerpolicy', 'no-referrer');
        card.appendChild(img);
      } else {
        card.appendChild(el('div', {
          style: 'float:right;margin-left:12px;padding:8px;border:1px dashed var(--red);color:var(--red);font-size:12px;border-radius:6px;max-width:140px',
          text: '⚠️ untrusted URL',
        }));
      }
    }

    // Meta line
    const meta = el('div', { style: 'font-size:11px;color:var(--muted)' });
    meta.appendChild(document.createTextNode(
      new Date(s.created_at).toLocaleString() + ' · ' + (s.submitter_email || '?') + ' · '
    ));
    meta.appendChild(el('span', { className: 'status', text: s.status || '' }));
    card.appendChild(meta);

    // Title
    card.appendChild(el('h3', { style: 'margin:4px 0', text: s.title || '' }));

    // Sub-meta
    const sub = el('div', { style: 'color:var(--muted);font-size:13px' });
    const cuisine = s.cuisine || '—';
    const prep = s.prep_minutes || 0;
    const cook = s.cook_minutes || 0;
    const servings = s.servings == null ? '?' : s.servings;
    sub.textContent = cuisine + ' · ' + prep + '+' + cook + ' min · ' + servings + ' servings';
    card.appendChild(sub);

    // Description
    if (s.description) {
      card.appendChild(el('p', { text: String(s.description) }));
    }

    // Duplicate warning
    if (s.dup_of_recipe_id) {
      const dup = el('div', { style: 'color:#b04a3c;font-size:13px' });
      dup.appendChild(el('b', { text: 'Possible duplicate of: ' }));
      dup.appendChild(el('code', { text: String(s.dup_of_recipe_id) }));
      card.appendChild(dup);
    }

    // Ingredients
    const ingDetails = el('details');
    ingDetails.appendChild(el('summary', { text: 'Ingredients (' + ings.length + ')' }));
    const ul = el('ul');
    for (const i of ings) {
      if (!i || typeof i !== 'object') continue;
      const qty = i.quantity == null ? '' : String(i.quantity);
      const unit = i.unit == null ? '' : String(i.unit);
      const name = i.name == null ? '' : String(i.name);
      ul.appendChild(el('li', { text: (qty + ' ' + unit + ' ' + name).replace(/\s+/g, ' ').trim() }));
    }
    ingDetails.appendChild(ul);
    card.appendChild(ingDetails);

    // Steps
    const stepDetails = el('details');
    stepDetails.appendChild(el('summary', { text: 'Steps (' + steps.length + ')' }));
    const ol = el('ol');
    for (const st of steps) {
      if (!st || typeof st !== 'object') continue;
      ol.appendChild(el('li', { text: st.text == null ? '' : String(st.text) }));
    }
    stepDetails.appendChild(ol);
    card.appendChild(stepDetails);

    // Actions
    const actions = el('div', { style: 'margin-top:10px;clear:both' });
    if (s.status === 'pending' || s.status === 'duplicate') {
      const approveBtn = el('button', { style: 'background:#7a8450;color:white', text: 'Approve' });
      approveBtn.addEventListener('click', () => approveSub(s.id));
      actions.appendChild(approveBtn);
      actions.appendChild(document.createTextNode(' '));
      const rejectBtn = el('button', { style: 'background:#b04a3c;color:white', text: 'Reject' });
      rejectBtn.addEventListener('click', () => rejectSub(s.id));
      actions.appendChild(rejectBtn);
    } else {
      let statusLine = s.status || '';
      if (s.approved_as) statusLine += ' → ' + s.approved_as;
      if (s.reject_reason) statusLine += ': ' + s.reject_reason;
      actions.appendChild(el('span', { className: 'status', text: statusLine }));
    }
    card.appendChild(actions);

    host.appendChild(card);
  }
}

async function approveSub(id) {
  if (!confirm('Approve submission and add to catalog?')) return;
  await fetch('/admin/submissions/' + encodeURIComponent(id) + '/approve', {
    method: 'POST',
    headers: { 'X-Admin-Key': KEY },
  });
  loadSubs('pending');
}

async function rejectSub(id) {
  const reason = prompt('Reject reason (shown to submitter)?', "Doesn't meet catalog guidelines");
  if (reason === null) return;
  await fetch('/admin/submissions/' + encodeURIComponent(id) + '/reject', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Admin-Key': KEY },
    body: JSON.stringify({ reason }),
  });
  loadSubs('pending');
}

load();
</script>
</body></html>`;

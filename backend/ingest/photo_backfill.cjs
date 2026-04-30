#!/usr/bin/env node
/**
 * Photo backfill — find license-clear photos for recipes that lack one
 * and patch them back into D1 via the admin endpoint.
 *
 * Sources tried (in order, first hit wins):
 *   1. TheMealDB   (CC0; only food recipes, exact title match)
 *   2. Wikimedia Commons  (cc-by, cc-by-sa, cc0, pd)
 *   3. Openverse  (license_type=commercial)
 *
 * Output:
 *   backend/ingest/photo_backfill_log.csv         — every attempted recipe
 *   backend/ingest/photo_backfill_pending.csv     — overflow once cap reached
 *
 * Env:
 *   PANTRIE_ADMIN_KEY  required (read from C:/Users/12566/Downloads/PANTRIE_ADMIN_KEY.txt if absent)
 *
 * CLI:
 *   node photo_backfill.cjs [--limit N] [--max-patches N] [--dry-run] [--bucket BUCKET]
 *     bucket = mealdb | usda | cfg | hf | wb | cck | all   (default all)
 */

const fs = require('fs');
const path = require('path');

const WORKER = 'https://pantrie-backend.schulgenkyle.workers.dev';
const KEY_FILE = 'C:/Users/12566/Downloads/PANTRIE_ADMIN_KEY.txt';
const ADMIN_KEY = (process.env.PANTRIE_ADMIN_KEY || fs.readFileSync(KEY_FILE, 'utf8')).trim();
const LOG_PATH = path.join(__dirname, 'photo_backfill_log.csv');
const PENDING_PATH = path.join(__dirname, 'photo_backfill_pending.csv');
const PROGRESS_PATH = path.join(__dirname, 'photo_backfill_progress.json');

const ACCEPTED_LICENSES = new Set([
  'cc0', 'pd', 'pdm', 'public domain',
  'cc-by', 'cc-by-2.0', 'cc-by-2.5', 'cc-by-3.0', 'cc-by-4.0',
  'cc-by-sa', 'cc-by-sa-2.0', 'cc-by-sa-2.5', 'cc-by-sa-3.0', 'cc-by-sa-4.0',
  'attribution', 'attribution-share alike',
]);

const args = process.argv.slice(2);
function flag(name, def) {
  const i = args.indexOf('--' + name);
  if (i === -1) return def;
  return args[i + 1];
}
const RUN_BUCKET = flag('bucket', 'all');                 // mealdb|usda|cfg|hf|wb|cck|all
const PER_BATCH = parseInt(flag('limit', '500'), 10);
const MAX_PATCHES = parseInt(flag('max-patches', '5000'), 10);
const MAX_BATCHES = parseInt(flag('max-batches', '100'), 10);
const DRY_RUN = args.includes('--dry-run');
const RATE_LIMIT_MS = parseInt(flag('rate', '350'), 10);  // intra-recipe delay between API calls
const CONCURRENCY = Math.max(1, parseInt(flag('concurrency', '4'), 10));

let totalAttempts = 0;
let totalHits = 0;
let totalPatched = 0;
let perSource = { themealdb: 0, wikimedia: 0, openverse: 0 };
let perBucketStats = {};

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function csvEscape(v) {
  if (v == null) return '';
  const s = String(v);
  if (s.includes(',') || s.includes('"') || s.includes('\n')) {
    return '"' + s.replace(/"/g, '""') + '"';
  }
  return s;
}

function appendLog(file, row) {
  fs.appendFileSync(
    file,
    row.map(csvEscape).join(',') + '\n',
    'utf8'
  );
}

function ensureLog() {
  const isEmpty = (p) => !fs.existsSync(p) || fs.statSync(p).size === 0;
  if (isEmpty(LOG_PATH)) {
    appendLog(LOG_PATH, ['ts', 'id', 'title', 'bucket', 'source', 'status', 'image_url', 'license', 'credit', 'source_url', 'reason']);
  }
  if (isEmpty(PENDING_PATH)) {
    appendLog(PENDING_PATH, ['id', 'title', 'bucket', 'image_url', 'license', 'credit', 'source_url']);
  }
}

function bucketOf(id) {
  if (!id) return 'unknown';
  if (id.startsWith('mealdb-')) return 'mealdb';
  if (id.startsWith('usda-') || id.startsWith('myplate-')) return 'usda';
  if (id.startsWith('cfg-') || id.startsWith('canada-')) return 'cfg';
  if (id.startsWith('hf-')) return 'hf';
  if (id.startsWith('wb-')) return 'wb';
  if (id.startsWith('cck_') || id.startsWith('cck-')) return 'cck';
  return 'other';
}

function normalizeLicense(raw) {
  if (!raw) return null;
  let s = String(raw).toLowerCase().trim();
  s = s.replace(/^https?:\/\/.*?creativecommons\.org\/(licenses|publicdomain)\/([\w.-]+).*$/, '$2');
  s = s.replace(/\s+/g, '-');
  // Common Wikimedia values: "cc-by-sa-3.0", "cc-by-4.0", "cc0", "pd", "public-domain"
  if (s.startsWith('cc-by-sa')) return 'cc-by-sa';
  if (s.startsWith('cc-by')) return 'cc-by';
  if (s === 'cc0' || s === 'cc-zero' || s.includes('cc0')) return 'cc0';
  if (s.includes('public-domain') || s === 'pd' || s === 'pdm') return 'pd';
  return s;
}

function licenseOk(norm) {
  if (!norm) return false;
  return ACCEPTED_LICENSES.has(norm);
}

async function fetchJson(url, opts = {}) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), 15000);
  try {
    const r = await fetch(url, { ...opts, signal: ctrl.signal, headers: { 'User-Agent': 'PantreePhotoBackfill/1.0 (kyle@brimm)', ...(opts.headers || {}) } });
    if (!r.ok) return { error: 'http ' + r.status };
    const j = await r.json();
    return { json: j };
  } catch (e) {
    return { error: e.message || 'fetch failed' };
  } finally {
    clearTimeout(t);
  }
}

// ---------- TheMealDB ----------
async function tryTheMealDB(title, contentType) {
  const q = encodeURIComponent(title.replace(/[^\w\s]/g, '').slice(0, 80));
  const norm = (s) => (s || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
  const want = norm(title);

  if (contentType === 'cocktail' || contentType === 'mocktail') {
    const { json, error } = await fetchJson(`https://www.thecocktaildb.com/api/json/v1/1/search.php?s=${q}`);
    if (error || !json?.drinks?.length) return null;
    let hit = json.drinks.find((m) => norm(m.strDrink) === want);
    if (!hit) hit = json.drinks[0];
    if (!hit?.strDrinkThumb) return null;
    return {
      image_url: hit.strDrinkThumb,
      photo_credit: 'TheCocktailDB',
      photo_license: 'cc0',
      photo_source_url: `https://www.thecocktaildb.com/drink/${hit.idDrink}`,
      source: 'themealdb',
    };
  }

  if (contentType !== 'food') return null;
  const { json, error } = await fetchJson(`https://www.themealdb.com/api/json/v1/1/search.php?s=${q}`);
  if (error || !json?.meals?.length) return null;
  let hit = json.meals.find((m) => norm(m.strMeal) === want);
  if (!hit) hit = json.meals[0];
  if (!hit?.strMealThumb) return null;
  return {
    image_url: hit.strMealThumb,
    photo_credit: 'TheMealDB',
    photo_license: 'cc0',
    photo_source_url: hit.strSource || `https://www.themealdb.com/meal/${hit.idMeal}`,
    source: 'themealdb',
  };
}

// Tokens from the title we expect in candidate filenames so we don't
// accept random Wikimedia hits (e.g. tartan images for "20th Century").
const STOPWORDS = new Set([
  'a', 'an', 'the', 'and', 'or', 'with', 'in', 'on', 'of', 'for', 'to',
  'recipe', 'recipes', 'easy', 'quick', 'old', 'new', 'best', 'good',
  'nice', 'very', 'super', 'simple', 'homemade', 'classic',
  'cocktail', 'drink', 'mocktail', 'food', 'meal', 'dish',
  's',
]);

function titleTokens(title) {
  return String(title || '')
    .toLowerCase()
    .replace(/['’]s\b/g, '')
    .replace(/[^a-z0-9]+/g, ' ')
    .split(/\s+/)
    .filter((t) => t.length >= 3 && !STOPWORDS.has(t));
}

function fileMatchesTitle(fname, tokens) {
  if (!tokens.length) return false;
  const lc = fname.toLowerCase();
  // At least one meaningful token must appear in the filename.
  return tokens.some((t) => lc.includes(t));
}

// ---------- Wikimedia Commons ----------
async function tryWikimedia(title, extraTokens = []) {
  const q = encodeURIComponent(title.slice(0, 80));
  const search = await fetchJson(
    `https://commons.wikimedia.org/w/api.php?action=query&format=json&list=search&srnamespace=6&srsearch=${q}&srlimit=8&origin=*`
  );
  if (search.error || !search.json?.query?.search?.length) return null;
  const tokens = [...titleTokens(title), ...extraTokens];
  const candidates = search.json.query.search.slice(0, 8);
  for (const c of candidates) {
    const fname = c.title;
    if (!/\.(jpg|jpeg|png|webp)$/i.test(fname)) continue; // skip gif/svg/etc
    // Relevance gate: filename must echo at least one meaningful token from the title.
    if (!fileMatchesTitle(fname, tokens)) continue;
    const info = await fetchJson(
      `https://commons.wikimedia.org/w/api.php?action=query&format=json&titles=${encodeURIComponent(fname)}&prop=imageinfo&iiprop=url%7Cextmetadata&origin=*`
    );
    if (info.error) continue;
    const pages = info.json?.query?.pages || {};
    const page = Object.values(pages)[0];
    const ii = page?.imageinfo?.[0];
    if (!ii?.url) continue;
    const meta = ii.extmetadata || {};
    const lic = normalizeLicense(meta.LicenseShortName?.value || meta.License?.value || meta.UsageTerms?.value);
    if (!licenseOk(lic)) continue;
    const artist = (meta.Artist?.value || 'Wikimedia Commons').replace(/<[^>]+>/g, '').trim().slice(0, 200);
    return {
      image_url: ii.url,
      photo_credit: artist || 'Wikimedia Commons',
      photo_license: lic,
      photo_source_url: ii.descriptionurl || `https://commons.wikimedia.org/wiki/${encodeURIComponent(fname)}`,
      source: 'wikimedia',
    };
  }
  return null;
}

// ---------- Openverse ----------
async function tryOpenverse(title, extraTokens = []) {
  const q = encodeURIComponent(title.slice(0, 80));
  const url = `https://api.openverse.org/v1/images/?q=${q}&license_type=commercial&page_size=8`;
  const { json, error } = await fetchJson(url);
  if (error || !json?.results?.length) return null;
  const tokens = [...titleTokens(title), ...extraTokens];
  for (const r of json.results) {
    const lic = normalizeLicense(r.license);
    if (!licenseOk(lic)) continue;
    if (!r.url) continue;
    // Relevance: title or tags should include at least one meaningful token.
    const hay = (
      (r.title || '') + ' ' +
      (Array.isArray(r.tags) ? r.tags.map((t) => t?.name || '').join(' ') : '')
    ).toLowerCase();
    if (tokens.length && !tokens.some((t) => hay.includes(t))) continue;
    return {
      image_url: r.url,
      photo_credit: (r.creator || 'Openverse contributor').slice(0, 200),
      photo_license: lic,
      photo_source_url: r.foreign_landing_url || r.url,
      source: 'openverse',
    };
  }
  return null;
}

// ---------- Worker patch ----------
async function patchBatch(items) {
  if (DRY_RUN) {
    return { ok: true, updated: items.length, dryRun: true };
  }
  const r = await fetch(`${WORKER}/admin/patch-recipe-photo`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Admin-Key': ADMIN_KEY },
    body: JSON.stringify({ items }),
  });
  if (!r.ok) return { error: 'patch http ' + r.status };
  return await r.json();
}

async function fetchPhotoless(limit, offset) {
  const url = `${WORKER}/admin/photoless-recipes?limit=${limit}&offset=${offset}&key=${encodeURIComponent(ADMIN_KEY)}`;
  const { json, error } = await fetchJson(url);
  if (error) {
    console.error('  photoless fetch failed:', error);
    return [];
  }
  return json?.recipes || [];
}

async function processRecipe(rec) {
  const bucket = bucketOf(rec.id);
  if (!perBucketStats[bucket]) perBucketStats[bucket] = { attempts: 0, hits: 0 };
  perBucketStats[bucket].attempts++;
  totalAttempts++;
  // Strategy by bucket: mealdb-prefixed are already from TheMealDB so skip that source.
  // Cocktails: prefer wikimedia "<title> cocktail".
  let result = null;
  let reasonChain = [];
  try {
    if (bucket !== 'mealdb') {
      result = await tryTheMealDB(rec.title, rec.content_type);
      if (!result) reasonChain.push('mealdb:miss');
      else result.attempted = 'themealdb';
      await sleep(RATE_LIMIT_MS);
    }
    if (!result) {
      const titleForWiki =
        rec.content_type === 'cocktail' ? `${rec.title} cocktail` : rec.title;
      // For cocktails allow "cocktail" as an extra relevance keyword.
      const extraTokens = rec.content_type === 'cocktail' ? ['cocktail'] : [];
      result = await tryWikimedia(titleForWiki, extraTokens);
      if (!result) reasonChain.push('wiki:miss');
      else result.attempted = 'wikimedia';
      await sleep(RATE_LIMIT_MS);
    }
    if (!result) {
      const extraTokens = rec.content_type === 'cocktail' ? ['cocktail'] : [];
      result = await tryOpenverse(rec.title, extraTokens);
      if (!result) reasonChain.push('openverse:miss');
      else result.attempted = 'openverse';
      await sleep(RATE_LIMIT_MS);
    }
  } catch (e) {
    reasonChain.push('error:' + (e.message || 'unknown'));
  }
  const ts = new Date().toISOString();
  if (result) {
    totalHits++;
    perBucketStats[bucket].hits++;
    perSource[result.source]++;
    appendLog(LOG_PATH, [
      ts, rec.id, rec.title, bucket, result.source, 'hit',
      result.image_url, result.photo_license, result.photo_credit, result.photo_source_url, '',
    ]);
    return result;
  } else {
    appendLog(LOG_PATH, [
      ts, rec.id, rec.title, bucket, '', 'miss',
      '', '', '', '', reasonChain.join(';'),
    ]);
    return null;
  }
}

async function main() {
  ensureLog();
  console.log(`[photo_backfill] start  bucket=${RUN_BUCKET}  per_batch=${PER_BATCH}  max_patches=${MAX_PATCHES}  dry_run=${DRY_RUN}`);

  let offset = 0;
  let pendingPatch = [];
  let batchN = 0;

  while (totalPatched < MAX_PATCHES && batchN < MAX_BATCHES) {
    batchN++;
    const recipes = await fetchPhotoless(PER_BATCH, offset);
    if (!recipes.length) {
      console.log('[photo_backfill] no more photoless recipes — done');
      break;
    }
    console.log(`[batch ${batchN}] offset=${offset} fetched=${recipes.length}`);
    let batchHits = 0;

    // Filter by bucket if requested
    const filtered = RUN_BUCKET === 'all' ? recipes : recipes.filter((r) => bucketOf(r.id) === RUN_BUCKET);
    if (filtered.length === 0) {
      offset += recipes.length;
      // If filter excludes everything in this page advance and continue.
      continue;
    }

    // Process recipes with bounded concurrency so we don't sit on serial sleeps.
    let cursor = 0;
    async function worker() {
      while (true) {
        const i = cursor++;
        if (i >= filtered.length) return;
        const rec = filtered[i];
        if (totalPatched + pendingPatch.length >= MAX_PATCHES) {
          appendLog(PENDING_PATH, [rec.id, rec.title, bucketOf(rec.id), '', '', '', '']);
          continue;
        }
        const found = await processRecipe(rec);
        if (found) {
          batchHits++;
          pendingPatch.push({
            id: rec.id,
            image_url: found.image_url,
            photo_credit: found.photo_credit,
            photo_license: found.photo_license,
            photo_source_url: found.photo_source_url,
          });
          if (pendingPatch.length >= 50) {
            const flush = pendingPatch;
            pendingPatch = [];
            const r = await patchBatch(flush);
            if (r.error) {
              console.error('  patch failed:', r.error);
            } else {
              totalPatched += r.updated || 0;
              console.log(`  patched ${r.updated} (total ${totalPatched})`);
            }
          }
        }
      }
    }
    await Promise.all(Array.from({ length: CONCURRENCY }, () => worker()));

    console.log(`[batch ${batchN}] hits=${batchHits}/${filtered.length}  cumulative_hits=${totalHits} patched=${totalPatched}`);

    // Save progress snapshot
    fs.writeFileSync(
      PROGRESS_PATH,
      JSON.stringify(
        { batchN, offset: offset + recipes.length, totalAttempts, totalHits, totalPatched, perSource, perBucketStats },
        null,
        2
      )
    );

    offset += recipes.length;
  }

  // Flush any remaining
  if (pendingPatch.length > 0) {
    const r = await patchBatch(pendingPatch);
    if (r.error) console.error('  final patch failed:', r.error);
    else {
      totalPatched += r.updated || 0;
      console.log(`  final patch ${r.updated} (total ${totalPatched})`);
    }
  }

  const summary = {
    totalAttempts,
    totalHits,
    totalPatched,
    hitRate: totalAttempts > 0 ? (totalHits / totalAttempts).toFixed(3) : 0,
    perSource,
    perBucketStats,
  };
  fs.writeFileSync(PROGRESS_PATH, JSON.stringify(summary, null, 2));
  console.log('[photo_backfill] done', JSON.stringify(summary, null, 2));
}

main().catch((e) => {
  console.error('FATAL', e);
  process.exit(1);
});

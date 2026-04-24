// Harvest CC-licensed food photos from Wikimedia Commons for Wikibooks (wb-*) recipes.
//
// Strategy (per recipe):
//   Pass A  en.wikibooks Cookbook:<Title>  -> prop=images        (batched up to 50/req)
//   Pass B  en.wikipedia <Title>           -> prop=pageimages    (batched up to 50/req)
//   Pass C  en.wikipedia title variants    -> prop=pageimages    (base noun, "(dish)", "(food)")
//   Pass D  commons list=search            -> file candidates    (per-recipe fallback)
//
// For every File:* candidate collected from any pass, we fetch Commons imageinfo
// (iiprop=url|extmetadata|size|mime), apply license + filename + size filters,
// and keep the single LARGEST surviving image across ALL passes for that recipe.
//
// Output: ingest/normalized/wikimedia-photos.json  (array of { recipeId, imageUrl,
//         author, licenseShort, licenseUrl, sourceUrl })
//
// NOTE: This script ONLY writes the JSON file. It does not modify D1. The user will
//       review and upload separately.
//
// Usage:
//   node ingest/fetch-wikimedia-photos.js              # full run (all wb-% where image_url IS NULL)
//   node ingest/fetch-wikimedia-photos.js --limit 50   # smoke test on first 50
//   node ingest/fetch-wikimedia-photos.js --resume     # keep existing results, only fill gaps

import { writeFileSync, readFileSync, existsSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import { execSync } from 'node:child_process';

// ---------- config ----------

const UA = 'Pan-Tree-Ingest/1.0 (https://pan-tree.app; schulgenkyle@gmail.com)';
const RATE_MS = 1000;                // 1 req/sec across ALL Wikimedia APIs
const REQUEST_TIMEOUT_MS = 30000;
const MAX_RETRIES = 3;
const BATCH_SIZE = 50;               // MediaWiki max titles per query

const OUTDIR  = fileURLToPath(new URL('./normalized/', import.meta.url));
const RAWDIR  = fileURLToPath(new URL('./raw/', import.meta.url));
const OUTFILE = join(OUTDIR, 'wikimedia-photos.json');
const RECIPES_CACHE = join(RAWDIR, 'wb-recipes.json');

mkdirSync(OUTDIR, { recursive: true });
mkdirSync(RAWDIR, { recursive: true });

// Accept ONLY if LicenseShortName.value starts with one of these prefixes.
const CC_PREFIXES = ['CC0', 'CC BY', 'CC BY-SA', 'Public domain', 'PD'];

// Reject if filename (lowercase) contains any of these substrings.
const BAD_NAME_BITS = ['flag', 'icon', 'logo', 'map', 'graph', 'chart', 'ingredient'];

// Reject these file extensions entirely (non-photo / scanned-document types).
const BAD_EXTENSIONS = ['.svg', '.pdf', '.djvu', '.tif', '.tiff', '.ogv', '.webm', '.ogg', '.mp4', '.gif'];

// Accept only these MIME types.
const ALLOWED_MIMES = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];

// Common non-food words in search-result filenames that indicate the image isn't a dish.
const BAD_CONTEXT_WORDS = [
  'hoarding', 'usda', 'bulletin', 'manual', 'inspection', 'poster',
  'stamp', 'cover', 'label', 'diagram', 'sign_', 'advertisement',
  'portrait', 'statue', 'building', 'factory', 'farm_field',
  'foraminifera', 'microscope', 'skeleton', 'fossil', 'specimen',
  'shop,', 'shop_', 'store_', 'market_', 'restaurant_',
  'candy',  // e.g. "Boston Baked Beans Candy"
  'social_responsab', 'social_responsib',
];

const MIN_WIDTH  = 300;
const MIN_HEIGHT = 300;

// Cooking verbs to strip from the start of a title when building the Pass C "base noun" form.
const COOKING_VERBS = [
  'Baked', 'Roasted', 'Fried', 'Deep Fried', 'Pan Fried', 'Stir Fried',
  'Grilled', 'Steamed', 'Sauteed', 'Sautéed', 'Poached', 'Braised',
  'Boiled', 'Broiled', 'Smoked', 'Stewed', 'Seared', 'Toasted',
  'Candied', 'Glazed', 'Stuffed', 'Creamed', 'Crispy',
];

// ---------- CLI args ----------

const argv = process.argv.slice(2);
function argVal(name) {
  const i = argv.indexOf(name);
  if (i < 0) return null;
  return argv[i + 1];
}
const LIMIT  = Number(argVal('--limit')) || 0;     // 0 = no limit
const RESUME = argv.includes('--resume');

// ---------- tiny utils ----------

const sleep = ms => new Promise(r => setTimeout(r, ms));

function stripTags(s) {
  if (!s) return '';
  return String(s).replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();
}

function matchesCCLicense(shortName) {
  if (!shortName) return false;
  const s = String(shortName).trim();
  return CC_PREFIXES.some(p => s.toUpperCase().startsWith(p.toUpperCase()));
}

function badFilename(name) {
  const lower = String(name || '').toLowerCase();
  for (const ext of BAD_EXTENSIONS) {
    if (lower.endsWith(ext)) return true;
  }
  return BAD_NAME_BITS.some(bit => lower.includes(bit));
}

// Extract alphanumeric tokens (>=3 chars) from a string, lowercased.
function tokens(s) {
  return String(s || '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
    .split(/\s+/)
    .filter(w => w.length >= 3);
}

// Filter used for Pass D search results: reject files whose name has clearly wrong context
// (scanned books, posters) AND require at least one token overlap with the recipe title.
function searchResultLooksRelevant(fileTitle, recipeTitle) {
  // Normalize: strip File: prefix, lowercase, and collapse underscores/spaces to a single space
  // so substring checks don't miss due to space-vs-underscore.
  const name = String(fileTitle)
    .replace(/^File:/i, '')
    .toLowerCase()
    .replace(/[_]+/g, ' ');
  for (const bad of BAD_CONTEXT_WORDS) {
    const needle = bad.replace(/_/g, ' ');
    if (name.includes(needle)) return false;
  }
  // Reject if the filename reads like a scanned-book title.
  // Wikisource/IA scans commonly contain "(IA ...)" — match the parenthesized form, not bare "ia".
  if (/\(ia[_ ]/.test(name)) return false;
  if (/\bfarmers?[_ ]bulletin/.test(name)) return false;
  if (/\bcookbook/.test(name) && name.length > 60) return false;
  const recTok = new Set(tokens(recipeTitle));
  const nameTok = new Set(tokens(name));
  // Drop generic/stop tokens from the recipe-side set.
  const STOP = new Set(['the', 'and', 'with', 'for', 'from', 'recipe', 'dish', 'food',
    'style', 'sauce', 'salad', 'cake', 'pie', 'soup', 'bread', 'cookies', 'biscuits',
    'chicken', 'beef', 'pork', 'fish', 'potato', 'cheese']);
  const meaningful = [...recTok].filter(t => !STOP.has(t));
  // If we have meaningful tokens, require at least one match. Otherwise require ANY overlap.
  if (meaningful.length > 0) {
    return meaningful.some(t => nameTok.has(t));
  }
  return [...recTok].some(t => nameTok.has(t));
}

// Strip leading cooking verbs to get a base noun ("Baked Pork Chops" -> "Pork Chops").
// Returns null if nothing stripped (so caller can skip the variant).
function stripCookingVerb(title) {
  const t = String(title || '').trim();
  for (const verb of COOKING_VERBS) {
    const re = new RegExp('^' + verb.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\s+', 'i');
    if (re.test(t)) {
      const stripped = t.replace(re, '').trim();
      if (stripped.length >= 3) return stripped;
    }
  }
  return null;
}

// ---------- rate-limited fetch ----------

let lastCallAt = 0;
let callsMade = 0;

async function wmFetch(url) {
  // Global 1 req/sec gate (shared across all passes / all helpers).
  const now = Date.now();
  const wait = Math.max(0, lastCallAt + RATE_MS - now);
  if (wait > 0) await sleep(wait);
  lastCallAt = Date.now();
  callsMade++;

  let lastErr;
  for (let attempt = 1; attempt <= MAX_RETRIES; attempt++) {
    const ctl = new AbortController();
    const t = setTimeout(() => ctl.abort(), REQUEST_TIMEOUT_MS);
    try {
      const res = await fetch(url, {
        headers: { 'User-Agent': UA, 'Accept': 'application/json' },
        signal: ctl.signal,
      });
      clearTimeout(t);
      if (res.status === 429 || res.status >= 500) {
        lastErr = new Error(`HTTP ${res.status}`);
        await sleep(2000 * attempt);
        continue;
      }
      if (!res.ok) {
        throw new Error(`HTTP ${res.status} on ${url}`);
      }
      return await res.json();
    } catch (e) {
      clearTimeout(t);
      lastErr = e;
      if (attempt < MAX_RETRIES) await sleep(1500 * attempt);
    }
  }
  throw lastErr || new Error('wmFetch failed');
}

// ---------- commonsImageInfo cache ----------
// Many candidate File: titles overlap across passes; cache metadata to avoid duplicate calls.

const infoCache = new Map();

async function commonsImageInfo(fileTitle) {
  if (infoCache.has(fileTitle)) return infoCache.get(fileTitle);
  const url =
    'https://commons.wikimedia.org/w/api.php?' +
    new URLSearchParams({
      action: 'query',
      titles: fileTitle,
      prop: 'imageinfo',
      iiprop: 'url|extmetadata|size|mime',
      format: 'json',
      formatversion: '2',
    });
  let info = null;
  try {
    const data = await wmFetch(url);
    const pages = data?.query?.pages || [];
    const page = pages[0];
    if (page && !page.missing) {
      info = page.imageinfo?.[0] || null;
    }
  } catch (e) {
    process.stderr.write(`[commons err] ${fileTitle}: ${e.message}\n`);
  }
  infoCache.set(fileTitle, info);
  return info;
}

// Evaluate a File: title against all gates. Returns a candidate obj or null.
async function evaluateFileCandidate(fileTitle) {
  if (badFilename(fileTitle)) return null;
  const info = await commonsImageInfo(fileTitle);
  if (!info) return null;

  const em = info.extmetadata || {};
  const shortName = em.LicenseShortName?.value;
  const licenseUrl = em.LicenseUrl?.value || null;
  const artist = stripTags(em.Artist?.value) || null;

  if (!matchesCCLicense(shortName)) return null;

  const copyrighted = em.Copyrighted?.value;
  if (copyrighted === 'True' && !matchesCCLicense(shortName)) return null;

  const w = Number(info.width || 0);
  const h = Number(info.height || 0);
  if (w < MIN_WIDTH || h < MIN_HEIGHT) return null;

  const mime = String(info.mime || '').toLowerCase();
  if (!ALLOWED_MIMES.includes(mime)) return null;

  const descUrl = info.descriptionurl ||
    `https://commons.wikimedia.org/wiki/${encodeURIComponent(fileTitle.replace(/ /g, '_'))}`;

  return {
    imageUrl: info.url,
    author: artist,
    licenseShort: String(shortName).trim(),
    licenseUrl,
    sourceUrl: descUrl,
    _w: w, _h: h,
    _file: fileTitle,
  };
}

// ---------- batched pass helpers ----------

// Pass A (batched): Wikibooks Cookbook:<Title> -> prop=images.
// Returns Map(recipeId -> File:* titles[]).
async function batchCookbookImages(batch) {
  const result = new Map(batch.map(r => [r.id, []]));
  // Build "Cookbook:<Title>" per recipe. Track back to recipe via normalized lookup.
  const cbToRecipe = new Map();
  const titles = [];
  for (const r of batch) {
    const cb = `Cookbook:${r.title}`;
    cbToRecipe.set(cb, r.id);
    titles.push(cb);
  }
  const url =
    'https://en.wikibooks.org/w/api.php?' +
    new URLSearchParams({
      action: 'query',
      prop: 'images',
      titles: titles.join('|'),
      format: 'json',
      imlimit: '20',
      formatversion: '2',
    });
  let data;
  try {
    data = await wmFetch(url);
  } catch (e) {
    process.stderr.write(`[passA batch err] ${e.message}\n`);
    return result;
  }

  // MediaWiki may normalize titles (e.g. underscores). Resolve via query.normalized.
  const normMap = new Map();
  for (const n of data?.query?.normalized || []) {
    normMap.set(n.to, n.from);
  }
  const pages = data?.query?.pages || [];
  for (const page of pages) {
    const canonical = page.title;
    // Original input title is either the canonical (no normalization) or mapped back.
    const original = normMap.get(canonical) || canonical;
    const rid = cbToRecipe.get(original) || cbToRecipe.get(canonical);
    if (!rid) continue;
    if (page.missing) continue;
    const imgs = (page.images || []).map(i => i.title).filter(Boolean);
    result.set(rid, imgs);
  }
  return result;
}

// Pass B / Pass C (batched): Wikipedia titles -> prop=pageimages (thumbnail).
// Returns Map(titleKey -> { pageimage, thumbUrl }) where titleKey is the INPUT title.
async function batchWikipediaPageImages(titles) {
  const result = new Map(titles.map(t => [t, null]));
  if (titles.length === 0) return result;

  const url =
    'https://en.wikipedia.org/w/api.php?' +
    new URLSearchParams({
      action: 'query',
      prop: 'pageimages',
      titles: titles.join('|'),
      pithumbsize: '800',
      format: 'json',
      formatversion: '2',
    });
  let data;
  try {
    data = await wmFetch(url);
  } catch (e) {
    process.stderr.write(`[passB/C batch err] ${e.message}\n`);
    return result;
  }

  const normMap = new Map();
  for (const n of data?.query?.normalized || []) {
    normMap.set(n.to, n.from);
  }
  const redirMap = new Map();
  for (const r of data?.query?.redirects || []) {
    redirMap.set(r.to, r.from);
  }
  const pages = data?.query?.pages || [];
  for (const page of pages) {
    const canonical = page.title;
    // walk redirects -> normalized back to original input
    let key = canonical;
    if (redirMap.has(key)) key = redirMap.get(key);
    if (normMap.has(key)) key = normMap.get(key);
    if (!result.has(key)) {
      // try alternate resolution ordering
      const alt = normMap.get(canonical) || canonical;
      if (result.has(alt)) key = alt;
    }
    if (!result.has(key)) continue;
    if (page.missing) continue;
    const pageimage = page.pageimage || null;
    const thumbUrl = page.thumbnail?.source || null;
    if (!pageimage && !thumbUrl) continue;
    result.set(key, { pageimage, thumbUrl });
  }
  return result;
}

// Pass D (per-recipe): Commons search for "<title> food" in namespace 6 (File:).
// Returns array of File:* titles (up to 5), filtered for obvious junk names.
async function commonsSearchFiles(title) {
  const url =
    'https://commons.wikimedia.org/w/api.php?' +
    new URLSearchParams({
      action: 'query',
      list: 'search',
      srsearch: `${title} food`,
      srnamespace: '6',
      srlimit: '5',
      format: 'json',
      formatversion: '2',
    });
  let data;
  try {
    data = await wmFetch(url);
  } catch (e) {
    process.stderr.write(`[passD err] ${title}: ${e.message}\n`);
    return [];
  }
  const hits = data?.query?.search || [];
  return hits
    .map(h => h.title)          // already namespaced like "File:Foo.jpg"
    .filter(t => !badFilename(t));
}

// ---------- recipe-list loader ----------

function loadRecipes() {
  if (!existsSync(RECIPES_CACHE)) {
    console.log('Fetching wb-% recipes from D1 via wrangler...');
    const sql = `SELECT id, title FROM recipe WHERE id LIKE 'wb-%' AND image_url IS NULL`;
    const cmd = `npx wrangler d1 execute pantrie-db-staging --remote --command="${sql}" --json`;
    const out = execSync(cmd, {
      cwd: fileURLToPath(new URL('../', import.meta.url)),
      maxBuffer: 64 * 1024 * 1024,
      stdio: ['ignore', 'pipe', 'inherit'],
    }).toString('utf8');
    writeFileSync(RECIPES_CACHE, out, 'utf8');
    console.log(`  cached -> ${RECIPES_CACHE}`);
  }
  const parsed = JSON.parse(readFileSync(RECIPES_CACHE, 'utf8'));
  const first = Array.isArray(parsed) ? parsed[0] : parsed;
  const rows = first?.results || [];
  return rows.filter(r => r && r.id && r.title);
}

function loadExistingResults() {
  if (!RESUME) return [];
  if (!existsSync(OUTFILE)) return [];
  try { return JSON.parse(readFileSync(OUTFILE, 'utf8')); }
  catch { return []; }
}

// ---------- per-recipe selection ----------

// From a bag of File:* candidate titles, evaluate each against Commons and return
// the largest passing one (null if none pass).
async function pickLargestValid(fileTitles) {
  const seen = new Set();
  const candidates = [];
  for (const ft of fileTitles) {
    if (!ft || seen.has(ft)) continue;
    seen.add(ft);
    const c = await evaluateFileCandidate(ft);
    if (c) candidates.push(c);
  }
  if (candidates.length === 0) return null;
  candidates.sort((a, b) => (b._w * b._h) - (a._w * a._h));
  const top = candidates[0];
  delete top._w; delete top._h; delete top._file;
  return top;
}

// ---------- main ----------

async function main() {
  const recipes = loadRecipes();
  console.log(`Loaded ${recipes.length} recipe rows (wb-% with NULL image_url).`);

  let work = recipes;
  const existing = loadExistingResults();
  const done = new Set(existing.map(r => r.recipeId));
  if (done.size) {
    work = work.filter(r => !done.has(r.id));
    console.log(`Resuming: ${done.size} already done, ${work.length} remaining.`);
  }
  if (LIMIT > 0) {
    work = work.slice(0, LIMIT);
    console.log(`--limit applied: processing ${work.length} recipes.`);
  }

  const results = [...existing];
  const startedAt = Date.now();
  let firstPrinted = false;

  // Per-recipe state across passes: { recipe, candidateFiles: Set<string>, hit: result|null, passHit: string|null }
  const state = new Map();
  for (const r of work) state.set(r.id, { recipe: r, candidateFiles: new Set(), hit: null, passHit: null });

  // Chunk into batches of BATCH_SIZE.
  const batches = [];
  for (let i = 0; i < work.length; i += BATCH_SIZE) {
    batches.push(work.slice(i, i + BATCH_SIZE));
  }
  console.log(`Processing ${batches.length} batch(es) of up to ${BATCH_SIZE} recipes.`);

  // -------- Pass A: batched Cookbook images --------
  console.log('\n=== Pass A: Wikibooks Cookbook prop=images ===');
  for (let bi = 0; bi < batches.length; bi++) {
    const batch = batches[bi];
    const passA = await batchCookbookImages(batch);
    for (const [rid, files] of passA) {
      const st = state.get(rid);
      if (!st) continue;
      for (const ft of files) st.candidateFiles.add(ft);
    }
    process.stdout.write(`  batch ${bi + 1}/${batches.length} done\n`);
  }

  // -------- Pass B: batched Wikipedia pageimages (ALWAYS run, regardless of A) --------
  console.log('\n=== Pass B: Wikipedia prop=pageimages (raw title) ===');
  for (let bi = 0; bi < batches.length; bi++) {
    const batch = batches[bi];
    const titles = batch.map(r => r.title);
    const passB = await batchWikipediaPageImages(titles);
    for (const r of batch) {
      const v = passB.get(r.title);
      if (!v) continue;
      const st = state.get(r.id);
      if (!st) continue;
      if (v.pageimage) st.candidateFiles.add(`File:${v.pageimage}`);
    }
    process.stdout.write(`  batch ${bi + 1}/${batches.length} done\n`);
  }

  // -------- Pass C: Wikipedia title variants (base noun, "(dish)", "(food)") --------
  // We run each variant batched across recipes that still need improvement. "Still need" here
  // just means: we always add any extra candidates found -- the largest-wins logic picks the best.
  const variants = [
    { name: 'base-noun', map: r => stripCookingVerb(r.title) },
    { name: '(dish)',    map: r => `${r.title} (dish)` },
    { name: '(food)',    map: r => `${r.title} (food)` },
  ];
  for (const variant of variants) {
    console.log(`\n=== Pass C (${variant.name}): Wikipedia prop=pageimages ===`);
    // Build a mapping: variantTitle -> [recipeId, ...] so we can back-propagate.
    const titleToRecipes = new Map();
    for (const r of work) {
      const vt = variant.map(r);
      if (!vt) continue;
      if (!titleToRecipes.has(vt)) titleToRecipes.set(vt, []);
      titleToRecipes.get(vt).push(r.id);
    }
    const allTitles = [...titleToRecipes.keys()];
    if (allTitles.length === 0) {
      console.log('  (no variant titles to query)');
      continue;
    }
    // Batch the variant titles.
    for (let i = 0; i < allTitles.length; i += BATCH_SIZE) {
      const chunk = allTitles.slice(i, i + BATCH_SIZE);
      const res = await batchWikipediaPageImages(chunk);
      for (const vt of chunk) {
        const v = res.get(vt);
        if (!v || !v.pageimage) continue;
        const rids = titleToRecipes.get(vt) || [];
        for (const rid of rids) {
          const st = state.get(rid);
          if (!st) continue;
          st.candidateFiles.add(`File:${v.pageimage}`);
        }
      }
      process.stdout.write(`  variant ${variant.name} batch ${Math.floor(i / BATCH_SIZE) + 1}/${Math.ceil(allTitles.length / BATCH_SIZE)} done\n`);
    }
  }

  // -------- Evaluate A+B+C pooled candidates, pick largest. Track who still needs D. --------
  console.log('\n=== Evaluating pooled A+B+C candidates per recipe ===');
  const needsPassD = [];
  let i = 0;
  for (const rec of work) {
    i++;
    const st = state.get(rec.id);
    const tag = `[${i}/${work.length}] ${rec.id}`;
    if (!st || st.candidateFiles.size === 0) {
      process.stdout.write(`${tag} (no A/B/C candidates) -> Pass D\n`);
      needsPassD.push(rec);
      continue;
    }
    const hit = await pickLargestValid([...st.candidateFiles]);
    if (hit) {
      st.hit = hit;
      st.passHit = 'ABC';
      results.push({ recipeId: rec.id, ...hit });
      process.stdout.write(`${tag} OK  (A/B/C)  ${hit.licenseShort}\n`);
      if (!firstPrinted) {
        console.log('  first result sample:', JSON.stringify({ recipeId: rec.id, ...hit }, null, 2));
        firstPrinted = true;
      }
    } else {
      needsPassD.push(rec);
    }

    if (i % 50 === 0) {
      writeFileSync(OUTFILE, JSON.stringify(results, null, 2), 'utf8');
    }
  }

  // -------- Pass D: Commons search fallback --------
  console.log(`\n=== Pass D: Commons list=search fallback (${needsPassD.length} recipes) ===`);
  let di = 0;
  for (const rec of needsPassD) {
    di++;
    const tag = `[D ${di}/${needsPassD.length}] ${rec.id}`;
    let searchFiles = [];
    try {
      searchFiles = await commonsSearchFiles(rec.title);
    } catch (e) {
      process.stderr.write(`[passD err] ${rec.id}: ${e.message}\n`);
    }
    // Relevance filter: drop results whose filename has no token overlap with the title or
    // looks like a scanned book/manual/poster. This avoids PDFs that squeaked past the ext filter.
    searchFiles = searchFiles.filter(t => searchResultLooksRelevant(t, rec.title));
    if (searchFiles.length === 0) {
      process.stdout.write(`${tag} --  no search hits\n`);
      continue;
    }
    const hit = await pickLargestValid(searchFiles);
    if (hit) {
      const st = state.get(rec.id);
      if (st) { st.hit = hit; st.passHit = 'D'; }
      results.push({ recipeId: rec.id, ...hit });
      process.stdout.write(`${tag} OK  (D)  ${hit.licenseShort}\n`);
      if (!firstPrinted) {
        console.log('  first result sample:', JSON.stringify({ recipeId: rec.id, ...hit }, null, 2));
        firstPrinted = true;
      }
    } else {
      process.stdout.write(`${tag} --  no usable image\n`);
    }
    if (di % 50 === 0) {
      writeFileSync(OUTFILE, JSON.stringify(results, null, 2), 'utf8');
    }
  }

  writeFileSync(OUTFILE, JSON.stringify(results, null, 2), 'utf8');

  const elapsedSec = (Date.now() - startedAt) / 1000;
  const processed = work.length;
  const foundNew = results.length - existing.length;
  const pct = processed ? ((foundNew / processed) * 100).toFixed(1) : '0.0';

  // License breakdown across the full results array (new + resumed).
  const licCounts = {};
  for (const r of results) {
    const k = r.licenseShort || '(unknown)';
    licCounts[k] = (licCounts[k] || 0) + 1;
  }
  const licSorted = Object.entries(licCounts).sort((a, b) => b[1] - a[1]);

  // Pass-contribution breakdown (new hits only).
  let abcHits = 0, dHits = 0;
  for (const st of state.values()) {
    if (st.passHit === 'ABC') abcHits++;
    else if (st.passHit === 'D') dHits++;
  }

  console.log('\n==== SUMMARY ====');
  console.log(`Processed this run : ${processed}`);
  console.log(`Photos found (new) : ${foundNew}  (${pct}% hit rate)`);
  console.log(`  via A/B/C        : ${abcHits}`);
  console.log(`  via Pass D       : ${dHits}`);
  console.log(`Total in output    : ${results.length}`);
  console.log(`Runtime            : ${elapsedSec.toFixed(1)}s  (${callsMade} API calls)`);
  console.log(`Output             : ${OUTFILE}`);
  console.log('License breakdown  :');
  for (const [lic, n] of licSorted) console.log(`  ${n.toString().padStart(4)}  ${lic}`);

  if (processed > 0) {
    const callsPerRecipe = callsMade / processed;
    const totalRecipes = recipes.length;
    const projSec = callsPerRecipe * totalRecipes * (RATE_MS / 1000);
    console.log(`Projected full run: ~${(projSec / 60).toFixed(1)} min ` +
                `(${callsPerRecipe.toFixed(2)} calls/recipe x ${totalRecipes} recipes)`);
  }

  if (processed >= 20 && (foundNew / processed) < 0.30) {
    console.log('\n!!! Hit rate < 30% — flagging per spec. Consider stopping before full run.');
  }
}

main().catch(e => { console.error(e); process.exit(1); });

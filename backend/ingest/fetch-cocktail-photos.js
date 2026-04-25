// Harvest CC-licensed cocktail photos for the recipe table.
//
// Strategy (per cocktail):
//   Pass 1  Openverse  -> CC0/CC-BY/CC-BY-SA images, commercial+modification allowed
//   Pass 2  Wikimedia Commons list=search namespace=6 (File:) -> per-recipe fallback
//
// Output: ingest/normalized/cocktail-photos.json  (array of { recipeId, imageUrl,
//         author, licenseShort, licenseUrl, sourceUrl, source })
//
// NOTE: This script ONLY writes the JSON file. It does not modify D1 directly.
//       A separate `--apply` step issues UPDATEs.
//
// Usage:
//   node ingest/fetch-cocktail-photos.js                  # fetch using built-in priority list
//   node ingest/fetch-cocktail-photos.js --limit 20       # smoke test
//   node ingest/fetch-cocktail-photos.js --resume         # keep existing results, fill gaps
//   node ingest/fetch-cocktail-photos.js --apply          # apply collected photos to D1

import { writeFileSync, readFileSync, existsSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import { execSync } from 'node:child_process';

// ---------- config ----------

const UA = 'Pan-Tree-Cocktail-Ingest/1.0 (https://pan-tree.app; schulgenkyle@gmail.com)';
const RATE_MS = 800;                 // global gate across BOTH APIs
const REQUEST_TIMEOUT_MS = 25000;
const MAX_RETRIES = 3;

const OUTDIR  = fileURLToPath(new URL('./normalized/', import.meta.url));
const OUTFILE = join(OUTDIR, 'cocktail-photos.json');
const SKIPFILE = join(OUTDIR, 'cocktail-photos-skipped.json');
mkdirSync(OUTDIR, { recursive: true });

// Accepted Openverse license codes -> normalized short name.
// (Openverse `license` field is lowercase: cc0, by, by-sa, by-nc, by-nd, by-nc-nd, by-nc-sa, pdm, sampling+, ...)
const OV_OK_LICENSES = new Set(['cc0', 'by', 'by-sa', 'pdm']);
const OV_LICENSE_NAME = {
  'cc0':   'CC0',
  'by':    'CC BY',
  'by-sa': 'CC BY-SA',
  'pdm':   'Public Domain Mark',
};

// Wikimedia Commons accepted prefixes (matches existing wikimedia script).
const WM_OK_PREFIXES = ['CC0', 'CC BY', 'CC BY-SA', 'Public domain', 'PD'];

const ALLOWED_MIMES = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
const MIN_WIDTH  = 400;
const MIN_HEIGHT = 400;

// Reject filenames that obviously aren't drink photos.
const BAD_NAME_BITS = ['flag', 'icon', 'logo', 'map', 'bottle', 'label_', 'menu', 'sign_', 'advertisement', 'advert_'];
const BAD_EXTENSIONS = ['.svg', '.pdf', '.djvu', '.tif', '.tiff', '.ogv', '.webm', '.ogg', '.mp4', '.gif'];

// ---------- CLI args ----------

const argv = process.argv.slice(2);
function argVal(name) {
  const i = argv.indexOf(name);
  if (i < 0) return null;
  return argv[i + 1];
}
const LIMIT  = Number(argVal('--limit')) || 0;
const RESUME = argv.includes('--resume');
const APPLY  = argv.includes('--apply');
const QUERY  = argVal('--query');  // overrides built-in priority WHERE clause

// ---------- target priority list (lowercase title match) ----------
// Famous tiki, classic, and modern craft cocktails. Wide coverage = more hits.

const PRIORITY_TITLES = [
  // tiki
  'mai tai', 'zombie', 'painkiller', 'navy grog', 'jungle bird', 'hurricane',
  'planters punch', "planter's punch", 'fog cutter', 'scorpion', 'singapore sling',
  'blue hawaii', 'pina colada', 'piña colada', 'piÃ±a colada',
  'dark and stormy', "dark 'n' stormy", 'rum runner', 'bahama mama',
  'suffering bastard', 'mary pickford', 'el presidente', 'hemingway daiquiri',
  'queen\'s park swizzle', 'queens park swizzle', 'chartreuse swizzle', 'airmail',
  // modern craft
  'penicillin', 'gold rush', 'last word', 'paper plane', 'naked and famous',
  'bramble', 'espresso martini', 'aperol spritz', 'vesper', 'tommy\'s margarita',
  'oaxaca old fashioned', 'mezcal mule', 'division bell', 'la paloma', 'batanga',
  // classic / pre-prohibition / historic-book famous names
  'sazerac', 'ramos gin fizz', 'vieux carre', 'vieux carré', 'brandy crusta',
  'japanese cocktail', 'blue blazer', 'tom and jerry', 'milk punch',
  'fish house punch', 'brandy punch', 'widow\'s kiss', 'seelbach',
  'bee\'s knees', 'bees knees', 'aviation', 'corpse reviver', 'corpse reviver no 2',
  'clover club', 'french 75', 'sidecar', 'between the sheets', 'ward 8',
  'jack rose', 'income tax', 'blood and sand', 'hanky panky', 'rob roy',
  'boulevardier', 'old pal', 'toronto', 'remember the maine',
  'floridita daiquiri', 'el diablo', 'paloma', 'margarita', 'mexican firing squad',
  'smoky margarita',
  // mocktails / zero-proof
  'virgin mojito', 'virgin pina colada', 'virgin colada',
  'shirley temple', 'arnold palmer', 'roy rogers', 'virgin mary', 'nojito',
  'seedlip garden', 'seedlip spice',
  // historic / pre-prohibition long-tail (Jerry Thomas / Kappeler / Engel / Bullock / Straub / Grohusko)
  'champagne cocktail', 'champagne cobbler', 'champagne cup', 'champagne julep',
  'champagne punch', 'champagne sour',
  'brandy flip', 'brandy smash', 'brandy cocktail', 'brandy daisy', 'brandy fix',
  'brandy sour', 'brandy float', 'brandy sangaree', 'brandy scaffa', 'brandy toddy',
  'brandy and soda', 'brandy fizz', 'brandy julep', 'brandy and ginger ale',
  'ale sangaree', 'ale flip', 'bishop', 'black stripe',
  'claret punch', 'claret cup', 'claret cobbler', 'claret lemonade',
  'coffee cocktail', 'jersey cocktail', 'knickerbocker', 'sherry cobbler',
  'soda cocktail', 'whiskey cocktail', 'whiskey cobbler', 'whiskey sour',
  'whiskey punch', 'whiskey toddy', 'whiskey skin', 'whiskey daisy',
  'whiskey fix', 'whiskey smash', 'whiskey fizz', 'whiskey sling',
  'absinthe cocktail', 'absinthe frappe', 'apple toddy', 'bacardi cocktail',
  'catawba cobbler', 'chocolate cocktail', 'cider cup', 'clover leaf cocktail',
  'egg flip', 'egg sour', 'egg lemonade', 'gin cocktail', 'gin punch',
  'gin sling', 'gin fizz', 'gin sour', 'gin daisy', 'gin smash', 'gin toddy',
  'hot spiced rum', 'manhattan cocktail', 'manhattan', 'martini cocktail',
  'dry martini', 'gin martini', 'vodka martini',
  'peach and honey', 'port wine negus', 'port wine sangaree',
  'remsen cooler', 'roman punch', 'sherry and bitters', 'sherry flip',
  'sherry sangaree', 'star cocktail', 'vanilla punch', 'white lion',
  'alexander cocktail', 'brandy alexander', 'arrack punch', 'baltimore egg nogg',
  'eggnog', 'egg nog', 'bamboo cocktail', 'boston cooler',
  'bronx cocktail', 'brooklyn cocktail', 'curacao punch', 'curacoa punch',
  'columbia skin', 'mint julep', 'mojito',
  // contemporary classics frequently photographed
  'old fashioned', 'whiskey old fashioned', 'rum old fashioned',
  'daiquiri', 'classic daiquiri', 'frozen daiquiri', 'strawberry daiquiri',
  'cosmopolitan', 'long island iced tea', 'tom collins', 'john collins',
  'whiskey collins', 'vodka collins', 'rum collins',
  'caipirinha', 'caipiroska', 'moscow mule', 'kentucky mule', 'whiskey sour',
  'amaretto sour', 'whisky sour', 'pisco sour',
  'negroni', 'negroni sbagliato', 'white negroni', 'mezcal negroni',
  'manhattan perfect', 'rusty nail', 'gimlet', 'gin gimlet',
  'kir', 'kir royale', 'mimosa', 'bellini', 'rossini',
  'b-52', 'b 52', 'irish coffee', 'kahlua coffee', 'spanish coffee',
  'white russian', 'black russian', 'mudslide', 'grasshopper',
  'tequila sunrise', 'tequila sour', 'el matador',
  'salty dog', 'screwdriver', 'sea breeze', 'bay breeze', 'cape codder',
  'gin and tonic', 'vodka tonic', 'rum and coke', 'cuba libre',
  'highball', 'whisky highball', 'whiskey highball', 'mamie taylor',
  'horse\'s neck', 'horses neck', 'pimms cup', 'pimm\'s cup',
];

// ---------- utils ----------

const sleep = ms => new Promise(r => setTimeout(r, ms));

function stripTags(s) {
  if (!s) return '';
  return String(s).replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();
}

function badFilename(name) {
  const lower = String(name || '').toLowerCase();
  for (const ext of BAD_EXTENSIONS) {
    if (lower.endsWith(ext)) return true;
  }
  return BAD_NAME_BITS.some(bit => lower.includes(bit));
}

function matchesWMLicense(shortName) {
  if (!shortName) return false;
  const s = String(shortName).trim().toUpperCase();
  // Reject anything containing "NC" or "ND" even if it starts with "CC BY"
  if (s.includes('-NC') || s.includes(' NC') || s.includes('-ND') || s.includes(' ND')) return false;
  return WM_OK_PREFIXES.some(p => s.startsWith(p.toUpperCase()));
}

// Build the search query for an API: lowercase, dedupe "cocktail", url-encode handled by URLSearchParams.
function searchQuery(title) {
  const t = String(title || '').toLowerCase();
  if (t.includes('cocktail') || t.includes('punch') || t.includes('martini') || t.includes('fizz') || t.includes('sling')) {
    return t;  // already category-like
  }
  return `${t} cocktail`;
}

// ---------- rate-limited fetch ----------

let lastCallAt = 0;
let callsMade = 0;
let rateLimitHits = 0;

async function rateGate() {
  const now = Date.now();
  const wait = Math.max(0, lastCallAt + RATE_MS - now);
  if (wait > 0) await sleep(wait);
  lastCallAt = Date.now();
}

async function rfetch(url, opts = {}) {
  await rateGate();
  callsMade++;
  let lastErr;
  for (let attempt = 1; attempt <= MAX_RETRIES; attempt++) {
    const ctl = new AbortController();
    const t = setTimeout(() => ctl.abort(), REQUEST_TIMEOUT_MS);
    try {
      const res = await fetch(url, {
        headers: { 'User-Agent': UA, 'Accept': 'application/json', ...(opts.headers || {}) },
        signal: ctl.signal,
      });
      clearTimeout(t);
      if (res.status === 429) {
        rateLimitHits++;
        await sleep(3000 * attempt);
        continue;
      }
      if (res.status >= 500) {
        lastErr = new Error(`HTTP ${res.status}`);
        await sleep(2000 * attempt);
        continue;
      }
      if (!res.ok) {
        // 4xx other than 429 -> give up for this URL
        return null;
      }
      return await res.json();
    } catch (e) {
      clearTimeout(t);
      lastErr = e;
      if (attempt < MAX_RETRIES) await sleep(1500 * attempt);
    }
  }
  if (lastErr) process.stderr.write(`[fetch err] ${url.slice(0, 120)}: ${lastErr.message}\n`);
  return null;
}

// HEAD request to verify the image URL responds 200 with an allowed image MIME.
async function headOk(url) {
  await rateGate();
  callsMade++;
  try {
    const ctl = new AbortController();
    const t = setTimeout(() => ctl.abort(), REQUEST_TIMEOUT_MS);
    const res = await fetch(url, { method: 'HEAD', headers: { 'User-Agent': UA }, signal: ctl.signal });
    clearTimeout(t);
    if (!res.ok) return false;
    const ct = String(res.headers.get('content-type') || '').toLowerCase();
    return ALLOWED_MIMES.some(m => ct.startsWith(m));
  } catch {
    return false;
  }
}

// ---------- Pass 1: Openverse ----------

async function searchOpenverse(title) {
  const q = searchQuery(title);
  // license_type=commercial,modification narrows server-side; license=cc0,by,by-sa,pdm is belt-and-suspenders.
  const params = new URLSearchParams({
    q,
    page_size: '20',
    license: 'cc0,by,by-sa,pdm',
    license_type: 'commercial,modification',
    mature: 'false',
  });
  const url = `https://api.openverse.org/v1/images/?${params}`;
  const data = await rfetch(url);
  if (!data || !Array.isArray(data.results)) return [];
  const out = [];
  for (const r of data.results) {
    const lic = String(r.license || '').toLowerCase();
    if (!OV_OK_LICENSES.has(lic)) continue;
    const w = Number(r.width || 0);
    const h = Number(r.height || 0);
    if (w && w < MIN_WIDTH) continue;
    if (h && h < MIN_HEIGHT) continue;
    if (!r.url) continue;
    if (badFilename(r.url)) continue;
    out.push({
      imageUrl: r.url,
      author: r.creator || null,
      authorUrl: r.creator_url || null,
      licenseShort: OV_LICENSE_NAME[lic] || lic.toUpperCase(),
      licenseUrl: r.license_url || null,
      sourceUrl: r.foreign_landing_url || r.url,
      provider: r.provider || r.source || 'openverse',
      _w: w, _h: h,
    });
  }
  // Sort by area desc -> prefer big.
  out.sort((a, b) => (b._w * b._h) - (a._w * a._h));
  return out;
}

// ---------- Pass 2: Wikimedia Commons ----------

async function commonsSearchFiles(title) {
  const q = searchQuery(title);
  const url =
    'https://commons.wikimedia.org/w/api.php?' +
    new URLSearchParams({
      action: 'query',
      list: 'search',
      srsearch: q,
      srnamespace: '6',
      srlimit: '8',
      format: 'json',
      formatversion: '2',
    });
  const data = await rfetch(url);
  if (!data) return [];
  const hits = data?.query?.search || [];
  return hits.map(h => h.title).filter(t => !badFilename(t));
}

async function commonsImageInfo(fileTitle) {
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
  const data = await rfetch(url);
  if (!data) return null;
  const pages = data?.query?.pages || [];
  const page = pages[0];
  if (!page || page.missing) return null;
  return page.imageinfo?.[0] || null;
}

async function evalCommonsCandidate(fileTitle) {
  if (badFilename(fileTitle)) return null;
  const info = await commonsImageInfo(fileTitle);
  if (!info) return null;
  const em = info.extmetadata || {};
  const shortName = em.LicenseShortName?.value;
  if (!matchesWMLicense(shortName)) return null;
  const w = Number(info.width || 0);
  const h = Number(info.height || 0);
  if (w < MIN_WIDTH || h < MIN_HEIGHT) return null;
  const mime = String(info.mime || '').toLowerCase();
  if (!ALLOWED_MIMES.includes(mime)) return null;
  const descUrl = info.descriptionurl ||
    `https://commons.wikimedia.org/wiki/${encodeURIComponent(fileTitle.replace(/ /g, '_'))}`;
  return {
    imageUrl: info.url,
    author: stripTags(em.Artist?.value) || null,
    authorUrl: null,
    licenseShort: String(shortName).trim(),
    licenseUrl: em.LicenseUrl?.value || null,
    sourceUrl: descUrl,
    provider: 'wikimedia',
    _w: w, _h: h,
  };
}

async function pickFromCommons(title) {
  const files = await commonsSearchFiles(title);
  const cands = [];
  for (const ft of files.slice(0, 5)) {
    const c = await evalCommonsCandidate(ft);
    if (c) cands.push(c);
  }
  if (cands.length === 0) return null;
  cands.sort((a, b) => (b._w * b._h) - (a._w * a._h));
  return cands[0];
}

// ---------- D1 helpers ----------

function d1Query(sql) {
  const cmd = `npx wrangler d1 execute pantrie-db-staging --remote --command="${sql.replace(/"/g, '\\"')}" --json`;
  const out = execSync(cmd, {
    cwd: fileURLToPath(new URL('../', import.meta.url)),
    maxBuffer: 64 * 1024 * 1024,
    stdio: ['ignore', 'pipe', 'inherit'],
  }).toString('utf8');
  const parsed = JSON.parse(out);
  const first = Array.isArray(parsed) ? parsed[0] : parsed;
  return first?.results || [];
}

function loadTargets() {
  if (QUERY) {
    return d1Query(QUERY);
  }
  // Build the "in (...)" list with proper SQL quoting for apostrophes.
  const inList = PRIORITY_TITLES
    .map(t => `'${t.replace(/'/g, "''")}'`)
    .join(',');
  const sql =
    `SELECT id, title, source_book FROM recipe ` +
    `WHERE image_url IS NULL AND content_type IN ('cocktail','mocktail') ` +
    `AND LOWER(title) IN (${inList}) ORDER BY title`;
  return d1Query(sql);
}

function loadExisting() {
  if (!RESUME) return [];
  if (!existsSync(OUTFILE)) return [];
  try { return JSON.parse(readFileSync(OUTFILE, 'utf8')); }
  catch { return []; }
}

// ---------- apply mode: push collected photos into D1 ----------

function sqlEsc(v) {
  if (v === null || v === undefined) return 'NULL';
  return `'${String(v).replace(/'/g, "''")}'`;
}

function buildAttribution(rec) {
  const parts = [];
  if (rec.author) parts.push(rec.author);
  if (rec.licenseShort) parts.push(rec.licenseShort);
  if (rec.provider) parts.push(`via ${rec.provider}`);
  return parts.join(' / ');
}

async function applyToD1() {
  if (!existsSync(OUTFILE)) {
    console.error(`No results file at ${OUTFILE}. Run without --apply first.`);
    process.exit(1);
  }
  const records = JSON.parse(readFileSync(OUTFILE, 'utf8'));
  console.log(`Applying ${records.length} photo records to D1...`);
  let ok = 0, fail = 0;
  // Batch updates into a single SQL file to minimize wrangler invocations.
  const stmts = records.map(r => {
    const credit = r.author || 'Unknown';
    const license = r.licenseShort || 'CC';
    const sourceUrl = r.sourceUrl || '';
    const attribution = buildAttribution(r);
    return `UPDATE recipe SET image_url=${sqlEsc(r.imageUrl)}, photo_credit=${sqlEsc(credit)}, photo_license=${sqlEsc(license)}, photo_source_url=${sqlEsc(sourceUrl)}, attribution=${sqlEsc(attribution)} WHERE id=${sqlEsc(r.recipeId)} AND image_url IS NULL;`;
  });
  const tmpFile = join(OUTDIR, 'cocktail-photos-apply.sql');
  writeFileSync(tmpFile, stmts.join('\n'), 'utf8');
  try {
    const cmd = `npx wrangler d1 execute pantrie-db-staging --remote --file="${tmpFile}"`;
    execSync(cmd, {
      cwd: fileURLToPath(new URL('../', import.meta.url)),
      stdio: 'inherit',
      maxBuffer: 64 * 1024 * 1024,
    });
    ok = records.length;
  } catch (e) {
    console.error(`Apply failed: ${e.message}`);
    fail = records.length;
  }
  console.log(`Apply complete: ${ok} ok, ${fail} fail.`);
}

// ---------- main ----------

async function main() {
  if (APPLY) { await applyToD1(); return; }

  const targets = loadTargets();
  console.log(`Loaded ${targets.length} target cocktails (NULL image_url).`);

  let work = targets;
  const existing = loadExisting();
  const done = new Set(existing.map(r => r.recipeId));
  if (done.size) {
    work = work.filter(r => !done.has(r.id));
    console.log(`Resuming: ${done.size} already done, ${work.length} remaining.`);
  }
  if (LIMIT > 0) {
    work = work.slice(0, LIMIT);
    console.log(`--limit applied: processing ${work.length}.`);
  }

  const results = [...existing];
  const skipped = [];
  let ovHits = 0, wmHits = 0;
  const startedAt = Date.now();

  for (let i = 0; i < work.length; i++) {
    const rec = work[i];
    const tag = `[${i + 1}/${work.length}] ${rec.id}  ${rec.title}`;
    let hit = null;

    // Pass 1: Openverse
    try {
      const ovList = await searchOpenverse(rec.title);
      // Pick the first whose URL passes a HEAD check (top is largest).
      for (const c of ovList.slice(0, 5)) {
        if (await headOk(c.imageUrl)) { hit = c; break; }
      }
      if (hit) {
        ovHits++;
        delete hit._w; delete hit._h;
        results.push({ recipeId: rec.id, source: 'openverse', ...hit });
        process.stdout.write(`${tag} OK (openverse, ${hit.licenseShort})\n`);
        if (results.length % 10 === 0) writeFileSync(OUTFILE, JSON.stringify(results, null, 2), 'utf8');
        continue;
      }
    } catch (e) {
      process.stderr.write(`[ov err] ${rec.id}: ${e.message}\n`);
    }

    // Pass 2: Wikimedia Commons
    try {
      const wm = await pickFromCommons(rec.title);
      if (wm && await headOk(wm.imageUrl)) {
        wmHits++;
        delete wm._w; delete wm._h;
        results.push({ recipeId: rec.id, source: 'wikimedia', ...wm });
        process.stdout.write(`${tag} OK (wikimedia, ${wm.licenseShort})\n`);
        if (results.length % 10 === 0) writeFileSync(OUTFILE, JSON.stringify(results, null, 2), 'utf8');
        continue;
      }
    } catch (e) {
      process.stderr.write(`[wm err] ${rec.id}: ${e.message}\n`);
    }

    process.stdout.write(`${tag} -- skipped (no image)\n`);
    skipped.push({ recipeId: rec.id, title: rec.title, source_book: rec.source_book || null });
  }

  writeFileSync(OUTFILE, JSON.stringify(results, null, 2), 'utf8');
  writeFileSync(SKIPFILE, JSON.stringify(skipped, null, 2), 'utf8');

  const elapsedSec = (Date.now() - startedAt) / 1000;
  console.log('\n==== SUMMARY ====');
  console.log(`Processed         : ${work.length}`);
  console.log(`Photos found      : ${results.length - existing.length}`);
  console.log(`  via Openverse   : ${ovHits}`);
  console.log(`  via Wikimedia   : ${wmHits}`);
  console.log(`Skipped           : ${skipped.length}`);
  console.log(`API calls         : ${callsMade}`);
  console.log(`Rate-limit hits   : ${rateLimitHits}`);
  console.log(`Runtime           : ${elapsedSec.toFixed(1)}s`);
  console.log(`Results           : ${OUTFILE}`);
  console.log(`Skipped log       : ${SKIPFILE}`);
  console.log('\nNext step: review results, then run with --apply to push to D1.');
}

main().catch(e => { console.error(e); process.exit(1); });

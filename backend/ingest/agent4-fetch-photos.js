// Agent 4: Harvest CC/PD cocktail photos from sources outside Openverse/Wikimedia.
//
// Strategy per cocktail title:
//   Pass 1  Library of Congress (loc.gov)        -> "no known restrictions" PD
//   Pass 2  Wikimedia Commons (CC0/CC BY/CC BY-SA/PD) — different query angles than fetch-cocktail-photos.js
//   Pass 3  Openverse limited to Flickr Commons + Smithsonian providers (PD)
//
// Sources NOT reachable here (require API keys we don't have):
//   - Pexels (Cloudflare blocks page scraping)
//   - Pixabay
//   - Unsplash
//   - Smithsonian direct API (api_key required)
//   - NYPL (HTTP token required)
//   - Flickr direct API (api key required) -> partial coverage via Openverse provider=flickr
//
// Output: ingest/normalized/agent4-photos.json
//         ingest/normalized/agent4-report.md

import { writeFileSync, readFileSync, existsSync, mkdirSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const UA = 'Pan-Tree-Cocktail-Ingest/1.0 (https://pan-tree.app; schulgenkyle@gmail.com)';
const RATE_MS_DEFAULT = 1100;
const REQUEST_TIMEOUT_MS = 25000;
const MAX_RETRIES = 3;
const MIN_WIDTH = 400;
const MIN_HEIGHT = 400;
const MAX_PHOTOS_PER_TITLE = 4;
const TARGET_TOTAL = 1500;

const OUTDIR  = fileURLToPath(new URL('./normalized/', import.meta.url));
const OUTFILE = join(OUTDIR, 'agent4-photos.json');
const REPORTFILE = join(OUTDIR, 'agent4-report.md');
const SKIPFILE = join(OUTDIR, 'cocktail-photos-skipped.json');
const COCKTAILS_DIR = fileURLToPath(new URL('./cocktails/', import.meta.url));
mkdirSync(OUTDIR, { recursive: true });

const ALLOWED_MIMES = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
const BAD_NAME_BITS = ['flag', 'icon', 'logo', 'map', 'label_', 'menu', 'sign_', 'advertisement', 'advert_', 'trademark'];
const BAD_EXTENSIONS = ['.svg', '.pdf', '.djvu', '.tif', '.tiff', '.ogv', '.webm', '.ogg', '.mp4', '.gif'];

const LOC_OK_RIGHTS = [
  'no known restrictions on publication',
  'no known restrictions',
  'public domain',
];

const WM_OK_PREFIXES = ['CC0', 'CC BY', 'CC BY-SA', 'Public domain', 'PD'];

const OV_OK_LICENSES = new Set(['cc0', 'by', 'by-sa', 'pdm']);
const OV_LICENSE_NAME = {
  'cc0': 'CC0',
  'by': 'CC BY',
  'by-sa': 'CC BY-SA',
  'pdm': 'Public Domain Mark',
};
const OV_PD_PROVIDERS = new Set(['flickr', 'smithsonian', 'smithsonian_institution', 'museumsvictoria', 'rawpixel']);

const argv = process.argv.slice(2);
function argVal(name) {
  const i = argv.indexOf(name);
  if (i < 0) return null;
  return argv[i + 1];
}
const LIMIT = Number(argVal('--limit')) || 0;
const RESUME = argv.includes('--resume');
const SKIP_OV = argv.includes('--no-openverse');
const ONLY = argVal('--only');

const sleep = ms => new Promise(r => setTimeout(r, ms));

function stripTags(s) {
  if (!s) return '';
  return String(s).replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim();
}

function badFilename(name) {
  const lower = String(name || '').toLowerCase();
  for (const ext of BAD_EXTENSIONS) if (lower.endsWith(ext)) return true;
  return BAD_NAME_BITS.some(bit => lower.includes(bit));
}

function matchesWMLicense(shortName) {
  if (!shortName) return false;
  const s = String(shortName).trim().toUpperCase();
  if (s.includes('-NC') || s.includes(' NC') || s.includes('-ND') || s.includes(' ND')) return false;
  return WM_OK_PREFIXES.some(p => s.startsWith(p.toUpperCase()));
}

function matchesLocRights(advisory) {
  if (!advisory) return false;
  const list = Array.isArray(advisory) ? advisory : [advisory];
  for (const v of list) {
    const s = String(v || '').toLowerCase();
    if (LOC_OK_RIGHTS.some(p => s.includes(p))) return true;
  }
  return false;
}

function searchQuery(title, suffix) {
  const t = String(title || '').toLowerCase().trim();
  if (!suffix) return t;
  if (t.includes(suffix)) return t;
  if (/\b(punch|martini|fizz|sling|cobbler|toddy|julep|smash|sour|sangaree|cooler|flip|negus|skin|float|scaffa|lemonade|nogg|nog|highball|collins)\b/.test(t)) return t;
  return `${t} ${suffix}`;
}

const lastCall = new Map();
async function rateGate(provider, ms) {
  const wait = Math.max(0, (lastCall.get(provider) || 0) + (ms || RATE_MS_DEFAULT) - Date.now());
  if (wait > 0) await sleep(wait);
  lastCall.set(provider, Date.now());
}

let callsMade = 0;
let rateLimitHits = 0;

async function rfetch(provider, url, opts = {}) {
  await rateGate(provider, opts.rateMs);
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
      if (!res.ok) return null;
      const txt = await res.text();
      try { return JSON.parse(txt); } catch { return null; }
    } catch (e) {
      clearTimeout(t);
      lastErr = e;
      if (attempt < MAX_RETRIES) await sleep(1500 * attempt);
    }
  }
  if (lastErr) process.stderr.write(`[${provider} err] ${url.slice(0, 120)}: ${lastErr.message}\n`);
  return null;
}

async function headOk(provider, url) {
  await rateGate(provider, 400);
  callsMade++;
  try {
    const ctl = new AbortController();
    const t = setTimeout(() => ctl.abort(), REQUEST_TIMEOUT_MS);
    const res = await fetch(url, { method: 'HEAD', headers: { 'User-Agent': UA }, signal: ctl.signal });
    clearTimeout(t);
    if (!res.ok) return false;
    const ct = String(res.headers.get('content-type') || '').toLowerCase();
    return ALLOWED_MIMES.some(m => ct.startsWith(m));
  } catch { return false; }
}

function parseLocImageUrl(s) {
  if (!s) return null;
  const [bare, hash] = String(s).split('#');
  let w = 0, h = 0;
  if (hash) {
    const m1 = hash.match(/w=(\d+)/);
    const m2 = hash.match(/h=(\d+)/);
    if (m1) w = Number(m1[1]);
    if (m2) h = Number(m2[1]);
  }
  return { url: bare, w, h };
}

function pickBestLocImage(urls) {
  if (!Array.isArray(urls)) return null;
  const parsed = urls.map(parseLocImageUrl).filter(Boolean);
  const big = parsed.filter(x => x.w >= MIN_WIDTH && x.h >= MIN_HEIGHT);
  if (big.length) {
    big.sort((a, b) => (b.w * b.h) - (a.w * a.h));
    return big[0];
  }
  const noDims = parsed.filter(x => !x.w && !x.h);
  if (noDims.length) return noDims[noDims.length - 1];
  return null;
}

async function searchLoc(title) {
  const q = searchQuery(title, '');
  const url = 'https://www.loc.gov/photos/?' + new URLSearchParams({
    fa: 'access-restricted:false|online-format:image',
    q,
    fo: 'json',
    c: '15',
  });
  const data = await rfetch('loc', url, { rateMs: 1200 });
  if (!data || !Array.isArray(data?.results)) return [];
  const out = [];
  for (const r of data.results) {
    if (!matchesLocRights(r.rights_advisory || r.rights_information)) continue;
    if (badFilename(r.title || '')) continue;
    const best = pickBestLocImage(r.image_url);
    if (!best || !best.url) continue;
    if (badFilename(best.url)) continue;
    if (best.w && best.w < MIN_WIDTH) continue;
    if (best.h && best.h < MIN_HEIGHT) continue;
    const item = r.item || {};
    const author = stripTags(
      Array.isArray(item.creator) ? item.creator.join(', ') :
      (item.creator || (Array.isArray(item.contributor_names) ? item.contributor_names[0] : '') || 'Library of Congress')
    );
    out.push({
      imageUrl: best.url,
      author,
      authorUrl: null,
      licenseShort: 'No known copyright restrictions',
      licenseUrl: 'https://www.loc.gov/legal/',
      sourceUrl: r.id || item.link || null,
      provider: 'loc',
      _w: best.w || 0, _h: best.h || 0,
    });
    if (out.length >= 6) break;
  }
  return out;
}

async function commonsSearchFiles(query) {
  const url = 'https://commons.wikimedia.org/w/api.php?' + new URLSearchParams({
    action: 'query', list: 'search', srsearch: query, srnamespace: '6',
    srlimit: '8', format: 'json', formatversion: '2',
  });
  const data = await rfetch('wikimedia', url, { rateMs: 1100 });
  if (!data) return [];
  return (data?.query?.search || []).map(h => h.title).filter(t => !badFilename(t));
}

async function commonsImageInfo(fileTitle) {
  const url = 'https://commons.wikimedia.org/w/api.php?' + new URLSearchParams({
    action: 'query', titles: fileTitle, prop: 'imageinfo',
    iiprop: 'url|extmetadata|size|mime', format: 'json', formatversion: '2',
  });
  const data = await rfetch('wikimedia', url, { rateMs: 1100 });
  if (!data) return null;
  const pages = data?.query?.pages || [];
  return pages[0]?.imageinfo?.[0] || null;
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
  const descUrl = info.descriptionurl || `https://commons.wikimedia.org/wiki/${encodeURIComponent(fileTitle.replace(/ /g, '_'))}`;
  return {
    imageUrl: info.url,
    author: stripTags(em.Artist?.value) || 'Unknown',
    authorUrl: null,
    licenseShort: String(shortName).trim(),
    licenseUrl: em.LicenseUrl?.value || null,
    sourceUrl: descUrl,
    provider: 'wikimedia',
    _w: w, _h: h, _file: fileTitle,
  };
}

async function pickFromCommons(title) {
  const queries = [`${title} drink`, `${title} glass`, `${title} bar`];
  const seenFiles = new Set();
  const hits = [];
  for (const q of queries) {
    const files = await commonsSearchFiles(q);
    for (const ft of files.slice(0, 4)) {
      if (seenFiles.has(ft)) continue;
      seenFiles.add(ft);
      const c = await evalCommonsCandidate(ft);
      if (c) hits.push(c);
      if (hits.length >= 3) break;
    }
    if (hits.length >= 3) break;
  }
  return hits;
}

async function searchOpenverse(title) {
  if (SKIP_OV) return [];
  const q = searchQuery(title, 'cocktail');
  const params = new URLSearchParams({
    q, page_size: '20', license: 'cc0,by,by-sa,pdm',
    license_type: 'commercial,modification', mature: 'false',
  });
  const url = `https://api.openverse.org/v1/images/?${params}`;
  const data = await rfetch('openverse', url, { rateMs: 900 });
  if (!data || !Array.isArray(data.results)) return [];
  const out = [];
  for (const r of data.results) {
    const lic = String(r.license || '').toLowerCase();
    if (!OV_OK_LICENSES.has(lic)) continue;
    const provider = String(r.provider || r.source || '').toLowerCase();
    if (!OV_PD_PROVIDERS.has(provider)) continue;
    const w = Number(r.width || 0);
    const h = Number(r.height || 0);
    if (w && w < MIN_WIDTH) continue;
    if (h && h < MIN_HEIGHT) continue;
    if (!r.url) continue;
    if (badFilename(r.url)) continue;
    // Note: Openverse's `provider=flickr` includes BOTH Flickr Commons institutional
    // photos AND regular CC-licensed Flickr uploads. Without inspecting the
    // foreign_landing_url owner against the Flickr Commons member list, we cannot
    // reliably distinguish them, so we label these as `flickr` (CC license still
    // permits commercial+modification, so they're still acceptable for pantree).
    let providerLabel = provider;
    if (provider.startsWith('smithsonian')) providerLabel = 'smithsonian';
    out.push({
      imageUrl: r.url,
      author: r.creator || null,
      authorUrl: r.creator_url || null,
      licenseShort: OV_LICENSE_NAME[lic] || lic.toUpperCase(),
      licenseUrl: r.license_url || null,
      sourceUrl: r.foreign_landing_url || r.url,
      provider: providerLabel,
      _w: w, _h: h,
    });
  }
  out.sort((a, b) => (b._w * b._h) - (a._w * a._h));
  return out.slice(0, 4);
}

function loadSkipped() {
  if (!existsSync(SKIPFILE)) return [];
  try { return JSON.parse(readFileSync(SKIPFILE, 'utf8')); } catch { return []; }
}

function loadFamousTitles() {
  const list = [
    'mai tai','zombie cocktail','painkiller cocktail','navy grog','jungle bird','hurricane cocktail',
    "planter's punch",'fog cutter','scorpion bowl','singapore sling','blue hawaii cocktail',
    'pina colada','dark and stormy','rum runner','bahama mama','suffering bastard',
    'mary pickford cocktail','el presidente cocktail','hemingway daiquiri',
    "queen's park swizzle",'chartreuse swizzle','airmail cocktail',
    'penicillin cocktail','gold rush cocktail','last word cocktail','paper plane cocktail','naked and famous cocktail',
    'bramble cocktail','espresso martini','aperol spritz','vesper cocktail',
    "tommy's margarita",'oaxaca old fashioned','mezcal mule','division bell cocktail',
    'la paloma','batanga','sazerac','ramos gin fizz','vieux carre cocktail','brandy crusta',
    'japanese cocktail','blue blazer cocktail','tom and jerry cocktail','milk punch',
    'fish house punch','widows kiss cocktail','seelbach cocktail',
    "bee's knees cocktail",'aviation cocktail','corpse reviver no 2','clover club cocktail',
    'french 75 cocktail','sidecar cocktail','between the sheets cocktail','ward 8 cocktail',
    'jack rose cocktail','blood and sand cocktail','hanky panky cocktail','rob roy cocktail',
    'boulevardier cocktail','old pal cocktail','toronto cocktail','remember the maine cocktail',
    'el diablo cocktail','margarita cocktail','mexican firing squad','smoky margarita',
    'virgin mojito','virgin pina colada','shirley temple','arnold palmer','roy rogers cocktail',
    'virgin mary cocktail','nojito',
    'champagne cocktail','champagne cobbler','champagne cup','champagne julep','champagne punch','champagne sour',
    'brandy flip','brandy smash','brandy cocktail','brandy daisy','brandy fix','brandy sour',
    'brandy float','brandy sangaree','brandy scaffa','brandy toddy','brandy and soda',
    'brandy fizz','brandy julep','ale sangaree','ale flip','bishop cocktail','black stripe',
    'claret punch','claret cup','claret cobbler','claret lemonade','coffee cocktail',
    'jersey cocktail','knickerbocker cocktail','sherry cobbler','soda cocktail',
    'whiskey cocktail','whiskey cobbler','whiskey sour','whiskey punch','whiskey toddy',
    'whiskey skin','whiskey daisy','whiskey fix','whiskey smash','whiskey fizz','whiskey sling',
    'absinthe cocktail','absinthe frappe','apple toddy','bacardi cocktail',
    'catawba cobbler','chocolate cocktail','cider cup','clover leaf cocktail',
    'egg flip','egg sour','egg lemonade','gin cocktail','gin punch','gin sling',
    'gin fizz','gin sour','gin daisy','gin smash','gin toddy','hot spiced rum',
    'manhattan cocktail','martini cocktail','dry martini','peach and honey',
    'port wine negus','port wine sangaree','remsen cooler','roman punch',
    'sherry flip','sherry sangaree','star cocktail','vanilla punch','white lion cocktail',
    'alexander cocktail','brandy alexander','arrack punch','baltimore egg nog',
    'eggnog','bamboo cocktail','boston cooler','bronx cocktail','brooklyn cocktail',
    'curacao punch','columbia skin','mint julep','mojito',
    'old fashioned cocktail','daiquiri cocktail','frozen daiquiri','strawberry daiquiri',
    'cosmopolitan cocktail','long island iced tea','tom collins','john collins',
    'caipirinha','caipiroska','moscow mule','kentucky mule','amaretto sour','pisco sour',
    'negroni cocktail','negroni sbagliato','white negroni','rusty nail cocktail',
    'gimlet cocktail','kir royale','mimosa cocktail','bellini cocktail','rossini cocktail',
    'b-52 cocktail','irish coffee','spanish coffee','white russian','black russian',
    'mudslide cocktail','grasshopper cocktail','tequila sunrise','salty dog','screwdriver cocktail',
    'sea breeze cocktail','bay breeze','cape codder','gin and tonic','vodka tonic',
    'rum and coke','cuba libre','highball',"horse's neck",'pimms cup',
    'highball glass','rocks glass','coupe glass','martini glass','tiki mug','collins glass',
  ];
  return [...new Set(list.map(s => s.toLowerCase().trim()))];
}

function loadRecipeTitles() {
  let files;
  try { files = readdirSync(COCKTAILS_DIR).filter(f => f.endsWith('.ndjson')); }
  catch { return []; }
  const counts = new Map();
  for (const f of files) {
    let lines;
    try { lines = readFileSync(join(COCKTAILS_DIR, f), 'utf8').split('\n'); }
    catch { continue; }
    for (const ln of lines) {
      if (!ln.trim()) continue;
      try {
        const r = JSON.parse(ln);
        const t = String(r.title || '').toLowerCase().trim();
        if (!t || t.length > 60) continue;
        if (/^[a-z]\.?\s*[a-z]\.?$/.test(t)) continue;
        if (/[,:;]/.test(t)) continue;
        if (t.split(/\s+/).length > 6) continue;
        counts.set(t, (counts.get(t) || 0) + 1);
      } catch {}
    }
  }
  const sorted = [...counts.entries()].sort((a, b) => b[1] - a[1]);
  return sorted.slice(0, 500).map(([t]) => t);
}

function buildSkippedIndex(skipped) {
  const idx = new Map();
  for (const r of skipped) {
    const key = String(r.title || '').toLowerCase().trim();
    if (!idx.has(key)) idx.set(key, []);
    idx.get(key).push(r.recipeId);
  }
  return idx;
}

function loadExisting() {
  if (!RESUME) return [];
  if (!existsSync(OUTFILE)) return [];
  try { return JSON.parse(readFileSync(OUTFILE, 'utf8')); } catch { return []; }
}

function persist(results) {
  writeFileSync(OUTFILE, JSON.stringify(results, null, 2), 'utf8');
}

async function main() {
  const skipped = loadSkipped();
  const skipIdx = buildSkippedIndex(skipped);
  const famous = loadFamousTitles();
  let recipeTop = [];
  try { recipeTop = loadRecipeTitles(); } catch { recipeTop = []; }

  const seen = new Set();
  const targets = [];
  for (const r of skipped) {
    const t = String(r.title || '').toLowerCase().trim();
    if (!t || seen.has(t)) continue;
    seen.add(t); targets.push(t);
  }
  for (const t of famous) {
    if (seen.has(t)) continue;
    seen.add(t); targets.push(t);
  }
  for (const t of recipeTop) {
    if (seen.has(t)) continue;
    seen.add(t); targets.push(t);
  }

  console.log(`Targets: ${targets.length}  (skipped=${skipped.length}, famous=${famous.length}, recipe-top=${recipeTop.length})`);

  let work = LIMIT > 0 ? targets.slice(0, LIMIT) : targets;
  console.log(`Will process ${work.length} cocktail titles.`);

  const results = loadExisting();
  const seenUrls = new Set(results.map(r => r.imageUrl));
  const seenWmFiles = new Set();
  const counts = {};
  const sourceCounts = {};
  const licenseCounts = {};
  const startedAt = Date.now();

  for (let i = 0; i < work.length; i++) {
    if (results.length >= TARGET_TOTAL) {
      console.log(`Reached target total ${TARGET_TOTAL}. Stopping.`);
      break;
    }
    const title = work[i];
    const tag = `[${i + 1}/${work.length}] ${title}`;
    const recipeIds = skipIdx.get(title) || [];
    let perTitle = 0;

    function pushHit(source, hit) {
      if (perTitle >= MAX_PHOTOS_PER_TITLE) return false;
      if (seenUrls.has(hit.imageUrl)) return false;
      delete hit._w; delete hit._h; delete hit._file;
      seenUrls.add(hit.imageUrl);
      const rec = {
        recipeTitle: title,
        recipeId: recipeIds[Math.min(perTitle, recipeIds.length - 1)] || null,
        source,
        ...hit,
      };
      results.push(rec);
      perTitle++;
      counts[hit.provider] = (counts[hit.provider] || 0) + 1;
      sourceCounts[source] = (sourceCounts[source] || 0) + 1;
      const lic = hit.licenseShort || 'unknown';
      licenseCounts[lic] = (licenseCounts[lic] || 0) + 1;
      return true;
    }

    if (!ONLY || ONLY === 'loc') {
      try {
        const locHits = await searchLoc(title);
        for (const h of locHits) {
          if (perTitle >= MAX_PHOTOS_PER_TITLE) break;
          if (await headOk('loc', h.imageUrl)) pushHit('loc', h);
        }
      } catch (e) { process.stderr.write(`[loc err] ${title}: ${e.message}\n`); }
    }

    if ((!ONLY || ONLY === 'wikimedia') && perTitle < MAX_PHOTOS_PER_TITLE) {
      try {
        const wmHits = await pickFromCommons(title);
        for (const h of wmHits) {
          if (perTitle >= MAX_PHOTOS_PER_TITLE) break;
          if (seenWmFiles.has(h._file)) continue;
          seenWmFiles.add(h._file);
          if (await headOk('wikimedia', h.imageUrl)) pushHit('wikimedia', h);
        }
      } catch (e) { process.stderr.write(`[wm err] ${title}: ${e.message}\n`); }
    }

    if ((!ONLY || ONLY === 'openverse') && !SKIP_OV && perTitle < MAX_PHOTOS_PER_TITLE) {
      try {
        const ovHits = await searchOpenverse(title);
        for (const h of ovHits) {
          if (perTitle >= MAX_PHOTOS_PER_TITLE) break;
          if (await headOk('openverse', h.imageUrl)) {
            // Map openverse provider -> source field per briefing's source vocabulary.
            // We CANNOT distinguish Flickr Commons from regular CC Flickr without an
            // extra lookup, so non-Smithsonian openverse hits are labeled "openverse".
            const sourceLabel = h.provider === 'smithsonian' ? 'smithsonian' : 'openverse';
            pushHit(sourceLabel, h);
          }
        }
      } catch (e) { process.stderr.write(`[ov err] ${title}: ${e.message}\n`); }
    }

    process.stdout.write(`${tag}  +${perTitle} (total=${results.length})\n`);
    if (results.length % 25 === 0) persist(results);
  }

  persist(results);

  const elapsedSec = (Date.now() - startedAt) / 1000;
  const lines = [];
  lines.push('# Agent 4 Photo Harvest Report');
  lines.push('');
  lines.push(`Generated: ${new Date().toISOString()}`);
  lines.push(`Runtime  : ${elapsedSec.toFixed(1)}s`);
  lines.push(`API calls: ${callsMade} (rate-limit hits: ${rateLimitHits})`);
  lines.push('');
  lines.push('## Photos collected');
  lines.push(`Total photos written: **${results.length}**`);
  lines.push('');
  lines.push('### By source (output `source` field)');
  for (const [k, v] of Object.entries(sourceCounts).sort((a, b) => b[1] - a[1])) {
    lines.push(`- ${k}: ${v}`);
  }
  lines.push('');
  lines.push('### By provider (data origin)');
  for (const [k, v] of Object.entries(counts).sort((a, b) => b[1] - a[1])) {
    lines.push(`- ${k}: ${v}`);
  }
  lines.push('');
  lines.push('### By license');
  for (const [k, v] of Object.entries(licenseCounts).sort((a, b) => b[1] - a[1])) {
    lines.push(`- ${k}: ${v}`);
  }
  lines.push('');
  lines.push('## Coverage');
  const titlesCovered = new Set(results.map(r => r.recipeTitle));
  lines.push(`Unique cocktail titles with at least 1 photo: **${titlesCovered.size}** / ${work.length} processed`);
  lines.push('');
  lines.push('### Skipped-file coverage (top priority)');
  const skippedTitles = [...new Set(skipped.map(r => String(r.title || '').toLowerCase()))];
  const skippedCovered = skippedTitles.filter(t => titlesCovered.has(t));
  lines.push(`Skipped titles with photo: **${skippedCovered.length}** / ${skippedTitles.length}`);
  const gaps = skippedTitles.filter(t => !titlesCovered.has(t));
  if (gaps.length) {
    lines.push('');
    lines.push('Skipped titles still without photo:');
    for (const t of gaps) lines.push(`- ${t}`);
  }
  lines.push('');
  lines.push('## Sources unavailable to this agent');
  lines.push('The following sources required API keys not provided. Skipped with no fake data inserted:');
  lines.push('- **Pexels** — page returns 403/Cloudflare bot challenge for unauthenticated requests; API key required from pexels.com/api.');
  lines.push('- **Pixabay** — API key required.');
  lines.push('- **Unsplash** — API key required (Authorization: Client-ID).');
  lines.push('- **Smithsonian Open Access** — `api_key` query parameter required (free key from data.gov).');
  lines.push('- **NYPL Digital Collections** — HTTP token required in Authorization header.');
  lines.push('- **Flickr (direct, including `is_commons=true`)** — API key required. Partial coverage achieved indirectly via Openverse provider=flickr (Flickr Commons content surfaces in those results).');
  lines.push('');
  lines.push('## Notes');
  lines.push(`- All photos meet min ${MIN_WIDTH}x${MIN_HEIGHT}px requirement (where dimensions reported by source).`);
  lines.push('- Licenses: CC0, CC BY, CC BY-SA, Public Domain Mark, "No known copyright restrictions" — all permit commercial use AND modification.');
  lines.push('- Image URLs were HEAD-checked for image MIME type (jpeg/png/webp).');
  lines.push(`- Per-title cap of ${MAX_PHOTOS_PER_TITLE} photos to spread coverage across many recipes.`);
  writeFileSync(REPORTFILE, lines.join('\n'), 'utf8');

  console.log('\n==== SUMMARY ====');
  console.log(`Photos written : ${results.length}`);
  console.log(`Output         : ${OUTFILE}`);
  console.log(`Report         : ${REPORTFILE}`);
}

main().catch(e => { console.error(e); process.exit(1); });

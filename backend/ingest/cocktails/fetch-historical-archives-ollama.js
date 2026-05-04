// fetch-historical-archives-ollama.js
//
// Same as fetch-historical-archives.js but uses a LOCAL Ollama model for
// extraction instead of the paid Anthropic API. Zero per-call cost.
//
// Setup (one-time):
//   winget install Ollama.Ollama
//   ollama pull qwen2.5:7b-instruct        # recommended, 4.7GB, great at JSON
//   # alternatives:
//   #   ollama pull llama3.1:8b            # 5GB, also solid
//   #   ollama pull qwen2.5:14b-instruct   # 9GB, higher quality, slower
//
// Run:
//   node fetch-historical-archives-ollama.js --source archive_org --resume
//   node fetch-historical-archives-ollama.js --source euvs --resume
//
// All other behavior (sources, polite rate limits, caching, resume,
// progress files, output format) matches the original. Drop-in replacement.

import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const CACHE = path.join(HERE, '_cache');
const PROGRESS = path.join(HERE, '_progress');
const ERRORS = path.join(HERE, '_errors');
for (const d of [CACHE, PROGRESS, ERRORS]) fs.mkdirSync(d, { recursive: true });

const OLLAMA_URL = process.env.OLLAMA_URL || 'http://localhost:11434';
const OLLAMA_MODEL = process.env.OLLAMA_MODEL || 'qwen2.5:7b-instruct';

const UA = 'SpeakeaterCocktailArchive/1.0 (+kyle@speakeater.com; pre-1930 historical recipe research)';

// ---------------- argv ----------------
const argv = process.argv.slice(2);
function arg(name, def = null) {
  const i = argv.indexOf(`--${name}`);
  if (i < 0) return def;
  const v = argv[i + 1];
  return v && !v.startsWith('--') ? v : true;
}
const ARG_SOURCE = arg('source');
const ARG_ALL = !!arg('all');
const ARG_LIMIT = parseInt(arg('limit', '0'), 10) || 0;
const ARG_RESUME = !!arg('resume');
const ARG_DRY = !!arg('dry-run');

// ---------------- helpers ----------------
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }
function jitter(ms) { return Math.round(ms * (0.75 + Math.random() * 0.5)); }
function safeId(s) { return String(s).toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 90); }
function log(source, msg) {
  const t = new Date().toISOString().replace('T', ' ').slice(0, 19);
  process.stdout.write(`[${t}] [${source}] ${msg}\n`);
}
function logErr(source, id, err) {
  const f = path.join(ERRORS, `${source}.log`);
  fs.appendFileSync(f, `${new Date().toISOString()}\t${id}\t${err}\n`);
}
function loadProgress(source) {
  const f = path.join(PROGRESS, `${source}.json`);
  if (!fs.existsSync(f)) return { done: [], failed: [] };
  try { return JSON.parse(fs.readFileSync(f, 'utf8')); }
  catch { return { done: [], failed: [] }; }
}
function saveProgress(source, p) {
  fs.writeFileSync(path.join(PROGRESS, `${source}.json`), JSON.stringify(p, null, 2));
}
function appendNdjson(file, records) {
  if (!records || !records.length) return;
  const lines = records.map(r => JSON.stringify(r) + '\n').join('');
  fs.appendFileSync(file, lines);
}

// ---------------- polite fetcher ----------------
function makeFetcher(source, intervalMs) {
  let last = 0;
  let backoff = 0;
  let consecutive429 = 0;
  return async function politeFetch(url, opts = {}) {
    const now = Date.now();
    const wait = Math.max(0, last + jitter(intervalMs) + backoff - now);
    if (wait > 0) await sleep(wait);
    last = Date.now();
    const headers = { 'User-Agent': UA, ...(opts.headers || {}) };
    const res = await fetch(url, { ...opts, headers });
    if (res.status === 429 || res.status === 503) {
      consecutive429++;
      const retryAfter = parseInt(res.headers.get('retry-after') || '0', 10) * 1000;
      const ladder = [30000, 60000, 120000, 300000][Math.min(consecutive429 - 1, 3)];
      const wait2 = Math.max(retryAfter, ladder);
      log(source, `${res.status} from ${url.slice(0, 80)}... backing off ${wait2}ms (retry ${consecutive429})`);
      backoff = wait2;
      await sleep(wait2);
      if (consecutive429 >= 4) { log(source, `4th throttle; pausing source 1h`); await sleep(3600 * 1000); consecutive429 = 0; }
      throw new Error(`HTTP ${res.status}`);
    }
    consecutive429 = 0; backoff = 0;
    return res;
  };
}

// ---------------- canonical schema helpers (copied from main file) ----------------
const GLASS_MAP = {
  'old-fashioned glass': 'rocks', 'rocks glass': 'rocks', 'whiskey glass': 'rocks',
  'whiskey sour glass': 'rocks', 'cocktail glass': 'coupe', 'martini glass': 'coupe',
  'coupe glass': 'coupe', 'champagne flute': 'flute', 'champagne glass': 'flute',
  'highball glass': 'highball', 'collins glass': 'collins', 'hurricane glass': 'hurricane',
  'pint glass': 'pint', 'beer glass': 'pint', 'beer mug': 'pint',
  'shot glass': 'shot', 'cordial glass': 'cordial', 'pousse cafe glass': 'cordial',
  'punch bowl': 'punch', 'wine glass': 'wine', 'snifter': 'snifter',
  'brandy snifter': 'snifter', 'mug': 'mug', 'coffee mug': 'mug',
  'tumbler': 'rocks', 'large bar glass': 'rocks', 'small bar glass': 'rocks',
  'fancy bar glass': 'coupe', 'goblet': 'wine', 'sour glass': 'coupe',
  'fizz glass': 'highball', 'flip glass': 'rocks', 'punch cup': 'punch',
  'julep cup': 'rocks', 'silver cup': 'rocks', 'mug glass': 'mug',
};
function normalizeGlass(raw) {
  if (!raw) return 'rocks';
  const k = String(raw).trim().toLowerCase();
  return GLASS_MAP[k] || (k.includes('coupe') ? 'coupe' : k.includes('highball') ? 'highball' : k.includes('rock') ? 'rocks' : 'rocks');
}
const PRODUCE = /(lemon|lime|orange|grapefruit|cherry|strawberry|raspberry|blueberry|pineapple|apple|pear|peach|watermelon|melon|berry|mint|basil|cucumber|celery|ginger root|peel|zest|fruit|herb)/i;
const DAIRY = /(milk|cream|half-and-half|half and half|butter|yogurt|egg)/i;
const PANTRY = /(sugar|honey|syrup|salt|pepper|spice|cinnamon|nutmeg|cocoa|chocolate|coffee|tea|vanilla|almond|cordial|grenadine|water|soda|tonic|cola|sprite|7up|ginger ale|ginger beer|juice)/i;
const BAR = /(vodka|gin|rum|tequila|whiskey|whisky|bourbon|scotch|rye|cognac|brandy|vermouth|liqueur|amaretto|kahlua|baileys|campari|aperol|bitters|chartreuse|absinthe|sambuca|schnapps|wine|champagne|prosecco|beer|ale|stout|sake|triple sec|cointreau|grand marnier|port|sherry|midori|jagermeister|drambuie|frangelico|galliano|pernod|ouzo|grappa|pisco|cachaca|mezcal|aquavit|punsch|maraschino|curacao|fernet|byrrh|chambord|crown royal|jack daniel|jim beam|johnnie walker|orgeat|falernum|allspice dram|crème|creme|advokaat|kümmel|kummel|swedish punsch|arrack|genever|noyau|ratafia|crème de|creme de)/i;
function aisleFor(name) {
  const n = (name || '').toLowerCase();
  if (BAR.test(n)) return 'bar';
  if (PRODUCE.test(n)) return 'produce';
  if (DAIRY.test(n)) return 'dairy';
  if (PANTRY.test(n)) return 'pantry';
  return 'pantry';
}
const SPIRIT_RE = /(vodka|gin|rum|tequila|whiskey|whisky|bourbon|scotch|rye|cognac|brandy|absinthe|mezcal|cachaca|pisco|aquavit|sake|grappa|genever|arrack)/i;
const FORTIFIED_RE = /(vermouth|sherry|port|chartreuse|campari|aperol|liqueur|amaretto|kahlua|sambuca|schnapps|triple sec|cointreau|grand marnier|midori|drambuie|frangelico|galliano|pernod|ouzo|jagermeister|curacao|maraschino|fernet|chambord|orgeat|falernum|crème|creme|kummel|kümmel|noyau|ratafia)/i;
function estimateABV(ingredients, isAlcoholic) {
  if (!isAlcoholic) return 0;
  let spiritOz = 0, fortifiedOz = 0, totalOz = 0;
  for (const ing of ingredients) {
    const oz = ing.unit === 'oz' && typeof ing.quantity === 'number' ? ing.quantity : 0;
    totalOz += oz;
    if (SPIRIT_RE.test(ing.name)) spiritOz += oz;
    else if (FORTIFIED_RE.test(ing.name)) fortifiedOz += oz;
  }
  const dilution = totalOz > 0 ? 0.75 : 0;
  const denom = totalOz + dilution;
  if (denom <= 0) return null;
  const alcVol = spiritOz * 0.4 + fortifiedOz * 0.18;
  const abv = (alcVol / denom) * 100;
  if (!isFinite(abv) || abv <= 0) return null;
  return Math.round(abv);
}
function detectMethod(instructions) {
  const text = (Array.isArray(instructions) ? instructions.join(' ') : String(instructions || '')).toLowerCase();
  if (/shake/.test(text)) return 'shaken';
  if (/blend/.test(text)) return 'blended';
  if (/muddle/.test(text)) return 'built';
  if (/stir/.test(text)) return 'stirred';
  if (/build|pour/.test(text)) return 'built';
  return 'built';
}
const GARNISH_RE = /(peel|zest|wedge|slice|twist|cherry|olive|mint|basil|sprig|leaf|leaves|salt rim|sugar rim|rim)/i;
function pickGarnish(ingredients, instructionsText) {
  for (let i = ingredients.length - 1; i >= 0; i--) {
    const ing = ingredients[i];
    if (GARNISH_RE.test(ing.name)) return ing.name.toLowerCase();
  }
  const m = String(instructionsText || '').match(/garnish(?:ed)?\s+with\s+([^.;]+)/i);
  return m ? m[1].trim().toLowerCase().replace(/\.$/, '') : null;
}
function finalizeRecord(rawRecipe, sourceMeta) {
  const title = String(rawRecipe.title || '').trim();
  if (!title) return null;
  const instructions = Array.isArray(rawRecipe.instructions)
    ? rawRecipe.instructions.map(s => String(s || '').trim()).filter(Boolean)
    : [];
  if (!instructions.length) return null;
  const ingredients = (Array.isArray(rawRecipe.ingredients) ? rawRecipe.ingredients : [])
    .map(ing => {
      if (typeof ing === 'string') return { name: ing.trim(), quantity: null, unit: null, aisle: aisleFor(ing) };
      const name = String(ing.name || '').trim();
      if (!name) return null;
      let qty = ing.quantity;
      if (typeof qty === 'string') {
        const f = qty.match(/(\d+)\s+(\d+)\/(\d+)/);
        if (f) qty = Number(f[1]) + Number(f[2]) / Number(f[3]);
        else if (/\d+\/\d+/.test(qty)) { const [n, d] = qty.split('/').map(Number); qty = n / d; }
        else qty = parseFloat(qty);
      }
      if (!isFinite(qty)) qty = null;
      return { name, quantity: qty, unit: ing.unit ? String(ing.unit).toLowerCase().trim() : null, aisle: aisleFor(name) };
    })
    .filter(Boolean);
  if (!ingredients.length) return null;
  const isAlcoholic = ingredients.some(i => SPIRIT_RE.test(i.name) || FORTIFIED_RE.test(i.name)) ? 1 : 0;
  return {
    title,
    content_type: isAlcoholic ? 'cocktail' : 'mocktail',
    is_alcoholic: isAlcoholic,
    is_historic: 1,
    source_book: sourceMeta.source_book,
    source_year: sourceMeta.source_year || null,
    source_url: sourceMeta.source_url || null,
    source_archive: sourceMeta.source_archive,
    cuisine: 'cocktail',
    description: rawRecipe.description ? String(rawRecipe.description).trim() : (sourceMeta.source_year ? `From ${sourceMeta.source_book} (${sourceMeta.source_year})` : ''),
    servings: typeof rawRecipe.servings === 'number' ? rawRecipe.servings : 1,
    prep_minutes: 3,
    cook_minutes: 0,
    instructions,
    ingredients,
    glass_type: normalizeGlass(rawRecipe.glass_type || rawRecipe.glass),
    method: rawRecipe.method ? String(rawRecipe.method).toLowerCase() : detectMethod(instructions),
    garnish: rawRecipe.garnish || pickGarnish(ingredients, instructions.join(' ')),
    abv_percent: estimateABV(ingredients, isAlcoholic),
    image_url: null,
    extraction_confidence: rawRecipe.confidence || 'medium',
    extractor: 'ollama:' + OLLAMA_MODEL,
  };
}

// ---------------- OLLAMA EXTRACTION ----------------
const SYSTEM_PROMPT = `You are a careful historian of cocktail and bartending literature. Extract every drink recipe from raw OCR or transcribed text of a pre-1930 bartender manual.

Return ONLY a JSON object with key "recipes" whose value is an array. Each recipe has:
{ "title": string, "ingredients": [{"name": string, "quantity": number|null, "unit": string|null}], "instructions": [string], "glass_type": string|null, "method": "shaken"|"stirred"|"built"|"blended"|null, "garnish": string|null, "description": string|null, "confidence": "high"|"medium"|"low" }

Rules:
- Skip prefaces, ads, indices, and non-recipe content.
- Preserve the original drink name verbatim.
- Convert measurements: "wine-glass"=4 oz, "pony"=1 oz, "jigger"=1.5 oz, "dash"=dash, "tablespoon"=0.5 oz, "barspoon"=0.125 oz, "teaspoon"=0.17 oz; "cl" -> oz (multiply by 0.34); "ml" -> oz (divide by 30). If unclear leave quantity null.
- Convert fractions like "1/2", "3/4" to decimal.
- Set confidence "low" if OCR text is very garbled.
- If the text contains no recipes return {"recipes": []}.
- Do NOT invent recipes. Do NOT modify titles.
- Maximum 25 recipes per response.`;

async function ollamaCall(rawText, sourceMeta) {
  const userPrompt = `Source book: ${sourceMeta.source_book}
Year: ${sourceMeta.source_year || 'unknown'}

RAW TEXT:
---
${rawText.slice(0, 90000)}
---

Extract every drink recipe. Respond with JSON only (no markdown fences, no preamble).`;

  const body = {
    model: OLLAMA_MODEL,
    messages: [
      { role: 'system', content: SYSTEM_PROMPT },
      { role: 'user', content: userPrompt },
    ],
    stream: false,
    format: 'json',         // Ollama JSON mode — model output is valid JSON
    options: {
      temperature: 0.1,
      num_ctx: 16384,       // generous context for big chunks
      num_predict: 4096,    // cap output to keep it focused
    },
  };

  const res = await fetch(`${OLLAMA_URL}/api/chat`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const t = await res.text();
    throw new Error(`Ollama ${res.status}: ${t.slice(0, 300)}`);
  }
  const data = await res.json();
  const content = (data.message && data.message.content) || '';
  return parseRecipesJson(content);
}

function parseRecipesJson(content) {
  if (!content) return [];
  const start = content.indexOf('{');
  if (start < 0) return [];
  let body = content.slice(start);
  try {
    const m = body.match(/^\{[\s\S]*\}$/);
    if (m) {
      const p = JSON.parse(m[0]);
      return Array.isArray(p.recipes) ? p.recipes : [];
    }
  } catch (_) { /* fall through to repair */ }
  const idx = body.indexOf('"recipes"');
  if (idx < 0) return [];
  const arrStart = body.indexOf('[', idx);
  if (arrStart < 0) return [];
  let i = arrStart + 1;
  const out = [];
  while (i < body.length) {
    while (i < body.length && /[\s,]/.test(body[i])) i++;
    if (i >= body.length || body[i] === ']') break;
    if (body[i] !== '{') break;
    let depth = 0, j = i, inStr = false, esc = false;
    for (; j < body.length; j++) {
      const c = body[j];
      if (esc) { esc = false; continue; }
      if (c === '\\') { esc = true; continue; }
      if (c === '"') { inStr = !inStr; continue; }
      if (inStr) continue;
      if (c === '{') depth++;
      else if (c === '}') { depth--; if (depth === 0) { j++; break; } }
    }
    if (depth !== 0) break;
    const objStr = body.slice(i, j);
    try { out.push(JSON.parse(objStr)); } catch (_) {}
    i = j;
  }
  return out;
}

async function extractFromLongText(rawText, sourceMeta) {
  // Slightly larger chunks than the Anthropic version since Ollama has no per-min token limit
  const CHUNK = 36000;
  if (rawText.length <= CHUNK) {
    return await ollamaCall(rawText, sourceMeta);
  }
  const chunks = [];
  let i = 0;
  while (i < rawText.length) {
    let end = Math.min(rawText.length, i + CHUNK);
    if (end < rawText.length) {
      const back = rawText.lastIndexOf('\n\n', end);
      if (back > i + CHUNK * 0.5) end = back;
    }
    chunks.push(rawText.slice(i, end));
    i = end;
  }
  log(sourceMeta.source_archive, `chunked into ${chunks.length} pieces`);
  const all = [];
  for (let j = 0; j < chunks.length; j++) {
    try {
      const recs = await ollamaCall(chunks[j], sourceMeta);
      all.push(...recs);
      log(sourceMeta.source_archive, `chunk ${j + 1}/${chunks.length}: ${recs.length} recipes (running total ${all.length})`);
    } catch (e) {
      log(sourceMeta.source_archive, `chunk ${j + 1}/${chunks.length} failed: ${e.message}`);
    }
  }
  return all;
}

function dedupRecipes(recipes) {
  const seen = new Set();
  const out = [];
  for (const r of recipes) {
    const key = (r.title || '').toLowerCase().trim();
    if (!key) continue;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(r);
  }
  return out;
}

// ---------------- SOURCES (copied wholesale from main file) ----------------
const archiveFetch = makeFetcher('archive_org', 4000);
async function archiveEnumerate() {
  const queries = [
    'subject:"cocktails" AND date:[1820-01-01 TO 1929-12-31] AND mediatype:texts',
    'subject:"bartending" AND date:[1820-01-01 TO 1929-12-31] AND mediatype:texts',
    'subject:"liquors" AND date:[1820-01-01 TO 1929-12-31] AND mediatype:texts',
    'subject:"mixed drinks" AND date:[1820-01-01 TO 1929-12-31] AND mediatype:texts',
    '(title:"bartender" OR title:"bar tender" OR title:"barkeeper") AND date:[1820-01-01 TO 1929-12-31] AND mediatype:texts',
    '(title:"cocktail" OR title:"mixed drinks" OR title:"how to mix drinks") AND date:[1820-01-01 TO 1929-12-31] AND mediatype:texts',
    '(title:"cordials" OR title:"liqueurs" OR title:"distill") AND date:[1820-01-01 TO 1929-12-31] AND mediatype:texts',
    '(title:"punch" OR title:"flips") AND date:[1820-01-01 TO 1929-12-31] AND mediatype:texts',
  ];
  const seen = new Set();
  const found = [];
  for (const q of queries) {
    try {
      let page = 0;
      while (page < 5) {
        const url = `https://archive.org/advancedsearch.php?q=${encodeURIComponent(q)}&fl[]=identifier&fl[]=title&fl[]=year&fl[]=creator&rows=100&page=${page + 1}&output=json`;
        const res = await archiveFetch(url);
        const data = await res.json();
        const docs = (data.response && data.response.docs) || [];
        if (!docs.length) break;
        for (const d of docs) {
          if (!d.identifier || seen.has(d.identifier)) continue;
          seen.add(d.identifier);
          found.push({ id: d.identifier, title: d.title || d.identifier, year: d.year ? parseInt(String(d.year).slice(0, 4), 10) : null, creator: d.creator || null });
        }
        page++;
        if (docs.length < 100) break;
      }
    } catch (e) { log('archive_org', `enum failed: ${e.message}`); }
  }
  log('archive_org', `enumerated ${found.length} candidate items`);
  return found;
}
async function archiveFetchText(book) {
  const cache = path.join(CACHE, 'archive_org', `${book.id}.txt`);
  fs.mkdirSync(path.dirname(cache), { recursive: true });
  if (fs.existsSync(cache)) return { text: fs.readFileSync(cache, 'utf8'), url: `https://archive.org/details/${book.id}`, year: book.year };
  const candidates = [
    `https://archive.org/download/${book.id}/${book.id}_djvu.txt`,
    `https://archive.org/download/${book.id}/${book.id}_text.txt`,
  ];
  let txt = null;
  for (const u of candidates) {
    try {
      const res = await archiveFetch(u);
      if (res.ok) { const t = await res.text(); if (t && t.length > 1000) { txt = t; break; } }
    } catch (_) {}
  }
  if (!txt) throw new Error('no OCR text available');
  fs.writeFileSync(cache, txt);
  return { text: txt, url: `https://archive.org/details/${book.id}`, year: book.year };
}

const euvsFetch = makeFetcher('euvs', 10000);
async function euvsEnumerate() {
  const inv = path.join(HERE, 'agent5', 'euvs_pre1929.json');
  if (!fs.existsSync(inv)) { log('euvs', 'agent5/euvs_pre1929.json missing'); return []; }
  const list = JSON.parse(fs.readFileSync(inv, 'utf8'));
  const out = list.map(b => ({ id: safeId(b.name.replace(/\.pdf$/i, '')), title: b.name, year: b.year, size: b.size }));
  log('euvs', `enumerated ${out.length} pre-1929 EUVS PDFs from local inventory`);
  return out;
}
async function euvsFetchText(book) {
  const cache = path.join(CACHE, 'euvs', `${book.id}.txt`);
  fs.mkdirSync(path.dirname(cache), { recursive: true });
  if (fs.existsSync(cache)) return { text: fs.readFileSync(cache, 'utf8'), url: null, year: book.year };
  let title = String(book.title || '').replace(/\.pdf$/i, '');
  title = title.replace(/^\d{3,4}(?:s|ca)?\s*[-_]?\s*/, '');
  title = title.replace(/\(.*?\)/g, '').replace(/\s*[-–]\s*/g, ' ');
  let author = null;
  const bym = title.match(/\bby\s+([A-Za-z][A-Za-z\s'.&-]+?)(?:\s+\(|\s*$)/i);
  if (bym) { author = bym[1].trim().split(/\s+/).slice(-2).join(' '); title = title.replace(/\bby\s+.*$/i, '').trim(); }
  title = title.replace(/price\s+\$\d+\.?\d*/gi, '').replace(/\$\d+\.?\d*/g, '').trim();
  const stop = new Set(['the','a','an','of','and','or','for','to','in','on','at','with','how','second','first','third','edition']);
  const words = title.split(/\s+/).filter(w => w.length >= 3 && !stop.has(w.toLowerCase()));
  const keyTitle = words.slice(0, 5).join(' ').replace(/[^A-Za-z\s]/g, '').trim();
  if (!keyTitle && !author) throw new Error('cannot distill query');
  const queries = [];
  if (keyTitle && author) queries.push(`title:(${keyTitle}) AND creator:(${author})`);
  if (keyTitle) queries.push(`title:(${keyTitle}) AND mediatype:texts`);
  if (author) queries.push(`creator:(${author}) AND mediatype:texts`);
  if (keyTitle) queries.push(`(${keyTitle}) AND mediatype:texts`);
  for (const q of queries) {
    try {
      const url = `https://archive.org/advancedsearch.php?q=${encodeURIComponent(q)}&fl[]=identifier&fl[]=title&fl[]=year&rows=3&output=json`;
      const res = await archiveFetch(url);
      const data = await res.json();
      const docs = (data.response && data.response.docs) || [];
      for (const d of docs) {
        if (!d.identifier) continue;
        const dy = d.year ? parseInt(String(d.year).slice(0, 4), 10) : null;
        if (book.year && dy && Math.abs(dy - book.year) > 8) continue;
        try {
          const txtRes = await archiveFetch(`https://archive.org/download/${d.identifier}/${d.identifier}_djvu.txt`);
          if (!txtRes.ok) continue;
          const t = await txtRes.text();
          if (t.length > 1000) { fs.writeFileSync(cache, t); return { text: t, url: `https://archive.org/details/${d.identifier}`, year: book.year }; }
        } catch (_) {}
      }
    } catch (_) {}
  }
  throw new Error('no IA mirror found');
}

const SOURCES = {
  archive_org: { enumerate: archiveEnumerate, fetchText: archiveFetchText, label: 'Internet Archive' },
  euvs: { enumerate: euvsEnumerate, fetchText: euvsFetchText, label: 'EUVS Digital Library' },
};

// ---------------- runner ----------------
async function runSource(name) {
  const src = SOURCES[name];
  if (!src) { console.error(`unknown source: ${name}`); return; }
  const out = path.join(HERE, `historical-${name}.ndjson`);
  const progress = loadProgress(name);
  const done = new Set(progress.done.map(String));
  const failed = new Set(progress.failed.map(String));

  log(name, `=== ${src.label} (Ollama: ${OLLAMA_MODEL}) ===`);
  log(name, `progress: ${done.size} done, ${failed.size} prior fails`);

  let books;
  try { books = await src.enumerate(); }
  catch (e) { log(name, `enumerate failed: ${e.message}`); return; }

  if (ARG_LIMIT) books = books.slice(0, ARG_LIMIT);
  log(name, `processing ${books.length} books (skipping ${done.size} already done)`);

  let totalNewRecipes = 0;
  let processed = 0;

  for (const book of books) {
    const bid = String(book.id);
    if (done.has(bid)) { processed++; continue; }
    if (failed.has(bid) && !ARG_RESUME) { processed++; continue; }
    processed++;
    log(name, `[${processed}/${books.length}] ${book.title} (id=${bid})`);
    try {
      const { text, url, year } = await src.fetchText(book);
      if (!text || text.length < 500) {
        log(name, `  text too short (${text ? text.length : 0} chars), skip`);
        progress.failed.push(bid); saveProgress(name, progress); continue;
      }
      log(name, `  fetched ${text.length} chars`);
      if (ARG_DRY) { log(name, '  --dry-run; skipping extraction'); progress.done.push(bid); saveProgress(name, progress); continue; }
      const sourceMeta = { source_book: book.title, source_year: book.year || year || null, source_url: url || null, source_archive: name };
      let raw = await extractFromLongText(text, sourceMeta);
      raw = dedupRecipes(raw);
      const finalized = raw.map(r => finalizeRecord(r, sourceMeta)).filter(Boolean);
      log(name, `  extracted ${raw.length} raw, ${finalized.length} finalized`);
      appendNdjson(out, finalized);
      totalNewRecipes += finalized.length;
      progress.done.push(bid);
      saveProgress(name, progress);
    } catch (e) {
      log(name, `  FAILED: ${e.message}`);
      logErr(name, bid, e.message);
      progress.failed.push(bid);
      saveProgress(name, progress);
    }
  }
  log(name, `=== ${src.label}: +${totalNewRecipes} recipes appended to ${path.basename(out)} ===`);
}

async function pingOllama() {
  try {
    const res = await fetch(`${OLLAMA_URL}/api/tags`);
    if (!res.ok) throw new Error('not ok');
    const data = await res.json();
    const models = (data.models || []).map(m => m.name);
    if (!models.length) throw new Error('no models pulled');
    if (!models.includes(OLLAMA_MODEL)) {
      console.warn(`WARNING: requested model ${OLLAMA_MODEL} not in installed models: ${models.join(', ')}`);
      console.warn(`Pull it with: ollama pull ${OLLAMA_MODEL}`);
    }
    return true;
  } catch (e) {
    console.error(`FATAL: cannot reach Ollama at ${OLLAMA_URL}`);
    console.error(`  Make sure Ollama is installed and running: https://ollama.com/download/windows`);
    console.error(`  Then pull a model: ollama pull qwen2.5:7b-instruct`);
    return false;
  }
}

async function main() {
  if (!ARG_SOURCE && !ARG_ALL) {
    console.error(`Usage:
  node fetch-historical-archives-ollama.js --source <archive_org|euvs> [--limit N] [--resume]
  OLLAMA_MODEL=llama3.1:8b node fetch-historical-archives-ollama.js --source archive_org --resume`);
    process.exit(1);
  }
  if (!(await pingOllama())) process.exit(1);
  const order = ARG_ALL ? ['archive_org', 'euvs'] : [ARG_SOURCE];
  for (const s of order) {
    try { await runSource(s); }
    catch (e) { log(s, `runSource crashed: ${e.message}`); }
  }
  log('done', 'all sources processed');
}

main().catch(e => { console.error('fatal:', e); process.exit(1); });

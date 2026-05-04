// fetch-historical-archives.js
//
// Polite, resumable crawler that pulls every digitized pre-1930 cocktail/bartender
// manual we can find, runs OCR text through Claude Haiku to extract structured
// recipes, and emits NDJSON in the canonical Speakeater schema with is_historic=1.
//
// Sources (priority order):
//   wikisource   — clean transcribed text, Wikimedia API
//   gutenberg    — public-domain plain text
//   archive_org  — Internet Archive _djvu.txt OCR
//   hathitrust   — Hathi public-domain full-view
//   loc          — Library of Congress + Chronicling America newspapers
//   nypl_menus   — NYPL "What's on the Menu?" cocktail lists
//   trove        — National Library of Australia newspaper recipes
//   euvs         — Exposition Universelle des Vins et Spiritueux PDFs
//
// Run examples:
//   node fetch-historical-archives.js --source wikisource
//   node fetch-historical-archives.js --source gutenberg --limit 5
//   node fetch-historical-archives.js --source archive_org --resume
//   node fetch-historical-archives.js --all --resume
//
// Environment:
//   ANTHROPIC_API_KEY  required for extraction (set in backend/.env)
//
// Outputs (per source, append-only NDJSON in canonical schema):
//   historical-<source>.ndjson
//   _cache/<source>/<book_id>.txt        raw OCR/text after polite fetch
//   _progress/<source>.json              resume state per source
//   _errors/<source>.log                 line per failed book
//
// Polite rate limits (jittered ±25%):
//   wikisource   3.0 s
//   gutenberg    5.0 s
//   archive_org  4.0 s
//   hathitrust   5.0 s
//   loc          4.0 s
//   nypl_menus   3.5 s
//   trove        4.0 s
//   euvs         10.0 s   (large PDFs)
//   anthropic    1.5 s
//
// Backoff: 30 → 60 → 120 → 300 s on 429/503; pause source 1 hour after 4th retry.

import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const CACHE = path.join(HERE, '_cache');
const PROGRESS = path.join(HERE, '_progress');
const ERRORS = path.join(HERE, '_errors');
for (const d of [CACHE, PROGRESS, ERRORS]) fs.mkdirSync(d, { recursive: true });

// Load .env from project root, backend/, or current dir
function loadEnv() {
  const candidates = [
    path.join(HERE, '..', '..', '..', '.env'),       // project root
    path.join(HERE, '..', '..', '..', '.env.local'),
    path.join(HERE, '..', '..', '.env'),             // backend/.env
    path.join(HERE, '.env'),
  ];
  for (const envPath of candidates) {
    if (!fs.existsSync(envPath)) continue;
    const text = fs.readFileSync(envPath, 'utf8');
    for (const line of text.split(/\r?\n/)) {
      const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.+?)\s*$/);
      if (!m) continue;
      if (!process.env[m[1]]) process.env[m[1]] = m[2];
    }
  }
}
loadEnv();

const ANTHROPIC_API_KEY = process.env.ANTHROPIC_API_KEY;
if (!ANTHROPIC_API_KEY) {
  console.error('FATAL: ANTHROPIC_API_KEY missing from backend/.env');
  process.exit(1);
}

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
const ARG_PHASE = arg('phase'); // optional pre-extraction phase target

// ---------------- helpers ----------------
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }
function jitter(ms) { return Math.round(ms * (0.75 + Math.random() * 0.5)); }
function safeId(s) { return String(s).toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 90); }
function sha(s) { return crypto.createHash('sha256').update(s).digest('hex').slice(0, 16); }

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
      if (consecutive429 >= 4) {
        log(source, `4th throttle; pausing source 1h`);
        await sleep(3600 * 1000);
        consecutive429 = 0;
      }
      throw new Error(`HTTP ${res.status}`);
    }
    consecutive429 = 0;
    backoff = 0;
    return res;
  };
}

// ---------------- canonical schema helpers ----------------
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
      return {
        name,
        quantity: qty,
        unit: ing.unit ? String(ing.unit).toLowerCase().trim() : null,
        aisle: aisleFor(name),
      };
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
  };
}

// ---------------- Anthropic Haiku extraction ----------------
// Rate limit: 50,000 input tokens/min (default tier). Track sliding-window usage.
const ANTHROPIC_TPM_LIMIT = parseInt(process.env.ANTHROPIC_TPM_LIMIT || '50000', 10);
const ANTHROPIC_TPM_TARGET = Math.floor(ANTHROPIC_TPM_LIMIT * 0.8); // stay 20% under
const tokenWindow = []; // [{ ts, tokens }]
function pruneWindow() {
  const cutoff = Date.now() - 60000;
  while (tokenWindow.length && tokenWindow[0].ts < cutoff) tokenWindow.shift();
}
function tokensInWindow() {
  pruneWindow();
  return tokenWindow.reduce((s, x) => s + x.tokens, 0);
}
async function waitForTokenBudget(needed) {
  while (true) {
    pruneWindow();
    const used = tokensInWindow();
    if (used + needed <= ANTHROPIC_TPM_TARGET) return;
    // Sleep until oldest entry rolls off
    const oldest = tokenWindow[0];
    const waitMs = oldest ? Math.max(2000, 60000 - (Date.now() - oldest.ts) + 500) : 5000;
    log('anthropic', `tpm guard: ${used}/${ANTHROPIC_TPM_TARGET} used, sleeping ${waitMs}ms before next call`);
    await sleep(waitMs);
  }
}

let lastAnthropic = 0;
async function extractRecipesFromText(rawText, sourceMeta) {
  // Rough token estimate: ~3.5 chars/token for English prose
  const estIn = Math.ceil((rawText.length + 2000) / 3.5);
  await waitForTokenBudget(estIn);
  const wait = Math.max(0, lastAnthropic + jitter(2500) - Date.now());
  if (wait > 0) await sleep(wait);
  lastAnthropic = Date.now();
  const sys = `You are a careful historian of cocktail and bartending literature. Extract every drink recipe from raw OCR or transcribed text of a pre-1930 bartender manual.

Return ONLY a JSON object with key "recipes" whose value is an array. Each recipe has:
{ "title": string, "ingredients": [{"name": string, "quantity": number|null, "unit": string|null}], "instructions": [string], "glass_type": string|null, "method": "shaken"|"stirred"|"built"|"blended"|null, "garnish": string|null, "description": string|null, "confidence": "high"|"medium"|"low" }

Rules:
- Skip prefaces, ads, indices, and non-recipe content.
- Preserve the original drink name verbatim (no modernization).
- Convert measurements: "wine-glass"≈4 oz, "pony"≈1 oz, "jigger"≈1.5 oz, "dash"=dash, "tablespoon"≈0.5 oz, "barspoon"≈0.125 oz, "teaspoon"≈0.17 oz; "cl"→oz (×0.34); "ml"→oz (÷30). If unclear leave quantity null.
- Convert fractions like "1/2", "3/4" to decimal.
- Set confidence "low" if OCR text is very garbled.
- If the text contains no recipes return {"recipes": []}.
- Do NOT invent recipes. Do NOT modify titles.
- Maximum 25 recipes per response. If text is huge, return what you can clearly parse first.
- Keep ingredient names and instruction strings concise.`;
  const user = `Source book: ${sourceMeta.source_book}
Year: ${sourceMeta.source_year || 'unknown'}

RAW TEXT:
---
${rawText.slice(0, 90000)}
---

Extract every drink recipe. Respond with JSON only.`;

  const body = {
    model: 'claude-haiku-4-5-20251001',
    max_tokens: 8192,
    system: sys,
    messages: [{ role: 'user', content: user }],
  };
  const res = await fetch('https://api.anthropic.com/v1/messages', {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'x-api-key': ANTHROPIC_API_KEY,
      'anthropic-version': '2023-06-01',
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const t = await res.text();
    if (res.status === 429) {
      // Sleep 65s and let caller retry
      log('anthropic', `429 from API; sleeping 65s and propagating`);
      await sleep(65000);
    }
    throw new Error(`Anthropic ${res.status}: ${t.slice(0, 200)}`);
  }
  const data = await res.json();
  // Track INPUT tokens only — Anthropic rate-limits on input tokens per minute.
  if (data.usage) {
    tokenWindow.push({ ts: Date.now(), tokens: data.usage.input_tokens || 0 });
  }
  const content = (data.content || []).map(b => b.text || '').join('');
  return parseRecipesJson(content);
}

// Parse Haiku JSON response, repair if truncated mid-array.
function parseRecipesJson(content) {
  if (!content) return [];
  // Pull from first { to last balanced point
  const start = content.indexOf('{');
  if (start < 0) return [];
  let body = content.slice(start);
  // Try direct parse first
  try {
    const m = body.match(/^\{[\s\S]*\}$/);
    if (m) {
      const p = JSON.parse(m[0]);
      return Array.isArray(p.recipes) ? p.recipes : [];
    }
  } catch (_) { /* fall through to repair */ }
  // Repair: find recipes array and grab complete objects
  const idx = body.indexOf('"recipes"');
  if (idx < 0) return [];
  const arrStart = body.indexOf('[', idx);
  if (arrStart < 0) return [];
  // Walk forward grabbing balanced objects until we can no longer find one
  let i = arrStart + 1;
  const out = [];
  while (i < body.length) {
    // Skip whitespace and commas
    while (i < body.length && /[\s,]/.test(body[i])) i++;
    if (i >= body.length || body[i] === ']') break;
    if (body[i] !== '{') break;
    // Walk to matching }
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
    if (depth !== 0) break; // truncated mid-object — stop
    const objStr = body.slice(i, j);
    try {
      out.push(JSON.parse(objStr));
    } catch (_) { /* skip malformed */ }
    i = j;
  }
  return out;
}

// Chunk long text and extract per chunk.
// Smaller chunks keep us under 50K input tokens/min and avoid truncated JSON output.
async function extractFromLongText(rawText, sourceMeta) {
  const CHUNK = 28000; // chars; ~7K input tokens; output stays well under 8K max_tokens
  if (rawText.length <= CHUNK) {
    const recs = await extractRecipesFromText(rawText, sourceMeta);
    return recs;
  }
  const chunks = [];
  let i = 0;
  while (i < rawText.length) {
    let end = Math.min(rawText.length, i + CHUNK);
    if (end < rawText.length) {
      // Backtrack to nearest blank line for clean boundary
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
      const recs = await extractRecipesFromText(chunks[j], sourceMeta);
      all.push(...recs);
      log(sourceMeta.source_archive, `chunk ${j + 1}/${chunks.length}: ${recs.length} recipes (running total ${all.length})`);
    } catch (e) {
      log(sourceMeta.source_archive, `chunk ${j + 1}/${chunks.length} failed: ${e.message}`);
    }
  }
  return all;
}

// Within-book dedup (same OCR can repeat sections)
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

// ---------------- SOURCES ----------------

// ---- Wikisource ----
const wikisourceFetch = makeFetcher('wikisource', 3000);
async function wikisourceEnumerate() {
  const seeds = [
    'The Bartender\'s Guide',
    'How to Mix Drinks',
    'The Ideal Bartender',
    'Recipes for Mixed Drinks',
    'The Savoy Cocktail Book',
    'The Modern Bartender\'s Guide',
    'Bartenders\' Manual',
    'Bartending',
    'Cocktail',
  ];
  const found = [];
  const seen = new Set();
  for (const seed of seeds) {
    try {
      const url = `https://en.wikisource.org/w/api.php?action=query&list=search&srsearch=${encodeURIComponent(seed)}&srnamespace=0&srlimit=50&format=json`;
      const res = await wikisourceFetch(url);
      const data = await res.json();
      for (const hit of (data.query && data.query.search) || []) {
        if (seen.has(hit.title)) continue;
        seen.add(hit.title);
        found.push({ id: safeId(hit.title), title: hit.title });
      }
    } catch (e) { log('wikisource', `enum ${seed}: ${e.message}`); }
  }
  log('wikisource', `enumerated ${found.length} candidate pages`);
  return found;
}
async function wikisourceFetchText(book) {
  const cache = path.join(CACHE, 'wikisource', `${book.id}.txt`);
  fs.mkdirSync(path.dirname(cache), { recursive: true });
  if (fs.existsSync(cache)) return { text: fs.readFileSync(cache, 'utf8'), url: `https://en.wikisource.org/wiki/${encodeURIComponent(book.title)}`, year: null };
  const url = `https://en.wikisource.org/w/api.php?action=parse&page=${encodeURIComponent(book.title)}&prop=wikitext&format=json`;
  const res = await wikisourceFetch(url);
  const data = await res.json();
  const wikitext = data && data.parse && data.parse.wikitext && data.parse.wikitext['*'];
  if (!wikitext) throw new Error('no wikitext');
  // Find subpages too
  const subPages = [...wikitext.matchAll(/\[\[\s*([^\]|]+\/[^\]|]+)/g)].map(m => m[1].trim()).slice(0, 20);
  let combined = wikitext;
  for (const sp of subPages) {
    try {
      const sub = await wikisourceFetch(`https://en.wikisource.org/w/api.php?action=parse&page=${encodeURIComponent(sp)}&prop=wikitext&format=json`);
      const sd = await sub.json();
      const sw = sd && sd.parse && sd.parse.wikitext && sd.parse.wikitext['*'];
      if (sw) combined += '\n\n' + sw;
    } catch (_) { /* skip */ }
  }
  fs.writeFileSync(cache, combined);
  // Detect year from title or text
  const ym = (book.title.match(/\b(1[6-9]\d{2}|19[0-2]\d)\b/) || combined.match(/\b(1[6-9]\d{2}|19[0-2]\d)\b/));
  return { text: combined, url: `https://en.wikisource.org/wiki/${encodeURIComponent(book.title)}`, year: ym ? parseInt(ym[1], 10) : null };
}

// ---- Project Gutenberg ----
const gutenbergFetch = makeFetcher('gutenberg', 5000);
const GUTENBERG_SEED_IDS = [
  // Bullock's Ideal Bartender (1917) — definitively a cocktail manual
  23707, 13487,
  // The Flowing Bowl, by William Schmidt
  // (will be added by enumerate)
];
async function gutenbergEnumerate() {
  // Pull the cocktail/bartender catalog by searching Gutenberg's bookshelf "Cooking" + queries
  const queries = ['cocktail', 'bartender', 'mixed drinks', 'bar tender', 'how to mix drinks', 'punch', 'beverages', 'liqueur', 'cordials'];
  const found = [];
  const seen = new Set(GUTENBERG_SEED_IDS);
  for (const id of GUTENBERG_SEED_IDS) found.push({ id, title: `Gutenberg #${id}` });
  for (const q of queries) {
    try {
      const url = `https://gutendex.com/books/?search=${encodeURIComponent(q)}`;
      const res = await gutenbergFetch(url);
      const data = await res.json();
      for (const b of (data.results || [])) {
        if (seen.has(b.id)) continue;
        // Filter: only public-domain US, only English/French/German/Spanish, only pre-1930ish
        // Gutenberg doesn't always expose year, so we keep all and let the extractor decide.
        const isCockOrBar = /cocktail|bartender|mixed drinks|drinks|punch|liqueur|beverage|bar.tender|distill/i.test(JSON.stringify(b.title) + JSON.stringify(b.subjects || []));
        if (!isCockOrBar) continue;
        seen.add(b.id);
        found.push({ id: b.id, title: b.title, authors: (b.authors || []).map(a => a.name) });
      }
    } catch (e) { log('gutenberg', `enum ${q}: ${e.message}`); }
  }
  log('gutenberg', `enumerated ${found.length} candidate books`);
  return found;
}
async function gutenbergFetchText(book) {
  const cache = path.join(CACHE, 'gutenberg', `${book.id}.txt`);
  fs.mkdirSync(path.dirname(cache), { recursive: true });
  if (fs.existsSync(cache)) return { text: fs.readFileSync(cache, 'utf8'), url: `https://www.gutenberg.org/ebooks/${book.id}`, year: null };
  const candidates = [
    `https://www.gutenberg.org/cache/epub/${book.id}/pg${book.id}.txt`,
    `https://www.gutenberg.org/files/${book.id}/${book.id}-0.txt`,
    `https://www.gutenberg.org/files/${book.id}/${book.id}.txt`,
    `https://www.gutenberg.org/ebooks/${book.id}.txt.utf-8`,
    `https://www.gutenberg.org/ebooks/${book.id}.txt`,
    // HTML fallback: strip tags
    `https://www.gutenberg.org/files/${book.id}/${book.id}-h/${book.id}-h.htm`,
    `https://www.gutenberg.org/cache/epub/${book.id}/pg${book.id}-images.html`,
  ];
  let txt = null;
  for (const u of candidates) {
    try {
      const res = await gutenbergFetch(u);
      if (!res.ok) continue;
      let raw = await res.text();
      if (u.endsWith('.htm') || u.endsWith('.html')) {
        // strip HTML tags + decode common entities
        raw = raw.replace(/<script[\s\S]*?<\/script>/gi, '')
                 .replace(/<style[\s\S]*?<\/style>/gi, '')
                 .replace(/<[^>]+>/g, ' ')
                 .replace(/&nbsp;/g, ' ')
                 .replace(/&amp;/g, '&')
                 .replace(/&lt;/g, '<')
                 .replace(/&gt;/g, '>')
                 .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(parseInt(n, 10)))
                 .replace(/[ \t]+/g, ' ')
                 .replace(/\n{3,}/g, '\n\n');
      }
      if (raw && raw.length > 5000) { txt = raw; break; }
    } catch (_) { /* try next */ }
  }
  if (!txt) throw new Error('no text variant available');
  fs.writeFileSync(cache, txt);
  const ym = txt.match(/\b(1[6-9]\d{2}|19[0-2]\d)\b/);
  return { text: txt, url: `https://www.gutenberg.org/ebooks/${book.id}`, year: ym ? parseInt(ym[1], 10) : null };
}

// ---- Internet Archive ----
const archiveFetch = makeFetcher('archive_org', 4000);
async function archiveEnumerate() {
  // Strategy: query advancedsearch.php for the right subjects + date filter, paginate
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
      let cursor = '';
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
    } catch (e) { log('archive_org', `enum failed for query: ${e.message}`); }
  }
  log('archive_org', `enumerated ${found.length} candidate items`);
  return found;
}
async function archiveFetchText(book) {
  const cache = path.join(CACHE, 'archive_org', `${book.id}.txt`);
  fs.mkdirSync(path.dirname(cache), { recursive: true });
  if (fs.existsSync(cache)) return { text: fs.readFileSync(cache, 'utf8'), url: `https://archive.org/details/${book.id}`, year: book.year };
  // Try _djvu.txt first (clean OCR), then _text.txt fallback
  const candidates = [
    `https://archive.org/download/${book.id}/${book.id}_djvu.txt`,
    `https://archive.org/download/${book.id}/${book.id}_text.txt`,
  ];
  let txt = null;
  for (const u of candidates) {
    try {
      const res = await archiveFetch(u);
      if (res.ok) {
        const t = await res.text();
        if (t && t.length > 1000) { txt = t; break; }
      }
    } catch (_) { /* try next */ }
  }
  if (!txt) throw new Error('no OCR text available');
  fs.writeFileSync(cache, txt);
  return { text: txt, url: `https://archive.org/details/${book.id}`, year: book.year };
}

// ---- HathiTrust ----
const hathiFetch = makeFetcher('hathitrust', 5000);
async function hathiEnumerate() {
  // HathiTrust's full-text bibliographic API. We constrain to subject + pre-1930.
  const queries = ['cocktail', 'bartender', 'mixed drinks', 'how to mix drinks', 'cordials liqueurs', 'punches'];
  const found = [];
  const seen = new Set();
  for (const q of queries) {
    try {
      const url = `https://catalog.hathitrust.org/Search/Home?lookfor=${encodeURIComponent(q)}&type=all&inst=&fulltext=1&filter%5B%5D=publishDateRange%3A1820-1929&filter%5B%5D=accessRestricted%3Afalse&format=json&limit=100`;
      // Hathi catalog doesn't have a public JSON API on this endpoint; fall back to the bib API
      const altUrl = `https://catalog.hathitrust.org/api/volumes/full/htid/.json`; // placeholder
      // Use the OAI-like search via the public CGI:
      const searchUrl = `https://babel.hathitrust.org/cgi/ls?q1=${encodeURIComponent(q)}&field1=ocr&a=srchls&lmt=ft&ft=ft&format=json`;
      const res = await hathiFetch(searchUrl);
      if (!res.ok) continue;
      let data;
      try { data = await res.json(); } catch { continue; }
      const items = (data && (data.items || data.records || [])) || [];
      for (const it of items) {
        const id = it.htid || it.id;
        if (!id || seen.has(id)) continue;
        const yr = it.publishDate ? parseInt(String(it.publishDate).slice(0, 4), 10) : null;
        if (yr && (yr < 1820 || yr > 1929)) continue;
        seen.add(id);
        found.push({ id, title: it.title || id, year: yr });
      }
    } catch (e) { log('hathitrust', `enum ${q}: ${e.message}`); }
  }
  log('hathitrust', `enumerated ${found.length} candidate items (Hathi search may be limited; IA usually mirrors Hathi)`);
  return found;
}
async function hathiFetchText(book) {
  const cache = path.join(CACHE, 'hathitrust', `${safeId(book.id)}.txt`);
  fs.mkdirSync(path.dirname(cache), { recursive: true });
  if (fs.existsSync(cache)) return { text: fs.readFileSync(cache, 'utf8'), url: `https://babel.hathitrust.org/cgi/pt?id=${book.id}`, year: book.year };
  // Hathi public-domain plain-text API
  const url = `https://babel.hathitrust.org/cgi/imgsrv/download/text?id=${book.id};orient=0;size=100;rotation=0`;
  const res = await hathiFetch(url);
  if (!res.ok) throw new Error('hathi text fetch failed');
  const txt = await res.text();
  if (txt.length < 1000) throw new Error('hathi text too short / restricted');
  fs.writeFileSync(cache, txt);
  return { text: txt, url: `https://babel.hathitrust.org/cgi/pt?id=${book.id}`, year: book.year };
}

// ---- Library of Congress / Chronicling America (newspapers) ----
const locFetch = makeFetcher('loc', 4000);
async function locEnumerate() {
  // Chronicling America has full-text cocktail recipe columns in newspapers.
  const queries = ['cocktail recipe', 'how to mix drinks', 'bartender recipe', 'mixed drink recipe'];
  const found = [];
  const seen = new Set();
  for (const q of queries) {
    try {
      let page = 1;
      while (page <= 5) {
        // ChroniclingAmerica uses proxtext (not andtext) for plain-text search
        const url = `https://chroniclingamerica.loc.gov/search/pages/results/?proxtext=${encodeURIComponent(q)}&dateFilterType=yearRange&date1=1860&date2=1929&format=json&page=${page}`;
        const res = await locFetch(url);
        if (!res.ok) {
          log('loc', `enum ${q} page ${page}: HTTP ${res.status}`);
          break;
        }
        const ct = res.headers.get('content-type') || '';
        if (!ct.includes('json')) {
          log('loc', `enum ${q}: non-JSON response (ct=${ct.slice(0, 30)}) — endpoint may have changed`);
          break;
        }
        const data = await res.json();
        const items = data.items || [];
        if (!items.length) break;
        for (const it of items) {
          if (!it.id || seen.has(it.id)) continue;
          seen.add(it.id);
          found.push({ id: it.id, title: it.title || it.id, year: it.date ? parseInt(String(it.date).slice(0, 4), 10) : null, raw: it });
        }
        page++;
      }
    } catch (e) { log('loc', `enum ${q}: ${e.message}`); }
  }
  log('loc', `enumerated ${found.length} newspaper pages with recipe-like text`);
  return found;
}
async function locFetchText(book) {
  const cache = path.join(CACHE, 'loc', `${safeId(book.id)}.txt`);
  fs.mkdirSync(path.dirname(cache), { recursive: true });
  if (fs.existsSync(cache)) return { text: fs.readFileSync(cache, 'utf8'), url: `https://chroniclingamerica.loc.gov${book.id}`, year: book.year };
  // OCR text endpoint: append /ocr.txt
  const url = `https://chroniclingamerica.loc.gov${book.id.replace(/\/$/, '')}/ocr.txt`;
  const res = await locFetch(url);
  if (!res.ok) throw new Error('loc ocr fetch failed');
  const txt = await res.text();
  if (txt.length < 200) throw new Error('loc ocr too short');
  fs.writeFileSync(cache, txt);
  return { text: txt, url: `https://chroniclingamerica.loc.gov${book.id}`, year: book.year };
}

// ---- Trove (National Library of Australia) ----
const troveFetch = makeFetcher('trove', 4000);
async function troveEnumerate() {
  // Trove's API is free but requires a key for higher quotas. We use anonymous (limited).
  const queries = ['"cocktail recipe"', '"how to mix drinks"', '"bartender" cocktail'];
  const found = [];
  const seen = new Set();
  for (const q of queries) {
    try {
      const url = `https://api.trove.nla.gov.au/v3/result?q=${encodeURIComponent(q)}&category=newspaper&l-decade=189,190,191,192&n=100&encoding=json`;
      const res = await troveFetch(url, { headers: { 'X-API-KEY': process.env.TROVE_API_KEY || '' } });
      if (res.status === 401 || res.status === 403) {
        log('trove', 'Trove API requires key — skipping (set TROVE_API_KEY in .env to enable)');
        return [];
      }
      const data = await res.json();
      const articles = ((data.category && data.category[0] && data.category[0].records && data.category[0].records.article) || []);
      for (const a of articles) {
        if (!a.id || seen.has(a.id)) continue;
        seen.add(a.id);
        found.push({ id: a.id, title: a.heading || a.id, year: a.date ? parseInt(String(a.date).slice(0, 4), 10) : null });
      }
    } catch (e) { log('trove', `enum ${q}: ${e.message}`); }
  }
  log('trove', `enumerated ${found.length} Trove articles`);
  return found;
}
async function troveFetchText(book) {
  const cache = path.join(CACHE, 'trove', `${book.id}.txt`);
  fs.mkdirSync(path.dirname(cache), { recursive: true });
  if (fs.existsSync(cache)) return { text: fs.readFileSync(cache, 'utf8'), url: `https://trove.nla.gov.au/newspaper/article/${book.id}`, year: book.year };
  const url = `https://api.trove.nla.gov.au/v3/newspaper/${book.id}?include=articleText&encoding=json`;
  const res = await troveFetch(url, { headers: { 'X-API-KEY': process.env.TROVE_API_KEY || '' } });
  if (!res.ok) throw new Error('trove fetch failed ' + res.status);
  const data = await res.json();
  const txt = (data.article && data.article.articleText) || '';
  if (txt.length < 200) throw new Error('trove text too short');
  fs.writeFileSync(cache, txt);
  return { text: txt, url: `https://trove.nla.gov.au/newspaper/article/${book.id}`, year: book.year };
}

// ---- NYPL Menus ----
const nyplFetch = makeFetcher('nypl_menus', 3500);
async function nyplEnumerate() {
  // The NYPL "What's on the Menu?" data set is a bulk download; we use their public API.
  // For cocktail-specific menus, we filter by dish names containing cocktail keywords.
  log('nypl_menus', 'NYPL menus require their bulk CSV download; skipping enumerate phase. Consider downloading: http://menus.nypl.org/data');
  return [];
}
async function nyplFetchText() { throw new Error('nypl not implemented in this pass'); }

// ---- EUVS PDFs ----
const euvsFetch = makeFetcher('euvs', 10000);
async function euvsEnumerate() {
  const inv = path.join(HERE, 'agent5', 'euvs_pre1929.json');
  if (!fs.existsSync(inv)) {
    log('euvs', 'agent5/euvs_pre1929.json missing; run prior reconnaissance');
    return [];
  }
  const list = JSON.parse(fs.readFileSync(inv, 'utf8'));
  const out = list.map(b => ({ id: safeId(b.name.replace(/\.pdf$/i, '')), title: b.name, year: b.year, size: b.size }));
  log('euvs', `enumerated ${out.length} pre-1929 EUVS PDFs from local inventory`);
  return out;
}
async function euvsFetchText(book) {
  // EUVS PDFs are large and require OCR. Strategy: many of these books are also
  // scanned on Internet Archive with OCR text already extracted. Search IA with
  // a smart short query distilled from the EUVS filename.
  const cache = path.join(CACHE, 'euvs', `${book.id}.txt`);
  fs.mkdirSync(path.dirname(cache), { recursive: true });
  if (fs.existsSync(cache)) return { text: fs.readFileSync(cache, 'utf8'), url: null, year: book.year };

  // Distill: drop year prefix, drop extension, keep first 4 significant words + author if present
  let title = String(book.title || '').replace(/\.pdf$/i, '');
  title = title.replace(/^\d{3,4}(?:s|ca)?\s*[-_]?\s*/, ''); // drop "1862 " or "1910s "
  title = title.replace(/\(.*?\)/g, ''); // drop parens
  title = title.replace(/\s*[-–]\s*/g, ' ');
  // Pull author from " by NAME" if present
  let author = null;
  const bym = title.match(/\bby\s+([A-Za-z][A-Za-z\s'.&-]+?)(?:\s+\(|\s*$)/i);
  if (bym) {
    author = bym[1].trim().split(/\s+/).slice(-2).join(' '); // last 1-2 words = surname(s)
    title = title.replace(/\bby\s+.*$/i, '').trim();
  }
  // Strip $price tokens
  title = title.replace(/price\s+\$\d+\.?\d*/gi, '').replace(/\$\d+\.?\d*/g, '').trim();
  // Take first 4-6 significant words (drop short stop-words)
  const stop = new Set(['the', 'a', 'an', 'of', 'and', 'or', 'for', 'to', 'in', 'on', 'at', 'with', 'how', 'second', 'first', 'third', 'edition']);
  const words = title.split(/\s+/).filter(w => w.length >= 3 && !stop.has(w.toLowerCase()));
  const keyTitle = words.slice(0, 5).join(' ').replace(/[^A-Za-z\s]/g, '').trim();
  if (!keyTitle && !author) throw new Error('cannot distill query from title');

  const queries = [];
  if (keyTitle && author) queries.push(`title:(${keyTitle}) AND creator:(${author})`);
  if (keyTitle) queries.push(`title:(${keyTitle}) AND mediatype:texts`);
  if (author) queries.push(`creator:(${author}) AND mediatype:texts`);
  if (keyTitle) queries.push(`(${keyTitle}) AND mediatype:texts`); // any-field

  for (const q of queries) {
    try {
      const url = `https://archive.org/advancedsearch.php?q=${encodeURIComponent(q)}&fl[]=identifier&fl[]=title&fl[]=year&rows=3&output=json`;
      const res = await archiveFetch(url);
      const data = await res.json();
      const docs = (data.response && data.response.docs) || [];
      for (const d of docs) {
        if (!d.identifier) continue;
        // Year filter: prefer items within ±5 years of book.year
        const dy = d.year ? parseInt(String(d.year).slice(0, 4), 10) : null;
        if (book.year && dy && Math.abs(dy - book.year) > 8) continue;
        try {
          const txtRes = await archiveFetch(`https://archive.org/download/${d.identifier}/${d.identifier}_djvu.txt`);
          if (!txtRes.ok) continue;
          const t = await txtRes.text();
          if (t.length > 1000) {
            fs.writeFileSync(cache, t);
            return { text: t, url: `https://archive.org/details/${d.identifier}`, year: book.year };
          }
        } catch (_) { /* try next doc */ }
      }
    } catch (_) { /* try next query */ }
  }
  throw new Error('no IA mirror found; PDF OCR not yet implemented');
}

// ---------------- registry ----------------
const SOURCES = {
  wikisource: { enumerate: wikisourceEnumerate, fetchText: wikisourceFetchText, label: 'Wikisource' },
  gutenberg: { enumerate: gutenbergEnumerate, fetchText: gutenbergFetchText, label: 'Project Gutenberg' },
  archive_org: { enumerate: archiveEnumerate, fetchText: archiveFetchText, label: 'Internet Archive' },
  hathitrust: { enumerate: hathiEnumerate, fetchText: hathiFetchText, label: 'HathiTrust' },
  loc: { enumerate: locEnumerate, fetchText: locFetchText, label: 'Library of Congress / Chronicling America' },
  trove: { enumerate: troveEnumerate, fetchText: troveFetchText, label: 'NLA Trove (Australia)' },
  nypl_menus: { enumerate: nyplEnumerate, fetchText: nyplFetchText, label: 'NYPL What\'s on the Menu?' },
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

  log(name, `=== ${src.label} ===`);
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

      const sourceMeta = {
        source_book: book.title,
        source_year: book.year || year || null,
        source_url: url || null,
        source_archive: name,
      };
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

async function main() {
  if (!ARG_SOURCE && !ARG_ALL) {
    console.error(`Usage:
  node fetch-historical-archives.js --source <wikisource|gutenberg|archive_org|hathitrust|loc|trove|euvs> [--limit N] [--resume]
  node fetch-historical-archives.js --all [--resume]
  node fetch-historical-archives.js --source <name> --dry-run     (cache only, no extraction)`);
    process.exit(1);
  }
  const order = ARG_ALL
    ? ['wikisource', 'gutenberg', 'archive_org', 'loc', 'hathitrust', 'trove', 'euvs']
    : [ARG_SOURCE];
  for (const s of order) {
    try { await runSource(s); }
    catch (e) { log(s, `runSource crashed: ${e.message}`); }
  }
  log('done', 'all sources processed');
}

main().catch(e => { console.error('fatal:', e); process.exit(1); });

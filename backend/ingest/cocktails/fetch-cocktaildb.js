// Pull every cocktail from TheCocktailDB's free public API and normalize
// to NDJSON for pan-tree's Mixology tab ingest.
//
// Free tier: dev key "1" + search.php?f=<letter>. ~600 cocktails A-Z.
// Output: cocktaildb-raw.ndjson (one normalized cocktail per line).

import { createWriteStream, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const BASE = 'https://www.thecocktaildb.com/api/json/v1/1';
const HERE = dirname(fileURLToPath(import.meta.url));
mkdirSync(HERE, { recursive: true });
const OUT_PATH = join(HERE, 'cocktaildb-raw.ndjson');

// ---------- HTTP with retry/backoff ----------
async function fetchJson(url, attempts = 3) {
  let lastErr;
  for (let i = 0; i < attempts; i++) {
    try {
      const res = await fetch(url);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return await res.json();
    } catch (e) {
      lastErr = e;
      const wait = 400 * Math.pow(2, i);
      await new Promise(r => setTimeout(r, wait));
    }
  }
  throw lastErr;
}

// ---------- Normalization helpers ----------

const GLASS_MAP = {
  'old-fashioned glass': 'rocks',
  'rocks glass': 'rocks',
  'whiskey glass': 'rocks',
  'whiskey sour glass': 'rocks',
  'cocktail glass': 'coupe',
  'martini glass': 'coupe',
  'coupe glass': 'coupe',
  'margarita/coupette glass': 'coupe',
  'margarita glass': 'coupe',
  'coupette glass': 'coupe',
  'champagne flute': 'flute',
  'highball glass': 'highball',
  'collins glass': 'collins',
  'hurricane glass': 'hurricane',
  'pint glass': 'pint',
  'beer glass': 'pint',
  'beer mug': 'pint',
  'beer pilsner': 'pilsner',
  'pilsner glass': 'pilsner',
  'shot glass': 'shot',
  'pousse cafe glass': 'cordial',
  'cordial glass': 'cordial',
  'punch bowl': 'punch',
  'pitcher': 'pitcher',
  'jar': 'jar',
  'mason jar': 'jar',
  'wine glass': 'wine',
  'white wine glass': 'wine',
  'balloon glass': 'snifter',
  'brandy snifter': 'snifter',
  'irish coffee cup': 'mug',
  'coffee mug': 'mug',
  'copper mug': 'mug',
  'tea cup': 'mug',
  'parfait glass': 'parfait',
  'nick and nora glass': 'coupe',
};

function normalizeGlass(raw) {
  if (!raw) return 'rocks';
  const k = String(raw).trim().toLowerCase();
  return GLASS_MAP[k] || 'rocks';
}

function detectMethod(instructions) {
  const text = (instructions || '').toLowerCase();
  if (/shake/.test(text)) return 'shaken';
  if (/blend/.test(text)) return 'blended';
  if (/muddle/.test(text)) return 'built';
  if (/stir/.test(text)) return 'stirred';
  if (/build|pour/.test(text)) return 'built';
  return 'built';
}

// Aisle assignment for ingredient lookup
const PRODUCE = /(lemon|lime|orange|grapefruit|cherry|strawberry|raspberry|blueberry|pineapple|apple|pear|peach|watermelon|melon|berry|mint|basil|cucumber|celery|ginger root|peel|zest|fruit|herb)/i;
const DAIRY = /(milk|cream|half-and-half|half and half|butter|yogurt|egg)/i;
const PANTRY = /(sugar|honey|syrup|salt|pepper|spice|cinnamon|nutmeg|cocoa|chocolate|coffee|tea|vanilla|almond|cordial|grenadine|water|soda|tonic|cola|sprite|7up|ginger ale|ginger beer|juice)/i;
const BAR = /(vodka|gin|rum|tequila|whiskey|whisky|bourbon|scotch|rye|cognac|brandy|vermouth|liqueur|amaretto|kahlua|baileys|campari|aperol|bitters|chartreuse|absinthe|sambuca|schnapps|wine|champagne|prosecco|beer|ale|stout|sake|triple sec|cointreau|grand marnier|port|sherry|midori|jagermeister|jaeger|drambuie|frangelico|galliano|pernod|ouzo|grappa|pisco|cachaca|mezcal|aquavit|punsch|maraschino|curacao|fernet|byrrh|chambord|crown royal|jack daniel|jim beam|johnnie walker)/i;

function aisleFor(name) {
  const n = (name || '').toLowerCase();
  if (BAR.test(n)) return 'bar';
  if (PRODUCE.test(n)) return 'produce';
  if (DAIRY.test(n)) return 'dairy';
  if (PANTRY.test(n)) return 'pantry';
  return 'pantry';
}

// Parse "1 1/2", "1/2", "2", "1.5", "2 1/2 oz", "Juice of 1/2 lime" ...
function parseQuantity(measureRaw) {
  if (!measureRaw) return { quantity: null, unit: null };
  let m = String(measureRaw).trim();
  if (!m) return { quantity: null, unit: null };

  // Drop leading "Juice of", "Top with", etc., but keep numbers.
  m = m.replace(/^juice of\s+/i, '').replace(/^top with\s+/i, '').replace(/^fill with\s+/i, '');

  // Pull out the leading number(s)
  // matches: "1 1/2", "1/2", "1.5", "2"
  const re = /^(\d+\s+\d+\/\d+|\d+\/\d+|\d+(?:\.\d+)?)\s*(.*)$/;
  const match = m.match(re);
  let qty = null;
  let rest = m;
  if (match) {
    const numPart = match[1];
    rest = match[2] || '';
    if (numPart.includes(' ')) {
      const [whole, frac] = numPart.split(/\s+/);
      const [n, d] = frac.split('/').map(Number);
      qty = Number(whole) + n / d;
    } else if (numPart.includes('/')) {
      const [n, d] = numPart.split('/').map(Number);
      qty = n / d;
    } else {
      qty = Number(numPart);
    }
  }

  // Unit normalization
  let unit = (rest || '').trim().toLowerCase();
  unit = unit.replace(/\.$/, '').replace(/s$/, '');
  // common cleanups
  if (/^oz\b/.test(unit) || /^fl\.?\s*oz/.test(unit)) unit = 'oz';
  else if (/^cl\b/.test(unit)) {
    // convert cl to oz roughly (1 cl ≈ 0.34 oz). Keep as oz so heuristics work.
    if (qty != null) qty = +(qty * 0.34).toFixed(2);
    unit = 'oz';
  }
  else if (/^ml\b/.test(unit)) {
    if (qty != null) qty = +(qty / 30).toFixed(2);
    unit = 'oz';
  }
  else if (/^tsp/.test(unit) || /^teaspoon/.test(unit)) unit = 'tsp';
  else if (/^tbsp/.test(unit) || /^tablespoon/.test(unit)) unit = 'tbsp';
  else if (/^dash/.test(unit) || /^dashe/.test(unit)) unit = 'dash';
  else if (/^splash/.test(unit)) unit = 'splash';
  else if (/^cup/.test(unit)) unit = 'cup';
  else if (/^shot/.test(unit)) { unit = 'oz'; if (qty != null) qty = qty * 1.5; }
  else if (/^jigger/.test(unit)) { unit = 'oz'; if (qty != null) qty = qty * 1.5; }
  else if (/^part/.test(unit)) unit = 'part';
  else if (/^slice/.test(unit)) unit = 'slice';
  else if (/^wedge/.test(unit)) unit = 'wedge';
  else if (/^leaf|^leave/.test(unit)) unit = 'leaf';
  else if (/^sprig/.test(unit)) unit = 'sprig';
  else if (/^cube/.test(unit)) unit = 'cube';
  else if (/^drop/.test(unit)) unit = 'drop';
  else if (/^bottle/.test(unit)) unit = 'bottle';
  else if (/^can/.test(unit)) unit = 'can';
  else if (!unit) unit = qty != null ? 'piece' : null;

  return { quantity: qty, unit: unit || null };
}

// Approximate ABV by spirit oz vs total oz. Most cocktails ~20-35%.
const SPIRIT_RE = /(vodka|gin|rum|tequila|whiskey|whisky|bourbon|scotch|rye|cognac|brandy|absinthe|mezcal|cachaca|pisco|aquavit|sake|grappa)/i;
const FORTIFIED_RE = /(vermouth|sherry|port|chartreuse|campari|aperol|liqueur|amaretto|kahlua|baileys|sambuca|schnapps|triple sec|cointreau|grand marnier|midori|drambuie|frangelico|galliano|pernod|ouzo|jagermeister|jaeger|curacao|maraschino|fernet|chambord)/i;

function estimateABV(ingredients) {
  let spiritOz = 0;
  let fortifiedOz = 0;
  let totalOz = 0;
  for (const ing of ingredients) {
    const oz = ing.unit === 'oz' && typeof ing.quantity === 'number' ? ing.quantity : 0;
    totalOz += oz;
    if (SPIRIT_RE.test(ing.name)) spiritOz += oz;
    else if (FORTIFIED_RE.test(ing.name)) fortifiedOz += oz;
  }
  // Add ~1 oz for ice melt/dilution if the cocktail is shaken/stirred-style sized.
  const dilution = totalOz > 0 ? 0.75 : 0;
  const denom = totalOz + dilution;
  if (denom <= 0) return null;
  // 40% ABV spirits, 18% fortifieds
  const alcVol = spiritOz * 0.4 + fortifiedOz * 0.18;
  const abv = (alcVol / denom) * 100;
  if (!isFinite(abv) || abv <= 0) return null;
  return Math.round(abv);
}

const GARNISH_RE = /(peel|zest|wedge|slice|twist|cherry|olive|mint|basil|sprig|leaf|leaves|salt rim|sugar rim|rim)/i;

function pickGarnish(ingredients, instructions) {
  // Look at trailing ingredients first
  for (let i = ingredients.length - 1; i >= 0; i--) {
    const ing = ingredients[i];
    if (GARNISH_RE.test(ing.name)) return ing.name.toLowerCase();
  }
  // Fall back: parse from instructions "garnish with ..."
  const text = (instructions || '');
  const m = text.match(/garnish(?:ed)?\s+with\s+([^.;]+)/i);
  if (m) return m[1].trim().toLowerCase().replace(/\.$/, '');
  return null;
}

function splitInstructions(raw) {
  if (!raw) return [];
  // Cocktail DB sometimes uses "\r\n" or single sentences.
  const cleaned = String(raw).replace(/\r/g, '').trim();
  // Split on newlines first, then sentence-ish boundaries.
  const lines = cleaned.split(/\n+/).map(s => s.trim()).filter(Boolean);
  let steps = [];
  for (const line of lines) {
    const parts = line.split(/(?<=[.!?])\s+(?=[A-Z])/).map(s => s.trim()).filter(Boolean);
    steps = steps.concat(parts.length ? parts : [line]);
  }
  return steps.map(s => s.replace(/\.$/, '')).filter(Boolean);
}

function buildIngredients(drink) {
  const out = [];
  for (let i = 1; i <= 15; i++) {
    const name = drink[`strIngredient${i}`];
    const measure = drink[`strMeasure${i}`];
    if (!name || !String(name).trim()) break; // stop at first null
    const cleanName = String(name).trim();
    const { quantity, unit } = parseQuantity(measure);
    out.push({
      name: cleanName,
      quantity,
      unit,
      aisle: aisleFor(cleanName),
    });
  }
  return out;
}

function descriptionFrom(drink) {
  const cat = drink.strCategory || '';
  const glass = drink.strGlass || '';
  const iba = drink.strIBA;
  const bits = [];
  if (iba) bits.push(`IBA ${iba}`);
  if (cat) bits.push(cat);
  if (glass) bits.push(`served in a ${glass.toLowerCase()}`);
  if (!bits.length) return '';
  return bits.join(' — ');
}

function normalizeDrink(drink) {
  const title = (drink.strDrink || '').trim();
  const instructionsRaw = drink.strInstructions || '';
  const instructions = splitInstructions(instructionsRaw);

  if (!title) return { ok: false, reason: 'empty title' };
  if (!instructions.length) return { ok: false, reason: 'empty instructions' };

  const alcTag = (drink.strAlcoholic || '').trim().toLowerCase();
  const isAlcoholic = alcTag === 'alcoholic';
  const contentType = isAlcoholic ? 'cocktail' : 'mocktail';

  const ingredients = buildIngredients(drink);
  if (!ingredients.length) return { ok: false, reason: 'no ingredients' };

  const glass_type = normalizeGlass(drink.strGlass);
  const method = detectMethod(instructionsRaw);
  const garnish = pickGarnish(ingredients, instructionsRaw);
  const abv = isAlcoholic ? estimateABV(ingredients) : 0;

  return {
    ok: true,
    record: {
      title,
      content_type: contentType,
      is_alcoholic: isAlcoholic ? 1 : 0,
      is_historic: 0,
      source_book: 'TheCocktailDB',
      cuisine: 'cocktail',
      description: descriptionFrom(drink),
      servings: 1,
      prep_minutes: 3,
      cook_minutes: 0,
      instructions,
      ingredients,
      glass_type,
      method,
      garnish: garnish || null,
      abv_percent: abv == null ? null : abv,
      image_url: drink.strDrinkThumb || null,
    },
  };
}

// ---------- Main ----------
async function main() {
  const letters = 'abcdefghijklmnopqrstuvwxyz'.split('');
  const seenIds = new Set();
  const seenTitles = new Set();
  const drinks = [];
  const fetchErrors = [];

  for (const l of letters) {
    process.stdout.write(`letter ${l.toUpperCase()}... `);
    try {
      const data = await fetchJson(`${BASE}/search.php?f=${l}`);
      const list = data.drinks || [];
      let added = 0;
      for (const d of list) {
        if (!d.idDrink || seenIds.has(d.idDrink)) continue;
        const titleKey = (d.strDrink || '').trim().toLowerCase();
        if (!titleKey || seenTitles.has(titleKey)) continue;
        seenIds.add(d.idDrink);
        seenTitles.add(titleKey);
        drinks.push(d);
        added++;
      }
      process.stdout.write(`${added} new (total ${drinks.length})\n`);
    } catch (e) {
      fetchErrors.push({ letter: l, error: e.message });
      process.stdout.write(`FAILED: ${e.message}\n`);
    }
    await new Promise(r => setTimeout(r, 250));
  }

  // Normalize + write NDJSON
  const stream = createWriteStream(OUT_PATH, { encoding: 'utf8' });
  let written = 0;
  const rejected = { 'empty title': 0, 'empty instructions': 0, 'no ingredients': 0 };

  for (const d of drinks) {
    const result = normalizeDrink(d);
    if (!result.ok) {
      rejected[result.reason] = (rejected[result.reason] || 0) + 1;
      continue;
    }
    stream.write(JSON.stringify(result.record) + '\n');
    written++;
  }
  await new Promise(r => stream.end(r));

  const totalRejected = Object.values(rejected).reduce((a, b) => a + b, 0);

  console.log('');
  console.log(`✓ wrote ${written} cocktails to ${OUT_PATH}`);
  console.log(`  raw fetched: ${drinks.length}`);
  console.log(`  rejected: ${totalRejected}`);
  for (const [reason, n] of Object.entries(rejected)) {
    if (n > 0) console.log(`    - ${reason}: ${n}`);
  }
  if (fetchErrors.length) {
    console.log(`  fetch errors on letters: ${fetchErrors.map(e => e.letter).join(',')}`);
  }
}

main().catch(e => { console.error(e); process.exit(1); });

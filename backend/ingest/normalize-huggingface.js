// Deterministic normalizer for HuggingFace RecipeNLG-style rows — no LLM cost.
// RecipeNLG schema: { title, ingredients (list of strings), directions (list of strings),
//                     link, source, NER (list of ingredient names) }
//
// Flexible: also handles close cousins (food.com dumps, corbt/all-recipes) where fields are
// named similarly.

import { createReadStream, createWriteStream, existsSync, mkdirSync } from 'node:fs';
import { createInterface } from 'node:readline';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const OUTDIR = fileURLToPath(new URL('./normalized/', import.meta.url));
mkdirSync(OUTDIR, { recursive: true });
const RAW_NDJSON = fileURLToPath(new URL('./raw/hf-raw.ndjson', import.meta.url));
const RAW_JSON = fileURLToPath(new URL('./raw/hf-raw.json', import.meta.url));
const RAWFILE = existsSync(RAW_NDJSON) ? RAW_NDJSON : RAW_JSON;
const OUTFILE = join(OUTDIR, existsSync(RAW_NDJSON) ? 'hf-normalized.ndjson' : 'hf-normalized.json');

const UNITS = new Set([
  'cup','cups','tbsp','tablespoon','tablespoons','tsp','teaspoon','teaspoons',
  'oz','ounce','ounces','lb','lbs','pound','pounds',
  'g','gram','grams','kg','kilogram','kilograms',
  'ml','milliliter','milliliters','l','liter','liters',
  'pinch','dash','can','cans','bottle','bottles','jar','jars',
  'bag','bags','pack','packs','box','boxes','slice','slices',
  'clove','cloves','head','heads','bunch','bunches','sprig','sprigs',
]);

const AISLE_MAP = [
  [/\b(beef|pork|chicken|turkey|lamb|bacon|sausage|ham|steak|ground|mince|meatball)\b/i, 'protein'],
  [/\b(fish|salmon|tuna|cod|tilapia|shrimp|prawn|crab|lobster|anchov|scallop)\b/i, 'protein'],
  [/\b(tofu|tempeh|seitan|edamame)\b/i, 'protein'],
  [/\b(milk|yogurt|cream|butter|cheese|mozzarella|cheddar|parmesan|ricotta|feta|brie|sour cream|half and half)\b/i, 'dairy'],
  [/\b(eggs?)\b/i, 'dairy'],
  [/\b(onion|garlic|pepper|tomato|carrot|celery|spinach|kale|lettuce|potato|broccoli|zucchini|cucumber|lemon|lime|apple|orange|banana|berr|avocado|leek|shallot|herb|basil|parsley|cilantro|mint|cabbage|cauliflower|pea|corn|mushroom|eggplant|squash|pumpkin)\b/i, 'produce'],
  [/\b(flour|sugar|rice|pasta|noodle|oat|lentil|bean|quinoa|barley|cornmeal)\b/i, 'grain'],
  [/\b(bread|tortilla|bagel|bun|roll|baguette|cracker|cereal)\b/i, 'bakery'],
  [/\b(salt|oregano|thyme|rosemary|cumin|paprika|cinnamon|nutmeg|ginger|turmeric|spice|seasoning|chili powder|curry powder|cayenne|bay leaf)\b/i, 'spice'],
  [/\b(oil|vinegar|soy sauce|ketchup|mustard|mayonnaise|mayo|sauce|honey|syrup|worcestershire|vanilla|extract)\b/i, 'condiment'],
  [/\bfrozen\b/i, 'frozen'],
  [/\b(water|juice|wine|beer|coffee|tea|broth|stock|soda)\b/i, 'beverage'],
];

function toAisle(name) {
  const n = String(name || '').toLowerCase();
  for (const [re, a] of AISLE_MAP) if (re.test(n)) return a;
  return 'pantry';
}

function parseFrac(str) {
  const m = str.match(/^(\d+)?\s*(\d+)\/(\d+)/);
  if (!m) return null;
  const whole = parseInt(m[1] || '0', 10);
  const num = parseInt(m[2], 10);
  const den = parseInt(m[3], 10);
  if (!den) return null;
  return { value: whole + num / den, rest: str.replace(m[0], '').trim() };
}

function parseIngredient(raw) {
  const s = String(raw || '').trim().toLowerCase();
  if (!s) return null;
  let quantity = null, unit = null, rest = s;

  // Try fraction first
  const f = parseFrac(s);
  if (f) { quantity = Math.round(f.value * 100) / 100; rest = f.rest; }
  else {
    const m = s.match(/^([\d.]+)\s*(.*)$/);
    if (m) { const q = parseFloat(m[1]); if (Number.isFinite(q)) { quantity = q; rest = m[2].trim(); } }
  }
  // Extract unit token
  const firstWord = rest.split(/\s+/, 1)[0].replace(/[^a-z]/g, '');
  if (UNITS.has(firstWord)) {
    unit = firstWord;
    rest = rest.replace(/^\S+/, '').trim();
  }
  // Strip leading "of"
  rest = rest.replace(/^of\s+/, '');
  // Name is whatever is left, capped
  const name = rest.slice(0, 80) || s.slice(0, 80);
  return { name, quantity, unit, aisle: toAisle(name), subs: [] };
}

function slugify(t, suffix = '') {
  const base = String(t || '').toLowerCase().replace(/[^\w]+/g, '-').replace(/^-|-$/g, '').slice(0, 50);
  return base + (suffix ? `-${suffix}` : '');
}

function splitStep(text) {
  // Extract timer: "for 10 minutes", "30 seconds", "1 hour"
  const hr = text.match(/(\d+)\s*(?:hour|hr)/i);
  const mn = text.match(/(\d+)\s*(?:minute|min)/i);
  const sc = text.match(/(\d+)\s*(?:second|sec)/i);
  let timer = null;
  if (hr) timer = parseInt(hr[1], 10) * 3600;
  else if (mn) timer = parseInt(mn[1], 10) * 60;
  else if (sc) timer = parseInt(sc[1], 10);
  return { text: String(text).slice(0, 2000), timerSeconds: timer };
}

/** Parse corbt/all-recipes style: single "input" blob with sections. */
function parseCorbtBlob(blob) {
  if (typeof blob !== 'string' || blob.length < 40) return null;
  const titleMatch = blob.match(/^([^\n]+?)\n/);
  if (!titleMatch) return null;
  const title = titleMatch[1].trim();
  const ingSec = blob.match(/Ingredients:\s*\n([\s\S]*?)(?:\n\s*Directions:|$)/i);
  const dirSec = blob.match(/Directions:\s*\n([\s\S]*)$/i);
  if (!ingSec || !dirSec) return null;
  const ingredients = ingSec[1].split(/\n/).map(s => s.replace(/^[-*•]\s*/, '').trim()).filter(Boolean);
  const directions = dirSec[1].split(/\n/).map(s => s.replace(/^[-*•]\s*/, '').trim()).filter(Boolean);
  return { title, ingredients, directions };
}

function normalize(row, idx) {
  // corbt/all-recipes style (single "input" field)
  if (row.input && typeof row.input === 'string') {
    const parsed = parseCorbtBlob(row.input);
    if (!parsed) return null;
    row = { title: parsed.title, ingredients: parsed.ingredients, directions: parsed.directions };
  }

  // Tolerate multiple field-naming conventions
  const title = row.title || row.name || row.Title || '';
  const ingredientsRaw = row.ingredients || row.Ingredients || row.ner_ingredients || [];
  const directionsRaw = row.directions || row.instructions || row.steps || row.Directions || [];
  const sourceUrl = row.link || row.url || row.source || null;

  // Sometimes ingredients arrive as a stringified JSON array
  const ingArr = Array.isArray(ingredientsRaw)
    ? ingredientsRaw
    : (typeof ingredientsRaw === 'string' ? safeJsonArray(ingredientsRaw) : []);
  const dirArr = Array.isArray(directionsRaw)
    ? directionsRaw
    : (typeof directionsRaw === 'string' ? safeJsonArray(directionsRaw) : []);

  if (!title || ingArr.length < 2 || dirArr.length < 2) return null;

  const ingredients = ingArr.map(parseIngredient).filter(Boolean).slice(0, 40);
  if (ingredients.length < 2) return null;

  const steps = dirArr.map((s, order) => ({ order, ...splitStep(s) })).slice(0, 30);
  if (steps.length < 2) return null;

  return {
    id: `hf-${idx}-${slugify(title)}`,
    title: String(title).slice(0, 200),
    cuisine: row.cuisine || null,
    description: row.description || null,
    skillLevel: 'intermediate',
    prepMinutes: toNum(row.prepMinutes || row.prep_time),
    cookMinutes: toNum(row.cookMinutes || row.cook_time),
    servings: toNum(row.servings) || 4,
    avgRating: 0,
    totalRatings: 0,
    dietaryFlags: [],
    allergenWarnings: [],
    ingredients,
    steps,
    source: 'huggingface',
    sourceUrl,
  };
}

function toNum(v) {
  if (v == null) return null;
  const n = Number(v);
  return Number.isFinite(n) && n > 0 && n < 600 ? Math.round(n) : null;
}

function safeJsonArray(s) {
  try {
    const v = JSON.parse(s);
    return Array.isArray(v) ? v : [];
  } catch {
    // Maybe Python-repr format: "['a', 'b']"
    try {
      const t = s.replace(/'/g, '"');
      const v = JSON.parse(t);
      return Array.isArray(v) ? v : [];
    } catch { return []; }
  }
}

async function main() {
  const isNdjson = RAWFILE.endsWith('.ndjson');
  const outStream = createWriteStream(OUTFILE, { encoding: 'utf8' });

  let kept = 0, rejected = 0, idx = 0;
  const writeOut = (n) => {
    if (isNdjson) outStream.write(JSON.stringify(n) + '\n');
    else outStream.write((kept === 0 ? '[' : ',') + JSON.stringify(n));
  };

  if (isNdjson) {
    const rl = createInterface({ input: createReadStream(RAWFILE, { encoding: 'utf8' }), crlfDelay: Infinity });
    for await (const line of rl) {
      if (!line.trim()) continue;
      let row; try { row = JSON.parse(line); } catch { rejected++; idx++; continue; }
      const n = normalize(row, idx++);
      if (n) { writeOut(n); kept++; } else rejected++;
      if (idx % 50000 === 0) process.stdout.write(`  ${idx} read, ${kept} kept, ${rejected} rejected\r`);
    }
  } else {
    // Legacy JSON-array path (small datasets)
    const { readFileSync } = await import('node:fs');
    const raw = JSON.parse(readFileSync(RAWFILE, 'utf8'));
    raw.forEach((r, i) => {
      const n = normalize(r, i);
      if (n) { writeOut(n); kept++; } else rejected++;
    });
  }

  if (!isNdjson && kept > 0) outStream.write(']');
  await new Promise(r => outStream.end(r));
  process.stdout.write('\n');
  console.log(`✓ Normalized ${kept} recipes (rejected ${rejected}) -> ${OUTFILE}`);
}

main();

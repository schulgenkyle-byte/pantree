// Normalize the MyPlate scrape into Pantrie's recipe schema (mirrors
// normalize-themealdb.js format). Reuses parseIngredient + UNITS + aisle
// mapping from normalize-huggingface.js.

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const RAWFILE = fileURLToPath(new URL('./raw/myplate-raw.json', import.meta.url));
const OUTDIR = fileURLToPath(new URL('./normalized/', import.meta.url));
mkdirSync(OUTDIR, { recursive: true });
const OUTFILE = join(OUTDIR, 'myplate-normalized.json');

// --- Shared with normalize-huggingface.js -----------------------------------
const UNITS = new Set([
  'cup', 'cups', 'tbsp', 'tablespoon', 'tablespoons', 'tsp', 'teaspoon', 'teaspoons',
  'oz', 'ounce', 'ounces', 'lb', 'lbs', 'pound', 'pounds',
  'g', 'gram', 'grams', 'kg', 'kilogram', 'kilograms',
  'ml', 'milliliter', 'milliliters', 'l', 'liter', 'liters',
  'pinch', 'dash', 'can', 'cans', 'bottle', 'bottles', 'jar', 'jars',
  'bag', 'bags', 'pack', 'packs', 'package', 'packages', 'box', 'boxes',
  'slice', 'slices', 'clove', 'cloves', 'head', 'heads', 'bunch', 'bunches',
  'sprig', 'sprigs', 'stick', 'sticks', 'piece', 'pieces',
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

/** Parse a single free-text ingredient line into { name, quantity, unit, aisle, subs }. */
function parseIngredientLine(raw) {
  const s = String(raw || '').trim().toLowerCase();
  if (!s) return null;
  let quantity = null, unit = null, rest = s;
  const f = parseFrac(s);
  if (f) {
    quantity = Math.round(f.value * 100) / 100;
    rest = f.rest;
  } else {
    const m = s.match(/^([\d.]+)\s*(.*)$/);
    if (m) {
      const q = parseFloat(m[1]);
      if (Number.isFinite(q)) { quantity = q; rest = m[2].trim(); }
    }
  }
  const firstWord = rest.split(/\s+/, 1)[0].replace(/[^a-z]/g, '');
  if (UNITS.has(firstWord)) {
    unit = firstWord;
    rest = rest.replace(/^\S+/, '').trim();
  }
  rest = rest.replace(/^of\s+/, '');
  const name = rest.slice(0, 80) || s.slice(0, 80);
  return { name, quantity, unit, aisle: toAisle(name), subs: [] };
}

// --- MyPlate-specific -------------------------------------------------------

/** MyPlate's fetcher pre-splits each <li> into { quantity, unit, name, note }.
 *  Build a Pantrie ingredient out of that; if the split looks wrong fall back
 *  to the free-text parser. */
function buildIngredient(ing) {
  if (!ing) return null;
  const rawName = String(ing.name || '').trim();
  if (!rawName) {
    // Maybe just a quantity-only line — skip
    return null;
  }
  // If we got clean qty/unit fields, use them
  const qtyRaw = String(ing.quantity || '').trim().toLowerCase();
  const unitRaw = String(ing.unit || '').trim().toLowerCase();

  let quantity = null, unit = null;
  if (qtyRaw) {
    const f = parseFrac(qtyRaw);
    if (f) quantity = Math.round(f.value * 100) / 100;
    else {
      const n = parseFloat(qtyRaw);
      if (Number.isFinite(n)) quantity = n;
    }
  }
  if (unitRaw && UNITS.has(unitRaw.replace(/[^a-z]/g, ''))) {
    unit = unitRaw.replace(/[^a-z]/g, '');
  }

  // If we didn't successfully extract anything and the name blob contains qty/unit,
  // fall back to full-line parsing
  if (quantity == null && unit == null) {
    const combined = [qtyRaw, unitRaw, rawName].filter(Boolean).join(' ');
    const parsed = parseIngredientLine(combined);
    if (parsed) return parsed;
  }

  // Clean name: lowercase, strip trailing "(..)" preparation notes that duplicate `note`
  let cleanName = rawName.toLowerCase().replace(/\s+/g, ' ').trim();
  // cap length
  cleanName = cleanName.slice(0, 120);

  const subs = [];
  if (ing.note) {
    // Notes like "(or red, yellow, or orange peppers)" are substitution hints
    const note = String(ing.note).replace(/^\(|\)$/g, '').trim();
    if (note) subs.push(note.slice(0, 200));
  }

  return {
    name: cleanName,
    quantity,
    unit,
    aisle: toAisle(cleanName),
    subs,
  };
}

function splitStep(text) {
  const hr = text.match(/(\d+)\s*(?:hour|hr)/i);
  const mn = text.match(/(\d+)\s*(?:minute|min)/i);
  const sc = text.match(/(\d+)\s*(?:second|sec)/i);
  let timer = null;
  if (hr) timer = parseInt(hr[1], 10) * 3600;
  else if (mn) timer = parseInt(mn[1], 10) * 60;
  else if (sc) timer = parseInt(sc[1], 10);
  return { text: String(text).slice(0, 2000), timerSeconds: timer };
}

function slugify(t) {
  return String(t || '')
    .toLowerCase()
    .replace(/[^\w]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 60);
}

function toPositiveInt(v) {
  if (v == null) return null;
  const n = parseInt(v, 10);
  return Number.isFinite(n) && n > 0 && n < 500 ? n : null;
}

function normalize(rec) {
  const title = (rec.title || '').trim();
  if (!title) return null;

  const ingredients = (rec.ingredients || [])
    .map(buildIngredient)
    .filter(Boolean)
    .slice(0, 40);
  if (ingredients.length < 2) return null;

  const steps = (rec.directions || [])
    .map((s, order) => ({ order, ...splitStep(s) }))
    .filter((s) => s.text && s.text.length > 2)
    .slice(0, 40);
  if (steps.length < 1) return null;

  // Reassign step order after filtering
  steps.forEach((s, i) => { s.order = i; });

  const slug = slugify(title);
  const id = `myplate-${slug}`;

  return {
    id,
    title: title.slice(0, 200),
    cuisine: 'American',
    description: rec.description ? String(rec.description).slice(0, 500) : null,
    skillLevel: 'beginner',
    prepMinutes: null,   // MyPlate pages don't expose these
    cookMinutes: null,
    servings: toPositiveInt(rec.servings) || 4,
    avgRating: rec.rating && Number.isFinite(rec.rating.value) ? rec.rating.value : 0,
    totalRatings: rec.rating && Number.isFinite(rec.rating.count) ? rec.rating.count : 0,
    dietaryFlags: [],
    allergenWarnings: [],
    imageUrl: rec.image || null,
    ingredients,
    steps,
    source: 'myplate',
    sourceUrl: rec.url,
  };
}

function main() {
  const raw = JSON.parse(readFileSync(RAWFILE, 'utf8'));
  const seenIds = new Set();
  const seenUrls = new Set();
  const out = [];
  let rejected = 0, duped = 0;

  for (const rec of raw) {
    if (!rec || !rec.url) { rejected++; continue; }
    if (seenUrls.has(rec.url)) { duped++; continue; }
    seenUrls.add(rec.url);

    const n = normalize(rec);
    if (!n) { rejected++; continue; }

    if (seenIds.has(n.id)) { duped++; continue; }
    seenIds.add(n.id);

    out.push(n);
  }

  writeFileSync(OUTFILE, JSON.stringify(out, null, 2), 'utf8');
  const withPhotos = out.filter((r) => r.imageUrl).length;
  console.log(`Normalized ${out.length} recipes (rejected ${rejected}, duped ${duped})`);
  console.log(`  with imageUrl: ${withPhotos} (${((withPhotos / (out.length || 1)) * 100).toFixed(1)}%)`);
  console.log(`  -> ${OUTFILE}`);
}

main();

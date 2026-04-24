// Deterministic normalizer for TheMealDB recipes — no LLM needed (FREE).
// TheMealDB pre-structures every field, so we just map -> Pantrie schema.

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const RAWFILE = fileURLToPath(new URL('./raw/themealdb-raw.json', import.meta.url));
const OUTDIR = fileURLToPath(new URL('./normalized/', import.meta.url));
mkdirSync(OUTDIR, { recursive: true });

const UNITS = [
  'cup', 'cups', 'tbsp', 'tablespoon', 'tablespoons', 'tsp', 'teaspoon', 'teaspoons',
  'oz', 'ounce', 'ounces', 'lb', 'lbs', 'pound', 'pounds',
  'g', 'gram', 'grams', 'kg', 'kilogram', 'kilograms',
  'ml', 'milliliter', 'milliliters', 'l', 'liter', 'liters',
  'pinch', 'dash', 'can', 'cans', 'bottle', 'bottles', 'jar', 'jars',
  'bag', 'bags', 'pack', 'packs', 'box', 'boxes', 'slice', 'slices',
  'clove', 'cloves', 'head', 'heads', 'bunch', 'bunches',
];

const AISLE_MAP = [
  [/(beef|pork|chicken|turkey|lamb|bacon|sausage|ham|steak|ground|mince)/i, 'protein'],
  [/(fish|salmon|tuna|cod|tilapia|shrimp|prawn|crab|lobster|anchov)/i, 'protein'],
  [/(milk|yogurt|cream|butter|cheese|mozzarella|cheddar|parmesan|ricotta)/i, 'dairy'],
  [/(egg|eggs)$/i, 'dairy'],
  [/(onion|garlic|pepper|tomato|carrot|celery|spinach|kale|lettuce|potato|broccoli|zucchini|cucumber|lemon|lime|apple|orange|banana|berr|avocado|leek|shallot|herb|basil|parsley|cilantro|mint)/i, 'produce'],
  [/(flour|sugar|rice|pasta|noodle|oat|lentil|bean|quinoa|barley|bread|tortilla|cereal)/i, 'grain'],
  [/(salt|pepper|oregano|thyme|rosemary|cumin|paprika|cinnamon|nutmeg|ginger|turmeric|spice|seasoning|chili powder|curry)/i, 'spice'],
  [/(oil|vinegar|soy sauce|ketchup|mustard|mayonnaise|mayo|sauce|honey|syrup|worcestershire)/i, 'condiment'],
  [/(frozen)/i, 'frozen'],
  [/(water|juice|wine|beer|coffee|tea|broth|stock)/i, 'beverage'],
];

function toAisle(name) {
  const n = String(name || '').toLowerCase();
  for (const [re, a] of AISLE_MAP) if (re.test(n)) return a;
  return 'pantry';
}

function parseMeasure(measure, name) {
  if (!measure) return { quantity: null, unit: null };
  const s = measure.trim().toLowerCase();
  if (!s) return { quantity: null, unit: null };

  // Handle fractions like "1/2", "1 1/2"
  const fracMatch = s.match(/^(\d+)?\s*(\d+)\/(\d+)/);
  if (fracMatch) {
    const whole = parseInt(fracMatch[1] || '0', 10);
    const num = parseInt(fracMatch[2], 10);
    const den = parseInt(fracMatch[3], 10);
    const q = whole + (num / den);
    const rest = s.replace(fracMatch[0], '').trim();
    const unit = UNITS.find(u => new RegExp(`\\b${u}s?\\b`, 'i').test(rest)) || null;
    return { quantity: Math.round(q * 100) / 100, unit };
  }
  // Plain number + unit
  const m = s.match(/^([\d.]+)\s*(.*)$/);
  if (m) {
    const q = parseFloat(m[1]);
    const rest = m[2].trim();
    const unit = UNITS.find(u => new RegExp(`\\b${u}s?\\b`, 'i').test(rest)) || null;
    return { quantity: Number.isFinite(q) ? q : null, unit };
  }
  // Text-only ("to taste", "pinch")
  return { quantity: null, unit: null };
}

function slugify(t) {
  return String(t || '').toLowerCase().replace(/[^\w]+/g, '-').replace(/^-|-$/g, '').slice(0, 60);
}

function splitSteps(instructions) {
  if (!instructions) return [];
  // First try: split on double newlines, numbered lists, or "STEP N"
  let chunks = instructions
    .split(/(?:\r?\n){2,}|\r?\n(?=STEP\s*\d)|\r?\n(?=\d+[.)])/gi)
    .map(s => s.trim())
    .filter(s => s.length > 10);
  // Fallback 1: single newlines
  if (chunks.length < 2) {
    chunks = instructions.split(/\r?\n+/).map(s => s.trim()).filter(s => s.length > 10);
  }
  // Fallback 2: sentence splitting (. followed by whitespace + capital letter)
  if (chunks.length < 2) {
    chunks = instructions.split(/(?<=[.!?])\s+(?=[A-Z])/).map(s => s.trim()).filter(s => s.length > 10);
  }
  // Fallback 3: take whole thing as one step
  if (chunks.length === 0) chunks = [instructions.trim()];
  return chunks.map((text, order) => {
    const tMin = text.match(/(\d+)\s*(?:minute|min)/i);
    const tSec = text.match(/(\d+)\s*(?:second|sec)/i);
    let timerSeconds = null;
    if (tMin) timerSeconds = parseInt(tMin[1], 10) * 60;
    else if (tSec) timerSeconds = parseInt(tSec[1], 10);
    return { order, text: text.replace(/^STEP\s*\d+\s*[:.-]?\s*/i, '').slice(0, 2000), timerSeconds };
  });
}

function normalize(m) {
  const ingredients = [];
  for (let i = 1; i <= 20; i++) {
    const name = (m[`strIngredient${i}`] || '').trim();
    const measure = (m[`strMeasure${i}`] || '').trim();
    if (!name) continue;
    const { quantity, unit } = parseMeasure(measure, name);
    ingredients.push({
      name: name.toLowerCase(),
      quantity, unit,
      aisle: toAisle(name),
      subs: [],
    });
  }

  const steps = splitSteps(m.strInstructions);
  const title = (m.strMeal || '').trim();
  if (!title || ingredients.length < 2 || steps.length < 1) return null;

  const id = `tmdb-${m.idMeal}-${slugify(title)}`;

  return {
    id,
    title: title.slice(0, 200),
    cuisine: m.strArea || null,
    description: null,
    skillLevel: 'intermediate',
    prepMinutes: null,
    cookMinutes: null,
    servings: 4,
    avgRating: 0,
    totalRatings: 0,
    dietaryFlags: [],
    allergenWarnings: [],
    ingredients,
    steps,
    source: 'themealdb',
    sourceUrl: m.strSource || `https://www.themealdb.com/meal/${m.idMeal}`,
  };
}

function main() {
  const raw = JSON.parse(readFileSync(RAWFILE, 'utf8'));
  const out = [];
  let rejected = 0;
  for (const m of raw) {
    const r = normalize(m);
    if (r) out.push(r); else rejected++;
  }
  const outfile = join(OUTDIR, 'themealdb-normalized.json');
  writeFileSync(outfile, JSON.stringify(out, null, 2), 'utf8');
  console.log(`✓ Normalized ${out.length} recipes (rejected ${rejected}) -> ${outfile}`);
}

main();

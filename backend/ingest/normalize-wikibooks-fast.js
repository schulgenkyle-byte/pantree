// Deterministic Wikibooks Cookbook normalizer — no LLM required.
// Parses the conventional template: == Ingredients ==, == Procedure ==.
// Quality is ~70% vs. an LLM pass; run normalize-wikibooks.js later for better results.

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const RAWFILE = fileURLToPath(new URL('./raw/wikibooks-raw.json', import.meta.url));
const OUTDIR = fileURLToPath(new URL('./normalized/', import.meta.url));
mkdirSync(OUTDIR, { recursive: true });
const OUTFILE = join(OUTDIR, 'wikibooks-normalized.json');

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
  [/\b(beef|pork|chicken|turkey|lamb|bacon|sausage|ham|steak|ground|mince)\b/i, 'protein'],
  [/\b(fish|salmon|tuna|cod|tilapia|shrimp|prawn|crab|lobster|scallop)\b/i, 'protein'],
  [/\b(tofu|tempeh|seitan)\b/i, 'protein'],
  [/\b(milk|yogurt|cream|butter|cheese|mozzarella|cheddar|parmesan|ricotta|feta)\b/i, 'dairy'],
  [/\b(eggs?)\b/i, 'dairy'],
  [/\b(onion|garlic|pepper|tomato|carrot|celery|spinach|kale|lettuce|potato|broccoli|zucchini|cucumber|lemon|lime|apple|orange|banana|berr|avocado|leek|shallot|herb|basil|parsley|cilantro|mint|cabbage|cauliflower|pea|corn|mushroom|eggplant|squash|pumpkin)\b/i, 'produce'],
  [/\b(flour|sugar|rice|pasta|noodle|oat|lentil|bean|quinoa|barley)\b/i, 'grain'],
  [/\b(bread|tortilla|bagel|bun|roll)\b/i, 'bakery'],
  [/\b(salt|oregano|thyme|rosemary|cumin|paprika|cinnamon|nutmeg|ginger|turmeric|spice|seasoning|chili powder|curry|cayenne|bay leaf)\b/i, 'spice'],
  [/\b(oil|vinegar|soy sauce|ketchup|mustard|mayonnaise|mayo|sauce|honey|syrup|worcestershire|vanilla|extract)\b/i, 'condiment'],
  [/\bfrozen\b/i, 'frozen'],
  [/\b(water|juice|wine|beer|coffee|tea|broth|stock)\b/i, 'beverage'],
];

function toAisle(name) {
  const n = String(name || '').toLowerCase();
  for (const [re, a] of AISLE_MAP) if (re.test(n)) return a;
  return 'pantry';
}

function stripWikitext(s) {
  return String(s || '')
    .replace(/<ref[\s\S]*?<\/ref>/gi, '')
    .replace(/<ref[^>]*\/>/gi, '')
    .replace(/\{\{[^{}]*\}\}/g, '')   // simple templates
    .replace(/\{\{[\s\S]*?\}\}/g, '') // multi-line templates
    .replace(/\[\[(?:[^|\]]*\|)?([^\]]+)\]\]/g, '$1') // [[Foo|bar]] -> bar
    .replace(/'''([^']+)'''/g, '$1')  // bold
    .replace(/''([^']+)''/g, '$1')    // italic
    .replace(/<[^>]+>/g, '')          // any HTML tags
    .replace(/\s+/g, ' ')
    .trim();
}

function parseQty(str) {
  const s = str.trim().toLowerCase();
  // Fraction
  let m = s.match(/^(\d+)?\s*(\d+)\/(\d+)(.*)$/);
  if (m) {
    const whole = parseInt(m[1] || '0', 10);
    const num = parseInt(m[2], 10);
    const den = parseInt(m[3], 10);
    if (den) return { quantity: Math.round((whole + num / den) * 100) / 100, rest: m[4].trim() };
  }
  m = s.match(/^([\d.]+)(.*)$/);
  if (m) {
    const q = parseFloat(m[1]);
    if (Number.isFinite(q)) return { quantity: q, rest: m[2].trim() };
  }
  return { quantity: null, rest: s };
}

function parseIngredientLine(line) {
  // Strip leading "*" or "#" wiki list markers
  const cleaned = stripWikitext(line.replace(/^[*#:]+\s*/, ''));
  if (!cleaned || cleaned.length < 2) return null;

  let rest = cleaned;
  let quantity = null, unit = null;

  const q = parseQty(rest);
  if (q.quantity != null) { quantity = q.quantity; rest = q.rest; }

  const firstWord = rest.split(/\s+/, 1)[0].toLowerCase().replace(/[^a-z]/g, '');
  if (UNITS.has(firstWord)) {
    unit = firstWord;
    rest = rest.replace(/^\S+/, '').trim();
  }
  rest = rest.replace(/^of\s+/i, '');
  const name = rest.split(/[,(]/)[0].trim().slice(0, 80).toLowerCase();
  if (!name) return null;
  return { name, quantity, unit, aisle: toAisle(name), subs: [] };
}

function extractSection(text, headings) {
  // Find a section whose heading matches any of `headings` (case-insensitive).
  for (const h of headings) {
    const re = new RegExp(`==+\\s*${h}\\s*==+\\s*\\n([\\s\\S]*?)(?=\\n==+\\s|$)`, 'i');
    const m = text.match(re);
    if (m) return m[1];
  }
  return null;
}

function extractItems(section) {
  if (!section) return [];
  // Lines starting with *, #, or : (sometimes)
  return section.split(/\r?\n/)
    .filter(l => /^\s*[*#:]/.test(l))
    .map(l => l.trim());
}

function extractSteps(section) {
  if (!section) return [];
  const items = section.split(/\r?\n/).filter(l => /^\s*[*#:]/.test(l));
  if (items.length >= 2) return items.map(l => stripWikitext(l.replace(/^[*#:]+\s*/, '')));
  // Fallback: split on sentences
  const s = stripWikitext(section);
  return s.split(/(?<=[.!?])\s+(?=[A-Z])/).filter(x => x.length > 15);
}

function slugify(t) {
  return String(t || '').toLowerCase().replace(/[^\w]+/g, '-').replace(/^-|-$/g, '').slice(0, 60);
}

function extractTimer(text) {
  const hr = text.match(/(\d+)\s*(?:hour|hr)/i);
  const mn = text.match(/(\d+)\s*(?:minute|min)/i);
  const sc = text.match(/(\d+)\s*(?:second|sec)/i);
  if (hr) return parseInt(hr[1], 10) * 3600;
  if (mn) return parseInt(mn[1], 10) * 60;
  if (sc) return parseInt(sc[1], 10);
  return null;
}

function normalize(page) {
  const title = (page.title || '').replace(/^Cookbook:/, '').trim();
  if (!title) return null;

  const ingredientSec = extractSection(page.wikitext, ['Ingredients', 'Ingredients needed']);
  const procedureSec = extractSection(page.wikitext, ['Procedure', 'Preparation', 'Method', 'Directions', 'Instructions']);

  const ingredientLines = extractItems(ingredientSec);
  const ingredients = ingredientLines.map(parseIngredientLine).filter(Boolean).slice(0, 30);
  if (ingredients.length < 2) return null;

  const stepsText = extractSteps(procedureSec);
  const steps = stepsText
    .map((text, order) => ({ order, text: text.slice(0, 2000), timerSeconds: extractTimer(text) }))
    .slice(0, 25);
  if (steps.length < 1) return null;

  return {
    id: `wb-${slugify(title)}`,
    title: title.slice(0, 200),
    cuisine: null,
    description: null,
    skillLevel: 'intermediate',
    prepMinutes: null,
    cookMinutes: null,
    servings: null,
    avgRating: 0,
    totalRatings: 0,
    dietaryFlags: [],
    allergenWarnings: [],
    ingredients,
    steps,
    source: 'wikibooks',
    sourceUrl: page.sourceUrl,
  };
}

function main() {
  const raw = JSON.parse(readFileSync(RAWFILE, 'utf8'));
  const out = [];
  let rejected = 0;
  for (const p of raw) {
    const n = normalize(p);
    if (n) out.push(n); else rejected++;
  }
  writeFileSync(OUTFILE, JSON.stringify(out), 'utf8');
  console.log(`✓ Normalized ${out.length} Wikibooks recipes (rejected ${rejected}) -> ${OUTFILE}`);
}

main();

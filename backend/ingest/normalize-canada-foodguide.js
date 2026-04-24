// Normalize Canada's Food Guide JSON-LD recipes -> Pantrie schema.
// License: Open Government Licence — Canada. Attribution preserved in `attribution` field.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const RAWFILE = fileURLToPath(new URL('./raw/canada-foodguide-raw.json', import.meta.url));
const OUTDIR = fileURLToPath(new URL('./normalized/', import.meta.url));
mkdirSync(OUTDIR, { recursive: true });

const ATTRIBUTION = "Source: Health Canada · Canada's Food Guide · Open Government Licence — Canada";
const UNITS = ['cup','cups','tbsp','tablespoon','tablespoons','tsp','teaspoon','teaspoons','oz','ounce','ounces','lb','lbs','pound','pounds','g','gram','grams','kg','ml','milliliter','milliliters','l','liter','liters','pinch','dash','can','cans','slice','slices','clove','cloves','head','bunch','package','packages'];
const AISLE_MAP = [
  [/(beef|pork|chicken|turkey|lamb|bacon|sausage|ham|steak|ground|mince|fish|salmon|tuna|cod|tilapia|shrimp|prawn|crab|lobster|anchov|tofu|tempeh)/i, 'protein'],
  [/(milk|yogurt|cream|butter|cheese|mozzarella|cheddar|parmesan|ricotta|\begg\b|\beggs\b)/i, 'dairy'],
  [/(onion|garlic|pepper|tomato|carrot|celery|spinach|kale|lettuce|potato|broccoli|zucchini|cucumber|lemon|lime|apple|orange|banana|berr|avocado|leek|shallot|herb|basil|parsley|cilantro|mint|mushroom|squash|cabbage|corn|beet|radish|pear|peach|grape|melon)/i, 'produce'],
  [/(flour|sugar|rice|pasta|noodle|oat|lentil|bean|quinoa|barley|bread|tortilla|cereal|couscous)/i, 'grain'],
  [/(salt|oregano|thyme|rosemary|cumin|paprika|cinnamon|nutmeg|ginger|turmeric|spice|seasoning|chili powder|curry)/i, 'spice'],
  [/(oil|vinegar|soy sauce|ketchup|mustard|mayonnaise|mayo|sauce|honey|syrup|worcestershire)/i, 'condiment'],
  [/frozen/i, 'frozen'],
  [/(water|juice|wine|beer|coffee|tea|broth|stock)/i, 'beverage'],
];
const toAisle = (n) => { const s = String(n||'').toLowerCase(); for (const [re,a] of AISLE_MAP) if (re.test(s)) return a; return 'pantry'; };
const slugify = (t) => String(t||'').toLowerCase().replace(/[^\w]+/g,'-').replace(/^-|-$/g,'').slice(0,60);

function parseIsoMinutes(iso) {
  if (!iso || typeof iso !== 'string') return null;
  const m = iso.match(/^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?$/i);
  if (!m) return null;
  const total = (+m[1]||0)*60 + (+m[2]||0) + Math.round((+m[3]||0)/60);
  return total > 0 ? total : null;
}

function parseYield(y) {
  if (y == null) return 4;
  const m = String(y).match(/(\d+)/);
  return m ? parseInt(m[1], 10) : 4;
}

const FRAC = { '¼':0.25,'½':0.5,'¾':0.75,'⅓':1/3,'⅔':2/3,'⅛':0.125,'⅜':0.375,'⅝':0.625,'⅞':0.875,'⅕':0.2,'⅖':0.4,'⅗':0.6,'⅘':0.8,'⅙':1/6,'⅚':5/6 };

function parseIngredientLine(raw) {
  // CFG lines look like "280 mL (1 ⅛ cups) white mushrooms". Use parenthetical imperial as user-friendly qty/unit.
  const name = raw.replace(/\s*\([^)]*\)\s*/g, ' ').replace(/\s+/g, ' ').trim();
  const paren = raw.match(/\(([^)]+)\)/);
  let cleaned = (paren ? paren[1] : raw).trim();
  let quantity = null;
  const mix = cleaned.match(/^(\d+)\s*([¼-⅞])\s*/);
  const uf = !mix && cleaned.match(/^([¼-⅞])\s*/);
  const frac = !mix && !uf && cleaned.match(/^(\d+)?\s*(\d+)\/(\d+)\s*/);
  const num = !mix && !uf && !frac && cleaned.match(/^([\d.]+)\s*/);
  if (mix) { quantity = +mix[1] + (FRAC[mix[2]] || 0); cleaned = cleaned.slice(mix[0].length); }
  else if (uf) { quantity = FRAC[uf[1]] || null; cleaned = cleaned.slice(uf[0].length); }
  else if (frac) { quantity = (+(frac[1]||0)) + (+frac[2] / +frac[3]); cleaned = cleaned.slice(frac[0].length); }
  else if (num) { quantity = parseFloat(num[1]); cleaned = cleaned.slice(num[0].length); }
  const unit = UNITS.find(u => new RegExp(`\\b${u}s?\\b`, 'i').test(cleaned)) || null;
  return { name: name.toLowerCase().slice(0,200), quantity: Number.isFinite(quantity) ? Math.round(quantity*100)/100 : null, unit, aisle: toAisle(name), subs: [] };
}

function toStep(text, order) {
  const t = String(text || '').trim();
  const tMin = t.match(/(\d+)\s*(?:minute|min)/i);
  const tSec = !tMin && t.match(/(\d+)\s*(?:second|sec)/i);
  const timerSeconds = tMin ? parseInt(tMin[1], 10) * 60 : tSec ? parseInt(tSec[1], 10) : null;
  return { order, text: t.slice(0, 2000), timerSeconds };
}

function normalize(entry) {
  const j = entry.jsonLd || {};
  const title = String(j.name || '').trim();
  if (!title) return null;
  const ingredients = (Array.isArray(j.recipeIngredient) ? j.recipeIngredient : []).map(parseIngredientLine).filter(i => i.name);
  // recipeInstructions may be strings or HowToStep objects
  const steps = (Array.isArray(j.recipeInstructions) ? j.recipeInstructions : [])
    .map(s => typeof s === 'string' ? s : (s && (s.text || s.name)) || '').filter(Boolean).map(toStep);
  if (ingredients.length < 2 || steps.length < 1) return null;
  const desc = (j.description && String(j.description).trim()) || entry.metaDescription || null;
  const image = Array.isArray(j.image) ? j.image[0] : j.image;
  return {
    id: `canada-${slugify(entry.slug || title)}`, title: title.slice(0, 200), cuisine: 'Canadian',
    description: desc ? String(desc).slice(0, 500) : null, skillLevel: 'beginner',
    prepMinutes: parseIsoMinutes(j.prepTime), cookMinutes: parseIsoMinutes(j.cookTime),
    servings: parseYield(j.recipeYield), imageUrl: image || null, ingredients, steps,
    source: 'canada-foodguide', sourceUrl: entry.sourceUrl, attribution: ATTRIBUTION,
    dietaryFlags: [], allergenWarnings: [], avgRating: 0, totalRatings: 0,
  };
}

function main() {
  const raw = JSON.parse(readFileSync(RAWFILE, 'utf8'));
  const out = raw.map(normalize).filter(Boolean);
  const rejected = raw.length - out.length;
  const withPhoto = out.filter(r => r.imageUrl).length;
  const outfile = join(OUTDIR, 'canada-foodguide-normalized.json');
  writeFileSync(outfile, JSON.stringify(out, null, 2), 'utf8');
  const pct = out.length ? Math.round((withPhoto / out.length) * 100) : 0;
  console.log(`Normalized ${out.length} recipes (rejected ${rejected}) -> ${outfile}`);
  console.log(`  with photo: ${withPhoto}/${out.length} (${pct}%)`);
}
main();

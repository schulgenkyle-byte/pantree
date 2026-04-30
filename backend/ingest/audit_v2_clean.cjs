#!/usr/bin/env node
// Recipe quality audit V2 — instruction-quality focused.
// Sources: tmdb (TheMealDB), myplate (USDA MyPlate), canada (Food Guide).
// Reads two-page sample dumps per source, dedupes by id, applies the V2
// criteria, and writes one CSV per source.

const fs = require('fs');
const path = require('path');

const HERE = __dirname;

// -------- helpers ----------------------------------------------------------

function loadDump(name) {
  const p = path.join(HERE, name);
  if (!fs.existsSync(p)) return [];
  const j = JSON.parse(fs.readFileSync(p, 'utf8'));
  return j.recipes || [];
}

function dedupe(arrs) {
  const seen = new Map();
  for (const arr of arrs) {
    for (const r of arr) {
      if (!seen.has(r.id)) seen.set(r.id, r);
    }
  }
  return Array.from(seen.values());
}

// Cooking verbs we consider "real cooking transformation".
// Used as case-insensitive whole-word matches. Includes both raw verbs and
// common conjugations.
const COOKING_VERBS = [
  'add','adds','added','adding',
  'bake','baked','baking',
  'beat','beaten','beating',
  'blend','blended','blending',
  'boil','boiled','boiling',
  'braise','braised','braising',
  'broil','broiled','broiling',
  'brown','browned','browning',
  'brush','brushed','brushing',
  'chop','chopped','chopping',
  'coat','coated','coating',
  'combine','combined','combining',
  'cook','cooked','cooking',
  'cool','cooled','cooling',
  'cover','covered','covering',
  'cream','creamed','creaming',
  'crush','crushed','crushing',
  'cut','cuts','cutting',
  'deglaze','deglazed',
  'dice','diced','dicing',
  'dip','dipped','dipping',
  'dissolve','dissolved','dissolving',
  'drain','drained','draining',
  'drizzle','drizzled','drizzling',
  'drop','dropped','dropping',
  'fill','filled','filling',
  'flip','flipped','flipping',
  'fold','folded','folding',
  'fry','fried','frying',
  'garnish','garnished','garnishing',
  'glaze','glazed','glazing',
  'grate','grated','grating',
  'grease','greased','greasing',
  'grill','grilled','grilling',
  'grind','ground','grinding',
  'heat','heated','heating',
  'knead','kneaded','kneading',
  'layer','layered','layering',
  'mash','mashed','mashing',
  'marinate','marinated','marinating',
  'melt','melted','melting',
  'microwave','microwaved','microwaving',
  'mince','minced','mincing',
  'mix','mixed','mixing',
  'place','placed','placing',
  'poach','poached','poaching',
  'pour','poured','pouring',
  'preheat','preheated','preheating',
  'puree','pureed',
  'reduce','reduced','reducing',
  'refrigerate','refrigerated','refrigerating',
  'remove','removed','removing',
  'rinse','rinsed','rinsing',
  'roast','roasted','roasting',
  'roll','rolled','rolling',
  'sauté','sautéed','sauteed','saute','sautes',
  'scoop','scooped',
  'scrape','scraped',
  'season','seasoned','seasoning',
  'sear','seared','searing',
  'serve','served','serving',
  'set','sets','setting',
  'shake','shaken','shaking',
  'shred','shredded','shredding',
  'sift','sifted','sifting',
  'simmer','simmered','simmering',
  'skim','skimmed',
  'slice','sliced','slicing',
  'soak','soaked','soaking',
  'spoon','spooned','spooning',
  'spread','spreading',
  'sprinkle','sprinkled','sprinkling',
  'steam','steamed','steaming',
  'stew','stewed',
  'stir','stirred','stirring',
  'strain','strained','straining',
  'stuff','stuffed','stuffing',
  'taste','tasted','tasting',
  'thicken','thickened','thickening',
  'toast','toasted','toasting',
  'top','topped','topping',
  'toss','tossed','tossing',
  'transfer','transferred','transferring',
  'turn','turned','turning',
  'warm','warmed','warming',
  'whip','whipped','whipping',
  'whisk','whisked','whisking',
  'wrap','wrapped','wrapping',
];
const VERB_RE = new RegExp('\\b(' + COOKING_VERBS.join('|') + ')\\b', 'gi');

function countCookingVerbs(text) {
  if (!text || typeof text !== 'string') return 0;
  const matches = text.match(VERB_RE);
  return matches ? matches.length : 0;
}

// Heat words the recipe must contain SOMEWHERE if it claims to cook.
// Used to detect "no actual cooking transformation" cases — a recipe that
// only says "mix beans, soak beans, refrigerate" never heats anything.
const HEAT_RE = /\b(heat|heats|heated|heating|bake|baked|baking|boil|boiled|boiling|simmer|simmered|simmering|fry|fried|frying|sauté|saute|sautéed|sauteed|grill|grilled|grilling|roast|roasted|roasting|broil|broiled|broiling|cook|cooked|cooking|microwave|microwaved|microwaving|toast|toasted|toasting|warm|warmed|warming|preheat|preheated|preheating|melt|melted|melting|steam|steamed|steaming|braise|braised|sear|seared)\b/i;

// Words that suggest a "no-cook" dish where lack of heat is fine.
// Things like beverages, salads, cold dips, raw bowls, dressings.
const NO_COOK_TITLE_RE = /\b(smoothie|shake|drink|beverage|cocktail|mocktail|tea|coffee|punch|lemonade|infusion|water|juice|salad|slaw|tartare|ceviche|gazpacho|hummus|guacamole|salsa|pesto|dressing|vinaigrette|dip|spread|raw|tartine|sandwich|wrap|pinwheel|parfait|granola|trail mix|bowl|bites|balls|energy bites|overnight oats|chia|popsicle|sorbet|gelatin|jelly|fruit salad|dessert sauce|stuffed avocado|seasoning|spice mix|spice blend|seasoning blend|rub|marinade|sauce mix|soup mix|dry mix|topping|crumble topping|bruschetta|crostini|bites)\b/i;

// Trivially-simple dishes where one step is acceptable
// (mix this with that and serve).
const TRIVIAL_TITLE_RE = /\b(smoothie|shake|drink|beverage|cocktail|mocktail|tea|coffee|punch|lemonade|infusion|salad|dressing|vinaigrette|dip|salsa|guacamole|hummus|spread|trail mix|granola|sprinkle|topping|sauce|marinade|rub|seasoning|spice blend|seasoning blend|spice mix|sauce mix|soup mix|dry mix|crumble topping|crumble|fruit salad|fruit cup|chia|overnight oats|parfait|bowl|bites|balls|wrap|sandwich|stuffed avocado|bruschetta|crostini)\b/i;

// -------- per-field checks -------------------------------------------------

function badTitle(t) {
  if (!t || typeof t !== 'string') return 'title missing';
  const s = t.trim();
  if (s.length === 0) return 'title empty';
  if (/^recipe\s*\d+/i.test(s)) return 'title generic';
  if (/^\d+$/.test(s)) return 'title is just a number';
  if (s.length < 3) return 'title too short';
  if (/^untitled/i.test(s)) return 'title is "untitled"';
  return null;
}

const FRACTION_GLITCH_QTYS = new Set([12, 14, 34, 13, 23, 18, 38, 58, 78]);
const SMALL_UNITS = new Set(['teaspoon','teaspoons','tsp','tablespoon','tablespoons','tbsp']);

function badIngredients(ings) {
  if (!Array.isArray(ings) || ings.length === 0) return 'no ingredients';
  let undefCount = 0, numericOnly = 0, singleChar = 0, htmlFrag = 0;
  let nameStartsDigits = 0, glitchFracQty = 0, zeroQty = 0;
  for (const i of ings) {
    const n = (i.name || '').toString().trim();
    const unit = (i.unit || '').toString().toLowerCase().trim();
    const q = i.quantity;
    if (!n || n === 'undefined' || n === 'null') { undefCount++; continue; }
    if (/^\d+(\.\d+)?$/.test(n)) numericOnly++;
    if (n.length === 1) singleChar++;
    if (/<[a-z][^>]*>/i.test(n)) htmlFrag++;
    if (/^\d{2,}\s+(cups?|tbsp|tsp|teaspoons?|tablespoons?)\b/i.test(n)) nameStartsDigits++;
    if (typeof q === 'number' && FRACTION_GLITCH_QTYS.has(q) && SMALL_UNITS.has(unit)) glitchFracQty++;
    if (q === 0) zeroQty++;
  }
  if (undefCount + numericOnly + singleChar + htmlFrag >= ings.length) return 'all ingredients garbage';
  if (undefCount >= Math.ceil(ings.length / 2)) return 'ingredients mostly undefined';
  if (numericOnly + singleChar >= Math.ceil(ings.length / 2)) return 'ingredients mostly garbage tokens';
  // Soft warts (not auto-delete):
  if (undefCount > 0) return `${undefCount} undefined ingredient(s)`;
  if (numericOnly > 0) return `${numericOnly} numeric-only ingredient name(s)`;
  if (singleChar > 0) return `${singleChar} single-letter ingredient name(s)`;
  if (htmlFrag > 0) return 'HTML in ingredient names';
  if (nameStartsDigits > 0) return 'fraction collapsed into name prefix';
  if (glitchFracQty > 0) return `${glitchFracQty} ingredient quantity looks like dropped fraction`;
  if (zeroQty >= Math.ceil(ings.length * 0.6)) return 'most ingredients have zero quantity';
  return null;
}

function classify(r) {
  const tBad = badTitle(r.title);
  if (tBad === 'title missing' || tBad === 'title empty' || tBad === 'title is just a number'
      || tBad === 'title generic' || tBad === 'title is "untitled"') {
    return { verdict: 'delete', reason: tBad };
  }

  const iBad = badIngredients(r.ingredients || []);
  if (iBad === 'no ingredients' || iBad === 'ingredients mostly undefined'
      || iBad === 'all ingredients garbage' || iBad === 'ingredients mostly garbage tokens') {
    return { verdict: 'delete', reason: iBad };
  }
  // Parser glitches that make the recipe dangerously wrong → delete.
  // (only the explicit name-prefix case; bulk dry-mix / seasoning recipes
  // legitimately use double-digit teaspoon counts so we don't auto-delete on
  // the quantity heuristic alone.)
  if (iBad === 'fraction collapsed into name prefix') {
    return { verdict: 'delete', reason: iBad };
  }

  // Instructions checks (V2 focus) ---------------------------------------
  const text = r.instructions || '';
  const steps = Array.isArray(r.steps) ? r.steps : [];
  const stepCount = (typeof r.step_count === 'number' && r.step_count > 0)
    ? r.step_count
    : steps.length;

  if (!stepCount || (text.trim().length === 0 && steps.length === 0)) {
    return { verdict: 'delete', reason: 'no instructions' };
  }
  if (text.trim().length < 20 && steps.length <= 1) {
    return { verdict: 'delete', reason: 'instructions extremely short' };
  }
  if (/^https?:\/\//i.test(text.trim())) {
    return { verdict: 'delete', reason: 'instructions are just a URL' };
  }

  const verbCount = countCookingVerbs(text);
  if (verbCount === 0) {
    return { verdict: 'delete', reason: 'instructions contain no cooking verbs' };
  }

  // "Sub-task only, no actual cooking transformation" check.
  // A recipe that doesn't apply heat AND isn't a clearly no-cook dish is
  // suspicious. (e.g. "Mix beans. Soak beans. Refrigerate beans.")
  // Note: "freeze" counts as a transformation (popsicles, ice cream, etc.).
  const hasHeatStep = HEAT_RE.test(text) || /\bfreez(e|ing|ers?)\b/i.test(text);
  const noCookOK = NO_COOK_TITLE_RE.test(r.title || '') || /\b(fruit pops?|frozen|popsicle|ice pop)\b/i.test(r.title || '');
  if (!hasHeatStep && !noCookOK) {
    // Allow "combine + serve"-style (toss, mix, stir, whisk, fold, blend, sprinkle, drizzle, garnish, layer)
    // only if there are at least 3 prep verbs total. Otherwise it looks like
    // half a recipe.
    if (verbCount < 3) {
      return { verdict: 'delete', reason: 'no heat step and too few prep verbs' };
    }
    // Otherwise treat as a fix candidate, but still flagged below.
  }

  // Single-step rule: only allowed if the dish is in the trivial list.
  if (stepCount === 1) {
    const trivial = TRIVIAL_TITLE_RE.test(r.title || '');
    if (!trivial) {
      return { verdict: 'delete', reason: 'single-step recipe for non-trivial dish' };
    }
  }

  // Soft FIX criteria ------------------------------------------------------
  const reasons = [];
  if (verbCount < 3) reasons.push(`only ${verbCount} cooking verb(s)`);
  if (!r.image_url || String(r.image_url).trim().length === 0) reasons.push('no image');
  if (iBad) reasons.push(iBad);
  if (tBad) reasons.push(tBad);
  if (!hasHeatStep && !noCookOK) reasons.push('no heat step (assumed mix-and-serve)');

  if (reasons.length > 0) return { verdict: 'fix', reason: reasons.join('; ') };
  return { verdict: 'keep', reason: '' };
}

// -------- run -------------------------------------------------------------

function csvCell(v) {
  if (v == null) return '';
  const s = String(v);
  if (/[",\n\r]/.test(s)) return '"' + s.replace(/"/g, '""') + '"';
  return s;
}

function audit(name, dumps, outFile) {
  const recipes = dedupe(dumps.map(loadDump));
  const counts = { keep: 0, fix: 0, delete: 0 };
  const reasonTally = new Map();
  const rows = [];
  const worstDeletes = [];
  for (const r of recipes) {
    const { verdict, reason } = classify(r);
    counts[verdict]++;
    if (verdict === 'delete') {
      reasonTally.set(reason, (reasonTally.get(reason) || 0) + 1);
      worstDeletes.push({ id: r.id, title: r.title, reason });
    }
    rows.push([
      r.id,
      r.title || '',
      verdict,
      reason,
      r.image_url ? '1' : '0',
      (typeof r.step_count === 'number' ? r.step_count : (Array.isArray(r.steps) ? r.steps.length : 0)),
      (Array.isArray(r.ingredients) ? r.ingredients.length : (r.ingredient_count || 0)),
    ]);
  }
  const header = 'id,title,verdict,reason,has_photo,step_count,ingredient_count';
  const csv = [header, ...rows.map(r => r.map(csvCell).join(','))].join('\n') + '\n';
  fs.writeFileSync(path.join(HERE, outFile), csv);

  console.log(`\n=== ${name} ===`);
  console.log(`reviewed ${recipes.length}  keep ${counts.keep}  fix ${counts.fix}  delete ${counts.delete}`);
  const sorted = Array.from(reasonTally.entries()).sort((a, b) => b[1] - a[1]);
  for (const [reason, n] of sorted.slice(0, 8)) {
    console.log(`  ${n.toString().padStart(3)} × ${reason}`);
  }
  // Two worst examples
  if (worstDeletes.length > 0) {
    console.log('  worst examples:');
    for (const w of worstDeletes.slice(0, 3)) {
      console.log(`    [${w.id}] ${w.title} → ${w.reason}`);
    }
  }
  return { name, reviewed: recipes.length, counts, reasonTally, worstDeletes };
}

const tmdb = audit('TheMealDB',  ['v2_tmdb_a.json', 'v2_tmdb_b.json'], 'audit_v2_tmdb.csv');
const usda = audit('USDA MyPlate',['v2_usda_a.json', 'v2_usda_b.json'], 'audit_v2_usda.csv');
const cfg  = audit('Canada FG',   ['v2_cfg_a.json'],                    'audit_v2_cfg.csv');

console.log('\n=== combined deletion reasons (top 10) ===');
const combined = new Map();
for (const r of [tmdb, usda, cfg]) {
  for (const [reason, n] of r.reasonTally) {
    combined.set(reason, (combined.get(reason) || 0) + n);
  }
}
for (const [reason, n] of Array.from(combined.entries()).sort((a, b) => b[1] - a[1]).slice(0, 10)) {
  console.log(`  ${n.toString().padStart(3)} × ${reason}`);
}

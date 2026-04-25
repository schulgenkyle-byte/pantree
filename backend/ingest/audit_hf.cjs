#!/usr/bin/env node
// Recipe quality audit for HF source bucket. Reads two sample JSON dumps,
// dedupes by id, scores each recipe on 5 criteria, writes CSV.

const fs = require('fs');
const path = require('path');

const HERE = __dirname;
const A = JSON.parse(fs.readFileSync(path.join(HERE, 'hf_samples_a.json'), 'utf8'));
const B = JSON.parse(fs.readFileSync(path.join(HERE, 'hf_samples_b.json'), 'utf8'));

const seen = new Map();
for (const r of [...(A.recipes || []), ...(B.recipes || [])]) {
  if (!seen.has(r.id)) seen.set(r.id, r);
}
const recipes = Array.from(seen.values());
console.log(`Loaded ${recipes.length} unique recipes from ${A.recipes.length} + ${B.recipes.length}.`);

// Heuristics ----------------------------------------------------------------

function badTitle(t) {
  if (!t || typeof t !== 'string') return 'title missing';
  const s = t.trim();
  if (s.length === 0) return 'title empty';
  if (/^recipe\s*\d+/i.test(s)) return 'title is generic "Recipe N"';
  if (/^\d+$/.test(s)) return 'title is just a number';
  if (s.length < 3) return 'title too short';
  if (/^untitled/i.test(s)) return 'title is "untitled"';
  // Lots of HF rows have ALLCAPS or weird punctuation but those are still fine.
  return null;
}

// Recognize the classic HF parser glitch where unicode fractions ½ ¼ ¾ ⅓ ⅔ ⅛
// got dropped or fused with surrounding digits — surfaces as quantity values
// like 12, 14, 34, 13, 23, 18, 38, 58, 78, or two-digit teaspoon counts.
const FRACTION_GLITCH_QTYS = new Set([12, 14, 34, 13, 23, 18, 38, 58, 78]);
const SMALL_UNITS = new Set(['teaspoon', 'teaspoons', 'tsp', 'tablespoon', 'tablespoons', 'tbsp']);

function badIngredients(ings) {
  if (!Array.isArray(ings) || ings.length === 0) return 'no ingredients';
  let undefCount = 0;
  let numericOnly = 0;
  let singleChar = 0;
  let htmlFrag = 0;
  let nameStartsDigits = 0; // e.g. "34 cups all-purpose flour"
  let glitchFracQty = 0;    // qty 12 with unit teaspoon → was probably 1/2 tsp
  let zeroQty = 0;
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
  if (undefCount >= Math.ceil(ings.length / 2)) return 'ingredients mostly undefined';
  if (undefCount > 0) return `${undefCount} undefined ingredient(s)`;
  if (numericOnly > 0) return `${numericOnly} numeric-only ingredient name(s)`;
  if (singleChar > 0) return `${singleChar} single-letter ingredient name(s)`;
  if (htmlFrag > 0) return 'HTML fragments in ingredient names';
  if (nameStartsDigits > 0) return 'fraction collapsed into quantity prefix (e.g. "34 cups")';
  if (glitchFracQty > 0) return `${glitchFracQty} ingredient quantity looks like a dropped unicode fraction`;
  if (zeroQty >= Math.ceil(ings.length * 0.6)) return 'most ingredients have zero quantity';
  return null;
}

function badInstructions(text, stepCount) {
  if (!stepCount || stepCount === 0) return 'no instructions';
  if (!text || typeof text !== 'string' || text.trim().length === 0) return 'instructions empty';
  if (text.trim().length < 20) return 'instructions extremely short';
  // Detect non-cooking text — e.g. just a URL, or just credits.
  if (/^https?:\/\//i.test(text.trim())) return 'instructions are just a URL';
  return null;
}

function badImage(url) {
  if (!url || typeof url !== 'string' || url.trim().length === 0) return 'no image';
  return null;
}

function badCuisine(cuisine, contentType) {
  // cuisine is allowed to be null for HF — most rows don't have it.
  if (cuisine == null || cuisine === '') return null; // not flagged on its own
  if (typeof cuisine !== 'string') return 'cuisine not a string';
  if (cuisine.length > 60) return 'cuisine field unreasonably long';
  return null;
}

// Verdict policy:
//   delete  → fundamental data is missing/broken (no instructions, mostly-undefined ingredients,
//             generic/garbage title, instructions are just a URL)
//   fix     → core recipe is salvageable but needs cleanup (missing photo, ingredient parse glitch,
//             missing cuisine, small ingredient parser warts)
//   keep    → all five criteria pass cleanly
function classify(r) {
  const reasons = [];
  const tBad = badTitle(r.title);
  const iBad = badIngredients(r.ingredients || [], r.ingredient_names);
  const sBad = badInstructions(r.instructions, r.step_count);
  const pBad = badImage(r.image_url);
  const cBad = badCuisine(r.cuisine, r.content_type);

  // Hard-fail criteria → delete (recipe is unusable as-is in catalog)
  if (tBad === 'title is just a number' || tBad === 'title is generic "Recipe N"' || tBad === 'title missing' || tBad === 'title empty' || tBad === 'title is "untitled"') {
    return { verdict: 'delete', reason: tBad };
  }
  if (iBad === 'no ingredients' || iBad === 'ingredients mostly undefined') {
    return { verdict: 'delete', reason: iBad };
  }
  if (sBad === 'no instructions' || sBad === 'instructions empty' || sBad === 'instructions are just a URL') {
    return { verdict: 'delete', reason: sBad };
  }
  // Parser glitches that make the recipe dangerously wrong (12 tsp salt, 34 cups flour).
  // These are not auto-fixable without re-fetching source — better to drop.
  if (iBad && iBad.startsWith('fraction collapsed')) {
    return { verdict: 'delete', reason: iBad };
  }
  if (iBad && iBad.endsWith('looks like a dropped unicode fraction')) {
    return { verdict: 'delete', reason: iBad };
  }

  // Soft issues → fix
  for (const x of [tBad, iBad, sBad, pBad, cBad]) if (x) reasons.push(x);
  if (reasons.length > 0) return { verdict: 'fix', reason: reasons.join('; ') };
  return { verdict: 'keep', reason: '' };
}

// Run audit ---------------------------------------------------------------

function csvCell(v) {
  if (v == null) return '';
  const s = String(v);
  if (/[",\n\r]/.test(s)) return '"' + s.replace(/"/g, '""') + '"';
  return s;
}

const rows = [];
const counts = { keep: 0, fix: 0, delete: 0 };
const reasonTally = new Map();
for (const r of recipes) {
  const { verdict, reason } = classify(r);
  counts[verdict]++;
  if (verdict === 'delete') {
    reasonTally.set(reason, (reasonTally.get(reason) || 0) + 1);
  }
  rows.push([r.id, r.title || '', verdict, reason]);
}

const header = 'id,title,verdict,reason';
const csv = [header, ...rows.map(r => r.map(csvCell).join(','))].join('\n') + '\n';
const outPath = path.join(HERE, 'recipe_audit_hf.csv');
fs.writeFileSync(outPath, csv);

console.log('---');
console.log(`Total reviewed: ${recipes.length}`);
console.log(`keep:   ${counts.keep}`);
console.log(`fix:    ${counts.fix}`);
console.log(`delete: ${counts.delete}`);
console.log('');
console.log('Top deletion reasons:');
const sorted = Array.from(reasonTally.entries()).sort((a, b) => b[1] - a[1]);
for (const [reason, n] of sorted.slice(0, 10)) {
  console.log(`  ${n.toString().padStart(3)} × ${reason}`);
}
console.log('');
console.log(`CSV written: ${outPath}`);

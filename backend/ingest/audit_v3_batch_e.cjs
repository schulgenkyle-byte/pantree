// Batch E audit: HF tail (3800-4300) + non-HF higher offsets (TMDB, USDA, CFG)
// Strict 8-criterion evaluation; conservative on DELETE.

const fs = require('fs');
const path = require('path');

const ROOT = 'C:/Users/12566/Downloads/pantrie-build (1)/pantrie-build/backend/ingest';

// ---------- helpers ----------
const SAFE_NO_COOK_KEYWORDS = [
  'salad','smoothie','dressing','dip','salsa','guacamole','hummus','pesto',
  'tartare','ceviche','gazpacho','overnight oats','chia pudding',
  'parfait','sandwich','wrap','toast','spread','butter','jam','jelly',
  'pickle','marinade','vinaigrette','slaw','tabbouleh','tabouli',
  'energy bar','energy ball','energy bite','no-bake','no bake','no_bake',
  'icebox','refrigerator','fridge','freezer','chilled','frozen',
  'icing','frosting','ice cream','popsicle','sorbet','granola','muesli',
  'cream pie','chiffon pie','frozen pie','icebox pie','rocky road','tiramisu',
  'cheesecake','dessert','pudding','mousse','trifle','panna cotta',
  'charcuterie','crudite','tartar','lassi','kombucha','horchata',
  'punch','infusion','syrup','simple syrup','cold brew','iced','milkshake',
  'shake','sushi roll','poke bowl','poke','tartine','bruschetta','crostini',
  'cocktail','mocktail','beverage','drink','tea','lemonade','juice'
];

const COOK_VERBS = [
  'bake','roast','grill','fry','saute','sauté','sear','simmer','boil',
  'broil','steam','poach','braise','stew','toast','heat','cook','cooking',
  'oven','stove','stovetop','skillet','pan-fry','deep-fry','stir-fry',
  'microwave','preheat','warm','melt','reduce','reduction','blanch',
  'caramelize','caramelise','brown','crisp','roasted','baked','grilled',
  'fried','seared','simmered','boiled','broiled','steamed','poached','braised',
  'instant pot','slow cook','slow cooker','pressure cook','crockpot','crock-pot','crock pot',
  'air fryer','airfryer','smoker','smoking','smoke','barbecue','barbeque','bbq',
  'griddle','sautéed','sauteed','reheat','char','blacken','flambe','flambé'
];

const HOT_DISH_KEYWORDS = [
  'soup','stew','chili','casserole','bake','roast','grilled','fried',
  'seared','steak','roast','braised','sauteed','sautéed','stir-fry','stir fry',
  'risotto','lasagna','lasagne','pasta','pizza','curry','tagine','goulash',
  'pot pie','quiche','frittata','omelet','omelette','meatloaf','meatballs',
  'burger','wing','wings','bacon','pulled pork','brisket','ribs',
  'chowder','bisque','mac and cheese','macaroni','enchilada','burrito',
  'fajita','quesadilla','souffle','soufflé','gratin','dauphinoise','rissole',
  'cobbler','crisp','crumble','pie','cake','muffin','bread','biscuit','scone',
  'pancake','waffle','french toast','grits','polenta','dumpling','potstickers',
  'porridge','oatmeal','rice','noodle','noodles','ramen','pho','udon',
  'jambalaya','paella','gumbo','stroganoff','wellington','schnitzel','katsu',
  'tempura','teriyaki','adobo','dal','daal','chana','rajma','kebab','kofta',
  'kibbe','shawarma','souvlaki','meat','chicken','beef','pork','lamb','turkey',
  'fish','salmon','tuna','shrimp','prawn','lobster','crab','scallop','clam',
  'mussel','oyster','squid','octopus','tofu','tempeh','sausage','ham','egg'
];

const FRACTION_RE = /[¼½¾⅐⅑⅒⅓⅔⅕⅖⅗⅘⅙⅚⅛⅜⅝⅞]|\d+\s*\/\s*\d+/;

function lower(s) { return (s || '').toString().toLowerCase(); }

function looksLikeRealDish(title) {
  const t = lower(title).trim();
  if (!t) return false;
  if (t.length < 3) return false;
  // mostly punctuation / numbers
  const letters = (t.match(/[a-z]/g) || []).length;
  if (letters < 3) return false;
  // single junk word like "untitled", "test"
  if (/^(untitled|test|recipe|tbd|todo|n\/?a|none|null)$/.test(t)) return false;
  // weird repeated chars or excessive symbols
  if (/^[^a-z0-9]+$/i.test(title)) return false;
  return true;
}

function ingredientsLook(ings) {
  if (!Array.isArray(ings) || ings.length === 0) return { ok: false, allUndef: true, suspects: [] };
  const suspects = [];
  let allUndef = true;
  for (const ing of ings) {
    const name = (ing && (ing.name || ing.canonical_name) || '').toString().trim();
    const qty = ing && ing.quantity;
    const unit = ing && ing.unit;
    const hasName = !!name && name.toLowerCase() !== 'undefined' && name.toLowerCase() !== 'null';
    const hasQty = qty != null && qty !== '' && qty !== 'undefined';
    const hasUnit = unit != null && unit !== '' && unit !== 'undefined';
    if (hasName && (hasQty || hasUnit)) allUndef = false;
    // Even ingredients with no qty/unit but a real name (e.g., "salt to taste", "lemon") are common -> not suspect
    if (!hasName) suspects.push('(no-name)');
    else if (!hasName && !hasQty && !hasUnit) suspects.push(name || '(blank)');
    // Garbage names: too long, weird symbols
    else if (name.length > 80) suspects.push(name.slice(0, 30) + '…');
    // Fraction-vs-quantity bug: qty > 20 with a fraction in original raw could indicate parsing error
    // We approximate by checking suspiciously huge integers on units that are usually small
    else if (typeof qty === 'number' && qty > 20 && unit && /^(tsp|teaspoon|tbsp|tablespoon|pinch|dash)$/i.test(unit)) {
      suspects.push(`${name} qty=${qty}${unit}`);
    }
  }
  return { ok: !allUndef, allUndef, suspects };
}

function stepsList(rec) {
  if (Array.isArray(rec.steps) && rec.steps.length) return rec.steps;
  const txt = rec.instructions || '';
  if (!txt) return [];
  // Split on bullets, periods, newlines
  const parts = txt.split(/(?:\s*•\s*|\s*\n+\s*|(?:\.\s+)(?=[A-Z]))/).map(s => s.trim()).filter(Boolean);
  return parts;
}

function hasCookTransformation(rec) {
  const blob = [
    rec.title,
    rec.description,
    rec.instructions,
    Array.isArray(rec.steps) ? rec.steps.join(' ') : ''
  ].map(lower).join(' ');
  for (const v of COOK_VERBS) {
    if (blob.includes(v)) return true;
  }
  return false;
}

function isHotDish(title) {
  const t = lower(title);
  for (const k of HOT_DISH_KEYWORDS) {
    if (t.includes(k)) return true;
  }
  return false;
}

function isSafeNoCook(title) {
  const t = lower(title);
  for (const k of SAFE_NO_COOK_KEYWORDS) {
    if (t.includes(k)) return true;
  }
  return false;
}

function titleVsIngredientMismatch(rec) {
  const t = lower(rec.title);
  const names = (rec.ingredients || []).map(i => lower(i.name || i.canonical_name || '')).join(' ');
  // Check protein nouns in title that should be in ingredient list
  const proteins = ['chicken','beef','pork','lamb','turkey','salmon','shrimp','tuna','tofu','bacon','sausage','ham','duck'];
  for (const p of proteins) {
    // Whole-word match on title
    const reTitle = new RegExp(`\\b${p}\\b`);
    if (reTitle.test(t) && !names.includes(p)) {
      // exception: "chicken-fried" or "vegan chicken" or "chicken-style"
      if (/(no[- ]?chicken|vegan|veggie|plant|mock|imitation|substitute)/.test(t)) continue;
      return p;
    }
  }
  return null;
}

function evaluate(rec) {
  const reasons = [];
  const suspects = [];
  let verdict = 'KEEP';

  // 1. Real dish title?
  if (!looksLikeRealDish(rec.title)) {
    return { verdict: 'DELETE', reasons: ['not-a-real-dish-title'], suspects: [] };
  }

  // 2. Ingredients with name+qty+unit? All-undefined -> DELETE
  const ingCheck = ingredientsLook(rec.ingredients);
  if (ingCheck.allUndef) {
    return { verdict: 'DELETE', reasons: ['all-ingredients-undefined'], suspects: ingCheck.suspects.slice(0, 4) };
  }
  if (ingCheck.suspects.length) suspects.push(...ingCheck.suspects.slice(0, 4));

  // 3. At least one step
  const steps = stepsList(rec);
  if (steps.length === 0) {
    return { verdict: 'DELETE', reasons: ['no-steps'], suspects: [] };
  }

  // 4. Cooking transformation present?
  const cookOK = hasCookTransformation(rec);
  const blob = lower([rec.title, rec.description, rec.instructions, Array.isArray(rec.steps)?rec.steps.join(' '):''].join(' '));
  const isColdPrep = /\b(chill|chilled|refrigerat|freeze|frozen|no[- ]?bake|no[- ]?cook|set in the fridge|overnight)\b/.test(blob);
  // pita/sandwich-style cold assembly detection
  const isAssemblyOnly = /\b(pita|sandwich|wrap|stuff|fill|spoon (into|onto)|tuck|spread|layer|assemble|top with|serve over|toss with)\b/.test(blob);
  if (!cookOK) {
    if (isHotDish(rec.title) && !isSafeNoCook(rec.title) && !isColdPrep && !isAssemblyOnly) {
      return { verdict: 'DELETE', reasons: ['hot-dish-no-cook-step'], suspects: [] };
    }
    // cold dishes are OK (raw salads, etc)
  }

  // 5. Beginner-followable: heuristic — if extremely trivial instructions ("make and enjoy"), DELETE
  const instr = (rec.instructions || '').trim();
  if (instr.length < 30 && steps.length < 2) {
    return { verdict: 'DELETE', reasons: ['instructions-trivial'], suspects: [] };
  }
  if (steps.length < 2 && instr.length < 80 && (rec.ingredients || []).length > 4) {
    reasons.push('instructions-too-thin');
    verdict = 'FIX';
  }

  // 6. Range parser bug: e.g., quantity stored as 1 with unit "to 2 cups" — we approximate by checking unit field for "to" or "-"
  for (const ing of (rec.ingredients || [])) {
    const u = (ing.unit || '').toString().toLowerCase();
    if (/\b(to|or)\b|\d+\s*[-–]\s*\d+/.test(u)) {
      reasons.push('range-parse-error');
      verdict = verdict === 'KEEP' ? 'FIX' : verdict;
      suspects.push(`${ing.name}|${ing.quantity}|${ing.unit}`);
      break;
    }
  }

  // 7. Fraction parser bug (qty>20 no fraction in name and a normally-small unit) — already added in ingCheck
  for (const ing of (rec.ingredients || [])) {
    const qty = ing.quantity;
    const unit = (ing.unit || '').toString().toLowerCase();
    if (typeof qty === 'number' && qty > 20 && /^(tsp|teaspoon|tbsp|tablespoon|pinch|dash)$/.test(unit)) {
      // If the name doesn't contain a fraction and qty looks like a misparsed fraction (e.g. 12 -> 1/2)
      return { verdict: 'DELETE', reasons: ['fraction-parser-bug'], suspects: [`${ing.name} qty=${qty}${unit}`] };
    }
  }

  // 8. Title-vs-ingredient mismatch
  const mismatch = titleVsIngredientMismatch(rec);
  if (mismatch) {
    reasons.push(`title-mentions-${mismatch}-not-in-ingredients`);
    verdict = verdict === 'KEEP' ? 'FIX' : verdict;
  }

  if (reasons.length === 0) reasons.push('ok');
  return { verdict, reasons, suspects: suspects.slice(0, 6) };
}

// ---------- run for one source ----------
function csvEscape(s) {
  if (s == null) return '';
  const v = s.toString();
  if (/[",\n]/.test(v)) return '"' + v.replace(/"/g, '""') + '"';
  return v;
}

function auditSource(label, jsonFiles, csvOut) {
  const all = [];
  for (const f of jsonFiles) {
    if (!fs.existsSync(f)) { console.log('  missing', f); continue; }
    const data = JSON.parse(fs.readFileSync(f, 'utf8'));
    const arr = data.recipes || [];
    for (const r of arr) all.push(r);
  }

  const counts = { KEEP: 0, FIX: 0, DELETE: 0 };
  const reasonHist = {};
  const rows = ['id,title,verdict,reason,has_photo,step_count,ingredient_count,suspect_ingredient_names'];

  for (const rec of all) {
    const e = evaluate(rec);
    counts[e.verdict] = (counts[e.verdict] || 0) + 1;
    for (const r of e.reasons) {
      const key = `${e.verdict}:${r}`;
      reasonHist[key] = (reasonHist[key] || 0) + 1;
    }
    const photo = rec.image_url ? 1 : 0;
    const stepCount = (Array.isArray(rec.steps) ? rec.steps.length : 0) || rec.step_count || stepsList(rec).length;
    const ingCount = (rec.ingredients || []).length;
    rows.push([
      csvEscape(rec.id),
      csvEscape(rec.title),
      csvEscape(e.verdict),
      csvEscape(e.reasons.join(';')),
      photo,
      stepCount,
      ingCount,
      csvEscape((e.suspects || []).join('|'))
    ].join(','));
  }

  fs.writeFileSync(csvOut, rows.join('\n'));
  console.log(`\n=== ${label} ===`);
  console.log(`  Total: ${all.length}`);
  console.log(`  KEEP:   ${counts.KEEP || 0}`);
  console.log(`  FIX:    ${counts.FIX || 0}`);
  console.log(`  DELETE: ${counts.DELETE || 0}`);
  const cookable = (counts.KEEP || 0) + (counts.FIX || 0);
  const yieldPct = all.length ? ((cookable / all.length) * 100).toFixed(1) : '0.0';
  console.log(`  Cookable yield: ${cookable}/${all.length} (${yieldPct}%)`);
  console.log(`  CSV: ${csvOut}`);
  return { label, total: all.length, counts, reasonHist, cookable, yieldPct, csvOut, all };
}

function batchFiles(dir, offsets) {
  return offsets.map(o => path.join(dir, `batch_${o}.json`));
}

const HF_OFFSETS = [3800, 3900, 4000, 4100, 4200, 4300];
const TMDB_OFFSETS = [200, 300];
const USDA_OFFSETS = [200, 300];
const CFG_OFFSETS = [100, 200];

const results = [];
results.push(auditSource('HF tail (3800-4300)',
  batchFiles(`${ROOT}/audit_v3_hf`, HF_OFFSETS),
  `${ROOT}/audit_v3_hf_e.csv`));
results.push(auditSource('TheMealDB (offset 200,300)',
  batchFiles(`${ROOT}/audit_v3_tmdb`, TMDB_OFFSETS),
  `${ROOT}/audit_v3_tmdb_v2.csv`));
results.push(auditSource('USDA MyPlate (offset 200,300)',
  batchFiles(`${ROOT}/audit_v3_usda`, USDA_OFFSETS),
  `${ROOT}/audit_v3_usda_v2.csv`));
results.push(auditSource('Canada FG (offset 100,200)',
  batchFiles(`${ROOT}/audit_v3_cfg`, CFG_OFFSETS),
  `${ROOT}/audit_v3_cfg_v2.csv`));

// Aggregate reason histograms
const allDelete = {};
const allFix = {};
let totalAll = 0, totalKeep = 0, totalFix = 0, totalDelete = 0;
const worst = []; // collect candidates (DELETE) with reason
const best = [];  // KEEP with photo + many ingredients

for (const r of results) {
  totalAll += r.total;
  totalKeep += (r.counts.KEEP || 0);
  totalFix  += (r.counts.FIX || 0);
  totalDelete += (r.counts.DELETE || 0);
  for (const [k, v] of Object.entries(r.reasonHist)) {
    if (k.startsWith('DELETE:')) {
      const rk = k.slice(7);
      allDelete[rk] = (allDelete[rk] || 0) + v;
    } else if (k.startsWith('FIX:')) {
      const rk = k.slice(4);
      if (rk === 'ok') continue;
      allFix[rk] = (allFix[rk] || 0) + v;
    }
  }
}

// Compute worst/best by re-evaluating
for (const r of results) {
  for (const rec of r.all) {
    const e = evaluate(rec);
    if (e.verdict === 'DELETE') worst.push({ src: r.label, rec, reasons: e.reasons });
    else if (e.verdict === 'KEEP' && rec.image_url && (rec.ingredients || []).length >= 6) best.push({ src: r.label, rec });
  }
}

const top8Del = Object.entries(allDelete).sort((a,b)=>b[1]-a[1]).slice(0, 8);
const top5Fix = Object.entries(allFix).sort((a,b)=>b[1]-a[1]).slice(0, 5);

console.log('\n========== FINAL SUMMARY ==========');
console.log(`Total recipes audited: ${totalAll}`);
console.log(`  KEEP:   ${totalKeep}`);
console.log(`  FIX:    ${totalFix}`);
console.log(`  DELETE: ${totalDelete}`);
console.log(`  Cookable yield: ${totalKeep + totalFix}/${totalAll} (${((totalKeep+totalFix)/totalAll*100).toFixed(1)}%)`);

console.log('\nTop 8 deletion reasons:');
for (const [k, v] of top8Del) console.log(`  ${v}  ${k}`);
console.log('\nTop 5 fix reasons:');
for (const [k, v] of top5Fix) console.log(`  ${v}  ${k}`);

console.log('\n5 worst (DELETE samples):');
for (const w of worst.slice(0, 5)) {
  console.log(`  [${w.src}] ${w.rec.id}  | ${w.rec.title}  | ${w.reasons.join(';')}`);
}
console.log('\n5 best (KEEP samples with photo + 6+ ingredients):');
for (const b of best.slice(0, 5)) {
  console.log(`  [${b.src}] ${b.rec.id}  | ${b.rec.title}  | ings=${b.rec.ingredients.length}`);
}

// Per-source non-HF holdup
console.log('\nNon-HF source holdup at higher offsets:');
for (const r of results.slice(1)) {
  const yieldNum = parseFloat(r.yieldPct);
  console.log(`  ${r.label}: ${r.yieldPct}% cookable -> ${yieldNum >= 95 ? 'YES (held up)' : yieldNum >= 80 ? 'PARTIAL' : 'NO'}`);
}

console.log('\nCSV paths:');
for (const r of results) console.log(`  ${r.csvOut}`);

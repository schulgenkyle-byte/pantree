// V2: removes the bias against complex recipes.
//
// Original score-huggingface.js had two bugs that capped the corpus to ~9-10
// ingredient recipes:
//   1. Hard reject at >20 ingredients (line 45) — drops curries, mole, gumbo,
//      Cincinnati chili, etc. that legitimately need 22-30 ingredients.
//   2. withQty bonus capped at 20 = +2 per ingredient with quantity, max 20.
//      A recipe with 10 quantity-bearing ingredients gets +20; a recipe with
//      5 only gets +10. Combined with the 5-10 ingredient sweet-spot bonus,
//      recipes with 9-10 ingredients always outrank 5-8 ingredient recipes.
//
// V2 fixes:
//   - Hard ceiling raised from 20 to 30 (covers 99.99% of real recipes)
//   - Flatten ingredient-count sweet spot to 4-25 (not 5-10)
//   - withQty bonus scaled per-ingredient ratio (proportion with qty), not
//     absolute count, so coverage is what matters not raw count
//
// Run:
//   TARGET=25000 node ingest/score-huggingface-v2.js
//
// Output: ./normalized/hf-top-v2.json (alongside the original hf-top.json)

import { createReadStream, createWriteStream, readFileSync, writeFileSync, existsSync } from 'node:fs';
import { createInterface } from 'node:readline';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const IN = fileURLToPath(new URL('./normalized/hf-normalized.ndjson', import.meta.url));
const OUTDIR = fileURLToPath(new URL('./normalized/', import.meta.url));
const OUT = join(OUTDIR, 'hf-top-v2.json');

const TARGET = parseInt(process.env.TARGET || '25000', 10);
const MAX_INGREDIENTS = parseInt(process.env.MAX_INGREDIENTS || '30', 10);
const MIN_INGREDIENTS = parseInt(process.env.MIN_INGREDIENTS || '3', 10);

const KNOWN_TITLE_WORDS = new Set();

const BAD_NAME_PATTERNS = [
  /^(cover|bring|allow|leave|serve|taste|place|spoon|press|enjoy|repeat|apply|smear|layer|whisk|knead|brush|let |wait|cool|warm|drain|remove|unwrap|wrap |cut |mix |chop |dice |peel |rinse|grate|shred|slice|blend|crush|mince|stir |fold |drizzl|sprink|arrang|spread|put |add |combine|pour |heat |simmer|reduce|transfer|preheat|bake|roast|fry|grill|boil|sauté|saute)/i,
  /\.\s*$/,
  /^\d+$/,
  /https?:/,
  /click|visit|see|see note|see above|see below/i,
];

const BAD_TITLE_PATTERNS = [
  /^(untitled|recipe|unnamed|no title)/i,
  /\d{3,}/,
  /[<>{}\[\]]/,
  /http/,
];

const COOKING_VERBS = /\b(bake|boil|broil|brown|chop|combine|cook|cover|cream|cut|dice|drain|fold|fry|grill|heat|knead|melt|mix|pour|rinse|roast|sauté|saute|sear|season|simmer|slice|spread|stir|whisk|add|preheat)\b/i;

function scoreRecipe(r) {
  const title = String(r.title || '').trim();
  if (title.length < 4 || title.length > 80) return null;
  if (BAD_TITLE_PATTERNS.some(p => p.test(title))) return null;

  const ings = r.ingredients || [];
  // V2: widened from 3-20 to MIN_INGREDIENTS-MAX_INGREDIENTS (default 3-30)
  if (ings.length < MIN_INGREDIENTS || ings.length > MAX_INGREDIENTS) return null;

  for (const i of ings) {
    const n = String(i.name || '');
    if (n.length < 2 || n.length > 60) return null;
    if (BAD_NAME_PATTERNS.some(p => p.test(n))) return null;
  }

  const steps = r.steps || [];
  if (steps.length < 3 || steps.length > 15) return null;

  const allStepText = steps.map(s => s.text || '').join(' ');
  if (!COOKING_VERBS.test(allStepText)) return null;

  for (const s of steps) {
    const t = String(s.text || '');
    if (t.length < 15 || t.length > 1500) return null;
    if (/https?:/.test(t) || /click here/i.test(t)) return null;
  }

  // ---- V2 Scoring ----
  let score = 0;
  const ingCount = ings.length;

  // V2: flat bonus for any reasonable ingredient count.
  // Was: 5-10 → +20, 4-12 → +12, else +5 (heavily favored simple recipes)
  // Now: 4-25 → +15, 3 or 26-30 → +8, else +3
  if (ingCount >= 4 && ingCount <= 25) score += 15;
  else if (ingCount === 3 || (ingCount >= 26 && ingCount <= 30)) score += 8;
  else score += 3;

  // Step count — sweet spot 4-8 (unchanged)
  const stepCount = steps.length;
  if (stepCount >= 4 && stepCount <= 8) score += 15;
  else if (stepCount >= 3 && stepCount <= 10) score += 8;
  else score += 3;

  // V2: withQty as a RATIO, not an absolute count.
  // Was: Math.min(20, withQty * 2) — 10-ing recipe always wins over 5-ing
  // Now: % of ingredients with quantity * 20, max 20 — proportion matters,
  // not raw count, so a 5-ing recipe with all qty parsed scores the same as
  // a 20-ing recipe with all qty parsed.
  const withQty = ings.filter(i => typeof i.quantity === 'number' && i.quantity > 0).length;
  const qtyRatio = withQty / Math.max(1, ingCount);
  score += Math.round(qtyRatio * 20);

  // Title quality
  const titleWords = title.split(/\s+/).filter(Boolean).length;
  if (titleWords >= 2 && titleWords <= 6) score += 10;
  if (/^[A-Z]/.test(title)) score += 5;

  // Cooking-verb density
  const verbs = (allStepText.match(COOKING_VERBS) || []).length;
  score += Math.min(10, verbs * 1.5);

  // Penalize absurdly long steps
  const avgStepLen = allStepText.length / stepCount;
  if (avgStepLen > 300) score -= 15;

  // Bonus: mentions cooking time
  const hasTimer = steps.some(s => s.timerSeconds != null);
  if (hasTimer) score += 8;

  // V2 bonus: complexity bonus for recipes with >12 ingredients to nudge them
  // into the picked top, slight tilt to add diversity to the corpus.
  if (ingCount > 12) score += 5;

  return score;
}

function normalizeTitleWord(t) {
  return String(t || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
}

async function main() {
  console.log(`V2 scoring HF recipes for top ${TARGET}...`);
  console.log(`  ingredient range: ${MIN_INGREDIENTS}-${MAX_INGREDIENTS}`);
  console.log(`  scoring: flat 4-25 sweet spot, withQty as ratio (proportion-based), +5 complexity bonus for >12 ings`);
  const scored = [];
  let read = 0, kept = 0;

  const rl = createInterface({ input: createReadStream(IN, { encoding: 'utf8' }), crlfDelay: Infinity });
  for await (const line of rl) {
    if (!line.trim()) continue;
    read++;
    let r; try { r = JSON.parse(line); } catch { continue; }
    const s = scoreRecipe(r);
    if (s == null) continue;
    scored.push([s, r]);
    kept++;
    if (read % 50000 === 0) process.stdout.write(`  ${read} read, ${kept} candidates so far\r`);
  }
  process.stdout.write('\n');

  console.log(`sorting ${scored.length} candidates...`);
  scored.sort((a, b) => b[0] - a[0]);

  console.log('deduping by title...');
  const seenTitles = new Set();
  const picked = [];
  for (const [s, r] of scored) {
    const key = normalizeTitleWord(r.title);
    if (seenTitles.has(key)) continue;
    seenTitles.add(key);
    picked.push({ score: s, ...r });
    if (picked.length >= TARGET) break;
  }

  writeFileSync(OUT, JSON.stringify(picked, null, 0), 'utf8');
  console.log(`✓ wrote ${picked.length} recipes to ${OUT}`);
  console.log(`  score range: ${picked[picked.length - 1].score} → ${picked[0].score}`);

  // V2: also dump ingredient-count distribution so we can see the fix worked
  const distMap = new Map();
  for (const r of picked) {
    const c = (r.ingredients || []).length;
    distMap.set(c, (distMap.get(c) || 0) + 1);
  }
  const dist = [...distMap.entries()].sort((a, b) => a[0] - b[0]);
  console.log('  ingredient count distribution:');
  for (const [n, count] of dist) console.log(`    ${n.toString().padStart(2)} ings: ${count}`);

  console.log(`  sample top 5:`);
  for (const r of picked.slice(0, 5)) console.log(`    [${r.score}] ${r.title} (${(r.ingredients || []).length} ings)`);
}

main().catch(e => { console.error(e); process.exit(1); });

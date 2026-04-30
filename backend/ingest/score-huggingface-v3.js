// V3: stratified sampling. Buckets recipes by ingredient count and picks
// proportionally so the final corpus spans the full ingredient-count
// distribution instead of clustering at one end.
//
// History of bugs:
//   V1 (original): hard reject >20 ings, +20 sweet-spot bonus 5-10, withQty
//   capped at 20 absolute. Result: 25K records all had 9-10 ingredients.
//   V2 (mine): widened to 3-30, flat sweet-spot 4-25, withQty as ratio,
//   +5 complexity bonus for >12. Result: scores clustered at 78-81 and
//   the complexity bonus dominated, so 25K records all had 13-25 ingredients.
//   V3: stratified sampling. Score buckets independently. Take 6,250 from
//   each bucket so we get a natural mix.
//
// Run:
//   TARGET=25000 node ingest/score-huggingface-v3.js
//
// Output: ./normalized/hf-top-v3.json

import { createReadStream, createWriteStream, readFileSync, writeFileSync, existsSync } from 'node:fs';
import { createInterface } from 'node:readline';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const IN = fileURLToPath(new URL('./normalized/hf-normalized.ndjson', import.meta.url));
const OUTDIR = fileURLToPath(new URL('./normalized/', import.meta.url));
const OUT = join(OUTDIR, 'hf-top-v3.json');

const TARGET = parseInt(process.env.TARGET || '25000', 10);

// Stratified buckets by ingredient count. Distribution roughly matches the
// natural shape of the source corpus (lots of 5-8 ing recipes, fewer 13+).
// Pick proportional to natural distribution, not equal across buckets.
const BUCKETS = [
  { min: 3,  max: 5,  share: 0.18 }, // 18% - very simple recipes
  { min: 6,  max: 8,  share: 0.32 }, // 32% - bulk of natural distribution
  { min: 9,  max: 12, share: 0.28 }, // 28% - mid-complexity (where v1 lived)
  { min: 13, max: 18, share: 0.16 }, // 16% - complex (curries, casseroles)
  { min: 19, max: 30, share: 0.06 }, // 6%  - very complex (mole, gumbo, paella)
];

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

function bucketFor(ingCount) {
  for (let i = 0; i < BUCKETS.length; i++) {
    const b = BUCKETS[i];
    if (ingCount >= b.min && ingCount <= b.max) return i;
  }
  return -1;
}

function scoreRecipe(r) {
  const title = String(r.title || '').trim();
  if (title.length < 4 || title.length > 80) return null;
  if (BAD_TITLE_PATTERNS.some(p => p.test(title))) return null;

  const ings = r.ingredients || [];
  if (ings.length < 3 || ings.length > 30) return null;
  const bucket = bucketFor(ings.length);
  if (bucket < 0) return null;

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

  // ---- V3 Scoring (no ingredient-count bias; bucket assignment handles diversity)
  let score = 0;

  // Step count — sweet spot 4-8
  const stepCount = steps.length;
  if (stepCount >= 4 && stepCount <= 8) score += 15;
  else if (stepCount >= 3 && stepCount <= 10) score += 8;
  else score += 3;

  // withQty as RATIO — coverage matters, not raw count
  const withQty = ings.filter(i => typeof i.quantity === 'number' && i.quantity > 0).length;
  const qtyRatio = withQty / Math.max(1, ings.length);
  score += Math.round(qtyRatio * 25);

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

  return { score, bucket };
}

function normalizeTitleWord(t) {
  return String(t || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
}

async function main() {
  console.log(`V3 stratified scoring HF recipes for top ${TARGET}...`);
  console.log('  buckets (ingredient count → share of corpus):');
  for (const b of BUCKETS) console.log(`    ${b.min}-${b.max} ings → ${Math.round(b.share*100)}%`);

  // Score and bucket each recipe
  const bucketed = BUCKETS.map(() => []);
  let read = 0, kept = 0;

  const rl = createInterface({ input: createReadStream(IN, { encoding: 'utf8' }), crlfDelay: Infinity });
  for await (const line of rl) {
    if (!line.trim()) continue;
    read++;
    let r; try { r = JSON.parse(line); } catch { continue; }
    const result = scoreRecipe(r);
    if (result == null) continue;
    bucketed[result.bucket].push([result.score, r]);
    kept++;
    if (read % 50000 === 0) process.stdout.write(`  ${read} read, ${kept} candidates so far\r`);
  }
  process.stdout.write('\n');

  console.log('candidates per bucket:');
  for (let i = 0; i < BUCKETS.length; i++) {
    console.log(`  bucket ${i} (${BUCKETS[i].min}-${BUCKETS[i].max} ings): ${bucketed[i].length}`);
  }

  // Sort each bucket by score desc
  for (const b of bucketed) b.sort((a, b) => b[0] - a[0]);

  // Pick proportional to share, dedupe by normalized title GLOBALLY
  const picked = [];
  const seenTitles = new Set();
  for (let i = 0; i < BUCKETS.length; i++) {
    const want = Math.round(TARGET * BUCKETS[i].share);
    let taken = 0;
    for (const [s, r] of bucketed[i]) {
      const key = normalizeTitleWord(r.title);
      if (seenTitles.has(key)) continue;
      seenTitles.add(key);
      picked.push({ score: s, ...r });
      taken++;
      if (taken >= want) break;
    }
    console.log(`  bucket ${i}: took ${taken} (target ${want})`);
  }

  // Shuffle so the JSON isn't bucket-ordered
  for (let i = picked.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [picked[i], picked[j]] = [picked[j], picked[i]];
  }

  writeFileSync(OUT, JSON.stringify(picked, null, 0), 'utf8');
  console.log(`✓ wrote ${picked.length} recipes to ${OUT}`);

  // Distribution
  const distMap = new Map();
  for (const r of picked) {
    const c = (r.ingredients || []).length;
    distMap.set(c, (distMap.get(c) || 0) + 1);
  }
  const dist = [...distMap.entries()].sort((a, b) => a[0] - b[0]);
  console.log('  ingredient count distribution:');
  for (const [n, count] of dist) {
    const bar = '#'.repeat(Math.floor(count / 100));
    console.log(`    ${n.toString().padStart(2)} ings: ${count.toString().padStart(5)} ${bar}`);
  }

  console.log(`  sample (random 5):`);
  for (const r of picked.slice(0, 5)) console.log(`    [${r.score}] ${r.title} (${(r.ingredients || []).length} ings)`);
}

main().catch(e => { console.error(e); process.exit(1); });

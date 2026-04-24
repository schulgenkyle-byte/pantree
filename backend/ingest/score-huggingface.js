// Score + filter normalized HF recipes, pick top N by quality heuristics.
// No LLM, no network — pure deterministic scoring on shape/content signals.

import { createReadStream, createWriteStream, readFileSync, writeFileSync, existsSync } from 'node:fs';
import { createInterface } from 'node:readline';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const IN = fileURLToPath(new URL('./normalized/hf-normalized.ndjson', import.meta.url));
const OUTDIR = fileURLToPath(new URL('./normalized/', import.meta.url));
const OUT = join(OUTDIR, 'hf-top.json');

const TARGET = parseInt(process.env.TARGET || '5000', 10);

// Canonicalize already-loaded recipe titles + canonical ingredient fingerprints
// so we don't dupe against anything already in the catalog.
// (We'll be stricter about dedup at upload time; here we just skip obvious ones.)
const KNOWN_TITLE_WORDS = new Set(); // optional

// Signals that the parser misfired
const BAD_NAME_PATTERNS = [
  /^(cover|bring|allow|leave|serve|taste|place|spoon|press|enjoy|repeat|apply|smear|layer|whisk|knead|brush|let |wait|cool|warm|drain|remove|unwrap|wrap |cut |mix |chop |dice |peel |rinse|grate|shred|slice|blend|crush|mince|stir |fold |drizzl|sprink|arrang|spread|put |add |combine|pour |heat |simmer|reduce|transfer|preheat|bake|roast|fry|grill|boil|sauté|saute)/i,
  /\.\s*$/,
  /^\d+$/, // just a number
  /https?:/, // URLs in ingredient
  /click|visit|see|see note|see above|see below/i,
];

const BAD_TITLE_PATTERNS = [
  /^(untitled|recipe|unnamed|no title)/i,
  /\d{3,}/,     // titles with big numbers (serial-number garbage)
  /[<>{}\[\]]/, // HTML/bracket junk
  /http/,
];

const COOKING_VERBS = /\b(bake|boil|broil|brown|chop|combine|cook|cover|cream|cut|dice|drain|fold|fry|grill|heat|knead|melt|mix|pour|rinse|roast|sauté|saute|sear|season|simmer|slice|spread|stir|whisk|add|preheat)\b/i;

function scoreRecipe(r) {
  // Hard filters — return null to drop entirely
  const title = String(r.title || '').trim();
  if (title.length < 4 || title.length > 80) return null;
  if (BAD_TITLE_PATTERNS.some(p => p.test(title))) return null;

  const ings = r.ingredients || [];
  if (ings.length < 3 || ings.length > 20) return null;

  // Reject if any ingredient looks like an instruction fragment
  for (const i of ings) {
    const n = String(i.name || '');
    if (n.length < 2 || n.length > 60) return null;
    if (BAD_NAME_PATTERNS.some(p => p.test(n))) return null;
  }

  const steps = r.steps || [];
  if (steps.length < 3 || steps.length > 15) return null;

  // Must have at least one cooking verb across steps
  const allStepText = steps.map(s => s.text || '').join(' ');
  if (!COOKING_VERBS.test(allStepText)) return null;

  // Each step must have reasonable length
  for (const s of steps) {
    const t = String(s.text || '');
    if (t.length < 15 || t.length > 1500) return null;
    if (BAD_NAME_PATTERNS.some(p => p.test(t.slice(0, 10)))) { /* ok for steps */ }
    if (/https?:/.test(t) || /click here/i.test(t)) return null;
  }

  // ---- Scoring (higher = better) ----
  let score = 0;

  // Ingredient count — sweet spot 5-10
  const ingCount = ings.length;
  if (ingCount >= 5 && ingCount <= 10) score += 20;
  else if (ingCount >= 4 && ingCount <= 12) score += 12;
  else score += 5;

  // Step count — sweet spot 4-8
  const stepCount = steps.length;
  if (stepCount >= 4 && stepCount <= 8) score += 15;
  else if (stepCount >= 3 && stepCount <= 10) score += 8;
  else score += 3;

  // Ingredients with actual quantities parsed (proves clean ingredient lines)
  const withQty = ings.filter(i => typeof i.quantity === 'number' && i.quantity > 0).length;
  score += Math.min(20, withQty * 2);

  // Title quality
  const titleWords = title.split(/\s+/).filter(Boolean).length;
  if (titleWords >= 2 && titleWords <= 6) score += 10;
  if (/^[A-Z]/.test(title)) score += 5; // proper case

  // Cooking-verb density in steps (cap at 10)
  const verbs = (allStepText.match(COOKING_VERBS) || []).length;
  score += Math.min(10, verbs * 1.5);

  // Penalize absurdly long steps (probably concatenated recipes)
  const avgStepLen = allStepText.length / stepCount;
  if (avgStepLen > 300) score -= 15;

  // Penalize recipes that are just dessert-adjacent to something else (keeps variety)
  // Actually, skip this — let the variety ranker handle it later.

  // Bonus: mentions cooking time (usually higher quality)
  const hasTimer = steps.some(s => s.timerSeconds != null);
  if (hasTimer) score += 8;

  return score;
}

function normalizeTitleWord(t) {
  return String(t || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
}

async function main() {
  console.log(`Scoring HF recipes for top ${TARGET}...`);
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

  // Dedup by normalized title (keep highest-scoring of each title)
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
  console.log(`  score range: ${picked[picked.length - 1].score} — ${picked[0].score}`);
  console.log(`  sample top 3:`);
  for (const r of picked.slice(0, 3)) console.log(`    [${r.score}] ${r.title}`);
}

main().catch(e => { console.error(e); process.exit(1); });

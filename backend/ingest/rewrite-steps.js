#!/usr/bin/env node
/**
 * rewrite-steps.js
 *
 * Post-process recipe_step.text to strip filler/flowery language using
 * pure regex — no LLM. Reads the JSON dump produced by:
 *
 *   wrangler d1 execute pantrie-db-staging --remote --json \
 *     --command="SELECT recipe_id, seq, text FROM recipe_step" \
 *     > ingest/tmp/all-steps.json
 *
 * Writes an UPDATE-per-changed-row SQL file to ingest/rewrite-steps.sql.
 *
 * Safety:
 *   - Never shortens a step below 10 chars (skip).
 *   - Preserves explicit temperatures ("350°F", "180C"), times
 *     ("for 10 minutes", "5 mins"), and measurements ("1 cup", "2 tbsp").
 *   - Only touches recipe_step.text.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname  = path.dirname(__filename);

const INPUT  = path.join(__dirname, 'tmp', 'all-steps.json');
const OUTPUT = path.join(__dirname, 'rewrite-steps.sql');

// --- load ------------------------------------------------------------------
const raw = JSON.parse(fs.readFileSync(INPUT, 'utf8'));
const rows = Array.isArray(raw) && raw[0] && Array.isArray(raw[0].results)
  ? raw[0].results
  : [];

if (!rows.length) {
  console.error('No rows found in', INPUT);
  process.exit(1);
}

// --- filler markers used to decide whether to run the pipeline at all ------
const FILLER_MARKERS = [
  /^(Okay|OK|Now|Next|Then|First|Second|Third|Alright|Finally|To start)[,!]/i,
  /^(After that|After this|Here's what you do|When you're ready|Once you've done that|Now for the fun part)/i,
  /\bgo ahead and\b/i,
  /\bmake sure to\b/i,
  /\bbe sure to\b/i,
  /\bsimply\b/i,
  /\bjust\b/i,
  /\bcarefully and gently\b/i,
  /\bslowly and carefully\b/i,
  /\b(you got this|trust the process|it's gonna be great)\b/i,
];

function hasFiller(s) {
  for (const re of FILLER_MARKERS) if (re.test(s)) return true;
  return false;
}

// --- rewrite rules ---------------------------------------------------------
// Rules run in order. Each rule is [pattern, replacement].
const RULES = [
  // 1. Leading conversational openers (only at the very start of the text).
  [/^(Okay|OK|Now|Next|Then|First|Second|Third|After that|After this|Finally|To start|Alright)[,!]\s+/i,
    ''],
  [/^(Here's what you do:|When you're ready,|Once you've done that,|Now for the fun part,)\s+/i,
    ''],

  // 2. Trailing wordy fillers ("— you got this" etc.).
  [/\s*[—–-]\s*you got this\.?\s*$/i,      ''],
  [/\s*[—–-]\s*trust the process\.?\s*$/i, ''],
  [/\s*[—–-]\s*it'?s gonna be great\.?\s*$/i, ''],

  // 3. Collapses (case-insensitive word-level). Preserve case of the
  //    first letter of the original phrase (so "Gently and carefully"
  //    stays capitalized, "gently and carefully" stays lowercase).
  [/\b([Cc])arefully and gently\b/g, (_, c) => (c === 'C' ? 'G' : 'g') + 'ently'],
  [/\b([Ss])lowly and carefully\b/g, (_, c) => (c === 'S' ? 'S' : 's') + 'lowly'],
  [/\b([Gg])ently and carefully\b/g, (_, c) => (c === 'G' ? 'G' : 'g') + 'ently'],

  // 4. Drop "make sure to" / "be sure to" (keeps the verb that follows).
  //    "make sure to not overcook" -> "not overcook"
  [/\b(?:please\s+)?make sure to\s+/gi, ''],
  [/\b(?:please\s+)?be sure to\s+/gi,   ''],
  [/\bgo ahead and\s+/gi,               ''],

  // 5. "simply" — drop it always (preserves following verb).
  //    "simply press them back" -> "press them back"
  [/\bsimply\s+/gi, ''],

  // 6. "just" — only when it leads a sentence or clause
  //    (start of string OR after ". ", "! ", "? ") to avoid mangling
  //    real uses like "just enough water".
  [/(^|[.!?]\s+)just\s+/g, '$1'],
  [/(^|[.!?]\s+)Just\s+/g, '$1'],

  // 7. Adjective stacks (CONSERVATIVE): only collapse stacks of 3+
  //    comma-separated adjective-like tokens. Keep only the last.
  //    Requirements:
  //      - All tokens are adjective-ish: end in one of
  //        -y, -ed, -ing, -en, -ous, -ful, -ish, -ic, -ive, -ant, -ent,
  //        -brown (color hyphen form), OR are a known color/texture word.
  //    Examples (collapse):
  //      "warm, bubbling, golden-brown sauce" -> "golden-brown sauce"
  //      "tender, juicy, perfectly-cooked chicken" -> "perfectly-cooked chicken"
  //    Non-examples (leave alone):
  //      "ready to cook, heat the grill" (cook/heat not adjective-ish)
  //      "Shape into a ball, and put"  (ball/and not adjective-ish)
];

// Curated flavor/texture/color adjectives. Only these count as stack-eligible.
// Keep this list TIGHT — every word here risks false positives.
const ADJ_WORDS = new Set([
  // color
  'golden', 'golden-brown', 'brown', 'deep-brown', 'pale', 'dark', 'light',
  'amber', 'caramel', 'white', 'red', 'green',
  // texture
  'crispy', 'crunchy', 'silky', 'velvety', 'creamy', 'tender', 'juicy',
  'fluffy', 'flaky', 'chewy', 'smooth', 'glossy', 'shiny', 'sticky',
  'thick', 'thin', 'soft', 'firm', 'springy', 'crackly', 'melty',
  // flavor
  'savory', 'savoury', 'spicy', 'sweet', 'salty', 'tangy', 'smoky', 'smokey',
  'zesty', 'fragrant', 'aromatic', 'nutty', 'buttery', 'rich',
  // temperature / state
  'warm', 'hot', 'piping-hot', 'bubbling', 'sizzling', 'steaming', 'simmering',
  'cold', 'chilled', 'icy', 'frozen', 'fresh', 'raw', 'dry', 'wet', 'moist',
  // visual
  'bright', 'vibrant', 'gorgeous', 'beautiful', 'perfect', 'perfectly-cooked',
  'lovely', 'delicious',
]);

function isAdj(w) { return ADJ_WORDS.has(w.toLowerCase()); }

function collapseAdjStacks(text) {
  // Find runs of the form: ADJ, ADJ, ADJ ... ADJ <noun>
  // where there are at least 3 adjectives (>=2 commas before the last).
  return text.replace(
    /\b((?:[a-z][a-z-]*,\s+){2,})([a-z][a-z-]*)(\s+[a-z][a-z-]*)/gi,
    (m, lead, lastAdj, tail) => {
      // Extract the comma-separated tokens from `lead`.
      const tokens = lead.split(/,\s*/).map(s => s.trim()).filter(Boolean);
      if (tokens.length < 2) return m;
      const allAdj = tokens.every(isAdj) && isAdj(lastAdj);
      if (!allAdj) return m;
      return lastAdj + tail;
    }
  );
}

// --- helpers ---------------------------------------------------------------
function normalizeSpaces(s) {
  return s.replace(/[ \t]+/g, ' ').replace(/ \n/g, '\n').trim();
}

function capitalizeFirst(s) {
  if (!s) return s;
  // find first letter and upper-case it (keeps quotes/numbers as prefix).
  const m = s.match(/[A-Za-z]/);
  if (!m) return s;
  const i = m.index;
  return s.slice(0, i) + s[i].toUpperCase() + s.slice(i + 1);
}

// After edits, ensure the letter after ". ", "! " or "? " is capitalized.
function capitalizeSentences(s) {
  return s.replace(/([.!?])(\s+)([a-z])/g, (_, p, ws, c) => p + ws + c.toUpperCase());
}

function rewrite(text) {
  let t = text;

  // Fast-skip: short & clean already.
  if (t.length < 120 && !hasFiller(t)) return null;

  for (const [pat, rep] of RULES) {
    t = t.replace(pat, rep);
  }

  t = collapseAdjStacks(t);

  t = normalizeSpaces(t);
  t = capitalizeFirst(t);
  t = capitalizeSentences(t);

  return t === text ? null : t;
}

// --- safety check: must preserve temperatures / times / measurements -------
const NUMERIC_UNITS = /(\d+(?:[.,]\d+)?)\s*(?:°\s*[CF]\b|°\b|C\/\d|C\b|F\b|min(?:s|ute|utes)?\b|hr(?:s|our|ours)?\b|sec(?:s|ond|onds)?\b|tsp\b|tbsp\b|cup(?:s)?\b|g\b|kg\b|ml\b|l\b|oz\b|lb(?:s)?\b|inch(?:es)?\b|cm\b)/gi;

function extractNumericTokens(s) {
  const out = [];
  let m;
  const re = new RegExp(NUMERIC_UNITS.source, 'gi');
  while ((m = re.exec(s)) !== null) out.push(m[0].toLowerCase().replace(/\s+/g, ''));
  return out;
}

function arraysEqual(a, b) {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
  return true;
}

function sqlEscape(s) {
  return s.replace(/'/g, "''");
}

// --- run pipeline ----------------------------------------------------------
let rewritten = 0;
let skippedTooShort = 0;
let skippedUnsafe = 0;
let unchanged = 0;

const samples = [];
const allChanges = []; // for worst-N selection at the end
const out = [];
out.push('-- Auto-generated by rewrite-steps.js. DO NOT HAND-EDIT.');
// NOTE: D1 disallows explicit BEGIN/COMMIT in uploaded SQL — each statement
// is its own transaction. Do not add transaction wrappers here.

for (const r of rows) {
  const original = r.text == null ? '' : String(r.text);
  const next = rewrite(original);
  if (next == null) { unchanged++; continue; }

  if (next.length < 10) { skippedTooShort++; continue; }

  // Safety: numeric tokens must survive exactly.
  const before = extractNumericTokens(original);
  const after  = extractNumericTokens(next);
  if (!arraysEqual(before, after)) { skippedUnsafe++; continue; }

  rewritten++;

  if (samples.length < 20) {
    samples.push({ recipe_id: r.recipe_id, seq: r.seq, before: original, after: next });
  }
  allChanges.push({
    recipe_id: r.recipe_id,
    seq: r.seq,
    before: original,
    after: next,
    drop: original.length - next.length,
  });

  out.push(
    `UPDATE recipe_step SET text='${sqlEscape(next)}' ` +
    `WHERE recipe_id='${sqlEscape(String(r.recipe_id))}' AND seq=${Number(r.seq) | 0};`
  );
}

fs.writeFileSync(OUTPUT, out.join('\n'), 'utf8');

// --- report ----------------------------------------------------------------
const pct = ((rewritten / rows.length) * 100).toFixed(2);
console.log('--- rewrite-steps.js report ---');
console.log('total steps       :', rows.length);
console.log('rewritten         :', rewritten, `(${pct}%)`);
console.log('unchanged         :', unchanged);
console.log('skipped too short :', skippedTooShort);
console.log('skipped unsafe    :', skippedUnsafe, '(numeric token drift)');
console.log('SQL written to    :', OUTPUT);
console.log('');
console.log('--- sample rewrites (first 5) ---');
for (const s of samples.slice(0, 5)) {
  console.log(`\n[${s.recipe_id}#${s.seq}]`);
  console.log('BEFORE:', s.before);
  console.log('AFTER :', s.after);
}

// Worst = biggest character-count drop (most aggressive rewrite).
allChanges.sort((a, b) => b.drop - a.drop);
console.log('\n--- worst (biggest drop) rewrites ---');
for (const s of allChanges.slice(0, 5)) {
  console.log(`\n[${s.recipe_id}#${s.seq}] drop=${s.drop} chars`);
  console.log('BEFORE:', s.before);
  console.log('AFTER :', s.after);
}

#!/usr/bin/env node
/**
 * repair_ingredients.cjs — fix ingest-corrupted recipe_ingredient rows in prod D1.
 *
 * Corruption classes (audit 2026-07-01, _AUDIT_2026-07-01.md §3):
 *   A. fused-fraction quantity: "1/2 tsp salt" stored as quantity=12 unit=tsp
 *   B. zero/null quantity with a unicode or ascii fraction still in the name ("¾ cup pineapple juice", qty 0)
 *   C. quantity echoed at the start of the name ("2 bay leaves", qty 2) — includes
 *      fused-fraction echoes ("14 cups pureed peaches", qty 2 → original "2 1/4 cups")
 *   D. unit abbreviation left in the name ("c. peanut butter", qty 1, unit null)
 *
 * Usage:
 *   node repair_ingredients.cjs fetch     # page suspect rows out of D1 into repair_rows.ndjson
 *   node repair_ingredients.cjs plan      # compute proposed fixes -> repair_plan.ndjson + repair_sample.txt
 *   node repair_ingredients.cjs sql       # emit chunked UPDATE .sql files + backup .sql
 *   node repair_ingredients.cjs apply     # backup table, then run chunks via wrangler (DESTRUCTIVE)
 *
 * Safety: `apply` first copies every to-be-changed row into recipe_ingredient_backup_20260701.
 */
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const DIR = path.join(__dirname, '_repair_20260701');
fs.mkdirSync(DIR, { recursive: true });
const ROWS = path.join(DIR, 'repair_rows.ndjson');
const PLAN = path.join(DIR, 'repair_plan.ndjson');

const DB = 'pantrie-db-staging';

// shell:true on Windows re-splits args; quote anything with spaces/parens ourselves.
function q(a) { return /[\s()&^]/.test(a) ? '"' + a + '"' : a; }
function run(args, opts) {
  return execFileSync('npx', args.map(q),
    Object.assign({ cwd: path.join(__dirname, '..'), encoding: 'utf8', maxBuffer: 256 * 1024 * 1024, shell: process.platform === 'win32' }, opts));
}

function d1(sql) {
  // --file mode returns a run summary, not rows; --command returns results.
  const out = run(['wrangler', 'd1', 'execute', DB, '--remote', '--json', '--command', sql]);
  const j = JSON.parse(out.slice(out.indexOf('[')));
  return j[0].results;
}

function d1File(file) {
  run(['wrangler', 'd1', 'execute', DB, '--remote', '--file', file, '-y'], { stdio: 'inherit' });
}

const SUSPECT_WHERE = `
  quantity = 0 OR quantity IS NULL
  OR name GLOB '[0-9]*'
  OR (quantity IN (12,13,14,18,23,34,38,58,78,112,114,134,212,214,234,312,334,412)
      AND LOWER(COALESCE(unit,'')) IN ('teaspoon','teaspoons','tsp','tsp.','tablespoon','tablespoons','tbsp','tbsp.','cup','cups','c','c.'))
  OR LOWER(name) GLOB 'c. *' OR LOWER(name) GLOB 'c *'
  OR LOWER(name) GLOB 'tsp*' OR LOWER(name) GLOB 'tbsp*'
  OR LOWER(name) GLOB 'pkg*' OR LOWER(name) GLOB 'oz*' OR LOWER(name) GLOB 'lb*'
  OR LOWER(name) GLOB 'qt*' OR LOWER(name) GLOB 'pt*' OR LOWER(name) GLOB 'gal *'
`.replace(/\s+/g, ' ');

// ---------------------------------------------------------------- fetch
function fetch() {
  fs.writeFileSync(ROWS, '');
  const PAGE = 4000;
  let off = 0, total = 0;
  for (;;) {
    const rows = d1(`SELECT rowid AS rid, recipe_id, seq, name, quantity, unit FROM recipe_ingredient WHERE ${SUSPECT_WHERE} ORDER BY rowid LIMIT ${PAGE} OFFSET ${off}`);
    if (!rows.length) break;
    fs.appendFileSync(ROWS, rows.map(r => JSON.stringify(r)).join('\n') + '\n');
    total += rows.length; off += PAGE;
    console.log(`fetched ${total}...`);
    if (rows.length < PAGE) break;
  }
  console.log(`DONE: ${total} suspect rows -> ${ROWS}`);
}

// ---------------------------------------------------------------- parsing
const UNI = { '¼': .25, '½': .5, '¾': .75, '⅐': 1/7, '⅑': 1/9, '⅒': .1, '⅓': 1/3, '⅔': 2/3, '⅕': .2, '⅖': .4, '⅗': .6, '⅘': .8, '⅙': 1/6, '⅚': 5/6, '⅛': .125, '⅜': .375, '⅝': .625, '⅞': .875 };
const FUSED = { 12: .5, 13: 1/3, 14: .25, 18: .125, 23: 2/3, 34: .75, 38: .375, 58: .625, 78: .875,
  112: 1.5, 114: 1.25, 134: 1.75, 212: 2.5, 214: 2.25, 234: 2.75, 312: 3.5, 334: 3.75, 412: 4.5 };
const UNIT_MAP = {
  'c': 'cup', 'c.': 'cup', 'cup': 'cup', 'cups': 'cup',
  'tsp': 'teaspoon', 'tsp.': 'teaspoon', 'teaspoon': 'teaspoon', 'teaspoons': 'teaspoon',
  'tbsp': 'tablespoon', 'tbsp.': 'tablespoon', 'tablespoon': 'tablespoon', 'tablespoons': 'tablespoon',
  'oz': 'oz', 'oz.': 'oz', 'ounce': 'oz', 'ounces': 'oz',
  'lb': 'lb', 'lb.': 'lb', 'lbs': 'lb', 'lbs.': 'lb', 'pound': 'lb', 'pounds': 'lb',
  'pkg': 'package', 'pkg.': 'package', 'pkgs': 'package', 'pkgs.': 'package', 'package': 'package', 'packages': 'package',
  'qt': 'quart', 'qt.': 'quart', 'quart': 'quart', 'quarts': 'quart',
  'pt': 'pint', 'pt.': 'pint', 'pint': 'pint', 'pints': 'pint',
  'gal': 'gallon', 'gal.': 'gallon', 'gallon': 'gallon', 'gallons': 'gallon',
  'g': 'g', 'kg': 'kg', 'ml': 'ml', 'l': 'l', 'stick': 'stick', 'sticks': 'stick',
  'can': 'can', 'cans': 'can', 'jar': 'jar', 'jars': 'jar', 'env': 'envelope', 'env.': 'envelope',
};
const SPOONCUP = new Set(['teaspoon','tablespoon','cup']);

function round2(n) { return Math.round(n * 100) / 100; }

/** Parse a full ingredient line ("1 1/2 c. firmly packed brown sugar") -> {quantity, unit, name} */
function parseLine(s) {
  s = String(s).trim().replace(/\s+/g, ' ');
  let qty = null;
  // unicode fraction with optional leading whole ("1½", "1 ½", "½")
  let m = s.match(/^(\d+)?\s*([¼½¾⅐-⅞])\s*/);
  if (m) { qty = (m[1] ? parseInt(m[1], 10) : 0) + UNI[m[2]]; s = s.slice(m[0].length); }
  else {
    // ascii fraction with optional whole ("1 1/2", "1/2")
    m = s.match(/^(\d+)?\s+?(\d+)\s*\/\s*(\d+)\s+/) || s.match(/^(\d+)?(\d+)\s*\/\s*(\d+)\s*/);
    if (m && parseInt(m[3], 10) > 0) { qty = (m[1] ? parseInt(m[1], 10) : 0) + parseInt(m[2], 10) / parseInt(m[3], 10); s = s.slice(m[0].length); }
    else {
      m = s.match(/^([\d.]+)\s+/);
      if (m && Number.isFinite(parseFloat(m[1]))) { qty = parseFloat(m[1]); s = s.slice(m[0].length); }
    }
  }
  // unit token
  let unit = null;
  m = s.match(/^([A-Za-z]+\.?)\s+/);
  if (m) {
    const u = UNIT_MAP[m[1].toLowerCase()];
    if (u) { unit = u; s = s.slice(m[0].length); }
  }
  s = s.replace(/^of\s+/i, '').trim();
  return { quantity: qty === null ? null : round2(qty), unit, name: s };
}

/** Decide the repair for one row. Returns null if no confident fix. */
function repairRow(r) {
  const name = String(r.name || '').trim();
  const qty = r.quantity;
  const unitLower = String(r.unit || '').toLowerCase();
  const unitCanon = UNIT_MAP[unitLower] || (unitLower || null);

  // Class A: fused-fraction quantity on spoon/cup units, clean name (no digits at start)
  if (qty !== null && FUSED[qty] !== undefined && SPOONCUP.has(unitCanon || '') && !/^\d/.test(name)) {
    return { quantity: round2(FUSED[qty]), unit: unitCanon, name, cls: 'A-fused' };
  }

  // Class B/C/D: the name itself starts with quantity/fraction/unit — reparse the whole line.
  // Reconstruct the "line" the parser should have seen. If DB qty echoes the name's leading
  // number we drop the DB qty; if DB qty is a whole number and the name starts with a fused
  // fraction token + unit, treat as whole+fraction.
  if (/^[¼½¾⅐-⅞]/.test(name) || /^\d/.test(name)) {
    // fused echo: qty=2, name="14 cups pureed peaches" -> "2 1/4 cups ..."
    const fm = name.match(/^(\d{2,3})\s+([A-Za-z]+\.?)\s+(.*)$/);
    if (qty !== null && Number.isInteger(qty) && fm && FUSED[fm[1]] !== undefined) {
      const u = UNIT_MAP[fm[2].toLowerCase()];
      if (u && SPOONCUP.has(u)) {
        // leading digit of fused token should equal DB qty for the echo pattern ("2" + "1/4"→"214"? no: "2 1/4"→qty 2, name "14 cups")
        return { quantity: round2(qty + FUSED[fm[1]]), unit: u, name: fm[3].trim(), cls: 'C-fused-echo' };
      }
    }
    const p = parseLine(name);
    // A name that is empty or just a bare unit token means the source row never
    // had an ingredient name — unrecoverable, leave it alone.
    if (p.name && UNIT_MAP[p.name.toLowerCase().replace(/\.$/, '')]) return null;
    if (p.name && p.name.length >= 2) {
      // if DB qty echoes parsed qty (or DB qty empty/0), use parsed
      if (qty === null || qty === 0 || (p.quantity !== null && Math.abs(p.quantity - qty) < .001) || p.quantity === null) {
        const q = p.quantity !== null ? p.quantity : (qty && qty !== 0 ? qty : null);
        return { quantity: q, unit: p.unit || unitCanon || null, name: p.name, cls: 'BC-reparse' };
      }
      // DB qty is real and differs: keep DB qty, still strip fraction/unit clutter from name
      return { quantity: qty, unit: p.unit || unitCanon || null, name: p.name, cls: 'BC-keepqty' };
    }
    return null;
  }

  // Class D: unit abbreviation stuck at the start of the name ("c. peanut butter")
  const dm = name.match(/^([A-Za-z]+\.?)\s+(.*)$/);
  if (dm) {
    const u = UNIT_MAP[dm[1].toLowerCase()];
    if (u && !r.unit && dm[2].length >= 2) {
      return { quantity: qty, unit: u, name: dm[2].trim(), cls: 'D-unit-in-name' };
    }
  }
  return null;
}

// ---------------------------------------------------------------- plan
function plan() {
  const lines = fs.readFileSync(ROWS, 'utf8').split('\n').filter(Boolean);
  const out = fs.createWriteStream(PLAN);
  const byClass = {}; let fixed = 0, skipped = 0;
  const samples = [];
  for (const ln of lines) {
    const r = JSON.parse(ln);
    const fix = repairRow(r);
    if (!fix || (fix.name === r.name && fix.quantity === r.quantity && (fix.unit || null) === (r.unit || null))) { skipped++; continue; }
    byClass[fix.cls] = (byClass[fix.cls] || 0) + 1; fixed++;
    out.write(JSON.stringify({ recipe_id: r.recipe_id, seq: r.seq, old: { n: r.name, q: r.quantity, u: r.unit }, new: { n: fix.name, q: fix.quantity, u: fix.unit }, cls: fix.cls }) + '\n');
    if (samples.length < 400 && Math.random() < 0.02) samples.push(`${fix.cls.padEnd(14)} | ${String(r.quantity)} ${r.unit || ''} "${r.name}"  ==>  ${fix.quantity} ${fix.unit || ''} "${fix.name}"`);
  }
  out.end();
  fs.writeFileSync(path.join(DIR, 'repair_sample.txt'), samples.join('\n'));
  console.log(`planned ${fixed} fixes, skipped ${skipped} (no confident fix / no change)`);
  console.log(byClass);
  console.log(`sample -> ${path.join(DIR, 'repair_sample.txt')}`);
}

// ---------------------------------------------------------------- sql
function esc(s) { return String(s).replace(/'/g, "''"); }
function sqlFiles() {
  const lines = fs.readFileSync(PLAN, 'utf8').split('\n').filter(Boolean);
  // backup only rows we will touch
  const ids = new Set(lines.map(l => { const p = JSON.parse(l); return `('${esc(p.recipe_id)}',${p.seq})`; }));
  const backup = [
    `CREATE TABLE IF NOT EXISTS recipe_ingredient_backup_20260701 AS SELECT * FROM recipe_ingredient WHERE 0;`,
  ];
  // backup via join table would be huge SQL; instead back up ALL suspect rows by the same WHERE
  backup.push(`INSERT INTO recipe_ingredient_backup_20260701 SELECT * FROM recipe_ingredient WHERE ${SUSPECT_WHERE};`);
  fs.writeFileSync(path.join(DIR, 'backup.sql'), backup.join('\n'));

  const CHUNK = 1500;
  let n = 0, file = 0, buf = [];
  const flush = () => {
    if (!buf.length) return;
    fs.writeFileSync(path.join(DIR, `update_${String(file).padStart(3, '0')}.sql`), buf.join('\n'));
    file++; buf = [];
  };
  for (const l of lines) {
    const p = JSON.parse(l);
    buf.push(`UPDATE recipe_ingredient SET name='${esc(p.new.n)}', quantity=${p.new.q === null ? 'NULL' : p.new.q}, unit=${p.new.u ? `'${esc(p.new.u)}'` : 'NULL'} WHERE recipe_id='${esc(p.recipe_id)}' AND seq=${p.seq};`);
    if (buf.length >= CHUNK) flush();
    n++;
  }
  flush();
  console.log(`${n} updates across ${file} files + backup.sql in ${DIR}`);
}

// ---------------------------------------------------------------- apply
function apply() {
  console.log('backing up suspect rows...');
  d1File(path.join(DIR, 'backup.sql'));
  const files = fs.readdirSync(DIR).filter(f => /^update_\d+\.sql$/.test(f)).sort();
  for (const f of files) {
    console.log(`applying ${f}...`);
    d1File(path.join(DIR, f));
  }
  console.log('done. run post-repair sanity queries.');
}

const cmd = process.argv[2];
({ fetch, plan, sql: sqlFiles, apply }[cmd] || (() => console.log('usage: fetch | plan | sql | apply')))();

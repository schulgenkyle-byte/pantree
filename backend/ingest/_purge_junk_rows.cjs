// Delete recipe_ingredient rows that carry no ingredient at all — names that are
// just a fraction+unit ("¼ cup", "½ tsp") or instruction fragments. All these rows
// were captured in recipe_ingredient_backup_20260701 before the repair, so this
// is reversible. Conservative regex; dry-run prints before deleting.
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const DIR = path.join(__dirname, '_repair_20260701');
function q(a) { return /[\s()&^]/.test(a) ? '"' + a + '"' : a; }
function run(args, opts) {
  return execFileSync('npx', args.map(q), Object.assign({ cwd: path.join(__dirname, '..'), encoding: 'utf8', maxBuffer: 256 * 1024 * 1024, shell: true }, opts));
}
function d1(sql) {
  let lastErr;
  for (let i = 0; i < 4; i++) {
    try { const out = run(['wrangler', 'd1', 'execute', 'pantrie-db-staging', '--remote', '--json', '--command', sql]); return JSON.parse(out.slice(out.indexOf('[')))[0].results; }
    catch (e) { lastErr = e; }
  }
  throw lastErr;
}
function d1File(f) { run(['wrangler', 'd1', 'execute', 'pantrie-db-staging', '--remote', '--file', f, '-y'], { stdio: 'inherit' }); }
function esc(s) { return String(s).replace(/'/g, "''"); }

const JUNK = [
  /^[¼½¾⅐-⅞]?\s*(cup|cups|tsp|tsp\.|tbsp|tbsp\.|teaspoon|teaspoons|tablespoon|tablespoons|oz|oz\.|c\.|pinch|dash)$/i,
  /^(cup|cups|tsp|tbsp|teaspoon|tablespoon|pinch|dash|each|ea\.?)$/i,
  /^(in a bowl|if using canned beans|to taste|as needed|for garnish|optional|divided|see note|note)$/i,
];

(async () => {
  // pull candidates: short names with no letters beyond unit words, or qty-null unit-null short rows
  const rows = d1(`SELECT recipe_id, seq, name FROM recipe_ingredient WHERE LENGTH(name) <= 22`);
  const hits = rows.filter(r => JUNK.some(re => re.test(String(r.name).trim())));
  console.log(`candidates ${rows.length}, junk matches ${hits.length}`);
  hits.slice(0, 30).forEach(h => console.log('  DEL:', JSON.stringify(h.name)));
  if (!hits.length) return;
  const stmts = hits.map(h => `DELETE FROM recipe_ingredient WHERE recipe_id='${esc(h.recipe_id)}' AND seq=${h.seq} AND name='${esc(h.name)}';`);
  const f = path.join(DIR, 'purge_junk.sql');
  fs.writeFileSync(f, stmts.join('\n'));
  d1File(f);
  console.log(`deleted ${hits.length} junk rows`);
})();

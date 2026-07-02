// Recompute canonical_name for every row touched by repair_ingredients.cjs.
// The repair fixed name/quantity/unit but canonical_name still derived from the
// corrupted pre-repair name — which is what the deck match % keys on.
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const DIR = path.join(__dirname, '_repair_20260701');
const PLAN = path.join(DIR, 'repair_plan.ndjson');

function q(a) { return /[\s()&^]/.test(a) ? '"' + a + '"' : a; }
function d1File(file) {
  execFileSync('npx', ['wrangler', 'd1', 'execute', 'pantrie-db-staging', '--remote', '--file', file, '-y'].map(q),
    { cwd: path.join(__dirname, '..'), encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, shell: true, stdio: 'inherit' });
}
function esc(s) { return String(s).replace(/'/g, "''"); }

(async () => {
  const { canonicalize } = await import('../src/canonicalize.js');
  const lines = fs.readFileSync(PLAN, 'utf8').split('\n').filter(Boolean);
  const CHUNK = 2000;
  let buf = [], file = 0, n = 0;
  const flush = () => {
    if (!buf.length) return;
    const p = path.join(DIR, `recanon_${String(file).padStart(3, '0')}.sql`);
    fs.writeFileSync(p, buf.join('\n'));
    file++; buf = [];
  };
  for (const l of lines) {
    const r = JSON.parse(l);
    const canon = canonicalize(r.new.n);
    if (!canon) continue;
    buf.push(`UPDATE recipe_ingredient SET canonical_name='${esc(canon)}' WHERE recipe_id='${esc(r.recipe_id)}' AND seq=${r.seq};`);
    n++;
    if (buf.length >= CHUNK) flush();
  }
  flush();
  console.log(`${n} recanon updates across ${file} files`);
  for (let i = 0; i < file; i++) {
    const p = path.join(DIR, `recanon_${String(i).padStart(3, '0')}.sql`);
    console.log('applying', path.basename(p));
    d1File(p);
  }
  console.log('recanonicalize done');
})();

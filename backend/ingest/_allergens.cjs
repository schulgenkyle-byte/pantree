// Backfill recipe.allergen_warnings for the whole catalog, derived from
// ingredient names. Deliberately over-warns (a "may contain" posture): tagging
// almond-flour cake as tree-nut is correct; missing it is dangerous.
// Coverage before this run: 7 of 27,242 recipes.
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const DIR = path.join(__dirname, '_repair_20260701');
fs.mkdirSync(DIR, { recursive: true });

function q(a) { return /[\s()&^]/.test(a) ? '"' + a + '"' : a; }
function run(args, opts) {
  return execFileSync('npx', args.map(q),
    Object.assign({ cwd: path.join(__dirname, '..'), encoding: 'utf8', maxBuffer: 256 * 1024 * 1024, shell: true }, opts));
}
function d1(sql) {
  const out = run(['wrangler', 'd1', 'execute', 'pantrie-db-staging', '--remote', '--json', '--command', sql]);
  return JSON.parse(out.slice(out.indexOf('[')))[0].results;
}
function d1File(file) {
  run(['wrangler', 'd1', 'execute', 'pantrie-db-staging', '--remote', '--file', file, '-y'], { stdio: 'inherit' });
}
function esc(s) { return String(s).replace(/'/g, "''"); }

// Big-9 allergen classes → LIKE terms (matched against lower(name)).
const CLASSES = {
  dairy:     ['milk', 'butter', 'cream', 'cheese', 'yogurt', 'yoghurt', 'ghee', 'mozzarella', 'parmesan', 'cheddar', 'ricotta', 'feta', 'brie', 'mascarpone', 'custard', 'whey', 'ice cream', 'gelato', 'queso', 'paneer', 'halloumi', 'gouda', 'gruyere'],
  egg:       ['egg', 'mayonnaise', 'mayo', 'meringue', 'aioli', 'hollandaise'],
  fish:      ['salmon', 'tuna', 'cod ', ' cod', 'anchov', 'tilapia', 'halibut', 'trout', 'sardine', 'mackerel', 'snapper', 'fish sauce', 'fish stock', 'fish fillet', 'worcestershire', 'haddock', 'catfish', 'swordfish', 'mahi'],
  shellfish: ['shrimp', 'prawn', 'crab', 'lobster', 'scallop', 'clam', 'mussel', 'oyster', 'crawfish', 'crayfish', 'langoustine', 'calamari', 'squid', 'octopus'],
  tree_nut:  ['almond', 'walnut', 'pecan', 'cashew', 'pistachio', 'hazelnut', 'macadamia', 'brazil nut', 'pine nut', 'praline', 'marzipan', 'nutella', 'chestnut', 'coconut'],
  peanut:    ['peanut'],
  wheat:     ['flour', 'wheat', 'bread', 'pasta', 'noodle', 'spaghetti', 'macaroni', 'cracker', 'couscous', 'barley', 'rye', 'semolina', 'panko', 'breadcrumb', 'tortilla', 'pita', 'phyllo', 'filo', 'puff pastry', 'pie crust', 'biscuit', 'cake mix', 'beer', 'seitan', 'orzo', 'farro', 'bulgur'],
  soy:       ['soy', 'tofu', 'edamame', 'tempeh', 'miso', 'tamari'],
  sesame:    ['sesame', 'tahini'],
};

(async () => {
  // 1) recipe_id sets per class
  const byRecipe = new Map();
  for (const [cls, terms] of Object.entries(CLASSES)) {
    const where = terms.map(t => `lower(name) LIKE '%${esc(t)}%'`).join(' OR ');
    const rows = d1(`SELECT DISTINCT recipe_id FROM recipe_ingredient WHERE ${where}`);
    for (const r of rows) {
      if (!byRecipe.has(r.recipe_id)) byRecipe.set(r.recipe_id, []);
      byRecipe.get(r.recipe_id).push(cls);
    }
    console.log(cls, '->', rows.length, 'recipes');
  }

  // 2) chunked UPDATEs (only overwrite empty/[] values — never clobber curated data)
  const CHUNK = 2000;
  let buf = [], file = 0, n = 0;
  const flush = () => {
    if (!buf.length) return;
    fs.writeFileSync(path.join(DIR, `allergen_${String(file).padStart(3, '0')}.sql`), buf.join('\n'));
    file++; buf = [];
  };
  for (const [rid, classes] of byRecipe) {
    const jsonArr = JSON.stringify([...new Set(classes)]);
    buf.push(`UPDATE recipe SET allergen_warnings='${esc(jsonArr)}' WHERE id='${esc(rid)}' AND (allergen_warnings IS NULL OR allergen_warnings='' OR allergen_warnings='[]');`);
    n++;
    if (buf.length >= CHUNK) flush();
  }
  flush();
  console.log(`${n} recipes get allergen warnings, ${file} files`);
  for (let i = 0; i < file; i++) {
    console.log('applying', i);
    d1File(path.join(DIR, `allergen_${String(i).padStart(3, '0')}.sql`));
  }
  console.log('allergen backfill done');
})();

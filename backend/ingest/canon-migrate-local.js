// Local-only canonicalization migration. Reads rows via wrangler, canonicalizes in Node,
// emits UPDATE SQL to canon-migrate.sql which we feed back to wrangler d1 execute --file.
// Bypasses Worker CPU limits entirely.

import { execSync } from 'node:child_process';
import { writeFileSync, mkdirSync } from 'node:fs';
import { canonicalize } from '../src/canonicalize.js';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const OUTDIR = fileURLToPath(new URL('./raw/', import.meta.url));
mkdirSync(OUTDIR, { recursive: true });
const OUT = join(OUTDIR, 'canon-migrate.sql');

function sqlEscape(s) {
  return String(s ?? '').replace(/'/g, "''");
}

function queryJson(sql) {
  const cmd = `npx wrangler d1 execute pantrie-db-staging --remote --json --command "${sql.replace(/"/g, '\\"')}"`;
  const out = execSync(cmd, { cwd: fileURLToPath(new URL('../', import.meta.url)), maxBuffer: 500_000_000 }).toString();
  const data = JSON.parse(out);
  // wrangler json output: array with single object { results: [...] }
  return data?.[0]?.results || [];
}

async function main() {
  console.log('Reading recipe_ingredient rows...');
  const recIngs = queryJson('SELECT recipe_id, seq, name FROM recipe_ingredient WHERE canonical_name IS NULL');
  console.log(`  ${recIngs.length} rows to canonicalize`);

  console.log('Reading pantry_item rows...');
  const pantry = queryJson('SELECT id, name FROM pantry_item WHERE canonical_name IS NULL');
  console.log(`  ${pantry.length} rows to canonicalize`);

  const lines = [];
  for (const r of recIngs) {
    const canon = canonicalize(r.name);
    lines.push(`UPDATE recipe_ingredient SET canonical_name = '${sqlEscape(canon)}' WHERE recipe_id = '${sqlEscape(r.recipe_id)}' AND seq = ${Number(r.seq)};`);
  }
  for (const p of pantry) {
    const canon = canonicalize(p.name);
    lines.push(`UPDATE pantry_item SET canonical_name = '${sqlEscape(canon)}' WHERE id = '${sqlEscape(p.id)}';`);
  }

  writeFileSync(OUT, lines.join('\n'), 'utf8');
  console.log(`\n✓ wrote ${lines.length} UPDATEs to ${OUT}`);
  console.log('\nNext: npx wrangler d1 execute pantrie-db-staging --file=ingest/raw/canon-migrate.sql --remote');
}

main().catch(e => { console.error(e); process.exit(1); });

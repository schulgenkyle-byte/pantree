// Pull recipes from a Hugging Face dataset via the datasets-server HTTP API.
// Default dataset: RecipeNLG (2.2M MIT-licensed recipes).
//
// Usage:
//   node ingest/fetch-huggingface.js                       # default: 100k from mbien/recipe_nlg
//   HF_DATASET=corbt/all-recipes HF_LIMIT=50000 node ...
//   HF_LIMIT=0 node ...                                    # 0 = fetch all rows
//
// Env:
//   HF_DATASET   default: mbien/recipe_nlg
//   HF_CONFIG    default: default
//   HF_SPLIT     default: train
//   HF_LIMIT     default: 100000 (set 0 for unlimited)
//   HF_TOKEN     optional — for gated/private datasets

import { writeFileSync, mkdirSync, existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const DATASET = process.env.HF_DATASET || 'corbt/all-recipes';
const CONFIG = process.env.HF_CONFIG || 'default';
const SPLIT = process.env.HF_SPLIT || 'train';
const LIMIT = parseInt(process.env.HF_LIMIT || '100000', 10);
const TOKEN = process.env.HF_TOKEN || '';
const PAGE = 100; // max rows per datasets-server call

const OUTDIR = fileURLToPath(new URL('./raw/', import.meta.url));
mkdirSync(OUTDIR, { recursive: true });
const OUTFILE = join(OUTDIR, 'hf-raw.json');
const CHECKPOINT = join(OUTDIR, 'hf-checkpoint.json');

function headers() {
  const h = { 'Accept': 'application/json', 'User-Agent': 'Pantrie-Ingest/0.1' };
  if (TOKEN) h['Authorization'] = `Bearer ${TOKEN}`;
  return h;
}

async function fetchPage(offset) {
  const url = `https://datasets-server.huggingface.co/rows?dataset=${encodeURIComponent(DATASET)}&config=${encodeURIComponent(CONFIG)}&split=${encodeURIComponent(SPLIT)}&offset=${offset}&length=${PAGE}`;
  const res = await fetch(url, { headers: headers() });
  if (!res.ok) {
    const t = await res.text();
    throw new Error(`${res.status} ${t.slice(0, 200)}`);
  }
  return res.json();
}

async function totalRows() {
  // HF datasets-server exposes num_rows_total when fetching offset=0
  const first = await fetchPage(0);
  return first?.num_rows_total || first?.rows?.length || 0;
}

async function main() {
  console.log(`Dataset: ${DATASET} (split=${SPLIT}, config=${CONFIG})`);
  const total = await totalRows();
  if (!total) { console.error('Dataset returned 0 rows — check HF_DATASET name'); process.exit(1); }
  const target = LIMIT === 0 ? total : Math.min(total, LIMIT);
  console.log(`Total available: ${total}. Target this run: ${target}.`);

  let rows = [];
  let startOffset = 0;
  if (existsSync(CHECKPOINT)) {
    try {
      const ck = JSON.parse(readFileSync(CHECKPOINT, 'utf8'));
      if (ck.dataset === DATASET && ck.rows) {
        rows = ck.rows;
        startOffset = rows.length;
        console.log(`Resuming from offset ${startOffset}`);
      }
    } catch {}
  }

  const t0 = Date.now();
  let errs = 0;
  for (let offset = startOffset; offset < target; offset += PAGE) {
    try {
      const data = await fetchPage(offset);
      const batch = (data.rows || []).map(r => r.row);
      rows.push(...batch);
      if (batch.length === 0) break;
      if (offset % 1000 === 0 || offset + PAGE >= target) {
        const pct = ((rows.length / target) * 100).toFixed(1);
        const eta = rows.length > 0 ? ((Date.now() - t0) / rows.length * (target - rows.length) / 1000).toFixed(0) : '?';
        process.stdout.write(`\r  ${rows.length}/${target} (${pct}%)  eta ${eta}s  errs ${errs}  `);
      }
      // Checkpoint every 5000 rows
      if (offset % 5000 === 0 && offset > startOffset) {
        writeFileSync(CHECKPOINT, JSON.stringify({ dataset: DATASET, rows }), 'utf8');
      }
    } catch (e) {
      errs++;
      process.stdout.write(`\n  error at ${offset}: ${e.message}\n`);
      // Rate-limit friendly backoff
      await new Promise(r => setTimeout(r, Math.min(30_000, 2000 * errs)));
      if (errs > 10) { console.error('Too many errors, aborting'); break; }
    }
    // Courtesy pause
    await new Promise(r => setTimeout(r, 50));
  }
  process.stdout.write('\n');

  writeFileSync(OUTFILE, JSON.stringify(rows), 'utf8');
  writeFileSync(CHECKPOINT, JSON.stringify({ dataset: DATASET, rows }), 'utf8');
  console.log(`✓ wrote ${rows.length} raw rows -> ${OUTFILE}`);
}

main().catch(e => { console.error(e); process.exit(1); });

// Download + parse HuggingFace parquet files directly. No datasets-server rate limits.
// Default dataset: corbt/all-recipes (2.15M recipes). Total ~800MB compressed parquet.
//
// Usage: HF_LIMIT=100000 node ingest/fetch-huggingface-parquet.js
//        HF_LIMIT=0 node ingest/fetch-huggingface-parquet.js   # all 2.15M
//
// Env:
//   HF_DATASET       default: corbt/all-recipes
//   HF_CONFIG        default: default
//   HF_SPLIT         default: train
//   HF_LIMIT         default: 100000 (0 = all)

import { createWriteStream, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';
import { parquetReadObjects } from 'hyparquet';

const DATASET = process.env.HF_DATASET || 'corbt/all-recipes';
const CONFIG = process.env.HF_CONFIG || 'default';
const SPLIT = process.env.HF_SPLIT || 'train';
const LIMIT = parseInt(process.env.HF_LIMIT || '100000', 10);

const OUTDIR = fileURLToPath(new URL('./raw/', import.meta.url));
mkdirSync(OUTDIR, { recursive: true });
const OUTFILE = join(OUTDIR, 'hf-raw.ndjson');

async function getParquetUrls() {
  const url = `https://datasets-server.huggingface.co/parquet?dataset=${encodeURIComponent(DATASET)}&config=${CONFIG}&split=${SPLIT}`;
  const res = await fetch(url);
  if (!res.ok) throw new Error(`parquet index ${res.status}`);
  const data = await res.json();
  return (data.parquet_files || []).map(f => f.url);
}

async function downloadFile(url, idx, total) {
  process.stdout.write(`  [${idx + 1}/${total}] downloading ${url.split('/').pop()}... `);
  const res = await fetch(url);
  if (!res.ok) throw new Error(`${res.status} ${url}`);
  const buf = Buffer.from(await res.arrayBuffer());
  process.stdout.write(`${(buf.length / 1024 / 1024).toFixed(1)} MB\n`);
  return buf;
}

async function main() {
  console.log(`Dataset: ${DATASET} (split=${SPLIT})`);
  const urls = await getParquetUrls();
  if (urls.length === 0) { console.error('No parquet files found'); process.exit(1); }
  console.log(`Found ${urls.length} parquet files.`);

  const target = LIMIT === 0 ? Infinity : LIMIT;
  const stream = createWriteStream(OUTFILE, { encoding: 'utf8' });
  let totalWritten = 0;

  for (let i = 0; i < urls.length; i++) {
    if (totalWritten >= target) break;
    const buf = await downloadFile(urls[i], i, urls.length);
    const arrayBuffer = buf.buffer.slice(buf.byteOffset, buf.byteOffset + buf.byteLength);

    process.stdout.write(`    parsing... `);
    const rows = await parquetReadObjects({ file: arrayBuffer });
    process.stdout.write(`${rows.length} rows. writing... `);

    for (const r of rows) {
      if (totalWritten >= target) break;
      stream.write(JSON.stringify(r) + '\n');
      totalWritten++;
    }
    process.stdout.write(`running total: ${totalWritten}\n`);
  }

  await new Promise(r => stream.end(r));
  console.log(`✓ wrote ${totalWritten} rows to ${OUTFILE}`);
}

main().catch(e => { console.error(e); process.exit(1); });

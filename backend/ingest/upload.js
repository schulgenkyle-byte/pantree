// Upload normalized recipe file(s) to the Worker's /recipes/seed endpoint.
// Supports both JSON-array and NDJSON inputs, streams NDJSON for large files.
// Batches 50 recipes per request by default; retries on transient failure.
//
// Usage:
//   BASE=https://pantrie-backend.<sub>.workers.dev SEED_KEY=<hex> \
//     node ingest/upload.js ingest/normalized/themealdb-normalized.json
//
//   Multiple files in one run:
//     node ingest/upload.js ingest/normalized/*.json ingest/normalized/hf-normalized.ndjson
//
//   Limit HF to first N recipes:
//     UPLOAD_LIMIT=10000 node ingest/upload.js ingest/normalized/hf-normalized.ndjson
//
// Env:
//   BASE            Worker URL
//   SEED_KEY        x-seed-key header value
//   BATCH_SIZE      default 50
//   CONCURRENCY     default 3
//   UPLOAD_LIMIT    default 0 (unlimited); caps total recipes uploaded per-file
//   START_AT        default 0; skip first N lines (for resuming NDJSON)

import { createReadStream, readFileSync, existsSync, statSync } from 'node:fs';
import { createInterface } from 'node:readline';
import { argv, exit, env } from 'node:process';

const BASE = env.BASE;
const SEED_KEY = env.SEED_KEY;
const BATCH_SIZE = parseInt(env.BATCH_SIZE || '50', 10);
const CONCURRENCY = parseInt(env.CONCURRENCY || '3', 10);
const LIMIT = parseInt(env.UPLOAD_LIMIT || '0', 10);
const START_AT = parseInt(env.START_AT || '0', 10);

if (!BASE || !SEED_KEY) { console.error('BASE and SEED_KEY env vars required'); exit(2); }

const files = argv.slice(2).filter(a => !a.startsWith('--'));
if (files.length === 0) { console.error('Pass at least one normalized JSON/NDJSON file'); exit(2); }

async function postBatch(batch, attempt = 1) {
  const res = await fetch(`${BASE}/recipes/seed`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'x-seed-key': SEED_KEY },
    body: JSON.stringify({ recipes: batch }),
  });
  if (res.status === 429 && attempt < 5) {
    const wait = attempt * 5000;
    await new Promise(r => setTimeout(r, wait));
    return postBatch(batch, attempt + 1);
  }
  if (res.status >= 500 && attempt < 3) {
    await new Promise(r => setTimeout(r, 2000 * attempt));
    return postBatch(batch, attempt + 1);
  }
  if (!res.ok) {
    const t = await res.text();
    throw new Error(`${res.status} ${t.slice(0, 200)}`);
  }
  return res.json();
}

/** Common batch dispatcher. */
class Dispatcher {
  constructor(concurrency) {
    this.concurrency = concurrency;
    this.inFlight = new Set();
    this.upserted = 0;
    this.errors = 0;
    this.batches = 0;
    this.t0 = Date.now();
  }
  async submit(batch) {
    while (this.inFlight.size >= this.concurrency) await Promise.race(this.inFlight);
    const p = postBatch(batch).then(r => {
      this.upserted += r.upserted || 0;
      if (r.errors?.length) this.errors += r.errors.length;
    }).catch(e => {
      this.errors += batch.length;
      process.stderr.write(`\n  batch failed: ${e.message}\n`);
    }).finally(() => this.inFlight.delete(p));
    this.inFlight.add(p);
    this.batches++;
  }
  async drain() { await Promise.all(this.inFlight); }
  progress(total) {
    const elapsed = (Date.now() - this.t0) / 1000;
    const rate = this.upserted / Math.max(elapsed, 1);
    return { elapsed, rate };
  }
}

async function uploadJsonArray(file, disp) {
  const recipes = JSON.parse(readFileSync(file, 'utf8'));
  if (!Array.isArray(recipes) || recipes.length === 0) return;
  const effective = LIMIT > 0 ? Math.min(LIMIT, recipes.length) : recipes.length;
  console.log(`  ${file}: ${effective}/${recipes.length} recipes in ${Math.ceil(effective / BATCH_SIZE)} batches`);
  let pushed = 0;
  for (let i = 0; i < effective; i += BATCH_SIZE) {
    const batch = recipes.slice(i, Math.min(i + BATCH_SIZE, effective));
    await disp.submit(batch);
    pushed += batch.length;
    if (disp.batches % 5 === 0) {
      const { rate } = disp.progress();
      process.stdout.write(`\r    pushed ${pushed}/${effective}  upserted ${disp.upserted}  errs ${disp.errors}  rate ${rate.toFixed(0)}/s    `);
    }
  }
  await disp.drain();
  process.stdout.write('\n');
}

async function uploadNdjson(file, disp) {
  const totalBytes = statSync(file).size;
  console.log(`  ${file}: streaming (${(totalBytes / 1024 / 1024).toFixed(0)} MB)`);
  const rl = createInterface({ input: createReadStream(file, { encoding: 'utf8' }), crlfDelay: Infinity });
  let batch = [];
  let read = 0, pushed = 0;
  for await (const line of rl) {
    if (!line.trim()) continue;
    read++;
    if (read <= START_AT) continue;
    let r; try { r = JSON.parse(line); } catch { continue; }
    batch.push(r);
    if (batch.length >= BATCH_SIZE) {
      await disp.submit(batch);
      pushed += batch.length;
      batch = [];
      if (disp.batches % 5 === 0) {
        const { rate } = disp.progress();
        process.stdout.write(`\r    pushed ${pushed}  upserted ${disp.upserted}  errs ${disp.errors}  rate ${rate.toFixed(0)}/s    `);
      }
      if (LIMIT > 0 && pushed >= LIMIT) break;
    }
  }
  if (batch.length) { await disp.submit(batch); pushed += batch.length; }
  await disp.drain();
  process.stdout.write('\n');
}

async function main() {
  console.log(`Uploading to ${BASE}`);
  for (const f of files) {
    if (!existsSync(f)) { console.error(`  file missing: ${f}`); continue; }
    const disp = new Dispatcher(CONCURRENCY);
    if (f.endsWith('.ndjson')) await uploadNdjson(f, disp);
    else await uploadJsonArray(f, disp);
    console.log(`  ${f} → upserted ${disp.upserted}  errors ${disp.errors}  (${((Date.now() - disp.t0) / 1000).toFixed(0)}s)`);
  }
  console.log(`\n✓ DONE.`);
}

main().catch(e => { console.error(e); exit(1); });

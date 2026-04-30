#!/usr/bin/env node
/**
 * Multi-key parallel orchestrator for Imagen gen scripts.
 *
 * Reads N API keys from a file, spawns N child processes of a target gen script,
 * each with its own GEMINI_API_KEY env var + a partitioned range of work
 * (--start --end indices into the script's TARGETS array). Streams stdout/stderr
 * from each worker prefixed with [W1] / [W2] / ... so logs interleave clearly.
 *
 * Usage:
 *   node parallel_run.cjs --keys=C:/Users/12566/Downloads/GEMINI_KEYS.txt \
 *                         --script=backend/ingest/generate_brimm_images.cjs \
 *                         --total=124 \
 *                         --workers=5
 *
 *   --keys      file with one Gemini API key per line (gitignored). Required.
 *   --script    path to gen script (relative to repo root or absolute). Required.
 *   --total     total number of work items in the script's TARGETS array. Required.
 *   --workers   number of parallel workers. Defaults to number of keys in file.
 *               Capped at the number of keys (cannot exceed key count).
 *
 * Each worker runs:  GEMINI_API_KEY=keyN node <script> --start=A --end=B
 * Workers split the [0, total) range as evenly as possible.
 */
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const argv = Object.fromEntries(
  process.argv.slice(2).map(a => {
    const m = a.match(/^--([^=]+)=(.*)$/);
    return m ? [m[1], m[2]] : [a.replace(/^--/, ''), true];
  })
);

const KEYS_FILE = argv.keys;
const SCRIPT = argv.script;
const TOTAL = parseInt(argv.total, 10);
let WORKERS = argv.workers ? parseInt(argv.workers, 10) : null;

if (!KEYS_FILE || !SCRIPT || !TOTAL) {
  console.error('Usage: node parallel_run.cjs --keys=FILE --script=PATH --total=N [--workers=N]');
  process.exit(2);
}
if (!fs.existsSync(KEYS_FILE)) {
  console.error(`FATAL: keys file not found: ${KEYS_FILE}`);
  process.exit(2);
}
if (!fs.existsSync(SCRIPT)) {
  console.error(`FATAL: script not found: ${SCRIPT}`);
  process.exit(2);
}

// One key per line, ignore blanks + comment lines starting with #
const keys = fs.readFileSync(KEYS_FILE, 'utf8')
  .split(/\r?\n/)
  .map(s => s.trim())
  .filter(s => s && !s.startsWith('#'));

if (keys.length === 0) {
  console.error(`FATAL: no usable keys in ${KEYS_FILE}`);
  process.exit(2);
}

WORKERS = Math.min(WORKERS || keys.length, keys.length);
const chunkSize = Math.ceil(TOTAL / WORKERS);

console.log(`Parallel orchestrator`);
console.log(`  script:   ${SCRIPT}`);
console.log(`  keys:     ${keys.length} loaded from ${KEYS_FILE}`);
console.log(`  workers:  ${WORKERS} (each handles ~${chunkSize} of ${TOTAL} items)`);
console.log('');

const workerColors = ['\x1b[36m', '\x1b[33m', '\x1b[32m', '\x1b[35m', '\x1b[34m', '\x1b[31m'];
const RESET = '\x1b[0m';

const children = [];
const t0 = Date.now();

for (let w = 0; w < WORKERS; w++) {
  const start = w * chunkSize;
  const end = Math.min((w + 1) * chunkSize, TOTAL);
  if (start >= end) continue;

  const tag = `[W${w + 1}]`;
  const color = workerColors[w % workerColors.length];

  const child = spawn('node', [SCRIPT, `--start=${start}`, `--end=${end}`], {
    env: { ...process.env, GEMINI_API_KEY: keys[w] },
    stdio: ['ignore', 'pipe', 'pipe'],
  });

  console.log(`${color}${tag}${RESET} spawned (range [${start}, ${end}), key index ${w})`);

  const prefixLine = (line) => `${color}${tag}${RESET} ${line}`;

  child.stdout.on('data', chunk => {
    chunk.toString().split(/\r?\n/).filter(Boolean).forEach(line => process.stdout.write(prefixLine(line) + '\n'));
  });
  child.stderr.on('data', chunk => {
    chunk.toString().split(/\r?\n/).filter(Boolean).forEach(line => process.stderr.write(prefixLine(line) + '\n'));
  });
  child.on('exit', code => {
    console.log(`${color}${tag}${RESET} exited code=${code}`);
  });

  children.push({ tag, child, start, end });
}

// Wait for all children to exit.
const waitAll = Promise.all(children.map(c => new Promise(resolve => c.child.on('exit', resolve))));
waitAll.then(() => {
  const elapsed = ((Date.now() - t0) / 1000).toFixed(1);
  console.log(`\nAll ${WORKERS} workers finished in ${elapsed}s`);
});

// Forward SIGINT to children so Ctrl-C cleans up.
process.on('SIGINT', () => {
  console.log('\nReceived SIGINT, killing all workers...');
  children.forEach(c => c.child.kill('SIGINT'));
  setTimeout(() => process.exit(130), 1000);
});

#!/usr/bin/env node
// Quick status check for the historical archive crawler. Run any time.
//   node status-historical.cjs

const fs = require('fs');
const path = require('path');

const HERE = __dirname;
const sources = ['archive_org', 'gutenberg', 'euvs', 'wikisource', 'loc', 'hathitrust', 'trove'];

console.log('=== Historical Archive Crawler Status ===');
console.log(new Date().toString());
console.log('');

let totalRecipes = 0;
let totalDone = 0;
let totalFailed = 0;

for (const s of sources) {
  const log = path.join(HERE, `_logs_${s}.txt`);
  const ndjson = path.join(HERE, `historical-${s}.ndjson`);
  const progress = path.join(HERE, '_progress', `${s}.json`);

  let recipes = 0, done = 0, failed = 0, lastLine = '(no log)';
  if (fs.existsSync(ndjson)) {
    recipes = fs.readFileSync(ndjson, 'utf8').split('\n').filter(l => l.trim()).length;
  }
  if (fs.existsSync(progress)) {
    try {
      const p = JSON.parse(fs.readFileSync(progress, 'utf8'));
      done = (p.done || []).length;
      failed = (p.failed || []).length;
    } catch (_) {}
  }
  if (fs.existsSync(log)) {
    const text = fs.readFileSync(log, 'utf8');
    const lines = text.split('\n').filter(Boolean);
    lastLine = lines[lines.length - 1] || '(empty)';
  }

  totalRecipes += recipes;
  totalDone += done;
  totalFailed += failed;

  const stillRunning = lastLine && !lastLine.includes('all sources processed');
  const status = stillRunning ? '⏳ running' : (done > 0 ? '✓ done' : '·');
  console.log(`[${s.padEnd(12)}] ${status}  ${recipes} recipes  ${done} books done  ${failed} failed`);
  console.log(`             last: ${lastLine.slice(0, 110)}`);
}

console.log('');
console.log(`TOTAL: ${totalRecipes} historical recipes  |  ${totalDone} books extracted  |  ${totalFailed} failures`);
console.log('');
console.log('To watch a single source live:');
console.log('  tail -f _logs_archive_org.txt');

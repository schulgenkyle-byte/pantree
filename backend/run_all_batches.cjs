#!/usr/bin/env node
/**
 * Orchestrator: loop through all remaining batches until done.
 * - Queries D1 for up to 200 rows at a time needing modernization
 * - Runs modernize_cocktails to generate SQL
 * - Applies SQL via wrangler
 */
const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const here = __dirname;

function shell(cmd, opts = {}) {
  return execSync(cmd, { cwd: here, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 50 * 1024 * 1024, ...opts });
}

function fetchBatch(batchNum) {
  const outFile = path.join(here, `batch_${batchNum}.json`);
  const cmd =
    `npx wrangler d1 execute pantrie-db-staging --remote ` +
    `--command "SELECT id, title, original_text, source_year, source_book, glass_type FROM recipe WHERE is_historic=1 AND (modernized_text IS NULL OR modernized_text = '') LIMIT 200" --json`;
  const out = shell(cmd);
  // wrangler prints some preamble to stderr; the JSON is on stdout starting with '[' or '{'
  const jsonStart = out.indexOf('[');
  const json = jsonStart >= 0 ? out.slice(jsonStart) : out;
  fs.writeFileSync(outFile, json);
  const parsed = JSON.parse(json);
  const rows = Array.isArray(parsed) ? parsed[0].results : parsed.results;
  return { file: outFile, count: rows.length };
}

function processBatch(batchNum, jsonFile) {
  const sqlFile = path.join(here, `batch_${batchNum}.sql`);
  const out = shell(`node modernize_cocktails.cjs "${jsonFile}" "${sqlFile}"`);
  const stats = JSON.parse(out);
  return { sqlFile, stats };
}

function applyBatch(sqlFile) {
  const out = shell(`npx wrangler d1 execute pantrie-db-staging --remote --file "${sqlFile}"`);
  return out;
}

function main() {
  const maxBatches = Number(process.env.MAX_BATCHES || 20);
  let batch = 1;
  let totalMod = 0;
  let totalSkip = 0;
  const allExamples = [];

  while (batch <= maxBatches) {
    console.log(`\n=== Batch ${batch} ===`);
    let fetched;
    try {
      fetched = fetchBatch(batch);
    } catch (e) {
      console.error(`fetch failed on batch ${batch}:`, e.message);
      break;
    }
    console.log(`fetched ${fetched.count} rows`);
    if (fetched.count === 0) {
      console.log('No more rows. Done!');
      break;
    }

    const { sqlFile, stats } = processBatch(batch, fetched.file);
    console.log(`modernized=${stats.modernized} skipped=${stats.skipped}`);
    totalMod += stats.modernized;
    totalSkip += stats.skipped;
    if (stats.examples) allExamples.push(...stats.examples);

    try {
      applyBatch(sqlFile);
      console.log(`applied batch ${batch}`);
    } catch (e) {
      console.error(`apply failed on batch ${batch}:`, e.message.slice(0, 500));
      break;
    }

    // cleanup intermediate files
    try { fs.unlinkSync(fetched.file); } catch {}
    try { fs.unlinkSync(sqlFile); } catch {}

    batch++;
  }

  console.log('\n=== SUMMARY ===');
  console.log(`batches run: ${batch - 1}`);
  console.log(`total modernized: ${totalMod}`);
  console.log(`total skipped: ${totalSkip}`);
  console.log(`example count: ${allExamples.length}`);
  fs.writeFileSync(path.join(here, 'modernize_summary.json'), JSON.stringify({
    batches: batch - 1,
    totalMod,
    totalSkip,
    examples: allExamples.slice(0, 10),
  }, null, 2));
}

main();

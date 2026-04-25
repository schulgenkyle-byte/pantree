// Dump modern cocktail titles to JSON for trademark scan
const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const sql = "SELECT id, title, source_book FROM recipe WHERE content_type IN ('cocktail','mocktail') AND is_historic = 0 AND audit_status != 'trademark_review' ORDER BY source_book, title";

const out = execSync(`npx wrangler d1 execute pantrie-db-staging --remote --command "${sql}" --json`, {
  cwd: __dirname,
  maxBuffer: 50 * 1024 * 1024,
  encoding: 'utf8',
});

// wrangler may print log lines before JSON; find the first '['
const jsonStart = out.indexOf('[');
const json = out.slice(jsonStart);
const parsed = JSON.parse(json);
const rows = parsed[0].results;

fs.writeFileSync(path.join(__dirname, 'cocktails_raw.json'), JSON.stringify(rows, null, 2));
console.log(`Wrote ${rows.length} cocktails to cocktails_raw.json`);
console.log('Sources:', [...new Set(rows.map(r => r.source_book))].join(', '));

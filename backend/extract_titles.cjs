const fs = require('fs');
const path = require('path');
const rows = JSON.parse(fs.readFileSync(path.join(__dirname, 'cocktails_raw.json'), 'utf8'));
fs.writeFileSync(
  path.join(__dirname, 'cocktail_titles.txt'),
  rows.map(r => `${r.source_book}\t${r.title}\t${r.id}`).join('\n')
);
console.log('Wrote', rows.length, 'lines');

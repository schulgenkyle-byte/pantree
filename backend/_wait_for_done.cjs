// Wait until cocktail_audit_progress.json shows processed_total >= 2565
const fs = require('fs');
const file = require('path').join(__dirname, 'cocktail_audit_progress.json');
function read() {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch { return null; }
}
function poll() {
  const p = read();
  if (p) {
    process.stdout.write(`processed=${p.processed_total} mod=${p.modernized} story=${p.story_filled} del=${p.deleted} ocr=${p.ocr_damaged}\n`);
    if (p.processed_total >= 2565) {
      console.log('DONE');
      process.exit(0);
    }
  }
  setTimeout(poll, 15000);
}
poll();
// hard timeout 25 min
setTimeout(() => { console.log('TIMEOUT'); process.exit(1); }, 25 * 60 * 1000);

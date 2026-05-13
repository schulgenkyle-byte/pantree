// Build a single HTML file with the Story text + images base64-embedded at the
// right break points. Open in Word OR browser, Ctrl+A, Ctrl+C, paste into
// Kickstarter's Story editor. Images come along with the paste.
const fs = require('fs');
const path = require('path');

const ROOT = __dirname;
const STORY_TXT = path.join(ROOT, '_paste', '01-story.txt');
const OUT = path.join(ROOT, '_paste', '01-story-with-images.html');

const HERO   = 'C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/kickstarter-hero-v2.png';
const MENU   = 'C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/curate-menu-card.png';
const APPS   = 'C:/Users/12566/projects/speakeater-site/app-screens';
const REC    = path.join(APPS, '02-recipes.png');
const POUR   = path.join(APPS, '05-pour.png');
const BOOT   = path.join(APPS, '08-era-bootlegger.png');
const PANTRY = path.join(APPS, '01-pantry.png');

function b64(p) {
  if (!fs.existsSync(p)) return null;
  const ext = path.extname(p).slice(1).toLowerCase();
  const mime = ext === 'jpg' ? 'image/jpeg' : `image/${ext}`;
  return `data:${mime};base64,${fs.readFileSync(p).toString('base64')}`;
}

// "Insert image AFTER any paragraph that contains this fragment" map.
// Ordered so the FIRST matching paragraph for each fragment gets the image.
const PINS = [
  { afterContains: '__TOP__',                                              src: HERO,   alt: 'Speakeater — pre-Prohibition cocktails from what is in your fridge', width: 100 },
  { afterContains: 'I built Speakeater to keep it going',                  src: REC,    alt: 'Recipe deck ranked by what is in your fridge',                       width: 38 },
  { afterContains: 'That mode is called Bootlegger',                       src: BOOT,   alt: 'Bootlegger mode — manuscript facsimile page',                        width: 38 },
  { afterContains: '23,743 food recipes that get ranked',                  src: PANTRY, alt: 'Pantry built from one photograph of the fridge',                     width: 38 },
  { afterContains: 'Each menu names the bar, the bartender, the year',    src: MENU,   alt: 'The Bee\'s Knees Garden Party — a sample Curate-a-Party menu',       width: 55 },
  { afterContains: 'I read every email',                                   src: POUR,   alt: 'Pouring a cocktail — sourced from the 1862–1923 manuscripts',         width: 38 },
];

const story = fs.readFileSync(STORY_TXT, 'utf-8');
const paragraphs = story.split(/\n\n+/).map(p => p.trim()).filter(Boolean);

// Walk paragraphs, emit each, inject image after any matching pin
const used = new Set();
const parts = [];

// Inject TOP image first
const topPin = PINS.find(p => p.afterContains === '__TOP__');
if (topPin && !used.has(topPin.src)) {
  const data = b64(topPin.src);
  if (data) {
    parts.push(`<p style="text-align:center"><img src="${data}" alt="${topPin.alt}" style="max-width:${topPin.width}%;height:auto;display:inline-block" /></p>`);
    used.add(topPin.src);
  }
}

for (const p of paragraphs) {
  // Detect heading-style paragraphs (the canonical brand voice has H1/H2 stripped to
  // plain prose but if a line looks like a heading we still want it visually prominent).
  // For Kickstarter paste we use <h2> for the second short bold line and <h3> for
  // section markers. Heuristic: short lines (<80 chars) standing alone, no period inside.
  const isHeadline = p.length < 100 && !p.includes('. ') && p.length > 4;
  if (isHeadline) {
    parts.push(`<h2 style="font-family:Georgia,serif;font-weight:500;letter-spacing:-0.01em;margin:32px 0 16px">${escapeHtml(p)}</h2>`);
  } else {
    parts.push(`<p style="font-family:Georgia,serif;font-size:16px;line-height:1.6;margin:0 0 18px">${escapeHtml(p).replace(/\n/g, '<br />')}</p>`);
  }
  // Check pins for this paragraph
  for (const pin of PINS) {
    if (pin.afterContains === '__TOP__') continue;
    if (used.has(pin.src)) continue;
    if (p.includes(pin.afterContains)) {
      const data = b64(pin.src);
      if (data) {
        parts.push(`<p style="text-align:center;margin:24px 0"><img src="${data}" alt="${pin.alt}" style="max-width:${pin.width}%;height:auto;display:inline-block" /></p>`);
        used.add(pin.src);
      }
    }
  }
}

function escapeHtml(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Speakeater Kickstarter — Story with Images</title>
<style>
  body { max-width: 720px; margin: 40px auto; padding: 0 24px; color: #1a1a1a; }
  h2 { font-size: 22px; }
  img { border: 0; }
</style>
</head>
<body>
${parts.join('\n')}
</body>
</html>`;

fs.writeFileSync(OUT, html);
const bytes = fs.statSync(OUT).size;
console.log(`built: ${OUT}`);
console.log(`size:  ${(bytes / 1024 / 1024).toFixed(2)} MB`);
console.log(`images embedded: ${used.size}/${PINS.length}`);
const missing = PINS.filter(p => !used.has(p.src));
if (missing.length) {
  console.log('UNPLACED (no matching paragraph):', missing.map(p => p.afterContains));
}

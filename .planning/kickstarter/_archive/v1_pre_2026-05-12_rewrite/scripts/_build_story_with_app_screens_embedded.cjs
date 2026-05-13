// Same as _build_story_with_app_screens.cjs, but every <img src=...> is
// replaced with a base64 data URL. The resulting HTML is fully self-contained:
// you can email it, drop it on a USB stick, or open it from anywhere on disk
// and the images still render. Will NOT survive paste into Kickstarter's
// editor (their sanitizer strips data URLs), but works for previews and
// stakeholder-share.
//
// Run: node _build_story_with_app_screens_embedded.cjs

const fs = require('fs');
const path = require('path');

const KS = __dirname;
const STORY_TXT = path.join(KS, '_paste', '01-story.txt');
const IMG_DIR = path.join(KS, '_paste', 'app-screens-NEW');
const OUT = path.join(KS, '_paste', '01-story-with-app-screens-EMBEDDED.html');

const SECTION_IMAGES = {
  '__hero__': [
    'speakeater_mystery-engine_09_host-code-j2g3-4players.jpg',
  ],
  "What's already built": [
    'speakeater_party-index_01_bees-knees-garden.jpg',
    'speakeater_party-index_02_roaring-rooftop-gatsby.jpg',
  ],
  'How a Mystery Night works': [
    'speakeater_mystery-engine_02_host-code-qmxl-empty.jpg',
    'speakeater_mystery-engine_01_join-code-entry.jpg',
    'speakeater_mystery-engine_03_host-code-qmxl-3players.jpg',
    'speakeater_mystery-engine_09_host-code-j2g3-4players.jpg',
    'speakeater_mystery-index_03_algonquin-bootlegger.jpg',
    'speakeater_mystery-index_04_ritz-pendennis.jpg',
    'speakeater_mystery-index_05_pendennis-vanishing.jpg',
    'speakeater_mystery-cast_01_playwright-actress.jpg',
    'speakeater_mystery-cast_02_editor-matron-author.jpg',
    'speakeater_mystery-cast_03_host-playbook-timeline.jpg',
  ],
  'And the food and drinks are real': [
    'speakeater_party-index_03_gatsby-bootlegger-fiveoffifty.jpg',
    'speakeater_party-detail_bootleggers-den_hero.jpg',
    'speakeater_party-detail_bootleggers-den_inside.jpg',
    'speakeater_party-detail_bootleggers-den_spec.jpg',
    'speakeater_roadmap_01_hotel-bars-holiday-tour.jpg',
    'speakeater_roadmap_02_holiday-nights.jpg',
    'speakeater_roadmap_03_occasions.jpg',
  ],
  'What this Kickstarter funds': [
    'speakeater_mystery-engine_04_host-code-qmxl-inprogress.jpg',
  ],
  'Stretch goals': [
    'speakeater_roadmap_06_speakeasy-world-tour-stretch.jpg',
    'speakeater_roadmap_07_world-tour-cities.jpg',
    'speakeater_roadmap_08_world-tour-more-cities.jpg',
    'speakeater_roadmap_05_more-mystery-nights-stretch.jpg',
  ],
};

const dataUrlCache = {};
function toDataUrl(filename) {
  if (dataUrlCache[filename]) return dataUrlCache[filename];
  const buf = fs.readFileSync(path.join(IMG_DIR, filename));
  const ext = path.extname(filename).slice(1).toLowerCase();
  const mime = ext === 'jpg' || ext === 'jpeg' ? 'image/jpeg' : `image/${ext}`;
  const url = `data:${mime};base64,${buf.toString('base64')}`;
  dataUrlCache[filename] = url;
  return url;
}

const storyTxt = fs.readFileSync(STORY_TXT, 'utf-8');
const rawLines = storyTxt.split('\n');
const blocks = [];
let buf = [];
for (const line of rawLines) {
  if (line.trim() === '') {
    if (buf.length) { blocks.push(buf.join(' ').trim()); buf = []; }
  } else {
    buf.push(line);
  }
}
if (buf.length) blocks.push(buf.join(' ').trim());

const filtered = blocks.filter(b =>
  !b.startsWith('Paste into Kickstarter') &&
  !b.startsWith('Persona declared')
);

function escapeHtml(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function imgTag(filename) {
  const caption = filename
    .replace(/^speakeater_/, '')
    .replace(/\.jpg$/, '')
    .replace(/_/g, ' · ')
    .replace(/-/g, ' ');
  return `<figure>
  <img src="${toDataUrl(filename)}" alt="${escapeHtml(caption)}">
  <figcaption>${escapeHtml(caption)}</figcaption>
</figure>`;
}

const out = [];
out.push('<!DOCTYPE html>');
out.push('<html lang="en">');
out.push('<head>');
out.push('<meta charset="utf-8">');
out.push('<title>Speakeater Kickstarter — Story with App Screens (embedded)</title>');
out.push(`<style>
  body { max-width: 760px; margin: 24px auto; padding: 0 16px; color: #1a1410; background: #fafaf7; }
  h1 { font-family: Georgia, serif; font-weight: 600; letter-spacing: -0.02em; line-height: 1.1; margin: 32px 0 24px; font-size: 32px; }
  h2 { font-family: Georgia, serif; font-weight: 500; letter-spacing: -0.01em; margin: 40px 0 16px; font-size: 22px; padding-top: 24px; border-top: 1px solid #d8d4ca; }
  p { font-family: Georgia, serif; font-size: 16px; line-height: 1.6; margin: 0 0 18px; }
  figure { margin: 24px 0; }
  figure img { max-width: 320px; height: auto; display: block; margin: 0 auto; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.15); }
  figcaption { font-family: Georgia, serif; font-size: 12px; color: #6b6357; text-align: center; margin-top: 8px; font-style: italic; }
  .screens-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin: 24px 0; }
  .screens-grid figure { margin: 0; }
  .screens-grid img { max-width: 100%; }
  .marker { font-family: ui-monospace, monospace; font-size: 11px; color: #b39468; text-transform: uppercase; letter-spacing: 0.08em; margin: 12px 0 4px; }
</style>`);
out.push('</head>');
out.push('<body>');

let sawH1 = false;
const usedSections = new Set();

function emitSectionImages(sectionTitle) {
  const imgs = SECTION_IMAGES[sectionTitle];
  if (!imgs || imgs.length === 0) return;
  if (usedSections.has(sectionTitle)) return;
  usedSections.add(sectionTitle);
  out.push(`<div class="marker">screens for: ${escapeHtml(sectionTitle)}</div>`);
  if (imgs.length === 1) {
    out.push(imgTag(imgs[0]));
  } else {
    out.push('<div class="screens-grid">');
    imgs.forEach(f => out.push(imgTag(f)));
    out.push('</div>');
  }
}

filtered.forEach((block) => {
  const isShort = block.length < 110;
  const hasTerminal = /[.?!]\s*$/.test(block);
  const looksLikeHeading = isShort && !hasTerminal && !block.startsWith('|') && !block.startsWith('$');

  if (!sawH1) {
    out.push(`<h1>${escapeHtml(block)}</h1>`);
    sawH1 = true;
    emitSectionImages('__hero__');
    return;
  }
  if (looksLikeHeading) {
    out.push(`<h2>${escapeHtml(block)}</h2>`);
    emitSectionImages(block);
  } else {
    out.push(`<p>${escapeHtml(block)}</p>`);
  }
});

// Alternates pool.
const placedBasenames = new Set(Object.values(SECTION_IMAGES).flat());
const alternates = fs.readdirSync(IMG_DIR)
  .filter(f => f.endsWith('.jpg') && !placedBasenames.has(f))
  .sort();

if (alternates.length) {
  out.push('<h2>Alternate shots (swap-in pool)</h2>');
  out.push('<p>Variants of screens already placed above. Swap them in if the primary shot doesn\'t feel right.</p>');
  out.push('<div class="screens-grid">');
  alternates.forEach(f => out.push(imgTag(f)));
  out.push('</div>');
}

out.push('</body>');
out.push('</html>');

fs.writeFileSync(OUT, out.join('\n'));
const stats = fs.statSync(OUT);
console.log(`wrote: ${OUT}`);
console.log(`size: ${(stats.size / 1024 / 1024).toFixed(1)} MB`);
console.log(`images embedded: ${Object.keys(dataUrlCache).length}`);

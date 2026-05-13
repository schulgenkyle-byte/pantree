// Builds _paste/01-story-with-app-screens.html — a preview of the campaign story
// interleaved with the renamed app screenshots in app-screens-NEW/.
//
// This is the file you open in a browser, scroll through, and use as the visual
// reference when you paste the story into Kickstarter and upload images section
// by section. Image paths are relative to _paste/, so the HTML must stay there.
//
// Run: node _build_story_with_app_screens.cjs
//
// Source story:  _paste/01-story.txt (already markdown-stripped)
// Source images: _paste/app-screens-NEW/*.jpg (renamed 2026-05-12)

const fs = require('fs');
const path = require('path');

const KS = __dirname;
const STORY_TXT = path.join(KS, '_paste', '01-story.txt');
const IMG_DIR = path.join(KS, '_paste', 'app-screens-NEW');
const OUT = path.join(KS, '_paste', '01-story-with-app-screens.html');

// Section-to-images map. Headings are matched as the first short, period-less
// line that appears in 01-story.txt. Order within each array = render order.
const SECTION_IMAGES = {
  // Hero (after H1, before any H2)
  '__hero__': [
    'app-screens-NEW/speakeater_mystery-engine_09_host-code-j2g3-4players.jpg',
  ],

  "What's already built": [
    'app-screens-NEW/speakeater_party-index_01_bees-knees-garden.jpg',
    'app-screens-NEW/speakeater_party-index_02_roaring-rooftop-gatsby.jpg',
  ],

  'How a Mystery Night works': [
    'app-screens-NEW/speakeater_mystery-engine_02_host-code-qmxl-empty.jpg',
    'app-screens-NEW/speakeater_mystery-engine_01_join-code-entry.jpg',
    'app-screens-NEW/speakeater_mystery-engine_03_host-code-qmxl-3players.jpg',
    'app-screens-NEW/speakeater_mystery-engine_09_host-code-j2g3-4players.jpg',
    'app-screens-NEW/speakeater_mystery-index_03_algonquin-bootlegger.jpg',
    'app-screens-NEW/speakeater_mystery-index_04_ritz-pendennis.jpg',
    'app-screens-NEW/speakeater_mystery-index_05_pendennis-vanishing.jpg',
    'app-screens-NEW/speakeater_mystery-cast_01_playwright-actress.jpg',
    'app-screens-NEW/speakeater_mystery-cast_02_editor-matron-author.jpg',
    'app-screens-NEW/speakeater_mystery-cast_03_host-playbook-timeline.jpg',
  ],

  'And the food and drinks are real': [
    'app-screens-NEW/speakeater_party-index_03_gatsby-bootlegger-fiveoffifty.jpg',
    'app-screens-NEW/speakeater_party-detail_bootleggers-den_hero.jpg',
    'app-screens-NEW/speakeater_party-detail_bootleggers-den_inside.jpg',
    'app-screens-NEW/speakeater_party-detail_bootleggers-den_spec.jpg',
    'app-screens-NEW/speakeater_roadmap_01_hotel-bars-holiday-tour.jpg',
    'app-screens-NEW/speakeater_roadmap_02_holiday-nights.jpg',
    'app-screens-NEW/speakeater_roadmap_03_occasions.jpg',
  ],

  'What this Kickstarter funds': [
    'app-screens-NEW/speakeater_mystery-engine_04_host-code-qmxl-inprogress.jpg',
  ],

  'Stretch goals': [
    'app-screens-NEW/speakeater_roadmap_06_speakeasy-world-tour-stretch.jpg',
    'app-screens-NEW/speakeater_roadmap_07_world-tour-cities.jpg',
    'app-screens-NEW/speakeater_roadmap_08_world-tour-more-cities.jpg',
    'app-screens-NEW/speakeater_roadmap_05_more-mystery-nights-stretch.jpg',
  ],
};

// ----- build -----

const storyTxt = fs.readFileSync(STORY_TXT, 'utf-8');

// Parse story into blocks separated by blank lines.
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

// Drop the paste-instruction preamble lines.
const filtered = blocks.filter(b =>
  !b.startsWith('Paste into Kickstarter') &&
  !b.startsWith('Persona declared')
);

function escapeHtml(s) {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function imgTag(rel) {
  const filename = path.basename(rel);
  // Caption derived from filename: strip prefix + ext, replace separators.
  const caption = filename
    .replace(/^speakeater_/, '')
    .replace(/\.jpg$/, '')
    .replace(/_/g, ' · ')
    .replace(/-/g, ' ');
  return `<figure>
  <img src="${rel}" alt="${escapeHtml(caption)}">
  <figcaption>${escapeHtml(caption)}</figcaption>
</figure>`;
}

const out = [];
out.push('<!DOCTYPE html>');
out.push('<html lang="en">');
out.push('<head>');
out.push('<meta charset="utf-8">');
out.push('<title>Speakeater Kickstarter — Story with App Screens</title>');
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

// Block 0 is the H1 (the first content line).
// Then blocks alternate between H2 (short, no period) and paragraphs.
let sawH1 = false;
let currentSection = '__hero__';
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
    imgs.forEach(rel => out.push(imgTag(rel)));
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
    currentSection = block;
    emitSectionImages(currentSection);
  } else {
    out.push(`<p>${escapeHtml(block)}</p>`);
  }
});

// Append any sections that didn't appear in story.txt (defensive).
Object.keys(SECTION_IMAGES).forEach(k => {
  if (!usedSections.has(k)) {
    out.push(`<div class="marker">unmatched section: ${escapeHtml(k)}</div>`);
    emitSectionImages(k);
  }
});

// Alternates pool — every screenshot on disk that wasn't placed above.
const placedBasenames = new Set(
  Object.values(SECTION_IMAGES).flat().map(p => path.basename(p))
);
const alternates = fs.readdirSync(IMG_DIR)
  .filter(f => f.endsWith('.jpg') && !placedBasenames.has(f))
  .sort();

if (alternates.length) {
  out.push('<h2>Alternate shots (swap-in pool)</h2>');
  out.push('<p>These are byte-different variants of screens already placed above — different codes, different player counts, slightly different status bars. Swap them in if a primary shot doesn\'t feel right in the campaign editor.</p>');
  out.push('<div class="screens-grid">');
  alternates.forEach(f => out.push(imgTag('app-screens-NEW/' + f)));
  out.push('</div>');
}

out.push('</body>');
out.push('</html>');

fs.writeFileSync(OUT, out.join('\n'));

// Coverage report.
const allMapped = new Set(
  Object.values(SECTION_IMAGES).flat().map(p => path.basename(p))
);
const onDisk = fs.readdirSync(IMG_DIR).filter(f => f.endsWith('.jpg'));
const unmapped = onDisk.filter(f => !allMapped.has(f));

console.log(`wrote: ${OUT}`);
console.log(`screens on disk: ${onDisk.length}`);
console.log(`screens placed:  ${allMapped.size}`);
console.log(`unmapped:        ${unmapped.length}`);
if (unmapped.length) {
  console.log('  - ' + unmapped.join('\n  - '));
}
console.log(`sections used: ${Array.from(usedSections).join(', ')}`);

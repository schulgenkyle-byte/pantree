// Builds _paste/01-PITCH-BODY-PASTE-READY.html — the single file Kyle opens
// in a browser, Ctrl+A / Ctrl+C / pastes into Kickstarter's Story editor.
//
// Differences from the preview HTMLs:
//   * Only inline styles (KS strips <style> blocks).
//   * Base64 images embedded (so the preview renders; KS will swap them out
//     when Kyle re-uploads each image via the editor's image button — but
//     the structure and surrounding text survive the paste).
//   * No planning markers / captions / alternates pool.
//   * Heading levels match KS's expected document hierarchy (h1 once, h2 for
//     section breaks).

const fs = require('fs');
const path = require('path');

const KS = __dirname;
const STORY_TXT = path.join(KS, '_paste', '01-story.txt');
const IMG_DIR = path.join(KS, '_paste', 'app-screens-NEW');
const OUT = path.join(KS, '_paste', '01-PITCH-BODY-PASTE-READY.html');

// Same section map as the preview, but ONLY the screens we want in the
// pasted pitch body. No alternates.
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
    'speakeater_roadmap_01_hotel-bars-holiday-tour.jpg',
    'speakeater_roadmap_02_holiday-nights.jpg',
    'speakeater_roadmap_03_occasions.jpg',
  ],
  'What this Kickstarter funds': [
    'speakeater_mystery-engine_04_host-code-qmxl-inprogress.jpg',
  ],
  'Stretch goals': [
    'speakeater_roadmap_06_speakeasy-world-tour-stretch.jpg',
    'speakeater_roadmap_05_more-mystery-nights-stretch.jpg',
  ],
};

const dataUrlCache = {};
function toDataUrl(filename) {
  if (dataUrlCache[filename]) return dataUrlCache[filename];
  const buf = fs.readFileSync(path.join(IMG_DIR, filename));
  const ext = path.extname(filename).slice(1).toLowerCase();
  const mime = (ext === 'jpg' || ext === 'jpeg') ? 'image/jpeg' : `image/${ext}`;
  const url = `data:${mime};base64,${buf.toString('base64')}`;
  dataUrlCache[filename] = url;
  return url;
}

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

const filtered = blocks.filter(b =>
  !b.startsWith('Paste into Kickstarter') &&
  !b.startsWith('Persona declared')
);

function esc(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

// Inline-style helpers (KS strips <style>; everything must be inline).
const IMG_SOLO_STYLE = "max-width:520px;width:100%;height:auto;display:block;margin:24px auto;border-radius:8px;";
const IMG_GRID_ITEM_STYLE = "max-width:240px;width:100%;height:auto;display:inline-block;margin:8px;border-radius:8px;vertical-align:top;";
const H1_STYLE = "font-family:Georgia,serif;font-weight:600;letter-spacing:-0.02em;line-height:1.15;margin:32px 0 24px;font-size:32px;color:#1a1410;";
const H2_STYLE = "font-family:Georgia,serif;font-weight:500;letter-spacing:-0.01em;margin:40px 0 16px;font-size:24px;color:#1a1410;";
const P_STYLE = "font-family:Georgia,serif;font-size:17px;line-height:1.65;margin:0 0 18px;color:#1a1410;";

function imgSolo(filename) {
  return `<img src="${toDataUrl(filename)}" alt="${esc(filename.replace(/^speakeater_/, '').replace(/\.jpg$/, '').replace(/_/g, ' ').replace(/-/g, ' '))}" style="${IMG_SOLO_STYLE}">`;
}
function imgGrid(filenames) {
  return `<div style="text-align:center;margin:24px 0;">${filenames.map(f =>
    `<img src="${toDataUrl(f)}" alt="${esc(f.replace(/^speakeater_/, '').replace(/\.jpg$/, '').replace(/_/g, ' ').replace(/-/g, ' '))}" style="${IMG_GRID_ITEM_STYLE}">`
  ).join('')}</div>`;
}

function emitSectionImages(sectionTitle) {
  const imgs = SECTION_IMAGES[sectionTitle];
  if (!imgs || imgs.length === 0) return '';
  if (imgs.length === 1) return imgSolo(imgs[0]);
  return imgGrid(imgs);
}

const out = [];
out.push('<!DOCTYPE html>');
out.push('<html lang="en">');
out.push('<head>');
out.push('<meta charset="utf-8">');
out.push('<title>Speakeater Kickstarter — Pitch Body (paste-ready)</title>');
out.push('</head>');
out.push('<body style="max-width:760px;margin:24px auto;padding:0 16px;background:#fafaf7;">');

let sawH1 = false;
const usedSections = new Set();

filtered.forEach((block) => {
  const isShort = block.length < 110;
  const hasTerminal = /[.?!]\s*$/.test(block);
  const looksLikeHeading = isShort && !hasTerminal && !block.startsWith('|') && !block.startsWith('$');

  if (!sawH1) {
    out.push(`<h1 style="${H1_STYLE}">${esc(block)}</h1>`);
    sawH1 = true;
    out.push(emitSectionImages('__hero__'));
    usedSections.add('__hero__');
    return;
  }
  if (looksLikeHeading) {
    out.push(`<h2 style="${H2_STYLE}">${esc(block)}</h2>`);
    out.push(emitSectionImages(block));
    usedSections.add(block);
  } else {
    out.push(`<p style="${P_STYLE}">${esc(block)}</p>`);
  }
});

out.push('</body>');
out.push('</html>');

fs.writeFileSync(OUT, out.join('\n'));
const stats = fs.statSync(OUT);
console.log(`wrote: ${OUT}`);
console.log(`size: ${(stats.size / 1024 / 1024).toFixed(1)} MB`);
console.log(`images embedded: ${Object.keys(dataUrlCache).length}`);
console.log(`sections matched: ${Array.from(usedSections).join(', ')}`);

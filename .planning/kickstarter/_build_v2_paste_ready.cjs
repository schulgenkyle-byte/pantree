// Builds the V2 paste-ready story for Kickstarter — short (~850 words),
// research-informed rewrite with 5 hero images embedded as base64.
//
// Reads:   01-CAMPAIGN-PAGE-V2.md
// Writes:
//   _paste/01-story-V2.txt                         (plain text for the Story field)
//   _paste/01-PITCH-BODY-V2-PASTE-READY.html       (paste-ready HTML, self-contained)
//
// Image placement = section breaks, one image per ~170 words on average.

const fs = require('fs');
const path = require('path');

const KS = __dirname;
const SRC_MD = path.join(KS, '01-CAMPAIGN-PAGE-V2.md');
const IMG_DIR = path.join(KS, '_paste', 'app-screens-NEW');
const OUT_TXT = path.join(KS, '_paste', '01-story-V2.txt');
const OUT_HTML = path.join(KS, '_paste', '01-PITCH-BODY-V2-PASTE-READY.html');

// Five hero images, one per section break. Curated for arc, not coverage.
const SECTION_IMAGES = {
  '__hero__': 'speakeater_mystery-engine_09_host-code-j2g3-4players.jpg',
  'What you\'re getting': 'speakeater_mystery-index_03_algonquin-bootlegger.jpg',
  'How a Mystery Night plays out': 'speakeater_mystery-cast_01_playwright-actress.jpg',
  'The five Mystery Nights ready at launch': 'speakeater_party-detail_bootleggers-den_hero.jpg',
  'Why I built this': 'speakeater_party-index_03_gatsby-bootlegger-fiveoffifty.jpg',
};

// ---- read + clean the markdown ----
const md = fs.readFileSync(SRC_MD, 'utf-8');

function mdToPlain(md) {
  return md
    // Strip everything before the first H1
    .replace(/^[\s\S]*?(?=^# [^\n]+$)/m, '')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/\b_([^_]+)_\b/g, '$1')
    .replace(/(^|\s)\*([^*\n]+)\*/g, '$1$2')
    .replace(/^[\-*]\s+/gm, '')
    .replace(/^---+$/gm, '')
    .replace(/^>\s?/gm, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

const plain = mdToPlain(md);
fs.writeFileSync(OUT_TXT, plain);

// ---- block-parse for HTML ----
const rawLines = plain.split('\n');
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

// ---- base64 image helper ----
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

function esc(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

// Inline style tokens (KS strips <style> blocks)
const H1_STYLE = "font-family:Georgia,serif;font-weight:600;letter-spacing:-0.02em;line-height:1.1;margin:32px 0 28px;font-size:38px;color:#1a1410;";
const H2_STYLE = "font-family:Georgia,serif;font-weight:500;letter-spacing:-0.01em;margin:44px 0 18px;font-size:24px;color:#1a1410;";
const P_STYLE = "font-family:Georgia,serif;font-size:17px;line-height:1.65;margin:0 0 18px;color:#1a1410;";
const IMG_STYLE = "max-width:520px;width:100%;height:auto;display:block;margin:28px auto;border-radius:8px;";

function imgTag(filename) {
  if (!filename) return '';
  return `<img src="${toDataUrl(filename)}" alt="" style="${IMG_STYLE}">`;
}

const out = [];
out.push('<!DOCTYPE html>');
out.push('<html lang="en">');
out.push('<head><meta charset="utf-8"><title>Speakeater Kickstarter — Pitch Body V2 (paste-ready)</title></head>');
out.push('<body style="max-width:760px;margin:24px auto;padding:0 16px;background:#fafaf7;">');

let sawH1 = false;
const usedSections = new Set();

blocks.forEach((block) => {
  const isShort = block.length < 110;
  const hasTerminal = /[.?!]\s*$/.test(block);
  const looksLikeHeading = isShort && !hasTerminal && !block.startsWith('|') && !block.startsWith('$');

  if (!sawH1) {
    out.push(`<h1 style="${H1_STYLE}">${esc(block)}</h1>`);
    sawH1 = true;
    out.push(imgTag(SECTION_IMAGES['__hero__']));
    usedSections.add('__hero__');
    return;
  }
  if (looksLikeHeading) {
    out.push(`<h2 style="${H2_STYLE}">${esc(block)}</h2>`);
    const img = SECTION_IMAGES[block];
    if (img && !usedSections.has(block)) {
      out.push(imgTag(img));
      usedSections.add(block);
    }
  } else {
    out.push(`<p style="${P_STYLE}">${esc(block)}</p>`);
  }
});

out.push('</body></html>');

fs.writeFileSync(OUT_HTML, out.join('\n'));

const wordCount = plain.split(/\s+/).filter(w => w.length > 0).length;
const sizeMb = fs.statSync(OUT_HTML).size / 1024 / 1024;

console.log('=== V2 STORY BUILT ===');
console.log(`source:     ${SRC_MD}`);
console.log(`plain text: ${OUT_TXT}`);
console.log(`paste HTML: ${OUT_HTML} (${sizeMb.toFixed(1)} MB)`);
console.log(`word count: ${wordCount}`);
console.log(`images:     ${Object.keys(dataUrlCache).length} embedded`);
console.log(`sections matched for images: ${Array.from(usedSections).join(', ')}`);

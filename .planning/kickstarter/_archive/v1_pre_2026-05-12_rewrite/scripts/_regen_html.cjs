// Regenerates _paste/01-story-with-images.html from the new game-first source.
// Pulls images out of the existing HTML and re-inserts them at the same
// section-break positions so we don't need to re-encode the 6 base64 photos.
//
// Run after editing 01-CAMPAIGN-PAGE.md + _build_paste_folder.cjs has refreshed
// _paste/01-story.txt:
//   node _regen_html.cjs

const fs = require('fs');
const path = require('path');

const KS = __dirname;
const oldHtml = fs.readFileSync(path.join(KS, '_paste/01-story-with-images.html'), 'utf-8');
const storyTxt = fs.readFileSync(path.join(KS, '_paste/01-story.txt'), 'utf-8');

// Extract base64-embedded images in order. We'll re-use them.
const imgs = [];
const imgRegex = /<img[^>]+\/?>(?:<\/img>)?/g;
let m;
while ((m = imgRegex.exec(oldHtml))) imgs.push(m[0]);
console.log(`extracted ${imgs.length} image embeds (preserving in new HTML)`);

// Build a structured doc from story.txt. Lines that are short + no terminal
// punctuation = H1/H2. Everything else = paragraph.
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

// Drop the preamble blockquote / instruction lines.
const filtered = blocks.filter(b => !b.startsWith('Paste into Kickstarter') && !b.startsWith('Persona declared'));

const out = [];
out.push('<!DOCTYPE html>');
out.push('<html lang="en">');
out.push('<head>');
out.push('<meta charset="utf-8">');
out.push('<title>Speakeater Kickstarter — Story with Images</title>');
out.push('<style>');
out.push('body { max-width: 720px; margin: 24px auto; padding: 0 16px; color: #1a1410; background: #fafaf7; }');
out.push('h1 { font-family: Georgia, serif; font-weight: 600; letter-spacing: -0.02em; line-height: 1.1; margin: 32px 0 24px; font-size: 32px; }');
out.push('h2 { font-family: Georgia, serif; font-weight: 500; letter-spacing: -0.01em; margin: 32px 0 16px; font-size: 22px; }');
out.push('p { font-family: Georgia, serif; font-size: 16px; line-height: 1.6; margin: 0 0 18px; }');
out.push('img { max-width: 100%; height: auto; margin: 18px 0; border-radius: 4px; display: block; }');
out.push('</style>');
out.push('</head>');
out.push('<body>');

let imgIdx = 0;
let sawH1 = false;
filtered.forEach((block) => {
  const isShort = block.length < 110;
  const hasPeriod = /[.?!]\s*$/.test(block);
  const looksLikeHeading = isShort && !hasPeriod && !block.startsWith('|') && !block.startsWith('$');

  if (!sawH1) {
    out.push(`<h1 style="font-family:Georgia,serif;font-weight:600;letter-spacing:-0.02em;line-height:1.1;margin:32px 0 24px;font-size:32px">${escapeHtml(block)}</h1>`);
    sawH1 = true;
    return;
  }

  if (looksLikeHeading) {
    out.push(`<h2 style="font-family:Georgia,serif;font-weight:500;letter-spacing:-0.01em;margin:32px 0 16px">${escapeHtml(block)}</h2>`);
    // Insert next image after a major section heading.
    if (imgIdx < imgs.length) {
      out.push(imgs[imgIdx++]);
    }
  } else {
    out.push(`<p style="font-family:Georgia,serif;font-size:16px;line-height:1.6;margin:0 0 18px">${escapeHtml(block)}</p>`);
  }
});

out.push('</body>');
out.push('</html>');

fs.writeFileSync(path.join(KS, '_paste/01-story-with-images.html'), out.join('\n'));
console.log(`wrote new HTML with ${imgIdx} of ${imgs.length} images placed.`);

function escapeHtml(s) {
  // Minimal escape — KS HTML doesn't need full encoding, just ampersands
  // and angle brackets in plain text wouldn't survive their sanitizer otherwise.
  return s
    .replace(/&/g, '&amp;')
    .replace(/<(?![ai\/])/g, '&lt;') // don't escape <a or <i
    .replace(/(?<![a-z]"|=")(?<!<)>/g, '&gt;');
}

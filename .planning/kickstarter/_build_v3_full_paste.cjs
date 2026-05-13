// V3 — TIGHT story (~450 words) plus rewards / stretch / FAQ / risks in one
// paste-ready HTML.
//
// Differences vs V2 builder:
//   * Source is 01-CAMPAIGN-PAGE-V3.md (story cut to ~450 words)
//   * Only 3 hero images instead of 5 (less scroll weight)
//   * Same trailing sections (tiers / stretch / FAQ / risks) so a single paste
//     still covers the entire Story field
//
// Reads:
//   01-CAMPAIGN-PAGE-V3.md, 02-REWARD-TIERS.md, _paste/02-risks.txt,
//   _paste/03-faqs/_ALL.txt, _paste/app-screens-NEW/*.jpg
//
// Writes:
//   _paste/00-FULL-CAMPAIGN-V3-PASTE-READY.html

const fs = require('fs');
const path = require('path');

const KS = __dirname;
const STORY_MD = path.join(KS, '01-CAMPAIGN-PAGE-V3.md');
const TIERS_MD = path.join(KS, '02-REWARD-TIERS.md');
const RISKS_TXT = path.join(KS, '_paste', '02-risks.txt');
const FAQS_TXT = path.join(KS, '_paste', '03-faqs', '_ALL.txt');
const IMG_DIR = path.join(KS, '_paste', 'app-screens-NEW');
const OUT = path.join(KS, '_paste', '00-FULL-CAMPAIGN-V3-PASTE-READY.html');

// Only 3 images. Hero shot, mystery list, cinematic atmosphere.
const SECTION_IMAGES = {
  '__hero__': 'speakeater_mystery-engine_09_host-code-j2g3-4players.jpg',
  'Five Mystery Nights at launch': 'speakeater_mystery-index_03_algonquin-bootlegger.jpg',
  'Why I built this': 'speakeater_party-detail_bootleggers-den_hero.jpg',
};

// ---- helpers ----
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
function esc(s) { return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }

function mdToPlain(md) {
  return md
    .replace(/^[\s\S]*?(?=^# [^\n]+$)/m, '')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/^[\-*]\s+/gm, '')
    .replace(/^---+$/gm, '')
    .replace(/^>\s?/gm, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

const H1 = "font-family:Georgia,serif;font-weight:600;letter-spacing:-0.02em;line-height:1.1;margin:32px 0 28px;font-size:38px;color:#1a1410;";
const H2 = "font-family:Georgia,serif;font-weight:500;letter-spacing:-0.01em;margin:44px 0 16px;font-size:24px;color:#1a1410;border-top:1px solid #d8d4ca;padding-top:28px;";
const P = "font-family:Georgia,serif;font-size:17px;line-height:1.65;margin:0 0 18px;color:#1a1410;";
const PSMALL = "font-family:Georgia,serif;font-size:15px;line-height:1.6;margin:0 0 14px;color:#3a2f24;";
const IMG = "max-width:520px;width:100%;height:auto;display:block;margin:24px auto;border-radius:8px;";
const TIER_BLOCK = "border-left:3px solid #b39468;padding:4px 0 4px 16px;margin:0 0 22px;";
const PRICE = "font-family:Georgia,serif;font-style:italic;font-size:20px;color:#6b4520;margin:0 0 4px;font-weight:600;";
const FAQ_Q = "font-family:Georgia,serif;font-size:17px;font-weight:600;color:#1a1410;margin:22px 0 6px;";

function imgTag(filename) {
  if (!filename) return '';
  return `<img src="${toDataUrl(filename)}" alt="" style="${IMG}">`;
}

// ---- STORY (V3) ----
function renderStory() {
  const md = fs.readFileSync(STORY_MD, 'utf-8');
  const plain = mdToPlain(md);
  // Preserve bold-marker so we can render the mystery names. Convert **X.** -> <strong>X.</strong>
  const blocks = [];
  let buf = [];
  for (const line of plain.split('\n')) {
    if (line.trim() === '') {
      if (buf.length) { blocks.push(buf.join(' ').trim()); buf = []; }
    } else buf.push(line);
  }
  if (buf.length) blocks.push(buf.join(' ').trim());

  function renderInline(s) {
    return esc(s).replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
  }

  const out = [];
  let sawH1 = false;
  blocks.forEach((block) => {
    const isShort = block.length < 110;
    const hasTerminal = /[.?!]\s*$/.test(block);
    const looksLikeHeading = isShort && !hasTerminal && !block.startsWith('|') && !block.startsWith('$') && !block.startsWith('**');

    if (!sawH1) {
      out.push(`<h1 style="${H1}">${esc(block)}</h1>`);
      sawH1 = true;
      out.push(imgTag(SECTION_IMAGES['__hero__']));
      return;
    }
    if (looksLikeHeading) {
      out.push(`<h2 style="${H2}">${renderInline(block)}</h2>`);
      const img = SECTION_IMAGES[block];
      if (img) out.push(imgTag(img));
    } else {
      out.push(`<p style="${P}">${renderInline(block)}</p>`);
    }
  });
  return out.join('\n');
}

// ---- TIERS ----
function renderTiers() {
  const md = fs.readFileSync(TIERS_MD, 'utf-8');
  const out = [];
  out.push(`<h2 style="${H2}">Reward tiers</h2>`);
  out.push(`<p style="${P}">Eleven tiers. Speakeater Pro is the cocktail and recipe subscription (45 dollars a year retail post-launch). Mystery Night games are a separate 14.99 a game retail. Backers always pay less.</p>`);

  const tierRe = /^##\s+(\$[\d,]+(?:\.\d+)?\.\s+[^\n]+)$([\s\S]*?)(?=^##\s+|\Z)/gm;
  let m;
  while ((m = tierRe.exec(md)) !== null) {
    const headerLine = m[1].trim();
    if (headerLine.startsWith('Stretch goals')) continue;
    if (headerLine.match(/Pricing math|Why .* are a separate|Why \$30\/yr/)) continue;
    const body = m[2].trim().replace(/^---+$/gm, '').trim();
    const paras = body.split(/\n\s*\n/).map(p => p.replace(/\n/g, ' ').trim()).filter(p => p.length > 0);

    out.push(`<div style="${TIER_BLOCK}">`);
    out.push(`<div style="${PRICE}">${esc(headerLine)}</div>`);
    paras.forEach(p => {
      const clean = p.replace(/\*\*(.+?)\*\*/g, '$1').replace(/_([^_]+)_/g, '$1');
      out.push(`<p style="${PSMALL}">${esc(clean)}</p>`);
    });
    out.push(`</div>`);
  }
  return out.join('\n');
}

// ---- STRETCH GOALS ----
function renderStretchGoals() {
  const md = fs.readFileSync(TIERS_MD, 'utf-8');
  const out = [];
  out.push(`<h2 style="${H2}">Stretch goals</h2>`);
  const tableRe = /\|\s*\$([0-9,]+)\s*\|\s*\*\*([^*]+)\*\*\s*([^|]+)\|/g;
  let m;
  while ((m = tableRe.exec(md)) !== null) {
    const threshold = m[1].trim();
    const title = m[2].trim().replace(/\.$/, '');
    const desc = m[3].trim();
    out.push(`<div style="${TIER_BLOCK}">`);
    out.push(`<div style="${PRICE}">$${threshold} &middot; ${esc(title)}</div>`);
    out.push(`<p style="${PSMALL}">${esc(desc)}</p>`);
    out.push(`</div>`);
  }
  return out.join('\n');
}

// ---- FAQ ----
function renderFaq() {
  const txt = fs.readFileSync(FAQS_TXT, 'utf-8');
  const out = [];
  out.push(`<h2 style="${H2}">FAQ</h2>`);
  const faqRe = /QUESTION:\s*([\s\S]*?)\n\nANSWER:\s*([\s\S]*?)(?=\n\s*---|\n\s*\[FAQ|\Z)/g;
  let m;
  while ((m = faqRe.exec(txt)) !== null) {
    const q = m[1].trim().replace(/\s+/g, ' ');
    const a = m[2].trim();
    out.push(`<p style="${FAQ_Q}">${esc(q)}</p>`);
    a.split(/\n\s*\n/).forEach(para => {
      const clean = para.replace(/\n/g, ' ').trim();
      if (clean) out.push(`<p style="${PSMALL}">${esc(clean)}</p>`);
    });
  }
  return out.join('\n');
}

// ---- RISKS ----
function renderRisks() {
  const txt = fs.readFileSync(RISKS_TXT, 'utf-8');
  const out = [];
  out.push(`<h2 style="${H2}">Risks and challenges</h2>`);
  txt.split(/\n\s*\n/).forEach(p => {
    const clean = p.replace(/\n/g, ' ').trim();
    if (clean) out.push(`<p style="${P}">${esc(clean)}</p>`);
  });
  return out.join('\n');
}

// ---- assemble ----
const out = [];
out.push('<!DOCTYPE html>');
out.push('<html lang="en">');
out.push('<head><meta charset="utf-8"><title>Speakeater Kickstarter — Full Campaign V3</title></head>');
out.push('<body style="max-width:760px;margin:24px auto;padding:0 16px;background:#fafaf7;">');
out.push(renderStory());
out.push(renderTiers());
out.push(renderStretchGoals());
out.push(renderFaq());
out.push(renderRisks());
out.push('</body></html>');

fs.writeFileSync(OUT, out.join('\n'));

// Word counts for transparency
const storyWords = mdToPlain(fs.readFileSync(STORY_MD, 'utf-8')).split(/\s+/).filter(Boolean).length;
const sizeMb = fs.statSync(OUT).size / 1024 / 1024;
console.log(`wrote: ${OUT}`);
console.log(`size:  ${sizeMb.toFixed(1)} MB`);
console.log(`story-narrative word count: ${storyWords}`);
console.log(`images embedded: ${Object.keys(dataUrlCache).length}`);

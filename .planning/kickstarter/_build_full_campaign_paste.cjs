// Builds ONE self-contained HTML containing the entire Kickstarter campaign:
// story + reward tiers + stretch goals + FAQ + risks. Designed for a single
// Ctrl+A / Ctrl+C / Ctrl+V into Kickstarter's Story editor — no piecemeal
// per-field paste needed.
//
// Note: reward TIER IMAGES still need to be uploaded into KS's reward sidebar
// separately (KS doesn't let you paste images into reward cards). Same for the
// reward prices, delivery dates, and caps — those are KS form fields.
//
// Reads:
//   01-CAMPAIGN-PAGE-V2.md          (short story body)
//   02-REWARD-TIERS.md              (11 tiers + stretch goals + pricing math)
//   _paste/02-risks.txt
//   _paste/03-faqs/_ALL.txt
//   _paste/app-screens-NEW/*.jpg    (5 hero photos, base64 embedded)
//
// Writes:
//   _paste/00-FULL-CAMPAIGN-PASTE-READY.html

const fs = require('fs');
const path = require('path');

const KS = __dirname;
const STORY_MD = path.join(KS, '01-CAMPAIGN-PAGE-V2.md');
const TIERS_MD = path.join(KS, '02-REWARD-TIERS.md');
const RISKS_TXT = path.join(KS, '_paste', '02-risks.txt');
const FAQS_TXT = path.join(KS, '_paste', '03-faqs', '_ALL.txt');
const IMG_DIR = path.join(KS, '_paste', 'app-screens-NEW');
const OUT = path.join(KS, '_paste', '00-FULL-CAMPAIGN-PASTE-READY.html');

const SECTION_IMAGES = {
  '__hero__': 'speakeater_mystery-engine_09_host-code-j2g3-4players.jpg',
  "What you're getting": 'speakeater_mystery-index_03_algonquin-bootlegger.jpg',
  'How a Mystery Night plays out': 'speakeater_mystery-cast_01_playwright-actress.jpg',
  'The five Mystery Nights ready at launch': 'speakeater_party-detail_bootleggers-den_hero.jpg',
  'Why I built this': 'speakeater_party-index_03_gatsby-bootlegger-fiveoffifty.jpg',
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

function esc(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function mdToPlain(md) {
  return md
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

function blocksFrom(text) {
  const rawLines = text.split('\n');
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
  return blocks;
}

// Inline styles — KS strips <style> blocks
const H1 = "font-family:Georgia,serif;font-weight:600;letter-spacing:-0.02em;line-height:1.1;margin:32px 0 28px;font-size:38px;color:#1a1410;";
const H2 = "font-family:Georgia,serif;font-weight:500;letter-spacing:-0.01em;margin:48px 0 18px;font-size:26px;color:#1a1410;border-top:1px solid #d8d4ca;padding-top:32px;";
const H3 = "font-family:Georgia,serif;font-weight:500;font-style:italic;margin:32px 0 12px;font-size:21px;color:#1a1410;";
const P  = "font-family:Georgia,serif;font-size:17px;line-height:1.65;margin:0 0 18px;color:#1a1410;";
const PSMALL = "font-family:Georgia,serif;font-size:15px;line-height:1.6;margin:0 0 14px;color:#3a2f24;";
const IMG = "max-width:520px;width:100%;height:auto;display:block;margin:28px auto;border-radius:8px;";
const TIER_BLOCK = "border-left:3px solid #b39468;padding:6px 0 6px 18px;margin:0 0 26px;";
const PRICE = "font-family:Georgia,serif;font-style:italic;font-size:22px;color:#6b4520;margin:0 0 6px;font-weight:600;";
const FAQ_Q = "font-family:Georgia,serif;font-size:17px;font-weight:600;color:#1a1410;margin:24px 0 6px;";

function imgTag(filename) {
  if (!filename) return '';
  return `<img src="${toDataUrl(filename)}" alt="" style="${IMG}">`;
}

// ---- Section: STORY (V2) ----

function renderStory() {
  const md = fs.readFileSync(STORY_MD, 'utf-8');
  const plain = mdToPlain(md);
  const blocks = blocksFrom(plain);
  const out = [];
  let sawH1 = false;
  const usedSections = new Set();

  blocks.forEach((block) => {
    const isShort = block.length < 110;
    const hasTerminal = /[.?!]\s*$/.test(block);
    const looksLikeHeading = isShort && !hasTerminal && !block.startsWith('|') && !block.startsWith('$');

    if (!sawH1) {
      out.push(`<h1 style="${H1}">${esc(block)}</h1>`);
      sawH1 = true;
      out.push(imgTag(SECTION_IMAGES['__hero__']));
      return;
    }
    if (looksLikeHeading) {
      out.push(`<h2 style="${H2}">${esc(block)}</h2>`);
      const img = SECTION_IMAGES[block];
      if (img && !usedSections.has(block)) {
        out.push(imgTag(img));
        usedSections.add(block);
      }
    } else {
      out.push(`<p style="${P}">${esc(block)}</p>`);
    }
  });
  return out.join('\n');
}

// ---- Section: REWARD TIERS ----

function renderTiers() {
  const md = fs.readFileSync(TIERS_MD, 'utf-8');
  const out = [];
  out.push(`<h2 style="${H2}">Reward tiers, in plain language</h2>`);
  out.push(`<p style="${P}">Eleven tiers. Two products underneath them. Speakeater Pro is the cooking and cocktail subscription (45 dollars a year retail post-launch). Mystery Night games are a separate purchase, 14.99 a game retail. The tiers bundle these in escalating ways. Backers always pay less than retail.</p>`);

  // Parse "## $X. Title" blocks
  const tierRe = /^##\s+(\$[\d,]+(?:\.\d+)?\.\s+[^\n]+)$([\s\S]*?)(?=^##\s+|\Z)/gm;
  let m;
  while ((m = tierRe.exec(md)) !== null) {
    const headerLine = m[1].trim();
    if (headerLine.startsWith('Stretch goals')) continue; // handled separately
    const body = m[2]
      .trim()
      .replace(/^---+$/gm, '')
      .trim();
    // Filter out "Pricing math" / "Why" / etc. content sections (they're not tiers)
    if (headerLine.match(/Pricing math|Why .* are a separate|Why \$30\/yr/)) continue;
    // Split body into paragraphs by blank lines
    const paras = body.split(/\n\s*\n/).map(p => p.replace(/\n/g, ' ').trim()).filter(p => p.length > 0);

    out.push(`<div style="${TIER_BLOCK}">`);
    out.push(`<div style="${PRICE}">${esc(headerLine)}</div>`);
    paras.forEach(p => {
      // Strip markdown bold
      const clean = p.replace(/\*\*(.+?)\*\*/g, '$1').replace(/_([^_]+)_/g, '$1');
      out.push(`<p style="${PSMALL}">${esc(clean)}</p>`);
    });
    out.push(`</div>`);
  }
  return out.join('\n');
}

// ---- Section: STRETCH GOALS ----

function renderStretchGoals() {
  const md = fs.readFileSync(TIERS_MD, 'utf-8');
  const out = [];
  out.push(`<h2 style="${H2}">Stretch goals</h2>`);
  out.push(`<p style="${P}">If the campaign overfunds, the following unlock for backers at the threshold tier or above.</p>`);

  // Parse the markdown table rows: | $25,000 | **Title.** Description... |
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

// ---- Section: FAQ ----

function renderFaq() {
  const txt = fs.readFileSync(FAQS_TXT, 'utf-8');
  const out = [];
  out.push(`<h2 style="${H2}">Frequently asked questions</h2>`);
  // _ALL.txt format: "---\n[FAQ N]\n\nQUESTION:\n...\n\nANSWER:\n...\n"
  const faqRe = /QUESTION:\s*([\s\S]*?)\n\nANSWER:\s*([\s\S]*?)(?=\n\s*---|\n\s*\[FAQ|\Z)/g;
  let m;
  while ((m = faqRe.exec(txt)) !== null) {
    const q = m[1].trim().replace(/\s+/g, ' ');
    const a = m[2].trim();
    out.push(`<p style="${FAQ_Q}">${esc(q)}</p>`);
    // Split answer into paragraphs by blank lines
    a.split(/\n\s*\n/).forEach(para => {
      const clean = para.replace(/\n/g, ' ').trim();
      if (clean) out.push(`<p style="${PSMALL}">${esc(clean)}</p>`);
    });
  }
  return out.join('\n');
}

// ---- Section: RISKS ----

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
out.push('<head><meta charset="utf-8"><title>Speakeater Kickstarter — Full Campaign (single paste)</title></head>');
out.push('<body style="max-width:760px;margin:24px auto;padding:0 16px;background:#fafaf7;">');

out.push(renderStory());
out.push(renderTiers());
out.push(renderStretchGoals());
out.push(renderFaq());
out.push(renderRisks());

out.push('</body></html>');

fs.writeFileSync(OUT, out.join('\n'));

const sizeMb = fs.statSync(OUT).size / 1024 / 1024;
console.log(`wrote: ${OUT}`);
console.log(`size:  ${sizeMb.toFixed(1)} MB`);
console.log(`images embedded: ${Object.keys(dataUrlCache).length}`);

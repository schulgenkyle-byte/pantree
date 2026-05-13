// Splits 01-CAMPAIGN-PAGE.md + 02-REWARD-TIERS.md into paste-ready .txt files.
// Plain text, no markdown markers, paragraph breaks preserved. Copy-paste straight
// into Kickstarter's form fields.
const fs = require('fs');
const path = require('path');

const KS = __dirname;
const OUT = path.join(KS, '_paste');
fs.mkdirSync(OUT, { recursive: true });
fs.mkdirSync(path.join(OUT, '03-faqs'), { recursive: true });
fs.mkdirSync(path.join(OUT, '04-rewards'), { recursive: true });

function mdToPlain(md) {
  return md
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

// V1 long source archived 2026-05-12. V2 is the live short rewrite.
const camp = fs.readFileSync(path.join(KS, '01-CAMPAIGN-PAGE-V2.md'), 'utf-8');
const tiers = fs.readFileSync(path.join(KS, '02-REWARD-TIERS.md'), 'utf-8');

// 01 — Story body (everything before ## FAQ, drop the H1/instruction preamble)
{
  let body = camp.replace(/^>.*\n+/m, '');                // drop preamble blockquote
  body = body.replace(/^# Speakeater[^\n]*\n+/m, '');     // drop the doc H1
  const faqIdx = body.indexOf('## FAQ');
  if (faqIdx > 0) body = body.slice(0, faqIdx);
  const risksIdx = body.indexOf('## Risks');
  if (risksIdx > 0 && faqIdx < 0) body = body.slice(0, risksIdx);
  fs.writeFileSync(path.join(OUT, '01-story.txt'), mdToPlain(body));
}

// 02 — Risks
{
  const m = camp.match(/## Risks and challenges\s+([\s\S]*?)(?=\n##|\n---|$)/);
  fs.writeFileSync(path.join(OUT, '02-risks.txt'), m ? mdToPlain(m[1]) : '');
}

// 03 — FAQs (one file per Q/A, plus a single combined file for reference)
{
  const start = camp.indexOf('## FAQ');
  const end = camp.indexOf('## Risks', start);
  const block = camp.slice(start, end > 0 ? end : undefined);
  const re = /\*\*([^*]+)\*\*\n\n([\s\S]*?)(?=\n\n\*\*|\n\n## |$)/g;
  let m, i = 0;
  const combined = [];
  while ((m = re.exec(block)) !== null) {
    i++;
    const q = m[1].trim();
    const a = mdToPlain(m[2].trim());
    const slug = q.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40);
    const fileName = `${String(i).padStart(2, '0')}-${slug}.txt`;
    fs.writeFileSync(path.join(OUT, '03-faqs', fileName), `QUESTION:\n${q}\n\nANSWER:\n${a}\n`);
    combined.push(`---\n[FAQ ${i}]\n\nQUESTION:\n${q}\n\nANSWER:\n${a}\n`);
  }
  fs.writeFileSync(path.join(OUT, '03-faqs', '_ALL.txt'), combined.join('\n'));
}

// 04 — Reward tiers (one file per tier)
{
  const re = /## (\$[\d,]+[.\s—]+[^\n]+)\n+([\s\S]*?)(?=\n## |---\n|$)/g;
  let m, i = 0;
  while ((m = re.exec(tiers)) !== null) {
    i++;
    const header = m[1].trim();
    const body = mdToPlain(m[2].trim());
    // header like "$30 — Pro Founder Year" -> "05-30-pro-founder-year"
    const slug = header.toLowerCase().replace(/\$/g, '').replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40);
    fs.writeFileSync(path.join(OUT, '04-rewards', `${String(i).padStart(2, '0')}-${slug}.txt`), `TIER:\n${header}\n\nDESCRIPTION:\n${body}\n`);
  }
  // Stretch goals
  const sg = tiers.match(/## Stretch goals\s+([\s\S]*?)(?=\n## |\n---|$)/);
  if (sg) {
    fs.writeFileSync(path.join(OUT, '04-rewards', '_stretch-goals.txt'), mdToPlain(sg[1]));
  }
}

// Index file — counts pulled from the actual generated folders so this stays
// accurate even when tiers or FAQs are added/removed in the source docs.
const faqCount = fs.readdirSync(path.join(OUT, '03-faqs')).filter(f => f.endsWith('.txt') && f !== '_ALL.txt').length;
const tierCount = fs.readdirSync(path.join(OUT, '04-rewards')).filter(f => f.endsWith('.txt') && f !== '_stretch-goals.txt').length;

const tierImageCount = (() => {
  try { return fs.readdirSync(path.join(OUT, 'tier-images')).filter(f => f.endsWith('.png')).length; }
  catch { return 0; }
})();

const idx = [
  'PASTE INTO KICKSTARTER',
  '',
  'TEXT TO PASTE (in this order, into Kickstarter\'s pitch editor):',
  '',
  `  1. Story body            -> 01-story.txt`,
  `  2. Risks and challenges  -> 02-risks.txt`,
  `  3. FAQs (${faqCount} total)        -> 03-faqs/*.txt (one per FAQ, or 03-faqs/_ALL.txt)`,
  `  4. Reward tiers (${tierCount})      -> 04-rewards/*.txt (one per tier)`,
  `  5. Stretch goals         -> 04-rewards/_stretch-goals.txt`,
  '',
  'Each .txt is plain text. Paragraph breaks preserved. No markdown syntax to clean up.',
  'Open file, Ctrl+A, Ctrl+C, paste into the matching Kickstarter field.',
  '',
  'IMAGES TO UPLOAD INSIDE KICKSTARTER\'S EDITOR:',
  '',
  '  kickstarter-hero.png         Campaign banner (top of page + social previews)',
  `  tier-images/ (${tierImageCount} PNGs)         Card art for the ${tierCount} reward tiers — one per tier`,
  '  app-screens-NEW/             33 renamed app screenshots, organized by section',
  '  IMAGE_UPLOAD_CHECKLIST.txt   Step-by-step list of which app screen to upload where',
  '',
  'VISUAL PREVIEWS (open in a browser, do not paste):',
  '',
  '  01-PITCH-BODY-PASTE-READY.html           Single file: full story with images, paste-ready into KS Story',
  '  01-story-with-app-screens.html           Linked-image preview (faster to open)',
  '  01-story-with-app-screens-EMBEDDED.html  Same, base64-embedded for sharing (~23 MB)',
  '  01-story-with-images.html                Legacy AI-hero-image preview',
  '',
  'BUILD COMMANDS (re-run from .planning/kickstarter/):',
  '',
  '  node _build_paste_folder.cjs                  Rebuild this folder from the .md sources',
  '  node _build_story_with_app_screens.cjs        Rebuild the linked-image preview',
  '  node _build_story_with_app_screens_embedded.cjs  Rebuild base64-embedded preview',
  '  node _build_pitch_body_paste_ready.cjs        Rebuild the paste-ready pitch body',
  '  node _build_upload_checklist.cjs              Rebuild the upload checklist',
].join('\n');
fs.writeFileSync(path.join(OUT, '00-README.txt'), idx);

console.log('built:', OUT);
console.log(fs.readdirSync(OUT));
console.log('faqs:', fs.readdirSync(path.join(OUT, '03-faqs')));
console.log('rewards:', fs.readdirSync(path.join(OUT, '04-rewards')));

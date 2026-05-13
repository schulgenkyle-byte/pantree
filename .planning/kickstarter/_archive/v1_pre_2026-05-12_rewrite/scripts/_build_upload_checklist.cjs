// Builds _paste/IMAGE_UPLOAD_CHECKLIST.txt — a step-by-step list telling Kyle
// exactly which screenshot to upload between which paragraphs in Kickstarter's
// editor. Kickstarter strips local file paths, so the only way to get images
// into the story is to upload them one at a time via "Add Image."
//
// Run: node _build_upload_checklist.cjs

const fs = require('fs');
const path = require('path');

const KS = __dirname;
const STORY_TXT = path.join(KS, '_paste', '01-story.txt');
const IMG_DIR = path.join(KS, '_paste', 'app-screens-NEW');
const OUT = path.join(KS, '_paste', 'IMAGE_UPLOAD_CHECKLIST.txt');

// Same mapping the HTML preview uses. Keep in sync.
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

const ABS_IMG_DIR = path.resolve(IMG_DIR);

const out = [];
out.push('==========================================');
out.push('KICKSTARTER IMAGE UPLOAD CHECKLIST');
out.push('==========================================');
out.push('');
out.push('Kickstarter\'s editor strips local file paths, so the HTML preview\'s');
out.push('embedded images won\'t survive paste. Instead, work through this list:');
out.push('');
out.push('1. Paste the plain story text from 01-story.txt into Kickstarter\'s pitch');
out.push('   editor first.');
out.push('2. For each step below, click "Add Image" in Kickstarter\'s editor at the');
out.push('   indicated position, then upload the named file from:');
out.push('');
out.push(`     ${ABS_IMG_DIR}\\`);
out.push('');
out.push('3. Check the [ ] box as you go.');
out.push('');
out.push('==========================================');
out.push('');

let step = 0;
function emitStep(position, file) {
  step++;
  const num = String(step).padStart(2, '0');
  out.push(`[ ] STEP ${num}`);
  out.push(`    Position: ${position}`);
  out.push(`    File:     ${file}`);
  out.push('');
}

out.push('--- HERO (top of campaign, right under the headline) ---');
out.push('');
SECTION_IMAGES['__hero__'].forEach(f => {
  emitStep('Top of campaign, immediately under the H1 headline.', f);
});

const sectionsInOrder = [
  "What's already built",
  'How a Mystery Night works',
  'And the food and drinks are real',
  'What this Kickstarter funds',
  'Stretch goals',
];

sectionsInOrder.forEach(section => {
  const imgs = SECTION_IMAGES[section] || [];
  if (imgs.length === 0) return;
  out.push(`--- SECTION: "${section}" ---`);
  out.push('');
  imgs.forEach((f, i) => {
    let pos;
    if (imgs.length === 1) {
      pos = `Directly under the "${section}" heading.`;
    } else if (i === 0) {
      pos = `Directly under the "${section}" heading.`;
    } else {
      pos = `Continue placing in "${section}" — group these as a strip after the first.`;
    }
    emitStep(pos, f);
  });
});

out.push('==========================================');
out.push('ALTERNATES (swap-in pool, do not upload by default)');
out.push('==========================================');
out.push('');
out.push('These are byte-different variants of screens already placed above.');
out.push('Only use one of these if a primary shot looks off in the editor.');
out.push('');

const placed = new Set(Object.values(SECTION_IMAGES).flat());
const onDisk = fs.readdirSync(IMG_DIR).filter(f => f.endsWith('.jpg')).sort();
const alternates = onDisk.filter(f => !placed.has(f));
alternates.forEach(f => out.push(`  - ${f}`));

out.push('');
out.push('==========================================');
out.push(`TOTAL UPLOADS: ${step}`);
out.push(`ALTERNATES:    ${alternates.length}`);
out.push(`TOTAL FILES:   ${step + alternates.length}`);
out.push('==========================================');

fs.writeFileSync(OUT, out.join('\n'));
console.log(`wrote: ${OUT}`);
console.log(`steps: ${step}, alternates: ${alternates.length}`);

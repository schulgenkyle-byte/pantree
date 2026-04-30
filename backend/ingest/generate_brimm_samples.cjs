#!/usr/bin/env node
/**
 * Brimm SAMPLE generator — runs 8 representative images via Imagen 4 Ultra
 * so the user can approve aesthetic + quality before committing to the full 124.
 *
 * Writes to a separate scratch dir (NOT into android resources) so failed samples
 * don't pollute the eventual full library or trigger an unintended rebuild.
 *
 * Reads key from C:\Users\12566\Downloads\GEMINI_KEY.txt (gitignored).
 */
const fs = require('fs');
const path = require('path');
const https = require('https');

const KEY_FILE = process.env.GEMINI_KEY_FILE || 'C:\\Users\\12566\\Downloads\\GEMINI_KEY.txt';
let API_KEY = process.env.GEMINI_API_KEY;
if (!API_KEY) {
  try { API_KEY = fs.readFileSync(KEY_FILE, 'utf8').trim(); }
  catch (e) { console.error(`FATAL: key file unreadable: ${KEY_FILE}`); process.exit(2); }
}

const OUT_DIR = 'C:\\Users\\12566\\OneDrive\\Desktop\\brimm_samples';
fs.mkdirSync(OUT_DIR, { recursive: true });

const MODEL = 'imagen-4.0-generate-001';

// ICON prompt — tight crop, subject fills frame, clean warm background so the
// image reads clearly at 24-48dp thumbnail size. Used for ingredients + glassware.
const ICON_PROMPT = (subject) =>
  `Macro product photography of a single subject. Subject fills 85% of square frame, centered, sharp focus, soft warm amber rim lighting, clean dark charcoal background with subtle warm gradient, no clutter, no text, no labels, no hands, photorealistic, high edge contrast, app icon style. Subject: ${subject}.`;

// HERO prompt — moody full-scene composition for cuisine cards + aisle headers
// where the image is rendered at larger sizes (>=80dp).
const HERO_PROMPT = (subject) =>
  `Professional food photography in dark moody speakeasy aesthetic. Low-key warm amber lighting, dark wood and velvet background, high contrast, vintage 1920s feel, no text, no menu cards, single subject centered, product photography style. Subject: ${subject}.`;

// 8 representative samples — 6 icons (small render) + 2 heroes (large render)
// so we can verify both styles at the size each will actually appear.
const SAMPLES = [
  ['tomato', 'a single ripe red tomato', 'icon'],
  ['beef', 'a raw ribeye steak, marbled', 'icon'],
  ['cheese', 'a wedge of aged cheddar cheese', 'icon'],
  ['herbs', 'a small bouquet of fresh basil and thyme', 'icon'],
  ['glass_coupe', 'an empty crystal coupe cocktail glass', 'icon'],
  ['glass_rocks', 'an empty old fashioned rocks glass', 'icon'],
  ['cuisine_italian', 'a single bowl of spaghetti pasta', 'hero'],
  ['aisle_produce', 'a small grouping of fresh vegetables: leafy greens, tomato, carrot', 'hero'],
];

function postImagen(prompt) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({
      instances: [{ prompt }],
      parameters: { sampleCount: 1, aspectRatio: '1:1', safetySetting: 'block_low_and_above' },
    });
    const req = https.request({
      method: 'POST',
      hostname: 'generativelanguage.googleapis.com',
      path: `/v1beta/models/${MODEL}:predict?key=${API_KEY}`,
      headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) },
    }, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        if (res.statusCode !== 200) {
          return reject(new Error(`HTTP ${res.statusCode}: ${data.slice(0, 400)}`));
        }
        try {
          const parsed = JSON.parse(data);
          const b64 = parsed?.predictions?.[0]?.bytesBase64Encoded;
          if (!b64) return reject(new Error(`No image in response: ${data.slice(0, 400)}`));
          resolve(Buffer.from(b64, 'base64'));
        } catch (e) { reject(new Error(`Parse error: ${e.message}`)); }
      });
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

(async () => {
  console.log(`Generating ${SAMPLES.length} samples via ${MODEL}`);
  console.log(`Output dir: ${OUT_DIR}\n`);
  let okCount = 0, failCount = 0;
  const t0 = Date.now();

  for (let i = 0; i < SAMPLES.length; i++) {
    const [slug, subject, style] = SAMPLES[i];
    const outPath = path.join(OUT_DIR, `v2_${slug}.png`);
    const prompt = style === 'icon' ? ICON_PROMPT(subject) : HERO_PROMPT(subject);
    try {
      const buf = await postImagen(prompt);
      fs.writeFileSync(outPath, buf);
      okCount++;
      console.log(`[${i + 1}/${SAMPLES.length}] OK   ${style} ${slug} (${(buf.length / 1024).toFixed(0)}KB)`);
    } catch (e) {
      failCount++;
      console.error(`[${i + 1}/${SAMPLES.length}] FAIL ${slug}: ${String(e.message).slice(0, 300)}`);
    }
    await sleep(1100);
  }

  const elapsed = ((Date.now() - t0) / 1000).toFixed(1);
  console.log(`\nDONE in ${elapsed}s — ok=${okCount} fail=${failCount}`);
  console.log(`Open: ${OUT_DIR}`);
})().catch((e) => { console.error('FATAL:', e); process.exit(1); });

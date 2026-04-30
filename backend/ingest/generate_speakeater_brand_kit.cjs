#!/usr/bin/env node
/**
 * Speakeater BRAND KIT generator — 16 logo / wordmark / mark concepts via
 * Imagen 4 Ultra so the user can pick a visual direction before commissioning
 * a real vector logo.
 *
 * IMPORTANT: these are RASTER PNGs from a generative model. Treat as concept
 * boards. Whichever direction the user picks gets re-created as a true vector
 * (Figma / Illustrator) before shipping as the actual app icon / Play Store
 * feature graphic.
 *
 * Output: image_assets/brimm/brand_kit/<slug>.png (folder renames to
 *   image_assets/pantrie/brand_kit/ in NAMING.md Phase G alongside ingredients).
 * Plus image_assets/brimm/brand_kit/brand_kit_manifest.json.
 * Plus image_assets/brimm/brand_kit/index.html — opens in browser as a grid
 *   review page so you can scroll through all 16 concepts and pick favorites.
 *
 * Resume-safe + retry-on-429 (matches ingredient gen pacing).
 */
const fs = require('fs');
const path = require('path');
const https = require('https');
const crypto = require('crypto');

const KEY_FILE = process.env.GEMINI_KEY_FILE || 'C:\\Users\\12566\\Downloads\\GEMINI_KEY.txt';
let API_KEY = process.env.GEMINI_API_KEY;
if (!API_KEY) {
  try { API_KEY = fs.readFileSync(KEY_FILE, 'utf8').trim(); }
  catch (e) { console.error(`FATAL: key file unreadable: ${KEY_FILE}`); process.exit(2); }
}

const REPO_ROOT = path.resolve(__dirname, '..', '..');
const OUT_DIR = path.join(REPO_ROOT, 'image_assets', 'brimm', 'brand_kit');
const MANIFEST_PATH = path.join(OUT_DIR, 'brand_kit_manifest.json');
const VIEWER_PATH = path.join(OUT_DIR, 'index.html');
fs.mkdirSync(OUT_DIR, { recursive: true });

const ENDPOINTS = [
  'imagen-4.0-generate-001',
  'imagen-4.0-fast-generate-001',
];

// Shared aesthetic scaffold that EVERY prompt inherits, so the kit feels coherent.
const AESTHETIC = `Dark moody speakeasy aesthetic. Deep charcoal black background (#0D0D0E base) with subtle warm amber rim light. Brass and antique gold (#C9A554) accents, no other colors except occasional cream highlight. Photorealistic high contrast product photography rendering, no flat illustration. No text artifacts, no lorem ipsum, no extra letters beyond what is specified. Centered subject, square composition, no border. 1024x1024.`;

const CONCEPTS = [
  // ---- "S pours into glass" thesis (user's idea, 4 variants) ----
  ['s_pour_martini', `Logo concept: a stylized capital letter S in elegant brass. The bottom curve of the S transforms into a flowing pour of golden liquid that lands in a martini glass with single olive garnish. The S is the focal point, the glass sits at the base. ${AESTHETIC}`],
  ['s_pour_coupe', `Logo concept: a stylized capital letter S in elegant brass, the lower curve of the S becoming a stream of golden liquid pouring into a vintage coupe cocktail glass. 1920s prohibition era styling. ${AESTHETIC}`],
  ['s_pour_rocks', `Logo concept: a stylized capital letter S in elegant brass, the bottom of the S pouring amber whiskey liquid into a heavy rocks glass with a single large ice cube. Whiskey/bourbon aesthetic. ${AESTHETIC}`],
  ['s_pour_flute', `Logo concept: a stylized capital letter S in elegant brass, the bottom of the S pouring effervescent golden champagne into a vintage flute glass with rising bubbles. Lighter, more celebratory feel. ${AESTHETIC}`],

  // ---- Monogram-only marks (4) ----
  ['monogram_artdeco', `Logo monogram: a geometric art deco capital letter S in brass on a dark velvet background. Sharp angles, vintage 1920s art deco geometry, embossed three-dimensional appearance, premium speakeasy mark. No glass, no other elements, just the letter mark. ${AESTHETIC}`],
  ['monogram_waxseal', `Logo monogram: a circular wax seal in deep oxblood-and-gold wax, embossed with an ornate capital letter S in the center. Rich texture of dripped wax, brass-toned highlights, sitting on dark wood. ${AESTHETIC}`],
  ['monogram_letterpress', `Logo monogram: a capital letter S deeply pressed into thick cream cotton paper using gold foil ink, vintage letterpress technique, visible paper texture and the deboss impression around the letter. The pressed paper sits on dark surface with dramatic side lighting. ${AESTHETIC}`],
  ['monogram_neon', `Logo monogram: a capital letter S rendered in glowing amber-orange neon tubing, mounted on a dark brick wall with subtle ambient glow. Vintage neon sign aesthetic, slight buzz/imperfection in the tubes. ${AESTHETIC}`],

  // ---- Wordmark options (4) ----
  ['wordmark_serif', `Wordmark logo: the single word "speakeater" in lowercase, set in an elegant vintage serif typeface, rendered as embossed gold foil on a dark velvet background. The word should be perfectly legible and well-kerned, no extra letters. ${AESTHETIC}`],
  ['wordmark_artdeco', `Wordmark logo: the single word "Speakeater" in mixed case, set in a geometric art deco sans-serif typeface, rendered as a brass nameplate on dark wood. Very legible, balanced kerning. No glass, no extra elements. ${AESTHETIC}`],
  ['wordmark_script', `Wordmark logo: the word "Speakeater" in flowing copperplate script with elegant flourishes, rendered in gold ink on cream label stock with dark border, like a vintage bourbon bottle label. Legible, no extra letters. ${AESTHETIC}`],
  ['wordmark_stencil', `Wordmark logo: the word "SPEAKEATER" in tall narrow uppercase stencil typeface, rendered as if painted in cream on a dark speakeasy door, slightly weathered. Legible, well kerned. ${AESTHETIC}`],

  // ---- Concept marks (4) ----
  ['concept_keyS', `Logo concept: an antique brass speakeasy door key, where the bow (handle end) of the key is shaped into a decorative letter S. The shaft and teeth of the key extend downward. Vintage 1920s speakeasy aesthetic. ${AESTHETIC}`],
  ['concept_matchbook', `Logo concept: a vintage matchbook cover sitting closed on dark wood, with the word "Speakeater" embossed in gold foil on the cover and a small striking strip visible. Slight wear on edges. ${AESTHETIC}`],
  ['concept_shaker', `Logo concept: the silhouette of a vintage three-piece cocktail shaker in brass, with an ornate capital letter S engraved into the body of the shaker. Single subject on dark background. ${AESTHETIC}`],
  ['concept_bowtie', `Logo concept: a minimalist composition of a brass bow tie with a single olive on a toothpick threaded through it, suggesting a bartender. Negative space and elegance. ${AESTHETIC}`],
];

// ---------- HTTP plumbing (mirrors generate_brimm_images.cjs) ----------
function postImagen(modelId, prompt) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({
      instances: [{ prompt }],
      parameters: { sampleCount: 1, aspectRatio: '1:1', safetySetting: 'block_low_and_above' },
    });
    const req = https.request({
      method: 'POST',
      hostname: 'generativelanguage.googleapis.com',
      path: `/v1beta/models/${modelId}:predict?key=${API_KEY}`,
      headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) },
    }, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        if (res.statusCode !== 200) return reject(new Error(`HTTP ${res.statusCode}: ${data.slice(0, 300)}`));
        try {
          const parsed = JSON.parse(data);
          const b64 = parsed?.predictions?.[0]?.bytesBase64Encoded;
          if (!b64) return reject(new Error(`No image: ${data.slice(0, 300)}`));
          resolve(Buffer.from(b64, 'base64'));
        } catch (e) { reject(new Error(`Parse: ${e.message}`)); }
      });
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

const sleep = (ms) => new Promise(r => setTimeout(r, ms));

async function generateOne(prompt) {
  let lastErr = null;
  for (const model of ENDPOINTS) {
    let attempt = 0;
    while (attempt < 5) {
      try {
        const buf = await postImagen(model, prompt);
        return { buf, model };
      } catch (e) {
        lastErr = e;
        const msg = String(e.message || '');
        if (/HTTP 404/.test(msg)) break;
        if (/HTTP 429/.test(msg) || /quota/i.test(msg) || /RESOURCE_EXHAUSTED/i.test(msg)) {
          attempt++;
          const backoff = Math.min(60_000, 15_000 * attempt);
          console.log(`    rate-limited (attempt ${attempt}), backing off ${backoff/1000}s...`);
          await sleep(backoff);
          continue;
        }
        throw e;
      }
    }
  }
  throw lastErr;
}

function writeViewer(items) {
  const cards = items.map(it => `
    <div class="card">
      <img src="${it.slug}.png" loading="lazy"/>
      <div class="meta">
        <div class="slug">${it.slug}</div>
        <div class="cat">${it.category}</div>
      </div>
    </div>`).join('\n');
  const html = `<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>Speakeater Brand Kit Concepts</title>
<style>
  body { background: #0D0D0E; color: #E8E3D9; font-family: -apple-system,sans-serif; margin: 0; padding: 32px; }
  h1 { color: #C9A554; font-weight: 300; letter-spacing: 4px; margin: 0 0 8px 0; }
  .sub { color: #8B8578; margin-bottom: 32px; font-size: 14px; }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 24px; }
  .card { background: #18181B; border-radius: 12px; overflow: hidden; border: 1px solid #2a2a2e; }
  .card img { width: 100%; display: block; aspect-ratio: 1/1; object-fit: cover; }
  .meta { padding: 12px 16px; }
  .slug { color: #C9A554; font-weight: 600; font-size: 14px; }
  .cat { color: #8B8578; font-size: 11px; text-transform: uppercase; letter-spacing: 1px; margin-top: 2px; }
  .legend { background: #18181B; padding: 16px; border-radius: 8px; margin-top: 32px; font-size: 13px; line-height: 1.6; }
</style></head>
<body>
  <h1>SPEAKEATER · BRAND KIT CONCEPTS</h1>
  <div class="sub">16 raster concepts. Pick a direction. Whichever you like best gets re-created as real vector before shipping.</div>
  <div class="grid">${cards}</div>
  <div class="legend">
    <strong style="color:#C9A554">Pour-into-glass thesis (4):</strong> your original idea in 4 glass variants (martini, coupe, rocks, flute).<br/>
    <strong style="color:#C9A554">Monogram (4):</strong> S-only marks in 4 treatments (art deco, wax seal, letterpress, neon).<br/>
    <strong style="color:#C9A554">Wordmark (4):</strong> "speakeater" set in 4 type styles.<br/>
    <strong style="color:#C9A554">Concept (4):</strong> non-letter approaches (key, matchbook, shaker, bowtie).
  </div>
</body></html>`;
  fs.writeFileSync(VIEWER_PATH, html);
}

(async () => {
  console.log(`Generating ${CONCEPTS.length} brand kit concepts via Imagen 4 Ultra`);
  console.log(`Output: ${OUT_DIR}\n`);

  const manifest = fs.existsSync(MANIFEST_PATH)
    ? JSON.parse(fs.readFileSync(MANIFEST_PATH, 'utf8'))
    : { generated_at: new Date().toISOString(), model: ENDPOINTS[0], items: {} };

  const items = [];
  let okCount = 0, failCount = 0, skippedCount = 0;
  const t0 = Date.now();

  for (let i = 0; i < CONCEPTS.length; i++) {
    const [slug, prompt] = CONCEPTS[i];
    const category = slug.startsWith('s_pour_') ? 'pour-into-glass' :
                     slug.startsWith('monogram_') ? 'monogram' :
                     slug.startsWith('wordmark_') ? 'wordmark' : 'concept';
    const outPath = path.join(OUT_DIR, `${slug}.png`);
    items.push({ slug, category });

    if (fs.existsSync(outPath) && fs.statSync(outPath).size > 1024) {
      skippedCount++;
      console.log(`[${i + 1}/${CONCEPTS.length}] SKIP ${slug}`);
      continue;
    }

    try {
      const { buf, model } = await generateOne(prompt);
      fs.writeFileSync(outPath, buf);
      const sha = crypto.createHash('sha256').update(buf).digest('hex').slice(0, 16);
      manifest.items[slug] = { category, prompt, model, bytes: buf.length, sha };
      fs.writeFileSync(MANIFEST_PATH, JSON.stringify(manifest, null, 2));
      okCount++;
      console.log(`[${i + 1}/${CONCEPTS.length}] OK   ${category}/${slug} (${(buf.length / 1024).toFixed(0)}KB)`);
    } catch (e) {
      failCount++;
      console.error(`[${i + 1}/${CONCEPTS.length}] FAIL ${slug}: ${String(e.message).slice(0, 240)}`);
    }
    await sleep(12_000); // ~5 RPM, stays under Imagen 4 Ultra cap
  }

  writeViewer(items);
  const elapsed = ((Date.now() - t0) / 1000).toFixed(1);
  console.log(`\nDONE in ${elapsed}s — ok=${okCount} fail=${failCount} skipped=${skippedCount}`);
  console.log(`Viewer: file:///${VIEWER_PATH.replace(/\\/g, '/')}`);
})().catch((e) => { console.error('FATAL:', e); process.exit(1); });

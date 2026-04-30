#!/usr/bin/env node
/**
 * Speakeater APP ICON v3 — "S with liquid" direction.
 * User feedback: hated v1 (bare S) and v2 (kitchen+drinks composites).
 * Liked the s_pour concept from the original brand kit, wants it as an icon.
 *
 * 5 variants exploring how liquid interacts with the S:
 *   - pours straight off bottom (the canonical version)
 *   - trails off the back/spine
 *   - splashes off bottom into ripples
 *   - forms the bottom of the S itself (liquid morph)
 *   - mini-glass at frame bottom catches the pour
 *
 * Output: image_assets/brimm/brand_kit/icons/v3_<slug>.png + viewer.
 */
const fs = require('fs');
const path = require('path');
const https = require('https');
const crypto = require('crypto');

const KEY_FILE = process.env.GEMINI_KEY_FILE || 'C:\\Users\\12566\\Downloads\\GEMINI_KEY.txt';
let API_KEY = process.env.GEMINI_API_KEY;
if (!API_KEY) {
  try { API_KEY = fs.readFileSync(KEY_FILE, 'utf8').trim(); }
  catch (e) { console.error(`FATAL: ${e.message}`); process.exit(2); }
}

const REPO_ROOT = path.resolve(__dirname, '..', '..');
const OUT_DIR = path.join(REPO_ROOT, 'image_assets', 'brimm', 'brand_kit', 'icons');
const MANIFEST_PATH = path.join(OUT_DIR, 'icons_v3_manifest.json');
const VIEWER_PATH = path.join(OUT_DIR, 'index_v3.html');
fs.mkdirSync(OUT_DIR, { recursive: true });

const ENDPOINTS = [
  'imagen-4.0-generate-001',
  'imagen-4.0-fast-generate-001',
];

const ICON_BASE = `App icon design, perfect square 1024x1024 composition, full-bleed background completely fills the frame edge-to-edge with no border, no margin, no shadow halo, no padding. The S and the liquid together fill approximately 80 percent of the frame area, perfectly centered. High edge contrast so the silhouette reads at thumbnail size. No text, no extra letters, no lorem ipsum. Photorealistic premium rendering quality.`;

const ICONS_V3 = [
  ['v3_pour_straight_down', `${ICON_BASE} Subject: a bold capital letter S in polished brass at the upper portion of the frame. From the bottom curve of the S, a continuous stream of golden amber liquid pours straight down, the stream tapering and widening like an actual fluid pour, ending in a small splash at the bottom edge of the frame. The liquid catches warm light. Solid charcoal black background fills the frame.`],

  ['v3_pour_trail_back', `${ICON_BASE} Subject: a bold capital letter S in polished brass with golden amber liquid trailing off the back spine of the letter, like the S was just rapidly stirred through a cocktail and is flinging droplets in motion behind it. Dynamic sense of movement, droplets and a streak of liquid arcing to the right of the S. Solid charcoal black background fills the frame.`],

  ['v3_pour_splash_ripple', `${ICON_BASE} Subject: a bold capital letter S in polished brass at the top half of the frame. From the bottom curve, golden amber liquid pours down and lands at the bottom of the frame creating concentric ripples and a small splash crown. The pool of liquid covers the bottom third of the frame. Solid charcoal black background fills the rest of the frame, ripples are tinted amber.`],

  ['v3_s_morphs_to_liquid', `${ICON_BASE} Subject: a single capital letter S where the top half is rendered as solid polished brass and the bottom half gradually morphs and dissolves into flowing golden amber liquid that pools at the base of the frame. The transition from solid metal to liquid is seamless, the bottom curve of the S literally becoming a wave of liquid. Solid charcoal black background fills the frame.`],

  ['v3_pour_into_mini_glass', `${ICON_BASE} Subject: a bold capital letter S in polished brass dominating the upper two-thirds of the frame. From the bottom curve, golden amber liquid pours down into a tiny minimal cocktail glass silhouette positioned at the very bottom edge of the frame, the glass barely larger than the pour itself. The S and pour stream are the focal point, the glass is a small accent. Solid charcoal black background fills the frame.`],
];

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
      try { return { buf: await postImagen(model, prompt), model }; }
      catch (e) {
        lastErr = e;
        const msg = String(e.message || '');
        if (/HTTP 404/.test(msg)) break;
        if (/HTTP 429/.test(msg) || /quota/i.test(msg) || /RESOURCE_EXHAUSTED/i.test(msg)) {
          attempt++;
          const backoff = Math.min(60_000, 15_000 * attempt);
          console.log(`    rate-limited, backing off ${backoff/1000}s...`);
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
      <img class="full" src="${it.slug}.png" loading="lazy"/>
      <div class="preview">
        <img class="s48" src="${it.slug}.png"/><span>48dp</span>
        <img class="s24" src="${it.slug}.png"/><span>24dp</span>
      </div>
      <div class="meta"><div class="slug">${it.slug.replace('v3_', '').replace(/_/g, ' ')}</div></div>
    </div>`).join('\n');
  fs.writeFileSync(VIEWER_PATH, `<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>Speakeater App Icon v3 — S with Liquid</title>
<style>
  body { background: #0D0D0E; color: #E8E3D9; font-family: -apple-system,sans-serif; margin: 0; padding: 32px; }
  h1 { color: #C9A554; font-weight: 300; letter-spacing: 4px; margin: 0 0 8px 0; }
  .sub { color: #8B8578; margin-bottom: 32px; font-size: 14px; max-width: 700px; }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 24px; max-width: 1400px; }
  .card { background: #18181B; border-radius: 12px; overflow: hidden; border: 1px solid #2a2a2e; }
  .full { width: 100%; display: block; aspect-ratio: 1/1; }
  .preview { display: flex; gap: 12px; align-items: center; padding: 12px 16px; background: #0a0a0c; }
  .preview .s48 { width: 48px; height: 48px; border-radius: 12px; }
  .preview .s24 { width: 24px; height: 24px; border-radius: 6px; }
  .preview span { color: #8B8578; font-size: 11px; }
  .meta { padding: 12px 16px; }
  .slug { color: #C9A554; font-weight: 600; font-size: 13px; text-transform: uppercase; letter-spacing: 1px; }
</style></head><body>
  <h1>SPEAKEATER · APP ICON v3 · S WITH LIQUID</h1>
  <div class="sub">5 variants of the S-with-liquid concept tuned for app icon. Each card shows 1024px master + 48dp + 24dp preview.</div>
  <div class="grid">${cards}</div>
</body></html>`);
}

(async () => {
  console.log(`Generating ${ICONS_V3.length} v3 (S+liquid) app icon candidates`);
  console.log(`Output: ${OUT_DIR}\n`);

  const manifest = fs.existsSync(MANIFEST_PATH)
    ? JSON.parse(fs.readFileSync(MANIFEST_PATH, 'utf8'))
    : { generated_at: new Date().toISOString(), model: ENDPOINTS[0], items: {} };

  const items = [];
  let okCount = 0, failCount = 0;
  const t0 = Date.now();

  for (let i = 0; i < ICONS_V3.length; i++) {
    const [slug, prompt] = ICONS_V3[i];
    const outPath = path.join(OUT_DIR, `${slug}.png`);
    items.push({ slug });
    if (fs.existsSync(outPath) && fs.statSync(outPath).size > 1024) {
      console.log(`[${i + 1}/${ICONS_V3.length}] SKIP ${slug}`);
      continue;
    }
    try {
      const { buf, model } = await generateOne(prompt);
      fs.writeFileSync(outPath, buf);
      manifest.items[slug] = { prompt, model, bytes: buf.length, sha: crypto.createHash('sha256').update(buf).digest('hex').slice(0, 16) };
      fs.writeFileSync(MANIFEST_PATH, JSON.stringify(manifest, null, 2));
      okCount++;
      console.log(`[${i + 1}/${ICONS_V3.length}] OK   ${slug} (${(buf.length / 1024).toFixed(0)}KB)`);
    } catch (e) {
      failCount++;
      console.error(`[${i + 1}/${ICONS_V3.length}] FAIL ${slug}: ${String(e.message).slice(0, 240)}`);
    }
    await sleep(12_000);
  }

  writeViewer(items);
  console.log(`\nDONE in ${((Date.now() - t0) / 1000).toFixed(1)}s — ok=${okCount} fail=${failCount}`);
  console.log(`Viewer: file:///${VIEWER_PATH.replace(/\\/g, '/')}`);
})().catch((e) => { console.error('FATAL:', e); process.exit(1); });

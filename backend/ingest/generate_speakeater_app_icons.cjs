#!/usr/bin/env node
/**
 * 5 Speakeater APP ICON candidates — S monograms tuned for tiny render sizes.
 * Each prompt enforces: full-bleed (no margin/border), single S dominates frame,
 * high edge contrast so the silhouette reads at 24dp on a phone home screen.
 *
 * Output: image_assets/brimm/brand_kit/icons/<slug>.png
 * (Folder renames to image_assets/pantrie/brand_kit/icons/ in NAMING.md Phase G.)
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
const MANIFEST_PATH = path.join(OUT_DIR, 'icons_manifest.json');
const VIEWER_PATH = path.join(OUT_DIR, 'index.html');
fs.mkdirSync(OUT_DIR, { recursive: true });

const ENDPOINTS = [
  'imagen-4.0-generate-001',
  'imagen-4.0-fast-generate-001',
];

// Common app-icon scaffold inherited by every prompt.
// Key constraints: full-bleed (no border, no margin, no shadow halo), single
// subject S fills 80%+ of square frame, high contrast silhouette, no text.
const ICON_BASE = `App icon design, perfect square 1024x1024 composition, full-bleed background completely fills the frame edge-to-edge with no border, no margin, no shadow halo, no padding. Single capital letter S is the focal subject and fills approximately 75 percent of the frame area, perfectly centered. Maximum edge contrast so the silhouette reads at thumbnail size. No text, no extra letters, no lorem ipsum, no decorative flourishes outside the S itself. Photorealistic premium rendering quality.`;

const ICONS = [
  ['icon_bold_brass', `${ICON_BASE} Subject: a bold geometric capital letter S in polished brass, raised three-dimensional with subtle bevel, sitting on a flat solid charcoal black background that fills the entire frame. The S has clean modern geometric construction, slightly tapered curves. Warm amber rim light from the upper left catches the brass surface.`],
  ['icon_embossed_plaque', `${ICON_BASE} Subject: a luxurious embossed brass nameplate filling the entire square frame as the background, with a single capital letter S deeply embossed into the brass surface in the center. The brass has visible brushed-metal texture, antique patina in the recesses, warm amber highlight along the top edge. Premium speakeasy door-plate aesthetic.`],
  ['icon_wax_seal', `${ICON_BASE} Subject: a thick circular wax seal in deep oxblood-red and antique gold wax that completely fills the square frame, with a capital letter S deeply pressed into the wax in the center. Visible drip texture around the perimeter, slight gold-leaf accents catching the warm light. The wax fills the entire frame, no background visible.`],
  ['icon_artdeco_burst', `${ICON_BASE} Subject: a capital letter S in brass at the center, with art deco geometric sun-burst rays in dark gold radiating outward from behind the S to fill the entire square frame. The rays form a complete background that touches every edge of the frame. 1920s prohibition-era geometric aesthetic, premium and bold.`],
  ['icon_glass_negspace', `${ICON_BASE} Subject: a capital letter S formed entirely by the negative space inside a stylized martini cocktail glass silhouette. The glass silhouette is rendered in solid brass and fills the square frame. The S shape is created by the empty interior of the glass and the stem curves. Clever visual pun where the S and the glass are the same shape. Full charcoal background.`],
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
      <img src="${it.slug}.png" loading="lazy"/>
      <div class="meta"><div class="slug">${it.slug.replace('icon_', '').replace(/_/g, ' ')}</div></div>
    </div>`).join('\n');
  fs.writeFileSync(VIEWER_PATH, `<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>Speakeater App Icon Candidates</title>
<style>
  body { background: #0D0D0E; color: #E8E3D9; font-family: -apple-system,sans-serif; margin: 0; padding: 32px; }
  h1 { color: #C9A554; font-weight: 300; letter-spacing: 4px; margin: 0 0 8px 0; }
  .sub { color: #8B8578; margin-bottom: 32px; font-size: 14px; }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 24px; max-width: 1200px; }
  .card { background: #18181B; border-radius: 12px; overflow: hidden; border: 1px solid #2a2a2e; }
  .card img { width: 100%; display: block; aspect-ratio: 1/1; }
  .meta { padding: 12px 16px; }
  .slug { color: #C9A554; font-weight: 600; font-size: 13px; text-transform: uppercase; letter-spacing: 1px; }
  .preview { display: flex; gap: 12px; align-items: center; padding: 8px 16px; background: #0a0a0c; }
  .preview img { aspect-ratio: 1/1; }
  .preview .s48 { width: 48px; height: 48px; border-radius: 12px; }
  .preview .s24 { width: 24px; height: 24px; border-radius: 6px; }
  .preview span { color: #8B8578; font-size: 11px; }
</style></head><body>
  <h1>SPEAKEATER · APP ICON CANDIDATES</h1>
  <div class="sub">5 S-monogram concepts tuned for tiny render. Each card shows 1024px master + 48dp + 24dp preview to test legibility at thumbnail.</div>
  <div class="grid">${cards.replace(/<\/div>\s*<\/div>$/gm, '</div>').replace(/(<img src="[^"]+\.png"[^>]*\/>)/g, (m, img) => `${img}<div class="preview"><img class="s48" src="${img.match(/src="([^"]+)"/)[1]}"/><span>48dp</span><img class="s24" src="${img.match(/src="([^"]+)"/)[1]}"/><span>24dp</span></div>`)}</div>
</body></html>`);
}

(async () => {
  console.log(`Generating ${ICONS.length} app icon candidates`);
  console.log(`Output: ${OUT_DIR}\n`);

  const manifest = fs.existsSync(MANIFEST_PATH)
    ? JSON.parse(fs.readFileSync(MANIFEST_PATH, 'utf8'))
    : { generated_at: new Date().toISOString(), model: ENDPOINTS[0], items: {} };

  const items = [];
  let okCount = 0, failCount = 0;
  const t0 = Date.now();

  for (let i = 0; i < ICONS.length; i++) {
    const [slug, prompt] = ICONS[i];
    const outPath = path.join(OUT_DIR, `${slug}.png`);
    items.push({ slug });
    if (fs.existsSync(outPath) && fs.statSync(outPath).size > 1024) {
      console.log(`[${i + 1}/${ICONS.length}] SKIP ${slug}`);
      continue;
    }
    try {
      const { buf, model } = await generateOne(prompt);
      fs.writeFileSync(outPath, buf);
      manifest.items[slug] = { prompt, model, bytes: buf.length, sha: crypto.createHash('sha256').update(buf).digest('hex').slice(0, 16) };
      fs.writeFileSync(MANIFEST_PATH, JSON.stringify(manifest, null, 2));
      okCount++;
      console.log(`[${i + 1}/${ICONS.length}] OK   ${slug} (${(buf.length / 1024).toFixed(0)}KB)`);
    } catch (e) {
      failCount++;
      console.error(`[${i + 1}/${ICONS.length}] FAIL ${slug}: ${String(e.message).slice(0, 240)}`);
    }
    await sleep(12_000);
  }

  writeViewer(items);
  console.log(`\nDONE in ${((Date.now() - t0) / 1000).toFixed(1)}s — ok=${okCount} fail=${failCount}`);
  console.log(`Viewer: file:///${VIEWER_PATH.replace(/\\/g, '/')}`);
})().catch((e) => { console.error('FATAL:', e); process.exit(1); });

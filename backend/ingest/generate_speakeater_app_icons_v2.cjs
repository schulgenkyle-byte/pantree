#!/usr/bin/env node
/**
 * Speakeater APP ICON v2 — kitchen + drinks variants. Every concept includes
 * a glass and/or chef's knife so the icon signals BOTH cooking AND cocktails
 * (the v1 set was S-only, user pushed back: "i need a glass or a knife or both").
 *
 * Output: image_assets/brimm/brand_kit/icons/v2_<slug>.png + viewer.
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
const MANIFEST_PATH = path.join(OUT_DIR, 'icons_v2_manifest.json');
const VIEWER_PATH = path.join(OUT_DIR, 'index_v2.html');
fs.mkdirSync(OUT_DIR, { recursive: true });

const ENDPOINTS = [
  'imagen-4.0-generate-001',
  'imagen-4.0-fast-generate-001',
];

const ICON_BASE = `App icon design, perfect square 1024x1024 composition, full-bleed background completely fills the frame edge-to-edge with no border, no margin, no shadow halo, no padding. Subject fills approximately 75 percent of the frame area, perfectly centered, high edge contrast so the silhouette reads at thumbnail size. No text, no extra letters, no lorem ipsum. Photorealistic premium rendering quality.`;

const ICONS_V2 = [
  ['v2_crest_knife_glass', `${ICON_BASE} Subject: a heraldic emblem composition with a brass capital letter S at the center, behind it a chef's knife and a martini cocktail glass crossed in an X pattern (knife angled up-right, glass stem angled up-left) like a coat of arms crest. All elements rendered in polished antique brass and warm gold. Solid charcoal black background fills the entire frame. Premium speakeasy + kitchen brand mark.`],

  ['v2_s_blade_to_glass', `${ICON_BASE} Subject: a single capital letter S where the upper curve is rendered as a sharp curved chef's knife blade catching warm amber light, and the lower curve flows naturally into the bowl of a martini cocktail glass with a tiny olive. The S, knife, and glass form one continuous brass silhouette. Solid charcoal black background fills the entire frame.`],

  ['v2_coupe_S_knife', `${ICON_BASE} Subject: a vintage coupe cocktail glass viewed straight-on filling most of the frame, rendered in polished brass. Inside the bowl of the coupe sits a capital letter S monogram in deep gold, with a small chef's knife laid horizontally across the rim of the glass like a garnish pick. Solid charcoal black background fills the entire frame.`],

  ['v2_knife_through_S', `${ICON_BASE} Subject: a bold geometric capital letter S in polished brass at the center of the frame, with a long chef's knife laid perfectly horizontal through the middle of the S, the blade catching warm amber light. The knife handle extends to the left edge, the blade tip to the right. Like a butcher's seal or a chef's signature mark. Solid charcoal background fills the entire frame.`],

  ['v2_glass_stem_S', `${ICON_BASE} Subject: a tall stemmed cocktail glass silhouette where the stem of the glass twists and curves into a perfect capital letter S shape. The bowl of the glass sits at the top with a single olive on a toothpick, the S-stem flows down to the base. A small chef's knife rests at the base of the glass. All in polished brass on solid charcoal black background that fills the entire frame.`],
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
      <div class="meta"><div class="slug">${it.slug.replace('v2_', '').replace(/_/g, ' ')}</div></div>
    </div>`).join('\n');
  fs.writeFileSync(VIEWER_PATH, `<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>Speakeater App Icon v2 — Kitchen + Drinks</title>
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
  <h1>SPEAKEATER · APP ICON v2 · KITCHEN + DRINKS</h1>
  <div class="sub">5 S-monogram concepts with knife + glass elements so the icon reads as both kitchen and bar. Each card shows 1024px master + 48dp + 24dp preview to test legibility at thumbnail size.</div>
  <div class="grid">${cards}</div>
</body></html>`);
}

(async () => {
  console.log(`Generating ${ICONS_V2.length} v2 app icon candidates`);
  console.log(`Output: ${OUT_DIR}\n`);

  const manifest = fs.existsSync(MANIFEST_PATH)
    ? JSON.parse(fs.readFileSync(MANIFEST_PATH, 'utf8'))
    : { generated_at: new Date().toISOString(), model: ENDPOINTS[0], items: {} };

  const items = [];
  let okCount = 0, failCount = 0;
  const t0 = Date.now();

  for (let i = 0; i < ICONS_V2.length; i++) {
    const [slug, prompt] = ICONS_V2[i];
    const outPath = path.join(OUT_DIR, `${slug}.png`);
    items.push({ slug });
    if (fs.existsSync(outPath) && fs.statSync(outPath).size > 1024) {
      console.log(`[${i + 1}/${ICONS_V2.length}] SKIP ${slug}`);
      continue;
    }
    try {
      const { buf, model } = await generateOne(prompt);
      fs.writeFileSync(outPath, buf);
      manifest.items[slug] = { prompt, model, bytes: buf.length, sha: crypto.createHash('sha256').update(buf).digest('hex').slice(0, 16) };
      fs.writeFileSync(MANIFEST_PATH, JSON.stringify(manifest, null, 2));
      okCount++;
      console.log(`[${i + 1}/${ICONS_V2.length}] OK   ${slug} (${(buf.length / 1024).toFixed(0)}KB)`);
    } catch (e) {
      failCount++;
      console.error(`[${i + 1}/${ICONS_V2.length}] FAIL ${slug}: ${String(e.message).slice(0, 240)}`);
    }
    await sleep(12_000);
  }

  writeViewer(items);
  console.log(`\nDONE in ${((Date.now() - t0) / 1000).toFixed(1)}s — ok=${okCount} fail=${failCount}`);
  console.log(`Viewer: file:///${VIEWER_PATH.replace(/\\/g, '/')}`);
})().catch((e) => { console.error('FATAL:', e); process.exit(1); });

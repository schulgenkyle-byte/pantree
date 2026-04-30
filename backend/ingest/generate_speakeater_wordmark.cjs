#!/usr/bin/env node
/**
 * Speakeater WORDMARK — crossword-style intersection layout.
 * SPEAK reads top-to-bottom vertically.
 * EATER reads left-to-right horizontally, sharing the E with SPEAK.
 *
 *   S
 *   P
 *   E A T E R
 *   A
 *   K
 *
 * The shared E is the pivot. Genius wordmark concept from the user.
 *
 * 5 typographic treatments. Aspect 4:3 (wider than tall) to give the
 * horizontal EATER extension room to breathe.
 *
 * Output: image_assets/brimm/brand_kit/wordmark/<slug>.png + viewer.
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
const OUT_DIR = path.join(REPO_ROOT, 'image_assets', 'brimm', 'brand_kit', 'wordmark');
const MANIFEST_PATH = path.join(OUT_DIR, 'wordmark_manifest.json');
const VIEWER_PATH = path.join(OUT_DIR, 'index.html');
fs.mkdirSync(OUT_DIR, { recursive: true });

const ENDPOINTS = [
  'imagen-4.0-generate-001',
  'imagen-4.0-fast-generate-001',
];

// Imagen struggles with multi-letter precision. Be VERY explicit about the layout.
const LAYOUT_INSTRUCTION = `
The wordmark uses a crossword-style intersection layout:
- The word SPEAK is stacked vertically, one letter per line, top to bottom: S, P, E, A, K (5 letters total stacked vertically, all uppercase, large prominent letters).
- The word EATER reads horizontally left-to-right, starting from the E in SPEAK and extending to the right with the letters A, T, E, R.
- The letter E is shared between SPEAK (vertical, 3rd letter) and EATER (horizontal, 1st letter). It is the pivot point of the cross.
- The result spells SPEAKEATER as a connected wordmark.
- All letters are perfectly legible and well-kerned.
- No extra letters, no decorative flourishes outside the letterforms, no lorem ipsum.`;

const FRAME = `Square 1024x1024 composition. The full crossword layout (vertical SPEAK + horizontal EATER) is centered in the frame and fills approximately 70 percent of the canvas with comfortable breathing room. Background fills the entire frame edge-to-edge with no border. Photorealistic premium rendering.`;

const WORDMARKS = [
  ['wordmark_brass_plaque', `Wordmark logo design. ${LAYOUT_INSTRUCTION} STYLE: All letters embossed in polished antique brass with subtle three-dimensional bevel. Background is solid charcoal black velvet (#0D0D0E). Warm amber rim lighting catches the brass surfaces. Premium speakeasy nameplate aesthetic. ${FRAME}`],

  ['wordmark_neon_sign', `Wordmark logo design. ${LAYOUT_INSTRUCTION} STYLE: All letters rendered in glowing amber-orange vintage neon tubing, each letter a distinct neon tube with subtle glow halo. Mounted on a dark brick wall background with slight ambient amber glow. Vintage speakeasy neon sign aesthetic. ${FRAME}`],

  ['wordmark_letterpress', `Wordmark logo design. ${LAYOUT_INSTRUCTION} STYLE: Each letter deeply pressed (debossed) into thick cream cotton paper using gold foil ink, vintage letterpress technique. Visible paper texture and the deboss impression around each letter. The pressed paper sits on a dark wood surface with dramatic side lighting. ${FRAME}`],

  ['wordmark_artdeco_geometric', `Wordmark logo design. ${LAYOUT_INSTRUCTION} STYLE: All letters set in a tall geometric art deco sans-serif typeface with sharp corners and precise lines, rendered in matte brass on a flat dark charcoal background. Clean, modern, 1920s prohibition-era geometry. ${FRAME}`],

  ['wordmark_ink_stamp', `Wordmark logo design. ${LAYOUT_INSTRUCTION} STYLE: Each letter rendered as if stamped in deep black ink onto aged cream parchment paper, with slight ink bleed and imperfect impression characteristic of a vintage rubber stamp. The parchment fills the entire frame with subtle aged texture. Vintage speakeasy bar tab aesthetic. ${FRAME}`],
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
      <div class="meta"><div class="slug">${it.slug.replace('wordmark_', '').replace(/_/g, ' ')}</div></div>
    </div>`).join('\n');
  fs.writeFileSync(VIEWER_PATH, `<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>Speakeater Wordmark — SPEAK/EATER Cross Layout</title>
<style>
  body { background: #0D0D0E; color: #E8E3D9; font-family: -apple-system,sans-serif; margin: 0; padding: 32px; }
  h1 { color: #C9A554; font-weight: 300; letter-spacing: 4px; margin: 0 0 8px 0; }
  .sub { color: #8B8578; margin-bottom: 32px; font-size: 14px; max-width: 700px; }
  pre.layout { background: #18181B; padding: 16px; border-radius: 8px; color: #C9A554; font-size: 14px; line-height: 1.4; max-width: 240px; }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)); gap: 24px; max-width: 1400px; margin-top: 32px; }
  .card { background: #18181B; border-radius: 12px; overflow: hidden; border: 1px solid #2a2a2e; }
  .card img { width: 100%; display: block; aspect-ratio: 1/1; }
  .meta { padding: 12px 16px; }
  .slug { color: #C9A554; font-weight: 600; font-size: 13px; text-transform: uppercase; letter-spacing: 1px; }
  .warn { background: #2a1d10; border-left: 3px solid #C9A554; padding: 12px 16px; margin: 16px 0 32px 0; color: #E8E3D9; font-size: 13px; max-width: 700px; }
</style></head><body>
  <h1>SPEAKEATER · WORDMARK · CROSS LAYOUT</h1>
  <div class="sub">Crossword-style intersection. SPEAK reads vertical, EATER reads horizontal, both sharing the E.</div>
  <pre class="layout">S
P
E A T E R
A
K</pre>
  <div class="warn">⚠ Imagen sometimes garbles or misspells multi-letter compositions. Whichever variant you like will get cleaned up + re-created as a real vector before shipping.</div>
  <div class="grid">${cards}</div>
</body></html>`);
}

(async () => {
  console.log(`Generating ${WORDMARKS.length} SPEAK/EATER cross-layout wordmarks`);
  console.log(`Output: ${OUT_DIR}\n`);

  const manifest = fs.existsSync(MANIFEST_PATH)
    ? JSON.parse(fs.readFileSync(MANIFEST_PATH, 'utf8'))
    : { generated_at: new Date().toISOString(), model: ENDPOINTS[0], items: {} };

  const items = [];
  let okCount = 0, failCount = 0;
  const t0 = Date.now();

  for (let i = 0; i < WORDMARKS.length; i++) {
    const [slug, prompt] = WORDMARKS[i];
    const outPath = path.join(OUT_DIR, `${slug}.png`);
    items.push({ slug });
    if (fs.existsSync(outPath) && fs.statSync(outPath).size > 1024) {
      console.log(`[${i + 1}/${WORDMARKS.length}] SKIP ${slug}`);
      continue;
    }
    try {
      const { buf, model } = await generateOne(prompt);
      fs.writeFileSync(outPath, buf);
      manifest.items[slug] = { prompt, model, bytes: buf.length, sha: crypto.createHash('sha256').update(buf).digest('hex').slice(0, 16) };
      fs.writeFileSync(MANIFEST_PATH, JSON.stringify(manifest, null, 2));
      okCount++;
      console.log(`[${i + 1}/${WORDMARKS.length}] OK   ${slug} (${(buf.length / 1024).toFixed(0)}KB)`);
    } catch (e) {
      failCount++;
      console.error(`[${i + 1}/${WORDMARKS.length}] FAIL ${slug}: ${String(e.message).slice(0, 240)}`);
    }
    await sleep(12_000);
  }

  writeViewer(items);
  console.log(`\nDONE in ${((Date.now() - t0) / 1000).toFixed(1)}s — ok=${okCount} fail=${failCount}`);
  console.log(`Viewer: file:///${VIEWER_PATH.replace(/\\/g, '/')}`);
})().catch((e) => { console.error('FATAL:', e); process.exit(1); });

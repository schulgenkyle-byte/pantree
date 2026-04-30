#!/usr/bin/env node
/**
 * Speakeater DOOR ASSETS generator — cinematic photoreal speakeasy door
 * compositions for speakeater.com hero / opening / closer.
 *
 * Mirrors the brand_kit pattern (Imagen 4 Ultra, resume-safe, retry-on-429).
 * Aspect ratios picked to match how each shot will be used on the site.
 *
 * RUN:  node backend/ingest/generate_speakeater_door_assets.cjs
 * OUT:  C:/Users/12566/projects/speakeater-site/door-assets/<slug>.png
 *       Plus door_assets_manifest.json + index.html viewer in same folder.
 *
 * After it finishes, the site picks them up automatically — index.html is
 * already wired to <slug>.png filenames listed below.
 */
const fs = require('fs');
const path = require('path');
const https = require('https');
const crypto = require('crypto');

const KEY_FILE = process.env.GEMINI_KEY_FILE || 'C:\\Users\\12566\\Downloads\\GEMINI_KEY.txt';
let API_KEY = process.env.GEMINI_API_KEY;
if (!API_KEY) {
  try { API_KEY = fs.readFileSync(KEY_FILE, 'utf8').trim(); }
  catch { console.error(`FATAL: key file unreadable: ${KEY_FILE}`); process.exit(2); }
}

const OUT_DIR = 'C:\\Users\\12566\\projects\\speakeater-site\\door-assets';
const MANIFEST_PATH = path.join(OUT_DIR, 'door_assets_manifest.json');
const VIEWER_PATH = path.join(OUT_DIR, 'index.html');
fs.mkdirSync(OUT_DIR, { recursive: true });

const ENDPOINTS = [
  'imagen-4.0-generate-001',
  'imagen-4.0-fast-generate-001',
];

// Shared cinematography rules so every shot belongs to the same scene.
const SCENE = `
Cinematic photorealistic rendering, shot on a full-frame camera with a 35mm prime lens.
Single grand vintage 1920s speakeasy double-door at the end of a brick-lined narrow alley.
The door is heavy walnut wood with deep grain texture, near-black stain, two leaves with a
center seam. Hand-forged iron strap hinges with ornate spear-tip terminals run horizontally
across both leaves at top, middle, and bottom. Riveted clavos studs encircle the perimeter.
A heavy brass ring knocker is mounted at face height on the left leaf. A small brass
peephole at eye level glows with warm amber light from inside. The door sits inside an
arched stone surround with a brass keystone at the apex. The setting is night, deep ambient
darkness, single warm tungsten sconce above the door casts dramatic chiaroscuro side light.
Volumetric haze, fine atmospheric dust catching the light. Brass and warm amber tones
(#C9A554, #d4a04a) are the only saturated colors against the otherwise monochrome charcoal
scene (#0a0807 base). No text artifacts, no signage, no extra letters anywhere on the door.
No people. Symmetric composition. Movie-quality grade with deep shadow lift and highlight
roll-off. ARRI Alexa Mini look.
`.trim().replace(/\s+/g, ' ');

// The 8 cinematic shots the site needs.
const SHOTS = [
  // [slug, aspectRatio, prompt]
  [
    'hero-closed',
    '3:4',
    `${SCENE} The doors are CLOSED tight. Camera centered, dead-on architectural symmetry,
    shot from a slightly low hero angle so the door feels imposing. The peephole glows
    warm. The wordmark "SPEAKEATER" is etched in brass running vertically down the center
    seam (S-P-E-A-K from top to middle), with "EATER" extending horizontally to the right
    from the shared E. Letters are stencil-cut and glow softly from within. The frame
    fills most of the image, slight room around it shows the brick alley walls and stone
    surround. This is the establishing hero shot.`
  ],
  [
    'hero-isolated',
    '3:4',
    `${SCENE} The doors are CLOSED. Same composition as the establishing hero shot but
    isolated against pure black background, no alley, no surround, only the door itself
    floating in deep darkness so it can be composited cleanly. Sharp edges around the
    door's outline for clean masking. Subtle ground shadow beneath. Brass peephole glow.
    Wordmark stencil same as established.`
  ],
  [
    'leaf-left',
    '4:5',
    `${SCENE} ONE leaf only — the LEFT half of the speakeasy double-door — shown swung
    open about 30 degrees inward. Camera at three-quarter angle so we see both the
    front face of the leaf (with iron strapwork, brass knocker ring, half of the
    SPEAKEATER stencil running down the inside edge) and a sliver of the leaf's edge
    showing thickness. Isolated against pure black background for compositing. The leaf
    casts a long warm shadow rightward as if light spills past it from a room beyond.`
  ],
  [
    'leaf-right',
    '4:5',
    `${SCENE} ONE leaf only — the RIGHT half of the speakeasy double-door — shown swung
    open about 30 degrees outward. Camera at three-quarter angle so we see both the
    front face of the leaf (with iron strapwork, half of the SPEAKEATER stencil running
    down the inside edge with the EATER horizontal arm visible) and a sliver of the
    leaf's edge showing thickness. Isolated against pure black background for
    compositing. Warm shadow falls leftward.`
  ],
  [
    'interior-warm',
    '3:4',
    `${SCENE} What you see THROUGH the open speakeasy doorway, no door visible, just the
    warm interior of the bar beyond. A long bar with rows of vintage liquor bottles
    catching warm tungsten light, antique brass shelving, leather banquette seats in the
    distance, single Edison-bulb pendant lights hanging on chains. Deep amber and gold
    palette, all warm color temperature. Volumetric haze, slight motion blur in the very
    background suggesting people. Shot from the perspective of someone standing in the
    doorway looking in. This image is the warm room you see when the doors swing apart.`
  ],
  [
    'closer-open',
    '16:9',
    `${SCENE} The doors are WIDE OPEN, both leaves swung outward toward the camera.
    Camera centered, dead-on, looking through the open arched threshold into the warm
    speakeasy bar interior beyond. The opened door leaves are visible at left and right
    edges of frame at three-quarter angle. The arched stone surround frames the entire
    composition. Warm tungsten light from inside spills across the threshold floor in a
    triangular wedge. Behind the threshold: a long brass-lit bar with bottles, leather
    banquettes, a single bartender silhouetted at the far end. This is the final
    cinematic reveal shot.`
  ],
  [
    'peephole-glow',
    '1:1',
    `${SCENE} EXTREME CLOSE-UP of just the brass speakeasy peephole on the closed door.
    The peephole is a circular brass-rimmed opening about three inches across, with a
    small swing-cover currently open showing warm amber light glowing from within.
    Sharp brass texture with patina, surrounding wood grain visible at edges of frame.
    The peephole occupies the center 60% of the image. Shallow depth of field, dramatic
    rim light. This is the texture asset for the pulsing peephole layer.`
  ],
  [
    'light-rays',
    '3:4',
    `${SCENE} Volumetric god-rays of warm tungsten light streaming diagonally through
    atmospheric haze and dust, on a pure black background. No door, no walls, just the
    light shafts themselves as if photographed in a room with a single window. Multiple
    overlapping rays at slightly different angles. This is an overlay asset to composite
    on top of the door scene during the door-opening reveal.`
  ],
];

function postImagen(modelId, prompt, aspectRatio) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({
      instances: [{ prompt }],
      parameters: { sampleCount: 1, aspectRatio, safetySetting: 'block_low_and_above' },
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

async function generateOne(prompt, aspectRatio) {
  let lastErr = null;
  for (const model of ENDPOINTS) {
    let attempt = 0;
    while (attempt < 5) {
      try {
        const buf = await postImagen(model, prompt, aspectRatio);
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
        <div class="cat">${it.aspect}</div>
      </div>
    </div>`).join('\n');
  const html = `<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>Speakeater Door Assets</title>
<style>
  body { background: #020203; color: #f4ecd9; font-family: -apple-system,sans-serif; margin: 0; padding: 32px; }
  h1 { color: #d4a04a; font-weight: 300; letter-spacing: 4px; margin: 0 0 8px 0; }
  .sub { color: rgba(244,236,217,0.6); margin-bottom: 32px; font-size: 14px; }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 24px; }
  .card { background: #0a0807; border: 1px solid rgba(161,98,7,0.2); overflow: hidden; }
  .card img { width: 100%; display: block; }
  .meta { padding: 14px 18px; }
  .slug { color: #d4a04a; font-weight: 600; font-size: 14px; }
  .cat { color: rgba(244,236,217,0.4); font-size: 11px; text-transform: uppercase; letter-spacing: 2px; margin-top: 2px; }
</style></head>
<body>
  <h1>SPEAKEATER · DOOR ASSETS</h1>
  <div class="sub">${items.length} cinematic door shots from Imagen 4 Ultra. Used by speakeater.com hero, opening sticky-scroll, and closer.</div>
  <div class="grid">${cards}</div>
</body></html>`;
  fs.writeFileSync(VIEWER_PATH, html);
}

(async () => {
  console.log(`Generating ${SHOTS.length} cinematic door shots via Imagen 4 Ultra`);
  console.log(`Output: ${OUT_DIR}\n`);

  const manifest = fs.existsSync(MANIFEST_PATH)
    ? JSON.parse(fs.readFileSync(MANIFEST_PATH, 'utf8'))
    : { generated_at: new Date().toISOString(), model: ENDPOINTS[0], items: {} };

  const items = [];
  let okCount = 0, failCount = 0, skippedCount = 0;
  const t0 = Date.now();

  for (let i = 0; i < SHOTS.length; i++) {
    const [slug, aspect, prompt] = SHOTS[i];
    const outPath = path.join(OUT_DIR, `${slug}.png`);
    items.push({ slug, aspect });

    if (fs.existsSync(outPath) && fs.statSync(outPath).size > 1024) {
      skippedCount++;
      console.log(`[${i + 1}/${SHOTS.length}] SKIP ${slug}`);
      continue;
    }

    try {
      console.log(`[${i + 1}/${SHOTS.length}] ${slug} (${aspect}) ...`);
      const { buf, model } = await generateOne(prompt, aspect);
      fs.writeFileSync(outPath, buf);
      const sha = crypto.createHash('sha256').update(buf).digest('hex').slice(0, 16);
      manifest.items[slug] = { aspect, prompt, model, bytes: buf.length, sha };
      fs.writeFileSync(MANIFEST_PATH, JSON.stringify(manifest, null, 2));
      okCount++;
      console.log(`         OK   (${(buf.length / 1024).toFixed(0)}KB)`);
    } catch (e) {
      failCount++;
      console.error(`         FAIL: ${String(e.message).slice(0, 240)}`);
    }
    await sleep(12_000); // ~5 RPM
  }

  writeViewer(items);
  const elapsed = ((Date.now() - t0) / 1000).toFixed(1);
  console.log(`\nDONE in ${elapsed}s — ok=${okCount} fail=${failCount} skipped=${skippedCount}`);
  console.log(`Viewer: file:///${VIEWER_PATH.replace(/\\/g, '/')}`);
})().catch((e) => { console.error('FATAL:', e); process.exit(1); });

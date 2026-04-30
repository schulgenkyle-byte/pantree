#!/usr/bin/env node
/**
 * Speakeater DOOR VIDEO generator — short cinematic clip of the speakeasy
 * doors opening, via Veo 3 (or Veo 2 fallback).
 *
 * One clip per run, auto-numbered. Re-run to iterate.
 *
 * USAGE:
 *   node backend/ingest/generate_speakeater_door_video.cjs
 *   node backend/ingest/generate_speakeater_door_video.cjs --variant noir
 *   node backend/ingest/generate_speakeater_door_video.cjs --prompt "your prompt..."
 *
 * The script picks the next available number (door-open-01.mp4, -02, -03 ...)
 * and writes a viewer at door-assets/clips.html so you can flip through all
 * attempts and pick the one to ship.
 */
const fs = require('fs');
const path = require('path');
const https = require('https');

const KEY_FILE = process.env.GEMINI_KEY_FILE || 'C:\\Users\\12566\\Downloads\\GEMINI_KEY.txt';
let API_KEY = process.env.GEMINI_API_KEY;
if (!API_KEY) {
  try { API_KEY = fs.readFileSync(KEY_FILE, 'utf8').trim(); }
  catch { console.error(`FATAL: key file unreadable: ${KEY_FILE}`); process.exit(2); }
}

const OUT_DIR = 'C:\\Users\\12566\\projects\\speakeater-site\\door-assets';
fs.mkdirSync(OUT_DIR, { recursive: true });

// ---------------- ARG PARSING ----------------
const args = process.argv.slice(2);
function arg(flag) {
  const i = args.indexOf(flag);
  return i >= 0 ? args[i + 1] : null;
}
const variantName = arg('--variant') || 'cinematic';
const promptOverride = arg('--prompt');

const MODELS = [
  'veo-3.0-generate-001',
  'veo-3.0-fast-generate-001',
  'veo-2.0-generate-001',
];

// ---------------- PROMPT VARIANTS ----------------
// Add more variants here as you iterate. Pass --variant <name> to use one.
const VARIANTS = {
  cinematic: `
    Cinematic photorealistic 8-second clip, locked-off camera, 9:16 vertical aspect.
    A grand vintage 1920s speakeasy double-door at the end of a brick alley at night.
    The door is heavy walnut wood with deep grain, near-black stain, two leaves with a
    center seam. Hand-forged iron strap hinges with ornate spear-tip terminals run
    horizontally across both leaves. Riveted clavos studs around the perimeter. A heavy
    brass ring knocker on the left leaf. A small brass peephole at eye level glows warm
    amber from inside. Arched stone surround with a brass keystone at the apex. Single
    warm tungsten sconce above casts dramatic chiaroscuro side light. Volumetric haze,
    fine atmospheric dust catching the light.

    Frame 0-1s: doors closed, peephole pulses gently, dust drifts.
    Frame 1-3s: an audible click, both leaves begin to swing slowly outward toward camera.
    Frame 3-6s: doors continue opening, warm tungsten light spills across the threshold,
    bottle silhouettes and a glowing speakeasy bar interior reveal behind the leaves,
    volumetric god-rays of warm light beam outward through the haze.
    Frame 6-8s: doors held wide open, camera holds steady on the warm interior reveal,
    embers of dust drift through the light beams. Tasteful slow-motion feel.

    Movie-quality color grade. Brass and warm amber are the only saturated colors against
    otherwise monochrome charcoal scene. ARRI Alexa Mini look. Deep shadow lift, smooth
    highlight roll-off. No text, no signage, no people in foreground. Symmetric composition.
  `,
  noir: `
    Black-and-white film noir 8-second clip, locked-off camera, 9:16 vertical aspect.
    A 1920s speakeasy double-door at the end of a wet brick alley at night, rain dripping
    from a single overhead lamp. Heavy iron strapwork, brass peephole glowing as a single
    warm point of light against the silver-grey monochrome. The doors swing slowly open
    over 5 seconds, revealing a smoke-filled interior with a warm sliver of amber light.
    Only the peephole and the interior reveal carry color; everything else is high-contrast
    monochrome. 1940s film stock grain, hard shadows, atmospheric haze. No people in foreground,
    no text, no signage.
  `,
  warm: `
    Cinematic 8-second clip, 9:16 vertical, warm-biased color grade. A 1920s speakeasy
    double-door inside a candlelit alcove. The walls are warm brick lit by a flickering
    sconce. The door is dark walnut with brass strapwork and clavos studs, brass peephole
    glowing intensely. Doors swing slowly outward over 5 seconds, the interior beyond is a
    riot of golden light, bottles glittering on brass shelves, leather banquettes in deep
    amber. Heavy color grade biased toward warm tungsten. Visible film grain. Slow zen
    motion. No text, no signage, no people in foreground.
  `,
  closeup: `
    Cinematic 8-second clip, 9:16 vertical, EXTREME CLOSE-UP at the level of the brass
    peephole. The peephole is centered in frame, a small brass-rimmed circular opening on a
    near-black walnut door. Frame 0-2s: peephole closed, brass cover. Frame 2-4s: peephole
    cover slides open from inside, warm amber light spills toward camera. Frame 4-8s: a hint
    of an eye behind the peephole, then it cuts to a wider view of the doors swinging open
    revealing a warm tungsten-lit speakeasy interior. Brass and amber against deep charcoal.
    Atmospheric haze. No text, no signage.
  `,
  pov: `
    Cinematic 8-second clip, 9:16 vertical, first-person POV walking up to a 1920s
    speakeasy door. Frame 0-2s: approaching the door from the alley, peephole glowing.
    Frame 2-4s: a knock — three soft raps. Frame 4-6s: the peephole cover slides, then the
    doors begin to swing inward. Frame 6-8s: stepping through into the warm-lit speakeasy
    interior, vintage liquor bottles in focus, brass shelves catching tungsten light.
    Subtle handheld camera motion. Heavy film grain. Brass and amber tones, otherwise
    monochrome charcoal. ARRI Alexa look. No text, no signage, no people in foreground.
  `,
};

let basePrompt = VARIANTS[variantName] || VARIANTS.cinematic;
if (!VARIANTS[variantName] && variantName !== 'cinematic') {
  console.error(`Unknown variant "${variantName}". Available: ${Object.keys(VARIANTS).join(', ')}`);
  process.exit(2);
}
const PROMPT = (promptOverride || basePrompt).trim().replace(/\s+/g, ' ');

// ---------------- AUTO-NUMBERED OUTPUT ----------------
function nextOutputPath() {
  const existing = fs.readdirSync(OUT_DIR)
    .map(f => /^door-open-(\d{2,3})\.mp4$/.exec(f))
    .filter(Boolean)
    .map(m => parseInt(m[1], 10));
  const next = (existing.length ? Math.max(...existing) : 0) + 1;
  const num = String(next).padStart(2, '0');
  return { path: path.join(OUT_DIR, `door-open-${num}.mp4`), num };
}

// ---------------- HTTP PLUMBING ----------------
function postJson(host, urlPath, body) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify(body);
    const req = https.request({
      method: 'POST', hostname: host, path: urlPath,
      headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) },
    }, (res) => {
      let out = '';
      res.on('data', (c) => { out += c; });
      res.on('end', () => {
        if (res.statusCode < 200 || res.statusCode >= 300) {
          return reject(new Error(`HTTP ${res.statusCode}: ${out.slice(0, 500)}`));
        }
        try { resolve(JSON.parse(out)); } catch (e) { reject(e); }
      });
    });
    req.on('error', reject);
    req.write(data); req.end();
  });
}

function getJson(host, urlPath) {
  return new Promise((resolve, reject) => {
    https.get({ hostname: host, path: urlPath }, (res) => {
      let out = '';
      res.on('data', (c) => { out += c; });
      res.on('end', () => {
        if (res.statusCode < 200 || res.statusCode >= 300) {
          return reject(new Error(`HTTP ${res.statusCode}: ${out.slice(0, 500)}`));
        }
        try { resolve(JSON.parse(out)); } catch (e) { reject(e); }
      });
    }).on('error', reject);
  });
}

function downloadTo(url, outPath) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    https.get({
      hostname: u.hostname,
      path: u.pathname + u.search,
      headers: { 'x-goog-api-key': API_KEY },
    }, (res) => {
      if (res.statusCode === 302 || res.statusCode === 301) {
        return downloadTo(res.headers.location, outPath).then(resolve, reject);
      }
      if (res.statusCode !== 200) return reject(new Error(`HTTP ${res.statusCode}`));
      const file = fs.createWriteStream(outPath);
      res.pipe(file);
      file.on('finish', () => file.close(() => resolve()));
    }).on('error', reject);
  });
}

const sleep = (ms) => new Promise(r => setTimeout(r, ms));

async function generateVideo(modelId, outPath) {
  console.log(`[veo] starting generation with ${modelId}...`);
  const startResp = await postJson(
    'generativelanguage.googleapis.com',
    `/v1beta/models/${modelId}:predictLongRunning?key=${API_KEY}`,
    {
      instances: [{ prompt: PROMPT }],
      parameters: {
        aspectRatio: '9:16',
        durationSeconds: 8,
        personGeneration: 'dont_allow',
      },
    }
  );

  const opName = startResp.name;
  if (!opName) throw new Error(`No operation name returned: ${JSON.stringify(startResp).slice(0, 400)}`);
  console.log(`[veo] operation ${opName} — polling every 10s (Veo takes 1-3 min)...`);

  for (let i = 0; i < 30; i++) {
    await sleep(10_000);
    const status = await getJson('generativelanguage.googleapis.com', `/v1beta/${opName}?key=${API_KEY}`);
    if (status.done) {
      const videos = status.response?.generatedVideos
        || status.response?.generateVideoResponse?.generatedSamples
        || [];
      if (!videos.length) throw new Error(`Done but no video: ${JSON.stringify(status).slice(0, 500)}`);
      const videoUri = videos[0]?.video?.uri || videos[0]?.uri;
      if (!videoUri) throw new Error(`No video uri: ${JSON.stringify(videos[0]).slice(0, 500)}`);
      console.log(`[veo] complete — downloading ...`);
      const finalUrl = videoUri.includes('?') ? `${videoUri}&key=${API_KEY}` : `${videoUri}?key=${API_KEY}`;
      await downloadTo(finalUrl, outPath);
      return;
    }
    process.stdout.write('.');
  }
  throw new Error('Polling timed out after 5 min');
}

function writeViewer() {
  const viewerPath = path.join(OUT_DIR, 'clips.html');
  const clips = fs.readdirSync(OUT_DIR)
    .filter(f => /^door-open-\d{2,3}\.mp4$/.test(f))
    .sort();
  const meta = (() => {
    try { return JSON.parse(fs.readFileSync(path.join(OUT_DIR, 'clips_meta.json'), 'utf8')); }
    catch { return {}; }
  })();
  const cards = clips.map(fn => {
    const m = meta[fn] || {};
    return `
    <div class="card">
      <video src="${fn}" controls loop muted playsinline preload="metadata"></video>
      <div class="meta">
        <div class="slug">${fn}</div>
        <div class="cat">${m.variant || '?'} · ${m.model || '?'} · ${m.bytes ? (m.bytes/1024/1024).toFixed(1) + ' MB' : ''}</div>
        <div class="prompt">${(m.prompt || '').slice(0, 200)}${(m.prompt || '').length > 200 ? '…' : ''}</div>
      </div>
    </div>`;
  }).join('\n');
  const html = `<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>Speakeater Door Clips</title>
<style>
  body { background:#020203; color:#f4ecd9; font-family:-apple-system,sans-serif; margin:0; padding:32px; }
  h1 { color:#d4a04a; font-weight:300; letter-spacing:4px; margin:0 0 8px; }
  .sub { color:rgba(244,236,217,0.6); margin-bottom:32px; font-size:14px; }
  .grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(360px, 1fr)); gap:24px; }
  .card { background:#0a0807; border:1px solid rgba(161,98,7,0.2); overflow:hidden; }
  .card video { width:100%; display:block; aspect-ratio:9/16; object-fit:cover; background:#000; }
  .meta { padding:14px 18px; }
  .slug { color:#d4a04a; font-weight:600; font-size:14px; }
  .cat { color:rgba(244,236,217,0.4); font-size:11px; text-transform:uppercase; letter-spacing:2px; margin:4px 0 10px; }
  .prompt { color:rgba(244,236,217,0.65); font-size:12px; line-height:1.5; }
</style></head>
<body>
  <h1>SPEAKEATER · DOOR CLIPS</h1>
  <div class="sub">${clips.length} attempt${clips.length === 1 ? '' : 's'}. Pick a winner. Tell Claude the filename to ship.</div>
  <div class="grid">${cards}</div>
</body></html>`;
  fs.writeFileSync(viewerPath, html);
  return viewerPath;
}

(async () => {
  const { path: outPath, num } = nextOutputPath();
  console.log(`Generating clip #${num} (variant=${variantName})`);
  console.log(`Output: ${outPath}\n`);

  let lastErr;
  let modelUsed = null;
  for (const model of MODELS) {
    try {
      await generateVideo(model, outPath);
      modelUsed = model;
      const sz = (fs.statSync(outPath).size / 1024 / 1024).toFixed(2);
      console.log(`\n[veo] DONE — ${path.basename(outPath)} (${sz} MB)`);
      break;
    } catch (e) {
      lastErr = e;
      const m = String(e.message);
      console.error(`[veo] ${model} failed: ${m.slice(0, 240)}`);
      if (/HTTP 404/.test(m) || /HTTP 403/.test(m) || /not.*available/i.test(m) || /quota/i.test(m) || /HTTP 429/.test(m)) continue;
      throw e;
    }
  }
  if (!modelUsed) throw lastErr || new Error('All Veo models failed');

  // record metadata
  const metaPath = path.join(OUT_DIR, 'clips_meta.json');
  const meta = (() => { try { return JSON.parse(fs.readFileSync(metaPath, 'utf8')); } catch { return {}; } })();
  meta[path.basename(outPath)] = {
    variant: variantName,
    model: modelUsed,
    prompt: PROMPT,
    bytes: fs.statSync(outPath).size,
    generated_at: new Date().toISOString(),
  };
  fs.writeFileSync(metaPath, JSON.stringify(meta, null, 2));
  const viewerPath = writeViewer();
  console.log(`\nViewer: file:///${viewerPath.replace(/\\/g, '/')}`);
  console.log(`Local:  http://localhost:5003/door-assets/${path.basename(outPath)}`);
  console.log(`\nNext run: node backend/ingest/generate_speakeater_door_video.cjs [--variant noir|warm|closeup|pov]`);
})().catch((e) => { console.error('FATAL:', e.message); process.exit(1); });

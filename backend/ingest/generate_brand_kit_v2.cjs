// Brand kit v2: 16 NEW concepts beyond what's in brand_kit_manifest.json.
// Filling gaps in glass types, monogram styles, wordmark treatments, icon variants.
//
// Falls through Standard → Fast on daily-quota 429. Run with:
//   node backend/ingest/generate_brand_kit_v2.cjs
//
// Skips slugs already present in brand_kit_manifest.json so re-runs are idempotent.

const fs = require('fs');
const path = require('path');
const https = require('https');
const crypto = require('crypto');

const KEY_FILE = process.env.GEMINI_KEY_FILE || 'C:\\Users\\12566\\Downloads\\GEMINI_KEY.txt';
let API_KEY = process.env.GEMINI_API_KEY;
if (!API_KEY) {
  try { API_KEY = fs.readFileSync(KEY_FILE, 'utf8').trim(); }
  catch (e) { console.error('No GEMINI key found:', e.message); process.exit(1); }
}

const ROOT = path.resolve(__dirname, '..', '..', 'image_assets', 'brimm', 'brand_kit');
const MANIFEST_PATH = path.join(ROOT, 'brand_kit_manifest.json');
const LOG_PATH = path.resolve(__dirname, '..', '..', 'image_assets', 'brimm', 'brand_kit_log.txt');

const ENDPOINTS = ['imagen-4.0-generate-001', 'imagen-4.0-fast-generate-001'];

const HOUSE_STYLE = (description) =>
  `Logo concept: ${description}. Dark moody speakeasy aesthetic. Deep charcoal black background (#0D0D0E base) with subtle warm amber rim light. Brass and antique gold (#C9A554) accents, no other colors except occasional cream highlight. Photorealistic high contrast product photography rendering, no flat illustration. No text artifacts, no lorem ipsum, no extra letters beyond what is specified. Centered subject, square composition, no border. 1024x1024.`;

const TARGETS = [
  // NEW glass types: S monogram with bottom curve pouring liquid into a different glass
  ['s_pour_highball', 'pour-into-glass', 'a stylized capital letter S in elegant brass, the lower curve of the S becoming a flowing pour of golden liquid that lands in a tall highball glass with ice cubes. Tall and slender silhouette'],
  ['s_pour_nick_and_nora', 'pour-into-glass', 'a stylized capital letter S in elegant brass, the lower curve of the S becoming a flowing pour of crystal-clear liquid that lands in a vintage Nick & Nora cocktail glass. The bell-shaped vessel sits at the base, classy 1930s feel'],
  ['s_pour_julep_cup', 'pour-into-glass', 'a stylized capital letter S in elegant brass, the bottom of the S pouring a stream of bourbon into a frosty hammered copper julep cup with crushed ice. Kentucky derby aesthetic'],
  ['s_pour_snifter', 'pour-into-glass', 'a stylized capital letter S in elegant brass, the bottom of the S pouring deep amber brandy into a wide-bowled brandy snifter glass cradled in shadow. Sophisticated cigar-lounge feel'],

  // NEW monogram styles: standalone S in different treatments
  ['monogram_engraved', 'monogram', 'a single capital letter S, deeply engraved into a sheet of solid brass with visible chisel marks and metallic depth. The engraving catches the warm rim light. No glass, no liquid, just the engraved metal surface'],
  ['monogram_neon_tube', 'monogram', 'a single capital letter S formed by a glowing amber neon tube, soft halo of warm light around the glass tubing. The neon S floats against a dark wet bar wall. 1940s diner-bar signage feel'],
  ['monogram_etched_glass', 'monogram', 'a single capital letter S etched into thick frosted glass, illuminated from behind so the etched lines glow. The glass panel has subtle bubbles and imperfections suggesting hand-blown craftsmanship'],
  ['monogram_artnouveau', 'monogram', 'a single capital letter S rendered in flowing Art Nouveau style, with organic curves resembling vines or smoke tendrils, executed in polished brass. Whiplash curves and elegant asymmetry, La Belle Epoque feel'],

  // NEW wordmark treatments: full SPEAKEATER text
  ['wordmark_vertical_stack', 'wordmark', 'the word SPEAK stacked vertically in tall serif brass capital letters, with the word EATER running horizontally off the bottom right of the E. Each letter is a separate brass plaque mounted to dark wood paneling. The composition is square'],
  ['wordmark_caps_descender', 'wordmark', 'the word SPEAKEATER in a single horizontal line, all-caps brass serif typography, with the K extending a long decorative descender that curls into a flourish below the baseline. Custom letterforms, hand-drawn quality, prohibition-era signage'],
  ['wordmark_ligature', 'wordmark', 'the word SPEAKEATER as a single horizontal logotype where the K and E ligate together into a custom joined glyph, all letters polished brass set against deep charcoal. The ligature feels intentional and crafted'],

  // NEW icon directions: app launcher candidates
  ['icon_v4_brass_chip', 'icon', 'a square app icon: a polished brass casino-style chip embossed with a single S in the center, photographed from a slight angle so the bevel catches warm light. The chip dominates the square frame'],
  ['icon_v4_keyhole_S', 'icon', 'a square app icon: a vintage brass keyhole escutcheon mounted on a dark mahogany door, the keyhole shape is an S-curve cutout. Speakeasy door reference. The escutcheon dominates the square frame'],
  ['icon_v4_olive_pour', 'icon', 'a square app icon: a single green olive falling into a pool of ice-clear liquid, splash frozen mid-motion, photographed from above. Top-down view, the splash forms a perfect circle filling the frame'],

  // NEW concept directions: looser brand stories
  ['concept_speakeasy_door', 'concept', 'an unmarked dark wooden door with a small brass speakeasy peephole slid open. Through the peephole only an amber glow and the silhouette of a bartender are visible. Dramatic chiaroscuro lighting'],
  ['concept_smoke_S', 'concept', 'a single capital letter S formed entirely from a curl of cigar smoke rising from below, against a dark backlit jazz-club background with a single brass spotlight catching the smoke. Ephemeral and atmospheric'],
];

const sleep = (ms) => new Promise(r => setTimeout(r, ms));

function postImagen(modelId, prompt) {
  const body = JSON.stringify({
    instances: [{ prompt }],
    parameters: { sampleCount: 1, aspectRatio: '1:1' },
  });
  return new Promise((resolve, reject) => {
    const req = https.request({
      method: 'POST',
      hostname: 'generativelanguage.googleapis.com',
      path: `/v1beta/models/${modelId}:predict?key=${API_KEY}`,
      headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) },
      timeout: 90_000,
    }, (res) => {
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => {
        const text = Buffer.concat(chunks).toString('utf8');
        if (res.statusCode !== 200) return reject(new Error(`HTTP ${res.statusCode}: ${text.slice(0, 400)}`));
        try {
          const j = JSON.parse(text);
          const b64 = j.predictions?.[0]?.bytesBase64Encoded;
          if (!b64) return reject(new Error('no bytesBase64Encoded in response'));
          resolve(Buffer.from(b64, 'base64'));
        } catch (e) { reject(e); }
      });
    });
    req.on('error', reject);
    req.on('timeout', () => { req.destroy(new Error('timeout')); });
    req.write(body);
    req.end();
  });
}

async function generateOne(prompt) {
  let lastErr = null;
  for (const model of ENDPOINTS) {
    let attempt = 0;
    let dailyQuotaHit = false;
    while (attempt < 3) {
      try {
        const buf = await postImagen(model, prompt);
        return { buf, model };
      } catch (e) {
        lastErr = e;
        const msg = String(e.message || '');
        if (/HTTP 404/.test(msg)) break;
        if (/HTTP 429/.test(msg) || /quota/i.test(msg) || /RESOURCE_EXHAUSTED/i.test(msg)) {
          if (/per_day/i.test(msg) || /predict_requests_per_model_per_day/i.test(msg)) {
            console.log(`    daily quota exhausted on ${model}, falling through...`);
            dailyQuotaHit = true;
            break;
          }
          attempt++;
          const backoff = Math.min(60_000, 15_000 * attempt);
          console.log(`    rate-limited on ${model} (attempt ${attempt}), backing off ${backoff/1000}s...`);
          await sleep(backoff);
          continue;
        }
        throw e;
      }
    }
    if (dailyQuotaHit) continue;
  }
  throw lastErr;
}

(async () => {
  if (!fs.existsSync(ROOT)) fs.mkdirSync(ROOT, { recursive: true });
  const manifest = fs.existsSync(MANIFEST_PATH)
    ? JSON.parse(fs.readFileSync(MANIFEST_PATH, 'utf8'))
    : { generated_at: new Date().toISOString(), model: ENDPOINTS[0], items: {} };

  console.log(`Brand kit v2: ${TARGETS.length} concepts, output -> ${ROOT}`);
  let ok = 0, skip = 0, fail = 0;

  for (let i = 0; i < TARGETS.length; i++) {
    const [slug, category, description] = TARGETS[i];
    const outPath = path.join(ROOT, `${slug}.png`);
    if (manifest.items[slug] && fs.existsSync(outPath)) {
      console.log(`[${i+1}/${TARGETS.length}] SKIP ${slug}`);
      fs.appendFileSync(LOG_PATH, `[${i+1}/${TARGETS.length}] SKIP ${slug}\n`);
      skip++; continue;
    }
    const prompt = HOUSE_STYLE(description);
    try {
      const { buf, model } = await generateOne(prompt);
      fs.writeFileSync(outPath, buf);
      const sha = crypto.createHash('sha256').update(buf).digest('hex').slice(0, 16);
      manifest.items[slug] = { category, prompt, model, bytes: buf.length, sha };
      fs.writeFileSync(MANIFEST_PATH, JSON.stringify(manifest, null, 2));
      console.log(`[${i+1}/${TARGETS.length}] OK ${slug} via ${model} (${buf.length} bytes)`);
      fs.appendFileSync(LOG_PATH, `[${i+1}/${TARGETS.length}] OK ${slug} via ${model}\n`);
      ok++;
      await sleep(800);  // gentle pacing
    } catch (e) {
      console.log(`[${i+1}/${TARGETS.length}] FAIL ${slug}: ${e.message.slice(0, 200)}`);
      fs.appendFileSync(LOG_PATH, `[${i+1}/${TARGETS.length}] FAIL ${slug}: ${e.message.slice(0, 200)}\n`);
      fail++;
    }
  }

  console.log(`\nDONE: ${ok} ok, ${skip} skip, ${fail} fail`);
})();

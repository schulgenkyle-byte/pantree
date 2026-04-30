#!/usr/bin/env node
/**
 * Brimm image generator — Imagen 4 Ultra, 124 photorealistic images replacing
 * every emoji in the app.
 *
 * File layout:
 *   image_assets/brimm/ingredients/<slug>.png   — organized masters by category
 *   image_assets/brimm/cuisines/<slug>.png
 *   image_assets/brimm/glasses/<slug>.png
 *   image_assets/brimm/aisles/<slug>.png
 *   image_assets/brimm/manifest.json            — slug → category, subject, style, prompt, sha
 *   android/app/src/main/res/drawable-xxhdpi/brimm_<slug>.png  — flat copies for Android build
 *
 * Two prompt styles:
 *   ICON  — ingredients + glassware (renders 24-48dp). Tight crop, clean bg, high edge contrast.
 *   HERO  — cuisines + aisle headers (renders >=80dp). Moody speakeasy full scene.
 *
 * Resume-safe: skips slugs whose master PNG already exists. Aborts on quota.
 */
const fs = require('fs');
const path = require('path');
const https = require('https');
const crypto = require('crypto');

// ----- API key -----
const KEY_FILE = process.env.GEMINI_KEY_FILE || 'C:\\Users\\12566\\Downloads\\GEMINI_KEY.txt';
let API_KEY = process.env.GEMINI_API_KEY;
if (!API_KEY) {
  try { API_KEY = fs.readFileSync(KEY_FILE, 'utf8').trim(); }
  catch (e) { console.error(`FATAL: key file unreadable: ${KEY_FILE}`); process.exit(2); }
}

// ----- Paths -----
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const MASTERS_ROOT = path.join(REPO_ROOT, 'image_assets', 'brimm');
const ANDROID_DRAWABLE = path.join(REPO_ROOT, 'android', 'app', 'src', 'main', 'res', 'drawable-xxhdpi');
const MANIFEST_PATH = path.join(MASTERS_ROOT, 'manifest.json');
const FAIL_LOG = path.join(MASTERS_ROOT, 'image_gen_failures.csv');

['ingredients', 'cuisines', 'glasses', 'aisles', 'nav'].forEach(sub =>
  fs.mkdirSync(path.join(MASTERS_ROOT, sub), { recursive: true })
);
fs.mkdirSync(ANDROID_DRAWABLE, { recursive: true });

// ----- Endpoints — Standard ONLY (Ultra removed per user, has tighter daily cap) -----
// Standard ONLY for ingredients. Imagen 4 Fast was tested as a fallback on 2026-04-27
// and produced wildly off-prompt outputs — milk → camera, taco → ball, rice → phone, etc.
// Fast can't follow detailed product-photography prompts. Better to halt and resume
// next quota window than to ship garbage. The brand_kit script keeps Fast as a fallback
// since abstract logos survive Fast's lower fidelity better than realistic food shots.
const ENDPOINTS = [
  'imagen-4.0-generate-001',
];

// ----- CLI args for parallel partitioning (parallel_run.cjs sets these) -----
const _argv = Object.fromEntries(
  process.argv.slice(2).map(a => {
    const m = a.match(/^--([^=]+)=(.*)$/);
    return m ? [m[1], m[2]] : [a.replace(/^--/, ''), true];
  })
);
const START_IDX = _argv.start ? parseInt(_argv.start, 10) : 0;
const END_IDX = _argv.end ? parseInt(_argv.end, 10) : null;

// ----- Prompt templates -----
const ICON_PROMPT = (subject) =>
  `Macro product photography of a single subject. Subject fills 85% of square frame, centered, sharp focus, soft warm amber rim lighting, clean dark charcoal background with subtle warm gradient, no clutter, no text, no labels, no hands, photorealistic, high edge contrast, app icon style. Subject: ${subject}.`;

const HERO_PROMPT = (subject) =>
  `Professional food photography in dark moody speakeasy aesthetic. Low-key warm amber lighting, dark wood and velvet background, high contrast, vintage 1920s feel, no text, no menu cards, single subject centered, product photography style. Subject: ${subject}.`;

// ============================================================================
// TARGETS — [slug, subject, category]
//   category in {ingredient, cuisine, glass, aisle}
//   ingredient + glass → ICON prompt (renders small in lists/chips)
//   cuisine    + aisle → HERO prompt (renders larger as section/cuisine cards)
// ============================================================================
const TARGETS = [
  // ----- INGREDIENTS (icon style) -----
  ['tomato', 'a single ripe red tomato', 'ingredient'],
  ['onion', 'a single yellow onion with papery skin', 'ingredient'],
  ['garlic', 'a head of fresh garlic, cloves loose', 'ingredient'],
  ['potato', 'a russet potato, dirt-flecked', 'ingredient'],
  ['sweet_potato', 'a sweet potato, deep orange flesh visible', 'ingredient'],
  ['carrot', 'a single fresh carrot with green tops', 'ingredient'],
  ['cucumber', 'a single fresh cucumber', 'ingredient'],
  ['chili', 'a fresh red chili pepper', 'ingredient'],
  ['bell_pepper', 'a glossy red bell pepper', 'ingredient'],
  ['broccoli', 'a head of fresh green broccoli', 'ingredient'],
  ['leafy_greens', 'a bunch of fresh leafy greens, kale or spinach', 'ingredient'],
  ['eggplant', 'a glossy purple eggplant', 'ingredient'],
  ['corn', 'an ear of yellow corn, husk partly peeled', 'ingredient'],
  ['avocado', 'a halved avocado showing the pit', 'ingredient'],
  ['mushroom', 'a cluster of fresh brown mushrooms', 'ingredient'],
  ['lemon', 'a single bright yellow lemon', 'ingredient'],
  ['lime', 'a single fresh green lime', 'ingredient'],
  ['orange', 'a single fresh orange', 'ingredient'],
  ['apple', 'a single red apple', 'ingredient'],
  ['banana', 'a single yellow banana', 'ingredient'],
  ['grape', 'a small bunch of dark purple grapes', 'ingredient'],
  ['strawberry', 'a fresh red strawberry', 'ingredient'],
  ['berries', 'a small handful of fresh blueberries', 'ingredient'],
  ['peach', 'a single ripe peach', 'ingredient'],
  ['pear', 'a single fresh pear', 'ingredient'],
  ['cherry', 'two fresh red cherries with stems', 'ingredient'],
  ['pineapple', 'a small fresh pineapple', 'ingredient'],
  ['watermelon', 'a slice of fresh watermelon', 'ingredient'],
  ['melon', 'a halved cantaloupe melon', 'ingredient'],
  ['mango', 'a ripe orange-yellow mango', 'ingredient'],
  ['kiwi', 'a halved kiwi fruit showing green flesh', 'ingredient'],
  ['coconut', 'a halved fresh coconut', 'ingredient'],
  ['olive', 'a small bowl of black and green olives', 'ingredient'],
  ['pea', 'a small handful of fresh green peas in pods', 'ingredient'],
  ['ginger', 'a knobbly ginger root', 'ingredient'],
  ['herbs', 'a small bouquet of fresh basil and thyme', 'ingredient'],
  ['chicken', 'a roasted chicken leg, golden brown', 'ingredient'],
  ['turkey', 'a sliced roasted turkey breast', 'ingredient'],
  ['beef', 'a raw ribeye steak, marbled', 'ingredient'],
  ['pork', 'a few crispy slices of bacon', 'ingredient'],
  ['lamb', 'a raw lamb chop on the bone', 'ingredient'],
  ['sausage', 'a single grilled sausage link', 'ingredient'],
  ['fish', 'a fresh raw salmon fillet', 'ingredient'],
  ['shrimp', 'a small pile of cooked pink shrimp', 'ingredient'],
  ['lobster', 'a single cooked lobster claw', 'ingredient'],
  ['shellfish', 'a few fresh oysters on the half shell', 'ingredient'],
  ['octopus', 'a small grilled octopus tentacle', 'ingredient'],
  ['egg', 'a single brown egg', 'ingredient'],
  ['tofu', 'a block of fresh white tofu', 'ingredient'],
  ['milk', 'a small glass bottle of milk', 'ingredient'],
  ['cheese', 'a wedge of aged cheddar cheese', 'ingredient'],
  ['butter', 'a stick of butter, partly unwrapped', 'ingredient'],
  ['yogurt', 'a small bowl of plain white yogurt', 'ingredient'],
  ['bread', 'a rustic loaf of crusty bread', 'ingredient'],
  ['bagel', 'a single sesame seed bagel', 'ingredient'],
  ['croissant', 'a single golden flaky croissant', 'ingredient'],
  ['pancake', 'a small stack of golden pancakes', 'ingredient'],
  ['rice', 'a small bowl of steamed white rice', 'ingredient'],
  ['pasta', 'a twirl of fresh spaghetti pasta', 'ingredient'],
  ['tortilla', 'a stack of fresh flour tortillas', 'ingredient'],
  ['taco', 'a single street-style taco', 'ingredient'],
  ['burrito', 'a single burrito wrapped in foil', 'ingredient'],
  ['pizza', 'a single slice of artisan pizza', 'ingredient'],
  ['dumpling', 'three steamed pork dumplings', 'ingredient'],
  ['sushi', 'two pieces of fresh nigiri sushi', 'ingredient'],
  ['oatmeal', 'a small bowl of warm oatmeal', 'ingredient'],
  ['flour', 'a small mound of white flour', 'ingredient'],
  ['beans', 'a small bowl of dried beans', 'ingredient'],
  ['nuts', 'a small handful of mixed nuts', 'ingredient'],
  ['seeds', 'a small pile of mixed seeds', 'ingredient'],
  ['oil', 'a small glass bottle of olive oil', 'ingredient'],
  ['sauce', 'a small jar of tomato sauce', 'ingredient'],
  ['honey', 'a glass jar of golden honey with a dipper', 'ingredient'],
  ['condiment', 'a small ramekin of mustard', 'ingredient'],
  ['salt', 'a small pile of coarse sea salt', 'ingredient'],
  ['vanilla', 'a few vanilla bean pods', 'ingredient'],
  ['chocolate', 'a few squares of dark chocolate', 'ingredient'],
  ['cookie', 'a single chocolate chip cookie', 'ingredient'],
  ['cake', 'a single small chocolate cupcake', 'ingredient'],
  ['pie', 'a slice of apple pie', 'ingredient'],
  ['ice_cream', 'a small scoop of vanilla ice cream in a bowl', 'ingredient'],
  ['water', 'a clear glass of sparkling water', 'ingredient'],
  ['coffee', 'a small espresso cup with crema', 'ingredient'],
  ['tea', 'a small ceramic cup of green tea', 'ingredient'],
  ['wine', 'a glass of dark red wine', 'ingredient'],
  ['beer', 'a small mug of amber beer', 'ingredient'],
  ['cocktail_generic', 'a classic martini in a coupe glass', 'ingredient'],
  ['juice', 'a small glass of fresh orange juice', 'ingredient'],
  ['frozen', 'a single ice cube on dark surface', 'ingredient'],
  ['soup', 'a small bowl of dark broth soup', 'ingredient'],
  ['salad', 'a small bowl of mixed green salad', 'ingredient'],
  ['popcorn', 'a small bowl of buttered popcorn', 'ingredient'],
  ['candy', 'a few wrapped hard candies', 'ingredient'],
  ['plate', 'an empty rustic ceramic dinner plate', 'ingredient'],

  // ----- CUISINES (hero style) -----
  ['cuisine_italian', 'a single bowl of spaghetti pasta', 'cuisine'],
  ['cuisine_mexican', 'two street tacos on a wooden board', 'cuisine'],
  ['cuisine_japanese', 'a small wooden tray of nigiri sushi', 'cuisine'],
  ['cuisine_chinese', 'a takeout box of chinese noodles', 'cuisine'],
  ['cuisine_indian', 'a small bowl of indian curry', 'cuisine'],
  ['cuisine_thai', 'a bowl of thai noodle soup', 'cuisine'],
  ['cuisine_french', 'a single golden flaky croissant on a plate', 'cuisine'],
  ['cuisine_american', 'a single classic cheeseburger', 'cuisine'],
  ['cuisine_mediterranean', 'a small mediterranean mezze plate', 'cuisine'],
  ['cuisine_korean', 'a bento box of korean bibimbap', 'cuisine'],
  ['cuisine_generic', 'an empty rustic ceramic dinner plate, fork and knife', 'cuisine'],

  // ----- GLASSES (icon style) -----
  ['glass_rocks', 'an empty old fashioned rocks glass', 'glass'],
  ['glass_coupe', 'an empty crystal coupe cocktail glass', 'glass'],
  ['glass_highball', 'an empty highball collins glass', 'glass'],
  ['glass_flute', 'an empty champagne flute', 'glass'],
  ['glass_martini', 'an empty martini glass', 'glass'],
  ['glass_shot', 'an empty shot glass', 'glass'],

  // ----- AISLES (hero style) -----
  ['aisle_produce', 'a small grouping of fresh vegetables: leafy greens, tomato, carrot', 'aisle'],
  ['aisle_bakery', 'a rustic loaf of crusty bread on a wooden board', 'aisle'],
  ['aisle_deli', 'a wedge of cheese and sliced cured meat on a board', 'aisle'],
  ['aisle_protein', 'a raw ribeye steak on a wooden board', 'aisle'],
  ['aisle_dairy', 'a small glass bottle of milk and a wedge of butter', 'aisle'],
  ['aisle_frozen', 'a single ice cube glistening on dark surface', 'aisle'],
  ['aisle_grain', 'a small wooden bowl of mixed grains: rice, oats, wheat', 'aisle'],
  ['aisle_pantry', 'a single rustic tin can of preserves on dark wood', 'aisle'],
  ['aisle_spice', 'a small wooden bowl of mixed whole spices', 'aisle'],
  ['aisle_condiment', 'a glass jar of golden honey with a wooden dipper', 'aisle'],
  ['aisle_bar', 'an empty old fashioned rocks glass next to a bottle on dark bar wood', 'aisle'],
  ['aisle_beverage', 'a tall glass bottle of cola on dark bar wood', 'aisle'],
  ['aisle_other', 'a small brown cardboard box on dark wood', 'aisle'],

  // ----- NAV ICONS (icon style) — replaces Material Icons in bottom nav + quick actions -----
  // Speakeater is SVG/emoji-free; every navigation affordance gets a photorealistic master image.
  ['nav_culinary', 'a rustic empty stoneware bowl with a polished steel spoon resting on the rim', 'nav'],
  ['nav_feed', 'a stack of three weathered postcards tied with string on dark wood', 'nav'],
  ['nav_you', 'a single brass key resting on dark felt, soft amber rim light', 'nav'],
  ['nav_mixology', 'a single coupe cocktail glass empty, dark velvet background, brass rim light', 'nav'],
  ['nav_snap_bar', 'a vintage brass camera lens close-up, dark backdrop, warm amber catchlight', 'nav'],
  ['nav_shop', 'a single vintage chrome and brass shopping cart, three-quarter angle, empty, dark wood floor, soft amber rim light, photorealistic, no text, no people', 'nav'],
  ['nav_plan', 'a leather-bound pocket calendar open to a blank page, brass clip, dark surface', 'nav'],
  ['nav_pantry', 'an open vintage wooden apothecary cabinet on dark wall, two shelves visible stocked with antique glass jars and small tins, warm amber light spilling from inside, photorealistic, no text, no labels', 'nav'],
  ['nav_saved', 'a worn leather bookmark ribbon resting on a dark cookbook page', 'nav'],
  ['nav_search', 'a vintage brass magnifying glass on dark felt', 'nav'],
  ['nav_adventurous', 'extreme close-up macro shot of a vintage silver fork and silver knife standing perfectly upright vertical side by side, the fork and knife fill 90 percent of the square frame from top to bottom, polished silver with patina, dark velvet background, warm amber rim light catching the tines and blade edge, photorealistic product photography, no plate, no text, no labels', 'nav'],
  ['nav_library', 'extreme close-up macro shot of three antique leather-bound cookbooks standing upright vertically side by side, the three book spines fill 90 percent of the square frame from top to bottom, gilded brass corners, weathered burgundy and forest-green leather spines, warm amber side light, dark wood shelf below barely visible, photorealistic, no text on spines, no titles, no labels', 'nav'],
];

const CATEGORY_DIR = {
  ingredient: 'ingredients',
  cuisine: 'cuisines',
  glass: 'glasses',
  aisle: 'aisles',
  nav: 'nav',
};
const CATEGORY_STYLE = {
  ingredient: 'icon',
  glass: 'icon',
  nav: 'icon',
  cuisine: 'hero',
  aisle: 'hero',
};

// ----- HTTP -----
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
        if (res.statusCode !== 200) {
          return reject(new Error(`HTTP ${res.statusCode}: ${data.slice(0, 300)}`));
        }
        try {
          const parsed = JSON.parse(data);
          const b64 = parsed?.predictions?.[0]?.bytesBase64Encoded;
          if (!b64) return reject(new Error(`No image in response: ${data.slice(0, 300)}`));
          resolve(Buffer.from(b64, 'base64'));
        } catch (e) { reject(new Error(`Parse error: ${e.message}`)); }
      });
    });
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

const sleep = (ms) => new Promise(r => setTimeout(r, ms));

// Imagen 4 Ultra has tight RPM limits (~6 RPM observed on paid tier). On 429,
// back off and retry several times before giving up on a single slug.
async function generateOne(prompt) {
  let lastErr = null;
  for (const model of ENDPOINTS) {
    let attempt = 0;
    let dailyQuotaHit = false;
    while (attempt < 3) {  // Reduced from 5 — daily-quota 429s never recover within a session.
      try {
        const buf = await postImagen(model, prompt);
        return { buf, model };
      } catch (e) {
        lastErr = e;
        const msg = String(e.message || '');
        if (/HTTP 404/.test(msg)) break;  // model not available, try next
        if (/HTTP 429/.test(msg) || /quota/i.test(msg) || /RESOURCE_EXHAUSTED/i.test(msg)) {
          // Daily-quota signature ("per_day"): never recoverable today, fall to next model immediately.
          if (/per_day/i.test(msg) || /predict_requests_per_model_per_day/i.test(msg)) {
            console.log(`    daily quota exhausted on ${model}, falling through to next model...`);
            dailyQuotaHit = true;
            break;
          }
          attempt++;
          const backoff = Math.min(60_000, 15_000 * attempt);
          console.log(`    rate-limited on ${model} (attempt ${attempt}), backing off ${backoff/1000}s...`);
          await sleep(backoff);
          continue;
        }
        throw e;  // Non-retryable
      }
    }
    if (dailyQuotaHit) continue;  // try the next model in ENDPOINTS
  }
  throw lastErr;
}

(async () => {
  const sliceEnd = END_IDX !== null ? Math.min(END_IDX, TARGETS.length) : TARGETS.length;
  const sliceStart = Math.max(0, Math.min(START_IDX, sliceEnd));
  const range = sliceEnd - sliceStart;
  console.log(`Generating ${range} of ${TARGETS.length} images (range [${sliceStart}, ${sliceEnd})) via Imagen 4 Standard`);
  console.log(`  Masters: ${MASTERS_ROOT}`);
  console.log(`  Android: ${ANDROID_DRAWABLE}\n`);
  // Append to fail log instead of clobbering, so parallel workers don't race.
  if (sliceStart === 0 && !fs.existsSync(FAIL_LOG)) fs.writeFileSync(FAIL_LOG, 'slug,category,style,error\n');

  const manifest = fs.existsSync(MANIFEST_PATH)
    ? JSON.parse(fs.readFileSync(MANIFEST_PATH, 'utf8'))
    : { generated_at: new Date().toISOString(), model: ENDPOINTS[0], items: {} };

  let okCount = 0, failCount = 0, skippedCount = 0;
  const t0 = Date.now();

  for (let i = sliceStart; i < sliceEnd; i++) {
    const [slug, subject, category] = TARGETS[i];
    const style = CATEGORY_STYLE[category];
    const prompt = style === 'icon' ? ICON_PROMPT(subject) : HERO_PROMPT(subject);
    const masterPath = path.join(MASTERS_ROOT, CATEGORY_DIR[category], `${slug}.png`);
    const drawablePath = path.join(ANDROID_DRAWABLE, `brimm_${slug}.png`);

    if (fs.existsSync(masterPath) && fs.statSync(masterPath).size > 1024) {
      // Resume — also ensure the drawable copy exists.
      if (!fs.existsSync(drawablePath)) fs.copyFileSync(masterPath, drawablePath);
      skippedCount++;
      console.log(`[${i + 1}/${sliceEnd}] SKIP ${slug}`);
      continue;
    }

    try {
      const { buf, model } = await generateOne(prompt);
      fs.writeFileSync(masterPath, buf);
      fs.writeFileSync(drawablePath, buf);
      const sha = crypto.createHash('sha256').update(buf).digest('hex').slice(0, 16);
      manifest.items[slug] = { category, style, subject, prompt, model, bytes: buf.length, sha };
      fs.writeFileSync(MANIFEST_PATH, JSON.stringify(manifest, null, 2));
      okCount++;
      console.log(`[${i + 1}/${sliceEnd}] OK   ${category}/${slug} (${(buf.length / 1024).toFixed(0)}KB)`);
    } catch (e) {
      failCount++;
      const msg = String(e.message || e).replace(/[\r\n,]/g, ' ').slice(0, 240);
      console.error(`[${i + 1}/${sliceEnd}] FAIL ${slug}: ${msg}`);
      fs.appendFileSync(FAIL_LOG, `${slug},${category},${style},"${msg}"\n`);
      // After 5 retries the helper still failed — log and keep going (don't abort whole batch).
    }

    // Pace at ~5 RPM (12s/req) to stay under Imagen 4 Ultra's per-minute cap.
    await sleep(12_000);
  }

  const elapsed = ((Date.now() - t0) / 1000).toFixed(1);
  console.log(`\nDONE in ${elapsed}s — ok=${okCount} fail=${failCount} skipped=${skippedCount} (of ${range})`);
  console.log(`Manifest: ${MANIFEST_PATH}`);
  if (failCount) console.log(`Failures: ${FAIL_LOG}`);
})().catch((e) => { console.error('FATAL:', e); process.exit(1); });

#!/usr/bin/env node
/**
 * Brimm RECIPE image generator — generates Imagen 4 Ultra photos for the top
 * 300 most-likely-to-be-suggested recipes (food + cocktails). These replace
 * Wikimedia/Openverse photos with brand-consistent speakeasy aesthetic.
 *
 * Selection algorithm (client-side, no new admin endpoint):
 *   1. Pull /admin/sample-recipes paginated (offset>0 = deterministic by id) until
 *      we've sampled ~2000 candidates across food + cocktail content_types.
 *   2. Filter: must have cuisine assigned, 3-15 ingredients, >=3 steps.
 *   3. Rank by canonical-ness signal: shorter title + has cuisine + moderate
 *      ingredient count = more likely to be a recognizable dish someone will cook.
 *   4. Take top N (default 300; --limit=N to override).
 *
 * File layout:
 *   image_assets/brimm/recipes/<recipe_id>.png            — masters
 *   image_assets/brimm/recipes/recipe_manifest.json       — slug → id, title, cuisine, prompt, sha
 *   (NO copy to android/ — recipe images are too many to bundle in APK; they
 *    need to be uploaded to R2/CDN and image_url patched via /admin/patch-recipe-photo.
 *    That upload step is a separate run after the user reviews the masters.)
 *
 * Usage:
 *   node generate_recipe_images.cjs                    # full 300 run
 *   node generate_recipe_images.cjs --samples=8        # preview 8 first
 *   node generate_recipe_images.cjs --limit=300        # override total
 *   node generate_recipe_images.cjs --content=food     # food only (default: food+cocktail)
 */
const fs = require('fs');
const path = require('path');
const https = require('https');
const crypto = require('crypto');

// ----- CLI args -----
const argv = Object.fromEntries(
  process.argv.slice(2).map(a => {
    const m = a.match(/^--([^=]+)=(.*)$/);
    return m ? [m[1], m[2]] : [a.replace(/^--/, ''), true];
  })
);
const SAMPLES = argv.samples ? parseInt(argv.samples, 10) : 0;
const LIMIT = argv.limit ? parseInt(argv.limit, 10) : 300;
const CONTENT_FILTER = argv.content || 'all'; // 'food' | 'cocktail' | 'all'

// ----- Keys + endpoints -----
const GEMINI_KEY_FILE = process.env.GEMINI_KEY_FILE || 'C:\\Users\\12566\\Downloads\\GEMINI_KEY.txt';
const ADMIN_KEY_FILE = process.env.PANTRIE_ADMIN_KEY_FILE || 'C:\\Users\\12566\\Downloads\\PANTRIE_ADMIN_KEY.txt';

let GEMINI_KEY = process.env.GEMINI_API_KEY;
if (!GEMINI_KEY) GEMINI_KEY = fs.readFileSync(GEMINI_KEY_FILE, 'utf8').trim();
let ADMIN_KEY = process.env.PANTRIE_ADMIN_KEY;
if (!ADMIN_KEY) ADMIN_KEY = fs.readFileSync(ADMIN_KEY_FILE, 'utf8').trim();

const WORKER = 'https://pantrie-backend.schulgenkyle.workers.dev';

// ----- Paths -----
const REPO_ROOT = path.resolve(__dirname, '..', '..');
const RECIPE_DIR = path.join(REPO_ROOT, 'image_assets', 'brimm', 'recipes');
const RECIPE_MANIFEST = path.join(RECIPE_DIR, 'recipe_manifest.json');
const FAIL_LOG = path.join(RECIPE_DIR, 'recipe_gen_failures.csv');
const CANDIDATES_CACHE = path.join(RECIPE_DIR, 'candidates.json');
fs.mkdirSync(RECIPE_DIR, { recursive: true });

// ----- Imagen endpoints — Standard ONLY (Ultra removed, lower daily cap) -----
const ENDPOINTS = [
  'imagen-4.0-generate-001',
  'imagen-4.0-fast-generate-001',
];

// ----- CLI args for parallel partitioning -----
const START_IDX = argv.start ? parseInt(argv.start, 10) : 0;
const END_IDX = argv.end ? parseInt(argv.end, 10) : null;

// ----- Prompt template — uses recipe title + cuisine for context -----
function recipePrompt(title, cuisine, contentType, ingredientNames) {
  const isCocktail = contentType === 'cocktail' || contentType === 'mocktail';
  const ingHint = ingredientNames
    ? ` Key ingredients: ${ingredientNames.split(',').slice(0, 5).map(s => s.trim()).join(', ')}.`
    : '';
  if (isCocktail) {
    return `Professional cocktail photography in dark moody speakeasy aesthetic. Low-key warm amber lighting, dark wood bar background with subtle bottle silhouettes, vintage 1920s feel, cocktail glass centered, garnish visible, condensation on glass, no text, no menu cards, photorealistic, high contrast.${ingHint} Subject: a "${title}" cocktail.`;
  }
  const cuisineHint = cuisine ? ` Style: ${cuisine} cuisine.` : '';
  return `Professional food photography in dark moody speakeasy aesthetic. Low-key warm amber lighting, dark wood and velvet background, plated on rustic ceramic, single dish centered, vintage 1920s feel, no text, no menu cards, no hands, photorealistic, sharp focus, restaurant-quality plating.${cuisineHint}${ingHint} Subject: a single serving of "${title}".`;
}

// ----- HTTP helpers -----
function httpJson(url, headers = {}) {
  return new Promise((resolve, reject) => {
    https.get(url, { headers }, (res) => {
      let data = '';
      res.on('data', c => { data += c; });
      res.on('end', () => {
        if (res.statusCode !== 200) return reject(new Error(`HTTP ${res.statusCode}: ${data.slice(0, 300)}`));
        try { resolve(JSON.parse(data)); }
        catch (e) { reject(new Error(`Parse: ${e.message}`)); }
      });
    }).on('error', reject);
  });
}

function postImagen(modelId, prompt) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify({
      instances: [{ prompt }],
      parameters: { sampleCount: 1, aspectRatio: '1:1', safetySetting: 'block_low_and_above' },
    });
    const req = https.request({
      method: 'POST',
      hostname: 'generativelanguage.googleapis.com',
      path: `/v1beta/models/${modelId}:predict?key=${GEMINI_KEY}`,
      headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) },
    }, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        if (res.statusCode !== 200) return reject(new Error(`HTTP ${res.statusCode}: ${data.slice(0, 300)}`));
        try {
          const parsed = JSON.parse(data);
          const b64 = parsed?.predictions?.[0]?.bytesBase64Encoded;
          if (!b64) return reject(new Error(`No image in response: ${data.slice(0, 300)}`));
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

// ----- Candidate fetch + ranking -----
async function fetchCandidates() {
  if (fs.existsSync(CANDIDATES_CACHE)) {
    const cached = JSON.parse(fs.readFileSync(CANDIDATES_CACHE, 'utf8'));
    if (cached.fetched_at && Date.now() - new Date(cached.fetched_at).getTime() < 86_400_000) {
      console.log(`Using cached candidate list (${cached.recipes.length} recipes)`);
      return cached.recipes;
    }
  }
  console.log('Fetching candidate recipes from backend...');
  const all = [];
  const types = CONTENT_FILTER === 'all' ? ['food', 'cocktail'] : [CONTENT_FILTER];
  for (const ct of types) {
    let offset = 0;
    const pageLimit = 500;
    const targetPerType = ct === 'cocktail' ? 800 : 2000;
    while (offset < targetPerType) {
      const url = `${WORKER}/admin/sample-recipes?content_type=${ct}&limit=${pageLimit}&offset=${offset}&key=${encodeURIComponent(ADMIN_KEY)}`;
      const res = await httpJson(url);
      const got = res.recipes || [];
      if (got.length === 0) break;
      all.push(...got);
      console.log(`  ${ct}: fetched ${got.length} (offset=${offset}, total=${all.length})`);
      offset += pageLimit;
      if (got.length < pageLimit) break;
      await sleep(300);
    }
  }
  fs.writeFileSync(CANDIDATES_CACHE, JSON.stringify({ fetched_at: new Date().toISOString(), recipes: all }, null, 2));
  return all;
}

function score(r) {
  // Ranking signal — higher is better.
  let s = 0;
  if (r.cuisine && r.cuisine.trim()) s += 3;
  if (r.ingredient_count >= 4 && r.ingredient_count <= 12) s += 2;
  else if (r.ingredient_count >= 3 && r.ingredient_count <= 15) s += 1;
  if (r.step_count >= 3) s += 1;
  if (r.title && r.title.length > 0 && r.title.length <= 40) s += 1;
  if (r.title && r.title.length <= 25) s += 1; // very canonical names
  if (r.image_url && r.image_url.length > 0) s += 1; // existing photo = vetted recipe
  return s;
}

function rankAndPick(candidates, n) {
  const filtered = candidates.filter(r =>
    r.cuisine && r.cuisine.trim() &&
    r.ingredient_count >= 3 && r.ingredient_count <= 15 &&
    r.step_count >= 3 &&
    r.title && r.title.trim().length > 0 &&
    r.title.length <= 60
  );
  filtered.sort((a, b) => {
    const ds = score(b) - score(a);
    if (ds !== 0) return ds;
    // Tie-break: shorter title first (more canonical).
    return (a.title || '').length - (b.title || '').length;
  });
  // Diversify cuisine — round-robin to avoid 200 italian dishes.
  const byCuisine = {};
  for (const r of filtered) {
    const c = (r.cuisine || 'unknown').toLowerCase();
    if (!byCuisine[c]) byCuisine[c] = [];
    byCuisine[c].push(r);
  }
  const cuisines = Object.keys(byCuisine);
  const out = [];
  let idx = 0;
  while (out.length < n) {
    let added = false;
    for (const c of cuisines) {
      if (byCuisine[c][idx]) {
        out.push(byCuisine[c][idx]);
        added = true;
        if (out.length >= n) break;
      }
    }
    if (!added) break;
    idx++;
  }
  return out.slice(0, n);
}

// ----- Main -----
(async () => {
  console.log(`Recipe image generator — content=${CONTENT_FILTER}, samples=${SAMPLES || 'none'}, target=${LIMIT}`);
  console.log(`Output: ${RECIPE_DIR}\n`);

  const candidates = await fetchCandidates();
  console.log(`Total candidates fetched: ${candidates.length}`);

  const targetCount = SAMPLES > 0 ? SAMPLES : LIMIT;
  const allPicked = rankAndPick(candidates, targetCount);
  // Slice to assigned range when running under parallel_run.cjs
  const sliceEnd = END_IDX !== null ? Math.min(END_IDX, allPicked.length) : allPicked.length;
  const sliceStart = Math.max(0, Math.min(START_IDX, sliceEnd));
  const picked = allPicked.slice(sliceStart, sliceEnd);
  console.log(`Selected ${allPicked.length} recipes after ranking + diversification; this worker handles [${sliceStart}, ${sliceEnd}) = ${picked.length} items\n`);

  if (SAMPLES > 0) {
    console.log('SAMPLE PREVIEW (first 8 picks):');
    picked.forEach((r, i) => console.log(`  ${i+1}. [${r.cuisine}] ${r.title} (${r.ingredient_count} ing, ${r.step_count} steps)`));
    console.log('');
  }

  const manifest = fs.existsSync(RECIPE_MANIFEST)
    ? JSON.parse(fs.readFileSync(RECIPE_MANIFEST, 'utf8'))
    : { generated_at: new Date().toISOString(), model: ENDPOINTS[0], items: {} };

  fs.writeFileSync(FAIL_LOG, 'recipe_id,title,cuisine,error\n');

  let okCount = 0, failCount = 0, skippedCount = 0;
  const t0 = Date.now();

  for (let i = 0; i < picked.length; i++) {
    const r = picked[i];
    const outPath = path.join(RECIPE_DIR, `${r.id}.png`);
    if (fs.existsSync(outPath) && fs.statSync(outPath).size > 1024) {
      skippedCount++;
      console.log(`[${i + 1}/${picked.length}] SKIP ${r.id} — ${r.title}`);
      continue;
    }
    const prompt = recipePrompt(r.title, r.cuisine, r.content_type, r.ingredient_names);
    try {
      const { buf, model } = await generateOne(prompt);
      fs.writeFileSync(outPath, buf);
      const sha = crypto.createHash('sha256').update(buf).digest('hex').slice(0, 16);
      manifest.items[r.id] = {
        title: r.title,
        cuisine: r.cuisine,
        content_type: r.content_type,
        prompt,
        model,
        bytes: buf.length,
        sha,
      };
      fs.writeFileSync(RECIPE_MANIFEST, JSON.stringify(manifest, null, 2));
      okCount++;
      console.log(`[${i + 1}/${picked.length}] OK   ${r.id} — ${r.title} (${(buf.length / 1024).toFixed(0)}KB)`);
    } catch (e) {
      failCount++;
      const msg = String(e.message || e).replace(/[\r\n,]/g, ' ').slice(0, 240);
      console.error(`[${i + 1}/${picked.length}] FAIL ${r.id} — ${r.title}: ${msg}`);
      fs.appendFileSync(FAIL_LOG, `${r.id},"${(r.title || '').replace(/"/g, "'")}","${r.cuisine || ''}","${msg}"\n`);
    }
    await sleep(12_000); // ~5 RPM, stays under Imagen 4 Ultra cap
  }

  const elapsed = ((Date.now() - t0) / 1000).toFixed(1);
  console.log(`\nDONE in ${elapsed}s — ok=${okCount} fail=${failCount} skipped=${skippedCount} (of ${picked.length})`);
  console.log(`Manifest: ${RECIPE_MANIFEST}`);
})().catch((e) => { console.error('FATAL:', e); process.exit(1); });

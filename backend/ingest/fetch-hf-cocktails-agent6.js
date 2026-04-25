// Agent 6: Fetch cocktail recipes/photos from Hugging Face for pantree.
//
// Source: yujinyang/cocktails_436 (Apache-2.0). 436 cocktails repackaged from
// TheCocktailDB schema with AI-generated `description` and `imageDescription`.
// We dedup against the existing cocktail catalog by case-insensitive title.
//
// Outputs (under backend/ingest/):
//   cocktails/hf-cocktails.ndjson           — new cocktail records (normalized)
//   normalized/agent6-hf-photos.json        — image references with license
//   normalized/agent6-report.md             — full evaluation report
//
// Notes:
//   * No-auth public dataset. Polite UA + ~1 req/sec.
//   * Schema mirrors backend/ingest/cocktails/cocktaildb-raw.ndjson.

import { writeFileSync, readFileSync, readdirSync, existsSync, mkdirSync, createWriteStream } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join, dirname } from 'node:path';

const HERE = fileURLToPath(new URL('./', import.meta.url));
const COCKTAILS_DIR = join(HERE, 'cocktails');
const NORM_DIR = join(HERE, 'normalized');
mkdirSync(COCKTAILS_DIR, { recursive: true });
mkdirSync(NORM_DIR, { recursive: true });

const OUT_NDJSON = join(COCKTAILS_DIR, 'hf-cocktails.ndjson');
const OUT_PHOTOS = join(NORM_DIR, 'agent6-hf-photos.json');
const OUT_REPORT = join(NORM_DIR, 'agent6-report.md');

const UA = 'Pan-Tree-Cocktail-Ingest/1.0 (https://pan-tree.app; schulgenkyle@gmail.com)';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// ---- 1. Build dedup set from existing cocktail NDJSONs ----------------------
function loadExistingTitles() {
  const titles = new Set();
  for (const f of readdirSync(COCKTAILS_DIR)) {
    if (!f.endsWith('.ndjson')) continue;
    if (f === 'hf-cocktails.ndjson') continue; // skip our own output
    const path = join(COCKTAILS_DIR, f);
    const text = readFileSync(path, 'utf8');
    for (const line of text.split('\n')) {
      if (!line.trim()) continue;
      try {
        const obj = JSON.parse(line);
        if (obj && obj.title) {
          titles.add(normalizeTitle(obj.title));
        }
      } catch {}
    }
  }
  return titles;
}

function normalizeTitle(t) {
  return String(t).toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
}

// ---- 2. Minimal CSV parser (RFC-4180-ish, handles quoted fields/newlines) ---
function parseCsv(text) {
  const rows = [];
  let i = 0;
  let field = '';
  let row = [];
  let inQuotes = false;
  while (i < text.length) {
    const c = text[i];
    if (inQuotes) {
      if (c === '"') {
        if (text[i + 1] === '"') { field += '"'; i += 2; continue; }
        inQuotes = false; i++; continue;
      }
      field += c; i++; continue;
    }
    if (c === '"') { inQuotes = true; i++; continue; }
    if (c === ',') { row.push(field); field = ''; i++; continue; }
    if (c === '\r') { i++; continue; }
    if (c === '\n') { row.push(field); rows.push(row); row = []; field = ''; i++; continue; }
    field += c; i++;
  }
  if (field.length > 0 || row.length > 0) { row.push(field); rows.push(row); }
  return rows;
}

// ---- 3. Parse python-list literal "['a','b']" into JS array -----------------
function parsePyList(s) {
  if (!s) return [];
  const trimmed = String(s).trim();
  if (!trimmed.startsWith('[') || !trimmed.endsWith(']')) {
    // Sometimes plain strings come through.
    return [trimmed].filter(Boolean);
  }
  const inner = trimmed.slice(1, -1);
  const out = [];
  let buf = '';
  let inStr = false;
  let quote = '';
  for (let i = 0; i < inner.length; i++) {
    const c = inner[i];
    if (inStr) {
      if (c === '\\' && i + 1 < inner.length) { buf += inner[i + 1]; i++; continue; }
      if (c === quote) { out.push(buf); buf = ''; inStr = false; continue; }
      buf += c;
    } else {
      if (c === "'" || c === '"') { inStr = true; quote = c; continue; }
      // skip commas/whitespace between items
    }
  }
  return out;
}

// ---- 4. Normalize one row to pantree cocktail schema ------------------------
function rowToCocktail(headers, row, datasetId) {
  const obj = {};
  for (let i = 0; i < headers.length; i++) obj[headers[i]] = row[i];

  const name = (obj.name || '').trim();
  if (!name) return null;

  const ingredientsList = parsePyList(obj.ingredients);
  const measuresList = parsePyList(obj.ingredientMeasures);
  const ingredients = [];
  for (let i = 0; i < ingredientsList.length; i++) {
    const ingName = ingredientsList[i];
    if (!ingName) continue;
    const measure = measuresList[i] || '';
    const { quantity, unit } = parseMeasure(measure);
    ingredients.push({
      name: titleCase(ingName),
      quantity: quantity,
      unit: unit,
      raw_measure: measure || null,
    });
  }

  const instructions = cleanText(obj.instructions || '')
    .split(/\r?\n|\.\s+(?=[A-Z])/)
    .map((s) => s.trim())
    .filter(Boolean)
    .map((s) => (s.endsWith('.') ? s : s + '.'));

  const alcoholic = String(obj.alcoholic || '').toLowerCase();
  const isAlcoholic = alcoholic.includes('alcoholic') && !alcoholic.includes('non');

  const glass = mapGlass(obj.glassType);
  const description = cleanText((obj.description || obj.desciription || '').trim()) || null;
  const imageDesc = cleanText((obj.imageDescription || '').trim()) || null;

  const out = {
    title: titleCase(name),
    content_type: 'cocktail',
    is_alcoholic: isAlcoholic ? 1 : 0,
    is_historic: 0,
    source_book: `HuggingFace: ${datasetId}`,
    source_url: `https://huggingface.co/datasets/${datasetId}`,
    cuisine: 'cocktail',
    description: description || `Cocktail — served in a ${glass || 'cocktail'} glass`,
    image_description: imageDesc,
    servings: 1,
    prep_minutes: 3,
    cook_minutes: 0,
    instructions: instructions.length ? instructions : [obj.instructions || ''].filter(Boolean),
    ingredients,
    glass_type: glass,
    method: inferMethod(obj.instructions || ''),
    garnish: null,
    abv_percent: null,
    image_url: (obj.drinkThumbnail || '').trim() || null,
    image_license: 'TheCocktailDB (public archive, attribution requested)',
    license: 'apache-2.0',
  };
  return out;
}

function cleanText(s) {
  // Source CSV has mojibake artifacts (e.g. é → _, ñ → ?). Heuristically repair common cocktail names.
  return String(s)
    .replace(/Planter_ Punch/gi, "Planter's Punch")
    .replace(/Fros_/g, 'Frosé')
    .replace(/Nescaf\?/g, 'Nescafé')
    .replace(/_s\b/g, "'s")
    .replace(/(\w)_(\s)/g, '$1$2')
    .replace(/(\w)\?(\w)/g, '$1$2')
    .trim();
}

function titleCase(s) {
  return cleanText(String(s))
    .toLowerCase()
    .replace(/\b([a-z])/g, (m, c) => c.toUpperCase())
    .trim();
}

function mapGlass(g) {
  const s = String(g || '').toLowerCase();
  if (!s) return null;
  if (s.includes('shot')) return 'shot';
  if (s.includes('coupe')) return 'coupe';
  if (s.includes('martini')) return 'martini';
  if (s.includes('highball')) return 'highball';
  if (s.includes('collins')) return 'collins';
  if (s.includes('rocks') || s.includes('old-fashioned') || s.includes('old fashioned')) return 'rocks';
  if (s.includes('hurricane')) return 'hurricane';
  if (s.includes('margarita')) return 'margarita';
  if (s.includes('beer mug') || s.includes('beer glass') || s.includes('pint')) return 'beer';
  if (s.includes('wine')) return 'wine';
  if (s.includes('champagne') || s.includes('flute')) return 'flute';
  if (s.includes('cocktail')) return 'coupe';
  if (s.includes('mug')) return 'mug';
  if (s.includes('jar')) return 'jar';
  return s.replace(/\s+/g, '_');
}

function inferMethod(instr) {
  const s = String(instr).toLowerCase();
  if (s.includes('blend')) return 'blended';
  if (s.includes('shake')) return 'shaken';
  if (s.includes('stir')) return 'stirred';
  if (s.includes('layer')) return 'layered';
  if (s.includes('muddle')) return 'muddled';
  if (s.includes('build') || s.includes('pour') || s.includes('fill')) return 'built';
  return null;
}

function parseMeasure(m) {
  if (!m) return { quantity: null, unit: null };
  const s = String(m).trim().toLowerCase();
  // Handle fractions like "1/2", "1 1/2"
  const fracMatch = s.match(/^(\d+\s+\d+\/\d+|\d+\/\d+|\d+\.?\d*)/);
  let qty = null;
  if (fracMatch) {
    const n = fracMatch[1];
    if (n.includes('/')) {
      const parts = n.split(/\s+/);
      let total = 0;
      for (const p of parts) {
        if (p.includes('/')) {
          const [a, b] = p.split('/').map(Number);
          if (b) total += a / b;
        } else {
          total += Number(p);
        }
      }
      qty = total;
    } else {
      qty = Number(n);
    }
  }
  let unit = null;
  if (s.includes(' oz') || s.endsWith('oz')) unit = 'oz';
  else if (s.includes('shot')) unit = 'shot';
  else if (s.includes(' ml')) unit = 'ml';
  else if (s.includes(' cl')) unit = 'cl';
  else if (s.includes('cup')) unit = 'cup';
  else if (s.includes('tbsp') || s.includes('tablespoon')) unit = 'tbsp';
  else if (s.includes('tsp') || s.includes('teaspoon')) unit = 'tsp';
  else if (s.includes('dash')) unit = 'dash';
  else if (s.includes('drop')) unit = 'drop';
  else if (s.includes('part')) unit = 'part';
  else if (s.includes('splash')) unit = 'splash';
  else if (s.includes('slice')) unit = 'slice';
  else unit = 'measure';
  return { quantity: qty, unit };
}

// ---- 5. Main ----------------------------------------------------------------
async function main() {
  console.log('Agent 6 — HF cocktail ingest starting');
  const existingTitles = loadExistingTitles();
  console.log(`Loaded ${existingTitles.size} existing cocktail titles for dedup`);

  const datasetId = 'yujinyang/cocktails_436';
  const csvUrl = `https://huggingface.co/datasets/${datasetId}/resolve/main/cocktails_436.csv`;
  console.log(`Fetching ${csvUrl}`);

  const res = await fetch(csvUrl, { headers: { 'User-Agent': UA, Accept: 'text/csv,*/*' } });
  if (!res.ok) {
    throw new Error(`HTTP ${res.status} fetching ${csvUrl}`);
  }
  const text = await res.text();
  console.log(`Downloaded ${text.length} bytes`);
  await sleep(1000);

  const rows = parseCsv(text);
  if (rows.length < 2) throw new Error('CSV has no data rows');
  const headers = rows[0].map((h) => h.trim());
  console.log(`Headers: ${headers.join(', ')}`);
  console.log(`Total rows: ${rows.length - 1}`);

  const stream = createWriteStream(OUT_NDJSON, { encoding: 'utf8' });
  const photos = [];
  let kept = 0;
  let dupes = 0;
  let skipped = 0;
  for (let i = 1; i < rows.length; i++) {
    const cocktail = rowToCocktail(headers, rows[i], datasetId);
    if (!cocktail) { skipped++; continue; }
    const key = normalizeTitle(cocktail.title);
    if (existingTitles.has(key)) { dupes++; continue; }
    existingTitles.add(key);
    stream.write(JSON.stringify(cocktail) + '\n');
    kept++;

    if (cocktail.image_url) {
      photos.push({
        title: cocktail.title,
        image_url: cocktail.image_url,
        image_description: cocktail.image_description,
        source_dataset: datasetId,
        source_dataset_license: 'apache-2.0',
        image_origin: 'thecocktaildb.com (URL reference; original CocktailDB image)',
        image_license_note:
          'Image is hosted by TheCocktailDB. CocktailDB images are publicly accessible reference images; for redistribution/in-app use, prefer to fetch directly from CocktailDB or self-host with attribution. License of individual images is not explicitly stated by the dataset author.',
        recommended_use: 'reference / link out — do NOT bake into app assets without verifying CocktailDB image rights',
      });
    }
  }
  await new Promise((r) => stream.end(r));

  writeFileSync(OUT_PHOTOS, JSON.stringify(photos, null, 2), 'utf8');

  console.log(`✓ Wrote ${kept} new cocktail records → ${OUT_NDJSON}`);
  console.log(`  Dupes skipped: ${dupes}`);
  console.log(`  Empty rows skipped: ${skipped}`);
  console.log(`✓ Wrote ${photos.length} photo refs → ${OUT_PHOTOS}`);

  // ---- Report -----------------------------------------------------------------
  const report = `# Agent 6 — Hugging Face cocktail ingest report
Generated: ${new Date().toISOString()}

## Summary
- **Datasets evaluated:** 23
- **Datasets accepted:** 1
- **Datasets rejected:** 22
- **New cocktail recipes (after dedup):** ${kept}
- **Duplicates skipped:** ${dupes}
- **Photo references collected:** ${photos.length}
- **Photos with confirmed permissive image license:** 0 (see notes below)

## Accepted datasets

### \`yujinyang/cocktails_436\`
- **License:** apache-2.0 (dataset packaging)
- **Size:** 436 cocktail recipes + 280 image files + QA pairs
- **Modality:** text + image
- **Schema:** id, name, alcoholic, category, glassType, instructions, drinkThumbnail, ingredients, ingredientMeasures, description, imageDescription
- **Source verification:** Schema and image URLs (\`thecocktaildb.com/images/media/drink/...\`) match TheCocktailDB exactly. This dataset is a repackage of TheCocktailDB enriched with AI-generated text via Qwen2.5-VL-7B. The Apache-2.0 license covers the dataset packaging + AI-generated descriptions. The underlying recipe content is from TheCocktailDB which we already ingest under \`cocktaildb-raw.ndjson\`.
- **Result:** ${kept} new (not in existing 426), ${dupes} duplicates dropped after title-normalized dedup.
- **Notes:** AI-generated \`description\` and \`imageDescription\` fields preserved as new metadata. \`drinkThumbnail\` URLs preserved as image references but not blindly assumed to be Apache-2.0 (see photo file).

## Rejected datasets

| Dataset | License | Reason for rejection |
| --- | --- | --- |
| \`erwanlc/cocktails_recipe\` | other | License \`other\` not on permissive allow-list. README does not clarify commercial use. |
| \`erwanlc/cocktails_recipe_no_brand\` | other | Same as above. Brand-stripped variant inherits same license problem. |
| \`brianarbuckle/cocktail_recipes\` | unknown | License field \`unknown\` — per constraints, ambiguous = reject. 875 recipes, semi-structured. |
| \`jrosseruk/cocktails-with-instructions\` | not specified | No license declared in cardData. 6,956 examples but ambiguous = reject. (Likely derived from erwanlc.) |
| \`Egrigor/Cocktails\` | not specified | No license. 78 recipes only. Reject. |
| \`motimmom/cocktails_clean_nobrand\` | not specified | No license; appears derived from erwanlc data (same row count: 6,956). Reject. |
| \`Howtointernetface/cocktail_dataset\` | openrail | License acceptable but storage is 0 bytes (only README + .gitattributes). No data to ingest. |
| \`tambascomarco35/cocktail-finetuning\` | unknown | Tagged \`license:unknown\`. 70KB CSV. Reject. |
| \`toiletsandpaper/cocktails_recipe_ru\` | mit (translation only) | Russian translation of \`erwanlc/cocktails_recipe\`. The MIT license covers translation, but underlying data is \`other\`-licensed → contaminated. Russian text also out of scope. Reject. |
| \`toiletsandpaper/cocktails_recipe_ru_small\` | mit | Same as above (smaller subset). Reject. |
| \`mclemcrew/MixologyDB\` | mit | Despite the name, this is about audio mixing in DAWs. Not drinks. Reject (irrelevant). |
| \`MocktaiLEngineer/qmsum-processed\` | mit | "Mocktail" is the username; dataset is QMSum meeting summarization. Not drinks. Reject (irrelevant). |
| \`Audi0417/Taiwan-drinks\` | mit | Taiwanese boba/coffee shop menu data (CoCo, 春水堂, 7-11, etc.). Not cocktails — bubble tea menu items. Out of scope for a cocktail catalog. Reject. |
| \`mlnomad/imnet1k_cocktail_shaker\` | not specified (ImageNet) | Images of cocktail shaker hardware (ImageNet class 503), not drinks. Plus ImageNet license is restrictive. Reject. |
| \`bhaskars113/whiskey_recipe_dataset\` | unknown | License unknown. Reject. |
| \`bhaskars113/whiskey-recipe\` | unknown | License unknown. Duplicate of above. Reject. |
| \`Rogudev/whiskey_dataset\` | mit | Synthetic whiskey *classification* dataset (tabular, not recipes). Reject (irrelevant). |
| \`Jaeuk-Han/korean-traditional-liquor-dataset\` | cc-by-nc-sa-2.0 | Non-commercial license. Reject. |
| \`lmmcfarland1/iowa_liquor_data_csv\` | cc-by-4.0 | License OK but content is Iowa state liquor *sales* data, not recipes. Reject (irrelevant). |
| \`VynerCK/Drinks-In-Iban-Language-Dataset\` | cc-by-nc-sa-4.0 | Non-commercial. Reject. |
| \`Var01/DrinksByMood\` | not specified | No license. Reject. |
| \`AdonisVainglory/Cocktailer\` | not specified | No license, n<1K. Reject. |
| \`cocktailpeanut/town\`, \`cocktailpeanut/friends\` | apache-2.0 | "Cocktail" is part of the user handle; datasets are not drink-related (other domain). Reject (irrelevant). |

(Additionally skipped without per-dataset card fetch: \`IR-Cocktail/*\` IR benchmark suite — information retrieval, not drinks; \`villekuosmanen/agilex_cocktail_sunset_*\` — robotics LeRobot data; \`Multi-Audio-Grounding/Cocktail_Party_Extraction\` and \`NeuroCodec/CocktailParty\` — audio "cocktail party effect" speech separation, not drinks; \`MinimaML/cocktail-6b\`, \`PXIN/reasoning-cocktail-6k\` — model training "cocktails" of mixed reasoning data; \`BangumiBase/bartenderkaminoglass\` — anime character images; \`cocktailpeanut/*\`, \`drinkcocoa/*\`, \`haduki33/*\`, \`philbutler/*\`, \`RoboCOIN/*\` — usernames or robotics/medical data unrelated to cocktail recipes.)

## Search terms used
\`cocktail\`, \`cocktails\`, \`drink\`, \`drinks\`, \`bartender\`, \`mixology\`, \`beverage\`, \`spirits\`, \`alcohol\`, \`recipe\` (filtered), \`wine\` (filtered), \`whiskey\`, \`tequila\`, \`gin\`, \`liquor\`, \`mocktail\`, \`cocktail+image\`, \`cocktail+photo\`, \`bar+recipe\`, \`thecocktaildb\`.

## Photo licensing notes
The accepted dataset references images via URLs pointing to \`thecocktaildb.com\`. While the dataset packaging itself is Apache-2.0, the actual image bytes belong to TheCocktailDB and have no explicit per-image license. We therefore:

1. Recorded these as **reference URLs** in \`agent6-hf-photos.json\`, NOT as files baked into the app.
2. Marked image_license_note for each entry so the user can audit before redistribution.
3. Counted **0 photos** in the "verified permissive license" total.

If you want to actually use these images in-app, the existing \`fetch-cocktail-photos.js\` / Wikimedia photo pipeline is the safer route — those licenses are explicit per file.

## Files written
- \`backend/ingest/cocktails/hf-cocktails.ndjson\` — ${kept} cocktail records
- \`backend/ingest/normalized/agent6-hf-photos.json\` — ${photos.length} image refs (reference only, license caveat included)
- \`backend/ingest/normalized/agent6-report.md\` — this file
`;
  writeFileSync(OUT_REPORT, report, 'utf8');
  console.log(`✓ Wrote report → ${OUT_REPORT}`);
}

main().catch((e) => {
  console.error('FATAL', e);
  process.exit(1);
});

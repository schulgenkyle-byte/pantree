#!/usr/bin/env node
/**
 * Deep cocktail recipe quality audit. 600 alcoholic cocktails across offsets 0..500.
 * Output CSV: backend/ingest/audit_v2_cocktails.csv
 */
const fs = require('fs');
const path = require('path');
const https = require('https');

const ADMIN_KEY = fs.readFileSync('C:/Users/12566/Downloads/PANTRIE_ADMIN_KEY.txt', 'utf8').trim();
const WORKER = 'https://pantrie-backend.schulgenkyle.workers.dev';
const OUT_CSV = path.join(__dirname, 'audit_v2_cocktails.csv');
const RAW_JSON = path.join(__dirname, 'audit_v2_cocktails_raw.json');

function fetchJson(url) {
  return new Promise((resolve, reject) => {
    const req = https.request(url, {
      method: 'GET',
      headers: { 'X-Admin-Key': ADMIN_KEY, 'User-Agent': 'audit-cocktails/1.0' },
    }, (res) => {
      const chunks = [];
      res.on('data', c => chunks.push(c));
      res.on('end', () => {
        const body = Buffer.concat(chunks).toString('utf8');
        if (res.statusCode !== 200) return reject(new Error(`HTTP ${res.statusCode}: ${body.slice(0, 200)}`));
        try { resolve(JSON.parse(body)); } catch (e) { reject(e); }
      });
    });
    req.on('error', reject);
    req.end();
  });
}

const SPIRIT_TERMS = [
  'gin', 'vodka', 'rum', 'tequila', 'whisk', 'bourbon', 'scotch', 'rye', 'brandy',
  'cognac', 'absinthe', 'mezcal', 'vermouth', 'campari', 'aperol', 'amaro', 'sherry',
  'port', 'liqueur', 'curacao', 'curaçao', 'chartreuse', 'benedictine', 'kirsch',
  'maraschino', 'cordial', 'bitters', 'cachaca', 'cachaça', 'pisco', 'arrack', 'genever',
  'schnapps', 'sambuca', 'ouzo', 'grappa', 'fernet', 'pernod', 'kahlua', 'baileys',
  'champagne', 'wine', 'beer', 'ale', 'cider', 'sake', 'soju', 'akvavit', 'aquavit',
  'liquor', 'spirits', 'cassis', 'triple sec', 'st-germain', 'st germain', 'apple-jack',
  'applejack', 'apple jack', 'rye-whiskey', 'jamaica rum', 'santa cruz', 'st croix',
  'old tom', 'plymouth', 'slivovitz', 'vermuth', 'vermooth', 'sloe', 'fernet',
  'eau de vie', 'eau-de-vie', 'amer ', 'picon', 'noyau', 'noyaux', 'curacoa',
  'creme de', 'crème de', 'creme-de', 'anisette', 'pernod', 'absinth', 'amer picon',
  'mead', 'mescal', 'tequila', 'aperitif', 'apéritif', 'digestif', 'liqueur',
  'rhum', 'claret', 'cointreau', 'dubonnet', 'calvados', 'madeira', 'rioja',
  'chambord', 'galliano', 'drambuie', 'frangelico', 'jagermeister', 'jägermeister',
  'midori', 'malibu', 'amaretto', 'limoncello', 'bourbon', 'okolehao',
  'arak', 'raki', 'soju', 'shochu', 'soochong', 'tincture', 'tinct.',
];
const MIXER_TERMS = [
  'soda', 'tonic', 'ginger', 'cola', 'juice', 'lemon', 'lime', 'orange', 'grapefruit',
  'pineapple', 'cranberry', 'tomato', 'water', 'cream', 'milk', 'syrup', 'sugar',
  'honey', 'grenadine', 'orgeat', 'falernum', 'mint', 'cucumber', 'egg', 'tea',
  'coffee', 'salt', 'pepper', 'cinnamon', 'nutmeg', 'mineral water', 'apollinaris',
  'seltzer', 'vichy', 'carbonic', 'club soda',
];
const GLASS_TERMS = [
  'glass', 'tumbler', 'goblet', 'snifter', 'flute', 'coupe', 'martini', 'cocktail glass',
  'highball', 'collins', 'rocks', 'old fashioned', 'mug', 'cup', 'punch bowl', 'pitcher',
  'wine glass', 'mixing glass', 'shaker', 'pony', 'jigger', 'beaker',
];
const METHOD_TERMS = [
  'stir', 'shake', 'shak', 'build', 'muddle', 'blend', 'strain', 'pour', 'serve',
  'mix', 'add', 'fill', 'shave', 'frappe', 'frappé', 'float', 'dash', 'top',
  'garnish', 'rim', 'twist', 'squeeze', 'combine', 'whisk', 'beat', 'churn',
];

function lc(s) {
  return (s || '').toString().toLowerCase()
    .normalize('NFD').replace(/[̀-ͯ]/g, '');
}
function hasAny(text, terms) { const t = lc(text); return terms.some(w => t.includes(lc(w))); }

function classifyCocktail(r) {
  const title = (r.title || '').trim();
  const ings = r.ingredients || [];
  const ingNames = ings.map(i => lc(i.name)).join(' ');
  const ingFull = ings.map(i => `${i.quantity || ''} ${i.unit || ''} ${i.name || ''}`).join(' | ');
  const steps = r.steps || (r.instructions ? r.instructions.split(' • ') : []);
  const stepText = steps.join(' ');
  const allText = `${ingFull} ${stepText} ${r.original_text || ''} ${r.description || ''}`;
  const allTextLc = lc(allText);

  const stepCount = steps.filter(s => s && s.trim().length > 0).length;
  const ingCount = ings.length;

  const hasPhoto = !!(r.image_url && r.image_url.trim() && r.image_url !== 'null');
  const isHistoric = r.is_historic === true || r.is_historic === 1;
  const abvPresent = r.abv_percent != null && r.abv_percent !== '' && Number(r.abv_percent) > 0;
  const glassPresent = !!(r.glass_type && r.glass_type.toString().trim()) ||
    hasAny(allText, GLASS_TERMS);
  const garnishPresent = !!(r.garnish && r.garnish.toString().trim());
  const methodPresent = !!(r.method && r.method.toString().trim()) ||
    hasAny(stepText, METHOD_TERMS);

  // Title sanity
  const titleBad = !title ||
    /^recipe[\s_-]*\d+$/i.test(title) ||
    /^[\s\d]+$/.test(title) ||
    title.length < 2;

  // Empty / no instructions
  const noInstructions = stepCount === 0 || !stepText.trim();
  // No ingredients
  const noIngredients = ingCount === 0;

  // All ingredient names "undefined" or single ing with no measurement
  const allUndefined = ingCount > 0 && ings.every(i =>
    !i.name || lc(i.name) === 'undefined' || lc(i.name) === 'null');
  const lonelyNoMeasure = ingCount === 1 &&
    !ings[0].quantity && !ings[0].unit;

  // Spirit / mixer / glass / method / garnish reference check
  const refsAnyMixology = hasAny(allText, SPIRIT_TERMS) ||
    hasAny(allText, MIXER_TERMS) ||
    hasAny(allText, GLASS_TERMS) ||
    hasAny(stepText, METHOD_TERMS) ||
    garnishPresent;
  const stepsTooShallow = !refsAnyMixology;

  // Method null AND instructions don't describe stir/shake/build/muddle
  const methodNullNoMix = (!r.method || !r.method.toString().trim()) &&
    !hasAny(stepText, ['stir', 'shake', 'shak', 'build', 'muddle', 'blend']);

  // ABV claimed but no alcohol
  const hasAlcoholInIngs = hasAny(ingNames, SPIRIT_TERMS) || hasAny(allTextLc, SPIRIT_TERMS);
  const abvButNoBooze = abvPresent && !hasAlcoholInIngs;

  // Title looks numeric/garbage
  const titleNumeric = /^recipe[\s_-]*\d+$/i.test(title) || /^\d+$/.test(title);

  const reasons = [];
  let verdict = 'KEEP';

  // For bootlegger items, the recipe lives in original_text + steps. ingredient_count is
  // often 0 because the ingest never tokenised it. Treat that as VALID provided the
  // steps reference real mixology (spirits/glass/method).
  const bootleggerValid = isHistoric && r.original_text && r.original_text.trim().length > 30 &&
    refsAnyMixology;

  // HARD DELETE
  if (noInstructions) { reasons.push('no_instructions'); verdict = 'DELETE'; }
  if (noIngredients && !bootleggerValid) { reasons.push('no_ingredients'); verdict = 'DELETE'; }
  if (allUndefined) { reasons.push('all_undefined_ingredients'); verdict = 'DELETE'; }
  if (lonelyNoMeasure && !bootleggerValid) { reasons.push('single_ing_no_measure'); verdict = 'DELETE'; }
  if (titleBad || titleNumeric) { reasons.push('title_garbage'); verdict = 'DELETE'; }
  if (stepsTooShallow && stepCount <= 1 && !bootleggerValid) {
    // only delete if instructions are also shallow AND no method/spirits at all
    reasons.push('no_mixology_signal');
    verdict = 'DELETE';
  }
  if (methodNullNoMix && !methodPresent && !bootleggerValid) { reasons.push('no_method_signal'); verdict = 'DELETE'; }
  if (abvButNoBooze) { reasons.push('abv_without_alcohol'); verdict = 'DELETE'; }

  if (verdict === 'DELETE') {
    return buildResult(r, verdict, reasons, hasPhoto, isHistoric, stepCount, ingCount, abvPresent, glassPresent);
  }

  // FIX heuristics
  const fixReasons = [];
  // Vague single sentence ("Mix together")
  const vagueSingle = stepCount === 1 && stepText.trim().split(/\s+/).length <= 4;
  if (vagueSingle) fixReasons.push('vague_one_sentence');

  // Glass missing on classic
  const isClassic = /(martini|negroni|manhattan|old.?fashioned|daiquiri|margarita|sidecar|sazerac|aviation|gimlet|cosmopolitan|whiskey sour|pisco sour|tom collins|french 75|mojito)/i.test(title);
  if (isClassic && !glassPresent) fixReasons.push('classic_missing_glass');

  // ABV missing
  if (!abvPresent) fixReasons.push('abv_missing');

  // No image (not for bootlegger)
  if (!hasPhoto && !isHistoric) fixReasons.push('no_image_modern');

  // No spirit at all but classified alcoholic - sanity flag
  if (!hasAlcoholInIngs) fixReasons.push('no_spirit_in_ings');

  // Glass type field missing entirely
  if (!r.glass_type) fixReasons.push('glass_type_field_missing');

  // Method field missing entirely
  if (!r.method) fixReasons.push('method_field_missing');

  if (fixReasons.length >= 2) {
    verdict = 'FIX';
    reasons.push(...fixReasons);
  } else if (fixReasons.length === 1 && (fixReasons[0] === 'vague_one_sentence' || fixReasons[0] === 'classic_missing_glass' || fixReasons[0] === 'no_spirit_in_ings')) {
    verdict = 'FIX';
    reasons.push(...fixReasons);
  }

  return buildResult(r, verdict, reasons, hasPhoto, isHistoric, stepCount, ingCount, abvPresent, glassPresent);
}

function buildResult(r, verdict, reasons, hasPhoto, isHistoric, stepCount, ingCount, abvPresent, glassPresent) {
  return {
    id: r.id,
    title: r.title || '',
    verdict,
    reason: reasons.join(';'),
    has_photo: hasPhoto ? 1 : 0,
    is_historic: isHistoric ? 1 : 0,
    step_count: stepCount,
    ingredient_count: ingCount,
    abv_present: abvPresent ? 1 : 0,
    glass_present: glassPresent ? 1 : 0,
  };
}

function csvEscape(v) {
  if (v == null) return '';
  const s = String(v);
  if (/[",\n\r]/.test(s)) return `"${s.replace(/"/g, '""')}"`;
  return s;
}

(async () => {
  const offsets = [0, 100, 200, 300, 400, 500];
  const allRecipes = [];
  for (const off of offsets) {
    const url = `${WORKER}/admin/sample-recipes?content_type=cocktail&limit=100&offset=${off}&full=1`;
    process.stderr.write(`Fetching offset ${off}... `);
    const data = await fetchJson(url);
    const recs = data.recipes || [];
    process.stderr.write(`got ${recs.length}\n`);
    allRecipes.push(...recs);
  }
  fs.writeFileSync(RAW_JSON, JSON.stringify(allRecipes, null, 2));
  process.stderr.write(`Total fetched: ${allRecipes.length}\n`);

  const results = allRecipes.map(classifyCocktail);

  const header = 'id,title,verdict,reason,has_photo,is_historic,step_count,ingredient_count,abv_present,glass_present\n';
  const lines = results.map(r =>
    [r.id, r.title, r.verdict, r.reason, r.has_photo, r.is_historic, r.step_count, r.ingredient_count, r.abv_present, r.glass_present]
      .map(csvEscape).join(',')
  );
  fs.writeFileSync(OUT_CSV, header + lines.join('\n') + '\n');
  process.stderr.write(`Wrote ${OUT_CSV}\n`);

  // Stats
  const counts = { KEEP: 0, FIX: 0, DELETE: 0 };
  const histCounts = { KEEP: 0, FIX: 0, DELETE: 0 };
  const modCounts = { KEEP: 0, FIX: 0, DELETE: 0 };
  const reasonCounts = {};
  const fixReasonCounts = {};
  const abvWeird = [];
  for (const r of results) {
    counts[r.verdict]++;
    if (r.is_historic) histCounts[r.verdict]++; else modCounts[r.verdict]++;
    const rs = r.reason ? r.reason.split(';').filter(Boolean) : [];
    for (const reason of rs) {
      if (r.verdict === 'DELETE') reasonCounts[reason] = (reasonCounts[reason] || 0) + 1;
      else if (r.verdict === 'FIX') fixReasonCounts[reason] = (fixReasonCounts[reason] || 0) + 1;
    }
    if (rs.includes('abv_without_alcohol')) abvWeird.push(r);
  }

  const sortByCount = (obj) => Object.entries(obj).sort((a, b) => b[1] - a[1]);
  const stats = {
    total: results.length,
    counts,
    historic: histCounts,
    modern: modCounts,
    top_delete_reasons: sortByCount(reasonCounts).slice(0, 10),
    top_fix_reasons: sortByCount(fixReasonCounts).slice(0, 10),
    abv_weird_count: abvWeird.length,
    abv_weird_sample: abvWeird.slice(0, 8).map(r => ({ id: r.id, title: r.title })),
  };
  fs.writeFileSync(path.join(__dirname, 'audit_v2_cocktails_stats.json'), JSON.stringify(stats, null, 2));
  console.log(JSON.stringify(stats, null, 2));
})().catch(e => { console.error(e); process.exit(1); });

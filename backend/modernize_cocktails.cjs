#!/usr/bin/env node
/**
 * modernize_cocktails.cjs
 *
 * Reads historic cocktail rows (from a D1 dump) and generates a modernized_text
 * for each using a ruleset. Produces an SQL file of UPDATE statements which
 * are then applied with `wrangler d1 execute --file ...`.
 *
 * Run:
 *   node modernize_cocktails.cjs <input.json> <output.sql>
 */
const fs = require('fs');

function esc(s) {
  return String(s).replace(/'/g, "''");
}

const GLOSSARY = {
  'holland gin': 'genever, a malty Dutch-style gin',
  'genever': 'a malty Dutch-style gin',
  'old tom gin': 'a lightly sweetened gin',
  'jamaica rum': 'dark funky rum',
  'santa cruz rum': 'aged rum',
  'batavia arrack': 'an Indonesian sugarcane/rice spirit',
  'maraschino': 'cherry liqueur like Luxardo',
  'absinthe': 'or pastis if unavailable',
  'curacoa': 'orange liqueur',
  'curacao': 'orange liqueur',
  'orgeat': 'almond syrup',
  'gum syrup': 'simple syrup',
  'bar sugar': 'superfine sugar',
  'cut loaf sugar': 'sugar cubes',
  'calisaya': 'a quinine-based amaro',
  'creme yvette': 'violet liqueur',
  'abricontine': 'apricot liqueur',
  'apricontine': 'apricot liqueur',
  'bevo': 'non-alcoholic near-beer',
  'tokay': 'a sweet Hungarian wine',
  'catawba': 'a sweet American grape wine',
  'vin mariani': 'coca wine (historical; omit)',
  'sarsaparilla': 'root beer',
  'apollinaris': 'sparkling mineral water',
  'seltzer': 'club soda',
  'kuemmel': 'caraway liqueur',
  'kummel': 'caraway liqueur',
  'acid phosphate': 'acid phosphate (or a few drops lemon juice)',
  'jamaica ginger': 'ginger tincture (or a pinch of ground ginger)',
  'angostura': 'Angostura bitters',
  "boker's bitters": "Boker's bitters (or sub Angostura)",
  'bokers bitters': "Boker's bitters (or sub Angostura)",
  'peychaud': "Peychaud's bitters",
  'creme de menthe': 'mint liqueur',
  'creme de cacao': 'chocolate liqueur',
  'creme de rose': 'rose liqueur',
  'creme de violette': 'violet liqueur',
  'anisette': 'anise liqueur',
  'benedictine': 'Benedictine',
  'chartreuse': 'Chartreuse',
  'forbidden fruit': 'Forbidden Fruit, a grapefruit liqueur',
  'parfait amour': 'Parfait Amour, a violet/vanilla liqueur',
  'applejack': 'apple brandy',
  'new england rum': 'New England-style dark rum',
};

// Numeric-aware rules: handle leading counts like "1 pony", "1-1/2 jiggers", "1/2 wineglass".
// These run before the generic word substitutions.
function convertCountedMeasures(text) {
  // Helper to parse a count like "1", "1/2", "1-1/2", "2/3" -> number
  const parse = (s) => {
    s = s.trim();
    if (/^\d+-\d+\/\d+$/.test(s)) {
      const [w, frac] = s.split('-');
      const [n, d] = frac.split('/').map(Number);
      return Number(w) + n / d;
    }
    if (/^\d+\/\d+$/.test(s)) {
      const [n, d] = s.split('/').map(Number);
      return n / d;
    }
    if (/^\d+(\.\d+)?$/.test(s)) return Number(s);
    return null;
  };
  const fmt = (n) => {
    if (Number.isInteger(n)) return String(n);
    // Try to express as fraction if clean
    const known = { 0.25: '1/4', 0.333: '1/3', 0.5: '1/2', 0.666: '2/3', 0.75: '3/4' };
    const rounded = Math.round(n * 1000) / 1000;
    if (known[rounded]) return known[rounded];
    // express as mixed
    const whole = Math.floor(n);
    const frac = n - whole;
    const fracStr = known[Math.round(frac * 1000) / 1000];
    if (whole > 0 && fracStr) return whole + ' ' + fracStr;
    return n.toFixed(2).replace(/\.?0+$/, '');
  };
  const unitRules = [
    { re: /(\d+(?:[-\s]\d+\/\d+|\/\d+)?)\s*wine[- ]?glass(?:ful)?(?:\s+of)?/gi, oz: 2 },
    { re: /(\d+(?:[-\s]\d+\/\d+|\/\d+)?)\s*wineglass(?:ful)?(?:\s+of)?/gi, oz: 2 },
    { re: /(\d+(?:[-\s]\d+\/\d+|\/\d+)?)\s*pony[- ]?glass(?:\s+of)?/gi, oz: 1 },
    { re: /(\d+(?:[-\s]\d+\/\d+|\/\d+)?)\s*ponies/gi, oz: 1 },
    { re: /(\d+(?:[-\s]\d+\/\d+|\/\d+)?)\s*pony(?:\s+of)?/gi, oz: 1 },
    { re: /(\d+(?:[-\s]\d+\/\d+|\/\d+)?)\s*jiggers?(?:\s+of)?/gi, oz: 1.5 },
    { re: /(\d+(?:[-\s]\d+\/\d+|\/\d+)?)\s*gills?/gi, oz: 4 },
  ];
  let t = text;
  for (const { re, oz } of unitRules) {
    t = t.replace(re, (m, countStr) => {
      const n = parse(countStr.replace(/\s/g, '-'));
      if (n == null) return m;
      const total = n * oz;
      return `${fmt(total)} oz`;
    });
  }
  return t;
}

const MEASURE_RULES = [
  // Fallback rules for unqualified mentions
  [/\bwine[- ]?glassful\b/gi, '2 oz'],
  [/\bwine[- ]?glass(?:\s+of)?\b/gi, '2 oz'],
  [/\bwineglass(?:ful)?(?:\s+of)?\b/gi, '2 oz'],
  [/\bpony[- ]?glass(?:\s+of)?\b/gi, '1 oz'],
  [/\bponies\b/gi, 'oz'],
  [/\bpony(?:\s+of)?\b/gi, '1 oz'],
  [/\bjiggers\b/gi, 'oz'],
  [/\bjigger(?:\s+of)?\b/gi, '1.5 oz'],
  [/\btablespoonfuls?\b/gi, 'tbsp'],
  [/\btable[- ]?spoons?(?:ful)?\b/gi, 'tbsp'],
  [/\bteaspoonfuls?\b/gi, 'tsp'],
  [/\btea[- ]?spoons?(?:ful)?\b/gi, 'tsp'],
  [/\ba dash\b/gi, '1 dash'],
  [/\bgills?\b/gi, '4 oz (1 gill)'],
  [/\bshaved ice\b/gi, 'crushed ice'],
  [/\bfine ice\b/gi, 'crushed ice'],
  [/\blump ice\b/gi, 'ice cubes'],
  [/\blumps? (?:of )?ice\b/gi, 'ice cubes'],
  [/\bpiece(?:s)? of ice\b/gi, 'ice cubes'],
  [/\bflowing bowl\b/gi, 'large punch bowl'],
  [/\bfizz[- ]?glass\b/gi, 'highball glass'],
  [/\bsmall bar glass\b/gi, 'rocks glass'],
  [/\blarge bar glass\b/gi, 'mixing glass'],
  [/\bmedium bar glass\b/gi, 'mixing glass'],
  [/\btumblers?\b/gi, 'rocks glass'],
  [/\bshell glass\b/gi, 'highball glass'],
  [/\bhot water glass\b/gi, 'mug'],
  [/\bclaret glass\b/gi, 'wine glass'],
  [/\bsherry glass\b/gi, 'cordial glass'],
  [/\bchampagne goblet\b/gi, 'champagne flute'],
  [/\bpunch glass(?:es)?\b/gi, 'punch cups'],
  [/\bapollinaris water\b/gi, 'sparkling mineral water'],
  [/\bapollinaris\b/gi, 'sparkling mineral water'],
  [/\bseltzer water\b/gi, 'club soda'],
  [/\bseltzer\b/gi, 'club soda'],
  [/\baerated water\b/gi, 'club soda'],
  [/\bcarbonated water\b/gi, 'club soda'],
  [/\bplain soda\b/gi, 'club soda'],
  [/\bsweet soda\b/gi, 'lemon-lime soda'],
  [/\bpowdered sugar\b/gi, 'superfine sugar'],
  [/\bbar sugar\b/gi, 'superfine sugar'],
  [/\bcut loaf sugar\b/gi, 'sugar cube'],
  [/\blumps? (?:of )?sugar\b/gi, 'sugar cube'],
  [/\bdomino sugar\b/gi, 'sugar cube'],
  [/\bloaf sugar\b/gi, 'sugar cube'],
  [/\bcuracoa\b/gi, 'curacao'],
];

const GLASS_MAP = {
  cocktail: 'cocktail glass',
  champagne: 'champagne flute',
  wine: 'wine glass',
  sherry: 'cordial glass',
  highball: 'highball glass',
  collins: 'collins glass',
  rocks: 'rocks glass',
  bar: 'mixing glass',
  punch: 'punch cups',
  punch_bowl: 'punch bowl',
  coupe: 'coupe',
  sour: 'coupe',
  rickey: 'highball glass',
  fizz: 'highball glass',
  goblet: 'goblet',
  mug: 'mug',
  pint: 'pint glass',
  stein: 'stein',
  toddy: 'toddy mug',
};

function applyMeasureRules(text) {
  let t = convertCountedMeasures(text);
  for (const [re, repl] of MEASURE_RULES) {
    t = t.replace(re, repl);
  }
  // Collapse "0 oz"/"N oz" stray spacing
  t = t.replace(/\s+oz\b/g, ' oz');
  return t;
}

function annotate(text) {
  let t = text;
  const added = new Set();
  for (const [term, gloss] of Object.entries(GLOSSARY)) {
    const re = new RegExp(
      '\\b' + term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\b',
      'i',
    );
    const m = t.match(re);
    if (m && !added.has(term)) {
      const after = t
        .slice(m.index + m[0].length, m.index + m[0].length + 80)
        .toLowerCase();
      if (!after.trim().startsWith('(')) {
        t =
          t.slice(0, m.index + m[0].length) +
          ' (' +
          gloss +
          ')' +
          t.slice(m.index + m[0].length);
        added.add(term);
      }
    }
  }
  return t;
}

function extractIngredientsAndPrep(orig) {
  const lines = orig
    .split(/\n+/)
    .map((l) => l.trim())
    .filter(Boolean);

  let startIdx = 0;
  if (
    lines.length &&
    /^[A-Z0-9"'\-,.!? ()\/&]+$/.test(lines[0]) &&
    lines[0].length < 80
  ) {
    startIdx = 1;
  }

  const ingLines = [];
  const prepLines = [];
  const prepVerbRe =
    /^(?:shake|stir|strain|pour|add|dress|garnish|serve|mix|twist|grate|ornament|decorate|place|beat|cover|ignite|break|bruise|let|top off|rub|float|this|if|for|when|set|bottle|cork)\b/i;
  const glassRe = /glass|tumbler|pitcher|mug|bowl|stein|shaker/i;

  for (let i = startIdx; i < lines.length; i++) {
    const line = lines[i];
    if (prepVerbRe.test(line)) {
      prepLines.push(line);
      continue;
    }
    if (/\b(fill|use|into|drop|put)\b/i.test(line) && glassRe.test(line)) {
      prepLines.push(line);
      continue;
    }
    // Lines that just describe setup/serving vessel (no count / ingredient noun)
    if (
      /^(?:mixing glass|bar glass|shell glass|six[- ]?ounce|champagne glass|cocktail glass|sour glass|punch glass|wine glass|sherry glass|stem glass|highball|fancy|tall)/i.test(
        line,
      )
    ) {
      prepLines.push(line);
      continue;
    }
    // Lines ending with "and serve" that are trailing instructions
    if (/\band serve\.?$/i.test(line) && line.length < 60) {
      prepLines.push(line);
      continue;
    }
    ingLines.push(line);
  }

  return { ingredients: ingLines, prep: prepLines };
}

function cleanIngredient(line) {
  let l = applyMeasureRules(line);
  l = l.replace(/\b1\/2\s+juice of\s+(\d+)\s+lemon/gi, 'juice of 1/2 lemon');
  l = l.replace(/\b1\/4\s+juice of\s+(\d+)\s+lemon/gi, 'juice of 1/4 lemon');
  l = l.replace(/\b(\d+)\s+lemon'?s?\s+juice\b/gi, 'juice of $1 lemon');
  l = l.replace(/\.\s*$/, '');
  return l.trim();
}

function cleanPrep(line) {
  let l = applyMeasureRules(line);
  l = l.replace(/\s+/g, ' ').trim();
  return l;
}

function modernize(row) {
  const orig = row.original_text || '';
  if (!orig || orig.trim().length < 20) {
    return 'Unable to modernize - incomplete original.';
  }
  if (/same as .*but use/i.test(orig) && orig.length < 200) {
    return (
      'See the parent recipe referenced in the original; substitute as noted. ' +
      applyMeasureRules(orig).replace(/\s+/g, ' ').trim()
    );
  }

  const { ingredients, prep } = extractIngredientsAndPrep(orig);

  if (ingredients.length === 0) {
    const rewritten = annotate(applyMeasureRules(orig))
      .replace(/\s+/g, ' ')
      .trim();
    return rewritten;
  }

  const bullets = ingredients
    .map(cleanIngredient)
    .filter(Boolean)
    .map((i) => '- ' + i);
  const prepClean = prep.map(cleanPrep).filter(Boolean);

  let methodLines = prepClean;
  if (methodLines.length === 0) {
    methodLines = [
      'Combine ingredients over ice in a mixing glass or shaker, stir or shake to chill, and strain into a chilled glass.',
    ];
  }

  const glass = (row.glass_type || '').toLowerCase();
  const glassHint = GLASS_MAP[glass];
  if (
    glassHint &&
    !methodLines.join(' ').toLowerCase().includes(glassHint.split(' ')[0])
  ) {
    methodLines.push(`Serve in a ${glassHint}.`);
  }

  let out =
    'Ingredients:\n' +
    bullets.join('\n') +
    '\n\nMethod:\n' +
    methodLines.map((m, i) => `${i + 1}. ${m}`).join('\n');
  out = annotate(out);
  return out;
}

function main() {
  const [, , inPath, outPath] = process.argv;
  if (!inPath || !outPath) {
    console.error('Usage: node modernize_cocktails.cjs <input.json> <output.sql>');
    process.exit(1);
  }
  const raw = fs.readFileSync(inPath, 'utf8');
  const parsed = JSON.parse(raw);
  const rows = Array.isArray(parsed)
    ? parsed[0]?.results || parsed
    : parsed.results || [];

  const sqlLines = [];
  let modernized = 0;
  let skipped = 0;
  const examples = [];

  for (const row of rows) {
    const modern = modernize(row);
    if (/^unable to modernize/i.test(modern)) skipped++;
    else modernized++;
    sqlLines.push(
      `UPDATE recipe SET modernized_text = '${esc(modern)}' WHERE id = '${esc(row.id)}';`,
    );
    if (
      examples.length < 3 &&
      modern.length > 180 &&
      !/unable to modernize/i.test(modern)
    ) {
      examples.push({ title: row.title, modern });
    }
  }

  fs.writeFileSync(outPath, sqlLines.join('\n') + '\n');
  console.log(
    JSON.stringify({ total: rows.length, modernized, skipped, examples }, null, 2),
  );
}

main();

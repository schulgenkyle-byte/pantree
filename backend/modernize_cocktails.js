#!/usr/bin/env node
/**
 * modernize_cocktails.js
 *
 * Reads historic cocktail rows (from a D1 dump) and generates a modernized_text
 * for each using a ruleset. Produces an SQL file of UPDATE statements which
 * are then applied with `wrangler d1 execute --file ...`.
 *
 * Run:
 *   node modernize_cocktails.js <input.json> <output.sql>
 */
const fs = require('fs');

function esc(s) {
  // Escape single quotes for SQL string literal.
  return String(s).replace(/'/g, "''");
}

// Known ingredient annotations -- added in parens the first time encountered.
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
  'bevo': 'non-alcoholic near-beer (sub alcohol-free beer)',
  'tokay': 'a sweet Hungarian wine',
  'catawba': 'a sweet American grape wine',
  'vin mariani': 'coca wine (omit; historical)',
  'sarsaparilla': 'root beer',
  'apollinaris': 'sparkling mineral water',
  'seltzer': 'club soda',
  'kuemmel': 'caraway liqueur',
  'kummel': 'caraway liqueur',
  'acid phosphate': 'acid phosphate (or a few drops lemon juice)',
  'jamaica ginger': 'ginger tincture (or a pinch of ground ginger)',
  'angostura': 'Angostura bitters',
  'boker\'s bitters': 'Boker\'s bitters (or sub Angostura)',
  'bokers bitters': 'Boker\'s bitters (or sub Angostura)',
  'peychaud': 'Peychaud\'s bitters',
  'creme de menthe': 'creme de menthe (mint liqueur)',
  'creme de cacao': 'creme de cacao (chocolate liqueur)',
  'creme de rose': 'rose liqueur',
  'creme de violette': 'violet liqueur',
  'anisette': 'anise liqueur',
  'benedictine': 'Benedictine',
  'chartreuse': 'Chartreuse',
  'grenadine': 'grenadine',
  'forbidden fruit': 'Forbidden Fruit (grapefruit liqueur)',
  'parfait amour': 'Parfait Amour (violet/vanilla liqueur)',
  'applejack': 'applejack (apple brandy)',
  'new england rum': 'New England-style dark rum',
  'sloe gin': 'sloe gin',
  'irish whiskey': 'Irish whiskey',
  'scotch whiskey': 'Scotch whisky',
  'rye whiskey': 'rye whiskey',
  'bourbon whiskey': 'bourbon',
};

// Map measurement phrases -> modern measurements. Order matters (longer first).
const MEASURE_RULES = [
  [/\bwine[- ]?glassful\b/gi, '2 oz'],
  [/\bwine[- ]?glass(?:\s+of)?\b/gi, '2 oz'],
  [/\bwineglass(?:ful)?(?:\s+of)?\b/gi, '2 oz'],
  [/\bpony[- ]?glass(?:\s+of)?\b/gi, '1 oz'],
  [/\bponies\b/gi, 'oz (1 oz each)'],
  [/\bpony(?:\s+of)?\b/gi, '1 oz'],
  [/\bjiggers\b/gi, 'oz (1.5 oz each jigger)'],
  [/\bjigger(?:\s+of)?\b/gi, '1.5 oz'],
  [/\btablespoonfuls?\b/gi, 'tbsp'],
  [/\btable[- ]?spoons?(?:ful)?\b/gi, 'tbsp'],
  [/\bteaspoonfuls?\b/gi, 'tsp'],
  [/\btea[- ]?spoons?(?:ful)?\b/gi, 'tsp'],
  [/\ba dash\b/gi, '1 dash'],
  [/\bgills?\b/gi, '4 oz (1 gill)'],
  [/\bshaved ice\b/gi, 'crushed ice'],
  [/\bfine ice\b/gi, 'crushed ice'],
  [/\bcracked ice\b/gi, 'cracked ice'],
  [/\blump ice\b/gi, 'ice cubes'],
  [/\blumps? (?:of )?ice\b/gi, 'ice cubes'],
  [/\bpiece(?:s)? of ice\b/gi, 'ice cubes'],
  [/\bflowing bowl\b/gi, 'large punch bowl'],
  [/\bfizz[- ]?glass\b/gi, 'highball glass'],
  [/\bsmall bar glass\b/gi, 'rocks glass'],
  [/\blarge bar glass\b/gi, 'collins glass'],
  [/\bmedium bar glass\b/gi, 'mixing glass'],
  [/\btumblers?\b/gi, 'rocks glass'],
  [/\bshell glass\b/gi, 'highball glass'],
  [/\bgoblets?\b/gi, 'goblet'],
  [/\bsour glass\b/gi, 'coupe'],
  [/\bhot water glass\b/gi, 'mug'],
  [/\bclaret glass\b/gi, 'wine glass'],
  [/\bsherry glass\b/gi, 'cordial glass'],
  [/\bchampagne goblet\b/gi, 'champagne flute'],
  [/\bpunch glass(?:es)?\b/gi, 'punch cups'],
  [/\bmixing glass\b/gi, 'mixing glass'],
  [/\bbar spoon\b/gi, 'bar spoon'],
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
  [/\blump(?:s)? (?:of )?sugar\b/gi, 'sugar cube'],
  [/\bdomino sugar\b/gi, 'sugar cube'],
  [/\bloaf sugar\b/gi, 'sugar cube'],
  [/\bcurac[o]a\b/gi, 'curacao'],
];

// Glass type -> modern glass suggestion (for serve line if we can infer it).
const GLASS_MAP = {
  'cocktail': 'cocktail glass',
  'champagne': 'champagne flute',
  'wine': 'wine glass',
  'sherry': 'cordial glass',
  'highball': 'highball glass',
  'collins': 'collins glass',
  'rocks': 'rocks glass',
  'bar': 'mixing glass',
  'punch': 'punch cups',
  'punch_bowl': 'punch bowl',
  'coupe': 'coupe',
  'sour': 'coupe',
  'rickey': 'highball glass',
  'fizz': 'highball glass',
  'goblet': 'goblet',
  'mug': 'mug',
  'pint': 'pint glass',
  'stein': 'stein',
  'toddy': 'toddy mug',
};

function applyMeasureRules(text) {
  let t = text;
  for (const [re, repl] of MEASURE_RULES) {
    t = t.replace(re, repl);
  }
  return t;
}

function annotate(text) {
  // Add parenthetical glosses the first time an unusual term appears.
  let t = text;
  const added = new Set();
  for (const [term, gloss] of Object.entries(GLOSSARY)) {
    // only annotate the first occurrence; case-insensitive match whole word
    const re = new RegExp('\\b' + term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\b', 'i');
    const m = t.match(re);
    if (m && !added.has(term)) {
      // Only annotate if gloss isn't already basically in the text.
      const after = t.slice(m.index + m[0].length, m.index + m[0].length + 60).toLowerCase();
      if (!after.includes(gloss.slice(0, 12).toLowerCase())) {
        t = t.slice(0, m.index + m[0].length)
          + ' (' + gloss + ')'
          + t.slice(m.index + m[0].length);
        added.add(term);
      }
    }
  }
  return t;
}

// Parse ingredients and prep from the original_text. The Bullock-era format
// is typically: TITLE\n\n<glass line>\n\n<ingredient>\n\n<ingredient>...\n\n<prep>.
function extractIngredientsAndPrep(orig) {
  // Split into logical lines (collapse multi-newlines).
  const lines = orig
    .split(/\n+/)
    .map(l => l.trim())
    .filter(Boolean);

  // Drop the title line (first ALL-CAPS or near all-caps line).
  let startIdx = 0;
  if (lines.length && /^[A-Z0-9"'\-,.!? ()\/&]+$/.test(lines[0]) && lines[0].length < 80) {
    startIdx = 1;
  }

  const ingLines = [];
  const prepLines = [];
  const prepVerbRe = /^(?:shake|stir|strain|fill|pour|add|dress|garnish|serve|mix|twist|grate|ornament|decorate|place|put|beat|cover|ignite|break|bruise|let|top off|rub|dash|drop|float|use|this|if|for|when|set|into|bottle|cork)\b/i;
  const glassRe = /glass|tumbler|pitcher|mug|bowl|stein/i;

  for (let i = startIdx; i < lines.length; i++) {
    const line = lines[i];
    if (prepVerbRe.test(line) || /\bshaker\b/i.test(line)) {
      prepLines.push(line);
      continue;
    }
    // "Fill ... glass ... full of ... Ice." lines are setup, not ingredients.
    if (/\b(fill|use|into|drop)\b/i.test(line) && glassRe.test(line)) {
      prepLines.push(line);
      continue;
    }
    // Treat remaining lines as ingredients.
    ingLines.push(line);
  }

  return { ingredients: ingLines, prep: prepLines };
}

// Normalize an ingredient line: apply measure rules, basic cleanup.
function cleanIngredient(line) {
  let l = applyMeasureRules(line);
  // "1/2 juice of 1 lemon" -> "juice of 1/2 lemon"
  l = l.replace(/\b1\/2\s+juice of\s+(\d+)\s+lemon/gi, 'juice of 1/2 lemon');
  l = l.replace(/\b1\/4\s+juice of\s+(\d+)\s+lemon/gi, 'juice of 1/4 lemon');
  // "1 lemon's juice" -> "juice of 1 lemon"
  l = l.replace(/\b(\d+)\s+lemon'?s?\s+juice\b/gi, 'juice of $1 lemon');
  // Remove trailing period.
  l = l.replace(/\.\s*$/, '');
  // Title-case leading letter? Leave as-is.
  return l.trim();
}

function cleanPrep(line) {
  let l = applyMeasureRules(line);
  l = l.replace(/\s+/g, ' ').trim();
  return l;
}

// Produce a concise modernized_text.
function modernize(row) {
  const orig = row.original_text || '';
  if (!orig || orig.trim().length < 20) {
    return 'Unable to modernize - incomplete original.';
  }

  // Detect "same as X" / reference-only originals.
  if (/same as .*but use/i.test(orig) && orig.length < 160) {
    // leave it to manual; produce a note.
    return `See the parent recipe referenced in the original; substitute as directed. ${applyMeasureRules(orig).replace(/\s+/g, ' ').trim()}`;
  }

  const { ingredients, prep } = extractIngredientsAndPrep(orig);

  if (ingredients.length === 0) {
    // Fall back: just apply measurement rules to full text.
    const rewritten = annotate(applyMeasureRules(orig)).replace(/\s+/g, ' ').trim();
    return rewritten;
  }

  const bullets = ingredients.map(cleanIngredient).filter(Boolean).map(i => '- ' + i);
  const prepClean = prep.map(cleanPrep).filter(Boolean);

  // Decide a terse method line if prep is empty.
  let methodLines = prepClean;
  if (methodLines.length === 0) {
    methodLines = ['Combine all ingredients over ice, stir or shake as appropriate, and strain into a chilled glass.'];
  }

  // Optionally append a glass recommendation.
  const glass = (row.glass_type || '').toLowerCase();
  const glassHint = GLASS_MAP[glass];
  if (glassHint && !methodLines.join(' ').toLowerCase().includes(glassHint)) {
    methodLines.push(`Serve in a ${glassHint}.`);
  }

  let out = 'Ingredients:\n' + bullets.join('\n') + '\n\nMethod:\n' + methodLines.map((m, i) => `${i + 1}. ${m}`).join('\n');
  out = annotate(out);
  return out;
}

function main() {
  const [, , inPath, outPath] = process.argv;
  if (!inPath || !outPath) {
    console.error('Usage: node modernize_cocktails.js <input.json> <output.sql>');
    process.exit(1);
  }
  const raw = fs.readFileSync(inPath, 'utf8');
  const parsed = JSON.parse(raw);
  const rows = Array.isArray(parsed) ? (parsed[0]?.results || parsed) : (parsed.results || []);

  const sqlLines = [];
  let modernized = 0;
  let skipped = 0;
  const examples = [];

  for (const row of rows) {
    const modern = modernize(row);
    if (/unable to modernize/i.test(modern)) skipped++;
    else modernized++;
    sqlLines.push(`UPDATE recipe SET modernized_text = '${esc(modern)}' WHERE id = '${esc(row.id)}';`);
    if (examples.length < 3 && modern.length > 120 && !/unable to modernize/i.test(modern)) {
      examples.push({ title: row.title, modern });
    }
  }

  fs.writeFileSync(outPath, sqlLines.join('\n') + '\n');
  console.log(JSON.stringify({
    total: rows.length,
    modernized,
    skipped,
    examples,
  }, null, 2));
}

main();

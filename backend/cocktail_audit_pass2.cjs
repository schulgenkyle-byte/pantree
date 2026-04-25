#!/usr/bin/env node
/**
 * cocktail_audit_pass2.cjs - second/cleanup pass
 *
 * Targets:
 *  - Rows with NULL or very short original_text → mark as audit_status='no_original_text'
 *    and leave a useful "consult a modern recipe" placeholder. Apply expanded canonical
 *    lookup for any titles we recognize.
 *  - Rows with heavy OCR damage (£, %, mid-word digits) → mark ocr_damaged with placeholder.
 *  - Reject obvious non-cocktails by full title scan (oysters, ices, broths, salads, etc.).
 *
 * Run:
 *   node cocktail_audit_pass2.cjs
 */

'use strict';

const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const DB = 'pantrie-db-staging';
const PROGRESS_FILE = path.join(__dirname, 'cocktail_audit_pass2_progress.json');

function runWrangler(args) {
  let cmd, fullArgs;
  if (process.platform === 'win32') {
    cmd = 'cmd.exe';
    fullArgs = ['/d', '/s', '/c', 'npx', ...args];
  } else {
    cmd = 'npx';
    fullArgs = args;
  }
  return spawnSync(cmd, fullArgs, {
    cwd: __dirname, encoding: 'utf8', maxBuffer: 256 * 1024 * 1024,
  });
}

function d1Query(sql) {
  let cmd, fullArgs;
  if (process.platform === 'win32') {
    cmd = 'cmd.exe';
    fullArgs = ['/d', '/s', '/c', 'npx', 'wrangler', 'd1', 'execute', DB, '--remote', '--json', '--command', sql];
  } else {
    cmd = 'npx';
    fullArgs = ['wrangler', 'd1', 'execute', DB, '--remote', '--json', '--command', sql];
  }
  const proc = spawnSync(cmd, fullArgs, { cwd: __dirname, encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 });
  if (proc.error) throw proc.error;
  if (proc.status !== 0) {
    console.error('STDERR:', (proc.stderr || '').slice(-500));
    throw new Error('D1 query failed');
  }
  const out = proc.stdout || '';
  const i = out.indexOf('[');
  if (i < 0) throw new Error('No JSON in output');
  return JSON.parse(out.slice(i))[0]?.results || [];
}

function d1ExecFile(sqlPath) {
  const proc = runWrangler(['wrangler', 'd1', 'execute', DB, '--remote', '--file', sqlPath]);
  if (proc.error) throw proc.error;
  if (proc.status !== 0) {
    console.error('STDERR:', (proc.stderr || '').slice(-500));
    throw new Error('D1 file exec failed');
  }
}

function esc(s) { return s == null ? null : String(s).replace(/'/g, "''"); }

// ---------- expanded canonical lookups ----------
// More cocktails known from modern bartender experience. Title-keyed lowercase.

const CANON2 = {
  'a1': {
    text: `1.5 oz London dry gin
0.75 oz Grand Marnier
0.5 oz fresh lemon juice
1 dash real grenadine

Shake with ice 10 seconds.
Strain into a chilled coupe.
Garnish with an expressed lemon peel.

Bartender's note: A modernized version of an unidentified vintage cocktail; balanced gin sour with cognac-orange lift.`,
  },
  'b-52': {
    text: `0.75 oz Kahlua
0.75 oz Bailey's Irish Cream
0.75 oz Grand Marnier

Layer in a shot glass: Kahlua first, Bailey's slowly over the back of a barspoon, Grand Marnier on top.
Serve immediately, optionally igniting the Grand Marnier surface.

Bartender's note: 1977 Banff Springs creation. The layering is the entire show — pour slowly or it merges into a single beige mess.`,
  },
  'flaming b-52': null,
  'amaretto sour': {
    text: `2 oz Amaretto di Saronno
1 oz fresh lemon juice
0.5 oz simple syrup
1 oz bonded bourbon (Old Grand-Dad 100)
1 fresh egg white (pasteurized)

Dry-shake without ice 10 seconds.
Add ice; shake 12 seconds.
Double-strain into a rocks glass over one large cube.
Garnish with a brandied cherry and lemon peel.

Bartender's note: Modern: pasteurized egg white. Jeffrey Morgenthaler's bonded-bourbon trick is what saves this drink from being cloying; without it, it's syrup.`,
  },
  'kamikaze': {
    text: `1 oz vodka
1 oz Cointreau
1 oz fresh lime juice

Shake with ice 10 seconds.
Strain into a chilled coupe.
Garnish with a lime wheel.

Bartender's note: Equal-parts shooter from the 1970s; modern fresh lime makes it drinkable instead of just chuggable.`,
  },
  'long beach iced tea': {
    text: `0.5 oz vodka
0.5 oz London dry gin
0.5 oz white rum
0.5 oz blanco tequila
0.5 oz Cointreau
0.75 oz fresh lemon juice
0.5 oz simple syrup
2 oz cranberry juice

Shake everything except cranberry with ice 8 seconds.
Strain into a collins glass over fresh ice.
Top with cranberry juice; do not stir.
Garnish with a lemon wedge.

Bartender's note: Long Island variant — cranberry instead of cola. Same logic: fresh lemon, real Cointreau, no sour mix.`,
  },
  'tequila sunrise': {
    text: `2 oz blanco tequila
4 oz fresh orange juice
0.5 oz real grenadine

Build tequila and orange juice in a highball over fresh ice; stir.
Slowly pour grenadine down a barspoon so it sinks to the bottom.
Garnish with an orange wheel and brandied cherry.

Bartender's note: 1972 Sausalito original. Real grenadine is what sells the sunrise effect; corn-syrup-red just looks like dish soap.`,
  },
  'screwdriver': {
    text: `1.5 oz vodka (Tito's)
4 oz fresh orange juice

Build in a highball over fresh ice.
Stir gently.
Garnish with an orange slice.

Bartender's note: Use real fresh-pressed OJ; pre-bottled juice has a cooked-pectin note that ruins this otherwise simple drink.`,
  },
  'bloody mary': {
    text: `1.5 oz vodka
4 oz tomato juice (Sacramento or fresh-pressed)
0.5 oz fresh lemon juice
3 dashes Worcestershire sauce
3 dashes Tabasco
Pinch of celery salt and black pepper
1 dash horseradish (optional)

Roll between two pint glasses or stir vigorously to combine without aerating.
Pour into a chilled highball over fresh ice.
Garnish with celery, olives, lemon wedge, and a pickled green bean.

Bartender's note: 1920s Paris Harry's New York Bar. Don't shake — aeration kills the texture. Quality tomato juice is 80 percent of the drink.`,
  },
  'mudslide': {
    text: `1 oz vodka
1 oz Kahlua
1 oz Bailey's Irish Cream
2 oz heavy cream

Shake with ice 8 seconds.
Strain into a rocks glass over fresh ice.
Garnish with a chocolate shaving.

Bartender's note: 1950s Cayman Islands Wreck Bar. Frozen-blender variant is fun but the shaken version is faster and less dilute.`,
  },
  'white russian': {
    text: `2 oz vodka
1 oz Kahlua
1 oz heavy cream

Build vodka and Kahlua in a rocks glass over one large ice cube.
Float cream slowly over the back of a barspoon.
No garnish; let the layers do the work.

Bartender's note: The Big Lebowski drink. Heavy cream (not half-and-half) gives the proper density to layer.`,
  },
  'black russian': {
    text: `2 oz vodka
1 oz Kahlua

Build in a rocks glass over one large ice cube.
Stir 10 seconds.
No garnish.

Bartender's note: 1949 Brussels original. Use good vodka — there is no place to hide here.`,
  },
  'irish coffee': {
    text: `1.5 oz Irish whiskey (Powers Gold or Jameson Black Barrel)
1 tsp brown sugar
4 oz hot strong black coffee
1 oz lightly whipped heavy cream

Pre-warm a stemmed glass mug with hot water; discard.
Add whiskey and sugar; top with coffee and stir to dissolve sugar.
Float lightly whipped cream slowly over the back of a barspoon.
Drink the hot coffee through the cold cream.

Bartender's note: 1943 Foynes/Shannon Airport original. The cream must be lightly whipped — fully whipped sits on top, unwhipped sinks; lightly whipped floats correctly.`,
  },
  'hot toddy': {
    text: `1.5 oz bourbon, rye, or blended Scotch
1 tsp honey (or demerara syrup)
0.5 oz fresh lemon juice
4 oz hot water (or hot black tea)
1 cinnamon stick
1 lemon wheel studded with 3 cloves

Pre-warm a heat-safe mug with hot water; discard.
Add whiskey, honey, lemon juice; top with hot water.
Stir until honey dissolves.
Garnish with cinnamon stick and clove-studded lemon.

Bartender's note: 18th-century medicinal classic. Use raw honey; processed honey throws off the texture.`,
  },
  'hot buttered rum': {
    text: `2 oz aged rum (Plantation OFTD or El Dorado 8)
1 tbsp unsalted butter
1 tsp brown sugar
Pinch each cinnamon, nutmeg, ground clove
4 oz boiling water or apple cider

Pre-warm a mug.
Combine butter, sugar, spices in the mug; mash to a paste.
Add rum and boiling water; stir until butter melts.
Garnish with a cinnamon stick and a fresh nutmeg grating.

Bartender's note: New England 18th-century origin. A spoonful of homemade hot-buttered-rum batter (butter, brown sugar, spices) kept in the freezer is the bar trick that makes this fast.`,
  },
  'mulled wine': {
    text: `1 bottle (750 ml) dry red wine
0.5 cup brandy or port
3 tbsp brown sugar
1 orange (sliced, peel-on)
6 cloves
2 cinnamon sticks
2 star anise
1 fresh nutmeg grating

Combine all in a saucepan; warm over low heat 20 minutes (do NOT boil).
Strain into pre-warmed mugs.
Garnish each with a fresh orange slice.

Bartender's note: Boiling kills the alcohol AND the wine character. Hold at 160-170°F until served.`,
  },
  'bee\'s knees': {
    text: `2 oz London dry gin
0.75 oz fresh lemon juice
0.5 oz honey syrup (3:1 honey:hot water)

Shake with ice 10 seconds.
Double-strain into a chilled coupe.
Garnish with an expressed lemon peel.

Bartender's note: Prohibition-era recipe to mask bathtub gin's roughness with honey. Modern: real honey syrup, real London dry gin — and now it's actually delicious.`,
  },
  'bees knees': null,
  'mary pickford cocktail': null,
  'last word cocktail': null,
  'aviation cocktail': null,
  'income tax': {
    text: `1.5 oz London dry gin
0.5 oz dry vermouth
0.5 oz sweet vermouth
0.5 oz fresh orange juice
1 dash Angostura bitters

Shake with ice 10 seconds.
Double-strain into a chilled coupe.
Garnish with an expressed orange peel.

Bartender's note: 1920s Bronx variant — same drink with bitters added. Splits both vermouths so it's drier than a Bronx but rounder than a Martini.`,
  },
  bronx: {
    text: `1.5 oz London dry gin
0.5 oz dry vermouth (Dolin)
0.5 oz sweet vermouth (Carpano Antica)
0.5 oz fresh orange juice

Shake with ice 10 seconds.
Double-strain into a chilled coupe.
Garnish with an expressed orange peel.

Bartender's note: Pre-Pro Waldorf-Astoria classic — fresh orange juice (not bottled) is the difference between this and an actual decent drink.`,
  },
  'bronx cocktail': null,
  'french martini': {
    text: `2 oz vodka
0.5 oz Chambord (raspberry liqueur)
2 oz fresh pineapple juice (must be fresh-pressed for foam)

Shake hard with ice 12 seconds.
Double-strain into a chilled cocktail glass.
Garnish with three raspberries on a pick.

Bartender's note: Keith McNally / Pravda NYC 1980s. Fresh pineapple juice generates the silky head; canned juice falls flat.`,
  },
  espresso_martini: {
    text: `2 oz vodka (Ketel One or Tito's)
1 oz fresh espresso (cooled slightly)
0.5 oz coffee liqueur (Mr Black or Kahlua)
0.25 oz simple syrup

Shake very hard with ice 15 seconds for proper crema.
Double-strain into a chilled coupe.
Garnish with three coffee beans floated on the foam.

Bartender's note: Dick Bradsell's late-80s London original. Fresh-pulled espresso is non-negotiable for the crema.`,
  },
  'espresso martini': null,
  'corn n oil': {
    text: `2 oz blackstrap rum (Cruzan or Hamilton)
0.5 oz Velvet Falernum (John D Taylor's)
0.5 oz fresh lime juice
2 dashes Angostura bitters

Build in a rocks glass packed with crushed ice.
Stir until cold.
Top with more crushed ice.
Garnish with a lime wedge.

Bartender's note: Barbados classic. Falernum is the star — the rum is the supporting character. Use blackstrap not gold for proper depth.`,
  },
  "corn 'n' oil": null,
  "queen's park swizzle": {
    text: `2 oz aged rum (El Dorado 12 or Doorly's 12)
0.75 oz fresh lime juice
0.5 oz demerara syrup (2:1)
8-10 fresh mint leaves
4 dashes Angostura bitters

Lightly bruise mint in the bottom of a tall glass.
Add lime, syrup, rum; pack with crushed ice.
Swizzle with a barspoon until the glass frosts.
Top with more crushed ice and 4 dashes Angostura on top.
Garnish with a mint sprig.

Bartender's note: Trinidad's Queen's Park Hotel. Bruise the mint, don't pulverize. Angostura cap is structural — it's the first thing you taste.`,
  },
  'rum punch': {
    text: `2 oz aged dark rum (Appleton 8 or Plantation Xaymaca)
1 oz fresh lime juice
1 oz demerara syrup (2:1)
4 oz cold water (or fruit-infused tea)
3 dashes Angostura bitters
Fresh nutmeg

Build in a tall glass over crushed ice.
Stir to combine.
Cap with fresh-grated nutmeg.

Bartender's note: "1 sour, 2 sweet, 3 strong, 4 weak" — the Caribbean rule. Memorize it; you can always make a punch.`,
  },
  'planters punch': {
    text: `2 oz aged Jamaican rum (Appleton 12 or Hamilton Pot Still Black)
0.75 oz fresh lime juice
0.5 oz fresh lemon juice
0.75 oz demerara syrup
0.5 oz pineapple juice (optional)
3 dashes Angostura bitters
Fresh nutmeg, mint sprig, orange slice

Shake with ice 10 seconds.
Pour unstrained into a tall glass; top with crushed ice.
Cap with fresh-grated nutmeg; garnish with mint and orange.

Bartender's note: Myers's Rum's marketing won — but Jamaican rum is what makes it work. Fresh-grated nutmeg cap is structural.`,
  },
  'planter\'s punch': null,
  'hurricane': {
    text: `2 oz dark Jamaican rum
2 oz aged white rum
4 oz passion fruit juice (or 2 oz pineapple + 2 oz passion fruit puree)
1 oz fresh lime juice
0.5 oz real grenadine

Shake with ice 10 seconds.
Pour unstrained into a hurricane glass; top with crushed ice.
Garnish with an orange slice and brandied cherry.

Bartender's note: New Orleans Pat O'Brien's 1940s. Real fruit juices, real grenadine — the bar mix is the cruise-ship version.`,
  },
  'sazerac cocktail': null,
  'penicillin cocktail': null,
  'orange whip': {
    text: `1 oz vodka
1 oz light rum
2 oz fresh orange juice
1 oz heavy cream

Shake with ice 10 seconds.
Strain into a chilled rocks glass over fresh ice.
Garnish with an orange wheel.

Bartender's note: Made famous by The Blues Brothers. Heavy cream + fresh OJ is the trick — half-and-half curdles slightly with citrus.`,
  },
  'orange crush': {
    text: `1.5 oz orange vodka (Absolut Mandarin)
1 oz triple sec
4 oz fresh orange juice
1 oz lemon-lime soda

Shake vodka, triple sec, OJ with ice 10 seconds.
Strain into a tall glass over fresh ice.
Top with lemon-lime soda.
Garnish with an orange slice.

Bartender's note: Maryland Eastern Shore classic. Fresh-squeezed OJ is the whole game.`,
  },
  'orange crush (cocktail)': null,
  'sangria': {
    text: `1 bottle (750 ml) dry red wine (Tempranillo or Garnacha)
3 oz brandy
2 oz orange liqueur (Cointreau)
1 cup mixed citrus and seasonal fruit (orange, lemon, apple)
2 oz simple syrup
4 oz cold club soda

Combine wine, brandy, Cointreau, fruit, syrup in a pitcher.
Refrigerate 4-24 hours to macerate.
Just before serving, add club soda and pour over fresh ice in wine glasses.

Bartender's note: Macerate ahead — the fruit needs time. Add bubbles last so they survive to the glass.`,
  },
  'orange blossom (cocktail)': null,
  'french connection cocktail': null,
  'painkiller cocktail': null,
};

// ---------- detection helpers ----------

function looksOcrDamagedV2(text) {
  if (!text) return false;
  const t = text;
  const len = t.length;
  if (len < 30) return false;
  // Indicators
  const pctMisuse = (t.match(/%/g) || []).length;
  const dittoMarks = (t.match(/\bdo\.\B|\bdo\b\s*\./gi) || []).length; // "1 do." style
  const vCount = (t.match(/\bV\s*\d/g) || []).length; // OCR-mangled fractions like "V 2"
  const garbleWords = (t.match(/\b[A-Z][A-Z]+\d[A-Z0-9]+\b/g) || []).length;
  const mixedCaseGarble = (t.match(/\b[A-Z]{2,}[a-z]+[A-Z]{2,}\b/g) || []).length;
  const score = pctMisuse + dittoMarks + vCount * 2 + garbleWords * 3 + mixedCaseGarble * 2;
  // Threshold: >5 indicators OR very high density
  return score > 5 || (garbleWords + mixedCaseGarble) > 2;
}

function isProseVariantV2(text) {
  if (!text) return false;
  const t = text.trim();
  if (t.length > 280) return false;
  return /\bsame as .* but/i.test(t)
    || /\bproceed as (in|with|for) the\b/i.test(t)
    || /\b(use|substitute|replace) .* (with|for)\b.*\b(in|recipe|formula)\b/i.test(t);
}

const DELETION_TITLE_PATTERNS = [
  /^orange ice$/i,
  /^lemon ice$/i,
  /^pineapple ice$/i,
  /^raspberry ice$/i,
  /^water ice$/i,
  /^italian ice$/i,
  /\bice cream\b/i,
  /\boyster cocktail\b/i,
  /\btomato cocktail\b/i,
  /\bfruit salad\b/i,
];

function shouldDelete(title, body) {
  const t = (title || '').toLowerCase();
  for (const re of DELETION_TITLE_PATTERNS) {
    if (re.test(t)) return true;
  }
  // body strongly suggests dessert
  if (body) {
    const b = body.toLowerCase();
    if (/\bfreezer\b.*\bfreeze\b/.test(b) && /\bdessert\b/.test(b)) return true;
    if (/\bin a (mold|mould)\b/.test(b) && /\bset\b/.test(b)) return true;
  }
  return false;
}

// ---------- main ----------

function main() {
  console.log('Pass 2: cleanup audit and OCR-damaged');

  // 1. Find OCR-damaged rows by SQL pattern (broader)
  console.log('Step 1: scanning for OCR damage...');
  const ocrCandidates = d1Query(`
    SELECT id, title, original_text, audit_status FROM recipe
    WHERE content_type IN ('cocktail','mocktail')
    AND audit_status NOT IN ('ocr_damaged','rejected_non_cocktail')
    AND (original_text LIKE '%£%'
      OR original_text LIKE '%JXB%'
      OR original_text LIKE '%THOM1B%'
      OR original_text LIKE '%§%'
      OR original_text LIKE '%¶%'
      OR original_text LIKE '%¥%')
  `.replace(/\s+/g, ' ').trim());
  console.log(`  Found ${ocrCandidates.length} OCR candidates by SQL pattern`);

  let ocrConfirmed = 0;
  const ocrUpdates = [];
  for (const row of ocrCandidates) {
    if (looksOcrDamagedV2(row.original_text)) {
      ocrConfirmed++;
      ocrUpdates.push(
        `UPDATE recipe SET audit_status = 'ocr_damaged', audit_notes = 'OCR garbage in original text', modernized_text = 'Original OCR damaged; see source book for clean version.' WHERE id = '${esc(row.id)}';`
      );
    }
  }
  console.log(`  Confirmed ${ocrConfirmed} OCR-damaged rows`);

  // 2. Find non-cocktails by full title scan
  console.log('Step 2: scanning for non-cocktails...');
  const allRows = d1Query(`
    SELECT id, title, original_text FROM recipe
    WHERE content_type IN ('cocktail','mocktail')
    AND audit_status NOT IN ('rejected_non_cocktail')
  `.replace(/\s+/g, ' ').trim());
  let deleteCount = 0;
  const deletes = [];
  for (const row of allRows) {
    if (shouldDelete(row.title, row.original_text)) {
      deletes.push(row.id);
      deleteCount++;
      console.log(`  DELETE ${row.id} - ${row.title}`);
    }
  }
  console.log(`  Found ${deleteCount} non-cocktails to delete`);

  // 3. Find empty/null original_text rows missing canonical match
  console.log('Step 3: scanning empty-original rows for canonical hits...');
  const emptyRows = d1Query(`
    SELECT id, title, original_text, modernized_text, audit_status FROM recipe
    WHERE content_type IN ('cocktail','mocktail')
    AND (original_text IS NULL OR length(original_text) < 20)
    AND audit_status != 'reviewed_canonical'
  `.replace(/\s+/g, ' ').trim());
  console.log(`  Found ${emptyRows.length} empty-original rows`);

  let canonicalHits = 0;
  let placeholderUpdates = 0;
  const emptyUpdates = [];
  for (const row of emptyRows) {
    const lower = (row.title || '').toLowerCase().trim()
      .replace(/\s*\(cocktail\)\s*$/, '')
      .replace(/\s+cocktail$/, '')
      .replace(/\s+/g, ' ');
    const canon = CANON2[lower];
    if (canon && canon.text) {
      canonicalHits++;
      emptyUpdates.push(
        `UPDATE recipe SET modernized_text = '${esc(canon.text)}', audit_status = 'reviewed_canonical', audit_notes = 'Canonical lookup (no original text)' WHERE id = '${esc(row.id)}';`
      );
    } else {
      // Better placeholder
      const placeholder = `Recipe pending — ${row.title} is in our index but the source database did not include a full recipe. Look up a contemporary craft-bar version (Death & Co, Difford's Guide, or PUNCH) for the modern build.`;
      placeholderUpdates++;
      emptyUpdates.push(
        `UPDATE recipe SET modernized_text = '${esc(placeholder)}', audit_status = 'no_original_text', audit_notes = 'Source DB did not include a recipe' WHERE id = '${esc(row.id)}';`
      );
    }
  }
  console.log(`  Canonical hits: ${canonicalHits}, Placeholder: ${placeholderUpdates}`);

  // 4. Apply all updates and deletions
  const allSql = [];
  for (const id of deletes) {
    allSql.push(`DELETE FROM recipe_ingredient WHERE recipe_id = '${esc(id)}';`);
    allSql.push(`DELETE FROM recipe_step WHERE recipe_id = '${esc(id)}';`);
    allSql.push(`DELETE FROM recipe WHERE id = '${esc(id)}';`);
  }
  allSql.push(...ocrUpdates);
  allSql.push(...emptyUpdates);

  if (allSql.length > 0) {
    const sqlFile = path.join(__dirname, '_audit_pass2.sql');
    // Apply in chunks of 500 statements to keep the upload manageable
    const CHUNK = 400;
    for (let i = 0; i < allSql.length; i += CHUNK) {
      const chunk = allSql.slice(i, i + CHUNK);
      fs.writeFileSync(sqlFile, chunk.join('\n') + '\n');
      console.log(`  Applying chunk ${i}/${allSql.length}...`);
      d1ExecFile(sqlFile);
    }
    fs.unlinkSync(sqlFile);
  }

  const summary = {
    ocr_marked: ocrConfirmed,
    deleted: deleteCount,
    canonical_hits: canonicalHits,
    placeholder_updates: placeholderUpdates,
    total_sql: allSql.length,
  };
  fs.writeFileSync(PROGRESS_FILE, JSON.stringify(summary, null, 2));
  console.log(JSON.stringify(summary, null, 2));
}

main();

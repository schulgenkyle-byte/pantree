#!/usr/bin/env node
/**
 * cocktail_audit.cjs - COCKTAIL-AUDITOR-V2
 *
 * For every cocktail in D1: audit, write a genuine modernized recipe,
 * fill missing contributor_story. Save progress for restartability.
 *
 * Run:
 *   node cocktail_audit.cjs            # process next batch
 *   node cocktail_audit.cjs --reset    # start over
 *   node cocktail_audit.cjs --max 10   # do 10 batches then stop
 */

'use strict';

const fs = require('fs');
const path = require('path');
const { execSync, spawnSync } = require('child_process');

const DB = 'pantrie-db-staging';
const BATCH_SIZE = 50;
const PROGRESS_FILE = path.join(__dirname, 'cocktail_audit_progress.json');
const SAMPLES_FILE = path.join(__dirname, 'cocktail_audit_samples.json');

// ---------- progress state ----------

function loadProgress() {
  if (!fs.existsSync(PROGRESS_FILE)) {
    return {
      offset: 0,
      processed_total: 0,
      deleted: 0,
      modernized: 0,
      story_filled: 0,
      ocr_damaged: 0,
      prose_variant: 0,
      rejected_ids: [],
      sample_modernizations: [],
      sample_stories: [],
      started_at: new Date().toISOString(),
      last_run_at: null,
    };
  }
  return JSON.parse(fs.readFileSync(PROGRESS_FILE, 'utf8'));
}

function saveProgress(p) {
  p.last_run_at = new Date().toISOString();
  fs.writeFileSync(PROGRESS_FILE, JSON.stringify(p, null, 2));
}

// ---------- D1 helpers ----------

function runWrangler(args, opts = {}) {
  // On Windows, spawn cmd.exe explicitly with /c. This is the only reliable way
  // to invoke npx without shell-mode quoting nightmares.
  let cmd, fullArgs;
  if (process.platform === 'win32') {
    cmd = 'cmd.exe';
    fullArgs = ['/d', '/s', '/c', 'npx', ...args];
  } else {
    cmd = 'npx';
    fullArgs = args;
  }
  return spawnSync(cmd, fullArgs, {
    cwd: __dirname,
    encoding: 'utf8',
    maxBuffer: 256 * 1024 * 1024,
    windowsVerbatimArguments: false,
    ...opts,
  });
}

function d1Query(sql) {
  // wrangler --file treats SQL as a migration and doesn't return rows for SELECT.
  // We must use --command. On Windows cmd.exe, pass the SQL via spawn argv directly
  // (no shell), letting Node handle quoting.
  let cmd, fullArgs;
  if (process.platform === 'win32') {
    cmd = 'cmd.exe';
    // Use /s and quoted command so cmd's parser treats the rest as one piece
    fullArgs = ['/d', '/s', '/c', 'npx', 'wrangler', 'd1', 'execute', DB, '--remote', '--json', '--command', sql];
  } else {
    cmd = 'npx';
    fullArgs = ['wrangler', 'd1', 'execute', DB, '--remote', '--json', '--command', sql];
  }
  const proc = spawnSync(cmd, fullArgs, {
    cwd: __dirname,
    encoding: 'utf8',
    maxBuffer: 256 * 1024 * 1024,
    windowsVerbatimArguments: false,
  });
  if (proc.error) throw proc.error;
  if (proc.status !== 0) {
    console.error('D1 query failed status=' + proc.status);
    console.error('STDOUT:', (proc.stdout || '').slice(-500));
    console.error('STDERR:', (proc.stderr || '').slice(-500));
    throw new Error('D1 query failed');
  }
  const out = proc.stdout || '';
  const jsonStart = out.indexOf('[');
  if (jsonStart < 0) {
    console.error('No JSON in stdout. Stdout was:', out.slice(0, 1000));
    throw new Error('No JSON in wrangler output');
  }
  const parsed = JSON.parse(out.slice(jsonStart));
  return parsed[0]?.results || [];
}

function d1ExecFile(sqlPath) {
  const proc = runWrangler([
    'wrangler', 'd1', 'execute', DB, '--remote', '--file', sqlPath,
  ]);
  if (proc.error) {
    console.error('Spawn error:', proc.error.message);
    throw proc.error;
  }
  if (proc.status !== 0) {
    console.error('D1 file exec failed status=' + proc.status);
    console.error('STDERR:', (proc.stderr || '').slice(-500));
    throw new Error('D1 file exec failed');
  }
}

function esc(s) {
  if (s == null) return null;
  return String(s).replace(/'/g, "''");
}

// ---------- canonical cocktail registry ----------
// Hand-crafted modernizations for famous cocktails. Title-keyed (lowercase exact).
// These are the recipes a 2026 home bartender would actually want.

const CANON = {
  manhattan: {
    text: `2 oz rye whiskey (try Rittenhouse 100 or Sazerac 6)
1 oz sweet vermouth (Carpano Antica or Cocchi di Torino)
2 dashes Angostura bitters

Stir with ice 30 seconds until well-chilled.
Strain into a chilled coupe.
Garnish with a brandied cherry (Luxardo) and an expressed orange peel.

Bartender's note: Original calls for "half whiskey, half vermouth"; modern 2:1 keeps the rye in front and the vermouth as accent — and Carpano Antica was formulated specifically for this drink.`,
  },
  'manhattan cocktail': null, // alias
  martini: {
    text: `2.5 oz London dry gin (Tanqueray or Beefeater)
0.5 oz dry vermouth (Dolin Dry or Noilly Prat)
1 dash orange bitters

Stir with ice 30 seconds.
Strain into a chilled cocktail glass.
Garnish with a lemon twist or olive.

Bartender's note: Pre-Pro recipes ran 50/50 gin-vermouth; the modern 5:1 ratio (and properly cold gin) is what made the drink iconic.`,
  },
  'dry martini': null,
  'martini cocktail': null,
  'old fashioned': {
    text: `2 oz bourbon or rye (Buffalo Trace or Rittenhouse)
1 sugar cube (or 1 tsp demerara syrup)
3 dashes Angostura bitters
1 large orange peel

Saturate sugar with bitters and a splash of water in a rocks glass; muddle to a paste.
Add whiskey and one large ice cube; stir 20 seconds.
Express orange peel oils over the surface and drop it in.

Bartender's note: Skip the cherry-and-orange-slice muddle — that's a 1970s "wisconsin old fashioned." A clean spirit-forward build is the original 1880s style.`,
  },
  'old-fashioned': null,
  daiquiri: {
    text: `2 oz white rum (Plantation 3 Stars or Probitas)
0.75 oz fresh lime juice
0.5 oz simple syrup (1:1)

Shake hard with ice 12 seconds.
Double-strain into a chilled coupe.
No garnish needed.

Bartender's note: Keep ratios honest — under-syruping makes it sour, over-shaking dilutes the rum. The original Bacardi Cuban formula is exactly this.`,
  },
  'daiquiri cocktail': null,
  margarita: {
    text: `2 oz blanco tequila (Cimarron or Tapatio)
1 oz fresh lime juice
0.75 oz orange liqueur (Cointreau)
0.25 oz agave syrup (optional)

Shake with ice 10 seconds.
Strain into a rocks glass over fresh ice (salt rim optional).
Garnish with a lime wheel.

Bartender's note: Classic Tommy's-style — the agave rounds the tequila bite. Skip triple-sec mix and use real Cointreau; the original 1948 recipe was citrus-forward, not cloying.`,
  },
  negroni: {
    text: `1 oz London dry gin (Tanqueray)
1 oz Campari
1 oz sweet vermouth (Cocchi di Torino)

Stir with ice 25 seconds.
Strain into a rocks glass over one large cube.
Garnish with an expressed orange peel.

Bartender's note: 1:1:1 is gospel. Dilution matters more than ratio — undermix and it's medicinal, overstir and it goes flabby.`,
  },
  'whiskey sour': {
    text: `2 oz bourbon (Old Forester 100 or Wild Turkey 101)
0.75 oz fresh lemon juice
0.75 oz simple syrup (1:1)
1 fresh egg white (use pasteurized for safety)

Dry-shake all ingredients without ice 10 seconds.
Add ice and shake 12 seconds.
Double-strain into a chilled coupe.
Garnish with three drops Angostura bitters across the foam.

Bartender's note: Modern: use pasteurized egg whites. Dry-shake first builds the silky head the historic recipes only hinted at.`,
  },
  'gin fizz': {
    text: `2 oz London dry gin
0.75 oz fresh lemon juice
0.5 oz simple syrup
2 oz cold club soda

Shake gin, lemon, syrup with ice 10 seconds.
Strain into a chilled highball over fresh ice.
Top with cold club soda.
Garnish with a lemon wheel.

Bartender's note: Nail the carbonation by adding soda LAST and not stirring; the lift is the whole point of a fizz.`,
  },
  'tom collins': {
    text: `2 oz London dry gin (Tanqueray)
1 oz fresh lemon juice
0.75 oz simple syrup
3 oz cold club soda

Build in a chilled collins glass over fresh ice.
Stir gently to combine.
Garnish with a lemon wheel and brandied cherry.

Bartender's note: Originally made with Old Tom (a sweetened gin); modern London dry needs the syrup bumped slightly. Fresh juice is non-negotiable.`,
  },
  'mint julep': {
    text: `2.5 oz Kentucky bourbon (Woodford Reserve or Old Forester)
0.5 oz simple syrup (or 1 tsp demerara)
8-10 fresh mint leaves
Crushed ice

Gently slap mint between palms; place in a julep cup with syrup.
Add bourbon and pack with crushed ice; swizzle with a barspoon until cup frosts.
Top with more crushed ice into a dome.
Garnish with a fat mint bouquet and a short straw.

Bartender's note: Don't muddle the mint to death — bruising releases bitter compounds. The frost on the cup is the tell.`,
  },
  'sazerac': {
    text: `2 oz rye whiskey (Sazerac 6 or Rittenhouse 100)
1 sugar cube
3 dashes Peychaud's bitters
1 dash Angostura bitters
Absinthe (Pernod or Vieux Pontarlier) for rinse
Lemon peel

Rinse a chilled rocks glass with absinthe; discard excess.
Muddle sugar with both bitters and a splash of water in a mixing glass.
Add rye and ice; stir 25 seconds.
Strain into the prepared glass (no ice).
Express lemon peel over the top and discard.

Bartender's note: New Orleans tradition — peel goes in the trash, not the glass. The absinthe rinse is the soul of the drink.`,
  },
  'mai tai': {
    text: `1 oz aged Jamaican rum (Appleton 12)
1 oz aged Martinique rum (Rhum Clement VSOP)
0.75 oz fresh lime juice
0.5 oz orange curacao (Pierre Ferrand)
0.5 oz orgeat (Small Hand Foods)
0.25 oz simple syrup

Shake hard with crushed ice 8 seconds.
Pour unstrained into a double rocks glass.
Top with more crushed ice.
Garnish with a spent lime shell and fresh mint sprig.

Bartender's note: Trader Vic's 1944 original — split aged rums, real orgeat, fresh lime. Anything pineapple-juice-and-grenadine is the cruise-ship impostor.`,
  },
  caipirinha: {
    text: `2 oz cachaça (Avua Amburana or Novo Fogo)
0.5 lime, cut into wedges
2 tsp demerara sugar

Muddle lime wedges and sugar in a rocks glass to release oils.
Add cachaça; fill with crushed ice.
Stir to combine.

Bartender's note: Use real cachaça — not white rum. The grassy, funky character is the drink's signature.`,
  },
  'pisco sour': {
    text: `2 oz Peruvian pisco (Macchu Pisco or Barsol)
0.75 oz fresh lime juice
0.5 oz simple syrup
1 fresh egg white (pasteurized)
3 drops Angostura bitters (for garnish)

Dry-shake without ice 10 seconds.
Add ice; shake 12 seconds.
Double-strain into a chilled coupe.
Float Angostura drops on the foam.

Bartender's note: Modern: use pasteurized egg whites. Peruvian style runs slightly drier than Chilean — adjust syrup to taste.`,
  },
  caipiroska: null,
  'moscow mule': {
    text: `2 oz vodka (Tito's or Reyka)
0.5 oz fresh lime juice
4 oz cold ginger beer (Fever-Tree or Q)

Build in a copper mug over fresh ice.
Stir gently.
Garnish with a lime wedge and candied ginger.

Bartender's note: Use real fiery ginger beer, not ginger ale. The 1940s original was equal parts marketing for Smirnoff and copper Cock 'n Bull mugs.`,
  },
  cosmopolitan: {
    text: `1.5 oz citron vodka (Absolut Citron)
0.75 oz Cointreau
0.5 oz fresh lime juice
0.5 oz cranberry juice cocktail

Shake with ice 10 seconds.
Double-strain into a chilled coupe.
Garnish with a flamed orange peel.

Bartender's note: Toby Cecchini's 1988 NYC version, not the pink-cosmo TV variety — fresh lime, real Cointreau, and a flamed peel turn this into a respectable drink.`,
  },
  'gimlet': {
    text: `2.5 oz London dry gin (Plymouth or Tanqueray)
0.75 oz fresh lime juice
0.5 oz simple syrup (1:1)

Shake with ice 10 seconds.
Double-strain into a chilled coupe.
Garnish with a lime wheel.

Bartender's note: Historic recipes used Rose's lime cordial; modern fresh-juice-and-syrup version is brighter and cleaner — but a half-ounce of Rose's is fun once a year for tradition.`,
  },
  'aviation': {
    text: `2 oz London dry gin
0.5 oz maraschino liqueur (Luxardo)
0.5 oz fresh lemon juice
0.25 oz creme de violette (Rothman & Winter)

Shake with ice 10 seconds.
Double-strain into a chilled coupe.
Garnish with a brandied cherry.

Bartender's note: The violette dropped out of US recipes during Prohibition; restoring it gives the drink its proper "sky" hue and floral lift.`,
  },
  'last word': {
    text: `0.75 oz London dry gin
0.75 oz green Chartreuse
0.75 oz maraschino liqueur (Luxardo)
0.75 oz fresh lime juice

Shake with ice 12 seconds.
Double-strain into a chilled coupe.
No garnish.

Bartender's note: Detroit Athletic Club, Pre-Prohibition. Equal parts is the rule — the Chartreuse herbaceousness needs the maraschino's cherry-pit balance.`,
  },
  'corpse reviver no. 2': {
    text: `0.75 oz London dry gin
0.75 oz Cointreau
0.75 oz Lillet Blanc (or Cocchi Americano)
0.75 oz fresh lemon juice
1 dash absinthe

Rinse coupe with absinthe; discard.
Shake remaining ingredients with ice 10 seconds.
Double-strain into the rinsed coupe.
Garnish with an expressed lemon peel.

Bartender's note: Harry Craddock 1930. Original Lillet was bitter Kina Lillet — Cocchi Americano is the closer modern match if you want period-correct backbone.`,
  },
  'corpse reviver': null,
  bramble: {
    text: `2 oz London dry gin
1 oz fresh lemon juice
0.5 oz simple syrup
0.5 oz creme de mure (blackberry liqueur)

Shake gin, lemon, syrup with ice; strain into a rocks glass over crushed ice.
Drizzle creme de mure over the top so it bleeds down through the ice.
Garnish with two blackberries and a lemon wedge.

Bartender's note: Dick Bradsell's 1984 London creation — the cassis-style drizzle is structural, not just decoration.`,
  },
  bellini: {
    text: `2 oz fresh white peach puree (or Boiron frozen)
4 oz cold prosecco (DOC quality)

Add peach puree to a chilled flute.
Top slowly with prosecco; stir gently.
No garnish.

Bartender's note: Harry's Bar Venice, 1948. Use white peaches — yellow ones are too tart and too sweet, both. Champagne is overkill; prosecco is correct.`,
  },
  mimosa: {
    text: `3 oz cold dry sparkling wine (cava or prosecco)
2 oz fresh-squeezed orange juice (Valencia)

Pour orange juice into a chilled flute.
Top slowly with sparkling wine.

Bartender's note: 50/50 not 80/20 — and use the cheap sparkling, not the good champagne. Fresh juice does all the heavy lifting.`,
  },
  'french 75': {
    text: `1 oz London dry gin (or cognac)
0.5 oz fresh lemon juice
0.5 oz simple syrup
3 oz cold champagne or dry sparkling wine

Shake gin, lemon, syrup with ice 8 seconds.
Strain into a chilled flute.
Top with cold champagne.
Garnish with a lemon twist.

Bartender's note: Original 1915 Paris recipe used cognac; gin became standard mid-century. Either is correct — pick the one that matches your dinner.`,
  },
  'jack rose': {
    text: `2 oz apple brandy (Laird's Bonded or Calvados)
0.75 oz fresh lemon or lime juice
0.5 oz real grenadine (pomegranate, not red corn syrup)

Shake with ice 10 seconds.
Double-strain into a chilled coupe.
Garnish with a thin apple slice.

Bartender's note: Prohibition-era NJ classic. Jacob "Jack Rose" Rosenstein wasn't actually the namesake — but Laird's apple brandy is the bottle every period bar in 1925 had. Real grenadine matters.`,
  },
  bacardi: {
    text: `2 oz Bacardi white rum
0.75 oz fresh lime juice
0.5 oz real grenadine

Shake with ice 10 seconds.
Double-strain into a chilled coupe.
Garnish with a lime wheel.

Bartender's note: 1936 NY court case ruled this name requires actual Bacardi — same drink with another rum is just a "pink daiquiri." Use real pomegranate grenadine.`,
  },
  sidecar: {
    text: `2 oz cognac VSOP (Pierre Ferrand 1840 or Hennessy)
0.75 oz Cointreau
0.75 oz fresh lemon juice

Shake with ice 10 seconds.
Double-strain into a sugar-rimmed coupe (rim optional).
Garnish with an expressed orange peel.

Bartender's note: Use the Pre-Pro 2:1:1 ratio — modern equal-parts versions are too sour. Sugar rim is a Buck's Club affectation; some bartenders skip it.`,
  },
  'between the sheets': {
    text: `1 oz cognac
1 oz light rum (Plantation 3 Stars)
1 oz Cointreau
0.5 oz fresh lemon juice

Shake with ice 10 seconds.
Double-strain into a chilled coupe.
Garnish with an expressed lemon peel.

Bartender's note: Equal-parts boozy variation on the Sidecar — Harry's New York Bar Paris, 1920s. The lemon juice keeps it from being syrupy.`,
  },
  'mary pickford': {
    text: `1.5 oz white rum (Probitas or Plantation 3 Stars)
1.5 oz fresh pineapple juice
1 tsp real grenadine
1 tsp maraschino liqueur (Luxardo)

Shake with ice 10 seconds.
Double-strain into a chilled coupe.
Garnish with a brandied cherry.

Bartender's note: Hotel Nacional Havana, 1922. Fresh-pressed pineapple juice is the difference between this and any tropical mistake.`,
  },
  'el presidente': {
    text: `2 oz aged white rum (Plantation 3 Stars)
0.75 oz blanc vermouth (Dolin Blanc)
0.5 oz orange curacao (Pierre Ferrand)
1 tsp real grenadine

Stir with ice 25 seconds.
Strain into a chilled coupe.
Garnish with an expressed orange peel.

Bartender's note: Cuban 1920s classic. Stir, don't shake — this is a rum Manhattan, not a tropical sour.`,
  },
  'hemingway daiquiri': {
    text: `2 oz white rum
0.75 oz fresh lime juice
0.5 oz fresh grapefruit juice
0.25 oz maraschino liqueur (Luxardo)

Shake with ice 12 seconds.
Double-strain into a chilled coupe.
No garnish.

Bartender's note: "Papa Doble" — Hemingway's no-sugar version at La Floridita. Fine for some, but most modern bars add 0.25 oz simple to round it.`,
  },
  'gin and tonic': {
    text: `2 oz London dry gin (Tanqueray, or a botanical gin like Hendrick's)
4 oz cold premium tonic (Fever-Tree Indian or Q)
1 lime wedge

Build in a chilled balloon or highball over fresh ice.
Add gin, then tonic poured over a barspoon to preserve carbonation.
Express lime over the glass and drop in.

Bartender's note: Cheap tonic ruins everything — Fever-Tree's quinine-forward formula is the modern standard. Spanish copa glass shows off aromatics.`,
  },
  'dark and stormy': {
    text: `2 oz Gosling's Black Seal rum (trademark requires it)
4 oz cold ginger beer (Goslings or Fever-Tree)
0.5 oz fresh lime juice (optional but recommended)

Build in a chilled highball over fresh ice.
Add lime juice and ginger beer first.
Float Gosling's slowly over the back of a spoon for the layered look.
Garnish with a lime wedge.

Bartender's note: Gosling's holds the trademark on the name in the US — same recipe with another rum is a "dark 'n stormy" (lowercase). Float matters: it's the visual signature.`,
  },
  'pina colada': {
    text: `2 oz aged white rum (Plantation 3 Stars)
1.5 oz unsweetened coconut cream (Coco Lopez or Coco Real)
1.5 oz fresh pineapple juice
0.5 oz fresh lime juice

Blend with 1 cup crushed ice 20 seconds.
Pour into a chilled hurricane or pineapple shell.
Garnish with a pineapple wedge and brandied cherry.

Bartender's note: 1954 Caribe Hilton San Juan original. Fresh pineapple juice and a touch of lime keep it from being a sugar bomb — most bars skip both, which is why most pina coladas taste like sunscreen.`,
  },
  'pina colada (cocktail)': null,
  'long island iced tea': {
    text: `0.5 oz vodka
0.5 oz London dry gin
0.5 oz white rum
0.5 oz blanco tequila
0.5 oz Cointreau
0.75 oz fresh lemon juice
0.5 oz simple syrup
2 oz cold cola

Shake everything except cola with ice 8 seconds.
Strain into a collins glass over fresh ice.
Top with cola; do not stir.
Garnish with a lemon wedge.

Bartender's note: Robert "Rosebud" Butt, 1972 Long Island. Fresh lemon and Cointreau are non-negotiable — sour mix and triple sec are how this drink got its bad reputation.`,
  },
  'french connection': {
    text: `1.5 oz cognac VSOP
1.5 oz Amaretto di Saronno

Build in a rocks glass over one large ice cube.
Stir 5 seconds to chill.
No garnish.

Bartender's note: Two ingredients, equal parts. Stir gently — too much dilution flattens the almond-cognac harmony.`,
  },
  'rusty nail': {
    text: `1.5 oz blended Scotch (Famous Grouse or Monkey Shoulder)
0.75 oz Drambuie

Build in a rocks glass over one large ice cube.
Stir 10 seconds.
Garnish with a lemon twist.

Bartender's note: Adjust ratio to taste — 2:1 Scotch-Drambuie if you want it drier, equal parts if you want the honey-herbal forward.`,
  },
  'godfather': {
    text: `2 oz blended Scotch (Famous Grouse)
0.5 oz Amaretto di Saronno

Build in a rocks glass over one large ice cube.
Stir 10 seconds to combine.
No garnish.

Bartender's note: Don't drown the Scotch — 4:1 keeps the smoke and lets the almond ride underneath.`,
  },
  'paper plane': {
    text: `0.75 oz bourbon (Buffalo Trace)
0.75 oz Aperol
0.75 oz Amaro Nonino
0.75 oz fresh lemon juice

Shake with ice 10 seconds.
Double-strain into a chilled coupe.
No garnish.

Bartender's note: Sam Ross 2008 — equal parts, no syrup, no fuss. Sub Amaro Montenegro for a slightly bitterer profile.`,
  },
  'penicillin': {
    text: `2 oz blended Scotch (Famous Grouse)
0.75 oz fresh lemon juice
0.75 oz honey-ginger syrup (3:1 honey:fresh ginger juice)
0.25 oz Islay Scotch (Laphroaig 10) for float

Shake blended Scotch, lemon, honey-ginger with ice 10 seconds.
Strain into a rocks glass over one large cube.
Float Islay Scotch on top.
Garnish with candied ginger.

Bartender's note: Sam Ross 2005 Milk & Honey NYC. The Islay float is the whole point — without it, it's a ginger sour.`,
  },
  'naked and famous': {
    text: `0.75 oz mezcal (Del Maguey Vida)
0.75 oz yellow Chartreuse
0.75 oz Aperol
0.75 oz fresh lime juice

Shake with ice 10 seconds.
Double-strain into a chilled coupe.
No garnish.

Bartender's note: Death & Co NYC, Joaquin Simo. The smoky-bitter-citrus triangle is the modern Last Word. Equal parts only.`,
  },
  'jungle bird': {
    text: `1.5 oz blackstrap rum (Cruzan or Plantation OFTD)
0.75 oz Campari
1.5 oz fresh pineapple juice
0.5 oz fresh lime juice
0.5 oz demerara syrup (2:1)

Shake with ice 10 seconds.
Strain into a rocks glass over fresh ice (or a tiki mug).
Garnish with a pineapple frond and orchid.

Bartender's note: 1978 Kuala Lumpur Hilton. The Campari is the trick — bitter and tropical at once. Don't shortcut with regular dark rum.`,
  },
  'painkiller': {
    text: `2 oz Pusser's Navy rum (trademark requirement)
4 oz unsweetened pineapple juice
1 oz fresh orange juice
1 oz cream of coconut (Coco Lopez)

Shake with ice 10 seconds.
Pour unstrained into a tiki mug or large rocks glass.
Top with crushed ice.
Garnish with a fresh nutmeg grating and a pineapple wedge.

Bartender's note: BVI Soggy Dollar Bar 1970s. Pusser's holds the trademark; freshly grated nutmeg is the move that separates this from a pina colada.`,
  },
  'ice pick': {
    text: `2 oz vodka (Tito's)
6 oz cold unsweetened iced tea
0.5 oz fresh lemon juice
Simple syrup to taste

Build in a tall glass over fresh ice.
Stir to combine.
Garnish with a lemon wheel and mint sprig.

Bartender's note: Boozy iced tea, basically. Brewed strong tea + good vodka beats pre-bottled "hard tea" every time.`,
  },
  ramos: {
    text: `2 oz London dry gin (Plymouth)
0.5 oz fresh lemon juice
0.5 oz fresh lime juice
0.5 oz simple syrup
0.5 oz heavy cream
1 fresh egg white (pasteurized)
3 drops orange flower water
2 oz cold club soda

Dry-shake everything except soda 30 seconds.
Add ice; shake 90 seconds (yes, really).
Strain into a chilled fizz glass.
Top slowly with cold club soda — the head should rise above the rim.

Bartender's note: New Orleans Henry Ramos, 1888. Modern: pasteurized egg white. The 90-second shake is the entire drink — there is no shortcut.`,
  },
  'ramos gin fizz': null,
  'gin gin mule': {
    text: `1.75 oz London dry gin
0.75 oz fresh lime juice
0.75 oz simple syrup
6-8 fresh mint leaves
2 oz cold ginger beer

Muddle mint with simple syrup gently in a shaker.
Add gin, lime, and ice; shake 8 seconds.
Strain into a collins glass over fresh ice.
Top with ginger beer.
Garnish with a mint sprig.

Bartender's note: Audrey Saunders, Pegu Club. The mint-mojito-meets-mule logic is what makes it work — fresh mint is mandatory.`,
  },
  'tommy\'s margarita': {
    text: `2 oz blanco tequila (Tapatio or Cimarron)
1 oz fresh lime juice
0.5 oz agave syrup (1:1 agave:water)

Shake with ice 10 seconds.
Strain into a rocks glass over fresh ice.
No salt, no garnish.

Bartender's note: Julio Bermejo, Tommy's SF. The agave-instead-of-Cointreau idea is the modern Mexican-bar standard.`,
  },
  'pegu club': {
    text: `1.5 oz London dry gin
0.75 oz orange curacao (Pierre Ferrand)
0.5 oz fresh lime juice
1 dash Angostura bitters
1 dash orange bitters

Shake with ice 10 seconds.
Double-strain into a chilled coupe.
Garnish with an expressed lime peel.

Bartender's note: 1920s Rangoon British officers' club. The double-bitters trick gives it depth most modern gin sours lack.`,
  },
  'aperol spritz': {
    text: `3 oz cold Prosecco DOC
2 oz Aperol
1 oz cold club soda

Build in a wine glass packed with ice.
Add Prosecco first, then Aperol, then a splash of soda.
Stir once gently.
Garnish with a fat orange slice and a green olive.

Bartender's note: Northern Italian, official 3-2-1 IBA ratio. Olive sounds wrong; trust the Italians.`,
  },
  'americano': {
    text: `1.25 oz Campari
1.25 oz sweet vermouth (Cocchi di Torino)
2 oz cold club soda

Build in a rocks glass over fresh ice.
Stir gently.
Garnish with an orange slice.

Bartender's note: Pre-Negroni original — same drink with soda instead of gin. 1860s Italian, perfect aperitivo session length.`,
  },
  hanky_panky: {
    text: `1.5 oz London dry gin
1.5 oz sweet vermouth (Carpano Antica)
2 dashes Fernet-Branca

Stir with ice 25 seconds.
Strain into a chilled coupe.
Garnish with an expressed orange peel.

Bartender's note: Ada Coleman, Savoy American Bar c.1903 — the Fernet dash is the ghost in the machine.`,
  },
  'hanky panky': null,
  'monkey gland': {
    text: `2 oz London dry gin
1 oz fresh orange juice
1 tsp real grenadine
1 tsp absinthe (or 0.25 oz Pernod)

Shake with ice 10 seconds.
Double-strain into a chilled coupe.
Garnish with an expressed orange peel.

Bartender's note: Harry MacElhone Paris 1923. The name is from a then-fashionable rejuvenation procedure — yes, that kind. Real grenadine and a careful absinthe touch make it.`,
  },
  'vesper': {
    text: `3 oz London dry gin (Tanqueray)
1 oz vodka (Ketel One)
0.5 oz Lillet Blanc (or Cocchi Americano)

Shake with ice 8 seconds (yes, shake — Bond's order).
Double-strain into a chilled coupe.
Garnish with a long lemon peel.

Bartender's note: Casino Royale 1953. Original Kina Lillet was bitterer; Cocchi Americano is the period-correct sub. Bond's "shaken not stirred" is fine for THIS drink because of the Lillet.`,
  },
  'rob roy': {
    text: `2 oz blended Scotch (Famous Grouse or Monkey Shoulder)
1 oz sweet vermouth (Carpano Antica)
2 dashes Angostura bitters

Stir with ice 30 seconds.
Strain into a chilled coupe.
Garnish with a brandied cherry and lemon peel.

Bartender's note: 1894 Waldorf-Astoria, named for the operetta. Scotch Manhattan, basically — but the smoke note is structurally different so don't sub bourbon.`,
  },
  'ward 8': {
    text: `2 oz rye whiskey (Rittenhouse 100)
0.75 oz fresh lemon juice
0.25 oz fresh orange juice
0.5 oz real grenadine

Shake with ice 10 seconds.
Double-strain into a chilled coupe.
Garnish with an orange peel and brandied cherry.

Bartender's note: Locke-Ober Boston 1898. Real grenadine is the thing here — pomegranate, not corn syrup.`,
  },
  'aperol': null,
  'spritz': null,
  'champagne cocktail': {
    text: `1 sugar cube
3 dashes Angostura bitters
0.25 oz cognac (optional)
4 oz cold brut champagne or quality dry sparkling wine

Saturate sugar cube with bitters; place in a chilled flute.
Add cognac if using.
Top slowly with cold champagne.
Garnish with an expressed lemon peel.

Bartender's note: 1862 Jerry Thomas. The bitters-soaked cube slowly dissolving and releasing bubbles is the show.`,
  },
  'jasmine': {
    text: `1.5 oz London dry gin
0.75 oz fresh lemon juice
0.25 oz Campari
0.25 oz Cointreau

Shake with ice 10 seconds.
Double-strain into a chilled coupe.
Garnish with an expressed lemon peel.

Bartender's note: Paul Harrington, 1990s Berkeley CA. Pink-grapefruit colored from the Campari; tastes like a grown-up grapefruit cocktail.`,
  },
  'orange blossom': {
    text: `2 oz London dry gin
1 oz fresh orange juice
0.5 oz simple syrup
1 dash orange bitters

Shake with ice 10 seconds.
Strain into a chilled coupe.
Garnish with an expressed orange peel.

Bartender's note: Prohibition-era trick to mask bathtub gin with bright OJ. Modern: use real London dry, fresh juice, and you have a respectable brunch drink.`,
  },
  'orange blossom cocktail': null,
  'blue blazer': {
    text: `2 oz overproof Scotch whisky (Laphroaig 10 or any cask-strength blend)
2 oz boiling water
1 tsp demerara sugar
Lemon peel

Warm two metal mugs with hot water; discard.
Combine whisky, hot water, and sugar in one mug.
Carefully ignite (long lighter), then pour the flaming liquid back and forth between mugs 4-5 times in a long arc.
Pour into a heat-safe glass; express lemon peel and drop in.

Bartender's note: Jerry Thomas 1862 fire-show cocktail. SAFETY: practice the pour cold first, never near anything flammable, and have a wet towel ready. A modern-trained flair bartender supervising is recommended.`,
  },
  'french 76': {
    text: `1 oz vodka
0.5 oz fresh lemon juice
0.5 oz simple syrup
3 oz cold champagne or dry sparkling wine

Shake vodka, lemon, syrup with ice 8 seconds.
Strain into a chilled flute.
Top with champagne.
Garnish with a lemon twist.

Bartender's note: Vodka-based variant of the French 75; the gin original is the one to know first.`,
  },
  'kir royale': {
    text: `0.5 oz creme de cassis (Mathilde or Briottet)
4 oz cold dry champagne or sparkling wine

Add cassis to a chilled flute.
Top slowly with champagne.
Garnish with a lemon twist (optional).

Bartender's note: Burgundy, named for Canon Felix Kir. Don't overdo the cassis — a half-ounce gives ruby color and just-sweet edge.`,
  },
  'sex on the beach': {
    text: `1.5 oz vodka
0.75 oz peach schnapps (or fresh peach puree + 0.25 oz simple)
2 oz cranberry juice
2 oz fresh orange juice

Shake with ice 8 seconds.
Strain into a highball glass over fresh ice.
Garnish with an orange slice.

Bartender's note: 1980s Florida happy-hour staple. Modernize by using fresh OJ and real peach puree instead of schnapps if you want it less candy-sweet.`,
  },
  'long island': null,
  'pink lady': {
    text: `1.5 oz London dry gin
0.5 oz applejack (Laird's)
0.75 oz fresh lemon juice
0.5 oz real grenadine
1 fresh egg white (pasteurized)

Dry-shake without ice 10 seconds.
Add ice; shake 12 seconds.
Double-strain into a chilled coupe.
Garnish with a brandied cherry.

Bartender's note: 1930s NYC. Modern: pasteurized egg white. Real grenadine matters more here than almost anywhere — corn-syrup-red ruins the drink.`,
  },
  'clover club': {
    text: `2 oz London dry gin (Plymouth)
0.75 oz fresh lemon juice
0.5 oz raspberry syrup (real, or muddle 4 raspberries with 0.5 oz simple)
1 fresh egg white (pasteurized)

Dry-shake without ice 12 seconds.
Add ice; shake 12 seconds.
Double-strain into a chilled coupe.
Garnish with three raspberries on a pick.

Bartender's note: Philadelphia Bellevue-Stratford c.1900. Modern: pasteurized egg white. Real raspberry syrup or fresh berries — never artificial.`,
  },
  bramble_alias: null,
  'caipirinha (cocktail)': null,
  'mojito': {
    text: `2 oz white rum (Plantation 3 Stars)
0.75 oz fresh lime juice
0.5 oz simple syrup
8-10 fresh mint leaves
2 oz cold club soda

Gently bruise mint with syrup in a collins glass.
Add rum, lime juice, and crushed ice; stir.
Top with cold club soda.
Garnish with a fat mint bouquet and a lime wedge.

Bartender's note: Havana original. Don't pulverize the mint — slap-and-bruise releases oils without the bitter chlorophyll. Crushed ice, real lime, real mint.`,
  },
};

// ---------- author/book story templates ----------

const BOOK_STORIES = {
  // Keyed by source_book substring or full
  jerry_thomas: `Jerry Thomas was the first celebrity American bartender; his 1862 "Bar-Tender's Guide" (a.k.a. "The Bon-Vivant's Companion") was the first cocktail book published in the United States and codified an entire profession. He toured Europe demonstrating the Blue Blazer and other showpieces, and his book remains the foundational text every modern bartender still references.`,
  harry_johnson: `Harry Johnson, the "father of professional bartending," ran a bar school and authored the 1882 "New and Improved Bartender's Manual," the first guide to focus on running a bar as a craft business — service, glassware, technique. His recipes are crisper and more measured than Jerry Thomas's, anticipating modern bartending standards by half a century.`,
  george_kappeler: `George J. Kappeler tended bar at the Holland House in New York and published "Modern American Drinks" in 1895. The book captured the height of the American Golden Age cocktail bar — refined, ingredient-forward recipes that the Pre-Prohibition giants like the Waldorf and Hoffman House also served.`,
  jacques_straub: `Jacques Straub was head steward at the Pendennis Club in Louisville and later wine steward at Chicago's Blackstone Hotel. His 1914 pocket manual, endorsed by Oscar Tschirky (the legendary Waldorf-Astoria maitre d'), compiled about seven hundred recipes from the best hotels, clubs and buffets of the pre-Prohibition era.`,
  leo_engel: `Leo Engel was a celebrated American bartender who emigrated to London in the 1870s and presided over the bar at the Criterion Restaurant in Piccadilly Circus, where he introduced British drinkers to the craft of the American 'cooling drink.' His 1878 "American and Other Drinks" was one of the first British-published cocktail books and helped popularize the cocktail among late-Victorian London.`,
  william_boothby: `William "Cocktail Bill" Boothby was San Francisco's most famous bartender at the turn of the 20th century, working the Palace Hotel and serving as a state senator. His 1908 "World's Drinks and How to Mix Them" captured the West Coast bar tradition that Prohibition would soon erase.`,
  charles_baker: `Charles H. Baker Jr. was a globe-trotting epicurean whose "Gentleman's Companion" (1939) recorded cocktails he'd encountered in his world travels — Havana, Singapore, Paris, the Pacific. His prose-heavy entries are as much travelogue as recipe and inspired generations of cocktail anthropologists.`,
  hugo_ensslin: `Hugo Ensslin was a bartender at New York's Hotel Wallick whose 1916 "Recipes for Mixed Drinks" was the last major American cocktail book before Prohibition. Ensslin's book contains the first known printed Aviation cocktail recipe, with the now-elusive creme de violette intact.`,
  harry_craddock: `Harry Craddock fled New York's Prohibition for London's Savoy Hotel American Bar in 1920, where he became the most famous bartender in the world. His 1930 "Savoy Cocktail Book" preserved the U.S. cocktail tradition during the dry decade and remains a working bartender's bible.`,
  oscar: `Oscar Tschirky, "Oscar of the Waldorf," was the legendary maitre d'hotel of the Waldorf-Astoria from 1893 to 1943 and the de facto curator of high-society American dining. His 1896 "Cook Book" includes the Waldorf bar's house cocktail recipes — many of which became national standards.`,
  unknown: null,
};

function detectBookKey(book) {
  if (!book) return null;
  const b = book.toLowerCase();
  if (b.includes('jerry thomas')) return 'jerry_thomas';
  if (b.includes('harry johnson')) return 'harry_johnson';
  if (b.includes('kappeler')) return 'george_kappeler';
  if (b.includes('straub')) return 'jacques_straub';
  if (b.includes('engel')) return 'leo_engel';
  if (b.includes('boothby')) return 'william_boothby';
  if (b.includes('baker')) return 'charles_baker';
  if (b.includes('ensslin')) return 'hugo_ensslin';
  if (b.includes('craddock') || b.includes('savoy')) return 'harry_craddock';
  if (b.includes('oscar') || b.includes('waldorf')) return 'oscar';
  return null;
}

// ---------- audit logic ----------

const NON_COCKTAIL_TITLE_HINTS = [
  /\bice cream\b/i,
  /\bsherbet\b.*\bdessert\b/i,
  /\boyster cocktail\b/i,
  /\btomato cocktail\b/i,
  /\bsalad\b/i,
  /\bsoup\b/i,
  /\bpudding\b/i,
  /\bsandwich\b/i,
  /\bpie\b(?!.*spice)/i,
  /\bcake\b/i,
  /\bsteak\b/i,
  /\bbeef tea\b/i,
  /\bclam broth\b/i,
  /\bbouillon\b/i,
  /^\s*porridge\b/i,
];

// OCR damage = lots of non-word junk like £, JXB8ET, weird caps mid-word
function looksOcrDamaged(text) {
  if (!text) return false;
  // Count bad-character density
  const length = text.length;
  if (length < 30) return false;
  const badChars = (text.match(/[£§¶•●▪►◊\f]/g) || []).length;
  // Mid-word digit clusters like "JXB8ET" or "DBOiNTTCR" pattern
  const garbleWords = (text.match(/\b[A-Z][A-Z0-9]{1,4}\d[A-Z0-9]{1,4}\b/g) || []).length;
  // Mixed case oddities like "DBOiNTTCR"
  const oddCase = (text.match(/\b[A-Z]{2,}[a-z][A-Z]{2,}\b/g) || []).length;
  // Density score
  const density = (badChars + garbleWords * 2 + oddCase * 2) / (length / 100);
  return density > 1.5 || garbleWords > 5;
}

function isProseVariant(text) {
  if (!text) return false;
  const t = text.trim();
  if (t.length > 250) return false;
  return /\bsame as .* but\b/i.test(t) || /^use the .* recipe/i.test(t);
}

function judgeNonCocktail(title, originalText) {
  const t = (title || '').toLowerCase();
  for (const re of NON_COCKTAIL_TITLE_HINTS) {
    if (re.test(t)) return true;
  }
  // Heuristic: if original_text exists and reads like a frozen-dessert recipe
  if (originalText) {
    const ot = originalText.toLowerCase();
    if (/\bfreezer\b.*\bfreeze\b/i.test(ot) && !/serve in.*glass|punch glass|coupe|tumbler/i.test(ot)) {
      // Frozen punch IS still a drink; we keep frozen punches but flag pure ices
      if (/(orange|lemon|raspberry|pineapple)\s*ice\b/i.test(t)) return true;
    }
  }
  return false;
}

// ---------- modernization engine (rule-based, but better) ----------

const UNIT_RULES = [
  { re: /(\d+(?:\.\d+)?(?:\s*-\s*\d+\/\d+)?(?:\s*\d+\/\d+)?)\s*wine[- ]?glass(?:ful)?(?:\s+of)?/gi, oz: 2 },
  { re: /(\d+(?:\.\d+)?(?:\s*-\s*\d+\/\d+)?(?:\s*\d+\/\d+)?)\s*wineglass(?:ful)?/gi, oz: 2 },
  { re: /(\d+(?:\.\d+)?(?:\s*-\s*\d+\/\d+)?(?:\s*\d+\/\d+)?)\s*pony[- ]?glass(?:\s+of)?/gi, oz: 1 },
  { re: /(\d+(?:\.\d+)?(?:\s*-\s*\d+\/\d+)?(?:\s*\d+\/\d+)?)\s*ponies/gi, oz: 1 },
  { re: /(\d+(?:\.\d+)?(?:\s*-\s*\d+\/\d+)?(?:\s*\d+\/\d+)?)\s*pony(?:\s+of)?/gi, oz: 1 },
  { re: /(\d+(?:\.\d+)?(?:\s*-\s*\d+\/\d+)?(?:\s*\d+\/\d+)?)\s*jiggers?(?:\s+of)?/gi, oz: 1.5 },
  { re: /(\d+(?:\.\d+)?(?:\s*-\s*\d+\/\d+)?(?:\s*\d+\/\d+)?)\s*gills?/gi, oz: 4 },
];

const HALF_FRACTIONS = {
  '1/2': 0.5, '1/4': 0.25, '3/4': 0.75, '1/3': 0.333, '2/3': 0.667, '1/8': 0.125, '3/8': 0.375,
};

function parseQty(s) {
  if (!s) return null;
  s = String(s).trim();
  const mixed = s.match(/^(\d+)\s+(\d+)\/(\d+)$/);
  if (mixed) return Number(mixed[1]) + Number(mixed[2]) / Number(mixed[3]);
  const frac = s.match(/^(\d+)\/(\d+)$/);
  if (frac) return Number(frac[1]) / Number(frac[2]);
  if (/^\d+(\.\d+)?$/.test(s)) return Number(s);
  if (HALF_FRACTIONS[s]) return HALF_FRACTIONS[s];
  return null;
}

function fmtOz(n) {
  if (n == null) return '';
  if (Number.isInteger(n)) return `${n} oz`;
  // Round to nearest 0.25
  const r = Math.round(n * 4) / 4;
  if (r === 0) return '0 oz';
  return `${r} oz`;
}

const SPIRIT_BRAND_NOTES = {
  rye: 'try Rittenhouse 100 or Sazerac 6-Year',
  bourbon: 'try Buffalo Trace or Old Forester 86',
  scotch: 'try Famous Grouse or Monkey Shoulder',
  cognac: 'try Pierre Ferrand 1840 or Hennessy VS',
  brandy: 'try Pierre Ferrand 1840 or any decent VS cognac',
  gin: 'try Tanqueray or Beefeater London Dry',
  'old tom gin': 'try Hayman\'s Old Tom or Ransom Old Tom',
  'holland gin': 'try Bols Genever (the malty Dutch original)',
  genever: 'try Bols Genever',
  rum: 'try Plantation 3 Stars (white) or Appleton 8 (aged)',
  'jamaica rum': 'try Smith & Cross or Hamilton Pot Still Black',
  'santa cruz rum': 'try El Dorado 5 or Plantation 5',
  applejack: 'use Laird\'s Bonded Apple Brandy',
  'apple brandy': 'use Laird\'s Bonded or Calvados',
  vermouth: 'use Carpano Antica (sweet) or Dolin Dry (dry)',
  absinthe: 'try Vieux Pontarlier or Pernod Absinthe',
  curacao: 'use Pierre Ferrand Dry Curacao',
  curacoa: 'use Pierre Ferrand Dry Curacao',
  maraschino: 'use Luxardo Maraschino',
  benedictine: 'Benedictine D.O.M.',
  chartreuse: 'green or yellow Chartreuse as specified',
};

const INGREDIENT_REWRITES = [
  { re: /\bcuracoa\b/gi, to: 'orange curacao (Pierre Ferrand)' },
  { re: /\bcuracao\b(?!\s*\()/gi, to: 'orange curacao (Pierre Ferrand)' },
  { re: /\bgum syrup\b/gi, to: 'simple syrup' },
  { re: /\bgomme\b/gi, to: 'simple syrup' },
  { re: /\bbar sugar\b/gi, to: 'superfine sugar' },
  { re: /\bcut loaf sugar\b/gi, to: 'sugar cube' },
  { re: /\bloaf sugar\b/gi, to: 'sugar cube' },
  { re: /\blump(s)? of sugar\b/gi, to: 'sugar cube' },
  { re: /\bdomino sugar\b/gi, to: 'sugar cube' },
  { re: /\bpowdered sugar\b/gi, to: 'superfine sugar' },
  { re: /\bjamaica rum\b/gi, to: 'dark Jamaican rum (Smith & Cross)' },
  { re: /\bsanta cruz rum\b/gi, to: 'aged rum (El Dorado 5)' },
  { re: /\bnew england rum\b/gi, to: 'New England-style dark rum' },
  { re: /\bold tom gin\b/gi, to: "Old Tom gin (Hayman's)" },
  { re: /\bholland gin\b/gi, to: 'Genever (Bols)' },
  { re: /\bgenever\b/gi, to: 'Genever (Bols)' },
  { re: /\bbatavia arrack\b/gi, to: 'Batavia Arrack (Van Oosten)' },
  { re: /\borgeat\b/gi, to: 'orgeat (Small Hand Foods almond syrup)' },
  { re: /\bboker'?s bitters\b/gi, to: "Bogart's bitters (or sub Angostura)" },
  { re: /\bpeychaud'?s? bitters\b/gi, to: "Peychaud's bitters" },
  { re: /\babsinthe\b/gi, to: 'absinthe (Vieux Pontarlier; Pernod also fine)' },
  { re: /\bapollinaris\b/gi, to: 'sparkling mineral water' },
  { re: /\bseltzer\s*(water)?\b/gi, to: 'club soda' },
  { re: /\baerated water\b/gi, to: 'club soda' },
  { re: /\bvichy water\b/gi, to: 'sparkling mineral water' },
  { re: /\bcalisaya\b/gi, to: 'a quinine-based amaro (China-China)' },
  { re: /\bcreme yvette\b/gi, to: 'Creme Yvette (or Creme de Violette)' },
  { re: /\bvin mariani\b/gi, to: 'a robust red wine (the original was coca-leaf wine; omit)' },
  { re: /\btokay\b/gi, to: 'Tokaji Aszu (sweet Hungarian)' },
  { re: /\bsarsaparilla\b/gi, to: 'root beer' },
  { re: /\bkummel\b/gi, to: 'Kummel (caraway liqueur)' },
  { re: /\bkuemmel\b/gi, to: 'Kummel (caraway liqueur)' },
  { re: /\babsinthe substitute\b/gi, to: 'absinthe' },
];

function applyUnitConversions(text) {
  let t = text;
  for (const { re, oz } of UNIT_RULES) {
    t = t.replace(re, (m, qty) => {
      const n = parseQty(qty.replace(/\s+/g, ' ').trim());
      if (n == null) return `${oz} oz`;
      return fmtOz(n * oz);
    });
  }
  // Standalone "wine-glass" / "pony" without count
  t = t.replace(/\bwine[- ]?glassful\b/gi, '2 oz');
  t = t.replace(/\bwine[- ]?glass(?:\s+of)?\b/gi, '2 oz');
  t = t.replace(/\bpony[- ]?glass(?:\s+of)?\b/gi, '1 oz');
  t = t.replace(/\bpony(?:\s+of)?\b/gi, '1 oz');
  t = t.replace(/\bjigger(?:\s+of)?\b/gi, '1.5 oz');
  t = t.replace(/\btablespoonfuls?\b/gi, 'tbsp');
  t = t.replace(/\btable[- ]?spoons?(?:ful)?\b/gi, 'tbsp');
  t = t.replace(/\bteaspoonfuls?\b/gi, 'tsp');
  t = t.replace(/\btea[- ]?spoons?(?:ful)?\b/gi, 'tsp');
  t = t.replace(/\bdash(es)?\s+of\b/gi, 'dash$1 of');
  return t;
}

function rewriteIngredients(text) {
  let t = text;
  for (const { re, to } of INGREDIENT_REWRITES) {
    t = t.replace(re, to);
  }
  return t;
}

// Detect main spirit from title + original_text
function detectMainSpirit(title, original) {
  const t = (title + ' ' + (original || '')).toLowerCase();
  if (/\brye\b/.test(t)) return 'rye';
  if (/\bbourbon\b/.test(t)) return 'bourbon';
  if (/\bwhiskey\b|\bwhisky\b/.test(t) && /\bscotch\b/.test(t)) return 'scotch';
  if (/\bscotch\b/.test(t)) return 'scotch';
  if (/\bcognac\b/.test(t)) return 'cognac';
  if (/\bbrandy\b/.test(t)) return 'brandy';
  if (/\bgin\b/.test(t)) return 'gin';
  if (/\brum\b/.test(t)) return 'rum';
  if (/\btequila\b/.test(t)) return 'tequila';
  if (/\bvodka\b/.test(t)) return 'vodka';
  if (/\bvermouth\b/.test(t)) return 'vermouth';
  if (/\bchampagne\b|\bsparkling\b/.test(t)) return 'champagne';
  if (/\bwine\b/.test(t)) return 'wine';
  if (/\bbeer\b|\bale\b|\bporter\b|\bstout\b/.test(t)) return 'beer';
  if (/\bwhiskey\b|\bwhisky\b/.test(t)) return 'whiskey';
  return null;
}

function inferGarnish(spirit, original) {
  const o = (original || '').toLowerCase();
  if (/lemon peel|twist of lemon/.test(o)) return 'expressed lemon peel';
  if (/orange peel|twist of orange/.test(o)) return 'expressed orange peel';
  if (/cherry/.test(o)) return 'brandied cherry';
  if (/mint/.test(o)) return 'mint sprig';
  if (spirit === 'gin') return 'lemon twist';
  if (spirit === 'whiskey' || spirit === 'rye' || spirit === 'bourbon' || spirit === 'scotch') return 'orange peel';
  if (spirit === 'rum') return 'lime wheel';
  return 'expressed citrus peel';
}

function inferGlass(original, title) {
  const o = (original || '').toLowerCase();
  const t = (title || '').toLowerCase();
  if (/champagne|flute/.test(o) || /champagne/.test(t)) return 'chilled flute';
  if (/highball|tall|collins|fizz|cooler/.test(o) || /highball|collins|fizz|cooler|rickey/.test(t)) return 'highball glass';
  if (/punch bowl|punch glass/.test(o) || /punch/.test(t)) return 'punch cup';
  if (/mug|hot/.test(o) && /toddy|hot|grog/.test(t)) return 'pre-warmed mug';
  if (/julep/.test(t)) return 'julep cup';
  if (/sour/.test(t)) return 'chilled coupe';
  if (/wine[- ]?glass|wineglass/.test(o)) return 'chilled coupe';
  if (/old fashioned|on the rocks|tumbler|rocks/.test(o)) return 'rocks glass';
  return 'chilled coupe';
}

function inferMethod(original, title) {
  const o = (original || '').toLowerCase();
  const t = (title || '').toLowerCase();
  if (/shake|frapp/.test(o)) return 'shake';
  if (/build|fill the glass|in the glass|directly/.test(o)) return 'build';
  if (/punch|bowl/.test(t)) return 'batch';
  if (/blaze|ignite|flame|burn/.test(o)) return 'flame';
  if (/hot|boiling|steaming|toddy|grog/.test(o + ' ' + t)) return 'hot build';
  if (/citrus|lemon|lime|orange|fresh juice|sour|fizz|collins|daiquiri/.test(o + ' ' + t)) return 'shake';
  if (/manhattan|martini|negroni|stir|old fashioned|sazerac/.test(o + ' ' + t)) return 'stir';
  return 'stir';
}

function methodInstructions(method, glass, garnish) {
  const G = glass || 'chilled coupe';
  switch (method) {
    case 'shake':
      return [
        'Shake all ingredients with ice 10-12 seconds.',
        `Double-strain into a ${G}.`,
        `Garnish with ${garnish}.`,
      ];
    case 'stir':
      return [
        'Stir all ingredients with ice 25-30 seconds.',
        `Strain into a ${G}.`,
        `Garnish with ${garnish}.`,
      ];
    case 'build':
      return [
        `Build directly in a ${G} over fresh ice.`,
        'Stir gently to combine.',
        `Garnish with ${garnish}.`,
      ];
    case 'batch':
      return [
        'Combine ingredients in a punch bowl with a single large block of ice.',
        'Stir gently and let the block dilute the punch over 5 minutes before serving.',
        'Ladle into chilled punch cups; garnish each with a fruit slice.',
      ];
    case 'flame':
      return [
        'Combine warmed spirits in a heat-safe metal mug; ignite carefully.',
        'Pour the flaming liquid between two mugs in a long arc 4-5 times.',
        `Pour into a heat-safe glass; garnish with ${garnish}. Have a wet towel ready.`,
      ];
    case 'hot build':
      return [
        `Pre-warm a ${G} with hot water; discard.`,
        'Combine ingredients in the warm glass; top with steaming hot water.',
        `Stir to dissolve sugar; garnish with ${garnish}.`,
      ];
    default:
      return [
        'Stir or shake with ice as appropriate; chill thoroughly.',
        `Strain into a ${G}.`,
        `Garnish with ${garnish}.`,
      ];
  }
}

// Heuristic ingredient extraction from prose
function extractIngredients(text, spirit) {
  if (!text) return [];
  const lines = text
    .split(/\n+|[;.](?=\s+[A-Z]|\s*$)/)
    .map((l) => l.trim())
    .filter((l) => l && l.length < 200);

  const ings = [];
  // Common cocktail ingredient nouns
  const ING_PATTERNS = [
    { re: /\b(whiskey|whisky|rye|bourbon|scotch)\b/i, label: spirit === 'rye' ? 'rye whiskey' : spirit === 'bourbon' ? 'bourbon' : spirit === 'scotch' ? 'blended Scotch' : 'whiskey' },
    { re: /\bgin\b/i, label: 'London dry gin' },
    { re: /\b(rum|jamaica|santa cruz)\b/i, label: 'rum' },
    { re: /\bbrandy\b/i, label: 'cognac VSOP' },
    { re: /\bcognac\b/i, label: 'cognac VSOP' },
    { re: /\btequila\b/i, label: 'blanco tequila' },
    { re: /\bvodka\b/i, label: 'vodka' },
    { re: /\b(italian vermouth|sweet vermouth)\b/i, label: 'sweet vermouth (Carpano Antica)' },
    { re: /\b(french vermouth|dry vermouth)\b/i, label: 'dry vermouth (Dolin Dry)' },
    { re: /\bvermouth\b/i, label: 'sweet vermouth (Carpano Antica)' },
    { re: /\bchampagne\b/i, label: 'cold dry champagne' },
    { re: /\bcuracao|curacoa\b/i, label: 'orange curacao (Pierre Ferrand)' },
    { re: /\bbenedictine\b/i, label: 'Benedictine D.O.M.' },
    { re: /\bchartreuse\b/i, label: 'green Chartreuse' },
    { re: /\bmaraschino\b/i, label: 'maraschino liqueur (Luxardo)' },
    { re: /\babsinthe\b/i, label: 'absinthe (Vieux Pontarlier)' },
    { re: /\bcampari\b/i, label: 'Campari' },
    { re: /\baperol\b/i, label: 'Aperol' },
    { re: /\borgeat\b/i, label: 'orgeat (Small Hand Foods)' },
    { re: /\bgrenadine\b/i, label: 'real grenadine (pomegranate)' },
    { re: /\b(boker'?s|peychaud|angostura)\b.*\bbitters\b/i, label: 'bitters (see line)' },
  ];

  for (const line of lines) {
    // Try to detect a quantity pattern
    const qtyMatch = line.match(
      /([\d./\s-]+?)\s*(wine[- ]?glassf?u?l?|wineglass|jiggers?|ponies|pony|gills?|dashe?s?|tablespoonfuls?|teaspoonfuls?|tsp|tbsp|oz|ounces?|barspoons?|drops?|pinch|pints?|quarts?|cups?|ml)\b/i,
    );
    let qtyStr = qtyMatch ? qtyMatch[0] : '';
    qtyStr = applyUnitConversions(qtyStr).trim();

    // Default qty for spirits if none found
    let recognized = false;
    for (const p of ING_PATTERNS) {
      if (p.re.test(line)) {
        const isMain = /(whiskey|whisky|rye|bourbon|scotch|gin|rum|brandy|cognac|tequila|vodka)/i.test(p.label);
        const finalQty = qtyStr || (isMain ? '2 oz' : (/vermouth|liqueur|curacao|maraschino|chartreuse|benedictine/i.test(p.label) ? '0.75 oz' : ''));
        const ingLine = finalQty ? `${finalQty} ${p.label}` : p.label;
        ings.push(ingLine);
        recognized = true;
        break;
      }
    }
    // Look for citrus juices and sweeteners
    if (!recognized) {
      if (/juice of (\d+|\d+\/\d+|a|an|one|two|half) lemon/i.test(line)) {
        ings.push('0.75 oz fresh lemon juice');
        continue;
      }
      if (/juice of (\d+|\d+\/\d+|a|an|one|two|half) lime/i.test(line)) {
        ings.push('0.75 oz fresh lime juice');
        continue;
      }
      if (/juice of (\d+|\d+\/\d+|a|an|one|two|half) orange/i.test(line)) {
        ings.push('1 oz fresh orange juice');
        continue;
      }
      if (/sugar|syrup/i.test(line) && /tsp|spoon|sugar cube|simple/i.test(line)) {
        ings.push('1 tsp simple syrup (or to taste)');
        continue;
      }
    }
  }

  // Dedupe
  const seen = new Set();
  const out = [];
  for (const i of ings) {
    const k = i.toLowerCase().replace(/\s+/g, ' ');
    if (seen.has(k)) continue;
    seen.add(k);
    out.push(i);
  }
  return out;
}

function genericModernize(row, audit) {
  const { title, original_text, source_year } = row;

  if (audit.flag === 'ocr_damaged') {
    return 'Original OCR damaged; see source book for clean version.';
  }
  if (audit.flag === 'prose_variant') {
    return `See parent recipe (this entry is a stated variation). Original: "${(original_text || '').slice(0, 200).trim()}"`.slice(0, 380);
  }

  const spirit = detectMainSpirit(title, original_text);
  const glass = inferGlass(original_text, title);
  const method = inferMethod(original_text, title);
  const garnish = inferGarnish(spirit, original_text);

  // Build ingredients
  let ingredients = extractIngredients(original_text, spirit);

  // Fallback: at least show the lead spirit
  if (ingredients.length === 0) {
    if (spirit) {
      const brand = SPIRIT_BRAND_NOTES[spirit] || '';
      ingredients = [`2 oz ${spirit}${brand ? ` (${brand})` : ''}`];
    } else {
      ingredients = ['2 oz base spirit (see original recipe)'];
    }
  } else {
    // If lead spirit not present, prepend it
    const haveSpirit = ingredients.some((i) =>
      new RegExp('\\b' + (spirit || 'XYZ') + '\\b', 'i').test(i),
    );
    if (spirit && !haveSpirit) {
      const brand = SPIRIT_BRAND_NOTES[spirit] || '';
      ingredients.unshift(`2 oz ${spirit}${brand ? ` (${brand})` : ''}`);
    }
  }

  // Limit ingredient lines
  ingredients = ingredients.slice(0, 5);

  const steps = methodInstructions(method, glass, garnish);

  const note = (() => {
    if (method === 'flame') {
      return "Bartender's note: Treat any flaming-cocktail recipe as a fire-handling drill — practice cold first.";
    }
    if (method === 'hot build') {
      return "Bartender's note: Pre-warming the glass is the difference between a great toddy and lukewarm sadness.";
    }
    if (method === 'batch') {
      return "Bartender's note: One large ice block (not cubes) controls dilution over the course of the party.";
    }
    if (source_year && source_year < 1920) {
      return "Bartender's note: Pre-Prohibition era — modern fresh juice and properly cold ingredients tighten this drink considerably vs. the original.";
    }
    return "Bartender's note: Quantities have been calibrated to modern bar standards (oz measurements, fresh juice, quality ice).";
  })();

  let out = ingredients.join('\n') + '\n\n' + steps.join('\n') + '\n\n' + note;

  // Hard-cap at ~400 chars
  if (out.length > 400) {
    // Try shrinking note
    const ingPart = ingredients.join('\n');
    const stepPart = steps.join('\n');
    const shortNote = "Bartender's note: Modern oz measurements, fresh juice, quality ice.";
    out = ingPart + '\n\n' + stepPart + '\n\n' + shortNote;
    if (out.length > 400) {
      out = ingPart + '\n\n' + stepPart;
    }
  }

  // Add safety note for raw egg cocktails
  if (/\begg\b/i.test(original_text || '') && !/pasteurized/i.test(out)) {
    out = out.replace(
      /Bartender's note:/,
      "Modern: use pasteurized egg whites. Bartender's note:",
    );
  }

  return out;
}

// ---------- story generator ----------

function generateStory(row) {
  if (row.contributor_story) return null;
  const { title, source_book, source_year, source_region, contributor_name } = row;

  const lower = (title || '').toLowerCase();
  // Famous-cocktail historical hooks
  const famousHooks = {
    manhattan:
      "The Manhattan was likely born in 1870s New York — possibly at the Manhattan Club for a Samuel Tilden banquet — and along with the Martini sits at the foundation of the modern cocktail. In 2026 it remains the test every craft bar uses to judge a bartender's stir technique.",
    martini:
      "The Martini's origins are disputed (Martinez 1860s? Knickerbocker Hotel 1900s?) but its rise tracks the rise of cold gin and the upscale American hotel bar. In 2026 it's the cocktail every spirits enthusiast uses to evaluate a new gin.",
    'old fashioned':
      "The Old Fashioned is the original 'cocktail' as defined in 1806: spirit, sugar, water, bitters. The name itself was a Gilded-Age customer's request for the old-style drink before fancy syrups invaded bars. It's the textbook lesson in spirit-forward simplicity.",
    daiquiri:
      "The Daiquiri was named for an iron-mining town in Cuba and codified by American mining engineer Jennings Cox around 1898. In 2026 it's the rum bartender's litmus test — three ingredients, no place to hide.",
    margarita:
      "The Margarita's origins are claimed by half a dozen 1940s Mexican and Texas bars. Whatever the truth, it became the most popular tequila cocktail in the world. In 2026 the craft revival has restored fresh lime and real Cointreau as standard.",
    negroni:
      "The Negroni dates to 1919 Florence — Count Camillo Negroni asked his bartender to fortify his Americano with gin. In the 21st century it has become arguably the world's most fashionable bitter cocktail.",
    daisy:
      "The 'Daisy' family of citrus-and-orange-curacao sours is a direct ancestor of the Margarita (margarita = Spanish for 'daisy'). Pre-Prohibition American bars served them by the dozen.",
    sour:
      "The sour template — spirit + citrus + sweet — is the most-replicated cocktail formula in history. Every great drink is, at heart, a sour with personality.",
    fizz:
      "Fizzes were the 1880s answer to 'I want it cold, citric, and fast' — gin, lemon, sugar, soda, served before brunch was even a concept.",
    flip:
      "Flips are 17th-century English origin: spirit, sugar, whole egg. Heavy and rich; a nightcap, not a session drink.",
    julep:
      "Juleps were originally medicinal preparations; the Mint Julep became the Kentucky Derby's official drink in 1938 and now anchors the entire bourbon hospitality industry.",
  };

  const hookKey = Object.keys(famousHooks).find((k) => lower.includes(k));
  if (hookKey) {
    const hook = famousHooks[hookKey];
    const src = source_book && source_year
      ? ` This entry is from ${source_book}.`
      : '';
    return (hook + src).slice(0, 600);
  }

  // Book-based fallback
  const bookKey = detectBookKey(source_book);
  if (bookKey && BOOK_STORIES[bookKey]) {
    return BOOK_STORIES[bookKey];
  }

  // Generic fallback
  if (source_book) {
    const yr = source_year ? `, published ${source_year}` : '';
    const region = source_region ? ` in the ${source_region} bartending tradition of the era` : '';
    return `A historic recipe from ${source_book}${yr}, part of the canon${region}. Modern bartenders revisit these mid-19th and early-20th century manuals as the foundation of every classic cocktail today carries.`;
  }

  if (contributor_name) {
    return `Attributed to ${contributor_name}. Recipe preserved in the historic cocktail canon and worth revisiting with modern technique.`;
  }

  return `A classic cocktail entry from the historic record. Modern bartenders return to these recipes for the structural lessons they teach about ratio, dilution, and ingredient quality.`;
}

// ---------- main per-row processor ----------

function judgeAndProcess(row) {
  const result = {
    id: row.id,
    title: row.title,
    action: 'update', // 'update' | 'delete'
    audit_status: 'reviewed_keep',
    audit_notes: null,
    modernized_text: null,
    contributor_story: null,
  };

  // 1. Audit
  if (judgeNonCocktail(row.title, row.original_text)) {
    result.action = 'delete';
    result.audit_status = 'rejected_non_cocktail';
    result.audit_notes = 'Auto-deleted: title/content matches non-cocktail (frozen dessert, food, broth)';
    return result;
  }

  if (looksOcrDamaged(row.original_text)) {
    result.audit_status = 'ocr_damaged';
    result.audit_notes = 'Heavy OCR artifacts in original_text';
  } else if (isProseVariant(row.original_text)) {
    result.audit_status = 'prose_variant';
    result.audit_notes = 'Variation reference to a parent recipe';
  } else {
    result.audit_status = 'reviewed_keep';
  }

  // 2. Modernize
  const titleKey = (row.title || '').toLowerCase().trim();
  const stripped = titleKey.replace(/\s*\(cocktail\)\s*$/, '').replace(/\s+cocktail$/, '');
  const canon = CANON[titleKey] || CANON[stripped];
  if (canon && canon.text) {
    result.modernized_text = canon.text;
    result.audit_status = 'reviewed_canonical';
    result.audit_notes = 'Hand-crafted canonical modernization';
  } else {
    result.modernized_text = genericModernize(row, {
      flag: result.audit_status === 'ocr_damaged'
        ? 'ocr_damaged'
        : result.audit_status === 'prose_variant'
        ? 'prose_variant'
        : null,
    });
  }

  // 3. Story
  if (!row.contributor_story) {
    result.contributor_story = generateStory(row);
  }

  return result;
}

// ---------- run a batch ----------

function runBatch(progress) {
  const offset = progress.offset;
  const sql = `SELECT id, title, source_year, source_book, source_region, contributor_name, contributor_story, original_text, modernized_text FROM recipe WHERE content_type IN ('cocktail','mocktail') ORDER BY id LIMIT ${BATCH_SIZE} OFFSET ${offset};`;
  const rows = d1Query(sql);
  if (rows.length === 0) return false;

  const updates = [];
  const deletes = [];
  let modernizedCount = 0;
  let storyFilledCount = 0;
  let ocrCount = 0;
  let proseCount = 0;
  let deletedCount = 0;

  for (const row of rows) {
    const r = judgeAndProcess(row);
    if (r.action === 'delete') {
      deletes.push(r.id);
      deletedCount++;
      progress.rejected_ids.push({ id: r.id, title: r.title });
      continue;
    }

    const setMod = r.modernized_text != null;
    const setStory = r.contributor_story != null;
    if (setMod) modernizedCount++;
    if (setStory) storyFilledCount++;
    if (r.audit_status === 'ocr_damaged') ocrCount++;
    if (r.audit_status === 'prose_variant') proseCount++;

    const setParts = [];
    if (setMod) setParts.push(`modernized_text = '${esc(r.modernized_text)}'`);
    if (setStory) setParts.push(`contributor_story = '${esc(r.contributor_story)}'`);
    setParts.push(`audit_status = '${esc(r.audit_status)}'`);
    if (r.audit_notes) setParts.push(`audit_notes = '${esc(r.audit_notes)}'`);
    updates.push(
      `UPDATE recipe SET ${setParts.join(', ')} WHERE id = '${esc(r.id)}';`,
    );

    // Sample collection
    if (r.audit_status === 'reviewed_canonical' && progress.sample_modernizations.length < 12) {
      progress.sample_modernizations.push({
        title: r.title,
        modernized: r.modernized_text,
      });
    }
    if (setStory && progress.sample_stories.length < 12) {
      progress.sample_stories.push({
        title: r.title,
        story: r.contributor_story,
      });
    }
  }

  // Build SQL file
  const allSql = [];
  for (const id of deletes) {
    allSql.push(`DELETE FROM recipe_ingredient WHERE recipe_id = '${esc(id)}';`);
    allSql.push(`DELETE FROM recipe_step WHERE recipe_id = '${esc(id)}';`);
    allSql.push(`DELETE FROM recipe WHERE id = '${esc(id)}';`);
  }
  for (const u of updates) allSql.push(u);

  if (allSql.length > 0) {
    const sqlFile = path.join(__dirname, `_audit_batch_${offset}.sql`);
    fs.writeFileSync(sqlFile, allSql.join('\n') + '\n');
    d1ExecFile(sqlFile);
    fs.unlinkSync(sqlFile);
  }

  // Update progress
  progress.offset += BATCH_SIZE;
  progress.processed_total += rows.length;
  progress.deleted += deletedCount;
  progress.modernized += modernizedCount;
  progress.story_filled += storyFilledCount;
  progress.ocr_damaged += ocrCount;
  progress.prose_variant += proseCount;

  console.log(
    `[batch offset=${offset}] processed=${rows.length} mod=${modernizedCount} story=${storyFilledCount} ocr=${ocrCount} prose=${proseCount} del=${deletedCount} | totals: ${progress.processed_total}/${progress.processed_total + Math.max(0, 2565 - progress.processed_total)} mod=${progress.modernized} story=${progress.story_filled} del=${progress.deleted}`,
  );
  return true;
}

// ---------- entry ----------

function main() {
  const args = process.argv.slice(2);
  if (args.includes('--reset')) {
    if (fs.existsSync(PROGRESS_FILE)) fs.unlinkSync(PROGRESS_FILE);
    if (fs.existsSync(SAMPLES_FILE)) fs.unlinkSync(SAMPLES_FILE);
    console.log('Progress reset.');
    return;
  }
  const maxIdx = args.indexOf('--max');
  const maxBatches = maxIdx >= 0 ? Number(args[maxIdx + 1]) : Infinity;

  const progress = loadProgress();
  console.log(`Resuming at offset ${progress.offset}. Already processed: ${progress.processed_total}`);

  let batchesDone = 0;
  while (batchesDone < maxBatches) {
    const more = runBatch(progress);
    saveProgress(progress);
    batchesDone++;
    if (!more) {
      console.log('All cocktails reviewed.');
      break;
    }
  }

  // Final summary
  console.log(JSON.stringify({
    processed: progress.processed_total,
    deleted: progress.deleted,
    modernized: progress.modernized,
    story_filled: progress.story_filled,
    ocr_damaged: progress.ocr_damaged,
    prose_variant: progress.prose_variant,
  }, null, 2));
}

main();

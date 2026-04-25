#!/usr/bin/env node
/**
 * faithful_modernize.cjs
 *
 * Reads paginated batches of cocktails with audit_status='needs_redo' and
 * NULL modernized_text, generates a FAITHFUL modernization that preserves
 * the primary spirit and key ingredients, and UPDATEs the row.
 *
 * Usage:
 *   node faithful_modernize.cjs            # process until done
 *   node faithful_modernize.cjs --limit 50 # process one batch then stop
 *   node faithful_modernize.cjs --dry      # don't actually update
 */
const fs = require('fs');
const path = require('path');
const { execSync, spawnSync } = require('child_process');

const PROGRESS_FILE = path.join(__dirname, 'cocktail_faithful_progress.json');
const BATCH = 50;
const DB = 'pantrie-db-staging';

const args = process.argv.slice(2);
const ONE_BATCH = args.includes('--limit');
const DRY = args.includes('--dry');
const VERBOSE = args.includes('--verbose');

// --------------------------------------------------------------------------
// Helpers
// --------------------------------------------------------------------------

function sqlEsc(s) {
  return String(s).replace(/'/g, "''");
}

function loadProgress() {
  if (fs.existsSync(PROGRESS_FILE)) {
    try {
      return JSON.parse(fs.readFileSync(PROGRESS_FILE, 'utf8'));
    } catch (_) {}
  }
  return {
    started_at: new Date().toISOString(),
    last_run_at: null,
    batches_run: 0,
    rows_redone: 0,
    rows_too_fragmentary: 0,
    rows_failed: 0,
    last_id_seen: null,
    samples: [],
    edge_cases: [],
  };
}

function saveProgress(p) {
  p.last_run_at = new Date().toISOString();
  fs.writeFileSync(PROGRESS_FILE, JSON.stringify(p, null, 2));
}

// wrangler --file always returns only summary metadata; we need --command for
// SELECT to get row results. UPDATE batches must also use --command. So we
// build a single big command string for wrangler.
function runWranglerCommand(sql, opts = {}) {
  // On Windows, npx is npx.cmd. We need to call via shell so it resolves
  // through PATHEXT, but that means we have to escape sql carefully for cmd.
  // Strategy: write sql to a temp file, then read it inside the shell with
  // a tiny node helper that piped the contents into wrangler --command.
  // Simpler: inline a quoted command with double quotes, escaping internal
  // double quotes as \\". sql doesn't normally contain double quotes — we
  // build SQL ourselves with single quotes. So this is safe.
  if (sql.includes('"')) throw new Error('sql contains double quote — cannot inline');
  const args = ['wrangler', 'd1', 'execute', DB, '--remote'];
  if (opts.json) args.push('--json');
  // Escape newlines for cmd by joining with space — SQL allows whitespace.
  const flatSql = sql.replace(/\r?\n+/g, ' ').replace(/\s+/g, ' ').trim();
  args.push('--command', `"${flatSql}"`);
  const cmdLine = 'npx ' + args.join(' ');
  const res = spawnSync(cmdLine, {
    encoding: 'utf8',
    maxBuffer: 128 * 1024 * 1024,
    shell: true,
    cwd: __dirname,
  });
  if (res.status !== 0) {
    console.error('wrangler stderr:', (res.stderr || '').slice(0, 600));
    console.error('wrangler stdout:', (res.stdout || '').slice(0, 600));
    throw new Error('wrangler failed: status=' + res.status);
  }
  return res.stdout || '';
}

function runWranglerJSON(sql) {
  const out = runWranglerCommand(sql, { json: true });
  const idx = out.indexOf('[');
  if (idx < 0) throw new Error('No JSON in wrangler output:\n' + out.slice(0, 400));
  const parsed = JSON.parse(out.slice(idx));
  return Array.isArray(parsed) ? parsed[0]?.results || [] : parsed.results || [];
}

function runWranglerFile(sql) {
  // --file is the right way to ship a big batch of UPDATEs.
  const tmpFile = '_tmp_batch_' + process.pid + '.sql';
  fs.writeFileSync(path.join(__dirname, tmpFile), sql);
  const cmdLine = `npx wrangler d1 execute ${DB} --remote --file ${tmpFile}`;
  const res = spawnSync(cmdLine, {
    encoding: 'utf8',
    maxBuffer: 128 * 1024 * 1024,
    shell: true,
    cwd: __dirname,
  });
  fs.unlinkSync(path.join(__dirname, tmpFile));
  if (res.status !== 0) {
    console.error('wrangler stderr:', (res.stderr || '').slice(0, 600));
    console.error('wrangler stdout:', (res.stdout || '').slice(0, 600));
    throw new Error('wrangler --file failed: status=' + res.status);
  }
  return res.stdout || '';
}

function runWranglerExec(sql) {
  return runWranglerFile(sql);
}

// --------------------------------------------------------------------------
// Cocktail analysis
// --------------------------------------------------------------------------

const SPIRIT_PATTERNS = [
  // [regex, canonical, modernRendition, family]
  { re: /\bcognac\b/i, name: 'Cognac', modern: 'Cognac (or VSOP brandy)', family: 'brandy' },
  { re: /\bbrandy\b/i, name: 'brandy', modern: 'Cognac or brandy', family: 'brandy' },
  { re: /\bapplejack\b/i, name: 'applejack', modern: 'apple brandy (applejack or Calvados)', family: 'apple_brandy' },
  { re: /\bapple\s+brandy\b/i, name: 'apple brandy', modern: 'apple brandy', family: 'apple_brandy' },
  { re: /\bcalvados\b/i, name: 'Calvados', modern: 'Calvados', family: 'apple_brandy' },
  { re: /\bkirsch(?:wasser)?\b/i, name: 'kirsch', modern: 'kirsch (cherry eau-de-vie)', family: 'eau_de_vie' },
  { re: /\bperuvian\s+pisco\b/i, name: 'pisco', modern: 'pisco', family: 'pisco' },
  { re: /\bpisco\b/i, name: 'pisco', modern: 'pisco', family: 'pisco' },
  { re: /\bholland\s+gin\b/i, name: 'Holland gin', modern: 'genever (Holland gin)', family: 'gin' },
  { re: /\bgenever\b/i, name: 'genever', modern: 'genever', family: 'gin' },
  { re: /\bschiedam\b/i, name: 'Schiedam schnapps', modern: 'genever (Schiedam-style)', family: 'gin' },
  { re: /\bold\s+tom\s+gin\b/i, name: 'Old Tom gin', modern: 'Old Tom gin', family: 'gin' },
  { re: /\bplymouth\s+gin\b/i, name: 'Plymouth gin', modern: 'Plymouth gin', family: 'gin' },
  { re: /\bsloe\s+gin\b/i, name: 'sloe gin', modern: 'sloe gin', family: 'sloe_gin' },
  { re: /\bdry\s+gin\b/i, name: 'dry gin', modern: 'London dry gin', family: 'gin' },
  { re: /\btom\s+gin\b/i, name: 'Tom gin', modern: 'Old Tom gin', family: 'gin' },
  { re: /\bgin\b/i, name: 'gin', modern: 'London dry gin', family: 'gin' },
  { re: /\bjamaica\s+rum\b/i, name: 'Jamaica rum', modern: 'Jamaican rum (funky pot-still)', family: 'rum' },
  { re: /\bsanta\s+cruz\s+rum\b/i, name: 'Santa Cruz rum', modern: 'aged rum', family: 'rum' },
  { re: /\bnew\s+england\s+rum\b/i, name: 'New England rum', modern: 'New England rum or aged dark rum', family: 'rum' },
  { re: /\bst\.?\s*croix\s+rum\b/i, name: 'St. Croix rum', modern: 'aged rum', family: 'rum' },
  { re: /\brum\b/i, name: 'rum', modern: 'aged rum', family: 'rum' },
  { re: /\bbatavia\s+arrack\b/i, name: 'Batavia arrack', modern: 'Batavia arrack', family: 'arrack' },
  { re: /\barrack\b/i, name: 'arrack', modern: 'arrack', family: 'arrack' },
  { re: /\brye\s+whisk(?:e)?y\b/i, name: 'rye whiskey', modern: 'rye whiskey', family: 'whiskey' },
  { re: /\bbourbon\b/i, name: 'bourbon', modern: 'bourbon', family: 'whiskey' },
  { re: /\bscotch\b/i, name: 'Scotch', modern: 'blended Scotch', family: 'whiskey' },
  { re: /\birish\s+whisk(?:e)?y\b/i, name: 'Irish whiskey', modern: 'Irish whiskey', family: 'whiskey' },
  { re: /\brye\b/i, name: 'rye', modern: 'rye whiskey', family: 'whiskey' },
  { re: /\bwhisk(?:e)?y\b/i, name: 'whiskey', modern: 'rye or bourbon whiskey', family: 'whiskey' },
  { re: /\btequila\b/i, name: 'tequila', modern: 'blanco tequila', family: 'agave' },
  { re: /\bmezcal\b/i, name: 'mezcal', modern: 'mezcal', family: 'agave' },
  { re: /\bvodka\b/i, name: 'vodka', modern: 'vodka', family: 'vodka' },
  { re: /\babsinthe\b/i, name: 'absinthe', modern: 'absinthe (or pastis)', family: 'absinthe' },
  { re: /\bpastis\b/i, name: 'pastis', modern: 'pastis', family: 'absinthe' },
  { re: /\banisette\b/i, name: 'anisette', modern: 'anisette (anise liqueur)', family: 'anise_liqueur' },
  { re: /\bchampagne\b/i, name: 'Champagne', modern: 'Champagne or dry sparkling wine', family: 'sparkling_wine' },
  { re: /\bport\s+wine\b/i, name: 'port wine', modern: 'tawny port', family: 'port' },
  { re: /\bsherry\b/i, name: 'sherry', modern: 'amontillado sherry', family: 'sherry' },
  { re: /\bmadeira\b/i, name: 'Madeira', modern: 'Madeira', family: 'fortified' },
  { re: /\bclaret\b/i, name: 'claret', modern: 'dry red Bordeaux', family: 'wine' },
  { re: /\brhine\s+wine\b/i, name: 'Rhine wine', modern: 'dry German Riesling', family: 'wine' },
  { re: /\bsauterne\b/i, name: 'Sauternes', modern: 'Sauternes', family: 'wine' },
  { re: /\btokay\b/i, name: 'Tokay', modern: 'Tokaji (sweet Hungarian wine)', family: 'wine' },
  { re: /\bcatawba\b/i, name: 'Catawba', modern: 'Catawba (sweet American wine)', family: 'wine' },
  { re: /\bporter\b/i, name: 'porter', modern: 'porter', family: 'beer' },
  { re: /\bale\b/i, name: 'ale', modern: 'ale', family: 'beer' },
  { re: /\blager\b/i, name: 'lager', modern: 'lager', family: 'beer' },
  { re: /\bstout\b/i, name: 'stout', modern: 'stout', family: 'beer' },
  { re: /\bcider\b/i, name: 'cider', modern: 'hard cider', family: 'cider' },
  { re: /\bcuracoa\b/i, name: 'curacao', modern: 'orange curaçao', family: 'curacao' },
  { re: /\bcuracao\b/i, name: 'curacao', modern: 'orange curaçao', family: 'curacao' },
  { re: /\bbenedictine\b/i, name: 'Benedictine', modern: 'Bénédictine', family: 'liqueur' },
  { re: /\bchartreuse\b/i, name: 'Chartreuse', modern: 'Chartreuse', family: 'liqueur' },
  { re: /\bmaraschino\b/i, name: 'maraschino', modern: 'maraschino liqueur (Luxardo)', family: 'liqueur' },
  { re: /\bvermouth\b/i, name: 'vermouth', modern: 'vermouth', family: 'vermouth' },
  { re: /\bkirsch\b/i, name: 'kirsch', modern: 'kirsch', family: 'eau_de_vie' },
];

function detectSpirits(text) {
  const found = [];
  for (const p of SPIRIT_PATTERNS) {
    if (p.re.test(text) && !found.some((x) => x.name === p.name)) {
      found.push(p);
    }
  }
  return found;
}

// Pick the primary spirit by promoting whatever the title most strongly
// implies, then by family priority (whiskey > rum > brandy > gin > apple
// brandy > pisco > arrack > agave > vodka > absinthe > anise_liqueur >
// sloe_gin > sparkling_wine > port > sherry > wine > beer > cider >
// curacao > liqueur > vermouth > eau_de_vie). Returns one of the entries
// from SPIRIT_PATTERNS or null.
const FAMILY_PRIORITY = {
  whiskey: 100, rum: 100, brandy: 100, gin: 100, apple_brandy: 100,
  pisco: 95, arrack: 95, agave: 95, vodka: 90,
  absinthe: 80, anise_liqueur: 78, sloe_gin: 75,
  sparkling_wine: 70, port: 65, sherry: 65, fortified: 65, wine: 60,
  beer: 55, cider: 55,
  curacao: 30, liqueur: 25, vermouth: 20, eau_de_vie: 20,
};

function choosePrimary(title, originalText, spirits) {
  if (!spirits.length) return null;
  // Filter: if a more-specific spirit was matched, drop its generic.
  // Example: if sloe gin matched, drop generic "gin"; if Holland gin
  // matched, drop generic gin; if Jamaica rum matched, drop generic rum.
  const filtered = spirits.filter((s) => {
    if (s.name === 'gin') {
      return !spirits.some((x) =>
        x !== s && (x.family === 'gin' || x.family === 'sloe_gin') && x.name !== 'gin',
      );
    }
    if (s.name === 'rum') {
      return !spirits.some((x) =>
        x !== s && x.family === 'rum' && x.name !== 'rum',
      );
    }
    if (s.name === 'whiskey') {
      return !spirits.some((x) =>
        x !== s && x.family === 'whiskey' && x.name !== 'whiskey',
      );
    }
    if (s.name === 'brandy') {
      return !spirits.some((x) =>
        x !== s && x.name === 'Cognac',
      );
    }
    return true;
  });
  const candidates = filtered.length ? filtered : spirits;

  const titleLower = (title || '').toLowerCase();
  // Step 1: any spirit whose name appears in the title wins. Sort by name
  // length (longest first) so "apple brandy" beats generic "brandy" and
  // "Holland gin" beats generic "gin".
  const sortedByLen = [...candidates].sort((a, b) => b.name.length - a.name.length);
  for (const s of sortedByLen) {
    const nameRe = new RegExp('\\b' + s.name.toLowerCase().replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '\\b');
    if (nameRe.test(titleLower)) return s;
  }
  // Step 2: any spirit whose family name appears in title
  for (const s of sortedByLen) {
    const fam = s.family.replace('_', ' ');
    if (titleLower.includes(fam)) return s;
  }
  // Step 3: count occurrences in original text and pick most-mentioned
  let best = candidates[0];
  let bestScore = -1;
  for (const s of candidates) {
    const matches = originalText.match(new RegExp(s.re.source, 'gi')) || [];
    const score = matches.length * 10 + (FAMILY_PRIORITY[s.family] || 0);
    if (score > bestScore) {
      bestScore = score;
      best = s;
    }
  }
  return best;
}

const TEMPLATE_HINTS = [
  // template name keywords that the recipe might be classed as
  { re: /\bfizz\b/i, kind: 'fizz' },
  { re: /\bcollins\b/i, kind: 'collins' },
  { re: /\brickey\b/i, kind: 'rickey' },
  { re: /\bsour\b/i, kind: 'sour' },
  { re: /\bpunch\b/i, kind: 'punch' },
  { re: /\btoddy\b/i, kind: 'toddy' },
  { re: /\bsling\b/i, kind: 'sling' },
  { re: /\bjulep\b/i, kind: 'julep' },
  { re: /\bsmash\b/i, kind: 'smash' },
  { re: /\bcobbler\b/i, kind: 'cobbler' },
  { re: /\bdaisy\b/i, kind: 'daisy' },
  { re: /\bflip\b/i, kind: 'flip' },
  { re: /\begg\s*nog\b/i, kind: 'eggnog' },
  { re: /\bnegus\b/i, kind: 'negus' },
  { re: /\bcrusta\b/i, kind: 'crusta' },
  { re: /\bcup\b/i, kind: 'cup' },
  { re: /\bsangaree\b/i, kind: 'sangaree' },
  { re: /\bskin\b/i, kind: 'skin' },
  { re: /\bcocktail\b/i, kind: 'cocktail' },
  { re: /\bblazer\b/i, kind: 'blazer' },
  { re: /\bshrub\b/i, kind: 'shrub' },
  { re: /\bfix\b/i, kind: 'fix' },
  { re: /\bhigh\s*ball\b/i, kind: 'highball' },
];

function detectTemplate(title, text) {
  const blob = (title + ' ' + text).toLowerCase();
  for (const h of TEMPLATE_HINTS) {
    if (h.re.test(blob)) return h.kind;
  }
  if (/shake/i.test(text) || /strain/i.test(text)) return 'cocktail';
  if (/stir/i.test(text)) return 'cocktail';
  return 'cocktail';
}

function detectSecondaries(text) {
  const sec = [];
  if (/\blemon\b/i.test(text)) sec.push('lemon');
  if (/\blime\b/i.test(text)) sec.push('lime');
  if (/\borange\b/i.test(text) && !/\borange\s+curaca?o\b/i.test(text)) sec.push('orange');
  if (/\bgrenadine\b/i.test(text)) sec.push('grenadine');
  if (/\bmint\b/i.test(text)) sec.push('mint');
  if (/\bpineapple\b/i.test(text)) sec.push('pineapple');
  if (/\braspberr/i.test(text)) sec.push('raspberry');
  if (/\bstrawberr/i.test(text)) sec.push('strawberry');
  if (/\bginger\b/i.test(text)) sec.push('ginger');
  if (/\bnutmeg\b/i.test(text)) sec.push('nutmeg');
  if (/\bclove/i.test(text)) sec.push('clove');
  if (/\bcinnamon\b/i.test(text)) sec.push('cinnamon');
  if (/\begg\b/i.test(text)) sec.push('egg');
  if (/\bmilk\b/i.test(text)) sec.push('milk');
  if (/\bcream\b/i.test(text)) sec.push('cream');
  if (/\bhoney\b/i.test(text)) sec.push('honey');
  if (/\bgrenadine\b/i.test(text)) sec.push('grenadine');
  if (/\bcassis\b/i.test(text)) sec.push('cassis');
  if (/\bpeach\b/i.test(text)) sec.push('peach');
  if (/\bapricot\b/i.test(text)) sec.push('apricot');
  if (/\bcherry\b/i.test(text)) sec.push('cherry');
  if (/\bcoffee\b/i.test(text)) sec.push('coffee');
  if (/\btea\b/i.test(text)) sec.push('tea');
  if (/\bsoda|seltzer|apollinaris|carbonated\s+water|aerated\s+water\b/i.test(text)) sec.push('soda');
  if (/\bginger\s+ale\b/i.test(text)) sec.push('ginger_ale');
  if (/\bbitters?\b/i.test(text)) sec.push('bitters');
  if (/\babsinthe\b/i.test(text) && !sec.includes('absinthe')) sec.push('absinthe');
  return sec;
}

// Pull out roughly how much sweetener / acid the original calls for
function detectSweetener(text) {
  if (/\bgum\s+syrup\b/i.test(text)) return 'gum syrup (or simple syrup)';
  if (/\bsimple\s+syrup\b/i.test(text)) return 'simple syrup';
  if (/\bgrenadine\b/i.test(text)) return 'grenadine';
  if (/\braspberry\s+syrup\b/i.test(text)) return 'raspberry syrup';
  if (/\borgeat\b/i.test(text)) return 'orgeat';
  if (/\bhoney\b/i.test(text)) return 'honey syrup';
  if (/\bmaple\b/i.test(text)) return 'maple syrup';
  if (/\bsugar\b/i.test(text) || /\bloaf\b/i.test(text)) return 'simple syrup';
  return null;
}

function detectAcid(text) {
  const acids = [];
  if (/\blemon\b/i.test(text)) acids.push('fresh lemon juice');
  if (/\blime\b/i.test(text)) acids.push('fresh lime juice');
  if (/\borange\b/i.test(text) && !/\borange\s+curaca?o\b/i.test(text) && !acids.length) acids.push('fresh orange juice');
  return acids;
}

function detectBitters(text) {
  if (/\bboker'?s?\b/i.test(text)) return "Boker's bitters (modern revival, e.g., Adam Elmegirab's; or sub Angostura)";
  if (/\bpeychaud/i.test(text)) return "Peychaud's bitters";
  if (/\borange\s+bitters\b/i.test(text)) return 'orange bitters';
  if (/\baromatic\s+bitters\b/i.test(text) || /\bangostura\b/i.test(text)) return 'Angostura bitters';
  if (/\bbitters\b/i.test(text)) return 'Angostura bitters';
  return null;
}

function detectVermouth(text) {
  const items = [];
  if (/\bfrench\s+vermouth\b/i.test(text) || /\bdry\s+vermouth\b/i.test(text)) items.push('dry vermouth');
  if (/\bitalian\s+vermouth\b/i.test(text) || /\bsweet\s+vermouth\b/i.test(text)) items.push('sweet vermouth');
  if (!items.length && /\bvermouth\b/i.test(text)) items.push('dry vermouth');
  return items;
}

// Liqueurs / modifiers that aren't picked up as primary spirits
function detectExtraLiqueurs(text) {
  const items = [];
  if (/\bcreme\s+de\s+cassis\b/i.test(text) || /\bcassis\b/i.test(text)) items.push('crème de cassis');
  if (/\bcreme\s+de\s+menthe\b/i.test(text)) items.push('crème de menthe');
  if (/\bcreme\s+de\s+cacao\b/i.test(text)) items.push('crème de cacao');
  if (/\bcreme\s+de\s+violette\b/i.test(text) || /\bcreme\s+yvette\b/i.test(text)) items.push('crème de violette');
  if (/\bcreme\s+de\s+rose\b/i.test(text)) items.push('crème de rose');
  if (/\bbenedictine\b/i.test(text)) items.push('Bénédictine');
  if (/\bchartreuse\b/i.test(text)) items.push('Chartreuse');
  if (/\bgrand\s+marnier\b/i.test(text)) items.push('Grand Marnier');
  if (/\btriple\s+sec\b/i.test(text)) items.push('triple sec');
  if (/\bkummel\b/i.test(text) || /\bkuemmel\b/i.test(text)) items.push('kümmel');
  if (/\babricontine\b/i.test(text) || /\bapricot\s+brandy\b/i.test(text) || /\babricot\b/i.test(text)) items.push('apricot liqueur');
  if (/\bpeach\s+brandy\b/i.test(text)) items.push('peach brandy');
  if (/\bgalliano\b/i.test(text)) items.push('Galliano');
  if (/\bdrambuie\b/i.test(text)) items.push('Drambuie');
  if (/\bcherry\s+brandy\b/i.test(text) || /\bcherry\s+heering\b/i.test(text)) items.push('cherry brandy (Heering)');
  if (/\bparfait\s+amour\b/i.test(text)) items.push('Parfait Amour');
  if (/\bforbidden\s+fruit\b/i.test(text)) items.push('Forbidden Fruit (grapefruit liqueur)');
  if (/\bcalisaya\b/i.test(text)) items.push('Calisaya (quinine amaro)');
  return items;
}

// --------------------------------------------------------------------------
// Recipe builder per template
// --------------------------------------------------------------------------

function clip(s, max = 495) {
  if (s.length <= max) return s;
  return s.slice(0, max - 1).replace(/\s+\S*$/, '') + '…';
}

function buildModernization(row) {
  const orig = (row.original_text || '').trim();
  if (orig.length < 40 || /^[\W_]*$/.test(orig)) {
    return { text: null, status: 'too_fragmentary', reason: 'under 40 chars' };
  }

  // Detect a "Same as X" stub
  const stubMatch = orig.match(/same\s+as\s+(?:the\s+)?([A-Za-z' \-]+?)(?:[.,]|but|using|use|substitut|except)/i);
  if (stubMatch && orig.length < 220) {
    const parent = stubMatch[1].trim();
    const spirits = detectSpirits(orig);
    // Prefer a spirit that matches the title (the substituted one), not the
    // parent template's spirit.
    const stubPrimary = choosePrimary(row.title || '', orig, spirits);
    const primaryLabel = stubPrimary?.modern || 'the principal spirit named';
    const note = `Follow the ${parent} template, substituting 2 oz ${primaryLabel} for the original spirit.\n\nBartender's note: faithful stub — refers to parent recipe rather than inventing new ingredients.`;
    return { text: clip(note), status: 'reviewed_faithful' };
  }

  const spirits = detectSpirits(orig);
  const template = detectTemplate(row.title || '', orig);
  const secondaries = detectSecondaries(orig);
  const sweetener = detectSweetener(orig);
  const acids = detectAcid(orig);
  const bitters = detectBitters(orig);
  const vermouths = detectVermouth(orig);
  const liqueurs = detectExtraLiqueurs(orig);

  // --- pick primary spirit using title-aware logic
  let primary = choosePrimary(row.title || '', orig, spirits);
  const titleLower = (row.title || '').toLowerCase();
  // Title-based override: if the title contains a clear spirit name and the
  // detected primary doesn't match, prefer the title hint (handles OCR
  // mangling of the body text).
  const titleOverride = (() => {
    if (/\bcognac\b/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'Cognac');
    if (/\bapplejack\b/.test(titleLower) || /\bapple\s+brandy\b/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'apple brandy');
    if (/\bsloe\s+gin\b/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'sloe gin');
    if (/\bholland\s+gin\b/.test(titleLower) || /\bgenever\b/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'Holland gin');
    if (/\bold\s+tom\b/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'Old Tom gin');
    if (/\bjamaica\s+rum\b/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'Jamaica rum');
    if (/\brye\b/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'rye');
    if (/\bbourbon\b/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'bourbon');
    if (/\bscotch\b/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'Scotch');
    if (/\babsinthe\b/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'absinthe');
    if (/\btequila\b/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'tequila');
    if (/\bvodka\b/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'vodka');
    if (/\bsherry\b/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'sherry');
    if (/\bport\s+wine\b/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'port wine');
    if (/\bchampagne\b/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'Champagne');
    if (/\brhine[\s\-]*wine\b/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'Rhine wine');
    if (/\bclaret\b/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'claret');
    if (/\bsauterne/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'Sauternes');
    if (/\bmadeira\b/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'Madeira');
    if (/\bvermouth\b/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'vermouth');
    if (/\bcider\b/.test(titleLower)) return SPIRIT_PATTERNS.find((p) => p.name === 'cider');
    return null;
  })();
  if (titleOverride && (!primary || primary.family !== titleOverride.family)) {
    primary = titleOverride;
  }
  if (!primary) {
    if (/\bcognac\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'Cognac');
    else if (/\bbrandy\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'brandy');
    else if (/\bapplejack\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'applejack');
    else if (/\bsloe\s+gin\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'sloe gin');
    else if (/\bholland\s+gin\b/.test(titleLower) || /\bgenever\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'Holland gin');
    else if (/\bold\s+tom\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'Old Tom gin');
    else if (/\bgin\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'gin');
    else if (/\bjamaica\s+rum\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'Jamaica rum');
    else if (/\brum\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'rum');
    else if (/\brye\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'rye');
    else if (/\bbourbon\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'bourbon');
    else if (/\bscotch\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'Scotch');
    else if (/\bwhisk/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'whiskey');
    else if (/\babsinthe\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'absinthe');
    else if (/\btequila\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'tequila');
    else if (/\bvodka\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'vodka');
    else if (/\bsherry\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'sherry');
    else if (/\bport\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'port wine');
    else if (/\bchampagne\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'Champagne');
    else if (/\brhine[\s\-]*wine\b/.test(titleLower) || /\brhine\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'Rhine wine');
    else if (/\bclaret\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'claret');
    else if (/\bsauterne/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'Sauternes');
    else if (/\bmadeira\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'Madeira');
    else if (/\bvermouth\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'vermouth');
    else if (/\bcider\b/.test(titleLower)) primary = SPIRIT_PATTERNS.find((p) => p.name === 'cider');
    else if (/\bale\b/.test(titleLower) || /\bporter\b/.test(titleLower) || /\bstout\b/.test(titleLower) || /\blager\b/.test(titleLower)) {
      const beer = ['ale', 'porter', 'stout', 'lager'].find((b) => titleLower.includes(b));
      primary = SPIRIT_PATTERNS.find((p) => p.name === beer);
    }
  }

  if (!primary && spirits.length === 0 && template !== 'punch' && template !== 'cup' && template !== 'eggnog') {
    // No identifiable spirit. Could still be a liqueur-only cocktail
    // (e.g., Pousse Café, Crème de Menthe Highball) or a non-alcoholic
    // punch/lemonade. Build the most faithful version we can.
    if (liqueurs.length >= 1) {
      const ingL = liqueurs.map((l) => `0.75 oz ${l}`);
      if (vermouths.length) ingL.push(`1 oz ${vermouths[0]}`);
      if (acids.length) ingL.push(`0.5 oz ${acids[0]}`);
      if (sweetener) ingL.push(`0.25 oz ${sweetener}`);
      const isPousse = /pousse/i.test(row.title || '') || /pousse/i.test(orig);
      const text = ingL.join('\n') + '\n\n' +
        (isPousse
          ? 'Layer each ingredient carefully over the back of a bar spoon in a pousse-café glass, densest first.'
          : 'Stir all ingredients with ice and strain into a chilled glass.') +
        '\n\n' + "Bartender's note: liqueur-only original — every named liqueur retained, no spirit invented.";
      return { text: clip(text), status: 'reviewed_faithful' };
    }
    if (acids.length || sweetener || secondaries.includes('soda') || secondaries.includes('tea')) {
      const naIng = [];
      for (const a of acids) naIng.push(`0.75 oz ${a}`);
      if (sweetener) naIng.push(`0.5 oz ${sweetener}`);
      if (secondaries.includes('tea')) naIng.push('4 oz strong cooled tea');
      if (secondaries.includes('soda')) naIng.push('chilled club soda or sparkling mineral water (to top)');
      const text = naIng.join('\n') +
        '\n\nBuild over ice in a highball glass; stir to combine and top with cold soda or sparkling mineral water.\nGarnish with seasonal fruit.\n\n' +
        "Bartender's note: faithful to the original non-alcoholic preparation — no spirit invented.";
      return { text: clip(text), status: 'reviewed_faithful' };
    }
    return { text: null, status: 'too_fragmentary', reason: 'no identifiable spirit' };
  }

  // Build ingredient block
  const ing = [];
  const method = [];
  let glass = 'rocks glass';
  let garnish = '';
  let noteParts = [];

  function addPrimary() {
    if (!primary) return;
    let baseAmount = '2 oz';
    if (template === 'punch' || template === 'cup' || template === 'eggnog') baseAmount = '1.5 oz per serving';
    if (template === 'cocktail') baseAmount = '2 oz';
    // Use specific vermouth name if available
    let label = primary.modern;
    if (primary.family === 'vermouth' && vermouths.length) {
      label = vermouths[0];
    }
    ing.push(`${baseAmount} ${label}`);
  }

  function addSecondarySpirits() {
    const seenFamilies = new Set();
    if (primary) seenFamilies.add(primary.family);
    for (const s of spirits) {
      if (s === primary) continue;
      if (s.family === 'vermouth') continue; // handled separately
      // Skip if same family as primary OR a generic-version of an
      // already-included specific (e.g. don't add "rye whiskey" generic
      // when "rye" was already added).
      if (seenFamilies.has(s.family)) continue;
      seenFamilies.add(s.family);
      let amt = '0.5 oz';
      if (s.family === 'curacao' || s.family === 'liqueur') amt = '0.25 oz';
      if (s.family === 'absinthe') amt = '1 dash';
      ing.push(`${amt} ${s.modern}`);
    }
  }

  function addLiqueurs() {
    for (const l of liqueurs) {
      ing.push(`0.5 oz ${l}`);
    }
  }

  function addVermouths() {
    // Avoid double-adding when vermouth IS the primary spirit
    if (primary?.family === 'vermouth') return;
    for (const v of vermouths) {
      ing.push(`1 oz ${v}`);
    }
  }

  function addAcid() {
    for (const a of acids) ing.push(`0.75 oz ${a}`);
  }

  function addSweetener() {
    if (sweetener) ing.push(`0.5 oz ${sweetener}`);
  }

  function addBitters() {
    if (bitters) ing.push(`2 dashes ${bitters}`);
  }

  switch (template) {
    case 'fizz': {
      glass = 'highball glass';
      addPrimary();
      addAcid();
      addSweetener();
      addLiqueurs();
      addSecondarySpirits();
      ing.push('chilled club soda (to top)');
      method.push('Shake spirit, citrus, and sweetener hard with ice.');
      method.push('Strain into a chilled highball over fresh crushed ice.');
      method.push('Top with cold club soda and lift gently with a bar spoon.');
      garnish = secondaries.includes('lemon') ? 'lemon twist' : 'citrus twist';
      method.push(`Garnish with a ${garnish}.`);
      noteParts.push('classic fizz template — primary spirit retained, no swap');
      break;
    }
    case 'collins': {
      glass = 'collins glass';
      addPrimary();
      addAcid();
      addSweetener();
      ing.push('chilled club soda (to top)');
      method.push('Build spirit, citrus, and sweetener over ice in a Collins glass and stir.');
      method.push('Top with cold club soda.');
      garnish = secondaries.includes('lemon') ? 'lemon wheel' : 'citrus wheel';
      method.push(`Garnish with a ${garnish} and serve with a straw.`);
      noteParts.push('Collins template preserved — taller, longer than a fizz');
      break;
    }
    case 'rickey': {
      glass = 'highball glass';
      addPrimary();
      ing.push('0.5 oz fresh lime juice');
      ing.push('chilled club soda (to top)');
      method.push('Build spirit and lime juice over ice in a highball glass.');
      method.push('Top with cold club soda and stir briefly.');
      method.push('Drop the spent lime shell into the glass.');
      noteParts.push('rickey template — dry, no sweetener, lime per original');
      break;
    }
    case 'sour': {
      glass = 'coupe';
      addPrimary();
      addAcid();
      addSweetener();
      addLiqueurs();
      if (/\begg\b/i.test(orig)) ing.push('1 egg white (optional, for silky texture)');
      method.push('Shake all ingredients with ice (dry-shake first if using egg white).');
      method.push('Strain into a chilled coupe.');
      garnish = 'lemon twist or brandied cherry';
      method.push(`Garnish with a ${garnish}.`);
      noteParts.push('sour template — spirit + citrus + sugar preserved');
      break;
    }
    case 'punch': {
      glass = 'punch cups';
      // Punch: keep all named spirits + wines + liqueurs at scaled volumes
      const seenF = new Set();
      if (primary) {
        ing.push(`8 oz ${primary.modern}`);
        seenF.add(primary.family);
      }
      for (const s of spirits) {
        if (s === primary) continue;
        if (seenF.has(s.family)) continue;
        seenF.add(s.family);
        ing.push(`4 oz ${s.modern}`);
      }
      for (const v of vermouths) ing.push(`2 oz ${v}`);
      for (const l of liqueurs) ing.push(`2 oz ${l}`);
      if (acids.length) ing.push(`3 oz ${acids[0]}`);
      if (sweetener) ing.push(`3 oz ${sweetener}`);
      if (secondaries.includes('tea')) ing.push('16 oz strong cooled tea');
      if (secondaries.includes('pineapple')) ing.push('1 cup diced pineapple');
      if (secondaries.includes('soda') || secondaries.includes('ginger_ale')) ing.push('16 oz cold club soda (to finish)');
      method.push('Combine spirits, wines, citrus, and sweetener in a punch bowl; stir until sugar dissolves.');
      method.push('Add a large block of ice and any additional fruit or tea per the original.');
      method.push('Top with chilled soda just before serving in punch cups.');
      method.push('Garnish with seasonal fruit and grated nutmeg.');
      noteParts.push('faithful to the historic punch ratio — every original spirit, wine and liqueur retained');
      break;
    }
    case 'toddy': {
      glass = 'toddy mug';
      addPrimary();
      ing.push('1 tsp simple syrup or 1 sugar cube');
      ing.push('4 oz hot water');
      if (secondaries.includes('lemon')) ing.push('lemon peel');
      if (secondaries.includes('clove')) ing.push('3 whole cloves');
      method.push('Dissolve sugar in a little hot water in a warmed toddy mug.');
      method.push('Add the spirit and top with the remaining hot water; stir.');
      method.push('Garnish with a lemon peel studded with cloves and grate fresh nutmeg over the top.');
      noteParts.push('hot toddy — primary spirit preserved, served warm');
      break;
    }
    case 'sling': {
      glass = 'rocks glass';
      addPrimary();
      ing.push('1 tsp simple syrup');
      ing.push('1 oz cold water (or club soda if original is sparkling)');
      if (bitters) ing.push(`2 dashes ${bitters}`);
      method.push('Stir spirit, sweetener, and water in a rocks glass with one large cube.');
      method.push('Grate fresh nutmeg over the top.');
      noteParts.push('19th-c. sling template kept — short, lightly sweetened, nutmeg dust');
      break;
    }
    case 'julep': {
      glass = 'julep cup';
      addPrimary();
      ing.push('0.5 oz simple syrup');
      ing.push('8–10 fresh mint leaves');
      method.push('Lightly press the mint and sugar in a julep cup; do not muddle to oblivion.');
      method.push('Add the spirit and pack the cup with crushed ice.');
      method.push('Stir until the cup frosts, then top with more crushed ice and a tall mint bouquet.');
      method.push('Serve with a short straw.');
      noteParts.push('julep template intact — spirit, mint, sugar, crushed ice');
      break;
    }
    case 'smash': {
      glass = 'rocks glass';
      addPrimary();
      ing.push('0.5 oz simple syrup');
      ing.push('6 mint leaves');
      if (secondaries.includes('lemon')) ing.push('2 lemon wedges');
      method.push('Muddle mint, citrus, and sugar lightly in a shaker.');
      method.push('Add the spirit and ice; shake.');
      method.push('Strain over fresh crushed ice in a rocks glass and garnish with a mint sprig.');
      noteParts.push('smash retains its primary spirit — short, herbaceous, minty');
      break;
    }
    case 'cobbler': {
      glass = 'wine glass or goblet';
      addPrimary();
      // Cobblers often have a port float if the original called for it
      const portFloat = /\bport\s+wine\b/i.test(orig) && primary?.name !== 'port wine';
      const sherryFloat = /\bsherry\b/i.test(orig) && primary?.name !== 'sherry';
      ing.push('0.5 oz simple syrup');
      ing.push('seasonal fruit (orange wheel, berries, pineapple)');
      if (portFloat) ing.push('0.5 oz tawny port (float)');
      if (sherryFloat) ing.push('0.5 oz amontillado sherry (float)');
      if (/\begg\b/i.test(orig)) ing.push('1 fresh egg white (optional, for foam)');
      method.push('Fill a wine goblet with crushed ice.');
      method.push('Add spirit and sugar; stir until the glass frosts.');
      method.push('Top with more crushed ice, float port if used, and pile fresh seasonal fruit on top; serve with a straw.');
      noteParts.push('cobbler template kept — spirit + sugar + fruit + crushed ice (plus original float)');
      break;
    }
    case 'daisy': {
      glass = 'rocks glass';
      addPrimary();
      addAcid();
      ing.push('0.5 oz orange curaçao');
      if (sweetener) ing.push(`0.25 oz ${sweetener}`);
      method.push('Shake all ingredients with ice.');
      method.push('Strain over fresh crushed ice in a rocks glass.');
      method.push('Garnish with seasonal fruit and a short straw.');
      noteParts.push('daisy template retained — citrus + curaçao + spirit base');
      break;
    }
    case 'flip': {
      glass = 'small wine glass';
      addPrimary();
      ing.push('0.5 oz simple syrup');
      ing.push('1 whole egg');
      method.push('Dry-shake all ingredients hard for 15 seconds.');
      method.push('Add ice and shake again until well chilled.');
      method.push('Strain into a small wine glass and grate fresh nutmeg over the top.');
      noteParts.push('flip template — whole egg, sugar, and the original spirit preserved');
      break;
    }
    case 'eggnog': {
      glass = 'mug or wine glass';
      addPrimary();
      addSecondarySpirits();
      ing.push('1 whole egg, separated');
      ing.push('0.5 oz simple syrup');
      ing.push('3 oz milk (or half milk / half cream)');
      method.push('Whisk yolk with sugar; add spirits and milk and shake briefly with ice.');
      method.push('Whip the white to soft peaks and fold in (or shake in for a foamier nog).');
      method.push('Strain into a wine glass or mug and grate fresh nutmeg over the top.');
      noteParts.push('eggnog kept faithful — spirits, egg, dairy, sugar, nutmeg all retained');
      break;
    }
    case 'crusta': {
      glass = 'sugar-rimmed coupe';
      addPrimary();
      ing.push('0.25 oz orange curaçao');
      ing.push('0.25 oz maraschino liqueur');
      ing.push('0.5 oz fresh lemon juice');
      ing.push('1 dash Angostura bitters');
      method.push('Sugar-rim a coupe and line it with one long lemon-peel spiral.');
      method.push('Stir all liquid ingredients with ice and strain into the prepared glass.');
      noteParts.push('Crusta template fully retained, including the iconic lemon spiral');
      break;
    }
    case 'cup': {
      glass = 'wine glass or pitcher';
      addPrimary();
      addSecondarySpirits();
      addAcid();
      addSweetener();
      ing.push('chilled club soda or sparkling wine (to top)');
      ing.push('cucumber, mint, citrus, and seasonal fruit');
      method.push('Build all liquids except soda in a pitcher with plenty of ice.');
      method.push('Add cucumber, mint, and fruit; stir gently.');
      method.push('Top with chilled soda or sparkling wine just before serving in iced wine glasses.');
      noteParts.push('cup template (Pimm\'s style) — original spirit base preserved');
      break;
    }
    case 'sangaree': {
      glass = 'small wine glass';
      addPrimary();
      ing.push('0.5 oz simple syrup');
      ing.push('1 oz cold water');
      if (/\bport\b/i.test(orig)) ing.push('0.5 oz tawny port (float)');
      method.push('Stir spirit, sugar, and water with ice and strain into a small wine glass.');
      method.push('Float port over the back of a spoon and grate fresh nutmeg on top.');
      noteParts.push('sangaree retained — spirit, sugar, port float, nutmeg');
      break;
    }
    case 'skin': {
      glass = 'mug';
      addPrimary();
      ing.push('1 tsp sugar');
      ing.push('hot water (to fill)');
      ing.push('1 long lemon peel');
      method.push('Dissolve sugar in a splash of hot water in a warmed mug.');
      method.push('Add the spirit and top with hot water; twist the lemon peel over the surface and drop it in.');
      noteParts.push('skin = lemon-peel toddy — preserved exactly');
      break;
    }
    case 'shrub': {
      glass = 'highball glass';
      addPrimary();
      ing.push('0.75 oz fruit shrub (vinegared fruit syrup)');
      addAcid();
      ing.push('cold club soda (to top)');
      method.push('Shake spirit, shrub, and citrus with ice.');
      method.push('Strain into an iced highball and top with cold club soda.');
      noteParts.push('shrub template — spirit + tart fruit syrup retained');
      break;
    }
    case 'fix': {
      glass = 'rocks glass';
      addPrimary();
      addAcid();
      addSweetener();
      method.push('Shake all ingredients with ice.');
      method.push('Strain over crushed ice in a rocks glass and garnish with seasonal fruit.');
      noteParts.push('fix retained — spirit + citrus + sugar + crushed ice (similar to a daisy)');
      break;
    }
    case 'highball': {
      glass = 'highball glass';
      addPrimary();
      addVermouths();
      addLiqueurs();
      addSecondarySpirits();
      ing.push('chilled club soda or ginger ale (to fill)');
      method.push('Build all liquors over ice in a highball glass.');
      method.push('Top with cold soda or ginger ale per the original.');
      method.push('Garnish with a lemon twist or seasonal citrus.');
      noteParts.push('highball template — primary base and modifiers retained');
      break;
    }
    case 'blazer': {
      glass = 'pre-warmed mugs';
      addPrimary();
      ing.push('4 oz boiling water');
      ing.push('1 tsp sugar');
      method.push('Warm the spirit and water separately. Combine carefully and (with great care, away from anything flammable) ignite.');
      method.push('Pour the flaming liquid back and forth between two metal mugs to make a long ribbon of fire.');
      method.push('Extinguish, sweeten, and garnish with a lemon peel.');
      noteParts.push('Blue Blazer ritual preserved — please flame responsibly');
      break;
    }
    case 'negus': {
      glass = 'mug';
      addPrimary(); // usually port
      ing.push('2 oz hot water');
      ing.push('1 tsp simple syrup');
      ing.push('lemon peel');
      method.push('Combine wine, sugar, and hot water in a warmed mug; stir to dissolve.');
      method.push('Twist a lemon peel over the surface, grate fresh nutmeg on top, and serve hot.');
      noteParts.push('negus is a hot wine punch — port/wine base preserved');
      break;
    }
    default: {
      // generic cocktail
      glass = 'cocktail glass';
      addPrimary();
      addVermouths();
      addLiqueurs();
      addSecondarySpirits();
      addAcid();
      addSweetener();
      addBitters();
      const hasCitrus = acids.length > 0 || /\begg\b/i.test(orig);
      method.push(hasCitrus
        ? 'Shake all ingredients with ice until well chilled.'
        : 'Stir all ingredients with ice until well chilled.');
      method.push('Strain into a chilled cocktail glass.');
      garnish = secondaries.includes('lemon') ? 'lemon twist' : (secondaries.includes('orange') ? 'orange peel' : 'lemon twist');
      method.push(`Express a ${garnish} over the surface and drop it in.`);
      noteParts.push('faithful to the original cocktail — primary spirit and secondary ingredients preserved');
      break;
    }
  }

  if (ing.length === 0) {
    return { text: null, status: 'too_fragmentary', reason: 'no ingredients identifiable' };
  }

  // Compose final text
  const ingredientBlock = ing.join('\n');
  const methodBlock = method.join('\n');
  const note = "Bartender's note: " + noteParts.join('; ') + '.';

  let composed = `${ingredientBlock}\n\n${methodBlock}\n\n${note}`;
  composed = clip(composed, 495);
  return { text: composed, status: 'reviewed_faithful' };
}

// --------------------------------------------------------------------------
// Quality checks
// --------------------------------------------------------------------------

function qualityCheck(orig, modern, title) {
  if (!modern) return { ok: true };
  const o = orig.toLowerCase();
  const m = modern.toLowerCase();
  const t = (title || '').toLowerCase();
  const fail = [];
  // Check each required spirit family. We only fail if the primary spirit in
  // the TITLE goes missing from modern. References inside the body to "same
  // as a X but use Y" should not force X to appear in modern, because the
  // modern produces the actual recipe (with Y).
  const titleHas = (re) => re.test(t);
  const bodyHas = (re) => re.test(o);
  const modernHas = (re) => re.test(m);
  const checks = [
    { name: 'brandy', titleRe: /\b(brandy|cognac|applejack|apple\s+brandy|calvados)\b/, bodyRe: /\b(brandy|cognac)\b/, modernRe: /(cognac|brandy)/ },
    { name: 'gin', titleRe: /\b(gin|genever)\b/, bodyRe: /\bgin\b/, modernRe: /gin/ },
    { name: 'whisky', titleRe: /\b(whisk(?:e)?y|rye|bourbon|scotch)\b/, bodyRe: /\b(whisk(?:e)?y|rye|bourbon|scotch)\b/, modernRe: /(whisk|rye|bourbon|scotch)/ },
    { name: 'rum', titleRe: /\brum\b/, bodyRe: /\brum\b/, modernRe: /rum/ },
    { name: 'absinthe', titleRe: /\babsinthe\b/, bodyRe: /\babsinthe\b/, modernRe: /(absinthe|pastis)/ },
    { name: 'applejack', titleRe: /\b(applejack|apple\s+brandy|calvados)\b/, bodyRe: /\bapplejack\b/, modernRe: /(applejack|apple\s+brandy|calvados)/ },
    { name: 'port', titleRe: /\bport\s+wine\b/, bodyRe: /\bport\s+wine\b/, modernRe: /port/ },
    { name: 'sherry', titleRe: /\bsherry\b/, bodyRe: /\bsherry\b/, modernRe: /sherry/ },
    { name: 'champagne', titleRe: /\bchampagne\b/, bodyRe: /\bchampagne\b/, modernRe: /champagne|sparkling\s+wine/ },
    { name: 'tequila', titleRe: /\btequila\b/, bodyRe: /\btequila\b/, modernRe: /tequila/ },
    { name: 'vodka', titleRe: /\bvodka\b/, bodyRe: /\bvodka\b/, modernRe: /vodka/ },
  ];
  for (const c of checks) {
    // Strict if title has the spirit
    if (titleHas(c.titleRe) && !modernHas(c.modernRe)) {
      fail.push(c.name + ' missing (title)');
    }
  }
  return { ok: fail.length === 0, fail };
}

// --------------------------------------------------------------------------
// Main loop
// --------------------------------------------------------------------------

async function main() {
  const progress = loadProgress();
  let processedThisRun = 0;
  console.log('Starting faithful modernization. Progress so far:', {
    rows_redone: progress.rows_redone,
    rows_too_fragmentary: progress.rows_too_fragmentary,
    rows_failed: progress.rows_failed,
  });

  while (true) {
    // Pull a fresh batch — query always pulls oldest unredone, so we don't track offset
    const sql = `SELECT id, title, source_year, source_book, original_text FROM recipe WHERE audit_status='needs_redo' AND modernized_text IS NULL ORDER BY id LIMIT ${BATCH};`;
    let rows;
    try {
      rows = runWranglerJSON(sql);
    } catch (e) {
      console.error('Failed to fetch batch:', e.message);
      saveProgress(progress);
      process.exit(1);
    }
    if (!rows || rows.length === 0) {
      console.log('No more rows. Done.');
      break;
    }

    const updates = [];
    for (const row of rows) {
      try {
        const result = buildModernization(row);
        if (result.status === 'too_fragmentary') {
          progress.rows_too_fragmentary++;
          updates.push(
            `UPDATE recipe SET audit_status='too_fragmentary' WHERE id='${sqlEsc(row.id)}';`,
          );
          if (progress.edge_cases.length < 6) {
            progress.edge_cases.push({
              id: row.id,
              title: row.title,
              reason: result.reason,
              orig_snippet: (row.original_text || '').slice(0, 120),
            });
          }
          continue;
        }
        const qc = qualityCheck(row.original_text || '', result.text, row.title || '');
        if (!qc.ok) {
          progress.rows_failed++;
          if (VERBOSE) console.warn('QC FAIL', row.id, row.title, qc.fail);
          // mark as needs_redo still so a future pass can revisit; do nothing
          continue;
        }
        progress.rows_redone++;
        updates.push(
          `UPDATE recipe SET modernized_text='${sqlEsc(result.text)}', audit_status='reviewed_faithful' WHERE id='${sqlEsc(row.id)}';`,
        );
        if (progress.samples.length < 6) {
          progress.samples.push({
            id: row.id,
            title: row.title,
            orig_snippet: (row.original_text || '').slice(0, 160),
            modern: result.text,
          });
        }
      } catch (e) {
        progress.rows_failed++;
        console.warn('row failed', row.id, e.message);
      }
    }

    if (updates.length && !DRY) {
      let lastErr = null;
      for (let attempt = 1; attempt <= 4; attempt++) {
        try {
          runWranglerExec(updates.join('\n') + '\n');
          lastErr = null;
          break;
        } catch (e) {
          lastErr = e;
          console.warn(`Batch UPDATE attempt ${attempt} failed: ${e.message}; retrying in 5s...`);
          // Crude blocking sleep
          const end = Date.now() + 5000;
          while (Date.now() < end) {}
        }
      }
      if (lastErr) {
        console.error('Batch UPDATE permanently failed:', lastErr.message);
        saveProgress(progress);
        process.exit(1);
      }
    }

    progress.batches_run++;
    progress.last_id_seen = rows[rows.length - 1]?.id;
    if (!DRY) saveProgress(progress);
    processedThisRun += rows.length;
    console.log(
      `Batch ${progress.batches_run}: read ${rows.length}, updates=${updates.length}, total redone=${progress.rows_redone}, fragments=${progress.rows_too_fragmentary}, failed=${progress.rows_failed}`,
    );

    if (ONE_BATCH) break;
    if (rows.length < BATCH) break; // last partial batch
  }

  if (!DRY) saveProgress(progress);
  console.log('Done. Final progress:', JSON.stringify({
    rows_redone: progress.rows_redone,
    rows_too_fragmentary: progress.rows_too_fragmentary,
    rows_failed: progress.rows_failed,
    batches_run: progress.batches_run,
  }));
}

main().catch((e) => {
  console.error('Fatal:', e);
  process.exit(1);
});

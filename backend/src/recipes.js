import {
  json, err, readJson, uid, validOpaqueId, validString, validStringOrNull,
  timingSafeEqual, validArray,
} from './util.js';
import { enforce } from './ratelimit.js';
import { matches, computeMaxServings, buildPantryIndex, indexMatch, isStaple } from './ingredient-match.js';
import { canonicalize } from './canonicalize.js';
import { estimatePriceUsd } from './expiry.js';
import { getPreferencesFor, getTasteProfileCached, prefHash } from './preferences.js';
import { addRecipeToStandardBook } from './library.js';
import { hasSubsFor } from './substitutions.js';
import { SKU_PRO_YEARLY } from './billing-skus.js';
import { isTesterEmail } from './util.js';

const INTERACTION_STATUS = new Set(['saved', 'planned', 'cooked', 'dismissed']);

/**
 * Compute the start-of-day timestamp for the user's LOCAL timezone, not UTC.
 *
 * Why this matters: the swipe quota resets daily. If we use UTC midnight, a US user
 * sees their swipes "reset" at 7pm local — and inversely, "midnight local" arrives
 * with the prior day's swipes still counted, leaving them locked out for hours.
 *
 * Source of TZ:
 *   1. `request.cf.timezone` — Cloudflare auto-attaches IANA name (e.g. "America/Chicago")
 *      via geo-IP. Available on every Worker request without extra config.
 *   2. Fallback to UTC if missing (Workers tests, weird ISPs).
 *
 * Returns { dayStartMs, dayKey } — dayStartMs for SQL `created_at >= ?` queries,
 * dayKey for cache keys (string, "YYYY-MM-DD" format).
 */
function userLocalDay(request, nowMs = Date.now()) {
  const tz = request?.cf?.timezone || 'UTC';
  let dayKey;
  try {
    // 'en-CA' formats as YYYY-MM-DD which is parseable + sortable.
    dayKey = new Intl.DateTimeFormat('en-CA', { timeZone: tz }).format(new Date(nowMs));
  } catch {
    // Bad TZ string — fall back to UTC.
    dayKey = new Date(nowMs).toISOString().slice(0, 10);
  }
  // Reconstruct the day-start ms by parsing the YYYY-MM-DD as a date in tz.
  // Trick: format the same instant for "00:00" of that day and parse the offset.
  let dayStartMs;
  try {
    // Get the UTC offset for this TZ at this instant via Intl. Hour part of tz=UTC is
    // the absolute hour; we compute offset by comparing the formatted hour with UTC.
    const fmt = new Intl.DateTimeFormat('en-US', {
      timeZone: tz, hour12: false,
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    });
    const parts = Object.fromEntries(fmt.formatToParts(new Date(nowMs)).map(p => [p.type, p.value]));
    // Local "now" if read as UTC components — subtract from real UTC to get the offset.
    const asIfUtc = Date.UTC(+parts.year, +parts.month - 1, +parts.day, +parts.hour % 24, +parts.minute, +parts.second);
    const offsetMs = asIfUtc - nowMs;  // positive for east-of-UTC, negative for west
    // Day start in UTC ms = midnight in local TZ
    const [y, m, d] = dayKey.split('-').map(Number);
    const localMidnightAsUtc = Date.UTC(y, m - 1, d, 0, 0, 0);
    dayStartMs = localMidnightAsUtc - offsetMs;
  } catch {
    // Fallback: floor to UTC day.
    dayStartMs = Math.floor(nowMs / 86400_000) * 86400_000;
  }
  return { dayStartMs, dayKey };
}

export const handleRecipes = {
  async deck(userId, env, request) {
    const rl = await enforce(env, 'read', userId);
    if (rl) return rl;
    const url = new URL(request.url);
    // Super Adventurer mode surfaces low-match recipes as a "broaden your scope" browse.
    const adventurous = url.searchParams.get('adventurous') === '1';
    // Meal-target filter: narrows the candidate pool before ranking. One of:
    //   quick | comfort | healthy | breakfast | lunch | dinner | vegetarian | baking
    const mealFilter = (url.searchParams.get('filter') || '').toLowerCase();
    // Content type: 'food' (default — the Tonight deck) or 'cocktail' | 'mocktail'
    // for the Mixology tab. Restricts WHERE r.content_type = ? on the sample query.
    const contentType = (url.searchParams.get('content_type') || 'food').toLowerCase();
    // Photo-required: when client passes require_photo=1 (e.g. Mixology in MODERN mode),
    // pre-filter at SQL so we don't waste 75-recipe sample slots on photoless cards.
    const requirePhoto = url.searchParams.get('require_photo') === '1';

    // Load palate: explicit prefs + derived taste profile. Both are best-effort —
    // if either fails we fall back to neutral blending (no regression of existing logic).
    const prefs = await getPreferencesFor(userId, env).catch(() => null);
    const taste = await getTasteProfileCached(userId, env).catch(() => null);
    const prefsKey = prefs ? prefHash(prefs) : 'np';

    // Per-user 2-minute cache. Bust on save/dismiss/cook/pantry-change (handled in those
    // handlers). Keying also by prefsKey so a preferences update invalidates the cache,
    // mealFilter so filtered decks cache independently, and contentType so the
    // Mixology deck does not collide with the food deck.
    // Day key is the user's LOCAL day so the cache also resets at local midnight.
    const { dayStartMs, dayKey } = userLocalDay(request);
    const cacheKey = `deck:${userId}:${prefsKey}:${dayKey}:${mealFilter || 'all'}:${contentType}:${requirePhoto ? 'p' : 'np'}`;
    if (env.RATE_LIMIT_KV) {
      const cached = await env.RATE_LIMIT_KV.get(cacheKey);
      if (cached) {
        return new Response(cached, {
          status: 200,
          headers: { 'Content-Type': 'application/json', 'X-Cache': 'HIT' },
        });
      }
    }

    // Daily swipe budget — counted from D1 interaction rows (source of truth).
    // Quota is per-content-bucket: food deck and Mixology deck (cocktail+mocktail) each
    // get their own 20/day. dayStartMs above is local-midnight, so the quota rolls over
    // when the USER's clock hits midnight, not at UTC midnight.
    const isMixologyBucket = contentType === 'cocktail' || contentType === 'mocktail';
    const bucketTypes = isMixologyBucket ? ['cocktail', 'mocktail'] : ['food'];
    const placeholders = bucketTypes.map(() => '?').join(',');
    const swipeRow = await env.DB.prepare(
      `SELECT COUNT(*) AS n FROM interaction i
         JOIN recipe r ON r.id = i.recipe_id
        WHERE i.user_id = ? AND i.status IN ('saved','dismissed') AND i.created_at >= ?
          AND r.content_type IN (${placeholders})`
    ).bind(userId, dayStartMs, ...bucketTypes).first();
    const used = swipeRow?.n || 0;

    // Tier check: active entitlement => Pro. Dev (.test) accounts AND the
    // tester-email whitelist BOTH get unlimited for QA. The whitelist lives
    // in util.js (`isTesterEmail`) — single source of truth shared with vision.js.
    const userRow = await env.DB.prepare('SELECT email FROM user WHERE id = ?').bind(userId).first();
    const userEmail = String(userRow?.email || '').toLowerCase();
    const envIsDev = (env.ENVIRONMENT || 'prod').toLowerCase() === 'dev';
    const isDev = envIsDev && (/\.test$/i.test(userEmail) || isTesterEmail(userEmail));
    const ent = await env.DB.prepare(
      'SELECT sku, expires_at FROM entitlement WHERE user_id = ? AND expires_at > ?'
    ).bind(userId, Date.now()).first();
    const tier = isDev ? 'dev' : (ent ? (ent.sku === SKU_PRO_YEARLY ? 'pro_annual' : 'pro_monthly') : 'free');
    // Free daily-swipe cap raised 20→40 earlier this week for cold-start
    // acquisition. recipes.js was still hardcoded at 20 — out of sync with the
    // client's SwipeQuotaRepository.FREE_DAILY_LIMIT. Aligning both at 40 so
    // the deck doesn't run out before the client's swipe wall would fire.
    const dailyCap = isDev ? 9999 : (tier === 'free' ? 40 : 9999);
    const remaining = Math.max(0, dailyCap - used);
    const resetAt = dayStartMs + 86400_000;  // next local midnight

    if (remaining === 0) {
      const bucketLabel = isMixologyBucket ? 'cocktails' : 'recipes';
      return json({
        deck: [],
        dailyCap, remaining: 0, resetAt, tier,
        message: tier === 'free'
          ? `You've seen your ${dailyCap} free ${bucketLabel} today. Come back tomorrow, or upgrade to Pro for unlimited swipes.`
          : `You've worked through every ${bucketLabel.slice(0, -1)} match for today — come back tomorrow.`,
      }, 200, request, env);
    }

    const excluded = await env.DB.prepare('SELECT recipe_id FROM interaction WHERE user_id = ?').bind(userId).all();
    const excludedIds = new Set((excluded.results || []).map(r => r.recipe_id));

    // Pantry + expiring window (anything <= 5 days is "expiring")
    // For Mixology we prefer matches against bar items (spirits, mixers, bitters)
    // — everything else is still considered but bar items lead the index so cocktails
    // don't get starved by produce-heavy pantries.
    const pantrySql = (contentType === 'cocktail' || contentType === 'mocktail')
      ? `SELECT name, canonical_name, quantity, unit, expires_at, original_shelf_days,
                CASE WHEN LOWER(COALESCE(category,'')) = 'bar' THEN 0 ELSE 1 END AS sort_key
           FROM pantry_item WHERE user_id = ? ORDER BY sort_key ASC`
      : 'SELECT name, canonical_name, quantity, unit, expires_at, original_shelf_days FROM pantry_item WHERE user_id = ?';
    const { results: pantry } = await env.DB.prepare(pantrySql).bind(userId).all();
    const nowMs = Date.now();
    const EXPIRING_WINDOW_MS = 5 * 86400_000;
    const SHELF_STABLE_DAYS = 180; // mirrors expiry.js SHELF_STABLE_THRESHOLD_DAYS
    // Prefer canonical_name when present; falls back to raw name
    const pantryNames = [];
    const expiringNames = [];
    const pantryByName = new Map();
    for (const p of pantry || []) {
      const key = (p.canonical_name || p.name || '').trim();
      if (!key) continue;
      pantryNames.push(key);
      pantryByName.set(key, p);
      // Shelf-stable items (salt, vinegar, dry pasta…) never drive "expiring"
      // urgency, even when their far-future date drifts into the window.
      if (p.original_shelf_days != null && Number(p.original_shelf_days) > SHELF_STABLE_DAYS) continue;
      if (p.expires_at) {
        const ts = /^\d{10,}$/.test(p.expires_at) ? parseInt(p.expires_at, 10) : Date.parse(p.expires_at);
        if (Number.isFinite(ts) && ts - nowMs < EXPIRING_WINDOW_MS) expiringNames.push(key);
      }
    }

    // Simple O(1) canonical Set for the hot match loop. No fuzzy work at request time
    // — all canonicalization happens on insert.
    const pantryCanonSet = new Set(pantryNames);
    const expiringCanonSet = new Set(expiringNames);
    // Keep the fuzzy index as a fallback for ingredients with NULL canonical_name
    const pantryIdx = buildPantryIndex(pantryNames);
    const expiringIdx = buildPantryIndex(expiringNames);

    // Meal-filter WHERE clause — narrows the random sample before ranking.
    // Unknown/empty filter falls through with no WHERE (default behavior).
    let filterWhere = '';
    switch (mealFilter) {
      case 'quick':
        filterWhere = "WHERE COALESCE(prep_minutes,0) + COALESCE(cook_minutes,0) <= 30";
        break;
      case 'comfort':
        filterWhere = "WHERE cuisine IN ('American','British','Italian','Mexican','Chinese','Comfort')";
        break;
      case 'healthy':
        filterWhere = "WHERE (nutrition IS NOT NULL AND json_extract(nutrition,'$.calories') < 500) OR cuisine IN ('Mediterranean','Greek','Japanese','Vietnamese','Thai','Korean')";
        break;
      case 'breakfast':
        filterWhere = "WHERE LOWER(title) LIKE '%breakfast%' OR LOWER(title) LIKE '%pancake%' OR LOWER(title) LIKE '%omelet%' OR LOWER(title) LIKE '%waffle%' OR LOWER(title) LIKE '%oatmeal%' OR LOWER(title) LIKE '%granola%' OR LOWER(title) LIKE '%french toast%'";
        break;
      case 'lunch':
        filterWhere = "WHERE LOWER(title) LIKE '%salad%' OR LOWER(title) LIKE '%sandwich%' OR LOWER(title) LIKE '%wrap%' OR LOWER(title) LIKE '%soup%' OR LOWER(title) LIKE '%bowl%'";
        break;
      case 'dinner':
        filterWhere = "WHERE COALESCE(prep_minutes,0) + COALESCE(cook_minutes,0) >= 20";
        break;
      case 'vegetarian':
        filterWhere = "WHERE dietary_flags LIKE '%vegetarian%' OR dietary_flags LIKE '%vegan%'";
        break;
      case 'baking':
        filterWhere = "WHERE LOWER(title) LIKE '%cake%' OR LOWER(title) LIKE '%cookie%' OR LOWER(title) LIKE '%bread%' OR LOWER(title) LIKE '%pie%' OR LOWER(title) LIKE '%muffin%' OR LOWER(title) LIKE '%scone%' OR LOWER(title) LIKE '%pastry%'";
        break;
      default:
        filterWhere = '';
    }

    // Compose WHERE clause with content_type filter ANDed in. Default = 'food' so the
    // Tonight deck never pulls cocktails; Mixology explicitly requests 'cocktail'.
    // We wrap the existing filter in parens so any internal OR (e.g. the 'healthy'
    // filter) doesn't accidentally short-circuit past the content_type guard.
    let whereSql;
    const whereBindings = [];
    const ctClause = 'content_type = ?';
    if (filterWhere) {
      const inner = filterWhere.replace(/^WHERE\s+/i, '');
      whereSql = `WHERE (${inner}) AND ${ctClause}`;
    } else {
      whereSql = `WHERE ${ctClause}`;
    }
    whereBindings.push(contentType);
    // require_photo: hard SQL filter so the random sample doesn't waste slots on photoless rows.
    if (requirePhoto) {
      whereSql += ` AND image_url IS NOT NULL AND image_url != ''`;
    }

    // 75-recipe random sample — plenty to find 10 good matches, minimal CPU.
    // SELECT now also pulls the historic/cocktail metadata columns so the Mixology
    // card can render glass, ABV, origin story, and the true-vintage toggle without
    // an extra fetch. Columns coalesced in the payload below; untouched for food.
    const { results: recipes } = await env.DB.prepare(
      `SELECT id, title, cuisine, description, skill_level, prep_minutes, cook_minutes, servings, avg_rating, total_ratings, cook_count, image_url, photo_credit, photo_license, photo_source_url, attribution,
              content_type, is_historic, is_alcoholic, glass_type, method, garnish, abv_percent, original_text, modernized_text, contributor_name, contributor_story, source_year, source_book, source_region,
              first_cooked_by_user_id, first_cooked_by_display_name
         FROM recipe ${whereSql} ORDER BY RANDOM() LIMIT 75`
    ).bind(...whereBindings).all();

    const candidates = (recipes || []).filter(r => !excludedIds.has(r.id));
    if (candidates.length === 0) return json({ deck: [], dailyCap, remaining, resetAt, tier }, 200, request, env);

    // Fetch all ingredients in one query (chunked by 80 to stay under D1 param limit)
    const ingsByRecipe = new Map();
    for (let i = 0; i < candidates.length; i += 80) {
      const ids = candidates.slice(i, i + 80).map(r => r.id);
      const ph = ids.map(() => '?').join(',');
      const { results: ings } = await env.DB.prepare(
        `SELECT recipe_id, name, canonical_name, quantity, unit, aisle FROM recipe_ingredient WHERE recipe_id IN (${ph})`
      ).bind(...ids).all();
      for (const row of ings || []) {
        // Use canonical_name as the match key; display stays `name`
        row.match_key = row.canonical_name || row.name;
        if (!ingsByRecipe.has(row.recipe_id)) ingsByRecipe.set(row.recipe_id, []);
        ingsByRecipe.get(row.recipe_id).push(row);
      }
    }

    // Pre-compute palate lookups once per request.
    const prefCuisines  = new Set((prefs?.cuisines  || []).map(s => String(s).toLowerCase()));
    const prefAvoid     = new Set((prefs?.avoid     || []).map(s => String(s).toLowerCase()));
    // Allergens get expanded into their actual ingredient keywords so the deck
    // score knows that "dairy" means "milk + cheese + butter + …". Mirror of
    // ALLERGEN_KEYWORDS in the Android RecipeDetailScreen — same keys, same
    // values, kept in sync by hand. Without this expansion, the substring
    // check below was a no-op (no recipe has "dairy" in an ingredient name).
    const ALLERGEN_KEYWORDS = {
      'peanuts': ['peanut', 'groundnut'],
      'tree nuts': ['almond', 'walnut', 'pecan', 'cashew', 'hazelnut', 'pistachio', 'macadamia', 'brazil nut', 'pine nut', 'chestnut'],
      'shellfish': ['shrimp', 'prawn', 'lobster', 'crab', 'crayfish', 'langoustine'],
      'molluscs': ['oyster', 'mussel', 'clam', 'scallop', 'squid', 'calamari', 'octopus', 'snail'],
      'fish': ['fish', 'salmon', 'tuna', 'cod', 'tilapia', 'haddock', 'trout', 'anchovy', 'sardine', 'bass', 'mackerel', 'halibut', 'snapper', 'swordfish'],
      'eggs': ['egg', 'yolk', 'albumen', 'mayonnaise', 'mayo'],
      'dairy': ['milk', 'butter', 'cheese', 'cream', 'yogurt', 'yoghurt', 'ricotta', 'parmesan', 'mozzarella', 'ghee', 'buttermilk', 'kefir', 'whey', 'casein', 'lactose', 'cheddar', 'feta', 'gouda', 'brie', 'burrata'],
      'soy': ['soy', 'soybean', 'tofu', 'tempeh', 'edamame', 'miso', 'tamari'],
      'wheat': ['wheat', 'flour', 'bread', 'pasta', 'couscous', 'bulgur', 'semolina', 'spelt', 'farro', 'noodle'],
      'gluten': ['wheat', 'barley', 'rye', 'flour', 'bread', 'pasta', 'couscous', 'bulgur', 'semolina', 'spelt', 'malt', 'farro', 'seitan', 'noodle'],
      'sesame': ['sesame', 'tahini'],
      'mustard': ['mustard'],
      'sulfites': ['sulfite', 'wine', 'dried apricot', 'molasses', 'champagne'],
      'corn': ['corn', 'cornstarch', 'polenta', 'hominy', 'masa', 'tortilla', 'popcorn'],
      'celery': ['celery'],
    };
    const prefAllergens = [];
    // Per-allergen labeled list — preserves which top-level allergen ('dairy',
    // 'wheat', etc.) each keyword belongs to, so the per-recipe banner color
    // logic can ask "are ALL matched allergens substitutable?"
    const prefAllergensByLabel = [];
    for (const raw of prefs?.allergens || []) {
      const key = String(raw).toLowerCase();
      const expanded = ALLERGEN_KEYWORDS[key] || [key];
      for (const k of expanded) prefAllergens.push(k);
      prefAllergensByLabel.push({ label: key, keywords: expanded });
    }
    // Allergen banner status is now data-driven: we ask the SEED substitutions
    // table directly via hasSubsFor() per matched ingredient. If at least one
    // matched ingredient has a sub on file, it's YELLOW (block w/ workaround).
    // If NONE do, it's RED (skip recipe). The previous hardcoded label set
    // was wrong — "dairy" obviously has subs (almond/oat/soy milk, vegan
    // butter, nutritional yeast, cashew cream...), it's per-ingredient.
    const tasteCuisineMap = new Map();
    const tasteIngMap = new Map();
    for (const c of taste?.topCuisines    || []) tasteCuisineMap.set((c.name || '').toLowerCase(), c.score || 0);
    for (const i of taste?.topIngredients || []) tasteIngMap.set((i.name || '').toLowerCase(), i.score || 0);
    const top3Cuisines = new Set(
      [...tasteCuisineMap.entries()].sort((a, b) => b[1] - a[1]).slice(0, 3).map(([k]) => k)
    );
    // Normalization caps — keep each component in 0..100 so weighted blend is stable.
    const maxTasteCuisine = Math.max(1, ...[...tasteCuisineMap.values(), 0]);
    const maxTasteIng     = Math.max(1, ...[...tasteIngMap.values(), 0]);

    const scored = [];
    for (const r of candidates) {
      const ingList = ingsByRecipe.get(r.id) || [];
      if (!ingList.length) continue;

      // O(1) match check: recipe canonical_name was pre-computed on insert, pantry canonicals
      // are in a Set. For rows still NULL (not-yet-migrated), fall back to the fuzzy index.
      const haveFlags = ingList.map(i => {
        const key = i.canonical_name;
        if (key) return pantryCanonSet.has(key) || indexMatch(key, pantryIdx) !== null;
        return indexMatch(i.name, pantryIdx) !== null;
      });
      const matched = haveFlags.filter(Boolean).length;
      // Exclude universal staples (salt/pepper/water) from the match %.
      // Nobody cares "do I have water for the Manhattan." Count only real ingredients
      // in both the numerator AND the denominator so staples don't inflate the score.
      const staplePositions = ingList.map(i => isStaple(i.canonical_name || i.name));
      const denominator = ingList.length - staplePositions.filter(Boolean).length;
      const numerator = haveFlags
        .filter((hit, idx) => hit && !staplePositions[idx]).length;
      const percent = denominator > 0
        ? Math.round((numerator / denominator) * 100)
        : 0;

      const usesExpiring = ingList
        .filter(i => {
          const key = i.canonical_name;
          if (key) return expiringCanonSet.has(key) || indexMatch(key, expiringIdx) !== null;
          return indexMatch(i.name, expiringIdx) !== null;
        })
        .map(i => i.name);

      const ms = computeMaxServings(r, ingList, pantryByName, pantryIdx);
      const maxServings = ms?.maxServings || null;

      // Rough $/serving — only count missing ingredients. Hard-capped so no wild outliers.
      const rawMissing = ingList.reduce((sum, i, idx) => {
        if (haveFlags[idx]) return sum;
        const cat = (i.aisle || 'other').toLowerCase();
        return sum + estimatePriceUsd(i.name, cat, Number(i.quantity) || 1);
      }, 0);
      const missingCost = Math.min(40, rawMissing); // cap a single recipe shop cost at $40
      const servings = Number(r.servings) || 2;
      const costPerServing = missingCost > 0
        ? Math.min(20, Math.round((missingCost / servings) * 100) / 100)
        : 0;
      // Shortest expiring-ingredient window (null if nothing's expiring)
      const shortestExpiryDays = usesExpiring.length ? 5 : null;

      // ---- Palate-aware blend ----
      // Normalize core signals to 0..100 for a clean weighted mix, then add palate.
      const recipeCuisine = (r.cuisine || '').trim().toLowerCase();
      const ingLowerList = ingList.map(i => String(i.canonical_name || i.name || '').toLowerCase()).filter(Boolean);

      // Expiring: 0..100 (3+ expiring ingredients = saturated).
      const expiringScore = Math.min(100, usesExpiring.length * 40);
      // Match already 0..100 by construction.
      const matchScore = percent;

      // Taste cuisine affinity (0..60): explicit pref = +60, taste history scaled to 0..40.
      // Substring match — user pref "italian" hits recipe cuisine "Italian-American",
      // "northern italian", etc. Exact-match alone was missing most of the catalog
      // because recipe cuisines are often qualified ("Indian (North)", "Tex-Mex").
      // 4-char minimum on the pref side avoids false positives like "thai" → "thailand".
      let tasteScore = 0;
      const cuisineMatchesPref = recipeCuisine && Array.from(prefCuisines).some(p => {
        if (!p || p.length < 4) return p === recipeCuisine;
        return recipeCuisine.includes(p) || p.includes(recipeCuisine);
      });
      if (cuisineMatchesPref) tasteScore += 60;
      if (recipeCuisine && tasteCuisineMap.has(recipeCuisine)) tasteScore += 40 * (tasteCuisineMap.get(recipeCuisine) / maxTasteCuisine);
      // Ingredient overlap with historical affinity (0..40).
      let ingAff = 0, ingHits = 0;
      for (const ing of ingLowerList) {
        if (tasteIngMap.has(ing)) { ingAff += tasteIngMap.get(ing); ingHits++; }
      }
      if (ingHits > 0) tasteScore += Math.min(40, 40 * (ingAff / (ingHits * maxTasteIng)));
      // Avoidance penalty — strong subtractive term.
      const hitsAvoid = ingLowerList.some(ing => {
        for (const a of prefAvoid)     if (a && ing.includes(a)) return true;
        for (const a of prefAllergens) if (a && ing.includes(a)) return true;
        return false;
      });
      if (hitsAvoid) tasteScore -= 80;
      // Clamp to 0..100.
      tasteScore = Math.max(0, Math.min(100, tasteScore));

      // Novelty: 1.0 if cuisine NOT in user's top-3 historical, else 0.3. Scaled 0..100.
      const isTopCuisine = recipeCuisine && top3Cuisines.has(recipeCuisine);
      const noveltyScore = (isTopCuisine ? 0.3 : 1.0) * 100;

      // Adventure slider nudges the novelty weight up/down a bit.
      const adventure = Math.max(0, Math.min(3, prefs?.adventure ?? 1));
      const noveltyWeight = 0.1 + (adventure - 1) * 0.03;   // 0.07..0.16
      const tasteWeight   = 0.2;
      const expiringWeight = 0.35;
      const matchWeight    = 1.0 - expiringWeight - tasteWeight - noveltyWeight;   // ~0.35

      // ---- Allergen status (none|yellow|red) ----
      // Per-ingredient sub lookup against the SEED table. For each matched
      // allergen we capture the FIRST hit ingredient (e.g., dairy hit "milk"
      // and "butter" — capture both); if any hit ingredient has known subs,
      // status is yellow (workable). Only red when EVERY hit ingredient has
      // zero subs in seed AND zero structural workarounds.
      let allergenStatus = 'none';
      const matchedAllergenLabels = [];
      const matchedIngredients = [];
      for (const { label, keywords } of prefAllergensByLabel) {
        let hit = false;
        for (const ing of ingLowerList) {
          if (keywords.some(k => k && ing.includes(k))) {
            hit = true;
            matchedIngredients.push(ing);
          }
        }
        if (hit) matchedAllergenLabels.push(label);
      }
      if (matchedAllergenLabels.length > 0) {
        // Yellow if ANY matched ingredient on this recipe has a sub on file.
        // Red only when zero of the matched ingredients have a workable swap.
        const anyHasSubs = matchedIngredients.some(ing => hasSubsFor(ing));
        allergenStatus = anyHasSubs ? 'yellow' : 'red';
      }

      // Final blended score (0..100 range). Keep small legacy kickers for ties.
      const blended =
        expiringScore * expiringWeight
        + matchScore  * matchWeight
        + tasteScore  * tasteWeight
        + noveltyScore* noveltyWeight;

      // Image bonus reduced 32→8 so photoless recipes can surface in the deck.
      // Was: photo'd cards essentially guaranteed the top 10 slots, so the
      // user-contribution camera CTA never had a card to render against. Now
      // photo'd recipes still tiebreak ahead of equivalent photoless ones,
      // but a recipe with strong pantry-match + expiring + taste-affinity
      // can outrank a photo'd recipe with weaker signals — and that's the
      // right outcome for both ranking quality and contribution opportunity.
      const score =
        blended                                                // 0..100 core
        + (r.image_url ? 8 : 0)                               // gentle photo tiebreaker
        + (percent >= 60 && r.image_url ? 4 : 0)              // photo + high-match small bump
        + (r.cook_count || 0) * 0.05                          // mild social proof
        + (r.avg_rating || 0) * 2                              // tiebreaker from ratings
        + (maxServings && maxServings >= (r.servings || 2) ? 6 : 0);  // fully-makeable tiebreaker

      scored.push({
        score, hasMatch: percent > 0 || usesExpiring.length > 0,
        recipe: {
          id: r.id, title: r.title, cuisine: r.cuisine, description: r.description,
          skillLevel: r.skill_level, prepMinutes: r.prep_minutes, cookMinutes: r.cook_minutes,
          servings: r.servings, avgRating: r.avg_rating, totalRatings: r.total_ratings,
          cookCount: r.cook_count || 0,
          imageUrl: r.image_url,
          photoCredit: r.photo_credit,
          photoLicense: r.photo_license,
          photoSourceUrl: r.photo_source_url,
          attribution: r.attribution,
          pantryMatchPercent: percent,
          usesExpiring,
          maxServings,
          costPerServing,
          // Cocktail / historic metadata — null for food recipes, populated for Mixology.
          content_type: r.content_type || 'food',
          is_historic: !!r.is_historic,
          is_alcoholic: !!r.is_alcoholic,
          glass_type: r.glass_type || null,
          method: r.method || null,
          garnish: r.garnish || null,
          abv_percent: r.abv_percent ?? null,
          original_text: r.original_text || null,
          modernized_text: r.modernized_text || null,
          contributor_name: r.contributor_name || null,
          contributor_story: r.contributor_story || null,
          source_year: r.source_year ?? null,
          source_book: r.source_book || null,
          source_region: r.source_region || null,
          // First-cook claim: surfaces "First cooked by X" badge on the card,
          // and gates the photo-less placeholder CTA. If null, the recipe is
          // unclaimed and the next cooker gets their name on it forever.
          first_cooked_by_display_name: r.first_cooked_by_display_name || null,
          // Allergen banner driver. 'none' | 'yellow' | 'red'.
          //   yellow: subs exist (block but workable)
          //   red:    no subs (skip recipe)
          allergenStatus,
          allergenLabels: matchedAllergenLabels,
          ingredients: ingList.map((i, idx) => ({
            name: i.name, quantity: i.quantity, unit: i.unit, aisle: i.aisle,
            have: haveFlags[idx],
          })).slice(0, 15),
        },
      });
    }

    scored.sort((a, b) => b.score - a.score);

    // Default deck: only recipes with a real match (>= 20%) OR at least one expiring-ingredient hit.
    // Super Adventurer: flip the filter to surface LOW match (< 20%) recipes on purpose.
    let picks;
    if (adventurous) {
      picks = scored.filter(s => s.recipe.pantryMatchPercent < 20 && s.recipe.pantryMatchPercent >= 0);
      if (picks.length === 0) picks = scored; // fallback if user has 100% match coverage
    } else {
      const strong = scored.filter(s => s.recipe.pantryMatchPercent >= 20 || s.recipe.usesExpiring.length > 0);
      picks = strong.length >= remaining ? strong : scored;
    }
    const deck = picks.slice(0, remaining).map(s => s.recipe);
    // Log shape so we can diagnose "cards aren't loading" without an actual
    // 4xx — responses can return 200 with deck:[] when filters drop everything.
    console.log(`deck u=${userId.slice(0,8)} ct=${contentType} candidates=${candidates.length} scored=${scored.length} strong=${scored.filter(s => s.recipe.pantryMatchPercent >= 20 || s.recipe.usesExpiring.length > 0).length} returned=${deck.length} remaining=${remaining}`);

    const payload = JSON.stringify({ deck, dailyCap, remaining, resetAt, tier });
    if (env.RATE_LIMIT_KV) {
      try { await env.RATE_LIMIT_KV.put(cacheKey, payload, { expirationTtl: 120 }); }
      catch (e) { console.warn('deck cache put failed (fail-open):', e?.message); }
    }
    return new Response(payload, {
      status: 200,
      headers: { 'Content-Type': 'application/json', 'X-Cache': 'MISS' },
    });
  },

  /** Search recipes by title. Bars want to look up "Margarita" and see all 50 versions
   *  in a grid. Results sorted by photo-first then by user engagement (saves desc).
   *  Filters: ?q=name (required), ?content_type=cocktail|food (optional), ?limit=50 (max 100). */
  async search(request, userId, env) {
    const rl = await enforce(env, 'read', userId);
    if (rl) return rl;
    const url = new URL(request.url);
    const q = (url.searchParams.get('q') || '').trim();
    if (q.length < 2) return json({ results: [], total: 0 }, 200, request, env);
    const contentType = (url.searchParams.get('content_type') || '').toLowerCase();
    const limit = Math.max(1, Math.min(100, parseInt(url.searchParams.get('limit') || '50', 10)));
    // Optional alcohol filter — '1' = alcoholic only, '0' = mocktails/zero-proof only.
    const alcoholParam = url.searchParams.get('alcoholic');
    const alcoholFilter = alcoholParam === '1' ? 1 : alcoholParam === '0' ? 0 : null;

    const like = `%${q.replace(/[%_]/g, m => '\\' + m)}%`;
    // Search both title AND ingredient names. Title matches rank higher (ORDER BY below).
    let sql = `SELECT id, title, cuisine, image_url, content_type, source_year, source_book,
                      glass_type, abv_percent, is_historic, is_alcoholic, avg_rating,
                      (LOWER(title) LIKE LOWER(?) ESCAPE '\\') AS title_hit
                 FROM recipe
                WHERE (
                  LOWER(title) LIKE LOWER(?) ESCAPE '\\'
                  OR id IN (
                    SELECT recipe_id FROM recipe_ingredient
                     WHERE LOWER(name) LIKE LOWER(?) ESCAPE '\\'
                     LIMIT 500
                  )
                )`;
    const bindings = [like, like, like];
    if (contentType === 'cocktail' || contentType === 'mocktail') {
      sql += ` AND content_type IN ('cocktail','mocktail')`;
    } else if (contentType === 'food') {
      sql += ` AND (content_type = 'food' OR content_type IS NULL)`;
    }
    if (alcoholFilter !== null) {
      sql += ` AND COALESCE(is_alcoholic, 0) = ?`;
      bindings.push(alcoholFilter);
    }
    // Title matches first, then photo'd, then quality, then alphabetic.
    sql += ` ORDER BY title_hit DESC,
                      (image_url IS NOT NULL AND image_url != '') DESC,
                      COALESCE(avg_rating, 0) DESC,
                      title ASC
              LIMIT ?`;
    bindings.push(limit);
    const { results } = await env.DB.prepare(sql).bind(...bindings).all();
    // Coerce SQLite ints → real booleans so kotlinx.serialization on Android can parse.
    // (Other deck/get endpoints already do this; the search handler missed it.)
    const coerced = (results || []).map(r => ({
      ...r,
      is_historic: !!r.is_historic,
      is_alcoholic: !!r.is_alcoholic,
    }));
    return json({ results: coerced, total: coerced.length }, 200, request, env);
  },

  /** All recipes the user has saved (swiped right on) — ordered newest-first,
   * with live pantry match % so they can see what they can cook right now. */
  async saved(userId, env, request) {
    const rl = await enforce(env, 'read', userId);
    if (rl) return rl;

    const { results: interactions } = await env.DB.prepare(
      `SELECT i.recipe_id, i.created_at, r.title, r.cuisine, r.prep_minutes, r.cook_minutes,
              r.servings, r.avg_rating, r.total_ratings, r.cook_count, r.image_url,
              r.content_type
         FROM interaction i JOIN recipe r ON r.id = i.recipe_id
        WHERE i.user_id = ? AND i.status = 'saved'
        ORDER BY i.created_at DESC LIMIT 200`
    ).bind(userId).all();

    if (!interactions?.length) return json({ saved: [] }, 200, request, env);

    // Pantry set for live match % calculation
    const { results: pantry } = await env.DB.prepare(
      'SELECT canonical_name, name FROM pantry_item WHERE user_id = ?'
    ).bind(userId).all();
    const pantrySet = new Set((pantry || []).map(p => (p.canonical_name || p.name || '').toLowerCase().trim()).filter(Boolean));

    const ids = interactions.map(r => r.recipe_id);
    const ingsByRecipe = new Map();
    for (let i = 0; i < ids.length; i += 80) {
      const chunk = ids.slice(i, i + 80);
      const ph = chunk.map(() => '?').join(',');
      const { results: ings } = await env.DB.prepare(
        `SELECT recipe_id, name, canonical_name FROM recipe_ingredient WHERE recipe_id IN (${ph})`
      ).bind(...chunk).all();
      for (const row of ings || []) {
        if (!ingsByRecipe.has(row.recipe_id)) ingsByRecipe.set(row.recipe_id, []);
        ingsByRecipe.get(row.recipe_id).push({
          key: (row.canonical_name || row.name || '').toLowerCase().trim(),
          display: row.name,
        });
      }
    }

    const saved = interactions.map(r => {
      const ings = ingsByRecipe.get(r.recipe_id) || [];
      const matched = ings.filter(x => x.key && pantrySet.has(x.key)).length;
      const percent = ings.length ? Math.round((matched / ings.length) * 100) : 0;
      return {
        id: r.recipe_id, title: r.title, cuisine: r.cuisine,
        prepMinutes: r.prep_minutes, cookMinutes: r.cook_minutes,
        servings: r.servings, avgRating: r.avg_rating, totalRatings: r.total_ratings,
        cookCount: r.cook_count || 0, imageUrl: r.image_url,
        // content_type was missing from this payload — caused EVERY saved recipe to default
        // to "food" on the client and dump into the Cooking book regardless of bucket.
        // Snake_case to match the @SerialName("content_type") binding on SavedRecipe DTO.
        content_type: r.content_type || 'food',
        pantryMatchPercent: percent,
        missingCount: ings.length - matched,
        savedAt: r.created_at,
      };
    });

    return json({ saved }, 200, request, env);
  },

  /** Re-add a saved recipe's still-missing ingredients to the shopping list.
   * Used from the Saved list when the user wants to shop for a specific meal. */
  async reshop(request, userId, env) {
    const rl = await enforce(env, 'write', userId);
    if (rl) return rl;
    const p = await readJson(request, 2_000);
    if (p.error) return p.error;
    const { recipeId } = p.value;
    if (!validOpaqueId(recipeId)) return err(400, 'recipeId required');

    const { results: pantry } = await env.DB.prepare(
      'SELECT canonical_name, name FROM pantry_item WHERE user_id = ?'
    ).bind(userId).all();
    const have = new Set((pantry || []).map(p => (p.canonical_name || p.name || '').toLowerCase().trim()).filter(Boolean));

    const { results: ings } = await env.DB.prepare(
      'SELECT name, canonical_name, quantity, unit, aisle FROM recipe_ingredient WHERE recipe_id = ?'
    ).bind(recipeId).all();

    let added = 0;
    for (const i of ings || []) {
      const key = (i.canonical_name || i.name || '').toLowerCase().trim();
      if (!key || have.has(key)) continue;
      if (isStaple(i.name) || isStaple(key)) continue;  // skip salt/pepper/water
      // Block obvious junk: undefined / empty / pure numerals / 1-char names
      if (!key || key === 'undefined' || key === 'null' || key.length < 2 || /^[\d\s./,&-]+$/.test(key)) continue;
      if (key.length > 35 || /\.\s*$/.test(key)) continue;
      if (/^(cover|bring|allow|leave|serve|taste|place|spoon|press|enjoy|repeat|apply|smear|layer|whisk|knead|brush|let |wait|cool|warm|drain|remove|unwrap|wrap |cut |mix |chop |dice |peel |rinse|grate|shred|slice|blend|crush|mince|stir |fold |drizzl|sprink|arrang|spread|put |add |combine|pour |heat |simmer|reduce|transfer)/.test(key)) continue;
      const existing = await env.DB.prepare(
        'SELECT id FROM shopping_item WHERE user_id = ? AND LOWER(name) = ? AND checked = 0'
      ).bind(userId, key).first();
      if (existing) continue;
      await env.DB.prepare(
        'INSERT INTO shopping_item (id, user_id, name, quantity, unit, aisle, checked, source, created_at) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)'
      ).bind(
        uid(), userId, String(i.name).slice(0, 80),
        Number.isFinite(Number(i.quantity)) ? Number(i.quantity) : null,
        i.unit ? String(i.unit).slice(0, 20) : null,
        i.aisle || 'other', 'reshop', Date.now(),
      ).run();
      added++;
    }
    return json({ ok: true, added }, 200, request, env);
  },

  async get(recipeId, userId, env, request) {
    if (!validOpaqueId(recipeId)) return err(400, 'id invalid');
    const r = await env.DB.prepare(
      'SELECT id, title, cuisine, description, skill_level, prep_minutes, cook_minutes, servings, avg_rating, total_ratings, dietary_flags, allergen_warnings, image_url, photo_credit, photo_license, photo_source_url, attribution FROM recipe WHERE id = ?'
    ).bind(recipeId).first();
    if (!r) return err(404, 'recipe not found');
    const ings = await env.DB.prepare(
      'SELECT seq, name, canonical_name, quantity, unit, aisle, subs FROM recipe_ingredient WHERE recipe_id = ? ORDER BY seq'
    ).bind(recipeId).all();
    const steps = await env.DB.prepare(
      'SELECT seq, text, timer_seconds FROM recipe_step WHERE recipe_id = ? ORDER BY seq'
    ).bind(recipeId).all();

    // Load pantry so each ingredient can carry a `have` flag — the Recipe Detail
    // screen needs this to render matched vs missing, matching the deck badge.
    // Key format mirrors deck(): canonical_name preferred, fall back to name.
    const { results: pantry } = await env.DB.prepare(
      'SELECT canonical_name, name FROM pantry_item WHERE user_id = ?'
    ).bind(userId).all();
    const pantryNames = [];
    for (const p of pantry || []) {
      const key = (p.canonical_name || p.name || '').trim();
      if (key) pantryNames.push(key);
    }
    const pantryCanonSet = new Set(pantryNames);
    // Fuzzy index fallback for ingredients whose canonical_name hasn't been backfilled yet.
    const pantryIdx = buildPantryIndex(pantryNames);

    return json({
      id: r.id,
      title: r.title,
      cuisine: r.cuisine,
      description: r.description,
      skillLevel: r.skill_level,
      prepMinutes: r.prep_minutes,
      cookMinutes: r.cook_minutes,
      servings: r.servings,
      avgRating: r.avg_rating,
      totalRatings: r.total_ratings,
      imageUrl: r.image_url,
      photoCredit: r.photo_credit,
      photoLicense: r.photo_license,
      photoSourceUrl: r.photo_source_url,
      attribution: r.attribution,
      dietaryFlags: r.dietary_flags ? JSON.parse(r.dietary_flags) : [],
      allergenWarnings: r.allergen_warnings ? JSON.parse(r.allergen_warnings) : [],
      ingredients: (ings.results || []).map(i => {
        const canon = (i.canonical_name || '').trim();
        const have = canon
          ? (pantryCanonSet.has(canon) || indexMatch(canon, pantryIdx) !== null)
          : (indexMatch(i.name, pantryIdx) !== null);
        return {
          name: i.name, quantity: i.quantity, unit: i.unit, aisle: i.aisle,
          subs: i.subs ? JSON.parse(i.subs) : [],
          have,
        };
      }),
      steps: (steps.results || []).map(s => ({ order: s.seq, text: s.text, timerSeconds: s.timer_seconds })),
    }, 200, request, env);
  },

  /**
   * Admin seed — gated behind SEED_KEY AND env=dev/staging. Deletes from prod after ingest.
   *
   * Two modes:
   *   - POST /recipes/seed          (no body): loads ./seed-data.js (legacy, 12 recipes)
   *   - POST /recipes/seed { recipes: [...] }: upserts that batch (up to 200 per call)
   */
  async seed(request, env) {
    const envName = (env.ENVIRONMENT || 'prod').toLowerCase();
    if (envName === 'prod') return err(404, 'not found');
    if (!env.SEED_KEY) return err(404, 'not found');
    const key = request.headers.get('x-seed-key') || '';
    if (!timingSafeEqual(key, env.SEED_KEY)) return err(403, 'forbidden');

    const rl = await enforce(env, 'seed', 'global');
    if (rl) return rl;

    let recipes;
    const contentLen = parseInt(request.headers.get('content-length') || '0', 10);
    if (contentLen > 2) {
      const p = await readJson(request, 5_000_000);
      if (p.error) return p.error;
      if (!validArray(p.value.recipes, 200)) return err(400, 'recipes: 1-200 per batch');
      recipes = p.value.recipes;
    } else {
      const { SEED_RECIPES } = await import('./seed-data.js');
      recipes = SEED_RECIPES;
    }

    // Build a single D1 batch across all recipes. Serial awaits per-row were
    // limiting throughput to ~2 recipes/s; a batch of ~480 statements lands
    // well inside D1's ~1000-statement limit and the Worker's 30s CPU budget.
    const stmts = [];
    const errors = [];
    let upserted = 0;
    for (const r of recipes) {
      if (!validOpaqueId(r.id) || !validString(r.title, { max: 200 })) {
        errors.push({ id: r?.id, reason: 'invalid id/title' });
        continue;
      }
      try {
        const recipeStmts = [];
        recipeStmts.push(env.DB.prepare(
          'INSERT OR REPLACE INTO recipe (id, title, cuisine, description, skill_level, prep_minutes, cook_minutes, servings, avg_rating, total_ratings, dietary_flags, allergen_warnings, image_url, photo_credit, photo_license, photo_source_url, attribution) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'
        ).bind(
          r.id,
          String(r.title).slice(0, 200),
          r.cuisine ? String(r.cuisine).slice(0, 40) : null,
          r.description ? String(r.description).slice(0, 1000) : null,
          r.skillLevel ? String(r.skillLevel).slice(0, 20) : null,
          r.prepMinutes || null, r.cookMinutes || null, r.servings || null,
          Number.isFinite(r.avgRating) ? r.avgRating : 0,
          Number.isInteger(r.totalRatings) ? r.totalRatings : 0,
          JSON.stringify(r.dietaryFlags || []),
          JSON.stringify(r.allergenWarnings || []),
          r.imageUrl ? String(r.imageUrl).slice(0, 500) : null,
          r.photoCredit ? String(r.photoCredit).slice(0, 200) : null,
          r.photoLicense ? String(r.photoLicense).slice(0, 60) : null,
          r.photoSourceUrl ? String(r.photoSourceUrl).slice(0, 500) : null,
          r.attribution ? String(r.attribution).slice(0, 300) : null,
        ));
        recipeStmts.push(env.DB.prepare('DELETE FROM recipe_ingredient WHERE recipe_id = ?').bind(r.id));
        let seq = 0;
        for (const i of r.ingredients || []) {
          if (seq >= 40) break;
          const rawName = String(i.name || '').slice(0, 100);
          recipeStmts.push(env.DB.prepare(
            'INSERT INTO recipe_ingredient (recipe_id, seq, name, canonical_name, quantity, unit, aisle, subs) VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
          ).bind(
            r.id, seq++, rawName, canonicalize(rawName),
            Number.isFinite(Number(i.quantity)) ? Number(i.quantity) : null,
            i.unit ? String(i.unit).slice(0, 20) : null,
            i.aisle ? String(i.aisle).slice(0, 40) : 'other',
            i.subs ? JSON.stringify(i.subs) : null,
          ));
        }
        recipeStmts.push(env.DB.prepare('DELETE FROM recipe_step WHERE recipe_id = ?').bind(r.id));
        let stepSeq = 0;
        for (const s of r.steps || []) {
          if (stepSeq >= 30) break;
          recipeStmts.push(env.DB.prepare(
            'INSERT INTO recipe_step (recipe_id, seq, text, timer_seconds) VALUES (?, ?, ?, ?)'
          ).bind(r.id, stepSeq++, String(s.text || '').slice(0, 2000), s.timerSeconds ?? null));
        }
        // Only count + commit this recipe's stmts if we successfully prepared them all.
        for (const s of recipeStmts) stmts.push(s);
        upserted++;
      } catch (e) {
        errors.push({ id: r.id, reason: e.message?.slice(0, 80) || 'prepare error' });
      }
    }
    if (stmts.length) {
      try {
        await env.DB.batch(stmts);
      } catch (e) {
        // Whole batch failed — surface the error and reset upserted count.
        return json({ ok: false, upserted: 0, errors: [{ reason: `batch failed: ${e.message?.slice(0, 160) || 'unknown'}` }] }, 500);
      }
    }
    return json({ ok: true, upserted, errors: errors.slice(0, 20) });
  },

  async interact(request, userId, env) {
    const rl = await enforce(env, 'write', userId);
    if (rl) return rl;

    const p = await readJson(request, 4_000);
    if (p.error) return p.error;
    const { recipeId, status, dismissReason, substitutesUsed } = p.value;
    if (!validOpaqueId(recipeId)) return err(400, 'recipeId invalid');
    if (!INTERACTION_STATUS.has(status)) return err(400, 'status invalid');
    if (!validStringOrNull(dismissReason, { max: 120 })) return err(400, 'dismissReason: <=120 chars');
    // Substitutes only meaningful on a cook event. Cap at 240 chars to avoid prose dumps.
    if (!validStringOrNull(substitutesUsed, { max: 240 })) return err(400, 'substitutesUsed: <=240 chars');

    const recipe = await env.DB.prepare('SELECT id FROM recipe WHERE id = ?').bind(recipeId).first();
    if (!recipe) return err(404, 'recipe not found');

    // Was this user's first time transitioning to 'cooked' for this recipe? If so, bump cook_count.
    const prior = await env.DB.prepare(
      "SELECT status FROM interaction WHERE user_id = ? AND recipe_id = ?"
    ).bind(userId, recipeId).first();
    const wasCookedBefore = prior?.status === 'cooked';

    await env.DB.prepare(
      'INSERT OR REPLACE INTO interaction (id, user_id, recipe_id, status, dismiss_reason, substitutes_used, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)'
    ).bind(uid(), userId, recipeId, status, dismissReason || null, substitutesUsed || null, Date.now()).run();

    // Adaptive learning: log a taste signal for every meaningful interaction.
    // Weights skew positive on cooked (the strongest "I liked this enough to
    // execute it" signal), neutral-positive on saved, negative on dismissed.
    // Substitute use is its own signal (later: train on "what you swap" to
    // predict ingredient aversions). Consumed=null = not yet rolled into the
    // user_taste vector by the nightly compactor.
    {
      const signalWeight = ({
        cooked: 3.0, saved: 1.0, planned: 0.5, dismissed: -1.0,
      })[status] ?? 0;
      if (signalWeight !== 0) {
        await env.DB.prepare(
          `INSERT INTO user_taste_signal (user_id, recipe_id, signal_kind, weight, created_at)
           VALUES (?, ?, ?, ?, ?)`
        ).bind(userId, recipeId, status, signalWeight, Date.now()).run();
      }
      if (substitutesUsed) {
        await env.DB.prepare(
          `INSERT INTO user_taste_signal (user_id, recipe_id, signal_kind, weight, created_at)
           VALUES (?, ?, 'swap', 0.5, ?)`
        ).bind(userId, recipeId, Date.now()).run();
      }
    }

    // Bust the deck cache — next /recipes/deck must exclude this recipe.
    // Key is `deck:<userId>:<prefHash>:<day>`; we don't know prefHash cheaply, so we look it up.
    if (env.RATE_LIMIT_KV) {
      const day = Math.floor(Date.now() / 86400_000);
      try {
        const prefs = await getPreferencesFor(userId, env);
        const key = `deck:${userId}:${prefHash(prefs)}:${day}`;
        await env.RATE_LIMIT_KV.delete(key);
      } catch { /* best-effort; TTL will expire it anyway */ }
      // Also nuke the legacy key for clients still in the old cache window.
      await env.RATE_LIMIT_KV.delete(`deck-cache:${userId}:${day}`);
    }

    let cookUndoId = null;
    if (status === 'cooked' && !wasCookedBefore) {
      await env.DB.prepare('UPDATE recipe SET cook_count = COALESCE(cook_count, 0) + 1 WHERE id = ?').bind(recipeId).run();

      // First-cook claim: if no user has ever cooked this recipe, the cooker's
      // name lands on the card forever. Atomically claim — UPDATE with WHERE
      // first_cooked_by_user_id IS NULL means concurrent cooks of the same
      // unclaimed recipe race correctly (only one update succeeds).
      const cookerRow = await env.DB.prepare(
        'SELECT display_name, email FROM user WHERE id = ?'
      ).bind(userId).first();
      const displayName = (cookerRow?.display_name || cookerRow?.email?.split('@')?.[0] || 'A Speakeater').slice(0, 60);
      await env.DB.prepare(
        `UPDATE recipe
            SET first_cooked_by_user_id = ?,
                first_cooked_by_display_name = ?,
                first_cooked_at = ?
          WHERE id = ?
            AND first_cooked_by_user_id IS NULL`
      ).bind(userId, displayName, Date.now(), recipeId).run();

      // Deduct matching pantry quantities. Store the inverse so we can undo.
      const { results: pantryRows } = await env.DB.prepare(
        'SELECT id, name, quantity FROM pantry_item WHERE user_id = ?'
      ).bind(userId).all();
      const { results: recIngs } = await env.DB.prepare(
        'SELECT name, quantity FROM recipe_ingredient WHERE recipe_id = ?'
      ).bind(recipeId).all();

      const deductions = [];
      for (const ing of recIngs || []) {
        const need = Number(ing.quantity);
        if (!Number.isFinite(need) || need <= 0) continue;
        const match = (pantryRows || []).find(p => matches(p.name, ing.name));
        if (!match) continue;
        const have = Number(match.quantity);
        if (!Number.isFinite(have) || have <= 0) continue;
        const newQty = Math.max(0, Math.round((have - need) * 100) / 100);
        deductions.push({ pantryId: match.id, qtyBefore: have, qtyAfter: newQty });
        if (newQty <= 0) {
          await env.DB.prepare('DELETE FROM pantry_item WHERE id = ? AND user_id = ?').bind(match.id, userId).run();
        } else {
          await env.DB.prepare('UPDATE pantry_item SET quantity = ? WHERE id = ? AND user_id = ?').bind(newQty, match.id, userId).run();
        }
      }

      if (deductions.length) {
        cookUndoId = uid();
        await env.DB.prepare(
          'INSERT INTO cook_undo (id, user_id, recipe_id, deductions, created_at) VALUES (?, ?, ?, ?, ?)'
        ).bind(cookUndoId, userId, recipeId, JSON.stringify(deductions), Date.now()).run();
      }
    }

    // Compute remaining swipes for the day from D1 (source of truth: interaction rows).
    let remaining = null;
    let dailyCap = null;
    if (status === 'saved' || status === 'dismissed') {
      const { dayStartMs } = userLocalDay(request);
      // Per-bucket quota (food vs cocktail/mocktail) — match the deck endpoint logic
      // so the `remaining` field returned to the client is accurate per content type.
      const recipeRow = await env.DB.prepare('SELECT content_type FROM recipe WHERE id = ?').bind(recipeId).first();
      const isMixologyBucket = recipeRow?.content_type === 'cocktail' || recipeRow?.content_type === 'mocktail';
      const bucketTypes = isMixologyBucket ? ['cocktail', 'mocktail'] : ['food'];
      const placeholders = bucketTypes.map(() => '?').join(',');
      const swipeRow = await env.DB.prepare(
        `SELECT COUNT(*) AS n FROM interaction i
           JOIN recipe r ON r.id = i.recipe_id
          WHERE i.user_id = ? AND i.status IN ('saved','dismissed') AND i.created_at >= ?
            AND r.content_type IN (${placeholders})`
      ).bind(userId, dayStartMs, ...bucketTypes).first();
      const used = swipeRow?.n || 0;
      const ent = await env.DB.prepare(
        'SELECT expires_at FROM entitlement WHERE user_id = ? AND expires_at > ?'
      ).bind(userId, Date.now()).first();
      dailyCap = ent ? 9999 : 20;
      remaining = Math.max(0, dailyCap - (used + 1));
    }

    // Right-swipe (save): auto-add recipe's missing ingredients to shopping list.
    // IMPORTANT: use the same synonym-aware matcher the deck card uses to compute `have`,
    // otherwise the card says "4 missing" but we add all 7 (fuzzy/canonical matches miss).
    let addedToShopping = 0;
    if (status === 'saved') {
      const { results: pantry } = await env.DB.prepare(
        'SELECT name, canonical_name FROM pantry_item WHERE user_id = ?'
      ).bind(userId).all();
      const pantryNames = (pantry || []).map(p => p.canonical_name || p.name).filter(Boolean);
      const pantryIdx = buildPantryIndex(pantryNames);
      const have = new Set(pantryNames.map(n => String(n).toLowerCase().trim()));
      const { results: ings } = await env.DB.prepare(
        'SELECT name, canonical_name, quantity, unit, aisle FROM recipe_ingredient WHERE recipe_id = ?'
      ).bind(recipeId).all();
      for (const i of ings || []) {
        const key = String(i.name || '').toLowerCase().trim();
        if (!key || have.has(key)) continue;
        // Synonym / canonical / token match — matches the deck's "have" calculation.
        if (indexMatch(i.canonical_name || i.name, pantryIdx) !== null) continue;
        if (isStaple(i.name) || isStaple(key)) continue;  // skip salt/pepper/water
      // Block obvious junk: undefined / empty / pure numerals / 1-char names
      if (!key || key === 'undefined' || key === 'null' || key.length < 2 || /^[\d\s./,&-]+$/.test(key)) continue;
        // Filter obvious instruction fragments that slipped into ingredient tables
        // (mostly Wikibooks imports). Reject names that are too long, end with a
        // period, or start with an imperative verb.
        if (key.length > 35) continue;
        if (/\.\s*$/.test(key)) continue;
        if (/^(cover|bring|allow|leave|serve|taste|place|spoon|press|enjoy|repeat|apply|smear|layer|whisk|knead|brush|let |wait|cool|warm|drain|remove|unwrap|wrap |cut |mix |chop |dice |peel |rinse|grate|shred|slice|blend|crush|mince|stir |fold |drizzl|sprink|arrang|spread|put |mix |add |combine|pour |heat |simmer|reduce|transfer)/.test(key)) continue;
        // Avoid duplicates: skip if user already has an unchecked shopping row with this name
        const existing = await env.DB.prepare(
          'SELECT id FROM shopping_item WHERE user_id = ? AND LOWER(name) = ? AND checked = 0'
        ).bind(userId, key).first();
        if (existing) continue;
        await env.DB.prepare(
          'INSERT INTO shopping_item (id, user_id, name, quantity, unit, aisle, checked, source, created_at) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)'
        ).bind(
          uid(), userId, String(i.name).slice(0, 80),
          Number.isFinite(Number(i.quantity)) ? Number(i.quantity) : null,
          i.unit ? String(i.unit).slice(0, 20) : null,
          i.aisle || 'other', 'saved-recipe', Date.now(),
        ).run();
        addedToShopping++;
      }
    }

    // Auto-add to the user's Saved Book (standard Library book). Idempotent
    // and non-fatal — Library is a best-effort enrichment, not a blocker.
    if (status === 'saved') {
      await addRecipeToStandardBook(env, userId, 'saved', recipeId);
    }

    return json({ ok: true, remaining, dailyCap, addedToShopping, cookUndoId }, 200, request, env);
  },

  /**
   * Admin: re-canonicalize every recipe_ingredient + pantry_item row using the current
   * synonym map. Safe to run repeatedly. Gated by SEED_KEY + env=dev.
   */
  async recanonicalize(request, env) {
    const envName = (env.ENVIRONMENT || 'prod').toLowerCase();
    if (envName === 'prod') return err(404, 'not found');
    if (!env.SEED_KEY) return err(404, 'not found');
    const key = request.headers.get('x-seed-key') || '';
    if (!timingSafeEqual(key, env.SEED_KEY)) return err(403, 'forbidden');

    const rl = await enforce(env, 'seed', 'global');
    if (rl) return rl;

    // Page through UNCANONICALIZED rows only — idempotent, resumable on multiple calls.
    const LIMIT = 1500;
    let recipeUpdated = 0, pantryUpdated = 0;

    const { results: recIngs } = await env.DB.prepare(
      'SELECT recipe_id, seq, name FROM recipe_ingredient WHERE canonical_name IS NULL LIMIT ?'
    ).bind(LIMIT).all();
    for (const row of recIngs || []) {
      const canon = canonicalize(row.name);
      await env.DB.prepare(
        'UPDATE recipe_ingredient SET canonical_name = ? WHERE recipe_id = ? AND seq = ?'
      ).bind(canon, row.recipe_id, row.seq).run();
      recipeUpdated++;
    }

    const pantryRemaining = LIMIT - recipeUpdated;
    if (pantryRemaining > 0) {
      const { results: pantryItems } = await env.DB.prepare(
        'SELECT id, name FROM pantry_item WHERE canonical_name IS NULL LIMIT ?'
      ).bind(pantryRemaining).all();
      for (const row of pantryItems || []) {
        const canon = canonicalize(row.name);
        await env.DB.prepare(
          'UPDATE pantry_item SET canonical_name = ? WHERE id = ?'
        ).bind(canon, row.id).run();
        pantryUpdated++;
      }
    }

    const done = recipeUpdated + pantryUpdated < LIMIT;
    return json({
      ok: true,
      recipeIngredientsUpdated: recipeUpdated,
      pantryItemsUpdated: pantryUpdated,
      done,
      message: done ? 'Migration complete' : 'More rows remaining — run again',
    });
  },

  /** Undo a cook within 5 minutes. Restores pantry + deletes the interaction + decrements cook_count. */
  async undoCook(request, userId, env) {
    const p = await readJson(request, 2_000);
    if (p.error) return p.error;
    const { cookUndoId } = p.value;
    if (!validOpaqueId(cookUndoId)) return err(400, 'cookUndoId required');

    const row = await env.DB.prepare(
      'SELECT recipe_id, deductions, created_at FROM cook_undo WHERE id = ? AND user_id = ?'
    ).bind(cookUndoId, userId).first();
    if (!row) return err(404, 'undo not found');
    if (Date.now() - row.created_at > 5 * 60 * 1000) {
      await env.DB.prepare('DELETE FROM cook_undo WHERE id = ? AND user_id = ?').bind(cookUndoId, userId).run();
      return err(410, 'undo window expired');
    }

    let deductions;
    try { deductions = JSON.parse(row.deductions); } catch { deductions = []; }
    for (const d of deductions) {
      // If the pantry item still exists, restore its quantity
      const existing = await env.DB.prepare('SELECT id FROM pantry_item WHERE id = ? AND user_id = ?')
        .bind(d.pantryId, userId).first();
      if (existing) {
        await env.DB.prepare('UPDATE pantry_item SET quantity = ? WHERE id = ? AND user_id = ?')
          .bind(d.qtyBefore, d.pantryId, userId).run();
      }
      // Note: if the user already added new items with the same name we don't merge — safer to leave alone.
    }

    await env.DB.prepare('DELETE FROM interaction WHERE user_id = ? AND recipe_id = ?').bind(userId, row.recipe_id).run();
    await env.DB.prepare('UPDATE recipe SET cook_count = MAX(0, COALESCE(cook_count, 0) - 1) WHERE id = ?').bind(row.recipe_id).run();
    await env.DB.prepare('DELETE FROM cook_undo WHERE id = ? AND user_id = ?').bind(cookUndoId, userId).run();

    return json({ ok: true, restored: deductions.length }, 200, request, env);
  },
};

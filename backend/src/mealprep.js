// Pro-tier Meal Prep Mode — macro-targeted multi-week plans.
//
// POST /plans/meal-prep
// Body: {
//   targetCalories: number,   // kcal/day 1200-4500
//   targetProtein:  number,   // g/day 50-300
//   servingsPerDay: number,   // 1-6
//   recipesPerWeek: number,   // 3-10
//   weeks:          number,   // 1-4
// }
//
// Response (Pro):   { ok:true, tier:"pro",  weeks:[ { index, recipes:[...], totals:{...} } ], shoppingList:[...] }
// Response (Free):  { ok:false, tier:"free", upgrade:"...", preview:{ index:1, recipes:[...], totals:{...} }, shoppingList:[...] }
//
// Greedy selection is O(n * k) where n = candidate-pool and k = weeks * recipesPerWeek.
// Pre-filter the recipe pool in SQL (diet / allergen / nutrition-present) so the hot
// loop stays fast under Cloudflare's CPU limits.

import {
  json, err, readJson, validInt, validFiniteNumber,
} from './util.js';
import { enforce } from './ratelimit.js';
import { canonicalize } from './canonicalize.js';

const MAX_POOL = 300;
const BATCH_COOK_MINUTES = 20;    // recipes ≥ this are preferred for batch cooking
const MACRO_TOLERANCE = 0.15;     // ±15 % target acceptance band

export const handleMealPrep = {
  async propose(request, userId, env) {
    const rl = await enforce(env, 'mealprep', userId);
    if (rl) return rl;

    const p = await readJson(request, 4_000);
    if (p.error) return p.error;

    const targetCalories = p.value.targetCalories;
    const targetProtein  = p.value.targetProtein;
    const servingsPerDay = p.value.servingsPerDay;
    const recipesPerWeek = p.value.recipesPerWeek;
    const weeks          = p.value.weeks;

    if (!validFiniteNumber(targetCalories, { min: 1200, max: 4500 })) {
      return err(400, 'targetCalories: 1200-4500 kcal/day');
    }
    if (!validFiniteNumber(targetProtein, { min: 50, max: 300 })) {
      return err(400, 'targetProtein: 50-300 g/day');
    }
    if (!validInt(servingsPerDay, { min: 1, max: 6 })) {
      return err(400, 'servingsPerDay: 1-6');
    }
    if (!validInt(recipesPerWeek, { min: 3, max: 10 })) {
      return err(400, 'recipesPerWeek: 3-10');
    }
    if (!validInt(weeks, { min: 1, max: 4 })) {
      return err(400, 'weeks: 1-4');
    }

    // ---- Tier gate (mirrors vision.js / recipes.js pattern) ----
    const userRow = await env.DB.prepare(
      'SELECT id, email, diet FROM user WHERE id = ?'
    ).bind(userId).first();
    const envName = (env.ENVIRONMENT || 'prod').toLowerCase();
    const isDev = envName === 'dev' && /\.test$/i.test(userRow?.email || '');
    const ent = await env.DB.prepare(
      'SELECT sku, expires_at FROM entitlement WHERE user_id = ? AND expires_at > ?'
    ).bind(userId, Date.now()).first();
    const isPro = isDev || !!ent;
    const tier = isDev ? 'dev' : (ent ? 'pro' : 'free');

    // ---- User diet & allergens ----
    const diet = (userRow?.diet || 'None').toLowerCase();
    const { results: allergyRows } = await env.DB.prepare(
      'SELECT allergen FROM user_allergy WHERE user_id = ?'
    ).bind(userId).all();
    const allergens = (allergyRows || [])
      .map(r => String(r.allergen || '').toLowerCase().trim())
      .filter(Boolean);

    // ---- Candidate pool (SQL-side pre-filter) ----
    // Diet filter: only apply when user has a real dietary flag we can match.
    // We keep it conservative: a recipe qualifies when its dietary_flags JSON
    // contains the user's diet as a substring (LIKE).
    const dietFilter = diet && diet !== 'none' ? "AND (dietary_flags IS NULL OR dietary_flags LIKE ?)" : '';
    const params = [];
    if (dietFilter) params.push(`%"${diet}"%`);

    // Require a nutrition blob so scoring is meaningful. LIMIT 300 keeps CPU bounded.
    const sql =
      'SELECT id, title, cuisine, cook_minutes, prep_minutes, servings, ' +
      'avg_rating, dietary_flags, allergen_warnings, nutrition ' +
      'FROM recipe WHERE nutrition IS NOT NULL AND nutrition != \'\' ' +
      dietFilter + ' ORDER BY RANDOM() LIMIT ?';
    params.push(MAX_POOL);
    const { results: recipes } = await env.DB.prepare(sql).bind(...params).all();

    // ---- Parse + filter pool in JS (allergens + malformed nutrition) ----
    const pool = [];
    for (const r of recipes || []) {
      let nut;
      try { nut = JSON.parse(r.nutrition); } catch { continue; }
      const cal = Number(nut?.calories);
      const pro = Number(nut?.protein_g);
      if (!Number.isFinite(cal) || cal <= 0) continue;
      if (!Number.isFinite(pro) || pro < 0) continue;

      // Allergen hard-exclude — recipe's allergen_warnings is a JSON array of strings.
      if (allergens.length) {
        let allergenHits = [];
        try { allergenHits = JSON.parse(r.allergen_warnings || '[]'); } catch { allergenHits = []; }
        const hay = allergenHits.map(a => String(a || '').toLowerCase());
        if (hay.some(h => allergens.some(a => h.includes(a)))) continue;
      }

      pool.push({
        id: r.id,
        title: r.title,
        cuisine: r.cuisine,
        cookMinutes: r.cook_minutes || 0,
        prepMinutes: r.prep_minutes || 0,
        servings: r.servings || 2,
        avgRating: r.avg_rating || 0,
        caloriesPerServing: cal,
        proteinPerServing: pro,
      });
    }

    if (pool.length === 0) {
      return json({
        ok: false, tier, error: 'no recipes with nutrition data available for your diet',
        weeks: [], shoppingList: [],
      }, 200, request, env);
    }

    // ---- Fetch ingredients for the pool (chunked, stays under D1 param limit) ----
    const poolIds = pool.map(r => r.id);
    const ingsByRecipe = new Map();
    for (let i = 0; i < poolIds.length; i += 80) {
      const chunk = poolIds.slice(i, i + 80);
      const ph = chunk.map(() => '?').join(',');
      const { results: ings } = await env.DB.prepare(
        `SELECT recipe_id, name, canonical_name, quantity, unit, aisle
         FROM recipe_ingredient WHERE recipe_id IN (${ph})`
      ).bind(...chunk).all();
      for (const x of ings || []) {
        if (!ingsByRecipe.has(x.recipe_id)) ingsByRecipe.set(x.recipe_id, []);
        ingsByRecipe.get(x.recipe_id).push({
          name: x.name || '',
          canonical: x.canonical_name || canonicalize(x.name || ''),
          quantity: Number(x.quantity) || 0,
          unit: x.unit || null,
          aisle: x.aisle || 'other',
        });
      }
    }

    // Per-day target: targetCalories and targetProtein are per-day. We need
    // recipesPerWeek meals to cover servingsPerDay * 7 portions per week, so each
    // recipe contributes (servingsPerDay * 7 / recipesPerWeek) portions on average.
    // Per-recipe macro targets (per serving) are therefore daily targets / servingsPerDay.
    const perServingCalTarget = targetCalories / servingsPerDay;
    const perServingProTarget = targetProtein  / servingsPerDay;

    // ---- Greedy weekly selection ----
    const selectedGlobal = new Set();     // id → skip once used across plan (distinct recipes)
    const outputWeeks = [];
    for (let w = 0; w < weeks; w++) {
      const weekIngredientIndex = new Set();
      const weekRecipes = [];
      let weekCal = 0, weekPro = 0;

      for (let k = 0; k < recipesPerWeek; k++) {
        let best = null;
        let bestScore = -Infinity;
        for (const r of pool) {
          if (selectedGlobal.has(r.id)) continue;
          const score = scoreRecipe(
            r, ingsByRecipe.get(r.id) || [],
            perServingCalTarget, perServingProTarget,
            weekIngredientIndex,
          );
          if (score > bestScore) { bestScore = score; best = r; }
        }
        if (!best) break;

        selectedGlobal.add(best.id);
        const ings = ingsByRecipe.get(best.id) || [];
        for (const i of ings) if (i.canonical) weekIngredientIndex.add(i.canonical);

        // Week totals: each recipe feeds its declared servings count.
        weekCal += best.caloriesPerServing * (best.servings || 2);
        weekPro += best.proteinPerServing  * (best.servings || 2);

        weekRecipes.push({
          id: best.id,
          title: best.title,
          cuisine: best.cuisine,
          cookMinutes: best.cookMinutes,
          prepMinutes: best.prepMinutes,
          servings: best.servings,
          caloriesPerServing: Math.round(best.caloriesPerServing),
          proteinPerServing: Math.round(best.proteinPerServing * 10) / 10,
          batchCookable: best.cookMinutes >= BATCH_COOK_MINUTES,
          score: Math.round(bestScore * 100) / 100,
        });
      }

      // Per-day projected (week totals / 7) — what the user will actually eat/day
      const dayCal = weekCal / 7;
      const dayPro = weekPro / 7;
      outputWeeks.push({
        index: w + 1,
        recipes: weekRecipes,
        totals: {
          weeklyCalories: Math.round(weekCal),
          weeklyProtein:  Math.round(weekPro),
          projectedDailyCalories: Math.round(dayCal),
          projectedDailyProtein:  Math.round(dayPro),
          calorieDeviation: Math.round(((dayCal - targetCalories) / targetCalories) * 100),
          proteinDeviation: Math.round(((dayPro - targetProtein)  / targetProtein)  * 100),
          withinTolerance:
            Math.abs(dayCal - targetCalories) / targetCalories <= MACRO_TOLERANCE &&
            Math.abs(dayPro - targetProtein)  / targetProtein  <= MACRO_TOLERANCE,
        },
      });
    }

    // ---- Aggregate consolidated shopping list across ALL weeks ----
    const shoppingMap = new Map();  // canonical -> { name, quantity, unit, aisle }
    for (const w of outputWeeks) {
      for (const r of w.recipes) {
        const ings = ingsByRecipe.get(r.id) || [];
        for (const i of ings) {
          const key = i.canonical || canonicalize(i.name || '') || (i.name || '').toLowerCase();
          if (!key) continue;
          if (!shoppingMap.has(key)) {
            shoppingMap.set(key, {
              name: i.name || key,
              canonical: key,
              quantity: i.quantity > 0 ? i.quantity : 0,
              unit: i.unit,
              aisle: i.aisle,
              recipeCount: 1,
            });
          } else {
            const entry = shoppingMap.get(key);
            entry.recipeCount += 1;
            if (entry.unit === i.unit && i.quantity > 0) entry.quantity += i.quantity;
          }
        }
      }
    }
    const shoppingList = [...shoppingMap.values()]
      .map(e => ({
        name: e.name,
        canonical: e.canonical,
        quantity: e.quantity > 0 ? Math.round(e.quantity * 100) / 100 : null,
        unit: e.unit,
        aisle: e.aisle,
        recipeCount: e.recipeCount,
      }))
      .sort((a, b) => b.recipeCount - a.recipeCount || a.aisle.localeCompare(b.aisle));

    // ---- Paywall enforcement ----
    // Free-tier users get a 1-week preview so the Android UI can render a paywall
    // card above a teaser plan. Status is 402 to make the gate unambiguous on the client.
    if (!isPro) {
      const previewWeek = outputWeeks[0] || null;
      const previewShop = previewWeek
        ? aggregateShoppingForWeek(previewWeek, ingsByRecipe)
        : [];
      return json({
        ok: false,
        tier: 'free',
        upgrade: 'Meal Prep Mode is a Pantrie Pro feature. Upgrade to unlock multi-week plans with macro targeting and consolidated shopping lists.',
        preview: previewWeek,
        shoppingList: previewShop,
      }, 402, request, env);
    }

    return json({
      ok: true, tier,
      targets: {
        dailyCalories: targetCalories,
        dailyProtein: targetProtein,
        servingsPerDay,
        recipesPerWeek,
        weeks,
      },
      weeks: outputWeeks,
      shoppingList,
    }, 200, request, env);
  },
};

function aggregateShoppingForWeek(week, ingsByRecipe) {
  const map = new Map();
  for (const r of week.recipes) {
    const ings = ingsByRecipe.get(r.id) || [];
    for (const i of ings) {
      const key = i.canonical || canonicalize(i.name || '') || (i.name || '').toLowerCase();
      if (!key) continue;
      if (!map.has(key)) {
        map.set(key, {
          name: i.name || key,
          canonical: key,
          quantity: i.quantity > 0 ? i.quantity : 0,
          unit: i.unit, aisle: i.aisle, recipeCount: 1,
        });
      } else {
        const e = map.get(key);
        e.recipeCount += 1;
        if (e.unit === i.unit && i.quantity > 0) e.quantity += i.quantity;
      }
    }
  }
  return [...map.values()].map(e => ({
    name: e.name, canonical: e.canonical,
    quantity: e.quantity > 0 ? Math.round(e.quantity * 100) / 100 : null,
    unit: e.unit, aisle: e.aisle, recipeCount: e.recipeCount,
  })).sort((a, b) => b.recipeCount - a.recipeCount);
}

/**
 * Recipe score = macroFit (0..1) * batchability (1.0 or 0.6) * ingredientOverlapBoost (1.0..1.5)
 * A small popularity term (avg_rating) tips ties.
 */
function scoreRecipe(r, ings, calTarget, proTarget, usedIngredientIndex) {
  // Macro fit: closer to target => higher (exponential penalty, 1.0 when perfect).
  const calDelta = Math.abs(r.caloriesPerServing - calTarget) / calTarget;
  const proDelta = Math.abs(r.proteinPerServing  - proTarget) / Math.max(1, proTarget);
  // Clamp so pathologically-off recipes still produce a numeric score.
  const macroFit = Math.max(0.02, Math.exp(-2 * (calDelta + proDelta)));

  const batchability = r.cookMinutes >= BATCH_COOK_MINUTES ? 1.0 : 0.6;

  let overlap = 0;
  let total = 0;
  for (const i of ings) {
    if (!i.canonical) continue;
    total++;
    if (usedIngredientIndex.has(i.canonical)) overlap++;
  }
  const overlapBoost = total > 0 ? 1 + 0.5 * (overlap / total) : 1.0;

  const rating = 1 + (r.avgRating || 0) * 0.02; // 0..0.1 tie-breaker

  return macroFit * batchability * overlapBoost * rating;
}

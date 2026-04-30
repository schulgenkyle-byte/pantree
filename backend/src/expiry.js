// Smart expiry defaults. Conservative estimates for refrigerated storage.
//
// Lookup order:
//   1) Canonical ingredient table (ingredient-shelf.js) — structured per-slug
//      shelf-life metadata (fridge / pantry / freezer / opened). Most accurate.
//   2) Name-level regex overrides (this file) — catches common items the
//      canonical table doesn't cover yet, and lets us patch without a rebuild.
//   3) Category defaults — last-resort generic bucket.
//
// `originalShelfDays` is exported alongside `expiresAt` so callers can persist
// it with the pantry row. Anything > 180 days is treated as shelf-stable for
// "expiring" surfacing (Plan + Shopping both suppress the badge for those).

import { canonicalize } from './canonicalize.js';
import { lookupShelfDays } from './ingredient-shelf.js';

const CATEGORY_DAYS = {
  produce:   7,
  protein:   3,
  dairy:     10,
  grain:     365,
  pantry:    365,
  spice:     1825,   // raised from 730 — most ground spices keep 3-5 years sealed
  condiment: 180,
  frozen:    90,
  beverage:  30,
  bakery:    5,
  deli:      5,
  other:     14,
};

// Name-level overrides — case-insensitive word-boundary regex. Order matters:
// first match wins, so put more specific rules above broader ones.
//
// Goal: cover EVERY common shelf-stable kitchen item so a misclassified
// category never triggers a bogus "Expiring" badge. Errors should bias high
// (longer shelf life) rather than show false-positive expirings.
const NAME_OVERRIDES = [
  // --- Dairy ---
  [/\b(milk|cream|half.?and.?half)\b/, 7],
  [/\b(yogurt|sour cream|cottage cheese|ricotta)\b/, 14],
  [/\b(hard cheese|cheddar|parmesan|gouda|gruyere|pecorino|asiago|manchego)\b/, 45],
  [/\b(soft cheese|brie|camembert|feta|goat cheese|chevre|burrata|mozzarella)\b/, 14],
  [/\b(eggs?)\b/, 28],
  [/\b(butter|ghee)\b/, 60],

  // --- Meat / poultry / seafood ---
  [/\b(chicken|turkey|ground beef|ground pork|ground turkey|ground chicken|ground lamb)\b/, 2],
  [/\b(steak|pork chop|pork loin|lamb|veal|brisket|ribeye|sirloin|tenderloin)\b/, 4],
  [/\b(salmon|tuna|cod|tilapia|halibut|trout|sea bass|mahi|swordfish|fish)\b/, 2],
  [/\b(shrimp|prawns?|scallops?|mussels?|clams?|oysters?|lobster|crab)\b/, 2],
  [/\b(bacon|deli meat|sausage|ham|prosciutto|salami|pepperoni|chorizo)\b/, 14],
  [/\b(canned tuna|canned salmon|sardines|anchovies)\b/, 1095],

  // --- Produce: berries / fast-spoiling fruit ---
  [/\b(berries?|strawberr|raspberr|blueberr|blackberr)\b/, 5],
  [/\b(banana|peach|nectarine|plum|apricot)\b/, 5],
  [/\b(grape|cherry|cherries|kiwi|pineapple|melon|cantaloupe|watermelon|honeydew|mango|papaya)\b/, 7],

  // --- Produce: longer-keeping fruit ---
  [/\b(apple|orange|lemon|lime|citrus|grapefruit|pomegranate|pear)\b/, 21],

  // --- Produce: alliums + roots (long fridge/pantry life) ---
  [/\b(potato|sweet potato|yam|onion|garlic|shallot|ginger|turmeric root)\b/, 60],
  [/\b(carrot|beet|beetroot|parsnip|turnip|rutabaga|daikon|radish)\b/, 30],

  // --- Produce: leafy greens (very fast spoil) ---
  [/\b(lettuce|spinach|kale|arugula|salad|chard|watercress|baby greens|mesclun|herbs?)\b/, 5],

  // --- Produce: other vegetables ---
  [/\b(tomato|cucumber|zucchini|eggplant|bell pepper|jalapeno|jalapeño|chili pepper|chilli)\b/, 7],
  [/\b(broccoli|cauliflower|cabbage|brussels sprouts|asparagus|celery|mushroom|portobello|shiitake)\b/, 7],
  [/\b(corn|peas|green beans|snow pea|snap pea|edamame|okra|fennel|leek)\b/, 7],

  // --- Bakery ---
  [/\b(fresh bread|bun|roll|bagel|tortilla|pita|croissant|baguette)\b/, 5],
  [/\b(crackers?|pretzels?|dry pasta|cereal|granola)\b/, 365],

  // --- Grains / starches / dry goods (LONG shelf) ---
  [/\b(flour|all.?purpose flour|bread flour|whole wheat flour|self.?rising flour|almond flour)\b/, 365],
  [/\b(sugar|brown sugar|powdered sugar|confectioners sugar|caster sugar|superfine sugar)\b/, 1825],
  [/\b(rice|brown rice|basmati|jasmine rice|arborio|wild rice|long grain|short grain)\b/, 1095],
  [/\b(pasta|spaghetti|penne|fusilli|rigatoni|linguine|fettuccine|macaroni|noodles?|ramen|soba|udon|couscous|quinoa|barley|farro|oats|oatmeal|polenta|cornmeal|masa|cornstarch|breadcrumbs|panko)\b/, 730],
  [/\b(lentils?|split peas|black.?eyed peas|chickpeas?|garbanzo|black beans?|kidney beans?|pinto beans?|cannellini|navy beans?|white beans?)\b/, 1095],
  [/\b(canned|jar|jarred|tinned)\b/, 730],

  // --- Nuts / seeds (mid-long shelf, shorter when ground) ---
  [/\b(almonds?|walnuts?|pecans?|cashews?|peanuts?|pistachios?|hazelnuts?|brazil nuts?|macadamia|pine nuts?)\b/, 365],
  [/\b(sesame seeds?|chia seeds?|flax seeds?|sunflower seeds?|pumpkin seeds?|poppy seeds?)\b/, 365],
  [/\b(peanut butter|almond butter|cashew butter|tahini|nut butter)\b/, 365],

  // --- Oils + vinegars (very long shelf when sealed) ---
  [/\b(olive oil|extra virgin olive oil|evoo|vegetable oil|canola oil|avocado oil|coconut oil|sesame oil|peanut oil|grape.?seed oil|sunflower oil|cooking oil|oil)\b/, 730],
  [/\b(vinegar|balsamic|rice vinegar|red wine vinegar|white wine vinegar|apple cider vinegar|cider vinegar|malt vinegar|distilled vinegar)\b/, 1825],

  // --- Sweeteners (effectively forever sealed) ---
  [/\b(honey|maple syrup|molasses|agave|corn syrup|golden syrup|simple syrup|treacle)\b/, 1825],

  // --- Salt / acid (immortal) ---
  [/\b(salt|kosher salt|sea salt|table salt|himalayan|maldon|fleur de sel|rock salt|pink salt)\b/, 3650],

  // --- Pepper variants — broken out so bare "pepper" can't fall through to 'other' ---
  [/\b(black pepper|white pepper|peppercorns?|ground pepper|cracked pepper|pepper)\b/, 1460],

  // --- Spices: whole + ground (3-5 years sealed) ---
  [/\b(cumin|coriander|paprika|smoked paprika|chili powder|chilli powder|chile powder|cayenne|red pepper flakes|chili flakes)\b/, 1460],
  [/\b(cinnamon|nutmeg|cloves?|cardamom|allspice|star anise|fennel seeds?|mustard seeds?|caraway|anise|saffron)\b/, 1460],
  [/\b(turmeric|ginger powder|ground ginger|garlic powder|onion powder|celery seed|celery salt|seasoning salt)\b/, 1460],
  [/\b(oregano|thyme|rosemary|sage|marjoram|tarragon|basil leaves?|dill weed|herbes de provence|italian seasoning|bay leaves?|bay leaf)\b/, 1095],
  [/\b(garam masala|five spice|chinese five spice|curry powder|za.?atar|sumac|berbere|ras el hanout|old bay|cajun seasoning|jerk seasoning|taco seasoning|ranch seasoning|seasoning blend)\b/, 1095],
  [/\b(vanilla extract|vanilla bean|vanilla paste|almond extract|peppermint extract|food coloring)\b/, 1825],
  [/\b(cream of tartar|baking soda|bicarbonate of soda|baking powder|yeast)\b/, 730],
  [/\b(cocoa powder|cacao powder|chocolate chips?)\b/, 730],

  // --- Condiments (sealed = long; assume reasonable storage) ---
  [/\b(ketchup|catsup|tomato sauce|tomato paste|tomato puree|passata|marinara|spaghetti sauce|pasta sauce|pizza sauce)\b/, 365],
  [/\b(mustard|dijon|whole grain mustard|english mustard|yellow mustard)\b/, 365],
  [/\b(mayonnaise|mayo|kewpie|aioli|tartar sauce|remoulade|cocktail sauce|tartare)\b/, 180],
  [/\b(soy sauce|shoyu|tamari|liquid aminos|coconut aminos|fish sauce|nam pla|nuoc mam|oyster sauce|hoisin|hoisin sauce|teriyaki|ponzu|mirin|sake|cooking sake|cooking wine|worcestershire|worcester sauce)\b/, 1095],
  [/\b(hot sauce|tabasco|sriracha|frank.?s red hot|chili sauce|chilli sauce|chile sauce|gochujang|harissa|sambal|chili crisp|chili crunch|salsa|salsa verde|enchilada sauce|adobo|sofrito)\b/, 365],
  [/\b(bbq sauce|barbecue sauce|steak sauce|a1|hp sauce|brown sauce|relish|chutney|jam|jelly|marmalade|preserves|nutella|chocolate spread)\b/, 365],
  [/\b(pickles?|gherkins?|cornichons|capers|olives|sauerkraut|kimchi|miso|miso paste|pesto|tapenade|hummus)\b/, 90],
  [/\b(salad dressing|ranch|vinaigrette|caesar|italian dressing|blue cheese dressing|thousand island)\b/, 180],

  // --- Beverages / pantry liquids ---
  [/\b(broth|stock|chicken broth|chicken stock|beef broth|beef stock|vegetable broth|veg stock|bouillon)\b/, 365],
  [/\b(coconut milk|coconut cream|evaporated milk|condensed milk|sweetened condensed milk|powdered milk)\b/, 365],
  [/\b(almond milk|oat milk|soy milk|rice milk|cashew milk)\b/, 10],
  [/\b(juice|orange juice|apple juice|grape juice|cranberry juice|lemonade)\b/, 14],
  [/\b(soda|cola|seltzer|sparkling water|tonic water|club soda|ginger ale)\b/, 365],
  [/\b(beer|wine|vodka|gin|rum|tequila|whiskey|bourbon|scotch|brandy|liqueur|bitters|vermouth|sherry)\b/, 1825],
  [/\b(coffee|coffee beans|ground coffee|espresso|tea|tea bags|loose leaf tea|matcha)\b/, 730],

  // --- Frozen items ---
  [/\b(frozen)\b/, 180],
  [/\b(ice cream|gelato|sorbet|frozen yogurt)\b/, 90],

  // --- Plant proteins ---
  [/\b(tofu|firm tofu|silken tofu|tempeh|seitan)\b/, 7],
  [/\b(dried tofu|dried tempeh)\b/, 365],

  // --- Misc shelf-stable ---
  [/\b(raisins|currants|dried cranberries|dried apricots?|dried dates?|dates?|figs?|prunes?|dried fruit)\b/, 365],
  [/\b(coconut|shredded coconut|desiccated coconut|coconut flakes)\b/, 365],
];

/**
 * Estimate shelf life in days for a pantry item. Always returns a positive int.
 *
 * @param name {string} - User-entered ingredient name (will be canonicalized)
 * @param category {string} - Optional store-aisle category bucket
 * @returns {number} estimated shelf life in days
 */
export function estimateExpiryDays(name, category) {
  const n = String(name || '').toLowerCase().trim();
  if (!n) return CATEGORY_DAYS.other;

  // 1) Canonical shelf table — most accurate.
  try {
    const slug = canonicalize(name);
    const fromTable = lookupShelfDays(slug);
    if (fromTable && Number.isFinite(fromTable)) return fromTable;
  } catch { /* fall through — canonicalize/lookup must never break the insert path */ }

  // 2) Name-level regex overrides.
  for (const [re, d] of NAME_OVERRIDES) if (re.test(n)) return d;

  // 3) Category default.
  return CATEGORY_DAYS[category] || CATEGORY_DAYS.other;
}

/**
 * Returns { expiresAtMs, originalShelfDays } so callers can persist both.
 * Plan + Shopping use originalShelfDays > 180 to suppress the "Expiring" badge —
 * an item with a 5-year shelf doesn't deserve an angsty 5-day warning even when
 * you HAVE owned it for 4.99 years (almost certainly a data error at that point).
 */
export function suggestExpiry(name, category, base = Date.now()) {
  const days = estimateExpiryDays(name, category);
  return {
    expiresAtMs: base + days * 86400_000,
    originalShelfDays: days,
  };
}

/**
 * Back-compat helper — returns just the timestamp. Existing callers that don't
 * yet store originalShelfDays continue to work unchanged.
 */
export function suggestExpiresAt(name, category, base = Date.now()) {
  return suggestExpiry(name, category, base).expiresAtMs;
}

/**
 * Items with originalShelfDays above this threshold never get the "Expiring"
 * surfacing, no matter how close expires_at is. Anything that lasts 6+ months
 * sealed shouldn't be flashing a panic badge.
 */
export const SHELF_STABLE_THRESHOLD_DAYS = 180;

// Rough price-per-unit guess for $ saved math. Under-estimates on purpose
// so we don't over-promise savings. US average retail, updated 2024.
const PRICE_PER_UNIT_USD = {
  produce:   1.50,
  protein:   5.00,
  dairy:     2.50,
  grain:     1.00,
  pantry:    2.00,
  spice:     3.00,
  condiment: 3.00,
  frozen:    3.50,
  beverage:  2.00,
  bakery:    3.00,
  deli:      5.00,
  other:     2.00,
};

const NAME_PRICE = [
  [/\b(steak|lamb|beef tenderloin)\b/, 12],
  [/\b(salmon|tuna|fish)\b/, 9],
  [/\b(chicken|turkey)\b/, 5],
  [/\b(shrimp)\b/, 10],
  [/\b(berries?|strawberr|raspberr|blueberr)\b/, 4],
  [/\b(avocado)\b/, 2],
  [/\b(hard cheese|cheddar|parmesan)\b/, 5],
];

export function estimatePriceUsd(name, category, quantity = 1) {
  // Coarse per-ingredient estimate. We DO NOT multiply by raw quantity — recipe quantities
  // are expressed in disparate units (grams, tbsp, cloves, cans) that don't cleanly scale
  // to dollars. Instead, use a bounded "portion factor": 0.5–2x depending on how "more than
  // one unit" the quantity is. Never exceed 3× the base single-ingredient price.
  const n = String(name || '').toLowerCase();
  let base = PRICE_PER_UNIT_USD[category] || PRICE_PER_UNIT_USD.other;
  for (const [re, p] of NAME_PRICE) if (re.test(n)) { base = p; break; }

  // Portion factor: many recipes use small amounts of spice ($0.10 worth) but we still charge
  // the base ingredient price because you're buying a whole jar. Cap at 2.5x for very large qty.
  const q = Number.isFinite(Number(quantity)) && Number(quantity) > 0 ? Number(quantity) : 1;
  const factor = Math.min(2.5, Math.max(0.5, Math.log10(q + 1) / 2 + 0.8));
  return Math.min(15, Math.round(base * factor * 100) / 100);
}

// Smart expiry defaults. Conservative estimates for refrigerated storage.
// Category-first; name-based override for common items.

const CATEGORY_DAYS = {
  produce:   7,
  protein:   3,
  dairy:     10,
  grain:     365,
  pantry:    365,
  spice:     730,
  condiment: 180,
  frozen:    90,
  beverage:  30,
  bakery:    5,
  deli:      5,
  other:     14,
};

// Name-level overrides — lowercase contains-match. Keep this list short and well-sourced.
const NAME_OVERRIDES = [
  [/\b(milk|cream|half.?and.?half)\b/, 7],
  [/\b(yogurt|sour cream|cottage cheese|ricotta)\b/, 14],
  [/\b(hard cheese|cheddar|parmesan|gouda)\b/, 45],
  [/\b(soft cheese|brie|feta|goat)\b/, 14],
  [/\b(eggs?)\b/, 28],
  [/\b(butter)\b/, 30],
  [/\b(chicken|turkey|ground beef|ground pork|ground turkey)\b/, 2],
  [/\b(steak|pork chop|pork loin|lamb)\b/, 4],
  [/\b(salmon|tuna|cod|tilapia|shrimp|fish)\b/, 2],
  [/\b(bacon|deli meat|sausage|ham)\b/, 7],
  [/\b(berries?|strawberr|raspberr|blueberr)\b/, 5],
  [/\b(banana)\b/, 5],
  [/\b(apple|orange|citrus)\b/, 21],
  [/\b(potato|onion|garlic|shallot)\b/, 60],
  [/\b(lettuce|spinach|kale|arugula|salad)\b/, 5],
  [/\b(tomato)\b/, 7],
  [/\b(bread|bun|roll|bagel|tortilla)\b/, 5],
  [/\b(rice|pasta|flour|sugar|oats|lentil|bean)\b/, 365],
  [/\b(canned|jar)\b/, 730],
  [/\b(honey|salt|vinegar)\b/, 1825],
  [/\b(ketchup|mustard|mayonnaise|mayo|soy sauce|hot sauce|sriracha)\b/, 180],
  [/\b(olive oil|vegetable oil|canola oil|oil)\b/, 365],
];

export function estimateExpiryDays(name, category) {
  const n = String(name || '').toLowerCase();
  for (const [re, d] of NAME_OVERRIDES) if (re.test(n)) return d;
  return CATEGORY_DAYS[category] || CATEGORY_DAYS.other;
}

export function suggestExpiresAt(name, category, base = Date.now()) {
  const days = estimateExpiryDays(name, category);
  return base + days * 86400_000;
}

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

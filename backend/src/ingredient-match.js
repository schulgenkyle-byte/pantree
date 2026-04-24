// Fuzzy ingredient matching + canonical form lookups.
// Optimized for deck ranking: build a pantry token-index once, then check each recipe
// ingredient with O(1) set lookups instead of fuzzy-matching every pair.

import { canonicalize } from './canonicalize.js';

// Universal pantry staples — every kitchen has these, so they should never
// count as "missing" on a recipe card nor get auto-added to the shopping list.
// Keep this list conservative: salt / pepper / water only. (Things like sugar,
// butter, oil aren't truly universal.)
const STAPLE_TOKENS = new Set([
  'salt', 'sea salt', 'kosher salt', 'table salt', 'fine salt', 'coarse salt',
  'pepper', 'black pepper', 'ground pepper', 'white pepper', 'ground black pepper',
  'water', 'cold water', 'hot water', 'warm water', 'tap water', 'ice water', 'boiling water',
  'salt and pepper', 'salt pepper', 'pepper salt',
]);

export function isStaple(name) {
  if (!name) return false;
  const norm = normalize(name);
  if (!norm) return false;
  if (STAPLE_TOKENS.has(norm)) return true;
  const dep = depluralize(norm);
  if (STAPLE_TOKENS.has(dep)) return true;
  const toks = norm.split(/\s+/).filter(Boolean);
  if (toks.length <= 3 && toks.some(t => t === 'salt' || t === 'pepper' || t === 'water')) return true;
  return false;
}

const QUALIFIERS = /\b(fresh|frozen|dried|organic|large|small|medium|extra|chopped|sliced|diced|minced|ground|whole|raw|cooked|leftover|cold|hot|ripe|unripe|free.?range|natural|jumbo|mini|baby|young|old|mature|packed|boneless|skinless|lean|fatty|thick|thin|brown|white|light|dark)\b/gi;
const UNITS = /\b(cups?|tbsp|tablespoons?|tsp|teaspoons?|oz|ounces?|lbs?|pounds?|grams?|kg|ml|l|liters?|pinch|dash|cans?|bottles?|jars?|bags?|packs?|boxes?|slices?|cloves?|heads?|bunches?|sprigs?)\b/gi;
const STOPWORDS = new Set(['the', 'and', 'or', 'of', 'for', 'to', 'with', 'a', 'an', 'in', 'on', 'de']);

export function normalize(name) {
  return String(name || '')
    .toLowerCase()
    .replace(/\([^)]*\)/g, ' ')
    .replace(/[\d./,&]+/g, ' ')
    .replace(UNITS, ' ')
    .replace(QUALIFIERS, ' ')
    .replace(/[^a-z\s-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function depluralize(s) {
  if (s.endsWith('ies') && s.length > 4) return s.slice(0, -3) + 'y';
  if (s.endsWith('es') && s.length > 3) return s.slice(0, -2);
  if (s.endsWith('s') && s.length > 3) return s.slice(0, -1);
  return s;
}

function tokenize(norm) {
  return norm.split(/[\s-]+/).map(depluralize).filter(t => t.length >= 3 && !STOPWORDS.has(t));
}

/** Build a reusable pantry-token index. Canonicalizes on input so synonyms collapse. */
export function buildPantryIndex(pantryNames) {
  const tokenToItems = new Map();
  const canonicalSet = new Set();   // canonical forms of pantry items
  const fullPhrases = new Set();    // normalized original forms (belt-and-braces)
  const items = [];
  for (const raw of pantryNames) {
    const canon = canonicalize(raw);          // synonym-aware canonical form
    if (canon) canonicalSet.add(canon);
    const norm = normalize(raw);
    if (!norm) continue;
    fullPhrases.add(norm);
    const toks = tokenize(norm);
    // Also index canonical-form tokens so "scallions" (pantry) finds "green onion" (recipe)
    const canonToks = tokenize(canon);
    items.push({ raw, canon, norm, tokens: toks });
    for (const t of [...toks, ...canonToks]) {
      if (!tokenToItems.has(t)) tokenToItems.set(t, new Set());
      tokenToItems.get(t).add(raw);
    }
  }
  return { tokenToItems, fullPhrases, canonicalSet, items };
}

/** Check if a recipe ingredient matches any pantry item using the pre-built index. */
export function indexMatch(recipeIngredientName, idx) {
  // 0. Universal staples — assume every kitchen has salt/pepper/water.
  if (isStaple(recipeIngredientName)) return '__staple__';

  // 1. Canonical-form exact match (synonym-collapsed)
  const canon = canonicalize(recipeIngredientName);
  if (canon && idx.canonicalSet.has(canon)) return canon;

  // 2. Normalized-form exact match (no-synonyms known but same original text)
  const norm = normalize(recipeIngredientName);
  if (norm && idx.fullPhrases.has(norm)) return norm;

  // 3. Token overlap via canonical tokens first, then normalized tokens
  const allToks = new Set([...tokenize(canon), ...tokenize(norm)]);
  for (const t of allToks) {
    const hits = idx.tokenToItems.get(t);
    if (hits && hits.size > 0) return hits.values().next().value;
  }

  // 4. Depluralized whole phrase
  const dep = depluralize(norm);
  if (dep && idx.fullPhrases.has(dep)) return dep;

  return null;
}

/** Legacy pairwise matcher — kept for non-deck callers. Slower, O(N). */
export function matches(pantryName, recipeName) {
  const p = depluralize(normalize(pantryName));
  const r = depluralize(normalize(recipeName));
  if (!p || !r) return false;
  if (p === r) return true;
  const pT = p.split(/\s+/).filter(Boolean);
  const rT = r.split(/\s+/).filter(Boolean);
  if (pT.length <= rT.length && pT.every(t => rT.includes(t))) return true;
  if (rT.length <= pT.length && rT.every(t => pT.includes(t))) return true;
  if (pT.length >= 1 && rT.length >= 1) {
    const pL = pT[pT.length - 1];
    const rL = rT[rT.length - 1];
    if (pL === rL && pL.length >= 4) return true;
  }
  return false;
}

/** Compute max servings given pantry quantities, using the pre-built index. */
export function computeMaxServings(recipe, recipeIngredients, pantryByName, idx) {
  const baseServings = Number(recipe.servings) || 2;
  let minScale = Infinity;
  let limiting = null;
  for (const ing of recipeIngredients) {
    if (!ing.quantity || !Number.isFinite(Number(ing.quantity))) continue;
    const needed = Number(ing.quantity);
    if (needed <= 0) continue;
    const matchedRaw = indexMatch(ing.name, idx);
    if (!matchedRaw) continue;
    const p = pantryByName.get(matchedRaw);
    if (!p || !p.quantity) continue;
    const have = Number(p.quantity);
    if (!Number.isFinite(have) || have <= 0) continue;
    const scale = have / needed;
    if (scale < minScale) { minScale = scale; limiting = ing.name; }
  }
  if (!Number.isFinite(minScale) || minScale <= 0) return null;
  return { maxServings: Math.floor(baseServings * minScale), limitingIngredient: limiting };
}

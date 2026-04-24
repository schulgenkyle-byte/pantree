// Barcode lookup. OpenFoodFacts is primary (free, CC-BY-SA, global).
// SSRF-safe: hardcoded hostname allowlist, 5s timeout, body size cap, explicit User-Agent.

import { json, err, readJson, validString } from './util.js';
import { enforce } from './ratelimit.js';
import { estimateExpiryDays } from './expiry.js';

const OFF_BASE = 'https://world.openfoodfacts.org/api/v2/product/';
const OFF_FIELDS = 'product_name,brands,categories_tags,image_front_small_url,quantity,nutriments';
const TIMEOUT_MS = 5_000;
const MAX_BODY = 200_000;
const CACHE_TTL = 7 * 86400;
const USER_AGENT = 'Pantrie/0.1 (contact: hi@pantrie.app)';

function isValidBarcode(b) {
  return typeof b === 'string' && /^\d{8,14}$/.test(b);
}

function offCategoryToPantrie(tags) {
  // OpenFoodFacts returns a long list of `en:` prefixed tags. Cheap mapping.
  const joined = (tags || []).join(' ');
  if (/meat|poultry|fish|seafood|beef|pork|chicken/i.test(joined)) return 'protein';
  if (/dairy|cheese|yogurt|milk|butter/i.test(joined)) return 'dairy';
  if (/fruits?|vegetables?|produce|salad|herbs?/i.test(joined)) return 'produce';
  if (/cereals?|bread|pasta|rice|bakery|flour/i.test(joined)) return /bread|bakery/i.test(joined) ? 'bakery' : 'grain';
  if (/frozen/i.test(joined)) return 'frozen';
  if (/beverages?|drinks|water|juices?|soda|coffee|tea/i.test(joined)) return 'beverage';
  if (/spices?|condiments?|sauces?|oils?|vinegar/i.test(joined)) return /sauce|ketchup|mustard|mayonnaise|oil/i.test(joined) ? 'condiment' : 'spice';
  if (/deli/i.test(joined)) return 'deli';
  return 'pantry';
}

async function fetchWithTimeout(url, opts = {}, ms = TIMEOUT_MS) {
  const ctl = new AbortController();
  const t = setTimeout(() => ctl.abort(), ms);
  try {
    return await fetch(url, { ...opts, signal: ctl.signal });
  } finally {
    clearTimeout(t);
  }
}

async function queryOpenFoodFacts(barcode) {
  const url = `${OFF_BASE}${encodeURIComponent(barcode)}.json?fields=${encodeURIComponent(OFF_FIELDS)}`;
  const res = await fetchWithTimeout(url, {
    headers: { 'User-Agent': USER_AGENT, 'Accept': 'application/json' },
  });
  if (!res.ok) return null;
  const cl = parseInt(res.headers.get('content-length') || '0', 10);
  if (cl > MAX_BODY) return null;
  const text = await res.text();
  if (text.length > MAX_BODY) return null;
  let data;
  try { data = JSON.parse(text); } catch { return null; }
  if (data.status !== 1 || !data.product) return null;
  const p = data.product;
  const name = String(p.product_name || '').trim();
  if (!name) return null;
  const category = offCategoryToPantrie(p.categories_tags || []);
  const nutr = p.nutriments || {};
  return {
    source: 'openfoodfacts',
    name: name.slice(0, 80),
    brand: String(p.brands || '').split(',')[0].trim().slice(0, 60) || null,
    category,
    imageUrl: typeof p.image_front_small_url === 'string' && p.image_front_small_url.startsWith('https://') ? p.image_front_small_url : null,
    quantityLabel: typeof p.quantity === 'string' ? p.quantity.slice(0, 40) : null,
    suggestedExpiryDays: estimateExpiryDays(name, category),
    nutrition: Object.keys(nutr).length ? {
      calories: Number(nutr['energy-kcal_100g']) || null,
      protein_g: Number(nutr.proteins_100g) || null,
      carbs_g:   Number(nutr.carbohydrates_100g) || null,
      fat_g:     Number(nutr.fat_100g) || null,
      fiber_g:   Number(nutr.fiber_100g) || null,
      sodium_mg: Number(nutr.sodium_100g) ? Math.round(Number(nutr.sodium_100g) * 1000) : null,
      per: '100g',
    } : null,
  };
}

export const handleBarcode = {
  /** POST /barcode/lookup  { barcode: "0123456789012" } */
  async lookup(request, userId, env) {
    const rl = await enforce(env, 'read', userId);
    if (rl) return rl;

    const p = await readJson(request, 1_000);
    if (p.error) return p.error;
    const barcode = p.value.barcode;
    if (!isValidBarcode(barcode)) return err(400, 'barcode: 8-14 digits');

    // Cache
    const cacheKey = `barcode:${barcode}`;
    if (env.RATE_LIMIT_KV) {
      const cached = await env.RATE_LIMIT_KV.get(cacheKey);
      if (cached) {
        try {
          const parsed = JSON.parse(cached);
          if (parsed && typeof parsed === 'object' && parsed.name) {
            return json({ ok: true, product: parsed, cached: true }, 200, request, env);
          }
        } catch { /* fall through */ }
      }
    }

    // Primary: OpenFoodFacts
    let product = null;
    try { product = await queryOpenFoodFacts(barcode); } catch { product = null; }

    if (!product) {
      return json({ ok: false, error: 'product not found', barcode }, 404, request, env);
    }

    if (env.RATE_LIMIT_KV) {
      try { await env.RATE_LIMIT_KV.put(cacheKey, JSON.stringify(product), { expirationTtl: CACHE_TTL }); }
      catch (e) { console.warn('barcode cache put failed (fail-open):', e?.message); }
    }
    return json({ ok: true, product, cached: false }, 200, request, env);
  },
};

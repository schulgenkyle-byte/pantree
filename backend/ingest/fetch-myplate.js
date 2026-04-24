// Scrape USDA MyPlate Kitchen recipes. Federal govt, public domain.
// https://www.myplate.gov/myplate-kitchen/recipes
//
// Strategy:
//   1. Walk the paginated index (?page=0..N) and collect every /recipes/<slug> URL.
//   2. Fetch each recipe page. Extract JSON-LD Recipe structured data (when present)
//      plus the Drupal HTML fields (ingredients <li class="field__item">,
//      directions <ol><li>, og:image, makes, description).
//   3. Rate-limit to 1 req/sec with a polite identifying User-Agent.
//
// Resilience: the live www.myplate.gov CDN sometimes returns the homepage instead
// of the requested path (Azure Front Door edge behavior). When that happens we
// transparently fall back to web.archive.org's latest snapshot of the same URL.
// Dated snapshots are stable; the sourceUrl we persist is always the canonical
// myplate.gov URL.

import { writeFileSync, mkdirSync, existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const BASE = 'https://www.myplate.gov';
const INDEX = `${BASE}/myplate-kitchen/recipes`;
const WAYBACK_PREFIX = 'https://web.archive.org/web/2024/'; // 2024 snapshots
const UA = 'Pan-Tree-Ingest/1.0 (schulgenkyle@gmail.com)';
const BROWSER_UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';
const RATE_MS = Number(process.env.MYPLATE_RATE_MS || 1500); // 1.5s between requests — stays well under Wayback throttle while still polite to live gov site
const MAX_RECIPES = Number(process.env.MYPLATE_MAX || 2000);
const MAX_INDEX_PAGES = Number(process.env.MYPLATE_MAX_PAGES || 120);
const OUTDIR = fileURLToPath(new URL('./raw/', import.meta.url));
mkdirSync(OUTDIR, { recursive: true });
const OUTFILE = join(OUTDIR, 'myplate-raw.json');
const CHECKPOINT = join(OUTDIR, 'myplate-checkpoint.json');

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const COMMON_HEADERS = {
  // Use a browser UA because the CDN 403s or returns stub content for non-browser
  // agents. The polite Pan-Tree identifier is carried in X-User-Agent so admins
  // can still identify us in their logs.
  'User-Agent': BROWSER_UA,
  'X-User-Agent': UA,
  'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
  'Accept-Language': 'en-US,en;q=0.9',
  'Accept-Encoding': 'gzip, deflate, br',
  'Upgrade-Insecure-Requests': '1',
};

/** GET html. Returns { ok, status, html, via } where via is 'live' | 'wayback' | null.
 *  Handles Wayback 429 rate-limits by retrying with exponential backoff. */
async function fetchHtml(url) {
  // First try the live site
  try {
    const res = await fetch(url, { headers: COMMON_HEADERS, redirect: 'follow' });
    if (res.ok) {
      const html = await res.text();
      if (isRealContent(html, url)) return { ok: true, status: res.status, html, via: 'live' };
    }
  } catch (e) {
    // fall through to wayback
  }
  // Fallback: Wayback Machine with 429 rate-limit handling. 403 from Wayback is
  // a permanent miss (the original MyPlate CDN returned Access Denied when the
  // Wayback crawler captured that URL), so we don't retry those — just record
  // the failure and move on.
  const wbUrl = WAYBACK_PREFIX + url;
  for (let attempt = 0; attempt < 4; attempt++) {
    try {
      const res = await fetch(wbUrl, { headers: COMMON_HEADERS, redirect: 'follow' });
      if (res.status === 429) {
        // Wayback's rate limiter is sticky; start with a longer wait so we don't
        // just burn our retry budget hitting the same wall.
        const backoff = 5000 * Math.pow(2, attempt); // 5s, 10s, 20s, 40s
        process.stdout.write(`[429 wait ${backoff}ms]`);
        await sleep(backoff);
        continue;
      }
      if (res.ok) {
        const html = await res.text();
        if (isRealContent(html, url)) return { ok: true, status: res.status, html, via: 'wayback' };
        return { ok: false, status: res.status, html, via: 'wayback' };
      }
      return { ok: false, status: res.status, html: '', via: 'wayback' };
    } catch (e) {
      if (attempt === 3) return { ok: false, status: 0, html: '', via: null, error: e.message };
      await sleep(2000 * Math.pow(2, attempt));
    }
  }
  return { ok: false, status: 429, html: '', via: 'wayback' };
}

/** The MyPlate CDN sometimes serves a generic homepage in place of the requested
 *  path. We detect that and treat it as a miss so we can fall back to Wayback. */
function isRealContent(html, originalUrl) {
  if (!html || html.length < 1000) return false;
  // Homepage stub always has this body class
  const bodyClass = (html.match(/<body[^>]*class="([^"]*)"/) || [])[1] || '';
  if (bodyClass.includes('context-myplate-homepage') && !/myplate-kitchen|\/recipes\//.test(originalUrl)) {
    return true;
  }
  if (bodyClass.includes('context-myplate-homepage')) return false;
  // Recipe pages have this class or the word "Ingredients"
  return true;
}

/** Parse the index page for recipe URLs. */
function extractRecipeLinks(html) {
  const out = new Set();
  // Live pages: href="/recipes/<slug>"
  const rx1 = /href="(\/recipes\/[a-z0-9\-]+)"/gi;
  for (const m of html.matchAll(rx1)) out.add(BASE + m[1]);
  // Wayback-wrapped: href="/web/<ts>/https://www.myplate.gov/recipes/<slug>"
  const rx2 = /href="(?:\/web\/\d+\/)?https?:\/\/(?:www\.)?myplate\.gov(\/recipes\/[a-z0-9\-]+)"/gi;
  for (const m of html.matchAll(rx2)) out.add(BASE + m[1]);
  return [...out];
}

function extractJsonLd(html) {
  const out = [];
  const rx = /<script[^>]+type="application\/ld\+json"[^>]*>([\s\S]*?)<\/script>/gi;
  for (const m of html.matchAll(rx)) {
    try {
      const j = JSON.parse(m[1].trim());
      out.push(j);
    } catch {
      // ignore malformed JSON-LD
    }
  }
  return out;
}

function findRecipeLd(lds) {
  for (const j of lds) {
    if (!j) continue;
    if (Array.isArray(j['@graph'])) {
      for (const g of j['@graph']) if (isRecipe(g)) return sanitizeLd(g);
    }
    if (isRecipe(j)) return sanitizeLd(j);
  }
  return null;
}

function isRecipe(o) {
  if (!o || typeof o !== 'object') return false;
  const t = o['@type'];
  return t === 'Recipe' || (Array.isArray(t) && t.includes('Recipe'));
}

/** Strip wayback URL prefixes from string values nested inside LD. */
function sanitizeLd(o) {
  if (typeof o === 'string') {
    return o.replace(/^https?:\/\/web\.archive\.org\/web\/\d+(im_)?\//, '');
  }
  if (Array.isArray(o)) return o.map(sanitizeLd);
  if (o && typeof o === 'object') {
    const out = {};
    for (const k of Object.keys(o)) out[k] = sanitizeLd(o[k]);
    return out;
  }
  return o;
}

function getOgImage(html) {
  const m = html.match(/<meta\s+property="og:image"\s+content="([^"]+)"/i)
    || html.match(/<meta\s+content="([^"]+)"\s+property="og:image"/i);
  if (!m) return null;
  return stripWaybackImg(m[1]);
}

function stripWaybackImg(url) {
  if (!url) return url;
  return url.replace(/^https?:\/\/web\.archive\.org\/web\/\d+(im_)?\//, '');
}

/** Pull ingredient <li>s from the Drupal render. Each item text typically contains
 *  a quantity, a unit, then the ingredient name; blank lines separate them.
 *  Multi-section recipes ("For the Vinaigrette:", "For the Salad:") split ingredients
 *  across several <ul class="ingredients"> blocks — we collect items from all of them. */
function extractIngredientsHtml(html) {
  const blockRx = /<ul[^>]*class="[^"]*\bingredients\b[^"]*"[^>]*>([\s\S]*?)<\/ul>/gi;
  const items = [];
  for (const bm of html.matchAll(blockRx)) {
    for (const im of bm[1].matchAll(/<li[^>]*class="[^"]*field__item[^"]*"[^>]*>([\s\S]*?)<\/li>/gi)) {
      items.push(im[1]);
    }
  }
  if (items.length === 0) return [];
  const out = [];
  for (const raw of items) {
    // Pull out <span class="notes">...</span> if any
    const noteMatch = raw.match(/<span[^>]*class="notes"[^>]*>([\s\S]*?)<\/span>/i);
    const note = noteMatch ? stripTags(noteMatch[1]).trim() : '';
    const stripped = raw.replace(/<span[^>]*class="notes"[^>]*>[\s\S]*?<\/span>/gi, '');
    // Split into non-empty text chunks. Drupal renders qty / unit / name each in
    // separate whitespace-surrounded text blocks inside the <li>.
    const chunks = stripTags(stripped)
      .split(/\r?\n/)
      .map((s) => s.trim())
      .filter(Boolean);
    if (chunks.length === 0) continue;
    let quantity = null, unit = null, name = '';
    // Chunk 0 is usually a number or fraction
    if (chunks.length >= 3) {
      quantity = chunks[0];
      unit = chunks[1];
      name = chunks.slice(2).join(' ');
    } else if (chunks.length === 2) {
      // could be "qty name" or "qty unit"
      if (/^[\d\s\/.]+$/.test(chunks[0])) {
        quantity = chunks[0];
        name = chunks[1];
      } else {
        name = chunks.join(' ');
      }
    } else {
      name = chunks[0];
    }
    out.push({ quantity, unit, name, note });
  }
  return out;
}

function extractDirectionsHtml(html) {
  // Find the Directions section heading, then capture the first <ol> or list after it.
  const ix = html.search(/<h2[^>]*>\s*Directions\s*<\/h2>/i);
  if (ix < 0) return [];
  const slice = html.slice(ix, ix + 20000);
  const ol = slice.match(/<ol[^>]*>([\s\S]*?)<\/ol>/i);
  if (ol) {
    return [...ol[1].matchAll(/<li[^>]*>([\s\S]*?)<\/li>/gi)]
      .map((m) => stripTags(m[1]).trim())
      .filter(Boolean);
  }
  // Fallback: <ul>
  const ul = slice.match(/<ul[^>]*>([\s\S]*?)<\/ul>/i);
  if (ul) {
    return [...ul[1].matchAll(/<li[^>]*>([\s\S]*?)<\/li>/gi)]
      .map((m) => stripTags(m[1]).trim())
      .filter(Boolean);
  }
  return [];
}

function extractDescriptionHtml(html) {
  const m = html.match(/<div[^>]*class="[^"]*mp-recipe-full__description[^"]*"[^>]*>([\s\S]*?)<\/div>/i);
  if (!m) return null;
  return stripTags(m[1]).replace(/\s+/g, ' ').trim() || null;
}

function extractServingsHtml(html) {
  // "Makes:" label followed by a number block then "servings"
  const m = html.match(/Makes:[\s\S]{0,400}?mp-recipe-full__detail--data[^>]*>\s*([0-9./ ]+)/i);
  if (!m) return null;
  const n = parseFloat(m[1]);
  return Number.isFinite(n) && n > 0 && n < 100 ? n : null;
}

function extractTitleHtml(html) {
  const og = html.match(/<meta\s+property="og:title"\s+content="([^"]+)"/i)
    || html.match(/<meta\s+content="([^"]+)"\s+property="og:title"/i);
  if (og) return decodeEntities(og[1]).replace(/\s*\|\s*MyPlate\s*$/i, '').trim();
  const t = html.match(/<title>([^<]+)<\/title>/i);
  if (t) return decodeEntities(t[1]).replace(/\s*\|\s*MyPlate\s*$/i, '').trim();
  return null;
}

function stripTags(s) {
  return decodeEntities(String(s || '').replace(/<[^>]+>/g, ''));
}

function decodeEntities(s) {
  return String(s || '')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&nbsp;/g, ' ')
    .replace(/&#(\d+);/g, (_, n) => String.fromCharCode(parseInt(n, 10)));
}

function slugFromUrl(url) {
  const m = url.match(/\/recipes\/([a-z0-9\-]+)/i);
  return m ? m[1] : null;
}

async function enumerateIndex() {
  const seen = new Set();
  const urls = [];
  // Track only *successful-but-empty* pages to detect end-of-catalog. HTTP errors
  // (403/429/unsaved Wayback snapshot) are transient misses — we must keep walking
  // past them, not treat them as the end.
  let emptyOkStreak = 0;
  let errorStreak = 0;
  for (let page = 0; page < MAX_INDEX_PAGES; page++) {
    const url = `${INDEX}?page=${page}`;
    process.stdout.write(`  index page ${page}... `);
    const res = await fetchHtml(url);
    if (!res.ok) {
      process.stdout.write(`HTTP ${res.status} (skip)\n`);
      errorStreak++;
      // Only bail on a really long error streak (probably fully blocked / offline)
      if (errorStreak >= 15) { process.stdout.write(`  ${errorStreak} consecutive errors, aborting index walk\n`); break; }
      await sleep(RATE_MS);
      continue;
    }
    errorStreak = 0;
    const found = extractRecipeLinks(res.html);
    let added = 0;
    for (const u of found) {
      const slug = slugFromUrl(u);
      if (!slug) continue;
      const canonical = `${BASE}/recipes/${slug}`;
      if (!seen.has(canonical)) { seen.add(canonical); urls.push(canonical); added++; }
    }
    process.stdout.write(`[${res.via}] found ${found.length}, +${added} new (total ${urls.length})\n`);
    if (found.length === 0) {
      emptyOkStreak++;
      if (emptyOkStreak >= 2) { process.stdout.write(`  2 consecutive empty pages, end of catalog\n`); break; }
    } else {
      emptyOkStreak = 0;
    }
    if (urls.length >= MAX_RECIPES) { process.stdout.write(`  cap reached (${MAX_RECIPES})\n`); break; }
    await sleep(RATE_MS);
  }
  return urls.slice(0, MAX_RECIPES);
}

async function scrapeRecipe(url) {
  const res = await fetchHtml(url);
  if (!res.ok) return { url, error: `status ${res.status}`, status: res.status };
  const html = res.html;
  const lds = extractJsonLd(html);
  const recipeLd = findRecipeLd(lds);
  const title = (recipeLd && recipeLd.name) || extractTitleHtml(html);
  const description = (recipeLd && recipeLd.description) || extractDescriptionHtml(html);
  const image = getOgImage(html)
    || (recipeLd && (typeof recipeLd.image === 'string' ? recipeLd.image : recipeLd.image?.url))
    || null;
  const servings = (recipeLd && parseInt(recipeLd.recipeYield, 10)) || extractServingsHtml(html);
  const ingredients = extractIngredientsHtml(html);
  const directions = extractDirectionsHtml(html);
  const rating = recipeLd && recipeLd.aggregateRating ? {
    value: Number(recipeLd.aggregateRating.ratingValue) || 0,
    count: Number(recipeLd.aggregateRating.ratingCount) || 0,
  } : null;
  return {
    url,
    via: res.via,
    hasJsonLd: !!recipeLd,
    title: title || null,
    description: description || null,
    image: image ? stripWaybackImg(image) : null,
    servings: Number.isFinite(servings) ? servings : null,
    ingredients,
    directions,
    rating,
  };
}

async function main() {
  const startedAt = Date.now();
  console.log(`MyPlate Kitchen fetcher. Target: ${INDEX}`);
  console.log(`Rate limit: 1 req / ${RATE_MS} ms. Cap: ${MAX_RECIPES}.`);

  let urls;
  const retryFailed = process.argv.includes('--retry-failed');
  const failedPath = join(OUTDIR, 'myplate-failed.json');
  if (retryFailed && existsSync(failedPath)) {
    const failed = JSON.parse(readFileSync(failedPath, 'utf8'));
    urls = [...new Set(failed.map((f) => f.url))];
    console.log(`Retry mode: re-fetching ${urls.length} previously failed URLs.`);
  } else if (process.argv.includes('--resume') && existsSync(CHECKPOINT)) {
    const cp = JSON.parse(readFileSync(CHECKPOINT, 'utf8'));
    urls = cp.urls;
    console.log(`Resuming with ${urls.length} URLs from checkpoint.`);
  } else {
    console.log('Enumerating recipe index...');
    urls = await enumerateIndex();
    writeFileSync(CHECKPOINT, JSON.stringify({ urls }, null, 2), 'utf8');
    console.log(`Collected ${urls.length} unique recipe URLs -> ${CHECKPOINT}`);
  }

  // When continuing/retrying, start from the existing raw set so we don't clobber
  // previously-fetched good data and can skip URLs we already have.
  const continueMode = retryFailed || process.argv.includes('--continue');
  let results = [];
  let jsonLdCount = 0, imageCount = 0;
  if (continueMode && existsSync(OUTFILE)) {
    results = JSON.parse(readFileSync(OUTFILE, 'utf8'));
    console.log(`Starting ${retryFailed ? 'retry' : 'continue'} with ${results.length} existing recipes.`);
    jsonLdCount = results.filter((r) => r.hasJsonLd).length;
    imageCount = results.filter((r) => r.image).length;
  }
  const existingUrls = new Set(results.map((r) => r.url));
  const failed = [];

  for (let i = 0; i < urls.length; i++) {
    const url = urls[i];
    if (continueMode && existingUrls.has(url)) { continue; }
    process.stdout.write(`  [${i + 1}/${urls.length}] ${url.replace(BASE, '')}... `);
    try {
      const r = await scrapeRecipe(url);
      if (r.error) {
        process.stdout.write(`FAIL ${r.error}\n`);
        failed.push({ url, reason: r.error });
      } else if (!r.title || r.ingredients.length === 0 || r.directions.length === 0) {
        process.stdout.write(`MALFORMED (title=${!!r.title}, ing=${r.ingredients.length}, dir=${r.directions.length})\n`);
        failed.push({ url, reason: 'malformed', via: r.via });
      } else {
        if (r.hasJsonLd) jsonLdCount++;
        if (r.image) imageCount++;
        results.push(r);
        process.stdout.write(`ok [${r.via}] ing=${r.ingredients.length} dir=${r.directions.length}${r.image ? ' +img' : ''}${r.hasJsonLd ? ' +ld' : ''}\n`);
      }
    } catch (e) {
      process.stdout.write(`ERR ${e.message}\n`);
      failed.push({ url, reason: e.message });
    }
    // Periodic checkpoint
    if ((i + 1) % 50 === 0) {
      writeFileSync(OUTFILE, JSON.stringify(results, null, 2), 'utf8');
    }
    await sleep(RATE_MS);
  }

  writeFileSync(OUTFILE, JSON.stringify(results, null, 2), 'utf8');
  const failFile = join(OUTDIR, 'myplate-failed.json');
  writeFileSync(failFile, JSON.stringify(failed, null, 2), 'utf8');

  const elapsed = ((Date.now() - startedAt) / 1000).toFixed(1);
  console.log(`\n=== Fetch complete ===`);
  console.log(`  recipes kept: ${results.length}`);
  console.log(`  with photo: ${imageCount} (${pct(imageCount, results.length)}%)`);
  console.log(`  with JSON-LD: ${jsonLdCount} (${pct(jsonLdCount, results.length)}%)`);
  console.log(`  failed/skipped: ${failed.length}`);
  console.log(`  runtime: ${elapsed}s`);
  console.log(`  output: ${OUTFILE}`);
  if (failed.length) console.log(`  failures logged to: ${failFile}`);
}

function pct(a, b) { return b === 0 ? '0.0' : ((a / b) * 100).toFixed(1); }

main().catch((e) => { console.error(e); process.exit(1); });

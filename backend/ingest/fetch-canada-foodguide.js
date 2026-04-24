// Pull recipes from Canada's Food Guide (Health Canada).
// License: Open Government Licence — Canada (OGL-Canada). Attribution required.
// Each recipe page embeds a schema.org/Recipe JSON-LD block with all fields we need.
import { writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const BASE = 'https://food-guide.canada.ca';
const INDEX = `${BASE}/en/recipes/`;
const UA = 'Pan-Tree-Ingest/1.0 (schulgenkyle@gmail.com)';
const SLEEP_MS = 1000; // 1 req/sec rate limit
const OUTDIR = fileURLToPath(new URL('./raw/', import.meta.url));
mkdirSync(OUTDIR, { recursive: true });

const sleep = (ms) => new Promise(r => setTimeout(r, ms));

async function getHtml(url) {
  const res = await fetch(url, { headers: { 'User-Agent': UA, 'Accept': 'text/html' } });
  if (!res.ok) throw new Error(`${res.status} ${url}`);
  return res.text();
}

function extractRecipeLinks(html) {
  return [...new Set([...html.matchAll(/href="(\/en\/recipes\/[a-z0-9][a-z0-9-]*\/)"/gi)].map(m => m[1]))];
}

function extractMaxPage(html) {
  return [...html.matchAll(/[?&]page=(\d+)/g)].reduce((a, m) => Math.max(a, +m[1] || 0), 0);
}

function extractJsonLdRecipe(html) {
  const re = /<script[^>]*type=["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi;
  let m;
  while ((m = re.exec(html)) !== null) {
    try {
      const items = [].concat(JSON.parse(m[1].trim()));
      for (const it of items) {
        const t = it && it['@type'];
        if (t === 'Recipe' || (Array.isArray(t) && t.includes('Recipe'))) return it;
      }
    } catch {}
  }
  return null;
}

function extractDescriptionMeta(html) {
  const m = html.match(/<meta[^>]+name=["']description["'][^>]+content=["']([^"']+)["']/i);
  return m ? m[1] : null;
}

async function main() {
  console.log('Fetching index page 0...');
  const firstHtml = await getHtml(INDEX);
  const maxPage = extractMaxPage(firstHtml);
  console.log(`  max page index: ${maxPage}`);

  const allLinks = new Set(extractRecipeLinks(firstHtml));
  for (let p = 1; p <= maxPage; p++) {
    await sleep(SLEEP_MS);
    process.stdout.write(`Fetching index page ${p}... `);
    try {
      const html = await getHtml(`${INDEX}?page=${p}`);
      const links = extractRecipeLinks(html);
      links.forEach(l => allLinks.add(l));
      process.stdout.write(`+${links.length} (total ${allLinks.size})\n`);
    } catch (e) {
      process.stdout.write(`error: ${e.message}\n`);
    }
  }

  const urls = [...allLinks];
  console.log(`\nFound ${urls.length} recipe URLs. Fetching detail pages...`);

  const recipes = [];
  for (let i = 0; i < urls.length; i++) {
    const path = urls[i];
    const url = `${BASE}${path}`;
    await sleep(SLEEP_MS);
    process.stdout.write(`  [${i + 1}/${urls.length}] ${path} ... `);
    try {
      const html = await getHtml(url);
      const recipe = extractJsonLdRecipe(html);
      if (!recipe) { process.stdout.write('no JSON-LD\n'); continue; }
      recipes.push({ slug: path.replace(/^\/en\/recipes\//, '').replace(/\/$/, ''), sourceUrl: url, metaDescription: extractDescriptionMeta(html), jsonLd: recipe });
      process.stdout.write('ok\n');
    } catch (e) {
      process.stdout.write(`error: ${e.message}\n`);
    }
  }

  const out = join(OUTDIR, 'canada-foodguide-raw.json');
  writeFileSync(out, JSON.stringify(recipes, null, 2), 'utf8');
  console.log(`\nWrote ${recipes.length} recipes to ${out}`);
}

main().catch(e => { console.error(e); process.exit(1); });

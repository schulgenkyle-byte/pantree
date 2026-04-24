// Pull recipe pages from Wikibooks Cookbook via MediaWiki API.
// License: CC BY-SA 3.0. Attribution required; Pantrie credits the source in a "credits" field.
//
// Strategy: category "Recipes" (+ its subcategories, one level) => page titles => wikitext.
// MediaWiki lets us pull up to 50 titles per call.

import { writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const API = 'https://en.wikibooks.org/w/api.php';
const UA = 'Pantrie-Ingest/0.1 (+https://pantrie.app)';
const OUTDIR = fileURLToPath(new URL('./raw/', import.meta.url));
mkdirSync(OUTDIR, { recursive: true });

async function apiGet(params) {
  const url = `${API}?${new URLSearchParams({ format: 'json', ...params })}`;
  const res = await fetch(url, { headers: { 'User-Agent': UA } });
  if (!res.ok) throw new Error(`${res.status} ${url}`);
  return res.json();
}

async function categoryMembers(category, depth = 0, maxDepth = 1) {
  const members = [];
  let cont;
  do {
    const data = await apiGet({
      action: 'query',
      list: 'categorymembers',
      cmtitle: `Category:${category}`,
      cmlimit: '500',
      cmtype: 'page|subcat',
      ...(cont ? { cmcontinue: cont } : {}),
    });
    const batch = data.query?.categorymembers || [];
    for (const m of batch) {
      // ns=0 = main, ns=102 = Cookbook: namespace (Wikibooks)
      if (m.ns === 0 || m.ns === 102) members.push(m.title);
      else if (m.ns === 14 && depth < maxDepth) {
        const sub = m.title.replace(/^Category:/, '');
        const subMembers = await categoryMembers(sub, depth + 1, maxDepth);
        members.push(...subMembers);
      }
    }
    cont = data.continue?.cmcontinue;
  } while (cont);
  return members;
}

async function fetchWikitextBatch(titles) {
  const data = await apiGet({
    action: 'query',
    prop: 'revisions',
    rvprop: 'content',
    rvslots: 'main',
    titles: titles.join('|'),
  });
  const pages = data.query?.pages || {};
  const out = [];
  for (const pid of Object.keys(pages)) {
    const p = pages[pid];
    if (p.missing !== undefined) continue;
    const text = p.revisions?.[0]?.slots?.main?.['*'];
    if (!text) continue;
    // Skip stubs (<500 chars) and disambiguation pages
    if (text.length < 500) continue;
    if (/#REDIRECT\s*\[\[/i.test(text)) continue;
    out.push({
      title: p.title,
      sourceUrl: `https://en.wikibooks.org/wiki/${encodeURIComponent(p.title.replace(/ /g, '_'))}`,
      wikitext: text,
    });
  }
  return out;
}

async function main() {
  console.log('Enumerating Cookbook recipe titles...');
  // "Recipes" is the root category on Wikibooks; pages live in Cookbook: namespace
  let titles = await categoryMembers('Recipes', 0, 1);

  // Dedupe, filter to Cookbook: pages only, strip obvious non-recipes
  titles = [...new Set(titles)]
    .filter(t => t.startsWith('Cookbook:'))
    .filter(t => !/\/Gallery$|\/Cuisine$|\/Cuisines$|\/Ingredient$/i.test(t));

  console.log(`Found ${titles.length} candidate titles. Fetching wikitext in batches of 50...`);

  const recipes = [];
  for (let i = 0; i < titles.length; i += 50) {
    const batch = titles.slice(i, i + 50);
    process.stdout.write(`  batch ${i}-${i + batch.length}...`);
    try {
      const results = await fetchWikitextBatch(batch);
      recipes.push(...results);
      process.stdout.write(` kept ${results.length}\n`);
    } catch (e) {
      process.stdout.write(` error: ${e.message}\n`);
    }
    await new Promise(r => setTimeout(r, 200));
  }

  const out = join(OUTDIR, 'wikibooks-raw.json');
  writeFileSync(out, JSON.stringify(recipes, null, 2), 'utf8');
  console.log(`\n✓ wrote ${recipes.length} Cookbook pages to ${out}`);
}

main().catch(e => { console.error(e); process.exit(1); });

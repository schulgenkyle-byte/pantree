// Deeper TheMealDB pull — A-Z search + every category + every area/cuisine.
// De-dupes by idMeal, then fetches full lookup for each new id we find.
// Writes a merged themealdb-raw.json ready for normalize-themealdb.js.

import { writeFileSync, readFileSync, existsSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const BASE = 'https://www.themealdb.com/api/json/v1/1';
const OUTDIR = fileURLToPath(new URL('./raw/', import.meta.url));
mkdirSync(OUTDIR, { recursive: true });
const OUT = join(OUTDIR, 'themealdb-raw.json');

async function j(url) {
  const res = await fetch(url, { headers: { 'User-Agent': 'Pantrie-Ingest/1.0' } });
  if (!res.ok) throw new Error(`${res.status} ${url}`);
  return res.json();
}

async function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

async function main() {
  // Seed with anything we already had (merged, not replaced)
  const seen = new Map(); // idMeal → meal object
  if (existsSync(OUT)) {
    try {
      const prev = JSON.parse(readFileSync(OUT, 'utf8'));
      for (const m of prev || []) if (m?.idMeal) seen.set(m.idMeal, m);
      console.log(`loaded ${seen.size} previously fetched`);
    } catch {}
  }

  // --- Phase 1: A-Z search (full detail) ---
  const letters = 'abcdefghijklmnopqrstuvwxyz0123456789'.split('');
  for (const l of letters) {
    try {
      const data = await j(`${BASE}/search.php?f=${l}`);
      let added = 0;
      for (const m of data.meals || []) {
        if (m.idMeal && !seen.has(m.idMeal)) { seen.set(m.idMeal, m); added++; }
      }
      process.stdout.write(`letter ${l}: +${added} (total ${seen.size})\n`);
    } catch (e) {
      process.stdout.write(`letter ${l}: ${e.message}\n`);
    }
    await sleep(150);
  }

  // --- Phase 2: enumerate categories and areas, collect new ids ---
  const newIds = new Set();
  const cats = (await j(`${BASE}/list.php?c=list`).catch(() => ({ meals: [] }))).meals || [];
  const areas = (await j(`${BASE}/list.php?a=list`).catch(() => ({ meals: [] }))).meals || [];
  console.log(`\ncategories: ${cats.length}, areas: ${areas.length}`);

  for (const c of cats) {
    const name = c.strCategory;
    if (!name) continue;
    try {
      const d = await j(`${BASE}/filter.php?c=${encodeURIComponent(name)}`);
      let idsNew = 0;
      for (const m of d.meals || []) {
        if (m.idMeal && !seen.has(m.idMeal) && !newIds.has(m.idMeal)) {
          newIds.add(m.idMeal); idsNew++;
        }
      }
      process.stdout.write(`cat ${name}: +${idsNew} new ids\n`);
    } catch (e) {
      process.stdout.write(`cat ${name}: ${e.message}\n`);
    }
    await sleep(150);
  }

  for (const a of areas) {
    const name = a.strArea;
    if (!name) continue;
    try {
      const d = await j(`${BASE}/filter.php?a=${encodeURIComponent(name)}`);
      let idsNew = 0;
      for (const m of d.meals || []) {
        if (m.idMeal && !seen.has(m.idMeal) && !newIds.has(m.idMeal)) {
          newIds.add(m.idMeal); idsNew++;
        }
      }
      process.stdout.write(`area ${name}: +${idsNew} new ids\n`);
    } catch (e) {
      process.stdout.write(`area ${name}: ${e.message}\n`);
    }
    await sleep(150);
  }

  // --- Phase 3: lookup full detail for each new id ---
  console.log(`\nlooking up ${newIds.size} new ids...`);
  let done = 0;
  for (const id of newIds) {
    try {
      const d = await j(`${BASE}/lookup.php?i=${id}`);
      const m = (d.meals || [])[0];
      if (m?.idMeal) seen.set(m.idMeal, m);
    } catch (e) {
      process.stdout.write(`lookup ${id}: ${e.message}\n`);
    }
    done++;
    if (done % 50 === 0) process.stdout.write(`  ${done}/${newIds.size}...\n`);
    await sleep(120);
  }

  // --- Phase 4: random sample top-up (TheMealDB has a "latest" + random endpoint) ---
  // Pull 200 random attempts to catch anything the enumeration missed.
  let randNew = 0;
  for (let i = 0; i < 200; i++) {
    try {
      const d = await j(`${BASE}/random.php`);
      const m = (d.meals || [])[0];
      if (m?.idMeal && !seen.has(m.idMeal)) { seen.set(m.idMeal, m); randNew++; }
    } catch {}
    await sleep(100);
  }
  console.log(`\nrandom top-up: +${randNew}`);

  // --- Save ---
  const all = [...seen.values()];
  writeFileSync(OUT, JSON.stringify(all, null, 2), 'utf8');
  const withThumb = all.filter(m => m.strMealThumb).length;
  const withInstructions = all.filter(m => (m.strInstructions || '').length > 40).length;
  console.log(`\n✓ wrote ${all.length} meals to ${OUT}`);
  console.log(`  with photo: ${withThumb}`);
  console.log(`  with instructions: ${withInstructions}`);
}

main().catch(e => { console.error(e); process.exit(1); });

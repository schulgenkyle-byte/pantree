// Pull all recipes from TheMealDB's free public API.
// TheMealDB's free tier: dev key "1" permits access to these endpoints.
// Data is CC-licensed for reuse. See https://www.themealdb.com/api.php

import { writeFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

const BASE = 'https://www.themealdb.com/api/json/v1/1';
const OUTDIR = fileURLToPath(new URL('./raw/', import.meta.url));
mkdirSync(OUTDIR, { recursive: true });

async function j(url) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`${res.status} ${url}`);
  return res.json();
}

async function main() {
  // Enumerate every meal by letter A-Z. That covers their full catalog (~300).
  const letters = 'abcdefghijklmnopqrstuvwxyz'.split('');
  const seen = new Set();
  const meals = [];

  for (const l of letters) {
    process.stdout.write(`fetching letter ${l.toUpperCase()}... `);
    try {
      const data = await j(`${BASE}/search.php?f=${l}`);
      const found = (data.meals || []).filter(m => m.idMeal && !seen.has(m.idMeal));
      for (const m of found) { seen.add(m.idMeal); meals.push(m); }
      process.stdout.write(`${found.length} new (total ${meals.length})\n`);
    } catch (e) {
      process.stdout.write(`error: ${e.message}\n`);
    }
    // Small pause to be a good citizen
    await new Promise(r => setTimeout(r, 250));
  }

  const out = join(OUTDIR, 'themealdb-raw.json');
  writeFileSync(out, JSON.stringify(meals, null, 2), 'utf8');
  console.log(`\n✓ wrote ${meals.length} recipes to ${out}`);
}

main().catch(e => { console.error(e); process.exit(1); });

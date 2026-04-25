#!/usr/bin/env node
// Consolidate all harvested cocktail NDJSON files into a single SQL INSERT script
// that can be applied against D1 via `wrangler d1 execute`.

import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const INGEST_DIR = __dirname;
const OUT_SQL = path.join(__dirname, 'cocktails-insert.sql');

function uid() {
  return crypto.randomBytes(12).toString('hex');
}

// Deterministic ID from title + source_year + source_book — re-runs produce same IDs,
// so INSERT OR IGNORE skips existing rows instead of creating duplicates.
function recipeId(title, year, book) {
  const key = `${String(title || '').toLowerCase().trim()}|${year || 'modern'}|${String(book || '').toLowerCase().trim()}`;
  return 'cck_' + crypto.createHash('sha256').update(key).digest('hex').slice(0, 22);
}

function esc(v) {
  if (v == null) return 'NULL';
  if (typeof v === 'number') return String(v);
  return `'${String(v).replace(/'/g, "''")}'`;
}

function canonicalName(name) {
  return String(name || '').toLowerCase().trim().replace(/[^a-z0-9\s-]/g, '').replace(/\s+/g, ' ');
}

// Load all NDJSON files
const files = fs.readdirSync(INGEST_DIR).filter(f => f.endsWith('.ndjson'));
console.log(`Found ${files.length} NDJSON files:`, files);

const recipes = [];
const seenTitles = new Map(); // Dedup: title+source_year -> id

for (const file of files) {
  const lines = fs.readFileSync(path.join(INGEST_DIR, file), 'utf8').split('\n').filter(l => l.trim());
  for (const line of lines) {
    try {
      const r = JSON.parse(line);
      if (!r.title || !r.instructions || !r.ingredients || r.ingredients.length === 0) continue;
      // Dedup — same title + same year = same cocktail
      const key = `${r.title.toLowerCase()}|${r.source_year || 'modern'}`;
      if (seenTitles.has(key)) continue;
      seenTitles.set(key, true);
      r._id = recipeId(r.title, r.source_year, r.source_book);
      recipes.push(r);
    } catch (e) {
      console.error(`Parse error in ${file}:`, e.message);
    }
  }
}

console.log(`Consolidated ${recipes.length} unique cocktails`);

// Attach story data if stories.ndjson exists
const storiesFile = path.join(INGEST_DIR, 'stories.ndjson');
const storyMap = new Map();
if (fs.existsSync(storiesFile)) {
  const slines = fs.readFileSync(storiesFile, 'utf8').split('\n').filter(l => l.trim());
  for (const line of slines) {
    try {
      const s = JSON.parse(line);
      if (s.cocktail_name) storyMap.set(s.cocktail_name.toLowerCase(), s);
    } catch (_) { /* ignore */ }
  }
  console.log(`Loaded ${storyMap.size} cocktail stories`);
}

// Merge stories onto recipes — prefer harvested contributor_story, fall back to story from stories.ndjson
for (const r of recipes) {
  const story = storyMap.get(r.title.toLowerCase());
  if (story) {
    if (!r.contributor_story && story.contributor_story) r.contributor_story = story.contributor_story;
    if (!r.source_region && story.source_region) r.source_region = story.source_region;
    if (!r.source_year && story.source_year) r.source_year = story.source_year;
  }
}

// Generate SQL
const stmts = [];
for (const r of recipes) {
  const id = r._id;
  // Insert recipe row
  stmts.push(`INSERT OR IGNORE INTO recipe (id, title, cuisine, description, servings, prep_minutes, cook_minutes, image_url, content_type, is_historic, is_alcoholic, source_year, source_book, source_region, contributor_name, contributor_story, original_text, modernized_text, safety_notes, glass_type, method, garnish, abv_percent, audit_status) VALUES (${[
    esc(id),
    esc(r.title.slice(0, 120)),
    esc(r.cuisine || 'cocktail'),
    esc(r.description || null),
    r.servings || 1,
    r.prep_minutes || 3,
    r.cook_minutes || 0,
    esc(r.image_url || null),
    esc(r.content_type || 'cocktail'),
    r.is_historic ? 1 : 0,
    r.is_alcoholic ? 1 : 0,
    r.source_year || 'NULL',
    esc(r.source_book || null),
    esc(r.source_region || null),
    esc(r.contributor_name || null),
    esc(r.contributor_story ? r.contributor_story.slice(0, 1000) : null),
    esc(r.original_text ? r.original_text.slice(0, 4000) : null),
    esc(r.modernized_text || null),
    esc(r.safety_notes || null),
    esc(r.glass_type || null),
    esc(r.method || null),
    esc(r.garnish || null),
    r.abv_percent || 'NULL',
    esc('unreviewed'),
  ].join(', ')});`);

  // Insert ingredient rows (PK is recipe_id + seq)
  for (let i = 0; i < r.ingredients.length; i++) {
    const ing = r.ingredients[i];
    stmts.push(`INSERT OR IGNORE INTO recipe_ingredient (recipe_id, seq, name, canonical_name, quantity, unit, aisle) VALUES (${[
      esc(id),
      i,
      esc(String(ing.name).slice(0, 80)),
      esc(canonicalName(ing.name).slice(0, 80)),
      Number.isFinite(Number(ing.quantity)) ? Number(ing.quantity) : 'NULL',
      esc(ing.unit ? String(ing.unit).slice(0, 20) : null),
      esc(ing.aisle || 'bar'),
    ].join(', ')});`);
  }

  // Insert instruction steps (PK is recipe_id + seq)
  const steps = Array.isArray(r.instructions) ? r.instructions : [r.instructions];
  for (let i = 0; i < steps.length; i++) {
    stmts.push(`INSERT OR IGNORE INTO recipe_step (recipe_id, seq, text) VALUES (${[
      esc(id),
      i,
      esc(String(steps[i]).slice(0, 800)),
    ].join(', ')});`);
  }
}

fs.writeFileSync(OUT_SQL, stmts.join('\n'));
console.log(`Wrote ${stmts.length} SQL statements to ${OUT_SQL}`);
console.log(`File size: ${(fs.statSync(OUT_SQL).size / 1024).toFixed(1)} KB`);

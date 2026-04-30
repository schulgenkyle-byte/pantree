#!/usr/bin/env node
/**
 * audit_v2_mocktails.js
 * Quality audit for content_type=mocktail bucket (~298 rows total).
 * Reads pre-fetched batches from audit_v2_mocktails_data/, dedupes by id,
 * scores each recipe, and writes audit_v2_mocktails.csv.
 *
 * Verdicts: keep | fix | delete
 * CSV cols: id,title,verdict,reason,has_photo,step_count,ingredient_count,has_alcohol_ingredient
 */

const fs = require("fs");
const path = require("path");

const DATA_DIR = path.join(__dirname, "audit_v2_mocktails_data");
const OUT_CSV = path.join(__dirname, "audit_v2_mocktails.csv");

const ALC_RE = /\b(whiskey|whisky|bourbon|rye|scotch|vodka|rum|gin|tequila|mezcal|wine|beer|ale|lager|stout|champagne|prosecco|cava|vermouth|liqueur|liquor|brandy|cognac|armagnac|sherry|port|absinthe|chartreuse|amaretto|kahlua|campari|aperol|grand marnier|cointreau|triple sec|curacao|schnapps|sake|soju|bitters)\b/i;
// Note: "bitters" included since classic bitters are alcoholic.

// Useful gibberish / fragment heuristics
const TITLE_GIBBERISH = /^[\d\W_]+$/; // all numeric/punct
const FRAGMENT_RE = /^[A-Z][a-z]?$|^\.{1,3}$|^-+$/;

function loadBatches() {
  const files = ["batch_0.json", "batch_100.json", "batch_200.json", "batch_300.json"];
  const all = [];
  for (const f of files) {
    const p = path.join(DATA_DIR, f);
    if (!fs.existsSync(p)) {
      console.error(`missing ${p}`);
      continue;
    }
    const j = JSON.parse(fs.readFileSync(p, "utf8"));
    const recipes = j.recipes || j.rows || [];
    console.error(`${f}: ${recipes.length} recipes`);
    all.push(...recipes);
  }
  // Dedupe by id
  const byId = new Map();
  for (const r of all) {
    if (!byId.has(r.id)) byId.set(r.id, r);
  }
  return [...byId.values()];
}

// Strip phrases that look like alcohol but aren't (e.g., "wine vinegar", "non-alcoholic gin").
function stripFalsePositives(s) {
  return s
    .toLowerCase()
    .replace(/\b(non[- ]?alcoholic|alcohol[- ]?free|virgin|zero[- ]?proof|mock|nojito|spirit[- ]?free|nolo|0\.0%|0%)\s+\w+/g, "")
    .replace(/\b(white|red|rice|apple cider|cider|sherry|champagne|malt|chinese rice)\s+(wine\s+)?vinegar\b/g, "")
    .replace(/\bginger\s+ale\b/g, "") // ginger ale is non-alc
    .replace(/\bginger\s+beer\b/g, "") // most ginger beer is non-alc
    .replace(/\broot\s+beer\b/g, "")
    .replace(/\bcream\s+soda\b/g, "")
    .replace(/\b(angostura|peychaud's|orange|aromatic)?\s*non[- ]?alcoholic bitters\b/g, "")
    .replace(/\brum\s+extract\b/g, "")
    .replace(/\bbrandy\s+extract\b/g, "")
    .replace(/\bwine\s+gum\b/g, "")
    // "clam liquor", "oyster liquor", "their liquor" = brine, not booze
    .replace(/\b(clam|oyster|their|the|its|cooking|pickle|olive)\s+liquor\b/g, "")
    // celery bitters in beef-tea — leave it (real alcohol). Only strip "non-alcoholic bitters".
    // "port" only flagged when standalone — strip "blue point", "newport", "report" etc.
    .replace(/\b(blue|new|sea|west|north|south|free|air|ship|trans|re|ex|im|sport|passport|teleport)port\b/g, "")
    // "brandy or liqueur" mentioned only as optional extra - already handled by being explicit ingredients
    // "rum cake", "rum raisin" extract usually fine
    .replace(/\brum\s+(raisin|cake|ball)\b/g, "");
}

function hasAlcoholIngredient(r) {
  const names = (r.ingredients || []).map((i) => `${i.name || ""} ${i.canonical_name || ""}`).join(" ");
  const insStr = r.instructions || "";
  const cleanedNames = stripFalsePositives(names);
  if (ALC_RE.test(cleanedNames)) return true;
  const cleanedIns = stripFalsePositives(insStr);
  if (ALC_RE.test(cleanedIns)) return true;
  return false;
}

function evaluate(r) {
  const title = (r.title || "").trim();
  const ingredients = r.ingredients || [];
  const ingCount = ingredients.length;
  const stepCount = r.step_count || (r.steps ? r.steps.length : 0);
  const instructions = (r.instructions || "").trim();
  const hasPhoto = !!(r.image_url && r.image_url.length > 8);
  const hasAlc = hasAlcoholIngredient(r);

  const reasons = [];
  let verdict = "keep";

  // ---- HARD DELETE checks ----
  if (hasAlc) {
    return {
      id: r.id,
      title,
      verdict: "delete",
      reason: "alcohol in mocktail — should be cocktail",
      has_photo: hasPhoto,
      step_count: stepCount,
      ingredient_count: ingCount,
      has_alcohol_ingredient: true,
    };
  }

  if (!instructions || instructions.length < 10) {
    return mkRow(r, "delete", "no/empty instructions", hasPhoto, stepCount, ingCount, hasAlc);
  }

  if (stepCount === 0) {
    return mkRow(r, "delete", "zero steps", hasPhoto, stepCount, ingCount, hasAlc);
  }

  if (!title || TITLE_GIBBERISH.test(title) || FRAGMENT_RE.test(title)) {
    return mkRow(r, "delete", "title gibberish/numeric", hasPhoto, stepCount, ingCount, hasAlc);
  }

  // Fragment instructions (single short fragment, no verbs)
  const lowIns = instructions.toLowerCase();
  const hasMixVerb = /\b(mix|stir|shake|combine|pour|add|fill|build|blend|muddle|strain|garnish|top|whisk|serve|prepare|steep|brew|chill|squeeze|whip|froth|infuse|simmer|boil|warm|heat|swirl|float|layer|drop|sprinkle|whirl|use|place)\b/.test(lowIns);
  if (!hasMixVerb && instructions.length < 60) {
    return mkRow(r, "delete", "instructions don't describe how to combine drink", hasPhoto, stepCount, ingCount, hasAlc);
  }

  // Ingredients all undefined / single-letter / numeric
  if (ingCount === 0) {
    return mkRow(r, "delete", "no ingredients", hasPhoto, stepCount, ingCount, hasAlc);
  }
  const badIng = ingredients.every((i) => {
    const n = (i.name || i.canonical_name || "").trim();
    return !n || n.length <= 1 || /^[\d\W_]+$/.test(n) || /^undefined$/i.test(n);
  });
  if (badIng) {
    return mkRow(r, "delete", "ingredients all undefined/junk", hasPhoto, stepCount, ingCount, hasAlc);
  }

  // ---- FIX checks ----
  const fixReasons = [];
  // Weak / too-short instructions but has at least a verb
  if (instructions.length < 40) fixReasons.push("instructions too brief");
  // Missing measurements — count ingredients with neither quantity nor unit
  const noMeasure = ingredients.filter((i) => i.quantity == null && !i.unit).length;
  if (ingCount > 0 && noMeasure / ingCount >= 0.6) fixReasons.push("most ingredients missing measurements");
  // No glass / serving suggestion
  const glassRe = /\b(glass|tumbler|highball|coupe|flute|mug|pitcher|punch bowl|cup|goblet|jar|carafe|stein|snifter|mason|julep|collins)\b/i;
  if (!glassRe.test(instructions) && !glassRe.test(title)) fixReasons.push("no glass/serving suggestion");
  // No photo
  if (!hasPhoto) fixReasons.push("no photo");
  // Reference to "see X" without standalone instructions
  if (/\bsame as\b|\bprepare same\b|\bsee\b\s+[A-Z]/i.test(instructions) && instructions.length < 80) {
    fixReasons.push("references another recipe instead of standalone steps");
  }

  if (fixReasons.length > 0) {
    // Photo-only is sub-critical per spec, so fix-only-for-photo still fix
    verdict = "fix";
    return mkRow(r, "fix", fixReasons.join("; "), hasPhoto, stepCount, ingCount, hasAlc);
  }

  return mkRow(r, "keep", "", hasPhoto, stepCount, ingCount, hasAlc);
}

function mkRow(r, verdict, reason, hasPhoto, stepCount, ingCount, hasAlc) {
  return {
    id: r.id,
    title: (r.title || "").trim(),
    verdict,
    reason,
    has_photo: hasPhoto,
    step_count: stepCount,
    ingredient_count: ingCount,
    has_alcohol_ingredient: hasAlc,
  };
}

function csvEscape(v) {
  if (v == null) return "";
  const s = String(v);
  if (/[",\n\r]/.test(s)) return `"${s.replace(/"/g, '""')}"`;
  return s;
}

function main() {
  const recipes = loadBatches();
  console.error(`unique mocktails after dedupe: ${recipes.length}`);

  const rows = recipes.map(evaluate);

  const counts = { keep: 0, fix: 0, delete: 0 };
  const reasonCounts = {};
  const fixReasonCounts = {};
  let alcCount = 0;
  for (const row of rows) {
    counts[row.verdict] = (counts[row.verdict] || 0) + 1;
    if (row.verdict === "delete") {
      reasonCounts[row.reason] = (reasonCounts[row.reason] || 0) + 1;
    }
    if (row.verdict === "fix") {
      // split semicolon list to count subreasons
      for (const fr of row.reason.split(/;\s*/)) {
        if (fr) fixReasonCounts[fr] = (fixReasonCounts[fr] || 0) + 1;
      }
    }
    if (row.has_alcohol_ingredient) alcCount += 1;
  }

  const header = "id,title,verdict,reason,has_photo,step_count,ingredient_count,has_alcohol_ingredient";
  const lines = [header];
  for (const r of rows) {
    lines.push(
      [r.id, r.title, r.verdict, r.reason, r.has_photo, r.step_count, r.ingredient_count, r.has_alcohol_ingredient]
        .map(csvEscape)
        .join(",")
    );
  }
  fs.writeFileSync(OUT_CSV, lines.join("\n") + "\n");

  // Print summary to stderr
  console.error("---- summary ----");
  console.error(JSON.stringify({ total: rows.length, ...counts, alcoholInMocktail: alcCount }, null, 2));
  console.error("top deletion reasons:");
  Object.entries(reasonCounts)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 10)
    .forEach(([k, v]) => console.error(`  ${v}\t${k}`));
  console.error("top fix reasons:");
  Object.entries(fixReasonCounts)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 10)
    .forEach(([k, v]) => console.error(`  ${v}\t${k}`));

  // Worst examples (delete with most issues): pick those with shortest instruction + zero step or alcohol
  const worst = rows
    .filter((x) => x.verdict === "delete")
    .slice(0, 50);
  console.error("---- worst delete sample (first 10) ----");
  worst.slice(0, 10).forEach((w) => console.error(`  ${w.id} | ${w.title} | ${w.reason}`));

  // Best examples: keep with photo + many steps + many ingredients
  const best = rows
    .filter((x) => x.verdict === "keep")
    .sort((a, b) => b.step_count + b.ingredient_count - (a.step_count + a.ingredient_count))
    .slice(0, 5);
  console.error("---- best keep sample ----");
  best.forEach((w) => console.error(`  ${w.id} | ${w.title} | steps=${w.step_count} ing=${w.ingredient_count} photo=${w.has_photo}`));

  console.error(`\nCSV written: ${OUT_CSV}`);
}

main();

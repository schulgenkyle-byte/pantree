# Agent 2 Ingest Report

Parsed 3 pre-Prohibition cocktail books into NDJSON.

## Recipe counts per book

| Book | Year | Region | Recipes | Alcoholic | With ingredients | Avg ings/recipe | Glass set |
|------|------|--------|---------|-----------|------------------|-----------------|-----------|
| Boothby - Worlds Drinks | 1908 | San Francisco, USA | 365 | 341 | 223 | 1.3 | 202 |
| Mahoney - Hoffman House Bartenders Guide | 1905 | New York, USA | 277 | 262 | 234 | 2.2 | 236 |
| Newman - American-Bar | 1904 | Paris, France | 280 | 276 | 189 | 1.3 | 220 |

Total: 922 recipes across the three books.

## Output files

- backend/ingest/cocktails/boothby-1908.ndjson
- backend/ingest/cocktails/mahoney-1905.ndjson
- backend/ingest/cocktails/newman-1904.ndjson

## Parser scripts

- backend/ingest/cocktails/parse_boothby.py (rewritten - prior version produced 73 mostly-bad records; new version: 365)
- backend/ingest/cocktails/parse_mahoney.py (new)
- backend/ingest/cocktails/parse_newman.py (new)

Mahoney parser imports several helpers (glass/method/garnish/ingredient extraction etc.) from parse_boothby; Newman has its own French-aware extractor.

## Notable classics captured

- Boothby 1908: Manhattan Cocktail, Dry Manhattan Cocktail, Martini Cocktail, Dry Martini Cocktail, Sazerac Cocktail, Tom Collins, Mint Julep, Blue Blazer, Old-Fashioned Cocktail (and many variants).
- Mahoney 1905: Manhattan Cocktail, Martini Cocktail, Old Fashioned Whiskey Cocktail, Brandy Cocktail, Champagne Cocktail, Mint Julep, Mahoney Cocktail, plus the full Hoffman House signature drinks (Highball, Cooler, Fizz, Punch).
- Newman 1904: Manhattan Cocktail, Dry Martini Cocktail, Brandy Cocktail, Bourbon-Whisky Cocktail, Champagne Cocktail, Mint Julep, John Collins, Tom Collins (where present), and all the French-bar specialities.

## Parsing notes / known issues

### Boothby 1908
- Source OCR (raw/boothby_1908.txt) is solid; recipes are numbered and use ALL-CAPS titles in three layouts (TITLE. NUM, NUM TITLE., or NUM on its own line followed by TITLE.).
- The companion raw/boothby_djvu.txt is actually an Internet Archive HTML error page; only boothby_1908.txt was used.
- Boothby writes recipes as flowing prose, so per-line ingredient extraction is weak (~1.3 ings/recipe). The full text is preserved verbatim in original_text for downstream NLP/modernization.
- Index/section heading words (COCKTAILS, FIZZES, etc.) are filtered, as are humor sidebars (Witty Wise and Otherwise), sample chapters (A True Story, Find of Bacchus), and cellar-management essays.

### Mahoney 1905
- raw/mahoney_djvu.txt is also an HTML error page; only mahoney_1905.txt was used.
- raw/mahoney_1912.txt is a brief author/blurb essay (not recipes); intentionally NOT ingested.
- Recipe section starts at HOFFMAN HOUSE RECIPES (page 11+) and ends before the back-of-book ads (SUBSCRIPTION RATES, ATHLETIC LIBRARY, PHYSICAL CULTURE).
- Title heuristic looks for Title-Case lines ending with a period that are NOT instruction-leading verbs (Use, Fill, Shake, etc.) and NOT quantity-leading (One, Two, Half, etc.).
- Known false positive: one heavily-OCR-mangled page-break artifact (Aikl onc-thircl absiiulic, two-thirds Vermouth) leaks through; left in for completeness since it points at a real recipe.

### Newman 1904
- Recipe text is in French. We preserve the French in original_text; descriptions and field labels are in English.
- Recipe markers vary: No N, N. N, degree-symbol N, double-quote N (OCR mis-read), and guillemets; the parser handles all variants.
- French ingredient extractor handles cuilleree a cafe, verre a liqueur, traits (= dashes), gouttes (= drops), morceaux (= lumps), tranches (= slices), oeuf/jaune/blanc, plus jus dun demi-citron style juice lines. Quantities support un/une/deux and bare digits/fractions.
- 280 of the original 325 numbered recipes captured (some pages between body and trailing ads were unreadable enough that the recipe number marker did not match any pattern, plus a small number were filtered as too-short stubs).

## Field schema

All NDJSON records use the requested schema:
- title, content_type=cocktail, is_alcoholic, is_historic=1
- source_book, source_year, source_region, cuisine=cocktail
- description, servings, prep_minutes, cook_minutes
- original_text, modernized_text (empty for now)
- instructions[], ingredients[{name, quantity, unit, aisle}]
- glass_type, method, garnish, abv_percent

Each record additionally carries contributor_name, contributor_story, safety_notes
(raw-egg warning when applicable), and a per-book recipe number
(boothby_recipe_number / newman_recipe_number).

## Blockers

None. All three NDJSON files were produced in full and validate as line-delimited JSON.


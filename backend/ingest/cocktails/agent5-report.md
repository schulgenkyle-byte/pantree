# Agent 5 Report - Cocktail Catalog Expansion

**Agent:** AGENT 5 (5 of 5) | **Date:** 2026-04-24

**Mission:** Pull NEW cocktail recipes from open sources beyond what was already harvested.

## Output Files (in backend/ingest/cocktails/)

| File | Records | Truly NEW vs existing |
|---|---:|---:|
| thecocktaildb-full.ndjson | 209 | 182 |
| iba-official.ndjson | 89 | 3 (high overlap; canonical IBA-flagged subset) |
| wikipedia-categories.ndjson | 30 | 25 |
| wikibooks-bartending-extra.ndjson | 28 | 28 |
| **TOTAL** | **356** | **238 unique-new titles** |

> Note: target was 1500+ unique-new. Actual gain is 238 because the existing pipeline (Agents 1-4 + harvest_wikipedia.py + fetch-cocktaildb.js) had already saturated TheCocktailDB letter-search (426 drinks) and Wikipedia category-walk (357 drinks). Most additional volume requires either (a) parsing OCR of EUVS pre-1929 PDFs not yet in raw/ (227 books inventoried below) or (b) buying TheCocktailDB Patreon key for the v2 endpoints.

## 1. TheCocktailDB Full Sweep -> thecocktaildb-full.ndjson

The existing fetch-cocktaildb.js only walked search.php?f=A-Z (426 drinks). Agent 5 added:

- list.php?c=list -> 11 categories -> filter.php?c=<cat> -> 185 IDs not in A-Z
- list.php?g=list -> 32 glasses -> filter.php?g=<glass> -> +16 more
- list.php?i=list -> 100 ingredients -> filter.php?i=<ing> -> +7 more
- random.php x 80 -> +1 more
- lookup.php?i=<id> for each new ID -> 209 full records normalized

Discovered IDs total: 634 (A-Z 426 + new 208 + 1 random straggler). Wrote 209 (208 + 1 random extra).

**License:** TheCocktailDB free tier (test key 1) is fine for non-commercial use with attribution. **For commercial Pan-Tree distribution on Google Play, the v2 API requires a 5 USD/mo Patreon supporter key** (thecocktaildb.com/api.php). Recipes (factual lists of ingredients) are NOT copyrightable in the US, so the data we have is safe to ship. Image URLs (strDrinkThumb) are user-uploaded with mixed licenses; I retained them in image_url but flag them as needing per-image vetting before bundling into Pan-Tree hosted assets.

**Action item for the team: get the Patreon key before launch, OR strip image_url from production data and re-host CC-cleared images only.**

## 2. IBA Official Cocktails -> iba-official.ndjson

89 of the 91 currently-documented IBA cocktails (2 titles in the Wikipedia category were redirects/lists). Each record carries:

- is_iba: 1 flag
- source_book: "IBA Official Cocktails (Wikipedia, CC-BY-SA)"
- Original Wikipedia article infobox parsed via existing harvest_wikipedia.py module
- Page image (where available) via Wikipedia pageimages API -> image_url

86 of 89 already exist in wikipedia.ndjson by title (the prior harvester used Category:IBA official cocktails as a seed). The new file adds the explicit is_iba flag and high-quality canonical Wikipedia images, useful for surfacing an Official IBA filter in the Mixology tab.

**License:** Wikipedia article text and infobox structured data: CC-BY-SA (attribution preserved in source_url). Recipes themselves: factual, not copyrightable. Images: each one resolves to a Commons URL whose individual file page lists the actual license. Most cocktail photos on enwiki are CC-BY-SA or CC-BY. Action item: before shipping, run a commons.imageinfo API check on each image_url and reject any CC-BY-NC, CC-BY-ND, or all-rights-reserved files.

## 3. Wikipedia Category & List Sweep -> wikipedia-categories.ndjson

Methodology: recursively walked every subcategory of Category:Cocktails (95 categories visited, 494 unique titles found), then pulled wikilinks from 15 List of... pages (1035 more candidates). After filtering with the existing should_skip_title() rules and de-duping against the 357 titles already in wikipedia.ndjson, 121 new titles fetched, 30 produced parseable infobox-based records (rest had no infobox or no recognizable ingredient list - mostly non-cocktail beverage articles that share categories: ginger ale, espresso, drinking straw, etc).

Image URLs added for 30/30 where Wikipedia returns one.

**License:** Same as IBA section. CC-BY-SA text + factual recipes.

**Could be expanded further** by writing a smarter prose-extraction fallback for cocktail articles that do not use {{Infobox cocktail}} (Blue Hawaiian, Fish house punch, Flirtini all have prose descriptions of ingredients but no structured infobox). Defer to a follow-up task.

## 4. Wikibooks Bartending -> wikibooks-bartending-extra.ndjson

**Important finding:** the existing normalized/wikibooks-normalized.json (3572 entries) is the **Wikibooks Cookbook** namespace (food recipes), NOT the Bartending wikibook. So the entire Wikibooks Bartending source was untapped before this agent.

Pulled all 169 subpages of Bartending/, filtered to 97 recipe pages (/Cocktails/, /Shots/, etc), fetched 89 wikitext bodies, parsed via custom wb_parse.py that handles the {{ingredient_template}} markup style used in this wikibook. 28 produced records that were not already in the catalog.

**License:** CC-BY-SA. Source URLs preserved.

## 5. Project Gutenberg Additional Cocktail Books

Searched Gutendex (PG search API) with terms bartender, cocktail, punch, mixed drinks, wine recipes, beverages, cordials. Real cocktail/bartender candidates not yet in the queue:

| PG # | Title | Author | Status |
|---|---|---|---|
| 13487 | The Ideal Bartender | Tom Bullock (1917) | Already covered by Agent 2 (per SCOUT-REPORT.md) |
| 23707 | The Ideal Bartender (alt edition) | Bullock | Same book, alt format |
| 75708 | Old-time recipes for home made wines, cordials and liqueurs | (anon) | **NEW LEAD** |
| 28491 | Dishes & Beverages of the Old South | McCulloch-Williams | **NEW LEAD** |
| 76921 | What to drink: blue book of beverages | Bertha E.L. Stockbridge | **NEW LEAD** |
| 65020 | The Complete Distiller (Cooper) | A. Cooper (1757) | Distillation manual; tangential to cocktails |

**Recommendation:** PG#28491 and PG#76921 should be added to the Agent 2 / historical-books harvest queue. Both are clean PG plain-text, US public domain.

## 6. EUVS (Vintage Cocktail Books) Inventory

The Internet Archive item vintage-cocktail-books-euvs contains **475 files** including **378 PDFs**. After filtering to pre-1929 publications (PD-US bright line) and titles containing bartender/cocktail/manual keywords, **101 cocktail-relevant pre-1929 books** are in the collection.

Of those, the existing cocktails/raw/ directory already has plain-text or scans for: Boothby 1908, Ensslin 1917, Macelhone 1923, Mahoney 1905, Newman 1904, Grohusko 1908. Per SCOUT-REPORT.md, additional already-queued: Jerry Thomas 1862, Bullock 1917, Kappeler 1895, Straub 1914, Schmidt 1892, Terrington 1869, Ricket 1873, Johnson 1900.

**Top NEW EUVS leads not yet in raw/ or scout queue (worth pursuing in a follow-up parse pass):**

| Year | Title |
|---|---|
| 1869 | Haney Steward & Barkeeper Manual |
| 1874 | The American Bar-Tender (E.A. Simmons) |
| 1884 | The Modern Bartenders Guide (O.H. Byron) |
| 1888 | Bartender Manual (Theodore Proulx) |
| 1888 | Police Gazette Bartenders Guide (Richard K Fox) |
| 1891 | Cocktail Boothby American Bar-Tender (1st ed) |
| 1895 | Mixed Drinks (Herbert W. Green) |
| 1898 | Cocktails How to Make Them (Livermore Knight) |
| 1901 | The Complete Buffet Manual (J.E. Sheridan) |
| 1902 | Fox Bartender Guide |
| 1903 | Daly Bartenders Encyclopedia |
| 1903 | The Complete Buffet Guide (V.B. Lewis) |
| 1905 | The Gorham Cocktail Book |
| 1906 | How to Mix Drinks (Spaulding & Whicher) |
| 1906 | Louis Mixed Drinks (Muckensturm) |
| 1910 | The Barkeepers Manual (Sullivan, 2nd ed) |
| 1913 | The Up-to-Date Bartenders Guide (Harry Montague) |
| 1914 | Rawling Book of Mixed Drinks (E.P. Rawling) |
| 1916 | Cocktail-Ology (Count Benvenito Martini) |
| 1917 | Seventy Recipes for Cocktails Cups and Punches (GGD/EEFP) |
| 1920 | Cocktails (Metropolitan Club Washington DC) |
| 1925 | About Town Cocktail Book (Joe Fitchett) |
| 1926 | The Cocktail Book - A Sideboard Manual for Gentlemen |
| 1927 | 900 Recettes de Cocktails et Boissons Americaines (FRENCH) |
| 1927 | Barflies and Cocktails (MacElhone follow-up) |
| 1928 | Cheerio: A Book of Punches and Cocktails |
| 1928 | Goderham and Worts ABC of Mixing Cocktails (Hiram Walker) |

Full list saved to agent5/euvs_cocktail_books.json (101 books with file sizes).

**Recommendation:** Spin up Agent 6 to OCR + parse the top 5-10 in this list (Haney 1869, Simmons 1874, Byron 1884, Proulx 1888, Boothby 1891, Green 1895, Sheridan 1901, Daly 1903, Lewis 1903, Rawling 1914) - estimated 2000+ additional historical recipes if those parse as well as the existing pre-1930 set.

## 7. Sources NOT pursued (and why)

- **Open Food Facts:** has no cocktail/recipe data (only barcoded products). Confirmed - skip.
- **TheCocktailDB v2 endpoints (popular.php, latest.php, multi-ingredient filter):** require Patreon key. Hold for commercial-launch decision.
- **TheCocktailDB localized names (Italian/Spanish/etc):** v2 only.
- **Difford Guide, Liquor.com, Punch Drink, Imbibe Magazine:** all proprietary editorial content (CC-incompatible). Recipes themselves not copyrightable but scraping is contractually disallowed. Hard skip.
- **r/cocktails / Kindred Cocktails / CocktailParty user-submitted DBs:** mixed licenses, often unclear. Skip.

## 8. Commercial Licensing Flags (action items before Pan-Tree ships on Google Play)

1. **TheCocktailDB image URLs** in cocktaildb-raw.ndjson (existing) and thecocktaildb-full.ndjson (this agent): each strDrinkThumb is user-uploaded to thecocktaildb.com under varying licenses. To ship safely:
   - Either subscribe to TheCocktailDB Patreon (5 USD/mo) which grants commercial image-use rights, OR
   - Strip image_url from production data and substitute Wikimedia Commons photos only (already CC-licensed).
2. **Wikipedia/Commons images** in iba-official.ndjson and wikipedia-categories.ndjson: each image_url is a Wikimedia Commons file. Run a Commons license-check pre-bundle and reject CC-BY-NC, CC-BY-ND, all-rights-reserved.
3. **CC-BY-SA attribution:** Pan-Tree must surface attribution for IBA, Wikipedia, and Wikibooks records (already in source_book and source_url fields, but the UI must display these in the recipe-detail screen to comply with the BY clause).
4. **CC-BY-SA share-alike:** description text from Wikipedia is CC-BY-SA. If Pan-Tree recipe-detail UI shows the description verbatim, downstream redistribution must remain CC-BY-SA. Easiest mitigation: keep descriptions as a separate optional field, or rewrite them in-house.

## 9. License Mix (across this agent output)

| Source | Records | Recipe license | Description license | Image license |
|---|---:|---|---|---|
| TheCocktailDB | 209 | factual / not copyrightable | TheCocktailDB ToS attribution required | mixed user-uploaded - VET BEFORE SHIP |
| IBA via Wikipedia | 89 | factual / not copyrightable | CC-BY-SA | mostly CC-BY/CC-BY-SA via Commons - vet per-image |
| Wikipedia categories | 30 | factual / not copyrightable | CC-BY-SA | as above |
| Wikibooks Bartending | 28 | factual / not copyrightable | CC-BY-SA | none retained |

## 10. New endpoints worth pursuing in a follow-up

1. EUVS pre-1929 PDF parsing (top 10 from section 6) -> est +2000 historical recipes
2. Wikipedia prose-extraction fallback for non-infobox cocktail articles (Blue Hawaiian, Fish house punch, Flirtini, Cohasset Punch, Goombay Smash, +80 others) -> est +60-100 records
3. PG#28491 (Old South Beverages) and PG#76921 (What to drink, 1920) parse -> est +200 records
4. TheCocktailDB v2 paid endpoints (popular.php, latest.php, multi-ingredient filter, localized names) once Patreon key is acquired -> est +100-200 records
5. Re-run wikibooks parser with broader regex for sub-pages (some recipes live under /Drinks/ or /Punches/ rather than /Cocktails/) -> est +20 records

## 11. Working data files (in backend/ingest/cocktails/agent5/)

Intermediate caches preserved for re-runs and follow-up work:

- cdb_az_full.json, cdb_new_records.json, cdb_random_extra.json, cdb_new_ids_*.json - TheCocktailDB raw responses
- wiki_cat_titles.json, wiki_to_fetch.json, wiki_titles_filtered.json, wiki_wikitext.json, wiki_new_titles.json, wiki_new_wikitext.json, wiki_list_links.json, wiki_list_new.json, wiki_list_wt.json, wiki_all_titles.json, wiki_images.json (260 page-images cached)
- iba_titles.json, iba_wikitext.json - IBA-specific
- wikibooks_pages.json, wikibooks_recipe_pages.json, wikibooks_wikitext.json, wb_parse.py, norm.py - Wikibooks parser + cache
- euvs_metadata.json, euvs_pdfs.json, euvs_pre1929.json, euvs_cocktail_books.json - EUVS inventory snapshots
- gutenberg_books.json, gutenberg_extra.json - PG search results

---
*End Agent 5 report.*

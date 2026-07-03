# Speakeater Layout Redesign Spec — 2026-07

**Scope (Kyle, 2026-07-02): layout, flow, and structure ONLY. Zero feature removal. All existing
logic, data, backend, and planned additions stay. Broken logic gets fixed, not redesigned away.**

## Why

The app has 28 feature areas and 27 nav routes surfaced more or less at once. Kyle: "the app has
so much value but no one — not a single soul — knows what to do." The fix is structural: one
mental model (everything answers to the pantry), four doors, and a first run that starts with the
camera.

## The mental model (target structure)

```
FIRST RUN:  camera scan → pantry filled → walkthrough → home
HOME:       "What are you doing?"  Eating · Drinking · Dipping · Seasoning
            + Pantry, Cookbooks, Shopping (utility row)
            + Back room (settings) holds everything else
RANKING:    every list everywhere is ranked live against the pantry, best match first
COOKBOOKS:  saved collections that re-rank against tonight's pantry
```

Visual skin: TBD from the localhost drafts (`projects/speakeater-app-drafts`, round 5 =
Playbill / Matchbook). Structure below is skin-independent.

## Route mapping — every existing route keeps a home

| Current route(s) | New home | Notes |
|---|---|---|
| `entry` (3 rooms) | REPLACED by new home | rooms metaphor retired; Sip/Eat/Solve → verticals + back room |
| `deck`, `recipe` | **Eating** | deck stays as the browse mechanic inside the vertical |
| `mealprep`, `plan` | **Eating** (secondary tabs/entry points inside) | not top-level anymore |
| `mixology` | **Drinking** | unchanged content |
| (corpus sauces) | **Dipping** | corpus filter; content exists (sauces vertical) |
| (corpus seasonings) | **Seasoning** | corpus filter; content exists |
| `pantry`, `scan`, `receipt`, `scan_bar`, `barcode` | **Pantry** | scan/receipt/barcode = capture methods INSIDE pantry, one "+ add" affordance |
| `shopping` | **Shopping** | keeps expiring/restock, aisles, vendor handoff |
| `price_demo`, `savings` | **Shopping** | mock → real; see Commerce below |
| `saved`, `library` | **Cookbooks** | live re-ranking badge ("3 of 4 ready tonight") |
| `submit`, `submit-photo-recipe`, `my_submissions`, `importlinks`, `contribute` | inside the matching vertical ("add your own") + back room list | keep all paths |
| `parties/*` (Mystery Nights, 5 routes) | **Back room** (later: house-ad slot) | fully functional, zero home-screen space |
| `community`, `beta`, `notifications` | **Back room** | |
| `settings`, `paywall` | **Back room** | paywall shown contextually as today |
| `login`, `onboarding` | first-run flow (below) | age gate stays |
| `search` | global, from home + verticals | |

## First-run flow (the make-or-break)

1. Age gate (legal, unchanged) → optional account LATER, not first.
2. **Camera opens.** "Open your fridge. Show us a shelf." One photo → pantry populates.
3. Three-beat walkthrough ON the real result: (a) "this is your pantry, tap what's wrong,"
   (b) "these four doors rank everything against it," (c) "cook one tonight — this number is
   how close you are." End inside a ranked list, not on a menu.
4. Existing tour engine is REUSED: `feature/walkthrough/` (TourAnchorRegistry, TourSteps 18KB,
   WalkthroughOverlay 16KB) — re-anchor steps to the new layout, cut steps that explain
   navigation the new layout makes obvious. Robust-walkthrough pass happens AFTER design lock.

## Broken logic to fix (audit list — verify each in code during implementation)

- [ ] Pantry: no bulk "clear all expired" (PantryScreen shows Expired state; only per-item
      actions exist via shopping's dismiss/restock). Add clear-all-expired + confirm.
- [ ] Shopping: "add to shopping list again" path broken per Kyle (re-add after check/clear?
      `toggleChecked`/`clearChecked`/`addManual` interplay) — reproduce and fix.
- [ ] Walkthrough: anchors/steps currently confusing; full re-do post-design-lock.
- [ ] Sweep: every TODO/FIXME in feature/* triaged into fix-or-file during the re-layout.

## Commerce (fix + deploy, not redesign)

- `ShoppingScreen.handoffUrl()` already builds Walmart + Instacart search URLs per item —
  extend to whole-list export (Instacart list API / Walmart affiliate deep link where available).
- `pricedemo/PriceComparisonMockScreen.kt` = mock "real-time price differences if they shop
  elsewhere." Wire to a real price source, move INTO Shopping as a live section, delete the
  mock route. (Comment at ShoppingScreen:405 already anticipates Amazon/Walmart/Instacart deals.)
- `savings` feature ties in ("what you saved by cooking from the pantry") — surface on the
  made-it moment and in Shopping.
- Any API with cost (price data) = flag to Kyle before signup ($1 rule).

## Order of operations

1. Kyle picks the skin from the drafts (iterating on :7700).
2. Freeze this structure map → implement new nav graph + home in Kotlin (all screens kept,
   re-parented; feature code mostly untouched).
3. Fix the broken-logic list as part of the same pass.
4. Rebuild the walkthrough against the final layout.
5. Commerce wiring (walmart/instacart/price) — can go in parallel, it's backend/deeplink work.
6. New AAB (vc69+): includes the 151MB drawable-xxhdpi WebP recompression already flagged.

*Nothing in this spec deletes a feature. It deletes confusion.*

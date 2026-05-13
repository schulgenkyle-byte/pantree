# Speakeater Architecture Improvement Proposal

**Date:** 2026-05-12
**Source:** `.planning/codebase/` (1,612 lines across 7 mapper docs)
**Scope:** Architecture moves only. Naming/rebrand items live in `NAMING.md` Phases A–H and are NOT covered here.

---

## The through-line

The rebrand-in-progress has fractured single-source-of-truth in three places (SKUs, brand strings, palette constants). Layered on top of zero tests, this is exactly the conditions for invisible launch-blocking bugs. The architecture fix is **centralize the things that should have ONE source of truth, then add the minimum test scaffold that locks them in.** Not "split god objects for cleanliness."

---

## Highest-leverage moves (ranked by impact / effort)

### 1. Fix the billing module — and centralize SKU identity

**Status: prod launch is blocked.**

- Android `BillingManager.kt:39-40` sends `brimm_pro_monthly` / `brimm_pro_yearly` (Play Console-locked names — cannot change without orphaning subscribers).
- Backend `billing.js:10-13` `ALLOWED_SKUS` has `pantrie_pro_monthly` / `pantrie_pro_annual`. Every real subscription purchase is rejected server-side as "unknown sku."
- `recipes.js:139` tier-detect compares against `pantrie_pro_annual` (never matches reality).
- `billing.js:166` `debugGrantPro` inserts yet another variant.
- 3 credit-pack SKUs (`speakeater_pack_*`) defined in `BillingManager.kt:47-49` but absent from backend allow-list AND lack credit-grant logic. The upsell copy in `vision.js` and `import.js` advertises a feature that cannot complete.

**Fix:**
- Single SKU constants file shared via API contract — e.g., `backend/src/billing-skus.js` and a generated Kotlin equivalent OR a Worker endpoint `/billing/skus` that the Android client trusts as the source of truth.
- Bring backend `ALLOWED_SKUS` to actual Play Console strings (`brimm_pro_monthly`, `brimm_pro_yearly`, the three `speakeater_pack_*`).
- Add credit-grant logic next to subscription verification in `billing.js`.

### 2. Introduce a Repository layer in Android

ViewModels currently inject `PantrieApi` and call network methods directly with `runCatching {}`. Cross-cutting logic — `RefreshBus.bump*()` after a save, optimistic UI, offline cache reads — is duplicated across `DeckViewModel`, `MixologyViewModel`, `SavedViewModel`, `ShoppingViewModel`.

**Fix:**
- Mirror the existing `EntitlementRepository` / `SwipeQuotaRepository` pattern. Add `RecipeRepository`, `PantryRepository`, `ShoppingRepository`, `InteractionRepository` in `android/app/src/main/java/app/pantrie/data/`.
- ViewModels become thin state-holders. `runCatching` and `RefreshBus.bump*` move into repositories.
- Unblocks #3 (god-object split) by giving the extracted ViewModels a clean dependency surface.

### 3. Split the three god-object screens

`DeckScreen.kt` (1,787 lines), `MixologyScreen.kt` (1,682 lines), `PhotoToRecipeScreen.kt` (1,160 lines) bundle composables + inline ViewModel + private palette constants + card chrome. Every change touches everything; recomposition is unscoped.

**Fix:**
- Extract `DeckViewModel.kt`, `MixologyViewModel.kt`, `PhotoToRecipeViewModel.kt` to sibling files (already the pattern in `feature/scan/ScanViewModel.kt`).
- Move card composables to `feature/cards/` — extend the existing `CardChrome.kt` pattern.
- Annotate DTO data classes (`Recipe`, `DeckResponse`, `Cocktail`) with `@Immutable` to cap recomposition.

### 4. Centralize Mixologist palette into the theme

`MixologyScreen.kt:79-87` defines `Sepia`, `SepiaInk`, `ModernBg`, `ModernCard`, `ModernInk`, `ModernGold`, `ModernInkMuted` as private file-level constants. These bypass `Color.kt`. Bootlegger Sepia is *intentionally* private per `DESIGN_SYSTEM.md §7`, but the Mixologist constants (modern dark mode) silently diverge from app-wide tokens.

**Fix:** Keep Bootlegger Sepia/SepiaInk private. Alias Mixologist's `ModernBg`/`ModernGold`/`ModernInk` to `Paper3`/`BrassBright`/`Ink` exports from `Color.kt`. Phase 2 theme updates then propagate automatically.

### 5. DRY backend helpers — minimal but security-sensitive

- `isUserPro` defined 3× (`submissions.js:543`, `import.js:29`, inline in `mealprep.js`)
- `adminAuthed` defined 3× (`beta.js:195`, `submissions.js:141`, `signups.js:8`)
- `TESTER_EMAILS` defined 2× (`vision.js:49-55`, `recipes.js:127-133`)

Any security fix to `adminAuthed` currently has to land in three places — easy to miss one. **Fix:** Hoist all three into `backend/src/util.js` exports.

---

## Tier 2 — fix-while-you're-in-there

- **Admin key URL leak** — `beta.js:710` puts `ADMIN_KEY` in `?key=...` query string on every stats XHR. Switch to `X-Admin-Key` header (matches every other admin write route). Remove `?key=` example from `BETA_RUNBOOK.md:24`.
- **EXIF strip inconsistency** — `submissions.js:184` strips JPEG metadata before storing photos. `vision.js:235` does NOT before forwarding to Anthropic. Extract `stripJpegMetadata()` to `util.js`, call from both.
- **Server-side swipe quota** — client-side `SwipeQuotaRepository` is bypassable by clearing app data. Server already counts via D1 interactions but uses a different source. Sync the client count from server response on every deck load, or remove client counter entirely.
- **`pan-tree/` mystery** — repo-root directory with its own `.git` (one "Initial commit") and no files. Either an abandoned submodule artifact or a legacy stash. Delete or add a `README.md` explaining what it is. Not referenced by anything.

## Tier 3 — performance hot spots

- `recipes.js:113-138` does three sequential D1 queries before the main fetch. Use `env.DB.batch([...])` — saves ~12–20ms per deck request.
- `recipes.js:159-160` exclusion query (`SELECT recipe_id FROM interaction WHERE user_id = ?`) is unbounded. Add 90-day recency window. Add `CREATE INDEX idx_interaction_user ON interaction(user_id)`.
- `AsyncImage` in DeckScreen/MixologyScreen has no explicit Coil disk-cache config. Configure `Coil.setImageLoader` in `PantrieApplication.kt` with a 100–200MB disk cache.

---

## Test scaffold — the lock-in

Zero tests anywhere is the multiplier on every concern above. Don't aim for coverage targets; aim for **regression locks on the highest-risk surfaces:**

- **Backend (vitest):** `auth.js` token mint/refresh rotation, `billing.js` `verifyPurchaseWithGoogle` with mocked Google response, `adminAuthed` timing-safe comparison, `recipes.js` deck ranking with a fixture pantry + recipe set.
- **Android (JUnit5 + Robolectric):** `BillingManager` purchase flow with mocked BillingClient, `EntitlementRepository` cache invalidation, `TokenStore` round-trip, `KeystoreKeyManager` key rotation.

Skip Compose UI tests for now — too costly per test, low ROI until the god-object split lands.

---

## Suggested sequence

1. **Day 1:** SKU centralization + billing fix (#1) — prod launch blocker. Add backend test for `verifyPurchaseWithGoogle` simultaneously.
2. **Day 2:** Admin key URL leak + EXIF strip + helper DRY (#5 and Tier 2 security items). Low effort, security-meaningful.
3. **Week 1:** Repository layer (#2). One repo at a time — start with `RecipeRepository`. Add a backend test for each endpoint as it's migrated.
4. **Week 2:** God-object split (#3) + Mixologist palette (#4). The repository layer makes the VM extractions straightforward.
5. **Week 3:** Tier 3 performance work + remaining test scaffold.

---

## What this proposal explicitly does NOT cover

Items already tracked in `NAMING.md` Phases A–H and excluded from this proposal:

- Folder renames `pantree-*` → `pantrie-*` (Phase F, blocked on process shutdown)
- Drawable rename `brimm_*` → content-prefixed (Phase G, blocked on image gen)
- `Brand.kt` `SUPPORT_EMAIL` / `LEGACY_DOMAIN` retention for 301 redirect wiring
- Memory file renames (Phase B, complete)
- Doc cross-references (Phase A, partial)

Run `NAMING.md` phases on their own track — they're hygienic and reversible. The architecture moves above unblock launch; the rebrand cleanup does not.

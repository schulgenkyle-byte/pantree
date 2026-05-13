# Testing Patterns

**Analysis Date:** 2026-05-12

> **Coverage summary: effectively zero.** There are no test source files anywhere in the repository. No unit tests, no instrumented tests, no backend tests. This is a solo-dev project shipped fast; tests were not written. See details below.

## Test Framework

**Android (Kotlin):**
- Runner: Not configured. `build.gradle.kts` has no `testImplementation` or `androidTestImplementation` dependencies.
- `android/gradle/libs.versions.toml` contains no JUnit, Espresso, Robolectric, Mockk, or Compose test entries.
- Config: None.

**Backend Worker (`backend/src/`):**
- Runner: Not configured. `backend/package.json` has no `devDependencies` beyond `wrangler`.
- No jest, vitest, or mocha config found.

**Ingest pipeline (`backend/ingest/`):**
- Runner: Not configured. Individual `.cjs` scripts have no test harness.

**Link parser (`backend/ingest/link-parser/`):**
- Runner: Not configured. `package.json` has no test devDependencies.
- Has a self-test CLI flag (`--self-test` on `src/parse.js`) and a manual eval runner (`eval/run-eval.js`). These are NOT automated test suites — they require network access and real URLs.

**Run Commands:**
```bash
# No test commands exist. These are the closest available alternatives:

# Link parser self-test (requires a running parse environment):
node backend/ingest/link-parser/src/parse.js --self-test

# Link parser eval set (requires network + a running Playwright/browser):
node backend/ingest/link-parser/eval/run-eval.js
```

## Test File Organization

**Android test directories:**
- `android/app/src/test/` — does NOT exist
- `android/app/src/androidTest/` — does NOT exist
- No `.kt` files anywhere under a `test` or `androidTest` path

**Backend test directories:**
- `backend/test/` — does NOT exist
- No `*.test.js` or `*.spec.js` files found outside `node_modules`
- No `*.test.cjs` or `*.spec.cjs` files found

**Link parser eval:**
- `backend/ingest/link-parser/eval/eval-set.json` — golden-set JSON of expected parse outputs (manual, not automated)
- `backend/ingest/link-parser/eval/run-eval.js` — script that runs the eval set and scores results against expectations

## Test Structure

No test files exist. No patterns to document.

## Mocking

**Framework:** None. No mock library is declared anywhere.

## Fixtures and Factories

**Test Data:** None for automated tests.

**Eval fixtures (link parser only):**
- `backend/ingest/link-parser/eval/eval-set.json` — hand-curated list of TikTok/YouTube URLs with expected parse outputs (title contains, ingredient count, step count, etc.)
- These serve as regression fixtures for the link-parser pipeline, but must be run manually

## Coverage

**Requirements:** None enforced.

**Estimate:**
| Layer | Coverage |
|-------|----------|
| Android Kotlin (unit) | 0% |
| Android Kotlin (instrumented/UI) | 0% |
| Cloudflare Worker routes | 0% |
| Ingest scripts | 0% |
| Link parser core pipeline | ~manual only (eval runner) |

**View Coverage:**
```bash
# Not applicable — no test runner configured.
```

## Test Types

**Unit Tests:**
- Not present. ViewModel logic (`runCatching`, sealed state transitions), `util.js` validators (`validString`, `validInt`, `validHttpsUrl`), and `ingredient-match.js` matching logic are all untested.

**Integration Tests:**
- Not present. Auth flow, billing purchase verification, Room DAO queries, and Worker D1 queries are untested.

**E2E Tests:**
- Not present for the app. No Espresso or Compose UI test setup.
- `backend/ingest/link-parser/eval/run-eval.js` is the closest analog — a manual end-to-end eval against real URLs, not an automated CI test.

## CI/CD

**No CI pipeline exists.** There is no `.github/` directory, no GitHub Actions workflows, no Bitrise config, no CircleCI config. Builds are done locally via Android Studio / Gradle, and the Worker is deployed manually via `wrangler deploy`.

This is expected for a solo-dev project at this stage.

## Common Patterns

No patterns to document — no test files exist.

## High-Value Test Targets

If tests are added in the future, these areas carry the highest risk per untested line:

**Android:**
1. `android/app/src/main/java/app/pantrie/billing/BillingManager.kt` — purchase acknowledgment logic; bugs here directly affect revenue
2. `android/app/src/main/java/app/pantrie/billing/EntitlementRepository.kt` — Pro tier entitlement gate; false negatives lock paying users out
3. `android/app/src/main/java/app/pantrie/billing/SwipeQuotaRepository.kt` — quota deduction logic; bugs can either over-charge users or give free unlimited access
4. `android/app/src/main/java/app/pantrie/network/ApiClient.kt` (`AuthInterceptor`) — 401 refresh race condition; incorrect mutex behavior could log users out under load
5. `android/app/src/main/java/app/pantrie/data/entities/Entities.kt` + `PantrieDatabase.kt` — Room DAO query correctness

**Backend Worker:**
1. `backend/src/util.js` — validators (`validString`, `validInt`, `validHttpsUrl`, `validPurchaseToken`) are used everywhere for input sanitation; pure functions, easy to unit test
2. `backend/src/billing.js` — Play Store purchase token verification; bugs allow free Pro access
3. `backend/src/vision.js` — quota enforcement (free/pro weekly caps, global day cap); bugs cause runaway Vision API spend
4. `backend/src/recipes.js` — swipe quota and daily-reset logic (timezone-aware); incorrect timezone math breaks quota resets for non-UTC users
5. `backend/src/ingredient-match.js` — recipe matching score; bugs cause wrong or no recipes to surface

**Link parser:**
- `backend/ingest/link-parser/src/pipeline.js` — the extraction cascade; already has a manual eval runner, straightforward to convert to automated Jest/Vitest tests

---

*Testing analysis: 2026-05-12*

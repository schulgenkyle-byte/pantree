<!-- refreshed: 2026-05-12 -->
# Architecture

**Analysis Date:** 2026-05-12

## System Overview

```text
┌─────────────────────────────────────────────────────────────────────┐
│                  Android App  (app.brimm / app.pantrie.*)            │
│                                                                      │
│  AgeGate → LanguagePicker → PantrieNav (NavHost)                    │
│                                                                      │
│  ┌─────────────┐  ┌─────────────┐  ┌────────────────────────────┐  │
│  │  Tonight     │  │  Mixology   │  │  Other Screens              │  │
│  │  (DeckScreen)│  │  (Mixology  │  │  Pantry · Shopping · Plan  │  │
│  │  DeckViewModel│  │   Screen)  │  │  Saved · Cook · Scan        │  │
│  └──────┬──────┘  │  BOOTLEGGER │  │  Settings · Community etc. │  │
│         │         │  Sepia+Serif│  └──────────────┬─────────────┘  │
│         │         │  MIXOLOGIST │                  │                │
│         │         │  Dark+Brass │                  │                │
│         └─────────┴─────────────┴──────────────────┘                │
│                   │        (via PantrieApi / Retrofit)               │
└───────────────────┼─────────────────────────────────────────────────┘
                    │  HTTPS · Bearer JWT
                    │  API_BASE_URL = pantrie-backend.schulgenkyle.workers.dev
                    │  (LOCKED — hardcoded in every shipped APK)
                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│              Cloudflare Worker  "pantrie-backend"                    │
│              backend/src/index.js  (route dispatch)                  │
│                                                                      │
│  CORS → configProblems() → path/method switch                       │
│    /auth/*  handleAuth          /scan  handleVision                 │
│    /pantry  handlePantry        /recipes/deck  handleRecipes.deck   │
│    /billing handleBilling       /beta  handleBeta                   │
│    /admin/* handleAdmin         …36 more route handlers              │
│                                                                      │
│  Middleware chain per authed route:                                  │
│    requireAuth (JWT verify) → enforce (rate limit) → handler        │
└──────────┬──────────────────────────────┬───────────────────────────┘
           │                              │
           ▼                              ▼
┌──────────────────────┐    ┌─────────────────────────────────────────┐
│  D1 (SQLite)          │    │  KV  "RATE_LIMIT_KV"                    │
│  binding: DB          │    │  rate buckets · JWKs cache · nonces     │
│  pantrie-db-staging   │    │  revocation list · service-account      │
│                       │    │  token cache · daily scan counters      │
│  Tables:              │    └─────────────────────────────────────────┘
│  user · session       │
│  pantry_item · recipe │    ┌─────────────────────────────────────────┐
│  interaction · plan   │    │  R2  "pan-tree" (LOCKED name)           │
│  shopping_item        │    │  binding: PHOTOS_BUCKET                 │
│  review · follow      │    │  env var: R2_BUCKET                     │
│  feedback · submission│    │  public base: PHOTOS_PUBLIC_BASE        │
│  entitlement · more   │    │  Stores user-submitted recipe photos    │
└──────────────────────┘    └─────────────────────────────────────────┘

─────────────────────────── OFFLINE / BATCH ───────────────────────────

┌─────────────────────────────────────────────────────────────────────┐
│  Ingest Pipelines  backend/ingest/  (batch, not in request path)    │
│                                                                      │
│  audit_v3_hf/  audit_v3_usda/  audit_v3_tmdb/  audit_v3_cfg/       │
│   └─ fetch_v2.cjs → normalize via Claude Haiku → push to D1        │
│                                                                      │
│  cocktails/  scrape_5star/  scrape_drinks/                          │
│   └─ Python + Node scrapers → raw JSON → faithfully modernized      │
│                                                                      │
│  link-parser/  (Oracle Cloud VM, not Cloudflare)                    │
│   └─ /api/import/links Worker endpoint → parser box callback        │
│      HMAC-auth · ytdlp · OCR · Claude extractor → recipe_submission │
└─────────────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| `MainActivity` | Single-Activity host: age gate, locale gate, NavHost, deep-link routing | `android/app/src/main/java/app/pantrie/MainActivity.kt` |
| `PantrieApplication` | Hilt entry point, AdMob warmup, EntitlementRepository refresh | `android/app/src/main/java/app/pantrie/PantrieApplication.kt` |
| `PantrieNav` | Compose NavHost with 30+ routes, bottom-nav tabs, WalkthroughOverlay, FAB | `android/app/src/main/java/app/pantrie/MainActivity.kt` (same file) |
| `DeckScreen` + `DeckViewModel` | Tonight swipe deck, match scoring display, Pro/ad quota gating | `android/app/src/main/java/app/pantrie/feature/deck/DeckScreen.kt` |
| `MixologyScreen` + `MixologyViewModel` | Cocktail swipe deck, BOOTLEGGER/MIXOLOGIST mode toggle | `android/app/src/main/java/app/pantrie/feature/mixology/MixologyScreen.kt` |
| `PantryScreen` + `PantryViewModel` | Pantry item list, scan/barcode entry points, savings card | `android/app/src/main/java/app/pantrie/feature/pantry/PantryScreen.kt` |
| `AppStateViewModel` | Process-wide badge counts (shopping, expiring); refreshes via `RefreshBus` | `android/app/src/main/java/app/pantrie/feature/app/AppStateViewModel.kt` |
| `RefreshBus` | `SharedFlow` event bus — mutations on one screen trigger badge refresh in nav | `android/app/src/main/java/app/pantrie/feature/app/RefreshBus.kt` |
| `PantrieApi` | Retrofit interface for all 60+ backend endpoints | `android/app/src/main/java/app/pantrie/network/PantrieApi.kt` |
| `ApiClient` / `NetworkModule` | OkHttp setup: `AuthInterceptor` (Bearer + 401 refresh), TLS 1.2/1.3, cert pinning | `android/app/src/main/java/app/pantrie/network/ApiClient.kt` |
| `TokenStore` | EncryptedSharedPreferences for access + refresh tokens | `android/app/src/main/java/app/pantrie/auth/TokenStore.kt` |
| `PantrieDatabase` | SQLCipher-encrypted Room DB; 5 entities, 5 DAOs | `android/app/src/main/java/app/pantrie/data/PantrieDatabase.kt` |
| `EntitlementRepository` | DataStore-cached Pro status; server-authoritative on Pro-gated calls | `android/app/src/main/java/app/pantrie/billing/EntitlementRepository.kt` |
| `SwipeQuotaRepository` | Daily swipe cap enforcement (10 free / 40 Pro) | `android/app/src/main/java/app/pantrie/billing/SwipeQuotaRepository.kt` |
| `BillingManager` | Google Play Billing library wrapper for subscription purchase flow | `android/app/src/main/java/app/pantrie/billing/BillingManager.kt` |
| `KeystoreKeyManager` | Android Keystore-backed AES key for SQLCipher passphrase | `android/app/src/main/java/app/pantrie/crypto/KeystoreKeyManager.kt` |
| `EncryptedFileStore` | EncryptedFile wrapper for user photo storage | `android/app/src/main/java/app/pantrie/crypto/EncryptedFileStore.kt` |
| `WalkthroughViewModel` + `TourRepository` | First-launch guided tour state machine; DataStore-persisted `tour_completed_v1` | `android/app/src/main/java/app/pantrie/feature/walkthrough/` |
| `Brand` | Single source of truth for user-facing brand strings | `android/app/src/main/java/app/pantrie/Brand.kt` |
| `Color.kt` / `Type.kt` / `Theme.kt` | Speakeater editorial dark palette + Playfair/SourceSerif/JetBrains Mono | `android/app/src/main/java/app/pantrie/ui/theme/` |
| Worker `index.js` | Cloudflare Worker entry: CORS, config validation, route dispatch | `backend/src/index.js` |
| `auth.js` | JWT mint/verify/refresh, Google OIDC exchange, Apple JWT (iOS) | `backend/src/auth.js` |
| `recipes.js` | Deck query (match scoring, quota), search, interactions, seed | `backend/src/recipes.js` |
| `vision.js` | CameraX fridge-photo → Anthropic Vision → pantry item extraction | `backend/src/vision.js` |
| `billing.js` | Play Developer API subscription verification, RTDN handler | `backend/src/billing.js` |
| `beta.js` | `handleBeta` (feedback, events, community) + `handleAdmin` (dashboard) | `backend/src/beta.js` |
| `import.js` + link-parser | TikTok/YouTube URL → recipe submission pipeline (parser box + callback) | `backend/src/import.js` + `backend/ingest/link-parser/` |
| D1 `pantrie-db-staging` | Authoritative relational store for all user + recipe data | `backend/schema.sql` + `backend/migrations/` |
| KV `RATE_LIMIT_KV` | Rate buckets, JWKs cache, nonces, refresh-token revocation list, daily scan counters | `wrangler.toml` binding |
| R2 `pan-tree` | User-submitted recipe photos (LOCKED bucket name) | `wrangler.toml` binding |

## Pattern Overview

**Overall:** MVVM (Android) + flat route-handler monolith (Worker)

**Key Characteristics:**
- Android: Hilt DI throughout; every screen has an `@HiltViewModel`; repositories are `@Singleton` and injected into ViewModels
- No explicit Repository layer between ViewModel and `PantrieApi` for most features — ViewModels call `api.*` directly. The few exceptions (`EntitlementRepository`, `SwipeQuotaRepository`, `TourRepository`) are genuine cross-feature stores.
- Worker: single `fetch()` entry point; routing is a large `if/else` switch on `path` + `method`; no router framework. Handler files export named objects (`handleRecipes`, `handleBeta`, etc.) with method functions.
- State flows downward from ViewModel to Compose via `StateFlow.collectAsState()`. Events flow upward via lambda callbacks passed from `PantrieNav` into screens.

## Layers

**UI Layer (Compose screens):**
- Purpose: Renders state, captures user events, delegates mutations to ViewModel
- Location: `android/app/src/main/java/app/pantrie/feature/*/`
- Contains: `*Screen.kt` composables, shared card components in `feature/cards/`
- Depends on: ViewModel state flows, shared UI components in `ui/`
- Used by: `PantrieNav` in `MainActivity.kt`

**ViewModel Layer:**
- Purpose: Holds UI state as `StateFlow`; orchestrates API calls and local DB reads
- Location: co-located with screen files (`*ViewModel.kt` or inside `*Screen.kt`)
- Contains: `@HiltViewModel` classes, coroutine scopes, `MutableStateFlow`
- Depends on: `PantrieApi`, `PantrieDatabase` DAOs, Repositories, `Analytics`
- Used by: Compose screens via `hiltViewModel()`

**Repository Layer (sparse):**
- Purpose: Cross-screen shared state that outlives individual ViewModels
- Location: `billing/EntitlementRepository.kt`, `billing/SwipeQuotaRepository.kt`, `feature/walkthrough/TourRepository.kt`, `feature/settings/LocalSettingsStore.kt`
- Contains: `@Singleton` classes backed by DataStore or local KV
- Depends on: `PantrieApi`, DataStore `Preferences`, `EncryptedSharedPreferences`
- Used by: ViewModels and `PantrieApplication`

**Network Layer:**
- Purpose: Retrofit client with Bearer-auth + 401 refresh, TLS enforcement, cert pinning
- Location: `android/app/src/main/java/app/pantrie/network/`
- Contains: `PantrieApi.kt` (interface), `ApiClient.kt` (OkHttp config + `AuthInterceptor`), `Dtos.kt` (serializable DTOs)
- Depends on: `TokenStore`, `BuildConfig.API_BASE_URL`
- Used by: All ViewModels and Repositories that need remote data

**Data Layer (local cache):**
- Purpose: Encrypted offline cache for pantry, recipe, shopping, interaction, review-draft data
- Location: `android/app/src/main/java/app/pantrie/data/`
- Contains: `PantrieDatabase.kt` (Room + SQLCipher), `entities/Entities.kt` (5 entities + 5 DAOs)
- Depends on: `KeystoreKeyManager` for passphrase
- Used by: `PantryViewModel`, `ShoppingViewModel`, and ViewModels that need offline reads

**Security Layer:**
- Purpose: Keystore-backed key management and encrypted file storage
- Location: `android/app/src/main/java/app/pantrie/crypto/`
- Contains: `KeystoreKeyManager.kt`, `EncryptedFileStore.kt`
- Depends on: Android Keystore system
- Used by: `PantrieDatabase`, `ScanViewModel` (photos)

**Worker Backend (flat):**
- Purpose: Single Cloudflare Worker handling all API endpoints
- Location: `backend/src/`
- Contains: `index.js` (dispatch), 30+ handler modules, `schema.sql`, `migrations/`
- Depends on: D1 (`DB`), KV (`RATE_LIMIT_KV`), R2 (`PHOTOS_BUCKET`), Anthropic Vision API, Google APIs
- Used by: Android app via HTTPS

## Data Flow

### Primary Request Path (authed API call)

1. Screen composable calls ViewModel method (e.g., `vm.refresh()`)
2. ViewModel calls `api.deck()` via injected `PantrieApi` (Retrofit interface)
3. `AuthInterceptor.intercept()` attaches `Authorization: Bearer <token>`; on 401, calls `singleFlightRefresh()` serialized behind a Mutex (`ApiClient.kt`)
4. Request reaches Worker: `fetch(request, env, ctx)` in `backend/src/index.js`
5. Worker validates config, handles CORS OPTIONS, then dispatches via `if/else` path+method switch
6. For authed routes: `requireAuth(request, env)` — verifies JWT signature, checks revocation in KV
7. `enforce(env, 'read', userId)` — per-user rate-limit check in KV
8. Handler function called (e.g., `handleRecipes.deck(userId, env, request)`)
9. Handler queries D1 via `env.DB.prepare(...).bind(...).all()` (or `.run()` for writes)
10. Handler returns `json(payload, 200, request, env)` — includes CORS headers
11. Retrofit deserializes JSON into Kotlin DTO via `kotlinx.serialization`
12. ViewModel updates `_state` StateFlow
13. Compose recomposes on state change

### Tonight Deck Scoring Flow

1. `handleRecipes.deck()` in `backend/src/recipes.js`
2. Fetch user's pantry items from D1
3. `buildPantryIndex()` in `ingredient-match.js` — canonical name map
4. Sample up to 75 candidate recipes from D1 (filtered by content_type, user history, photo availability)
5. For each recipe: `indexMatch()` — counts pantry hits, identifies expiring boosts, staple penalty
6. Sort by match score; apply daily local-TZ swipe quota check (KV counter)
7. Return top N recipes in ranked order

### Bootlegger / Mixologist Mode Bifurcation

The mode toggle exists **only** in `MixologyScreen.kt`. It is not a global theme switch.

- `var vintageMode by rememberSaveable { mutableStateOf(true) }` — composable-local state, persists across rotation
- **Bootlegger (vintageMode = true):** private `Sepia = Color(0xFFE6D3A7)` and `SepiaInk = Color(0xFF3A2B1A)` defined at lines 79-80 of `MixologyScreen.kt`. These override `Color.kt`/`Theme.kt` colors within the screen. Card content filters to `isHistoric = true` and requires non-null `originalText`.
- **Mixologist (vintageMode = false):** uses `ModernBg`, `ModernGold`, `ModernInk` private constants defined in the same file (lines 83-87), NOT the app-wide `Color.kt` tokens. Card content filters to `isHistoric = false` (modern cocktails).
- No other screen is affected. `DeckScreen` (food), `PantryScreen`, etc. always use `Color.kt` tokens.
- The segmented VINTAGE/MODERN toggle is a `Row` with two `Surface` buttons at line 300 of `MixologyScreen.kt`.
- Backend is aware of the mode via the `content_type=cocktail` query param. Clients also pass `require_photo=1` in Mixologist mode.

### Vision Scan Flow

1. `ScanScreen.kt` captures image via CameraX
2. `ScanViewModel` base64-encodes downscaled image, calls `api.scan()`
3. Worker `handleVision()` in `backend/src/vision.js`: checks daily scan quota (KV), forwards image to Anthropic Vision API
4. Vision response parsed into pantry item list
5. Items bulk-added to D1 via `handlePantry.addBulk()`
6. Android updates local Room cache + refreshes `PantryScreen`

### Background Workers (Android)

- `RescanWorker` — fires a notification if pantry items have expiry approaching; deep-links to `pantry` or `expiring` routes (whitelist in `ALLOWED_DEEP_LINK_ROUTES`)
- `SwipeRefillWorker` — fires when daily swipe cap refills; skips notification if user opened app within last 12h (checks `KEY_LAST_APP_OPEN_MS` in SharedPreferences)
- `NotificationScheduler` — schedules both workers via WorkManager on `onCreate`

**State Management:**
- Remote state: fetched by ViewModels, cached in `StateFlow`. No global remote-state cache (no `ViewModel` scope exceeds screen lifetime except `AppStateViewModel` and `hiltViewModel()` at `Activity` scope for `WalkthroughViewModel`).
- Local persistent state: DataStore for entitlement, tour completion, LocalSettingsStore (mixed notes, prefs), EncryptedSharedPreferences for tokens.
- Offline cache: Room (SQLCipher) for pantry, recipe deck, shopping, interactions, review drafts.
- Cross-screen signals: `RefreshBus` (SharedFlow in Hilt singleton) — emits from pantry/shopping mutations, `AppStateViewModel` observes it to refresh badge counts.

## Key Abstractions

**PantrieApi (Retrofit interface):**
- Purpose: Typed contract for all 60+ backend endpoints
- Examples: `android/app/src/main/java/app/pantrie/network/PantrieApi.kt`
- Pattern: `@GET`/`@POST`/`@PATCH`/`@DELETE` annotations; suspend functions; DTOs in `Dtos.kt`

**Handler module (Worker):**
- Purpose: Groups related endpoints behind a named export object
- Examples: `backend/src/recipes.js` (handleRecipes), `backend/src/pantry.js` (handlePantry), `backend/src/beta.js` (handleBeta + handleAdmin)
- Pattern: `export const handleX = { async methodName(userId, env, request) { ... } }`

**Brand.kt (Android brand strings):**
- Purpose: Single source for user-facing brand strings (APP_NAME, DOMAIN, etc.)
- Examples: `android/app/src/main/java/app/pantrie/Brand.kt`
- Pattern: `object Brand { const val APP_NAME = "Speakeater" ... }`

## Entry Points

**Android app entry:**
- Location: `android/app/src/main/java/app/pantrie/MainActivity.kt`
- Triggers: Android OS (launcher intent), WorkManager PendingIntents, share-sheet ACTION_SEND
- Responsibilities: splash screen, age gate, locale gate, NavHost, share-intent URL extraction

**Worker entry:**
- Location: `backend/src/index.js` — `export default { async fetch(request, env, ctx) { ... } }`
- Triggers: Every inbound HTTPS request
- Responsibilities: CORS, config validation, rate limit, auth, route dispatch, error correlation ID

**Ingest entry points (offline only):**
- `backend/ingest/audit_v3_*/fetch_v2.cjs` — individual pipeline runners, read `PANTRIE_ADMIN_KEY` env var
- `backend/ingest/link-parser/src/server.js` — Oracle Cloud VM HTTP server for link-import parser box

## Architectural Constraints

- **Threading:** Android: Coroutines with `Dispatchers.IO` for DB/network, `Dispatchers.Main` for UI state. Worker: single-threaded V8 event loop; all async via `await`. No true parallelism in the Worker.
- **Global state:** `AppStateViewModel` is scoped to the Activity and shared between all `NavHost` composables. `EntitlementRepository`, `SwipeQuotaRepository`, `RefreshBus` are Hilt `@Singleton`s — process-global.
- **API_BASE_URL lock:** `BuildConfig.API_BASE_URL` defaults to `pantrie-backend.schulgenkyle.workers.dev` and is baked into every shipped APK. Cannot be changed without a new release.
- **applicationId lock:** `app.brimm` — locked to the Play Store listing. The Kotlin package is `app.pantrie.*` (permanent internal codename).
- **R2 bucket name lock:** `pan-tree` — R2 buckets cannot be renamed. The `PHOTOS_BUCKET` env var abstracts this from Worker code.
- **D1 binding:** `pantrie-db-staging` — renameable in `wrangler.toml` but requires coordinated migration and deploy.
- **SQLCipher dependency:** Database key is derived from Android Keystore; Keystore invalidation on biometrics change causes a destructive migration (key reset + db delete), designed by `PantrieDatabase.buildDatabase()`.
- **Circular imports:** None detected. Handler modules import from `util.js`, `ratelimit.js`, `ingredient-match.js`, `canonicalize.js`, `preferences.js` — no cycles.

## Anti-Patterns

### ViewModel-as-god-object (MixologyScreen)

**What happens:** `MixologyViewModel` (in `MixologyScreen.kt`) bundles cocktail deck state, analytics, review loading, note CRUD, entitlement check, ad quota, and `LocalSettingsStore` access into one 220-line class. The VM is also defined in the same file as the composable it serves.

**Why it's wrong:** The file is 1,682 lines. Adding any Mixology feature requires navigating a single mega-file. `MixologyViewModel` has 8 injected constructor dependencies. Testing any single behavior requires mocking all 8.

**Do this instead:** Extract `MixologyNoteRepository` (note CRUD + share-as-review), `MixologyCocktailRepository` (deck fetch + swipe interactions), and move the VM to its own file `MixologyViewModel.kt`. Mirror the pattern in `feature/scan/ScanViewModel.kt` which is already separate.

### DeckScreen is equally large

**What happens:** `DeckScreen.kt` is 1,787 lines — the largest file in the project. `DeckViewModel` is defined inside it alongside composable functions for the swipe card, quick-action row, Pro upgrade prompt, and empty states.

**Why it's wrong:** Any change to the swipe gesture logic risks touching paywall logic and vice versa. Code review is difficult. Component reuse is blocked because the card composable is private inside the file.

**Do this instead:** Extract `SwipeCard.kt`, `DeckQuickActions.kt`, `DeckViewModel.kt` as separate files. The shared card chrome already exists in `feature/cards/CardChrome.kt` — extend that pattern.

### Direct API calls in ViewModels (no Repository layer)

**What happens:** Most ViewModels (`PantryViewModel`, `ShoppingViewModel`, `SearchViewModel`, etc.) inject `PantrieApi` and call network methods directly with `runCatching { api.foo() }`. There is no intermediate Repository abstraction.

**Why it's wrong:** Business logic (e.g., "after saving a recipe, also bump the shopping list and emit to RefreshBus") is duplicated across `DeckViewModel`, `MixologyViewModel`, and `SavedViewModel`. Adding offline caching of any endpoint later requires touching every ViewModel that calls it.

**Do this instead:** Introduce `RecipeRepository`, `PantryRepository`, `ShoppingRepository` in `data/` — mirror the existing `EntitlementRepository` pattern. Move `runCatching` + `RefreshBus.bump*()` calls into the repository. ViewModels become thin state holders.

### Private palette constants in MixologyScreen bypass the design system

**What happens:** `MixologyScreen.kt` defines `Sepia`, `SepiaInk`, `ModernBg`, `ModernCard`, `ModernInk`, `ModernGold`, `ModernInkMuted` as `private val` at the file level. These bypass `Color.kt` entirely.

**Why it's wrong:** When `Color.kt` receives Phase 2 updates, the Mixologist dark palette (ModernBg, ModernGold) will diverge from the app theme. The design system doc (`DESIGN_SYSTEM.md`) explicitly says Bootlegger is "preserved unchanged" — but the Mixologist palette is also private and not linked to `Color.kt`.

**Do this instead:** The Bootlegger Sepia/SepiaInk should remain private (per `DESIGN_SYSTEM.md §7`). The Mixologist constants (`ModernBg`, `ModernGold`, etc.) should be aliases of `Paper3`/`BrassBright`/`Ink` from `Color.kt` so Phase 2 redesign changes propagate automatically.

### pan-tree as a git submodule without documentation

**What happens:** `pan-tree/` at the repo root is a directory with its own `.git` (one commit: "Initial commit"). It contains no visible files beyond the `.git` directory.

**Why it's wrong:** Its purpose is undocumented. It appears to be either an abandoned submodule or a legacy stash from before the `backend/` layout was established. It is not referenced by any build file, `.gitmodules`, or import.

**Do this instead:** Remove `pan-tree/` from the repo root, or add a `README.md` inside explaining its purpose. Check `.gitmodules` for a submodule entry — if none, the directory is an artifact and should be deleted.

## Error Handling

**Strategy:** Optimistic UI + silent recovery on Android; structured error responses on the Worker.

**Patterns:**
- Android ViewModels wrap every API call in `runCatching { }` — success updates `_state`, failure updates `_error` StateFlow (or is swallowed silently). Screens observe `error` and show a `Snackbar` or inline error text.
- Worker: `try/catch` in the outer `fetch()` handler catches any unhandled exception, logs with a `correlationId` (UUID), and returns `err(500, 'internal error', { correlationId })`. The correlation ID is surfaced to the client but the stack trace is server-only.
- Auth errors: 401 from any endpoint triggers `AuthInterceptor.singleFlightRefresh()`. If refresh fails, `TokenStore.clear()` is called and the user is returned to the login screen (this navigation is implicit — `PantrieNav` observes login state).
- Billing errors: `billing.js` returns structured `{ ok, reason }` objects from `verifyPurchaseWithGoogle()` rather than throwing, so the Worker can log without crashing.

## Cross-Cutting Concerns

**Logging:** Android: `android.util.Log.i("PantrieHttp", ...)` via `HttpLoggingInterceptor` (BASIC in debug, NONE in release). Worker: `console.error(...)` — captured by Cloudflare Worker observability (enabled in `wrangler.toml`).

**Validation:** Worker validates inputs with helpers from `util.js` (`validString`, `validOpaqueId`, `validPurchaseToken`, `readJson`). Cloudflare Worker observability is enabled (`[observability] enabled = true` in `wrangler.toml`).

**Authentication:** JWT (HS256 with `JWT_SECRET`) + refresh token rotation with family-based reuse detection. Google OIDC exchange for login. Apple Sign-In supported for iOS path (`auth-apple.js`, `billing-apple.js`). Android: Credential Manager + Google Sign-In. Server: `requireAuth()` in `auth.js` validates JWT on every authed route; checks KV revocation list on each request.

---

*Architecture analysis: 2026-05-12*

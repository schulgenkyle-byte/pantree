# Codebase Structure

**Analysis Date:** 2026-05-12

## Directory Layout

```
pantrie-build/
├── android/                    # Android Kotlin + Compose app (package app.brimm / app.pantrie.*)
│   ├── app/
│   │   ├── build.gradle.kts    # Build config: applicationId, API_BASE_URL, signing, deps
│   │   └── src/
│   │       ├── debug/res/xml/  # network_security_config.xml (debug variant)
│   │       └── main/
│   │           ├── java/app/pantrie/
│   │           │   ├── Brand.kt                  # User-facing brand strings (APP_NAME, DOMAIN)
│   │           │   ├── MainActivity.kt           # Single Activity + PantrieNav NavHost
│   │           │   ├── PantrieApplication.kt     # Hilt entry, AdMob init, entitlement refresh
│   │           │   ├── auth/                     # CredentialManagerFlow, TokenStore
│   │           │   ├── billing/                  # AdManager, BillingManager, EntitlementRepository,
│   │           │   │                             #   SwipeQuotaRepository, PaywallScreen, ProUpgradeCard,
│   │           │   │                             #   InlineAdCard, SwipeWallSheet, BannerAd
│   │           │   ├── crypto/                   # KeystoreKeyManager, EncryptedFileStore
│   │           │   ├── data/                     # PantrieDatabase (Room + SQLCipher), Entities.kt (DAOs)
│   │           │   │   └── entities/
│   │           │   ├── feature/                  # One subdirectory per screen/feature
│   │           │   │   ├── app/                  # AppStateViewModel, RefreshBus (process-wide state)
│   │           │   │   ├── auth/                 # LoginScreen, LoginViewModel
│   │           │   │   ├── barcode/              # BarcodeScreen, BarcodeViewModel
│   │           │   │   ├── beta/                 # CommunityScreen, BetaFeedbackSheet, Analytics
│   │           │   │   ├── cards/                # CardChrome.kt (shared swipe-card components)
│   │           │   │   ├── contribute/           # ContributeRecipePhotoScreen
│   │           │   │   ├── cook/                 # CookModeScreen, CookViewModel
│   │           │   │   ├── deck/                 # DeckScreen + DeckViewModel (1,787 lines)
│   │           │   │   ├── importlinks/          # ImportLinksScreen, ImportLinksViewModel,
│   │           │   │   │                         #   ImportReviewScreen, ImportLinksDto
│   │           │   │   ├── library/              # LibraryScreen, LibraryViewModel, BookDetailScreen
│   │           │   │   ├── mealprep/             # MealPrepScreen, MealPrepViewModel
│   │           │   │   ├── mixology/             # MixologyScreen + MixologyViewModel (1,682 lines)
│   │           │   │   ├── notifications/        # NotificationScheduler, RescanWorker, SwipeRefillWorker
│   │           │   │   ├── onboarding/           # OnboardingScreen, OnboardingViewModel, AgeGateScreen
│   │           │   │   ├── pantry/               # PantryScreen + PantryViewModel
│   │           │   │   ├── plan/                 # PlanScreen, PlanViewModel
│   │           │   │   ├── pricedemo/            # PriceComparisonMockScreen (debug-only)
│   │           │   │   ├── recipe/               # RecipeDetailScreen, SubstitutionSheet
│   │           │   │   ├── review/               # (review submission support)
│   │           │   │   ├── saved/                # SavedScreen, SavedViewModel
│   │           │   │   ├── savings/              # SavingsCard (waste-savings widget)
│   │           │   │   ├── scan/                 # ScanScreen, ScanViewModel (CameraX + Vision)
│   │           │   │   ├── search/               # SearchSheet, SearchViewModel
│   │           │   │   ├── settings/             # SettingsScreen, SettingsViewModel, LocalSettingsStore
│   │           │   │   ├── shopping/             # ShoppingScreen, ShoppingViewModel
│   │           │   │   ├── submit/               # SubmitRecipeScreen, PhotoToRecipeScreen,
│   │           │   │   │                         #   MySubmissionsScreen + matching ViewModels
│   │           │   │   └── walkthrough/          # WalkthroughOverlay, WalkthroughViewModel,
│   │           │   │                             #   TourRepository, TourSteps, TourSegment,
│   │           │   │                             #   TourAnchorRegistry
│   │           │   ├── locale/                   # LocaleManager, LanguagePickerScreen + ViewModel
│   │           │   ├── network/                  # PantrieApi (Retrofit interface), ApiClient,
│   │           │   │                             #   AuthInterceptor, Dtos (all DTOs)
│   │           │   └── ui/                       # BrandImage.kt, IngredientImage.kt, theme/
│   │           │       └── theme/                # Color.kt, Type.kt, Theme.kt
│   │           └── res/
│   │               ├── drawable/                 # brimm_*.png ingredient/cuisine/glass/aisle images
│   │               ├── drawable-xxhdpi/
│   │               ├── mipmap-*/                 # Adaptive icon densities (s_pour_martini.png base)
│   │               ├── values/                   # strings.xml (brand strings), themes.xml, font_certs.xml
│   │               ├── values-es/hi/id/pt-rBR/   # Localized strings (4 languages)
│   │               ├── values-v31/               # Android 12+ themed splash
│   │               └── xml/                      # network_security_config.xml, data_extraction_rules.xml
│   └── gradle/wrapper/                           # Gradle wrapper (generated on first Studio open)
│
├── backend/                    # Cloudflare Worker "pantrie-backend" + offline ingest pipelines
│   ├── src/                    # Worker source (ES modules, no bundler)
│   │   ├── index.js            # Entry point: CORS, config check, route dispatch
│   │   ├── auth.js             # JWT mint/verify/refresh, Google + Apple OIDC
│   │   ├── recipes.js          # Deck, search, interactions, seed (largest handler, 1,164 lines)
│   │   ├── vision.js           # Fridge-photo → Anthropic Vision → pantry items
│   │   ├── billing.js          # Play Developer API subscription verify, RTDN, Apple billing
│   │   ├── pantry.js           # Pantry CRUD
│   │   ├── shopping.js         # Shopping list CRUD
│   │   ├── plans.js            # Week plan CRUD, propose, alternatives
│   │   ├── mealprep.js         # Meal prep proposal (Claude Haiku)
│   │   ├── reviews.js          # Public recipe reviews + feed
│   │   ├── beta.js             # Beta feedback, events, community feed, admin dashboard
│   │   ├── submissions.js      # User-submitted recipes + photo-to-recipe (Pro)
│   │   ├── import.js           # TikTok/YouTube link-import → parser box callback
│   │   ├── library.js          # Three-level library (Book → Chapter → Recipe)
│   │   ├── users.js            # /me CRUD, home stats, export, delete account
│   │   ├── preferences.js      # User preference/taste profile
│   │   ├── ingredient-match.js # Pantry match scoring engine
│   │   ├── canonicalize.js     # Ingredient synonym normalization
│   │   ├── ratelimit.js        # KV-backed per-user rate limiting
│   │   ├── util.js             # json(), err(), cors(), validString(), uid() helpers
│   │   ├── expiry.js           # Expiry date utilities, price estimation
│   │   ├── core-ingredients.js # CORE ingredient algorithm (weekly pantry analysis)
│   │   ├── waste.js            # Waste log + savings summary
│   │   ├── nutrition.js        # Per-recipe nutrition fetch
│   │   ├── substitutions.js    # Ingredient substitution lookups
│   │   ├── follows.js          # User follow/unfollow/block
│   │   ├── signups.js          # Landing page beta signup list
│   │   ├── barcode.js          # Barcode product lookup
│   │   ├── ingredient-shelf.js # Shelf-life data
│   │   ├── seed-data.js        # Seed recipe helpers (used by /recipes/seed)
│   │   ├── google-auth.js      # Google service-account token helper
│   │   ├── jwks.js             # JWKs cache (Apple Sign-In OIDC verification)
│   │   ├── auth-apple.js       # Apple Sign-In exchange
│   │   ├── billing-apple.js    # Apple IAP receipt verification (iOS path)
│   │   ├── parser-stub.js      # Stub for link-parser integration testing
│   │   └── apple-jwks.js       # Apple JWKS endpoint helper
│   │
│   ├── schema.sql              # D1 schema (authoritative); run on new D1 instance
│   ├── schema-library.sql      # Library feature schema extension
│   ├── schema-import.sql       # Link-import feature schema extension
│   ├── migrations/             # Dated migration SQL files (applied after initial schema)
│   ├── wrangler.toml           # Worker name, D1/KV/R2 bindings, non-secret env vars
│   ├── package.json            # Worker dev/deploy scripts (Wrangler)
│   │
│   └── ingest/                 # OFFLINE batch pipelines — not part of request path
│       ├── audit_v3_hf/        # Hugging Face recipe dataset normalization
│       ├── audit_v3_usda/      # USDA MyPlate Kitchen normalization
│       ├── audit_v3_tmdb/      # TheMealDB normalization
│       ├── audit_v3_cfg/       # CFG dataset normalization
│       ├── audit_v2_*/         # Legacy v2 pipeline artifacts (superseded by v3)
│       ├── cocktails/          # Pre-Prohibition cocktail book scrapers + normalizers
│       │   └── raw/            # Per-book page caches (thomas-1862, kappeler-1895, etc.)
│       ├── scrape_5star/       # High-rated recipe scrapers (30+ food blog sources)
│       ├── scrape_drinks/      # Drinks/cocktail web scrapers
│       ├── link-parser/        # TikTok/YouTube → recipe_submission pipeline (runs on Oracle Cloud VM)
│       │   └── src/            # server.js, pipeline.js, extractor.js, ytdlp.js, ocr.js
│       ├── normalized/         # Normalized JSON recipe output staging area
│       └── raw/                # Raw downloaded JSON/HTML from data sources
│
├── demo/
│   └── pantrie-demo.html       # Self-contained single-file interactive mockup (stakeholder demo)
│
├── docs/                       # Product + engineering reference documents
│   ├── product-spec.md         # Full product specification
│   ├── android-production-spec.md
│   ├── API_SCHEMA_v2.md        # REST API documentation
│   ├── privacy-policy.md       # Privacy policy (deployed to speakeater.com/privacy)
│   ├── data-safety-draft.md    # Play Store data safety form
│   ├── play-console-setup.md
│   ├── play-console-submission.md
│   └── playstore-listing.md    # Play Store listing copy
│
├── image_assets/
│   └── brimm/                  # Master image assets (pending rename to pantrie/ per NAMING.md Phase G)
│       ├── ingredients/        # Per-ingredient photorealistic PNGs (91 files, brimm_*.png prefix)
│       ├── cuisines/           # Cuisine images (11 files, brimm_cuisine_*.png prefix)
│       ├── glasses/            # Glass type images (6 files, brimm_glass_*.png prefix)
│       ├── aisles/             # Aisle category images (13 files, brimm_aisle_*.png prefix)
│       ├── nav/                # Nav bar icon PNGs (brimm_nav_*.png)
│       ├── brimm_samples/      # Sample/generated image output
│       └── brand_kit/          # Icons, wordmark assets
│
├── pan-tree/                   # Legacy directory — contains only a .git (1 commit: "Initial commit")
│                               # Purpose unknown; not referenced by any build or import. Candidate for deletion.
│
├── pentest/                    # Security testing resources
│   ├── pentest-playbook.md     # 40+ tests across 10 OWASP categories
│   ├── curl-all-endpoints.sh   # Full endpoint test suite
│   ├── scan-image.sh           # Vision API test
│   └── seed-recipes.sh         # Catalog seed script
│
├── scripts/
│   └── generate-keystore.sh    # Release keystore generation helper
│
├── .planning/                  # GSD planning workspace (not shipped)
│   └── codebase/               # Codebase analysis documents (this directory)
│
├── NAMING.md                   # CANONICAL: three-name coexistence rules, locked surfaces, migration log
├── DESIGN_SYSTEM.md            # Speakeater editorial dark palette, typography, component rules
├── PANTRIE.md                  # Product north star, roadmap, pricing, competitive positioning
├── README.md                   # Quickstart: demo, backend deploy, pen test, Android build
├── BETA_RUNBOOK.md             # Beta test operations: backend setup, admin dashboard, tester invite
└── .gitignore
```

## Directory Purposes

**`android/`:**
- Purpose: Android native app (Kotlin + Jetpack Compose)
- Contains: Single Gradle module `app/`, all Kotlin source, Android resources
- Key files: `app/build.gradle.kts` (applicationId, API_BASE_URL, signing), `MainActivity.kt` (NavHost), `PantrieApplication.kt` (Hilt root)

**`android/app/src/main/java/app/pantrie/`:**
- Purpose: Root Kotlin package for the app (internal codename `pantrie`, user brand `Speakeater`)
- Contains: Feature modules, network, data, billing, crypto, auth, locale, UI theme
- Key files: `Brand.kt`, `MainActivity.kt`, `PantrieApplication.kt`

**`android/app/src/main/java/app/pantrie/feature/`:**
- Purpose: Feature-first package layout — each screen or closely related screen group has its own subdirectory
- Contains: `*Screen.kt` composables, `*ViewModel.kt` classes (often co-located in same file as screen)
- Key files: `deck/DeckScreen.kt` (Tonight), `mixology/MixologyScreen.kt` (Bootlegger/Mixologist), `pantry/PantryScreen.kt`

**`android/app/src/main/java/app/pantrie/network/`:**
- Purpose: Retrofit + OkHttp setup and all DTO definitions
- Contains: `PantrieApi.kt` (interface), `ApiClient.kt` (NetworkModule + AuthInterceptor), `Dtos.kt`
- Key files: `ApiClient.kt` (auth interceptor, cert pinning, TLS config)

**`android/app/src/main/java/app/pantrie/ui/theme/`:**
- Purpose: Speakeater editorial design tokens
- Contains: `Color.kt` (dark palette + legacy aliases), `Type.kt` (Playfair/SourceSerif/JetBrains Mono), `Theme.kt`
- Key files: `Color.kt` — primary source for all color constants; `DESIGN_SYSTEM.md` is the spec

**`android/app/src/main/res/drawable/`:**
- Purpose: Photorealistic PNG assets for ingredients, cuisines, glasses, aisles, nav icons
- Contains: `brimm_*.png` (pending rename to content-prefixed names per NAMING.md Phase G)
- Key files: `brimm_nav_culinary.png`, `brimm_nav_feed.png`, `brimm_nav_you.png`, `brimm_nav_mixology.png`

**`backend/src/`:**
- Purpose: Cloudflare Worker source — all request handling logic
- Contains: `index.js` (dispatch), 30+ handler modules, utility helpers
- Key files: `index.js` (routing), `recipes.js` (deck scoring), `auth.js` (JWT), `vision.js` (scan), `billing.js` (Play billing)

**`backend/ingest/`:**
- Purpose: Offline batch pipelines for recipe catalog ingestion — NOT part of the live request path
- Contains: Node.js/Python scripts, raw + normalized JSON, cocktail book page caches
- Key files: `audit_v3_*/fetch_v2.cjs` (pipeline runners), `cocktails/` (historic cocktail books), `link-parser/src/` (Oracle Cloud VM server)

**`backend/migrations/`:**
- Purpose: Schema migrations applied after initial `schema.sql`
- Contains: Dated SQL files, applied via `wrangler d1 execute`
- Key files: `schema.sql` in parent `backend/` is the base; migrations layer on top

**`docs/`:**
- Purpose: Product specification, API schema, legal docs, Play Store materials
- Contains: Reference documents for product, API, privacy policy, Play Console submission
- Key files: `API_SCHEMA_v2.md`, `product-spec.md`, `privacy-policy.md`

**`image_assets/brimm/`:**
- Purpose: Master source images (before Android density resizing)
- Contains: Photorealistic PNGs for ingredients, cuisines, glasses, aisles, nav, brand kit
- Key files: Awaiting rename to `image_assets/pantrie/` per NAMING.md Phase G

**`pan-tree/`:**
- Purpose: Unknown — contains only a `.git` directory with one initial commit. Not referenced by any build file, import, or documentation. Likely an artifact from the original project layout.

**`pentest/`:**
- Purpose: Security testing resources for the backend API
- Contains: Shell scripts (curl), OWASP-aligned test playbook
- Key files: `pentest-playbook.md` (40+ tests), `curl-all-endpoints.sh`

## Key File Locations

**Entry Points:**
- `android/app/src/main/java/app/pantrie/MainActivity.kt`: Activity, PantrieNav, all route definitions
- `android/app/src/main/java/app/pantrie/PantrieApplication.kt`: Hilt root, AdMob init
- `backend/src/index.js`: Worker fetch entry point, all route dispatch

**Configuration:**
- `android/app/build.gradle.kts`: applicationId (`app.brimm`), API_BASE_URL, signing config, all dependencies
- `backend/wrangler.toml`: Worker name, D1/KV/R2 bindings, ENVIRONMENT, ALLOWED_ORIGIN
- `backend/schema.sql`: D1 database schema (authoritative)
- `NAMING.md`: Three-name coexistence rules, locked surfaces, rename migration log

**Core Logic:**
- `backend/src/recipes.js`: Deck scoring algorithm (match %, expiry boost, swipe quota)
- `backend/src/ingredient-match.js`: Pantry match engine (`buildPantryIndex`, `indexMatch`, `isStaple`)
- `android/app/src/main/java/app/pantrie/network/ApiClient.kt`: Auth interceptor (Bearer + refresh)
- `android/app/src/main/java/app/pantrie/data/PantrieDatabase.kt`: SQLCipher Room setup + Keystore integration
- `android/app/src/main/java/app/pantrie/feature/mixology/MixologyScreen.kt`: BOOTLEGGER/MIXOLOGIST mode (lines 79-87 define private palette constants; line 255 initializes `vintageMode`)
- `android/app/src/main/java/app/pantrie/ui/theme/Color.kt`: App-wide color tokens (dark editorial palette)

**Brand:**
- `android/app/src/main/java/app/pantrie/Brand.kt`: `APP_NAME`, `DOMAIN`, `SUPPORT_EMAIL`, etc.
- `android/app/src/main/res/values/strings.xml`: Android string resources for user-visible text

**Testing:**
- `pentest/pentest-playbook.md`: Manual security test playbook
- No automated test files detected in the repo (no `*.test.kt`, `*.spec.js`, or test directories)

## Naming Conventions

**Kotlin (Android):**
- Package: `app.pantrie.*` (permanent internal codename, never changes)
- Screen composables: `PascalCaseScreen` (e.g., `DeckScreen`, `MixologyScreen`)
- ViewModels: `PascalCaseViewModel` (e.g., `DeckViewModel`, `MixologyViewModel`)
- Repositories: `PascalCaseRepository` (e.g., `EntitlementRepository`, `TourRepository`)
- Files: `PascalCase.kt` matching the primary class name
- Hilt modules: `PascalCaseModule` as `object` inside the file they provide

**JavaScript (Worker):**
- Handler exports: `handleX` (camelCase noun, e.g., `handleRecipes`, `handleBeta`)
- Handler methods: `async methodName(userId, env, request)` or `(request, userId, env)`
- Files: `kebab-case.js` (e.g., `ingredient-match.js`, `core-ingredients.js`)
- Utility functions: camelCase (e.g., `readJson`, `validString`, `clientIp`)

**Android resources:**
- Drawable assets: `brimm_<content>_<slug>.png` (ingredient/cuisine/glass/aisle prefix) — pending Phase G rename to `food_`, `cuisine_`, `glass_`, `aisle_` prefixes
- Nav icons: `brimm_nav_<tab>.png` (culinary, feed, you, mixology)
- Mipmap icons: `ic_launcher*.png` at 5 density buckets + adaptive

**Directories:**
- Feature screens: lowercase, one-word where possible (`deck`, `pantry`, `mixology`, `importlinks`)
- Ingest pipelines: `audit_v3_<source>` (versioned, source-named)

## Where to Add New Code

**New feature screen (Android):**
- Create `android/app/src/main/java/app/pantrie/feature/<featurename>/`
- Add `<Feature>Screen.kt` (composable) + `<Feature>ViewModel.kt` (or co-locate)
- Add route in `PantrieNav` in `MainActivity.kt` — add `composable("route") { ... }` in the NavHost
- If it needs a bottom-nav tab: add to `BASE_TABS` or `MIXOLOGY_TAB` in `MainActivity.kt`
- If it needs a new API endpoint: add the method to `PantrieApi.kt` and the DTO to `Dtos.kt`

**New backend endpoint:**
- Add the handler logic to the appropriate existing handler file (e.g., recipe-related → `backend/src/recipes.js`), or create a new `backend/src/<feature>.js` file
- Register the route in `backend/src/index.js` — add an `if (path === '/...' && request.method === '...')` branch before the final `return err(404, 'not found')`
- If the endpoint needs a new DB table: add to `backend/schema.sql` and create a dated file in `backend/migrations/`

**New D1 migration:**
- Create `backend/migrations/YYYY-MM-DD-<description>.sql`
- Apply via `wrangler d1 execute pantrie-db-staging --remote --file=migrations/YYYY-MM-DD-<description>.sql`

**New color or typography token:**
- Add to `android/app/src/main/java/app/pantrie/ui/theme/Color.kt` or `Type.kt`
- Mirror the site's CSS variable from `speakeater-site/index.html` (source of truth per `DESIGN_SYSTEM.md`)
- Update `DESIGN_SYSTEM.md` in the same commit

**New ingest pipeline:**
- Create `backend/ingest/<source>/` with `fetch_v2.cjs` (Node.js) or a Python script
- Read `PANTRIE_ADMIN_KEY` from env (not `PANTREE_ADMIN_KEY` — per NAMING.md Phase C)
- Output normalized JSON to `backend/ingest/normalized/`
- Push to D1 via the seed Worker endpoint or a direct `wrangler d1 execute`

**New user-facing brand string (Android):**
- If referenced from Compose: add to `android/app/src/main/res/values/strings.xml`, reference via `stringResource(R.string.key)`
- If referenced from non-Compose code: add as a constant in `android/app/src/main/java/app/pantrie/Brand.kt`
- NEVER hardcode brand strings inline in Kotlin files

## Special Directories

**`.planning/`:**
- Purpose: GSD planning workspace — phases, plans, codebase analysis
- Generated: No (human + AI authored)
- Committed: Yes (in the repo)

**`android/app/build/`:**
- Purpose: Gradle build output
- Generated: Yes
- Committed: No (in `.gitignore`)

**`android/.gradle/`, `android/.idea/`, `android/.kotlin/`:**
- Purpose: Gradle cache, IntelliJ/Android Studio IDE metadata, Kotlin incremental compilation
- Generated: Yes
- Committed: No

**`backend/.wrangler/`:**
- Purpose: Local Wrangler dev state (local D1, KV, cache miniflare objects)
- Generated: Yes (by `wrangler dev`)
- Committed: No

**`backend/node_modules/`:**
- Purpose: Worker npm dependencies (Wrangler CLI)
- Generated: Yes
- Committed: No

**`backend/ingest/cocktails/_cache/`, `backend/ingest/scrape_5star/`, etc.:**
- Purpose: Downloaded raw source data — large binary/JSON files from scrapers
- Generated: Yes (by ingest scripts)
- Committed: Partially (raw data dirs are in repo; progress/status JSON files are committed)

**`backend/ingest/link-parser/node_modules/`:**
- Purpose: link-parser Node.js dependencies
- Generated: Yes
- Committed: No

**`image_assets/brimm/`:**
- Purpose: Master PNG image assets generated by `backend/ingest/generate_brimm_images.cjs`
- Generated: Yes (AI image generation, completed offline)
- Committed: Yes (large binary files, tracked in git)

---

*Structure analysis: 2026-05-12*

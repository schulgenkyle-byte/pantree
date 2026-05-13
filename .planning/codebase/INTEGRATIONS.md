# External Integrations

**Analysis Date:** 2026-05-12

## APIs & External Services

**AI / Vision:**
- Anthropic Claude Haiku (`claude-haiku-4-5-20251001`) — fridge/shelf scan vision (`backend/src/vision.js`), receipt scan, ingredient substitutions (`backend/src/substitutions.js`), review moderation (`backend/src/reviews.js`), recipe extraction from URLs (`backend/src/parser-stub.js`), nutrition estimation (`backend/src/nutrition.js`)
  - SDK/Client: raw `fetch` to `https://api.anthropic.com/v1/messages` (no SDK in Worker; `@anthropic-ai/sdk ^0.32.1` used in link-parser box only)
  - Auth: `env.ANTHROPIC_API_KEY` (Wrangler secret)
  - Global circuit breaker: `VISION_GLOBAL_DAILY` env var (default 5000 images/day across all users), tracked in KV key `vision:day-count:<epoch-day>`

**Recipe Data Sources (ingest-time only, not live):**
- TheMealDB public API (`https://www.themealdb.com/api/json/v1/1`) — ~300 CC-licensed recipes; free dev key `"1"`; fetched by `backend/ingest/fetch-themealdb.js`, normalized by `backend/ingest/normalize-themealdb.js` (no LLM needed — deterministic field mapping)
- USDA MyPlate (`myplate.gov`) — ~1,000 US government public-domain recipes; `backend/ingest/fetch-myplate.js` + `backend/ingest/normalize-myplate.js`
- HuggingFace Datasets API (`https://datasets-server.huggingface.co`) — RecipeNLG / `corbt/all-recipes` (MIT); `backend/ingest/fetch-huggingface.js`; optional `HF_TOKEN` env var for gated datasets
- Canada Food Guide (`fetch-canada-foodguide.js`) — public government nutrition data
- TheMealDB "TMDB" audit batches — additional offset fetches via admin API; `backend/ingest/audit_v3_tmdb/fetch_v2.cjs`
- Wikibooks Cookbook — public-domain recipes; `backend/ingest/fetch-wikibooks.js`

**Video Recipe Parsing (link-parser box):**
- TikTok / YouTube — scraped via Playwright browser automation (`backend/ingest/link-parser/src/tiktok-pinned.js`) for recipe extraction
- Fallback: Claude Haiku extraction if Playwright cannot extract structured data
  - Auth: `BRIMM_PARSER_SHARED_SECRET` (HMAC-SHA256 `X-Brimm-Signature` header between Worker and parser box)
  - Worker env: `PARSER_BOX_URL` (secret), `BRIMM_PARSER_SHARED_SECRET` (secret)

**Google Identity:**
- Google Sign-In (OAuth 2.0 / OIDC) — primary auth on Android via Credential Manager; ID token sent to Worker
  - Android SDK: `androidx.credentials:credentials:1.3.0` + `com.google.android.libraries.identity.googleid:googleid:1.1.1`
  - Backend: verifies Google ID tokens against Google's JWKS (`backend/src/jwks.js`, `backend/src/google-auth.js`)
  - Config: `PANTRIE_GOOGLE_CLIENT_ID` in `android/gradle.properties`; `GOOGLE_CLIENT_ID` (comma-separated, multi-client) in `backend/wrangler.toml`

## Data Storage

**Databases:**
- Cloudflare D1 (SQLite)
  - Binding: `DB` (`database_name = "pantrie-db-staging"`, ID `3a141f01-de57-4a7e-a332-e576c9df83b3`)
  - Schema: `backend/schema.sql` — tables: `user`, `user_allergy`, session, `entitlement`, `recipe`, `pantry_item`, `scan_history`, `plan`, `shopping_item`, `review`, `follow`, `waste_log`, `beta_signup`, `feedback`, `recipe_submission`, `user_recipe_library`; migrations in `backend/migrations/`
  - Client: `env.DB.prepare(sql).bind(…).first()/.all()/.run()` — D1 Workers binding, used directly in all `backend/src/*.js` handlers
- Room (Android local cache, encrypted)
  - DB file: `pantrie.db`
  - Entities: `PantryItemEntity`, `RecipeCacheEntity`, `InteractionEntity`, `ShoppingItemEntity`, `ReviewDraftEntity`
  - DAOs: `PantryDao`, `RecipeDao`, `InteractionDao`, `ShoppingDao`, `ReviewDao`
  - Config: `android/app/src/main/java/app/pantrie/data/PantrieDatabase.kt`
  - Encryption: SQLCipher 4.6.1; AES-256 passphrase derived from Android Keystore (`android/app/src/main/java/app/pantrie/crypto/KeystoreKeyManager.kt`)

**File Storage:**
- Cloudflare R2
  - Binding: `PHOTOS_BUCKET` (bucket name `pan-tree` — legacy name, LOCKED)
  - Public base URL: `env.PHOTOS_PUBLIC_BASE` (`https://pub-65a8f8e3232e458b87e6e0fe67312158.r2.dev` — configure custom domain post-launch)
  - Usage: user-submitted recipe photos (`backend/src/submissions.js`)
- Android local encrypted files
  - `EncryptedFile` (Jetpack Security Crypto) — encrypted photo cache (`android/app/src/main/java/app/pantrie/crypto/`)
  - `EncryptedSharedPreferences` — auth tokens (`android/app/src/main/java/app/pantrie/auth/TokenStore.kt`)

**Caching:**
- Cloudflare KV (binding `RATE_LIMIT_KV`, ID `077c4c0587494d66a90ede9efb58bf31`) — multipurpose: rate buckets, Google JWKS cache, nonce store, JWT revocation list, daily vision scan counters; all via `backend/src/ratelimit.js`
- DataStore Preferences (Android) — `androidx.datastore:datastore-preferences:1.1.1`; stores walkthrough completion flag (`tour_completed_v1`), scan quota state

## Authentication & Identity

**Auth Provider:**
- Google (primary) — OIDC ID token flow
  - Android: Credential Manager + Google ID (`CredentialManagerFlow.kt` at `android/app/src/main/java/app/pantrie/auth/`)
  - Backend: ID token verified via Google JWKS; HS256 JWT issued (15 min access, 30-day rolling refresh with reuse detection)
  - Implementation: `backend/src/auth.js` — `JWT_ISS = "pantrie"`, `JWT_AUD = "pantrie-mobile"`, JTI stored in KV for revocation
- Apple (stub — not yet mounted)
  - `backend/src/billing-apple.js` contains `handleAppleBilling` but import is commented out in `backend/src/index.js`
  - Required secrets: `APPLE_SHARED_SECRET`, `APPLE_BUNDLE_IDS`
  - Bundle IDs defined: `app.brimm.ios.pro.monthly`, `app.brimm.ios.pro.annual`
- Admin key (`ADMIN_KEY` Wrangler secret) — constant-time comparison for `/admin/*` routes; no user auth required
- Dev token (`DEV_TOKEN_KEY`) — header `x-dev-key`; only accepted in `ENVIRONMENT = "dev"`; hardcoded to empty string in release APKs (`android/app/build.gradle.kts`)

## Monetization

**Google Play Billing:**
- Android SDK: `com.android.billingclient:billing-ktx:7.1.1`
- Manager: `android/app/src/main/java/app/pantrie/billing/BillingManager.kt`
- Entitlements: `android/app/src/main/java/app/pantrie/billing/EntitlementRepository.kt`
- SKUs (LOCKED in Play Console — cannot rename):
  - `brimm_pro_monthly` — monthly Pro subscription
  - `brimm_pro_yearly` — annual Pro subscription
  - Note: `brimm_pro_lifetime` is deprecated; must be deactivated in Play Console
- Backend verification: `backend/src/billing.js` — calls Google Play Developer API (`androidpublisher/v3/…/purchases/subscriptions/…/tokens/…`) using service-account OAuth token
  - Internal allowed SKUs in Worker: `pantrie_pro_monthly`, `pantrie_pro_annual` (note: these differ from Play Console names — reconcile before launch)
  - Auth: `PLAY_SERVICE_ACCOUNT_JSON` Wrangler secret; service-account scoped to `https://www.googleapis.com/auth/androidpublisher`
- RTDN (Real-Time Developer Notifications): endpoint `/billing/rtdn`; Pub/Sub OIDC verification via `backend/src/jwks.js`; audience config: `RTDN_AUDIENCE`, `RTDN_SA_EMAIL` in `wrangler.toml`
- Play package name: `app.brimm` (`PLAY_PACKAGE_NAME` in `wrangler.toml`)

**AdMob:**
- Android SDK: `com.google.android.gms:play-services-ads:23.6.0`
- GDPR consent: `com.google.android.ump:user-messaging-platform:3.0.0`
- App ID (production): `ca-app-pub-8540719149057182~9475320940` (in `android/app/src/main/AndroidManifest.xml`)
- Cadence: inline ad card every N swipes (`SwipeQuotaRepository.AD_EVERY_N_SWIPES`); no ads on cooking flow
- Manager: `android/app/src/main/java/app/pantrie/billing/AdManager.kt`

**Apple Billing (stub):**
- Defined in `backend/src/billing-apple.js` but NOT imported in `backend/src/index.js` — not live
- iOS SKUs pre-defined: `app.brimm.ios.pro.monthly`, `app.brimm.ios.pro.annual`, photo packs `nightcap/bootlegger/gatsby`

## Play Integrity

- Android SDK: `com.google.android.play:integrity:1.4.0`
- Config: `CredentialManagerFlow.kt` — gated on `BuildConfig.CLOUD_PROJECT_NUMBER != 0`
- Backend: `PLAY_INTEGRITY_PROJECT` in `wrangler.toml` (currently placeholder `REPLACE_WITH_YOUR_CLOUD_PROJECT_NUMBER`)
- Status: best-effort; not blocking launch

## Monitoring & Observability

**Error Tracking:**
- None deployed. `wrangler.toml` has `[observability] enabled = true` (Cloudflare built-in Worker logs)
- `SENTRY_DSN` mentioned in `wrangler.toml` comments as optional; not configured

**Logs:**
- Backend: `console.log/warn/error` → Cloudflare Workers Logpush / Tail
- Android: `android.util.Log.i("PantrieHttp", …)` for HTTP traffic (debug only; redacts Authorization header); `Log.d/e` throughout feature code

## CI/CD & Deployment

**Hosting:**
- Android: Google Play Store (package `app.brimm`)
- Backend: Cloudflare Workers (Worker name `pantrie-backend`, URL `pantrie-backend.schulgenkyle.workers.dev` — LOCKED)
- Link-parser: Oracle Cloud VM (manual deploy; `backend/ingest/link-parser/deploy/`)

**CI Pipeline:**
- None detected — no GitHub Actions, CircleCI, or Bitrise config found

**Deploy commands:**
- Backend: `cd backend && npm run deploy` (runs `wrangler deploy`)
- Android: `./gradlew :app:bundleRelease` → manual upload to Play Console

## Network Security (Android)

**Domains pinned** (`android/app/src/main/res/xml/network_security_config.xml`):
- `speakeater.com` (all subdomains) — current brand domain
- `brimmapp.com` (all subdomains) — legacy, kept for 301 redirects
- `workers.dev` (all subdomains) — Cloudflare Worker hostname
- Cleartext traffic disabled globally (`cleartextTrafficPermitted="false"`)
- SPKI pinning currently disabled for launch (placeholder pins caused SSLPeerUnverifiedException); system CA validation enforced; re-enable before Production promotion

**CORS (Worker):**
- `ALLOWED_ORIGIN` in `wrangler.toml`: `speakeater.com`, `www.speakeater.com`, `brimmapp.com`, `www.brimmapp.com`, `pantrie.app`, `www.pantrie.app`, `localhost:5003`, `localhost:3001`

## Environment Configuration

**Required Wrangler secrets (production):**
- `JWT_SECRET` — HS256 signing key; `openssl rand -hex 32`
- `ANTHROPIC_API_KEY` — Anthropic platform key
- `PLAY_SERVICE_ACCOUNT_JSON` — Google service account JSON (Android Publisher + Play Integrity roles)
- `ADMIN_KEY` — admin dashboard + feedback triage
- `BETA_DISCORD_WEBHOOK` — Discord mirror for beta feedback (optional)
- `PARSER_BOX_URL` — HTTPS URL of Oracle Cloud parser box
- `BRIMM_PARSER_SHARED_SECRET` — HMAC secret shared with parser box; rotate quarterly
- `DEV_TOKEN_KEY` — dev only; omit in production
- `SEED_KEY` — catalog ingest only; rotate after seeding

**Required Android local.properties (not committed):**
- `PANTRIE_API_URL` — Worker base URL
- `PANTRIE_GOOGLE_CLIENT_ID` — OAuth Web client ID
- `RELEASE_KEYSTORE` / `RELEASE_KEYSTORE_PW` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PW` — keystore for release signing
- `PANTRIE_API_PIN_PRIMARY` / `PANTRIE_API_PIN_BACKUP` — SPKI pins (blank until cert-pinning re-enabled)

**Secrets location:**
- Worker secrets: Cloudflare dashboard or `wrangler secret put NAME`
- Android keystore: outside repo (per `reference_pantrie_secrets.md` memory note)
- Admin key file: `C:/Users/12566/Downloads/PANTRIE_ADMIN_KEY.txt` (outside repo, used by ingest scripts)

## Webhooks & Callbacks

**Incoming:**
- `POST /billing/rtdn` — Google Play Real-Time Developer Notifications (Pub/Sub OIDC push); verified in `backend/src/billing.js`
- `POST /beta/signup` and `POST /signup` — landing page form submission (no auth)
- `POST /webhook/job` — link-parser async job callback in `backend/ingest/link-parser/src/server.js` (parser box → Worker result delivery)

**Outgoing:**
- Discord webhook (`BETA_DISCORD_WEBHOOK`) — mirrors beta feedback on submission; `backend/src/beta.js`

---

*Integration audit: 2026-05-12*

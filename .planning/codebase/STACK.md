# Technology Stack

**Analysis Date:** 2026-05-12

## Languages

**Primary:**
- Kotlin 2.0.21 — Android app (`android/app/src/main/java/app/pantrie/`)
- JavaScript (ESM) — Cloudflare Worker backend (`backend/src/`), ingest pipelines (`backend/ingest/`)

**Secondary:**
- Java 17 (JVM target) — Kotlin compiles to JVM 17 bytecode; Room/Hilt annotation processing runs on JDK 17
- Python — `android/convert-launcher.py` (icon conversion utility, auxiliary only)

## Runtime

**Android:**
- minSdk = 26 (Android 8.0)
- targetSdk = 35 (Android 15)
- compileSdk = 35
- Java source/target compatibility = VERSION_17

**Backend — Cloudflare Worker:**
- Runtime: Cloudflare Workers (V8 isolate, `compatibility_date = "2024-12-01"`)
- No Node.js in production Worker; native `fetch`, `crypto`, `WebCrypto` APIs used throughout
- Entry point: `backend/src/index.js`

**Link-Parser (Oracle Cloud box, not on Workers):**
- Node.js >= 20 (`backend/ingest/link-parser/`)
- Runs as a persistent HTTP server (`backend/ingest/link-parser/src/server.js`)

## Package Manager

**Backend / Ingest:**
- npm (backend `package-lock.json` present)
- Lockfile: `backend/package-lock.json` — present and committed

**Android:**
- Gradle 8.x (wrapper at `android/gradlew`; AGP 8.7.3)
- Version catalog: `android/gradle/libs.versions.toml`

## Frameworks

**Core (Android):**
- Jetpack Compose BOM `2024.12.01` — entire UI is Compose; no XML layouts
- Hilt 2.52 — dependency injection across all modules
- Room 2.6.1 + SQLCipher 4.6.1 — encrypted local database (`android/app/src/main/java/app/pantrie/data/PantrieDatabase.kt`)
- Retrofit 2.11.0 + OkHttp 4.12.0 — REST client to Cloudflare Worker
- Kotlinx Serialization 1.7.3 — JSON ser/de (replaces Gson/Moshi)
- Navigation Compose 2.8.5 — in-app routing
- Lifecycle / ViewModel 2.8.7 — MVVM state management

**Core (Backend):**
- No web framework — raw Cloudflare Workers `fetch` handler in `backend/src/index.js`
- Hand-rolled router (path + method string matching, ~300 routes)

**Link-Parser (Node.js box):**
- Fastify 5.1.0 — HTTP server
- Playwright 1.49.0 — browser automation for TikTok/YouTube page scraping
- Anthropic SDK `@anthropic-ai/sdk ^0.32.1` — Claude Haiku extraction fallback
- Pino 9.5.0 — structured logging
- AJV 8.17.1 — JSON Schema validation

**Testing:**
- Not detected (no Jest/Vitest config; no `.test.` or `.spec.` files found in either `backend/src/` or `android/`)

**Build/Dev:**
- Wrangler `^4.84.1` — Worker dev server, deploy, D1/KV/R2 management (`backend/package.json`)
- KSP `2.0.21-1.0.28` — Kotlin Symbol Processing for Room and Hilt annotation processors
- Android Gradle Plugin (AGP) 8.7.3

## Key Dependencies

**Critical (Android):**
- `net.zetetic:sqlcipher-android:4.6.1` — AES-256 encryption for local Room DB; passphrase from Android Keystore (`android/app/src/main/java/app/pantrie/crypto/KeystoreKeyManager.kt`)
- `com.android.billingclient:billing-ktx:7.1.1` — Google Play Billing; manages `brimm_pro_monthly` / `brimm_pro_yearly` SKUs
- `com.google.android.play:integrity:1.4.0` — Play Integrity attestation (best-effort; gated on `CLOUD_PROJECT_NUMBER != 0`)
- `androidx.credentials:credentials:1.3.0` + `com.google.android.libraries.identity.googleid:googleid:1.1.1` — Google Sign-In via Credential Manager
- `com.google.android.gms:play-services-ads:23.6.0` + `com.google.android.ump:user-messaging-platform:3.0.0` — AdMob + GDPR consent
- `com.google.mlkit:barcode-scanning:17.3.0` — on-device barcode recognition

**Critical (Backend):**
- Cloudflare D1 (binding `DB`) — primary relational store; SQLite-compatible
- Cloudflare KV (binding `RATE_LIMIT_KV`) — rate buckets, JWKs cache, nonces, revocation list, daily scan counters
- Cloudflare R2 (binding `PHOTOS_BUCKET`, bucket `pan-tree`) — user-submitted recipe photos

**Infrastructure (Android):**
- `androidx.camera:camera-*:1.4.1` (CameraX) — fridge/shelf scanning
- `io.coil-kt:coil-compose:2.7.0` — async image loading in Compose
- `androidx.work:work-runtime-ktx:2.10.0` + `androidx.hilt:hilt-work:1.2.0` — background sync jobs
- `androidx.datastore:datastore-preferences:1.1.1` — lightweight preference persistence (walkthrough state, scan counters)
- `androidx.security:security-crypto:1.1.0-alpha06` — EncryptedSharedPreferences for tokens
- `androidx.biometric:biometric-ktx:1.2.0-alpha05` — optional biometric lock

## Configuration

**Android — build-time:**
- `android/gradle.properties` — committed defaults (`PANTRIE_API_URL`, `PANTRIE_GOOGLE_CLIENT_ID`, empty pins)
- `android/local.properties` — gitignored overrides (keystore paths, dev token key, real SPKI pins)
- `android/app/build.gradle.kts` — reads both via `localProp()` helper; injects as `BuildConfig` fields at compile time
- Key `BuildConfig` fields: `API_BASE_URL`, `GOOGLE_SERVER_CLIENT_ID`, `API_PIN_PRIMARY`, `API_PIN_BACKUP`, `CLOUD_PROJECT_NUMBER`, `DEV_TOKEN_KEY` (empty string in release builds)

**Backend — runtime:**
- `backend/wrangler.toml` — non-secret vars (`ENVIRONMENT`, `GOOGLE_CLIENT_ID`, `ALLOWED_ORIGIN`, `PLAY_PACKAGE_NAME`, `VISION_GLOBAL_DAILY`, `PHOTOS_PUBLIC_BASE`)
- Wrangler secrets (never committed): `JWT_SECRET`, `ANTHROPIC_API_KEY`, `PLAY_SERVICE_ACCOUNT_JSON`, `DEV_TOKEN_KEY`, `SEED_KEY`, `ADMIN_KEY`, `BETA_DISCORD_WEBHOOK`, `PARSER_BOX_URL`, `BRIMM_PARSER_SHARED_SECRET`

**Build:**
- `android/app/build.gradle.kts` — single-module Android app config
- `android/gradle/libs.versions.toml` — version catalog (all lib/plugin versions centralized here)
- `android/settings.gradle.kts` — single-module project (`include(":app")`)

## Platform Requirements

**Development:**
- Android Studio Ladybug+ (for Compose tooling)
- JDK 17
- Gradle wrapper generated by Android Studio on first open (JAR not committed)
- Node.js 20+ for backend and ingest scripts
- Wrangler CLI: `npx wrangler` or global install

**Production:**
- Android: Google Play Store; `applicationId = "app.brimm"` (locked)
- Backend: Cloudflare Workers (free/paid plan); D1 + KV + R2 required
- Link-parser: Oracle Cloud VM (or any Linux box with Node 20+ and Chromium)

---

*Stack analysis: 2026-05-12*

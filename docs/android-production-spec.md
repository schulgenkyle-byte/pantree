# pan-tree — Production Architecture & Play Store Compliance Specification

_see it. save it. savor it._

_Version 0.1 · Android-native · Production-ready baseline_

---

## 0. What this document is (and isn't)

**Is**: The architecture and compliance spec for a production Android build of pan-tree. Every decision maps to a specific implementation so engineers don't have to guess. Security and privacy controls are baked in from day one — not retrofitted.

**Is not**: A runnable codebase. The sample code is illustrative of the pattern; it still needs to be wired into a real Gradle project with the actual dependencies, Hilt modules, backend endpoints, and a Play Console configuration. Plan on 3–5 months to v1 public launch at a realistic solo pace, longer if backend and ops are greenfield.

This doc assumes the pan-tree product spec (v2) as the feature contract. If the feature list changes, sections 2 (data model), 6 (payments), and 7 (backend) update.

---

## 1. Stack decisions at a glance

| Concern | Choice | Why |
|---|---|---|
| Language | Kotlin 2.0+ | Android standard, null-safe, coroutines |
| UI | Jetpack Compose (Material 3) | Modern declarative UI; aligns with editorial mockup typography |
| Min SDK | 26 (Android 8.0) | ~97% of active devices; avoids legacy biometric APIs |
| Target SDK | 35 (Android 15) | Play Store requires within 1 year of release |
| DI | Hilt | Standard for Android; compile-time verified |
| Async | Kotlin Coroutines + Flow | Structured concurrency, reactive streams |
| Local DB | Room + SQLCipher (via Net Cipher or Commonsware SQLCipher-android) | Encrypted at rest; Room gives type-safe queries |
| Key/Value | DataStore Preferences (Proto) | Replaces SharedPreferences; supports encryption via Tink |
| Crypto primitives | Android Keystore + AndroidX Security (Tink-based) | Hardware-backed keys where available |
| Auth | Credential Manager API + Sign in with Google + Passkeys | Play-recommended modern auth surface |
| Biometric | `androidx.biometric:biometric` (BiometricPrompt) | Standardized UI, class 3 preferred |
| Networking | Retrofit + OkHttp + kotlinx.serialization | Industry default |
| TLS | TLS 1.3 minimum (OkHttp default), optional cert pinning via Network Security Config | Cert pinning done carefully or not at all |
| Images (capture) | CameraX | Lifecycle-aware, sane APIs |
| Images (pick) | Android Photo Picker (ACTION_PICK_IMAGES) | No storage permission needed |
| Background | WorkManager | Constraint-aware, survives reboots |
| Payments | Google Play Billing Library 7 | Mandatory for digital goods on Play |
| Integrity | Play Integrity API | Required for sign-in, purchase, sensitive actions |
| Crash/analytics | Firebase Crashlytics + Firebase Analytics (with privacy-safe config) OR Sentry + PostHog (EU-friendly) | Either works; must redact PII |
| Backend | TBD — recommend Kotlin (Ktor), Go, or Node (Hono/Fastify). Supabase only if self-hosted or with strong RLS | Must own auth verification and purchase verification |
| CI/CD | GitHub Actions → Play Console internal testing track | Auto-submit on merge to release branch |

---

## 2. Module and package structure

Multi-module Gradle project. Modules enforce security boundaries and keep debug code out of release.

```
pan-tree/
├── app/                          # Application module, DI wiring, navigation graph
├── core/
│   ├── core-ui/                  # Compose theme, design tokens, shared components
│   ├── core-data/                # Repository interfaces, DTO mappers
│   ├── core-database/            # Room DB + SQLCipher, DAO interfaces
│   ├── core-network/             # Retrofit clients, interceptors, auth
│   ├── core-crypto/              # Keystore wrappers, encryption utilities
│   ├── core-auth/                # Credential Manager, session management
│   └── core-testing/             # Shared test utilities
├── feature/
│   ├── feature-pantry/           # Pantry scan, item CRUD
│   ├── feature-deck/             # Tonight swipe deck
│   ├── feature-recipe/           # Recipe detail + cook mode
│   ├── feature-plan/             # Meal planner + shopping list
│   ├── feature-review/           # Review submission flow
│   ├── feature-feed/             # Social feed
│   ├── feature-profile/          # User profile, settings
│   └── feature-onboarding/       # First-run + permissions
└── build-logic/                  # Shared Gradle conventions, version catalog
```

Feature modules depend only on `core` modules. No inter-feature dependencies. This keeps blast radius small if one feature ships a bug.

---

## 3. Data model and data classification

Every field is classified. Classification determines storage location, encryption, and backup rules.

### 3.1 Classification tiers

| Tier | Definition | Storage | Encryption | Backup |
|---|---|---|---|---|
| **T1 — Highly sensitive** | Auth tokens, refresh tokens, encryption keys | Android Keystore / EncryptedSharedPreferences | Hardware-backed when available | **NEVER** |
| **T2 — Private user content** | Pantry photos, dish photos, private notes, private reviews | App-internal storage + SQLCipher DB | AES-256-GCM, key from Keystore | **NEVER** (opt-in sync only) |
| **T3 — Personal data** | Email, display name, avatar, bio, preferences, allergies, diet | SQLCipher Room DB | AES-256-GCM | Excluded by default via `dataExtractionRules.xml` |
| **T4 — Pseudonymous app data** | Recipe IDs saved, swipe history, cooked history, streak counters | Room DB (no encryption strictly required, but DB-level encryption gives it anyway) | AES-256-GCM (DB-wide) | Allowed — user can restore on new device |
| **T5 — Public content** | Public reviews (text + photo), public leaderboard entries | Backend only | HTTPS in transit; at-rest per backend standard | N/A |

### 3.2 Core tables (local, SQLCipher-encrypted)

```kotlin
// Simplified schema — see full Room entity definitions in code

user_profile           // local cache of account; T3
pantry_item            // id, name, category, quantity, unit, expires_at; T3
recipe_cache           // denormalized TheMealDB + AI-generated recipes; T4
interaction            // recipe_id, status (saved/planned/cooked/dismissed), timestamps, dismiss_reason; T4
review                 // local draft reviews before sync; T2 (contains photo path)
review_photo           // filename, local path, encryption nonce; T2
shopping_item          // id, name, quantity, aisle, checked, source, recipe_id_ref; T4
plan                   // id, name, created_at, recipe_ids; T4
follow                 // followed_user_id; T4
scan_history           // scan_id, thumbnail_ref, detected_items_json, created_at; T2
```

### 3.3 File storage

- **Pantry photos captured during scan**: `context.filesDir/scans/{scan_id}/{index}.jpg` — encrypted via `EncryptedFile` (AndroidX Security Crypto)
- **Dish photos from reviews**: `context.filesDir/reviews/{review_id}.jpg` — encrypted via `EncryptedFile`
- **No use of `MediaStore` or `getExternalFilesDir()`** for private photos — keeps them outside any media-scanning surface
- **When cloud sync is enabled and photo uploads**: TLS 1.3 transport, server stores in private S3/R2 bucket with per-user prefix, signed URL access only, client deletes local copy only after server ACK

---

## 4. Security architecture

### 4.1 Keystore and encryption hierarchy

Two-tier key hierarchy:

1. **Key Encryption Key (KEK)** — generated at first run, stored in Android Keystore, hardware-backed where available. Never leaves Keystore. Used only to wrap/unwrap DEKs.
2. **Data Encryption Keys (DEKs)** — generated per data class (one for DB, one for photos, one for preferences). Stored encrypted by KEK in a protected file. Loaded into memory on demand.

```kotlin
// core-crypto/KeystoreKeyManager.kt (illustrative)

object KeystoreKeyManager {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEK_ALIAS = "pantrie_kek_v1"

    fun getOrCreateKek(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        ks.getKey(KEK_ALIAS, null)?.let { return it as SecretKey }

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEK_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // Set true for KEK used by re-auth flows
            .setRandomizedEncryptionRequired(true)
            .setIsStrongBoxBacked(isStrongBoxAvailable())
            .build()

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(keyGenParameterSpec)
        return gen.generateKey()
    }

    private fun isStrongBoxAvailable(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
}
```

### 4.2 Database encryption

Room + SQLCipher. DB passphrase is a DEK wrapped by KEK, read at app start into RAM only.

```kotlin
// core-database/DatabaseModule.kt (illustrative)

@Provides @Singleton
fun provideDatabase(@ApplicationContext ctx: Context, keys: KeyProvider): PantrieDatabase {
    val passphrase: ByteArray = keys.getDatabasePassphrase() // bytes, never String
    val supportFactory = SupportFactory(passphrase, null, /* clearPassphrase = */ true)

    return Room.databaseBuilder(ctx, PantrieDatabase::class.java, "pantrie.db")
        .openHelperFactory(supportFactory)
        .fallbackToDestructiveMigration(false) // migrations are real; don't silently drop data
        .build()
}
```

Passphrase bytes are zeroed immediately after SQLCipher consumes them (`clearPassphrase = true`).

### 4.3 Encrypted preferences

```kotlin
// DataStore Preferences with Tink-based encryption
// Use androidx.security.crypto for EncryptedSharedPreferences if sticking with SP

val mainPrefs = EncryptedSharedPreferences.create(
    ctx,
    "pantrie_prefs",
    MasterKey.Builder(ctx, "pantrie_master")
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .setUserAuthenticationRequired(false, 0)
        .build(),
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
```

**What goes into encrypted prefs**: small secrets only. Refresh token, last sync timestamp, feature flags tied to the user. Not payment state, not bulk data.

### 4.4 File encryption for photos

```kotlin
fun savePhoto(bytes: ByteArray, scanId: String, index: Int): File {
    val dir = File(context.filesDir, "scans/$scanId").apply { mkdirs() }
    val file = File(dir, "$index.jpg")
    val encrypted = EncryptedFile.Builder(
        context, file, masterKey, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()
    encrypted.openFileOutput().use { it.write(bytes) }
    return file
}
```

### 4.5 Data-in-transit

- **TLS 1.3 enforced via `NetworkSecurityConfig.xml`**:

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system"/>
        </trust-anchors>
    </base-config>
    <!-- Debug only — allows charles/mitmproxy in dev builds -->
    <debug-overrides>
        <trust-anchors>
            <certificates src="system"/>
            <certificates src="user"/>
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```

- **Certificate pinning**: recommended only if you can maintain pin rotation reliably. Alternative is strict TLS (as above) plus server-side checks. If you do pin, use OkHttp `CertificatePinner` with ≥2 backup pins and a forced update path if the pin is wrong.

### 4.6 Session management and authentication

- **Primary**: Credential Manager API (Android 14+, back-ported) offering Sign in with Google and passkey flows.
- **Session tokens**: short-lived JWT access token (15 minute expiry) + opaque refresh token (30 day rolling expiry).
- **Access token**: held in memory only. Never persisted.
- **Refresh token**: stored in `EncryptedSharedPreferences`. On app start, app exchanges refresh for new access.
- **Logout**: clear prefs, clear Room DB user session, revoke refresh token server-side, optionally `CredentialManager.clearCredentialState()`.

```kotlin
// feature-auth/AuthFlow.kt (illustrative)

suspend fun signInWithGoogle(activity: Activity) {
    val credentialManager = CredentialManager.create(activity)
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(
            GetGoogleIdOption.Builder()
                .setServerClientId(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
                .setFilterByAuthorizedAccounts(true)
                .build()
        )
        .build()
    val result = credentialManager.getCredential(activity, request)
    val idToken = (result.credential as GoogleIdTokenCredential).idToken

    // Send to our backend, which verifies with Google's token infoEndpoint
    // and issues our own session tokens.
    val session = authApi.exchangeGoogleIdToken(idToken)
    tokenStore.saveRefreshToken(session.refreshToken)
    sessionCache.setAccessToken(session.accessToken)
}
```

### 4.7 Biometric gating on sensitive actions

Trigger BiometricPrompt (class 3 strong preferred) before any of:
- Exporting account data
- Deleting account
- Changing email / password / recovery factors
- Viewing/modifying linked payment info (Play Store handles actual payments; we gate account-level billing screen)
- Disabling cloud sync (because it triggers server-side data deletion)
- Revealing full review photos after 30-day inactivity (optional, privacy-hardening)

```kotlin
suspend fun requireBiometric(activity: FragmentActivity, reason: String): Boolean =
    suspendCancellableCoroutine { cont ->
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    cont.resume(true)
                }
                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    cont.resume(false)
                }
            })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Confirm it's you")
                .setSubtitle(reason)
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build()
        )
    }
```

### 4.8 Play Integrity API

Required for sign-in exchange, purchase flows, and redemption. Flow:

1. Client requests integrity token from `IntegrityManager` with nonce from backend.
2. Client sends `{idToken, integrityToken}` to backend `/auth/exchange`.
3. Backend verifies integrity token via Google Play Integrity API server endpoint, checks verdicts (`MEETS_DEVICE_INTEGRITY`, `MEETS_BASIC_INTEGRITY`, app licensing check), rejects if fails policy.
4. Only then issues session tokens.

```kotlin
suspend fun getIntegrityToken(nonce: String): String {
    val manager = IntegrityManagerFactory.createStandard(context)
    val request = StandardIntegrityTokenRequest.builder()
        .setRequestHash(nonce)
        .build()
    return manager.request(request).await().token()
}
```

**Policy decision**: reject only `NO_INTEGRITY`. Degrade gracefully (allow sign-in but flag for review) on `BASIC_ONLY`. Require `STRONG_INTEGRITY` for initial purchase only if verdict is reliably available on your user population — don't gate 30% of Android users out of purchasing.

---

## 5. Privacy architecture

### 5.1 Consent and permission flows

| Permission | When requested | Rationale shown |
|---|---|---|
| CAMERA | First time user taps "Snap your pantry" | "To photograph your pantry, fridge, or groceries. Photos stay on your device unless you turn on cloud sync." |
| POST_NOTIFICATIONS (Android 13+) | After first meal plan or expiry alert opt-in | "So we can remind you about expiring ingredients and planned meals." |
| — | No READ_MEDIA_IMAGES: we use Photo Picker | Photo Picker does not require permission. |
| — | No location permission | We don't need it. |
| — | No contacts permission | We don't need it. |

Reject patterns that harm compliance:
- Don't batch-request permissions up front in onboarding.
- Don't gate core app behind permissions the user can defer.
- Always give a path to do the task without the permission (manual add without camera, for example).

### 5.2 Data deletion and export (Play requirement)

- **In-app account deletion**: Settings → Account → Delete account. Requires re-auth via BiometricPrompt. Fires backend `DELETE /me` which hard-deletes within 30 days, soft-deletes immediately for user-facing features.
- **Web account deletion URL**: Play now requires this for any app with account creation. `https://pantrie.app/delete-account` — must be reachable without installing the app.
- **Data export**: Settings → Privacy → Download my data. Produces JSON archive of profile + reviews + pantry history + shopping history. Photos excluded from automated export for size (user can opt in explicitly for photo export, which takes a backend job and emails a signed URL).
- **Data retention**: review what we keep vs delete. Analytics events retained 14 months max. Server logs retained 30 days. Account data deleted on account deletion except legal hold cases (must be documented in privacy policy).

### 5.3 Backup rules (`android:allowBackup` and `dataExtractionRules.xml`)

- `allowBackup=true` in `AndroidManifest.xml` **BUT** with strict `dataExtractionRules.xml` excluding everything sensitive.

```xml
<!-- res/xml/data_extraction_rules.xml -->
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <!-- EXCLUDE everything by default -->
        <exclude domain="root" />
        <exclude domain="database" path="pantrie.db" />
        <exclude domain="database" path="pantrie.db-journal" />
        <exclude domain="sharedpref" path="pantrie_prefs.xml" />
        <exclude domain="file" path="scans/" />
        <exclude domain="file" path="reviews/" />
        <!-- INCLUDE only non-sensitive prefs that are nice to restore -->
        <include domain="sharedpref" path="ui_prefs.xml" />
    </cloud-backup>
    <device-transfer>
        <!-- Same pattern for device-to-device transfer -->
        <exclude domain="root" />
        <include domain="sharedpref" path="ui_prefs.xml" />
    </device-transfer>
</data-extraction-rules>
```

Rationale: encryption keys live in Android Keystore which is **device-bound**. If we backed up the encrypted DB to a new device, the old Keystore key wouldn't exist there, and the DB couldn't be opened. Better to exclude and have the user re-auth and resync.

### 5.4 Analytics and telemetry policy

- **No PII in event payloads**. Ever. Period.
- **Use stable pseudonymous IDs** for event correlation (app-install UUID generated locally, rotated on uninstall).
- **Property allowlist**: only approved properties can be sent. Implement as a typed `AnalyticsEvent` sealed class — no free-form `track(name, Map<String, Any>)` APIs exposed to feature code.
- **Opt-out respected**: user can disable analytics in Settings → Privacy. Must disable Crashlytics and Analytics SDK collection on disable.
- **Crashlytics redaction**: custom log redactor that strips paths, emails, any string matching a PII pattern before reporting.

```kotlin
// core-data/AnalyticsRedactor.kt (illustrative)

object Redactor {
    private val EMAIL = Regex("[A-Za-z0-9+._-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val PATH = Regex("/data/(?:user|data)/[^\\s]+")
    fun scrub(input: String): String = input.replace(EMAIL, "[email]").replace(PATH, "[path]")
}
```

### 5.5 Logging

- **Release builds**: all logs go through a single `Logger` that is a no-op in release (compile-time via BuildConfig). Crash reports redacted before upload.
- **Never log**: tokens, secrets, user content text, photo paths, email, server responses beyond HTTP status.
- **Proguard/R8**: strip Timber, Log.d/v/i/w calls in release via `-assumenosideeffects`.

---

## 6. Payments — Google Play Billing

### 6.1 SKU model

Recommended minimum set for launch:

- `pantrie_pro_monthly` — $4.99/mo subscription
- `pantrie_pro_annual` — $39/yr subscription (discounted)
- Feature gating: advanced meal planning beyond 3 meals/week, AI recipe generation beyond daily cap, unlimited scan history (default free: 30 days)

No in-app consumable products at launch. Keep billing simple.

### 6.2 Integration

```kotlin
// feature-billing/BillingRepository.kt (illustrative)

class BillingRepository @Inject constructor(
    @ApplicationContext ctx: Context,
    private val api: PantrieApi,           // our backend
    private val scope: CoroutineScope
) : PurchasesUpdatedListener {

    private val client = BillingClient.newBuilder(ctx)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    suspend fun launchPurchase(activity: Activity, sku: String) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(sku)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )).build()
        val details = client.queryProductDetails(params).productDetailsList?.first() ?: return

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(details.subscriptionOfferDetails!!.first().offerToken)
                    .build()
            ))
            .build()
        client.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK) return
        purchases?.forEach { purchase ->
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> scope.launch { verifyAndAck(purchase) }
                Purchase.PurchaseState.PENDING -> {
                    // User paid via slow payment method (cash, bank transfer in some markets).
                    // Do NOT grant entitlement until state flips to PURCHASED via a later callback.
                    scope.launch { api.recordPendingPurchase(purchase.purchaseToken) }
                }
            }
        }
    }

    private suspend fun verifyAndAck(purchase: Purchase) {
        // 1. Send purchase token to our backend
        val verification = api.verifyPurchase(
            VerifyPurchaseRequest(
                purchaseToken = purchase.purchaseToken,
                productId = purchase.products.first(),
                integrityToken = IntegrityProvider.getToken()
            )
        )
        if (!verification.valid) {
            // DO NOT acknowledge; fraud signal
            return
        }
        // 2. Only acknowledge after backend confirms
        if (!purchase.isAcknowledged) {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            )
        }
        // 3. Update local entitlement cache from server response
        entitlementStore.update(verification.entitlement)
    }
}
```

### 6.3 Server-side verification (non-negotiable)

Backend `/billing/verify` endpoint:

1. Receives `{purchaseToken, productId, integrityToken}`.
2. Calls Google Play Developer API `purchases.subscriptions.get` with service account credentials.
3. Validates: purchase token exists, SKU matches, payment state = PAYMENT_RECEIVED, not expired.
4. Validates Play Integrity token.
5. Writes entitlement record: `{user_id, sku, expires_at, auto_renewing, purchase_token}`.
6. Returns entitlement to client.

**Critical**: the client is never the source of truth for entitlement. The client asks the backend on every cold start and on every entitled-action attempt. Expensive? No — cache for 24h locally, but always re-verify server-side for the actual gate.

### 6.4 Subscription state handling

| State | Action |
|---|---|
| `PURCHASED` | Grant entitlement, ack |
| `PENDING` | Display "Payment processing" — do not grant |
| Cancelled but within paid period | Grant until `expires_at`, show "Won't renew" in settings |
| `ON_HOLD` / `IN_GRACE_PERIOD` | Grant access, show recovery banner; Google will retry billing |
| `PAUSED` | No access; show resume CTA |
| `EXPIRED` | Revoke access within 24h of verification cycle |
| Refund issued | Revoke access on next verification (backend gets Real-Time Developer Notification) |

Implement Google Play Real-Time Developer Notifications (RTDN) on the backend via Pub/Sub. Don't rely on client to report state changes.

### 6.5 What NOT to do

- No custom card handling in-app. Ever.
- No Stripe/Braintree/PayPal inside the Android app for digital subscriptions. (Physical goods / services are a different Play policy; not applicable here.)
- No off-Play payment CTAs ("Subscribe on our website for cheaper!") inside the app for digital services. This violates Play policy and gets apps removed.

---

## 7. Backend contract (must exist)

This app cannot ship securely without a backend. Minimum endpoints:

### 7.1 Auth

- `POST /auth/google-exchange` — body: `{idToken, integrityToken}`. Returns: `{userId, accessToken, refreshToken, expiresAt}`.
- `POST /auth/refresh` — body: `{refreshToken}`. Returns: new pair. Rotates refresh token.
- `POST /auth/logout` — body: `{refreshToken}`. Server revokes.
- `DELETE /me` — deletes account. Requires recent re-auth (within 5 min).

### 7.2 Purchase verification

- `POST /billing/verify` — body: `{purchaseToken, productId, integrityToken}`. Returns: `{valid, entitlement: {sku, expiresAt, autoRenewing}}`.
- `POST /billing/rtdn` — Pub/Sub push endpoint for Google's Real-Time Developer Notifications.
- `GET /me/entitlement` — current user's entitlement state. Called on app cold start.

### 7.3 Integrity verification

- `POST /integrity/nonce` — returns `{nonce}`. Bound to user session.
- `POST /integrity/verify` — called internally by other endpoints before processing sensitive actions. Not usually called directly by client.

### 7.4 User data

- `GET /me` — profile
- `PATCH /me` — update profile (bio, name, etc.)
- `POST /me/export` — queue data export job. Returns job ID. Emails signed URL when ready.
- `DELETE /me` — account deletion (see above)
- `POST /me/photos/upload-url` — returns signed upload URL for a photo. Photo goes directly to object storage, not through our API.

### 7.5 Core product endpoints

- Pantry scan: `POST /pantry/scan` — multipart image, returns `{detectedItems: [...]}`
- Recipe recommendations: `GET /recipes/deck?pantryHash=...` — returns swipe deck
- Reviews: `POST /reviews`, `GET /reviews/{id}`, `POST /reviews/{id}/report`
- Follows: `POST /follows/{userId}`, `DELETE /follows/{userId}`
- Meal plans: standard CRUD

### 7.6 Backend security baseline

- Auth: middleware verifies `Authorization: Bearer <accessToken>` on every authenticated route.
- Rate limiting: 60 req/min per user for standard endpoints; 10 req/min for sensitive (auth, verify, delete).
- Input validation: schema validation on every request body (Zod, go-validator, JSON Schema). Reject unknown fields.
- Output minimization: never return other users' PII in list endpoints.
- SQL injection: parameterized queries only. No string concatenation.
- Logging: structured logs, request ID correlation, secret redaction, 30-day retention max.
- Secrets: environment variables or managed secrets (AWS Secrets Manager, Google Secret Manager, Vault). Never in code. Never in config committed to git.
- Dependencies: automated scanning (Dependabot / Renovate). Quarterly review minimum.

---

## 8. Requirement-by-requirement mapping (direct response to Kyle's brief)

### 8.1 User data protection

| Requirement | Implementation |
|---|---|
| Minimize data collection | Collect only email (from Sign in with Google), display name, and explicit user-entered content (pantry items, recipes, reviews). No device fingerprinting. No location. No contacts. |
| Pantry/fridge photos sensitive | T2 classification. `EncryptedFile` on-device. Never uploaded unless user opts into cloud sync. |
| Local by default | Default config: sync off. Setting to enable is gated behind a consent screen explaining what happens. |
| HTTPS/TLS in transit, encryption at rest | `NetworkSecurityConfig` enforces cleartext disallowed; SQLCipher for DB; `EncryptedFile` for photos; `EncryptedSharedPreferences` for secrets. |
| No sensitive data in logs | `Redactor` utility; release builds strip `Log.*` via R8 rules; Crashlytics custom keys only for non-sensitive metadata. |

### 8.2 Local storage security

| Requirement | Implementation |
|---|---|
| Secure storage patterns | Internal app storage only. No `WRITE_EXTERNAL_STORAGE`. |
| Android Keystore | KEK stored there; hardware-backed when `isStrongBoxBacked` available. |
| Encrypt sensitive locally stored data | SQLCipher + `EncryptedFile` + `EncryptedSharedPreferences`. |
| Exclude from backup | `dataExtractionRules.xml` excludes DB, photos, encrypted prefs. |
| No plaintext secrets | Refresh tokens in `EncryptedSharedPreferences`. Access tokens in-memory only. No passwords stored. |

### 8.3 Authentication and account security

| Requirement | Implementation |
|---|---|
| Modern Android identity | Credential Manager API. |
| Passkeys / Sign in with Google | Launch with Sign in with Google + passkey support via Credential Manager. |
| Biometric re-auth on high-risk actions | BiometricPrompt (class 3 strong) on account delete, data export, sync toggle. |
| Short-lived session tokens | 15-min access tokens, rotating 30-day refresh tokens. |
| Correct logout | Revoke on server, clear local session and refresh token, `CredentialManager.clearCredentialState()`, clear in-memory user cache. |

### 8.4 Payments

Already covered in §6. Specifically:

- Google Play Billing Library 7 only
- Server-side verification via Play Developer API + Integrity token
- RTDN Pub/Sub listener for lifecycle events
- Client never trusts own state
- All states handled: PURCHASED, PENDING, CANCELLED, ON_HOLD, GRACE, PAUSED, EXPIRED, REFUNDED

### 8.5 App integrity and abuse prevention

| Requirement | Implementation |
|---|---|
| Play Integrity API | Standard request flow on sign-in, purchase verify, report review. |
| Server-side validation | Every high-risk endpoint validates Integrity token server-side. |
| Rate limiting | 60 req/min user default; 10 req/min sensitive; IP-level fallback for unauth routes. |
| Tamper / replay / root abuse | Integrity verdict gates sensitive flows; unique request nonces prevent replay; session tokens rotate. |

### 8.6 Permissions and privacy

| Requirement | Implementation |
|---|---|
| Smallest permission scope | CAMERA only. POST_NOTIFICATIONS optional and opt-in. No storage permission. |
| Not at install time | Just-in-time prompts only. |
| Photo picker / scoped access | Photo Picker (ACTION_PICK_IMAGES) for selecting existing photos — no permission needed. |
| Clear consent | Rationale screens before system prompt, explaining use and where data goes. |

### 8.7 Google Play compliance

| Requirement | Implementation |
|---|---|
| Data Safety form accurate | Maintained from authoritative data inventory (see §9). Reviewed each release. |
| In-app privacy policy link | Settings → Privacy → opens `pantrie.app/privacy` in Custom Tab. |
| Account deletion in-app | Settings → Account → Delete account, biometric-gated, calls `DELETE /me`. |
| Web deletion URL | `pantrie.app/delete-account` (required by Play). |
| Data deletion + export + control | All three in Settings → Privacy, each a real product surface not a dark pattern. |
| Documented retention | §5.2 for client; backend must document in its own spec. |

### 8.8 Secure engineering standards

| Requirement | Implementation |
|---|---|
| OWASP MASVS aligned | Self-assessment in Appendix A; annual review. |
| No hardcoded secrets | API keys via BuildConfig from environment variables; secrets from signed config server, not bundled. |
| Dev/staging/prod separation | Three `buildTypes` + three `flavors` combined to produce dev-debug, staging, production. Different package names: `app.pantrie.dev`, `app.pantrie.staging`, `app.pantrie` — can co-install. |
| Backend auth/authz/validation | §7.6. |
| Cert pinning | Not enabled initially. Revisit post-launch if needed. |
| Dependency management | Gradle version catalog (`libs.versions.toml`). Renovate bot. Quarterly manual audit. |
| Debug disabled in release | `isDebuggable=false`, `isMinifyEnabled=true`, `isShrinkResources=true`. |
| Privacy-safe crash reporting | Crashlytics with custom log redactor; opt-out in Settings. |

### 8.9 Release readiness

See §10 launch checklist.

### 8.10 Data model expectations

Covered by §3 (data classification) and §5.2 (deletion/export). Specifically:

- Email = T3 personal data
- Pantry/fridge photos = T2 private content
- Payment = Play-only + backend verification; no card storage
- Everything mappable: §9 provides the inventory that drives the Data Safety form, privacy policy, and incident response.

---

## 9. Data inventory (drives Data Safety form and privacy policy)

| Data | Type | Collected? | Purpose | Stored | Shared? | Retention | Optional? | How to delete |
|---|---|---|---|---|---|---|---|---|
| Email | Personal | Yes (on sign-in) | Account identity, comms | Backend DB | No | Until account deletion | No (required to sign in) | Delete account |
| Display name | Personal | Yes | Display to user, community features | Backend + local | Public if user reviews publicly | Until account deletion | Yes (can leave blank) | Delete account |
| Pantry photos | Private user content | Yes (on scan) | Ingredient detection | Local encrypted by default; cloud if sync enabled | No | User-controlled | Yes | Per-photo delete + "Clear scan history" |
| Dish photos (public reviews) | User content | Yes (if user submits public review) | Community feature | Backend object storage | Yes — public via app feed | Until user deletes review | Yes | Delete review |
| Dish photos (private reviews) | Private user content | Yes (if user submits private) | User's personal journal | Local encrypted | No | User-controlled | Yes | Delete review |
| Recipe interactions (saves/swipes/cooks) | App activity | Yes | Core app functionality | Backend + local | No | Until account deletion | No (required for feature) | Delete account |
| Reviews (text + rating) | User content | Yes | Community feature / personal journal | Backend + local | Public if user chooses | Until deleted | Yes | Delete review |
| Pantry inventory items | User content | Yes | Core functionality | Local encrypted; cloud if sync on | No | User-controlled | No | Delete item or delete account |
| Crash logs | Diagnostics | Yes (unless opted out) | Stability | Firebase Crashlytics | With Google | 90 days | Yes (opt-out in settings) | Clear on uninstall |
| Analytics events | App activity | Yes (unless opted out) | Product improvement | Analytics provider | With provider only | 14 months | Yes | Opt-out clears future; past anonymized |
| Device identifiers | — | NO | n/a | n/a | — | — | — | — |
| Location | — | NO | n/a | — | — | — | — | — |
| Contacts | — | NO | n/a | — | — | — | — | — |
| SMS/Call logs | — | NO | n/a | — | — | — | — | — |

---

## 10. Launch checklist

### 10.1 Pre-development (week 0–2)

- [ ] Register Google Play Developer account ($25 one-time, verification up to 2 weeks)
- [ ] Reserve package name: `app.pantrie` (and `app.pantrie.dev`, `app.pantrie.staging`)
- [ ] Draft privacy policy and host at `pantrie.app/privacy` (must be live before first internal test upload)
- [ ] Draft account deletion web page at `pantrie.app/delete-account`
- [ ] Decide backend stack + provisioning (Ktor on Fly.io / Go on Cloud Run / Node on Railway — pick one and own it)
- [ ] Set up GitHub repo with branch protection, CODEOWNERS, and Dependabot

### 10.2 Pre-alpha (week 3–10)

- [ ] Multi-module Android project scaffolded with all core modules
- [ ] CI (GitHub Actions): lint, test, build debug AAB, static analysis (Detekt + Android Lint)
- [ ] Room + SQLCipher wired and tested (encrypted DB open/close, migration plan)
- [ ] Android Keystore + EncryptedSharedPreferences + EncryptedFile utilities in `core-crypto`
- [ ] Network Security Config enforced; TLS-only
- [ ] Auth: Credential Manager + Google Sign-In + backend exchange working end-to-end
- [ ] BiometricPrompt wrapper tested on Android 9/13/14 devices
- [ ] Backend: `/auth/*`, `/me`, `/billing/verify` endpoints live on staging
- [ ] Play Integrity API integrated on client + server
- [ ] Logger with redaction; Crashlytics wired with opt-out
- [ ] `dataExtractionRules.xml` enforced

### 10.3 Pre-beta (week 11–14)

- [ ] All 3 tiers of features from the mockup spec shipped (planner, intelligence, social)
- [ ] Play Billing integrated; subscription lifecycle tested with Google's test SKUs
- [ ] Data deletion flow tested end-to-end (client → server → actual data gone)
- [ ] Data export flow tested (JSON archive, signed URL, email delivery)
- [ ] Permission rationale screens reviewed
- [ ] R8/ProGuard release config tested (app runs in release with obfuscation)
- [ ] Crash-free rate ≥99.5% over 7 days of internal testing
- [ ] Dependency vulnerability scan clean (no critical/high CVEs)
- [ ] Static analysis clean (Detekt, Android Lint, at zero errors)
- [ ] MASVS self-assessment completed; gaps documented

### 10.4 Pre-production (week 15–18)

- [ ] **Play Console submission artifacts ready**:
  - Signed release AAB (Play App Signing enrolled — Google holds upload key)
  - Screenshots (phone, tablet) per current Play requirements
  - Short + full descriptions
  - Content rating questionnaire complete
  - **Data Safety form** — every row in §9 mapped
  - Privacy policy URL live and accurate
  - Account deletion URL live
  - Contact email (developer)
- [ ] **Closed testing track** with 20 testers for ≥14 days (Play requires this for production access for new apps)
- [ ] Target SDK meets Play's current requirement (Android 15 / SDK 35 as of this writing)
- [ ] `android.permission` inventory reviewed — only what's needed, all declared in manifest
- [ ] Pen test: at minimum OWASP MASVS-L1 self-assessment; ideally third-party MASTG-based test
- [ ] Incident response plan written (who gets paged, runbook for data breach disclosure)
- [ ] Legal: TOS + Privacy Policy reviewed (real lawyer, not ChatGPT — once you're shipping paid)

### 10.5 Post-launch ongoing

- [ ] Monthly: crash-free rate, ANR rate, review trends
- [ ] Quarterly: dependency audit, security patch upgrades
- [ ] Annual: full MASVS re-assessment, privacy policy review, data retention audit
- [ ] On each release: Data Safety form accuracy check
- [ ] On each policy change: in-app notice + updated privacy policy version

---

## 11. Threat model (condensed STRIDE)

### 11.1 Assets

| Asset | Value | Impact if compromised |
|---|---|---|
| User account credentials | High | Full account takeover; can post reviews, access private data |
| Pantry/dish photos | Medium-High | Personal content leak; privacy violation |
| Auth tokens | High | Session hijack |
| Encryption keys | Critical | All encrypted local data readable |
| Payment entitlement state | Medium | Unauthorized premium access; revenue loss |
| Server-side user database | Critical | Mass PII breach; GDPR/CCPA exposure |

### 11.2 Adversaries

1. **Opportunistic attacker**: steals device, tries to access app data. Mitigation: device lock + Keystore-backed encryption + biometric re-auth.
2. **Network adversary**: Wi-Fi MITM. Mitigation: TLS 1.3, NetworkSecurityConfig cleartextTrafficPermitted=false.
3. **App-level attacker**: reverse engineers APK, finds secrets. Mitigation: no secrets in APK; R8 obfuscation; Play Integrity on sensitive flows.
4. **Rooted/hooked device attacker**: runs modified client to bypass checks. Mitigation: Play Integrity verdicts; server is source of truth.
5. **Malicious user**: tries to abuse review/social features. Mitigation: reporting, rate limits, content moderation (Claude Haiku pre-publish on images + text).
6. **Compromised dev/CI**: secrets leak from repo. Mitigation: secrets in managed store, not in code; least-privilege CI; signed commits optional.
7. **Google Play infrastructure abuse**: fake refund abuse, promo code abuse. Mitigation: Integrity + server verification + RTDN.

### 11.3 Key attack surfaces

- **AndroidManifest permissions / exported components**: audit that nothing sensitive is exported. `android:exported="false"` on all internal activities/services.
- **Deep links**: validate and sanitize every input. Don't blindly trust `Intent` extras.
- **WebView**: avoid if possible. If used, disable JS unless strictly needed, disable file access, validate URL allowlist.
- **Inter-app IPC**: don't expose content providers without permissions.
- **Backup**: covered — excluded.
- **Clipboard**: no sensitive data copied to clipboard (passwords etc.); if we add a "share recipe" feature, only recipe ID goes to clipboard.

---

## 12. Open decisions (need Kyle's input before engineers start)

1. **Backend stack** — Ktor (Kotlin, matches client language)? Go (fast, ops-friendly)? Node (ecosystem, fast to iterate)? Supabase (fastest to start, but owns auth + DB)? **Recommendation**: Ktor on Fly.io for MVP if team knows Kotlin; Supabase with caveats (self-host RLS rules are critical) if speed matters most.
2. **Analytics provider** — Firebase (convenient, Google ecosystem) vs Sentry + PostHog (EU-friendly, more privacy-forward). **Recommendation**: Firebase for crash + PostHog for product analytics, both configured privacy-safe.
3. **Auth identity** — Sign in with Google only at launch? Or add Apple Sign-In equivalent (Play allows third-party; not required like iOS)? Email/password? **Recommendation**: Sign in with Google only at launch; add others after v1.
4. **Free tier limits** — what's free, what's Pro? Impacts feature gating and billing wiring. From mockup context: 3 meals/week plan, daily AI generation cap, 30-day scan history. Pro = unlimited. **Needs confirmation**.
5. **Review moderation model** — Claude Haiku for pre-publish check (cost, latency)? Or post-publish reporting only? Recommendation: pre-publish check for images (Claude Vision), post-publish for text + user reports. Needs budget line.
6. **Cloud sync default** — stay off by default (my recommendation for privacy) or on by default (better UX continuity)? **Strong recommendation**: off by default. User enables knowingly.
7. **Data residency** — any need for EU user data to stay in EU? **If yes, backend region strategy matters from day one.** Recommendation: start US-only; add EU region when >5% EU users.

---

## 13. Deferred from v1 (explicit)

These are real features, but keep them out of launch to ship:

- Android Wear / tablet layouts
- Apple Sign-In
- SMS/email 2FA
- Multi-device cloud sync conflict resolution (keep sync simple: last-write-wins with timestamp)
- Grocery ordering APIs (Instacart, etc.) — defer until post-launch retention signal (previously discussed)
- Public API for third-party integrations
- Recipe remixing / user-generated recipes published to catalog
- Shared household pantries (multi-user per pantry)
- Voice / Assistant integration

---

## Appendix A: MASVS v2 self-assessment template (high level)

| Control | L1 | L2 | Notes |
|---|---|---|---|
| MSTG-ARCH-1: Threat model documented | ✓ | ✓ | §11 |
| MSTG-STORAGE-1: No sensitive data in logs | ✓ | ✓ | Redactor + R8 strip |
| MSTG-STORAGE-2: Uses platform secure storage | ✓ | ✓ | Keystore + SQLCipher + EncryptedFile |
| MSTG-STORAGE-3: No sensitive data in SharedPreferences unencrypted | ✓ | ✓ | EncryptedSharedPreferences |
| MSTG-CRYPTO-1: Does not rely on weak crypto | ✓ | ✓ | AES-256-GCM throughout |
| MSTG-AUTH-1: Authentication per platform best practice | ✓ | ✓ | Credential Manager |
| MSTG-AUTH-2: Session invalidated on logout/timeout | ✓ | ✓ | Short-lived access + refresh rotation |
| MSTG-AUTH-8: Biometric where appropriate | ✓ | ✓ | BiometricPrompt class 3 |
| MSTG-NETWORK-1: TLS used for all connections | ✓ | ✓ | NetworkSecurityConfig |
| MSTG-NETWORK-2: Cert validation done correctly | ✓ | ✓ | Default OkHttp |
| MSTG-PLATFORM-1: Minimal permissions | ✓ | ✓ | CAMERA + optional POST_NOTIFICATIONS |
| MSTG-PLATFORM-11: WebView securely configured | N/A | ✓ | Avoided |
| MSTG-CODE-2: Debuggable not enabled in release | ✓ | ✓ | `isDebuggable=false` |
| MSTG-CODE-3: Debug symbols stripped | ✓ | ✓ | R8 + minify |
| MSTG-CODE-9: No known vulnerable deps | ✓ | ✓ | Dependabot + quarterly audit |
| MSTG-RESILIENCE-* | — | ✓ | Play Integrity covers most resilience controls |

Fill each row with evidence link (build config, test report) before each release.

---

## Appendix B: AndroidManifest security essentials (excerpt)

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.INTERNET" />
    <!-- Only for Android 13+ notification opt-in -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- NOT requested: READ_MEDIA_IMAGES (we use Photo Picker instead) -->

    <application
        android:name=".PantrieApplication"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules_legacy"
        android:networkSecurityConfig="@xml/network_security_config"
        android:usesCleartextTraffic="false"
        android:hasFragileUserData="true"
        android:requestLegacyExternalStorage="false"
        android:theme="@style/Theme.Pantrie">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask">
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="https" android:host="pantrie.app" />
            </intent-filter>
        </activity>

        <!-- All other activities/services android:exported="false" unless required -->

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
                       android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>
```

---

## Appendix C: Release build configuration (app/build.gradle.kts excerpt)

```kotlin
android {
    namespace = "app.pantrie"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.pantrie"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            // Load from ~/.gradle/gradle.properties or CI secret env vars.
            // NEVER commit real values.
            storeFile = file(System.getenv("RELEASE_KEYSTORE") ?: "keystore.jks")
            storePassword = System.getenv("RELEASE_KEYSTORE_PW")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PW")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
```

And `proguard-rules.pro` to strip logs:

```pro
# Strip log calls in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}
-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep Retrofit/kotlinx.serialization models
-keep,allowobfuscation,allowshrinking class kotlin.reflect.jvm.internal.impl.** { *; }
-keep @kotlinx.serialization.Serializable class **

# Keep Room entities
-keep @androidx.room.Entity class * { *; }
```

---

## Appendix D: Privacy policy required sections (checklist)

At minimum, the live privacy policy must cover:

1. **Who we are** — legal entity name, contact email, registered address (required by GDPR if EU users)
2. **What data we collect** — mirrors §9 data inventory exactly
3. **Why we collect it** — stated purpose per data type
4. **How long we retain it** — per data type
5. **Who we share with** — Google (Play, Firebase), [backend host], [analytics provider]. No data broker sharing. No sale of personal data.
6. **User rights** — access, correction, deletion, export, opt-out of analytics, withdraw consent for cloud sync
7. **How to exercise rights** — in-app flows + email fallback
8. **Security measures** — high-level description of encryption, auth, storage
9. **International transfers** — if data leaves user's country (Standard Contractual Clauses for EU→US if applicable)
10. **Children's use** — not intended for under 13 (COPPA) / 16 (GDPR)
11. **Changes to this policy** — notification method
12. **Date last updated**

---

## Appendix E: What I recommend as the very next artifact

After you've reviewed this doc and approved the stack choices, the next concrete deliverable is:

**Kotlin scaffold repo** — a working multi-module Gradle project with:
- All core modules created and empty
- `core-crypto` fully implemented with Keystore utilities
- `core-auth` with Credential Manager sign-in end-to-end (needs a backend endpoint to talk to; can mock initially)
- `core-database` with an encrypted Room DB with a sample entity
- `feature-onboarding` with permissions rationale + first-run
- GitHub Actions CI building release AAB
- README with "how to run locally" instructions

I can produce this (it would be a repository of ~40–60 files, delivered as a zip or pushed to a repo). It's the foundation a dev continues from.

After that: backend scaffolding (OpenAPI spec + reference implementation in chosen stack).

---

_End of spec._

# Coding Conventions

**Analysis Date:** 2026-05-12

## Naming Patterns

**Kotlin files:**
- Screens: `<Feature>Screen.kt` — e.g. `BarcodeScreen.kt`, `DeckScreen.kt`, `PantryScreen.kt`
- ViewModels: `<Feature>ViewModel.kt` — e.g. `BarcodeViewModel.kt`, `MealPrepViewModel.kt`
- Repositories: `<Domain>Repository.kt` — e.g. `EntitlementRepository.kt`, `SwipeQuotaRepository.kt`, `TourRepository.kt`
- Data Transfer Objects: grouped in `Dtos.kt` or `<Feature>Dtos.kt` — e.g. `android/app/src/main/java/app/pantrie/network/Dtos.kt`
- Room entities: in `Entities.kt` — `android/app/src/main/java/app/pantrie/data/entities/Entities.kt`
- Composable helper files: `<Purpose>.kt` — e.g. `CardChrome.kt`, `BrandImage.kt`, `IngredientImage.kt`

**Kotlin identifiers:**
- Classes/objects: PascalCase — `BarcodeViewModel`, `AuthInterceptor`, `NetworkModule`
- Functions (Compose): PascalCase — `BarcodeScreen`, `DeckCard`, `ProUpgradeCard`
- Functions (non-Compose): camelCase — `refreshHome()`, `onCodeDetected()`, `singleFlightRefresh()`
- Constants (top-level): SCREAMING_SNAKE_CASE — `SKU_PRO_MONTHLY`, `SKU_PRO_YEARLY`, `FREE_WEEKLY`
- Private state flows: `_camelCase` with public `camelCase` alias — e.g. `_state`/`state`, `_home`/`home`
- Sealed interface cases: PascalCase — `BarcodeUiState.Scanning`, `BarcodeUiState.Found`

**JavaScript files (backend Worker, `backend/src/`):**
- Modules: kebab-case — `apple-jwks.js`, `auth-apple.js`, `core-ingredients.js`, `ingredient-match.js`
- Handler functions: `handle<Domain>` — `handleVision`, `handleRecipes`, `handleBilling`
- Exported helpers: camelCase functions — `json()`, `err()`, `uid()`, `readJson()`

**JavaScript files (ingest scripts, `backend/ingest/`):**
- Scripts: snake_case or kebab-case with version suffixes — `audit_v2_cocktails.cjs`, `audit_v3_batch_e.cjs`
- All ingest scripts use CommonJS (`require`/`module.exports`) via `.cjs` extension; Worker uses ESM (`import`/`export`) via `.js`

## Code Style

**Formatting (Kotlin):**
- No ktlint, detekt, or Spotless plugin detected in `android/app/build.gradle.kts`. Formatting is manual / Android Studio default.
- `android { lint { abortOnError = false } }` — lint errors are reported but do not fail builds.
- Wildcard imports are used extensively in screen files (e.g. `import androidx.compose.foundation.layout.*`, `import androidx.compose.material3.*`). This is the observed norm for Compose screen files.
- Non-screen files (ViewModels, Repositories) use explicit per-symbol imports.

**Formatting (JavaScript — Worker):**
- No ESLint, Prettier, or Biome config found. Style is manual.
- Consistent 2-space indentation throughout `backend/src/`.
- Destructured named imports from local modules: `import { json, err, readJson } from './util.js';`
- Trailing semicolons used throughout.

**Formatting (JavaScript — ingest `.cjs`):**
- CommonJS `require()` style; no formatter config.
- Scripts are standalone executables with shebang `#!/usr/bin/env node`.
- Style varies per script (written at different times); no enforcement.

## Import Organization

**Kotlin (observed pattern in screen files):**
1. `@file:OptIn(...)` annotation (if needed)
2. `package` declaration
3. Blank line
4. Android framework imports (`android.*`, `androidx.*`) — often wildcarded within a package
5. Compose imports (`androidx.compose.*`) — often wildcarded
6. Material icons imports
7. Hilt imports (`androidx.hilt.*`, `dagger.hilt.*`)
8. Lifecycle/ViewModel imports
9. App-internal imports (`app.pantrie.*`)
10. Third-party library imports (`coil.*`, `retrofit.*`, etc.)
11. Java standard library imports (`java.*`, `javax.*`)

See `android/app/src/main/java/app/pantrie/feature/barcode/BarcodeScreen.kt` for a clean example of this ordering.

**JavaScript (Worker):**
- Named destructure imports from local modules first, then external packages (though external packages are rare — the Worker uses Web APIs and CF bindings, not npm packages).

**Path Aliases:**
- None. Kotlin uses full package paths (`app.pantrie.*`). JS uses relative paths (`./util.js`, `./ratelimit.js`).

## Error Handling

**Kotlin — ViewModels:**
Use `runCatching { }` for network calls, then `.onSuccess { }` / `.onFailure { }`:

```kotlin
// android/app/src/main/java/app/pantrie/feature/barcode/BarcodeViewModel.kt
runCatching {
  api.addPantryItem(PantryAddRequest(...))
}.onSuccess { _state.value = BarcodeUiState.Added }
  .onFailure { _state.value = BarcodeUiState.Error(it.message ?: "Add failed") }
```

Some older/larger ViewModels use `try { } catch (e: Exception) { }` — e.g. `BarcodeViewModel.onCodeDetected()`. `runCatching` is preferred in newer code.

UI state is expressed as a sealed interface with an `Error` variant — e.g. `BarcodeUiState.Error(message: String)`. The Screen composable collects state and renders the error case.

**Kotlin — Sync/Background tasks:**
Errors are logged via `android.util.Log.e(tag, msg, throwable)` and surfaced to a separate `_syncMessage` state flow for user display — see `android/app/src/main/java/app/pantrie/feature/pantry/PantryScreen.kt` (PantryViewModel).

**JavaScript (Worker):**
The `err(status, message, extra?)` helper from `backend/src/util.js` is used uniformly across all route handlers. It returns a `Response` with JSON body `{ ok: false, error: message }` and sets security headers.

Fatal configuration errors at startup (`backend/src/index.js`) are reported via `console.error('config fatal', ...)` and return HTTP 503.

Route-level try/catch at the top of `fetch()` in `index.js` provides a last-resort fallback — unhandled exceptions return a generic 500 error.

**JavaScript (ingest `.cjs`):**
No standardized error handling. Scripts use ad-hoc `try/catch` or rely on Node.js default process-exit on exception. Not expected to be production-hardened.

## Logging

**Kotlin — logging framework:** `android.util.Log` (stdlib). No Timber dependency.

**Patterns:**
- Tag convention: `"Pantrie<Module>"` — e.g. `"PantrieHttp"` (ApiClient.kt), `"PantrieSync"` (PantryScreen.kt)
- HTTP logging is routed through `android.util.Log.i("PantrieHttp", msg)` in the OkHttp interceptor (`android/app/src/main/java/app/pantrie/network/ApiClient.kt`)
- `Log.BASIC` in debug builds; no logging in release (`BuildConfig.DEBUG` gate)
- Auth headers are redacted: `redactHeader("Authorization")`, `redactHeader("Cookie")`

**JavaScript (Worker):**
- `console.error()` used sparingly — only for startup config validation failures and unexpected exceptions (`backend/src/index.js`, `backend/src/submissions.js`)
- Route handlers do not log per-request; rely on Cloudflare's automatic Worker log tail
- `backend/src/vision.js` uses `console.log()` for Vision API call tracing (highest-cost path)

**JavaScript (link-parser):**
- Uses a dedicated `log` wrapper from `backend/ingest/link-parser/src/logger.js` (backed by `pino`)

## Brand Strings

**The rule (from NAMING.md):** Any string visible to users containing the brand name must come from one canonical source.

**Android:**
- User-facing constants: `android/app/src/main/java/app/pantrie/Brand.kt` — `Brand.APP_NAME`, `Brand.PRO_NAME`, `Brand.TAGLINE`, `Brand.SUPPORT_EMAIL`
- String resources: `android/app/src/main/res/values/strings.xml` for strings used via `stringResource(R.string.*)`
- **Do not hardcode** `"Speakeater"` or `"Brimm"` directly in Kotlin source. Current audit: `grep "\"Speakeater\""` returns only the `Brand.kt` definition — the rule is followed.
- References use either `Brand.APP_NAME` (in Kotlin/Compose) or `app.pantrie.Brand.APP_NAME` (fully-qualified, in files without a direct import — e.g. `BetaFeedbackSheet.kt`, `DeckScreen.kt`)

**Backend:** No `backend/src/brand.js` exists yet (NAMING.md Phase H pending). User-visible backend strings (error messages) are plain string literals in the route handlers.

## Compose Conventions

**Composable naming:** PascalCase, same name as the `.kt` file that contains the screen — `BarcodeScreen`, `PantryScreen`, `DeckScreen`. Sub-composables within a file are also PascalCase.

**State hoisting pattern:**
- ViewModel owns state as `StateFlow` (`MutableStateFlow` privately, `.asStateFlow()` publicly)
- Screen composable calls `hiltViewModel()` to inject VM
- State collected with `collectAsState()`: `val state by vm.state.collectAsState()`
- Screen receives navigation callbacks as lambdas: `onBack: () -> Unit`, `onDone: () -> Unit`

**ViewModel injection:** Hilt is used throughout. `@HiltViewModel` + `@Inject constructor`. Modules are `@Module @InstallIn(SingletonComponent::class) object`.

**Sealed UI state:** Preferred pattern for async screens. Each feature defines a `sealed interface <Feature>UiState` with variants like `Loading`, `Found(data: T)`, `Error(message: String)`. See `android/app/src/main/java/app/pantrie/feature/barcode/BarcodeViewModel.kt` for a clean example.

**Theme tokens:** Use tokens from `android/app/src/main/java/app/pantrie/ui/theme/` — import via wildcard `import app.pantrie.ui.theme.*`. Use `Ink`, `InkSoft`, `Brass`, `Paper`, `Paper2`, `Paper3` etc. Do NOT use `Cream`/`CreamAlt`/`Beige` (legacy aliases being retired in Phase 2 redesign per DESIGN_SYSTEM.md).

**`@OptIn` file-level annotations:** Used when entire file uses experimental APIs — e.g. `@file:OptIn(ExperimentalMaterial3Api::class)` at the top of BarcodeScreen.kt.

## Comments

**When to comment:**
- Explain non-obvious design decisions and trade-offs (not what the code does, but why)
- Document locked constraints that cannot be changed without external consequences — e.g. SKU ids, API URLs, DB bindings
- Mark migration todos with NAMING.md phase references — e.g. `// TODO(naming): NAMING.md Phase G renames...`
- Explain quota/cost decisions in backend — e.g. `FREE_WEEKLY = 5` is commented with business rationale

**KDoc/JSDoc:**
- Used on classes and managers where the contract is non-obvious — e.g. `AuthInterceptor` KDoc in `ApiClient.kt`, `BillingManager` class KDoc
- Inline `//` comments preferred for short explanations
- No JDoc on trivial data classes or simple composables

## Module Design

**Exports (Kotlin):** Kotlin `object` singletons for stateless utilities (e.g. `Brand`). Hilt-injected classes for stateful services. No manual companion object factories — Hilt handles construction.

**Exports (JS Worker):** Named ES module exports. Each `backend/src/*.js` handler file exports a `handle<Domain>` function; `util.js` exports pure helpers. No default exports except `index.js` which exports the Worker entry point object.

**Barrel files:** Not used. Imports are always direct file references.

---

*Convention analysis: 2026-05-12*

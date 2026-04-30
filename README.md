# pan-tree — Full Build

_see it. save it. savor it._

Pantry-first meal app. Snap your shelves, get recipes ranked by what you actually have, plan your week in day/slot cards, shop smart, cook with auto-advancing step-by-step mode, minimize food waste.

```
pantrie-build/
├── demo/pantrie-demo.html      Interactive single-file mockup (stakeholder demo)
├── backend/                    Cloudflare Worker + D1 + KV — auth, vision, recipes, plans, shopping, reviews, billing
├── android/                    Kotlin + Jetpack Compose scaffold with SQLCipher, Keystore, Credential Manager, Play Billing
├── pentest/                    curl scripts + security playbook
├── docs/                       Product spec, Android spec, privacy/data-safety drafts
└── README.md                   You are here
```

---

## 1. Show the demo (30 seconds)

Open `demo/pantrie-demo.html` in any browser. No server, no install. In-memory state. Refresh resets.

Try:
- **Home** — 5-day scan nudge banner, horizontal deck preview with match % + "uses expiring" badges
- **Tonight** — swipe cards. Right saves + auto-adds missing to shopping list. Left skips silently
- **Shop → Menu** — full week view, 7 day cards × 3 slot rows. Tap empty slot for suggestions grouped by expiring/saved/matches
- **Shop → Menu → Plan it for me** — auto-fills dinners with reasoning ("Uses your expiring spinach")
- **Shop → Shopping list** — expiring items expandable with Restock/Use, "Unlock more recipes" recommendations
- **Pantry** — expiring rows have red left borders; tap any to edit
- **Recipe sheet → Begin cooking** — auto-advancing slides, swipe for prev/next, pause/play, huge buttons
- **Settings** (tap avatar) — diets, allergies, 35 cuisines, meal types, cooking styles

---

## 2. Deploy the backend (10 minutes)

```bash
cd backend
npm install
npx wrangler login

npx wrangler d1 create pantrie-db              # copy ID to wrangler.toml
npx wrangler kv namespace create RATE_LIMIT_KV # copy ID to wrangler.toml

npx wrangler secret put ANTHROPIC_API_KEY      # sk-ant-...
npx wrangler secret put JWT_SECRET             # openssl rand -hex 32
npx wrangler secret put SEED_KEY               # any random string
npx wrangler secret put DEV_TOKEN_KEY          # delete before launch

# Edit wrangler.toml → set GOOGLE_CLIENT_ID (Web OAuth client ID)
npx wrangler d1 execute pantrie-db --file=schema.sql --remote
npx wrangler deploy

SEED_KEY=<key> BASE=<url> ../pentest/seed-recipes.sh
```

---

## 3. Pen test

```bash
# Dev session (requires DEV_TOKEN_KEY)
curl -X POST <url>/auth/dev-token -H "x-dev-key: <DEV_TOKEN_KEY>" -d '{}'

# Full endpoint suite
TOKEN=<accessToken> BASE=<url> ./pentest/curl-all-endpoints.sh

# Real image through vision
TOKEN=<accessToken> BASE=<url> IMG=~/fridge.jpg ./pentest/scan-image.sh
```

40+ tests in `pentest/pentest-playbook.md` — 10 categories, exact commands, expected results.

---

## 4. Android

```bash
cd android
# Edit app/build.gradle.kts: API_BASE_URL + GOOGLE_SERVER_CLIENT_ID
# Open in Android Studio (Ladybug+, JDK 17). First sync generates Gradle wrapper.
./gradlew :app:assembleDebug
```

Ships with: Keystore-backed SQLCipher, EncryptedFile for photos, EncryptedSharedPreferences for tokens, Credential Manager + Google Sign-In, Play Integrity (best-effort), Retrofit + auth interceptor + refresh-on-401, CameraX + Photo Picker with 1600px downscale.

Scan flow is end-to-end wired. Deck / Plan / Review / Shop / Mine screens are stubs — reference the mockup.

---

## 5. What's in the mockup but not yet in the scaffolds

Mockup is the spec. Backend + Android have core plumbing. Still to wire:

- Week plan day/slot storage + full week screen
- Smart meal planner (expiring-optimization algorithm) + proposal review UI
- Recommended groceries (unlock-count ranking) + in-app card
- Scan nudge (last-scan timestamp) + home banner
- Full dietary/cuisine/meal-type/cooking-style filtering (schema is there, wire the queries)
- My recipes + collections + drag-reorder
- Recipe URL import (Worker fetches + Haiku extracts)
- Cook count tracking + "X cooks" pills
- Follow / feed / reports (schema present, wire handlers + feed UI)

Detailed plans in `docs/product-spec.md` and `docs/android-production-spec.md`.

---

## 6. Known gaps (deliberate)

- Gradle wrapper JAR not included — Android Studio generates on first open
- `/billing/verify` ships as dev stub — swap service-account version before launch
- Play Integrity is best-effort; set `CLOUD_PROJECT_NUMBER` in `CredentialManagerFlow.kt`
- Only scan flow end-to-end on Android; other screens are stubs
- No cert pinning (revisit post-launch per spec §4.5)
- **Delete `DEV_TOKEN_KEY` before production** (`wrangler secret delete DEV_TOKEN_KEY`)

---

## 7. Recipe data strategy

Don't live-connect third-party recipe APIs for the base catalog. Bulk-ingest once, own the data.

**Safe to ingest + redistribute:**
- **TheMealDB** — 300 recipes, $15 one-time for dev access + full export
- **USDA MyPlate Kitchen** — ~1,000 recipes, US government work (public domain)
- **Wikibooks Cookbook** — ~2,800 public-domain recipes

**Pipeline:**
1. Download raw JSON/HTML
2. Normalize via Claude Haiku batch — parse ingredients `{name,qty,unit,aisle}`, classify cuisine/meal_type/cooking_style, compute cost/calories, flag allergens
3. Write to D1 via seed Worker

Cost estimate: ~$20 in Anthropic fees to normalize 3,000 recipes.

**Do NOT** base catalog on Spoonacular/Edamam/AllRecipes scrapes without legal review.

**Live APIs** only for on-demand features:
- URL import (user pastes → Worker fetches → Haiku extracts → saves to their my_recipes)
- Optional Pro-tier "search beyond my catalog" via Spoonacular (paywall protects cost)

---

## 8. Launch checklist

- [ ] Google Play Developer account ($25)
- [ ] Reserve `app.brimm` package (DONE — Play Store identity)
- [ ] Privacy policy live at brimmapp.com/privacy (use `docs/privacy-policy.md`)
- [ ] Account deletion page at brimmapp.com/delete-account
- [ ] Data Safety form filled from `docs/data-safety.md`
- [ ] Release keystore → enroll in Play App Signing
- [ ] Set `CLOUD_PROJECT_NUMBER` for Play Integrity
- [ ] Swap real Play Billing service-account verifier
- [ ] 14-day closed testing, ≥20 testers
- [ ] Delete `DEV_TOKEN_KEY` secret
- [ ] Third-party pen test (OWASP MASVS L1)
- [ ] Ingest recipe catalog (TheMealDB + USDA + Wikibooks batch)

---

pan-tree · v0.5 · Editorial design · Cloudflare + Android native

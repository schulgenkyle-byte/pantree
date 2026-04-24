# pan-tree — Play Console Submission Pack

Everything you need to paste into Google Play Console to get pan-tree onto
Internal Testing (instant) → Closed Testing (1 day review) → Production (3-7 day review).

## Files you'll upload

| File | Purpose | Location |
|---|---|---|
| `app-release.aab` | **The app bundle Play Store ingests** | `android/app/build/outputs/bundle/release/app-release.aab` |
| Feature graphic (1024×500 PNG) | Store listing banner | NEED TO CREATE |
| Phone screenshots (2-8) | Store listing | NEED TO CAPTURE |
| App icon (512×512 PNG) | Store listing | `android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp` (convert to PNG) |

## Step 1 — Create Play Console account

1. Go to **https://play.google.com/console/signup**
2. Accept developer agreement
3. Pay the **$25 one-time registration fee** (credit card)
4. Complete identity verification — Google requires a government ID. **This takes 1-3 business days.** Start here first so it runs in parallel with everything else.

## Step 2 — Create the app

1. In Play Console, click **Create app**
2. App name: `pan-tree`
3. Default language: English (United States)
4. App or game: **App**
5. Free or paid: **Free**
6. Accept the declarations → Create

## Step 3 — App content

Left nav → **Policy** → **App content**. Fill out each card:

### Privacy policy
URL: Host `docs/privacy-policy.md` as a public page. Easiest option is GitHub Pages:
1. In GitHub, go to **schulgenkyle-byte/pantree** → Settings → Pages
2. Source: `main` branch, folder: `/docs`
3. Save. GitHub will publish at `https://schulgenkyle-byte.github.io/pantree/privacy-policy.html`
4. Convert `privacy-policy.md` → `privacy-policy.html` (or just rename to `.html`, Markdown renders fine in raw form)
5. Paste that URL into Play Console.

### Ads
**No**, this app contains no ads.

### App access
**All functionality is available without special access.** (No demo account needed — all features work with a standard Google sign-in.)

If they require a test account for review, provide: a regular Gmail you control. Play doesn't actually use it unless they encounter gated features.

### Content rating
Run the IARC questionnaire. Answer honestly:
- **Violence, sexual, profanity, drugs, gambling, etc.: No**
- You'll get an **Everyone** rating.

### Target audience
Age groups: **13 and over** (matches privacy policy).

### News app: No
### COVID-19 contact tracing: No
### Data safety: See Step 4 below (biggest section)
### Government app: No
### Financial features: No

## Step 4 — Data safety declarations

Left nav → **App content** → **Data safety**. This is the form Google is strict about. Answers matching `docs/privacy-policy.md`:

### Does your app collect or share any of the required user data types?
**Yes**

### Is all user data encrypted in transit?
**Yes** (TLS + certificate pinning)

### Do you provide a way for users to request that their data be deleted?
**Yes** — in-app (Settings → Delete account) AND by contacting schulgenkyle@gmail.com

### Data types collected — for each, answer:
- **Collected: Yes/No**
- **Shared: No** (pan-tree shares with no third parties beyond processors — Cloudflare, Anthropic, Google Play — which count as "processing," not "sharing")
- **Processing: processed ephemerally? or persistent?**
- **Required or optional for functionality?**
- **Purpose: app functionality / analytics / personalization**

Declare these:

| Data type | Collected? | Purpose | Required? |
|---|---|---|---|
| Personal info → Name | Yes (display name from Google) | App functionality, Account management | Optional |
| Personal info → Email address | Yes | Account management | Required |
| Personal info → User IDs | Yes (Google sub) | Account management | Required |
| Photos and videos → Photos | Yes (scan + submissions) | App functionality | Optional |
| Files and docs | No | — | — |
| App activity → App interactions | Yes (taps, swipes, sessions) | Analytics | Optional |
| App activity → In-app search history | Yes (recipe searches) | App functionality | Optional |
| App info → Crash logs | Yes | App functionality | Required |
| App info → Diagnostics | Yes (app version, device model) | Analytics | Optional |
| Financial info → Purchase history | Yes (if user buys subscription) | App functionality | Optional |
| Location → Approximate / Precise | No | — | — |
| Contacts, Calendar, Messages, Health, etc. | No | — | — |

## Step 5 — Main store listing

Left nav → **Grow** → **Store presence** → **Main store listing**.

### App name
`pan-tree` (max 30 chars)

### Short description (max 80 chars)
`Turn your fridge into your cookbook. Cook what you have, waste less.`

### Full description (max 4,000 chars)

```
pan-tree is a pantry-first cooking assistant. Tell it what's in your kitchen,
and it surfaces recipes you can actually make — no more scrolling past meals
that need three ingredients you don't own.

WHAT IT DOES

• Pantry tracking — snap a photo of your fridge, pantry, or a receipt and let
  the scanner log your ingredients. Expiring items float to the top.

• Tonight deck — swipe through recipe cards ranked by what's in your pantry
  right now. Each card shows a match percentage, cook time, and which
  ingredients you're missing. Swipe right to save, left to skip.

• Filter by mood — quick, comfort, healthy, breakfast, lunch, dinner,
  vegetarian, baking. Adventurous mode pushes unusual picks up the deck.

• Meal plan — drag saved recipes onto a 7-day plan. The app adds missing
  ingredients to your shopping list automatically, grouped by aisle so you
  walk the store in one loop.

• Meal prep — turn the plan into a prep-day schedule: what to chop, what to
  marinate, what to cook ahead.

• Cookbook — your saved recipes, categorized and searchable.

• Community — read reviews from other cooks; write your own.

• Submit recipes — share your own recipes with the community (photos reviewed
  before they go public).

WHAT MAKES IT DIFFERENT

• 24,000+ recipes, ranked against YOUR pantry — not just what's trending.

• No ads. No data sold to third parties. No shopping-cart affiliate links
  jammed into your meal plan.

• Works with what you already have. The app's whole premise is reducing food
  waste and grocery budget creep.

• Editorial recipes from Wikibooks, Canada's Food Guide, USDA MyPlate, and
  licensed open sources — all attribution preserved.

WHAT IT NEEDS

• A camera (for scanning fridge/pantry/receipts/barcodes)
• A Google sign-in (so your pantry syncs across devices)

Free while in beta. Subscription tiers coming later will cover expanded
scanning limits; all core features stay free forever.

Questions / feedback: schulgenkyle@gmail.com
```

### Graphics

**App icon:** convert `android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp` → 512×512 PNG using any image tool. (I can generate this for you with Python if needed.)

**Feature graphic:** 1024×500 PNG. Needed for store listing banner. Placeholder: text-only on paper-cream background, "pan-tree" wordmark + "see it. save it. savor it." tagline.

**Phone screenshots:** 2-8 required. Capture from your device or Android Studio emulator of:
1. Tonight deck (full card visible)
2. Pantry grouped by aisle
3. Shopping list with aisle sections
4. Plan week view
5. Recipe detail
6. Cookbook
7. Community feed
8. Scan screen

Dimensions: minimum 320px, 16:9 or 9:16 ratio, PNG or JPEG, max 8MB each.

**Video (optional):** a 30-120 second YouTube URL showing core flows. Not required for launch.

## Step 6 — Upload the AAB

Left nav → **Release** → **Testing** → **Internal testing** → **Create new release**.

1. Click **Upload** and select: `C:\Users\12566\Downloads\pantrie-build (1)\pantrie-build\android\app\build\outputs\bundle\release\app-release.aab`
2. Release name (auto-fills): `1 (0.1.0)`
3. Release notes:
   ```
   First internal beta. Core features: pantry tracking, recipe deck,
   meal plan, shopping list, cookbook, community feed. Please report
   bugs via the floating feedback icon in-app.
   ```
4. Click **Save** → **Review release** → **Start rollout to Internal testing**
5. **Internal testing goes live immediately** — no review wait.

## Step 7 — Add testers

Still in **Internal testing**:

1. **Testers** tab → **Create email list**
2. Name it `pan-tree beta`
3. Add tester emails (your own Gmail + anyone you want to beta test)
4. Save
5. Copy the **opt-in URL** Play Console gives you. Send it to your testers.
6. Each tester clicks the link → opts in → waits ~15 min → downloads pan-tree from Play Store.

## Step 8 — Promote to higher tracks (later)

After internal testing shakes out bugs:

- **Closed testing** (~1 day review by Google). Allows up to 100 testers without individual invites.
- **Open testing** (~1 day review). Anyone with the link can install.
- **Production** (~3-7 day review for first release). Public launch.

Each promotion = copy the same AAB across tracks.

## Rebuilding the AAB later

When you need a new version:

1. Bump `versionCode` (integer, must increase) and `versionName` (string, human-readable) in `android/app/build.gradle.kts`:
   ```kotlin
   versionCode = 2     // was 1
   versionName = "0.2.0"  // was "0.1.0"
   ```
2. From the project root:
   ```
   cd android
   gradle :app:bundleRelease
   ```
3. New AAB appears at the same path: `android/app/build/outputs/bundle/release/app-release.aab`
4. Upload in Play Console → new release → same track.

You don't need a new keystore ever. Same `pantree-release.keystore` + same password signs every future release. Losing that keystore = losing the ability to update the listing, so back it up now.

## Quick reference — built artifacts right now

- **Signed AAB (for Play Store)**: `android/app/build/outputs/bundle/release/app-release.aab` (28 MB)
- **Signed APK (for sideloading on your phone)**: `android/app/build/outputs/apk/release/app-release.apk` (once the background build finishes)
- **Upload keystore**: `C:\Users\12566\Downloads\pantree-release.keystore`
- **Keystore password**: `C:\Users\12566\Downloads\PANTREE_KEYSTORE_PW.txt`
- **Admin API key (for backend dashboard)**: `C:\Users\12566\Downloads\PANTREE_ADMIN_KEY.txt`
- **Privacy policy source**: `docs/privacy-policy.md`

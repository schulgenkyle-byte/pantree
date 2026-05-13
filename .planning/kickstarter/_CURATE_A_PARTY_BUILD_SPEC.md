# Curate-a-Party — Build Spec (in-app)

Target: ship a screenshot-ready, top-UX Parties tab inside the existing Speakeater Android app. Kyle plugs in his phone, sideloads, takes the screenshots that go into the Kickstarter campaign as PROOF the feature is real.

This is NOT the full production spec at `03-CURATE-A-PARTY-SPEC.md`. This is the **minimum viable polished** that yields screenshots indistinguishable from a shipped feature. Cuts the admin authoring UI, cuts the D1 migration, cuts PDF generation, cuts Play Billing wire-up. Adds redemption-code unlock for Kickstarter backers.

---

## Scope this delivers

1. **New bottom-nav tab "Parties"** between Mixology and Saved, brass-fan icon
2. **Grid landing screen** — 5 menu cards, hero image + title + era + lock state
3. **Menu detail screen** — full editorial layout, locked by default with paywall overlay
4. **Paywall sheet** — $5 unlock OR redemption code field
5. **Redemption code flow** — `POST /menus/redeem` accepts `SPEAK-XXXX-YYYY`, grants menu(s) free
6. **Backend redemption codes table + admin endpoint** to mint codes for Kickstarter backers
7. **Five hardcoded menus** with full content (drinks, food, timeline, shopping list, music)

What's deliberately cut for this MVP:
- D1 `party_menu*` migration — menus are STATIC Kotlin/JSON data baked into the APK
- Admin authoring UI — not needed for 5 hardcoded menus
- PDF generation — defer
- Play Billing single-menu SKU — defer; paywall ONLY accepts redemption codes for now. The $5 message is shown but the "Pay $5" button says "Coming with Pro launch — use your Kickstarter code instead"

This is what the screenshots will show. After the campaign, you flesh out the production version per `03-CURATE-A-PARTY-SPEC.md`.

---

## Visual reference (this is the bar)

**Existing Speakeater app surfaces to match in polish:**
- `feature/mixology/MixologyScreen.kt` — Bootlegger sepia + Mixologist dark dual mode. Match the editorial typography.
- `feature/cards/CardChrome.kt` (or wherever swipe-card chrome lives) — period-correct frame, drop shadows, brass accents.
- `feature/deck/DeckScreen.kt` — the gold standard for hero+content layout.
- `ui/theme/Color.kt` — palette tokens. Use Paper/Paper2/Paper3/Ink/InkSoft/InkFaint/BrassBright/Olive/Terracotta. NO new color values.
- `ui/theme/Type.kt` — Playfair Display (display), Source Serif 4 (body), JetBrains Mono (mono). All loaded via Compose Google Fonts.

**Banned at the UX level:**
- No emoji anywhere in the UI
- No Material Design defaults that look like a generic app (TabRow, Card with rounded-12-corners — those scream Material). Override styles.
- No gradients in card backgrounds — use solid Paper3 + 1px border + dotted divider rules
- No SVG-rendered "objects" trying to substitute for real photographs — if a menu's hero image is missing, use a typographic title card with manuscript-page background, not a CSS cocktail glass

---

## File layout (new Kotlin)

```
android/app/src/main/java/app/pantrie/feature/parties/
├── PartiesScreen.kt              # tab landing — 5 menu cards in a grid
├── PartiesViewModel.kt           # state hoisting, redemption-code flow
├── MenuDetailScreen.kt           # full menu detail view
├── PaywallSheet.kt               # bottom sheet: $5 or redemption code
├── PartyMenuData.kt              # the 5 hardcoded menus (data only)
├── components/
│   ├── MenuHeroCard.kt           # the grid-cell card (hero image + title + era + lock chip)
│   ├── MenuSectionHeader.kt      # eyebrow + heading + gold rule (reused 5x in detail)
│   ├── DrinkListItem.kt          # cocktail row: name, year, source-book, recipe-summary expander
│   ├── FoodListItem.kt           # plate row: name + serving note
│   ├── HostTimelineBlock.kt      # vertical-rule timeline component
│   └── ShoppingListBlock.kt      # checklist with "in pantry" green-check overlay
```

Hilt-injected. ViewModel observes `entitlementRepo` for `hasMenu(menuId)` + the in-memory redemption state.

---

## The 5 menus (hardcoded)

Use `01-bees-knees-garden-party.md` content for menu 01. The other 4 need brand-voice rewrites first (originals archived at `_archive/sample_menus_v1_old_voice/`). **Do menu 01 first as a vertical slice, then rewrite + add the other 4.**

Each menu's Kotlin data class:

```kotlin
data class PartyMenu(
  val id: String,           // "bees-knees-garden-party"
  val title: String,        // "The Bee's Knees Garden Party"
  val eraYear: Int,         // 1928
  val eraCity: String,      // "Long Island, USA"
  val eraSourceLine: String, // "Frank Meier, Ritz Paris, 1929"
  val description: String,  // 2-3 sentence narrative intro
  val heroImageRes: Int,    // R.drawable.menu_bees_knees_hero (designer-quality JPG, not SVG)
  val drinks: List<MenuDrink>,
  val food: List<MenuPlate>,
  val timeline: List<TimelineStep>,
  val musicNote: String,
  val shoppingList: List<ShoppingItem>,
  val isHistoric: Boolean,  // true → Bootlegger mode default
  val isPriceLocked: Boolean = true,  // defaults to true, flipped by redemption or purchase
)
```

Music note example: "Solo piano. Eubie Blake. Earl Hines's earliest stride records. 90 to 120 BPM."

Shopping list example: `ShoppingItem("London Dry gin", "1 × 750ml", aisle = "bar", isInPantry = false)`. The `isInPantry` flag pulls live from `PantryDao` to render green checks on items the user already owns.

---

## Hero images (production-grade, NOT SVG)

These need real photographic-quality JPGs at 1080×720, deeply on-brand. Two ways to get them:

**Option A (recommended, fast):** Commission from a designer or use Midjourney/Sora with the brand-pack prompts. Examples:
- "Editorial overhead photograph of a 1928 Long Island garden party setup. White-linen table, brass jiggers, coupe glasses, lemons, mint, raspberries. Warm afternoon light. Filmstock grain. Shallow depth of field. No people. Period-correct. 16:9."
- "Below-street speakeasy bar at midnight, 1924 Manhattan. Brass rail, marble top, low warm light from a single Edison bulb. Two coupes, a shaker, a cigar in a brass tray. No people. Cinematic shadow falloff. 16:9."

Generate 5 hero images. Drop them into `android/app/src/main/res/drawable-xhdpi/menu_<slug>_hero.jpg`. Reference via `R.drawable.menu_bees_knees_hero`.

**Option B (slower, but doable):** Real photography. You stage a small set at home, shoot with your phone in portrait/landscape, edit in Lightroom mobile to match the brand palette (warm tones, deep shadows, slight film grain). Same hero spec, real assets.

Either way: **no CSS cocktail glasses, no SVG ornaments standing in for the real thing.** The user looking at this app sees a photograph, not an illustration.

---

## Menu detail screen layout

Top-down, scrollable. Sticky tab bar appears once the user scrolls past the hero. Tabs: The Night / Drinks / Food / Shopping / Notes.

**Above-the-fold (paywalled-user view):**

1. Hero image fills the top half of the viewport, slight scrim at the bottom for text legibility
2. Title in Playfair Display italic, 36pt, Ink color, bottom-left of hero
3. Era line (mono, all caps, letterspaced) below the title: "LONG ISLAND · 1928 · FRANK MEIER"
4. Sticky paywall overlay across the lower third: "Curate-a-Party menus unlock at $5 each. Kickstarter backers got a code with their pledge." + two buttons: [Enter Kickstarter code] and [$5 single-menu unlock — coming with Pro launch]

**Above-the-fold (unlocked view):**

1. Hero image full-bleed
2. Title + era line same position
3. The narrative intro paragraph (~50 words) directly below the hero
4. Sticky tab row appears as user scrolls

**Body sections (unlocked, scrolled):**

- **The Night** — narrative paragraphs from the .md file's intro + timeline section. Mono section header eyebrow ("THE NIGHT") + Playfair section heading ("Three hours, eight guests").
- **Drinks** — 5 cocktail rows. Each row: name (Playfair italic 22pt) → expanded view: source line (mono) → recipe (Source Serif 4 14pt) → "Open in Mixology" button → links to existing MixologyScreen.kt with the matching recipe ID.
- **Food** — 6 plate rows in similar pattern.
- **Shopping** — checklist with green-check icon on items the user already has in pantry (read PantryDao). Pull-quote at top: "$115 for 8 guests, excluding spirits."
- **Notes** — music line + faithful-vs-modern variants section.

**Section transitions:** 1px dotted gold rule (8px dashes, BrassBright at 30% opacity) between every major block. Never solid rules — those read modern.

---

## Paywall sheet

Bottom sheet, slides up over the menu detail. Background: Paper2 with paper-noise overlay at 8% opacity. 90% height.

Contents top-to-bottom:
- Drag handle (period-correct: brass dot)
- Header eyebrow "Speakeater · Curate a Party" mono caps
- Title "Unlock this menu" Playfair italic 32pt
- Two columns of options:
  - **Left:** "Kickstarter backer?" — Source Serif 4 14pt copy: "Your code came with your reward delivery email. Enter it below to unlock this menu (or your full Founding Set)." Then an OutlinedTextField with placeholder "SPEAK-XXXX-YYYY" (uppercase as you type), then a primary button "Redeem code" (BrassBright background, Paper text).
  - **Right:** "Don't have a code?" — Source Serif 4 14pt copy: "Single menus unlock at $5 each once Pro launches on June 10. Pro is $45 a year. Nine menus' worth of value. Fifty menus' worth of access." Then a secondary outlined button "Notify me at launch" (which saves to a DataStore preference).
    > **Note:** the $30 Kickstarter founder rate is intentionally NOT mentioned in the in-app paywall — it's a one-time-only offer for backers during the campaign, and surfacing it post-launch invites support tickets from users who missed the window. Founders see their grandfathered rate in Settings → Account → Subscription, not in this paywall.
- Footer line: "We honor every Kickstarter code for the operational lifetime of the app. If we shut down, all menus unlock for everyone."

---

## Backend: redemption codes

New D1 table (just one):

```sql
CREATE TABLE party_menu_code (
  code           TEXT PRIMARY KEY,           -- 'SPEAK-A4F2-9X8R' format
  user_id        TEXT,                       -- NULL until redeemed
  granted_menus  TEXT NOT NULL,              -- JSON: ['bees-knees-garden-party'] or ['*'] for all
  granted_tier   TEXT,                       -- 'kickstarter_t02_one_menu' / 'kickstarter_t06_founding_set' / etc. (no `$` — keep SQL-safe)
  created_at     INTEGER NOT NULL,
  redeemed_at    INTEGER,                    -- NULL until used
  notes          TEXT
);
```

New backend endpoints (`backend/src/menus.js`):

```
POST /menus/redeem            body: { code }, auth: required
  - Validates format (regex /^SPEAK-[A-Z0-9]{4}-[A-Z0-9]{4}$/)
  - Looks up code in party_menu_code
  - Rejects if redeemed_at is not null
  - Sets user_id and redeemed_at = now
  - Returns granted_menus list

POST /admin/menus/codes/mint   body: { count, granted_menus, granted_tier, notes }
  - admin-key required (use util.adminAuthed)
  - Generates N codes via crypto.randomBytes
  - Inserts each into party_menu_code
  - Returns the list of codes as CSV for paste into your Kickstarter backer survey response

GET /me/menus
  - Returns the list of menu IDs this user has unlocked (via redemption or Pro)
```

Wire `EntitlementRepository.hasMenu(menuId)` to combine: Pro-subscriber-yes OR user has a row in `party_menu_code` where `granted_menus` includes the id (or `*`).

---

## Kickstarter backer fulfillment flow

1. Campaign ends. Backers get Kickstarter's automated survey to collect their email and Speakeater account info.
2. Kyle runs `POST /admin/menus/codes/mint` with `count = number_of_backers_at_$4_tier` and `granted_menus = ['<menu-they-chose>']`. Repeat for each tier.
3. Kyle exports the CSV of codes. Pastes one code per backer into Kickstarter's survey response or a follow-up email.
4. Backer downloads Speakeater, signs in, opens Parties tab, taps any locked menu, taps "Redeem code," types the code, gets the menu(s) unlocked.

For the $49+ tiers that include the full Founding Set, `granted_menus = ['*']` unlocks everything.

---

## Build order (vertical slice first)

1. **Hour 1-2:** Hardcoded data for menu 01 only. PartiesScreen with one menu card. MenuDetailScreen rendering the data.
2. **Hour 2-3:** PaywallSheet UI (no backend yet). Lock state hardcoded.
3. **Hour 3-4:** Backend `POST /menus/redeem` + `party_menu_code` table + admin mint endpoint. Wire frontend.
4. **Hour 4-5:** Add remaining 4 menus (after voice rewrites). 5 hero images dropped in.
5. **Hour 5-6:** Polish — animations, sticky tab transitions, screenshots for Kickstarter.

After hour 6: Kyle deploys via `gradle :app:installRelease` (or sideload the new AAB), opens Parties tab, takes 8-10 screenshots:
- Tab landing grid showing all 5 menus
- Menu detail above-the-fold (locked view with paywall)
- Menu detail unlocked (Bee's Knees full scroll, captured as multiple stitched screenshots)
- Paywall sheet open
- Shopping list with pantry green-checks
- Mixology recipe deep-link (existing screen with new Curate-a-Party "back to menu" affordance)

These screenshots replace the Remotion-rendered tier cards in the Kickstarter campaign. They prove the feature exists.

---

## Acceptance bar (do not ship without these)

- Hero images are PHOTOGRAPHS or photo-quality renders, not CSS shapes
- Typography hierarchy is Playfair / Source Serif / JBM exclusively, no Material defaults
- The paywall sheet's copy reads in brand voice (no "Unlock the magic of cocktail history" garbage)
- Redemption code flow round-trips end-to-end against staging D1
- One real Kickstarter test code generated and successfully redeemed before screenshots are taken
- The locked-menu paywall surface is so visually compelling that a backer screenshot of it AT A KICKSTARTER UPDATE makes other people back the campaign

If any of those fail, the feature is not ready for screenshots.

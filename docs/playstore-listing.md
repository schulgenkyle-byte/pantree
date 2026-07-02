# Speakeater — Play Store Listing Copy (rewritten 2026-07-01)

Copy-paste ready for Google Play Console. Brand is **Speakeater** everywhere; "Brimm" appears in nothing user-facing.

> **WHY THIS REWRITE EXISTS — the live listing (checked 2026-07-01) is wrong in ways that cost installs and risk policy strikes:**
> 1. Body copy is still the old Brimm draft, including the hard-banned phrase "pantry-first cooking app" and the dead tagline "See it. Save it. Savor it."
> 2. Pricing block advertises **$29.99/yr** and a **$59.99 lifetime** tier. V3 pricing (locked 2026-05-17) is **$4.99/mo · $45/yr, NO lifetime**. Deactivate the `brimm_pro_lifetime` SKU.
> 3. Data safety declares **Location collected and shared**. The app does not touch location. Fix the form — false declarations are a strike risk.
> 4. Developer display name shows the internal codename **"pan-tree"** — rename to Speakeater in Play Console account settings.
> 5. Content rating shows "Everyone" despite 3,800+ cocktail recipes; re-run the questionnaire (expected Teen).
> 6. Counts below are D1-verified 2026-07-01: ~23,300 food recipes (claim "23,000+"), ~3,883 drinks (claim "3,800+"). Do NOT claim 4,000+ cocktails or 27,000+ with the drinks folded in and 178 mocktails on top — the numbers must survive an honest count.

---

## 1. App Title (30 char max)

```
Speakeater: cook what you have
```

*(30 characters — title keywords are the biggest ASO input; "Speakeater" alone wastes 20 of them)*

---

## 2. Short Description (80 char max)

```
Photograph your fridge. Get dinners you can cook tonight. Plus a vintage bar.
```

*(77 characters, pain-frame, keyword-carrying, no banned phrases)*

---

## 3. Long Description (4000 char max)

```
Speakeater is for people who are tired of asking what's for dinner.

Open your fridge. We'll figure out dinner.

Open the camera, sweep your fridge and pantry, and Speakeater identifies what you already own. Then it hands you a short, honest list of recipes you can actually cook tonight, ranked by how much of each dish is already sitting on your shelf, and what's closest to turning.

No infinite feed. No fourteen paragraphs about someone's grandmother before the ingredient list. Just dinner.

WHAT MAKES IT DIFFERENT

Most recipe apps start with the recipe. Speakeater starts with your kitchen. We look at what's there, what's expiring, and what you'll actually use, then surface the best matches for the night. You won't be swiping for an hour. You'll be eating.

THE DAILY RHYTHM

- Scan: multi-photo capture, ingredients auto-identified
- Swipe: a focused deck of recipes ranked by what you already own
- Cook: step-by-step Cook Mode with auto-advancing timers
- Track: see what you saved, what you wasted, what's next

THE BAR

The Mixology tab pours 3,800+ drinks, more than half transcribed from pre-Prohibition bartenders' manuals. Bootlegger mode shows the recipe in the original printed wording of Jerry Thomas (1862), Harry Johnson (1882), and Hugo Ensslin (1917). Mixologist mode translates to modern ounces, ice, and glassware. Zero-proof mocktails included for nights off.

FEATURES

- Camera-first pantry scan (multi-photo capture, ingredients auto-identified)
- 23,000+ real recipes across every major cuisine, none invented by AI
- 3,800+ cocktails and zero-proof drinks, from 1862 manuscripts to modern craft
- Match % on every card: the honest percentage of ingredients already in your kitchen
- Cook Mode: full-screen, auto-advancing timers, no thumb-taps with oily hands
- Smart shopping list, auto-grouped by aisle, built from the recipes you picked
- Expiring-soon alerts, so the cilantro doesn't go to the bin
- Waste tracker: saved dollars vs wasted dollars, in plain numbers
- User-submitted recipes: photo required, admin-reviewed, no spam

PRICING, STRAIGHT

Free forever:
- Full access to the cookbook and the bar
- 20 swipes per day in the Tonight deck
- 3 fridge-photo scans per month
- Pantry tracking, shopping list, Cook Mode, waste tracker
- Light ads between swipe sessions

Speakeater Pro:
- $4.99 / month or $45 / year
- Unlimited swipes and unlimited fridge scans
- Meal-prep planner and smart shopping
- Submit your own recipes
- No ads, ever

WHO IT'S FOR

Home cooks who want dinner decided in under a minute. Cocktail enthusiasts who'd rather pour a real Old Fashioned than scroll. People who buy produce with good intentions and watch it die in the crisper.

If you open your fridge, sigh, and order takeout, Speakeater is built for you.

Open your fridge. We'll figure out dinner.

Speakeater is a small independent app. We don't sell your data. We don't share your kitchen with advertisers.
```

---

## 4. What's New (500 char max) — post-repair release

```
Recipe quality pass: we re-parsed the ingredient lists across the catalog. Quantities and fractions now read the way a cook wrote them. Cook Mode steps unchanged and reliable.

Also in this update: corrected pricing display, refreshed listing, and the bar now labels its pre-Prohibition transcriptions by source manual and year.

Open your fridge. We'll figure out dinner.
```

---

## 5. Category & Tags

**Primary:** Food & Drink
**Tags:** Cooking, Recipes, Cocktails, Meal Planning, Grocery

## 6. Content Rating

Re-run the questionnaire. Answer YES to alcohol references (3,800+ cocktail recipes, filterable to zero-proof, no consumption depicted). Expected: **Teen**. The current live "Everyone" rating does not match the content and must not be left as-is.

## 7. Data Safety (fix the live form)

- **Location: NOT collected, NOT shared.** Remove both declarations from the live form.
- Collected: email (account), pantry/receipt photos (auto-purged after recognition), usage analytics (pseudonymous), crash logs, purchase history (Play Billing), Advertising ID (free tier only, shared with AdMob for ad serving).
- All encrypted in transit; deletion via Settings → Delete Account.

## 8. SKUs

- `brimm_pro_monthly` — $4.99 (locked ID, display name "Speakeater Pro Monthly")
- `brimm_pro_yearly` — $45.00 (verify: live listing advertised $29.99)
- `brimm_pro_lifetime` — **DEACTIVATE.** No lifetime tier exists in V3 pricing.

## 9. Developer display name

Play Console → Account → change public developer name **pan-tree → Speakeater**.

## 10. Screenshots

Re-capture AFTER the ingredient repair ships (do not screenshot broken "12 teaspoon salt" lines). Capture list unchanged from the previous version of this doc: Tonight deck with match %, Mixology Bootlegger/Mixologist toggle, camera scan with chips, Cook Mode timer, aisle-grouped shopping list, library, cocktail search, Pro paywall. 9:16, 1080×1920, no device frames.

---

*Old Brimm-era draft fully superseded 2026-07-01. Do not resurrect "pantry-first", "cook-with-what-you-have cooking app", "See it. Save it. Savor it.", 27,000+/4,000+ counts, $29.99/yr, or the lifetime tier.*

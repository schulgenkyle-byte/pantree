# Demo critique — pantrie-demo.html

Walked through `demo/pantrie-demo.html` (1,435 lines, single-file mockup) and cross-checked every flow against `PANTRIE.md`, `README.md`, and `docs/playstore-listing.md`. Findings ranked by impact on closed-testing → open-beta → conversion.

Verification: every P0 item below is grounded in a specific line number or grep result. No speculation.

---

## P0 — fix this week (blocks beta tester trust or store listing match)

### 1. Three brand names ship in the same repo

Grep counts: `Brimm` appears 32× in `docs/playstore-listing.md` and 2× in `backend/wrangler.toml` (`PLAY_PACKAGE_NAME = "app.brimm"`, `brimmapp.com`). **`Brimm` appears 0× in the demo, 0× in `README.md`, 0× in `PANTRIE.md`.** The demo browser title is `Pantrie` (line 7), the badge says `PANTRIE · DEMO`, footer says `Pantrie · v0.4 mockup`, and the README/spec call it `pan-tree`.

If the Play listing is "Brimm: cook what you have" but the in-app title bar says "Pantrie", testers screenshot it and the product looks unfinished. Pick one. Given the package name is locked to `app.brimm` and the privacy policy is hosted at `brimmapp.com`, the answer is Brimm. Then global-replace everywhere else.

### 2. Two icons render blank everywhere they appear

`i-flame` and `i-trash` are referenced via `ic('flame', ...)` / `ic('trash', ...)` but neither symbol is defined in the `<defs>` block (lines 401–428). Affected surfaces:

- "Plan it for me" CTA on Shop tab (line 806) and inside Plan Picker (line 834)
- "Unlock more recipes" header on shopping list (line 792)
- "Use" button on each expiring item (line 768)
- "Start cooking" menu item in slot action sheet (line 1112)
- "Smart planner" reasoning rows (line 1258)
- Pantry-edit Delete button (line 1080)

These are your highest-conversion CTAs. Right now they have a leading whitespace where the icon should be. Add the two `<symbol>` defs.

### 3. The free-tier 10-swipes/day cap is not in the demo at all

`PANTRIE.md` positions "Tinder swipe deck with finite attention" as the anti-doom-scroll moat ("nothing else matters"). The demo's `sortedDeckRecipes()` function (line 606) returns the entire filtered catalog with no day-based limit. The Tonight header literally says `unseen.length+' left'` (line 716) — for the production catalog of 23k+ recipes, this would read "22,847 left" on day 1. That completely undermines the marketed message.

Closed testers who read the Play listing will expect the cap. When they don't hit it, they wonder if the app is broken or if marketing was lying. Either implement the cap (`SwipesPerDay` counter, paywall sheet at swipe 11) or rewrite the listing.

### 4. CORE ingredients (moat #1) is not implemented anywhere

`PANTRIE.md` calls CORE "the moat nobody else has" — week-over-week pantry analysis, auto-restock when CORE items run low. Grep on the demo: 0 hits for `core`, 0 hits for `week-over-week`. The Shop tab's "Running low · suggested" block (line 790) uses a static `STAPLES` array hardcoded at line 509 with three items. That's not CORE, that's a placeholder.

Until this ships, "what makes Brimm different" pitches against SuperCook and Mealime collapse. Either add a real CORE inference (n-week rolling pantry hash → flag persistent items → mark in pantry UI → push to shopping when low) or stop calling it a moat in the listing.

### 5. Waste tracker exists in copy only

The Play listing says "Waste tracker — saved dollars vs. wasted dollars, in plain numbers" and the README mission statement is "minimize food waste". The demo's portfolio (line 873) computes `moneySaved = cooked.reduce(...) * 0.4` — that's *takeout savings*, not waste prevented. There is no concept of waste in the data model. Pantry items have `expires_at` but never roll up into a "you wasted $X this month" view.

For the headline differentiator vs. every other recipe app, this is the single biggest hole. Even a faked-out waste rollup ("We see you tossed cilantro twice this month — $4.99 lost") on the Mine tab would close the loop the entire pitch is built around.

### 6. Cook mode "swipe to next step" can accidentally finish a recipe

`bindCook` (line 1207) uses 60px horizontal swipe to advance. On the last step, `cookFinish()` fires immediately on right-swipe — no confirm sheet, no review prompt overlay, just *boom, you cooked it, here's the rating screen*. With oily hands during a real cook session, an accidental drag past 60px in any direction triggers either pause (if sub-60) or finish (if past 60). At minimum, gate the last-step → finish transition behind a held tap or a "Finished?" confirm.

### 7. Auto-advance defaults to ON for imported and user recipes — but their step durations are 60s

Line 1409 (URL import) and line 1419 (recipe editor) both bake `duration: 60` for every step regardless of content. That means a step that says "simmer 45 minutes" auto-advances after 60 seconds. Either:

- Default `duration: null` for user/imported steps, and don't auto-advance when null
- Or use Haiku at import time to extract durations from step text

Right now the auto-advance feature is a footgun for anyone using their own recipes.

---

## P1 — fix before open beta (hurts conversion or feels broken)

### 8. No first-run / onboarding flow

The demo opens directly into Home with a populated pantry and pre-set diets. A real new user lands on a hero that says *"Tonight's selection"* with zero data and gets recommendations they can't possibly cook. The onboarding should be: scan first → quick diet/allergy chips → land on Home. The pieces exist (Settings has the chips, Scan flow exists), they just need stitching.

### 9. Pro-tier paywall is invisible

Listing promises Pro at $4.99/mo for 40 swipes, voice cook mode, family sharing, etc. Demo has zero paywall surfaces. Free testers never see what they'd be paying for. Even a stubbed-out "Pro · Try free" sheet that shows the value-prop bullets would help conversion math when you flip from closed to open testing.

### 10. `maxServings` is a vanity calculation

Line 584: `if(r.uses_pantry_percent>=90) return r.servings*2`. But `uses_pantry_percent` is a *count of ingredient names matched* (line 466 has `have:true` baked into recipe data), not a quantity check. You may have 1 egg of a recipe's required 4 — still counted as "have", and you'll see "Make up to 4 servings" on a card. Users who try this once and find they're short will not trust the app again.

Fix: compare actual `pantry[item].quantity * unit` against `recipe.ingredients[item].quantity * unit`, then take the floor of the most-constraining ratio.

### 11. Diet/allergy filters silently delete recipes

`recipeMatchesSettings` (line 590) returns `false` for non-matching recipes; the deck just shows fewer cards. No "filtered 14 recipes due to your allergies" toast, no way to peek. Users who toggle Vegan and see the deck shrink to 2 cards will think the app is broken. Add a footer in Settings: "Right now: 8 of 23k recipes match your filters."

### 12. Substitution intelligence is a regex, not the data model

Line 1138: cook mode flags butter/cream substitutions via `step.text.match(/\bbutter\b|\bcream\b/i)`. But ingredients already have a `subs` array in the recipe data (line 466: `subs:['Olive oil','Ghee']`). Wire the data model into cook mode so any ingredient with `subs` shows the chip when its step is active. Right now it's two hardcoded keywords pretending to be AI.

### 13. "Among friends" shows strangers as friends to brand-new users

Home (line 689) renders `FRIEND_REVIEWS.slice(0,3)` — a static array of three made-up users (Maya/Dev/Aria) shown to every user regardless of `S.follows`. New users with zero connections see fake reviews labeled as "Among friends". Either gate it on `S.follows` having entries, or rename the section to "Recently cooked" until you have enough real users to populate it.

### 14. Cook count looks fabricated

Recipe data has counts like 18,903 / 12,408 / 8,723 — believable for a mature catalog, suspicious for a closed-beta app. If real users open the leaderboard at Mine → Most Made and see the Top 1 with 18k cooks while their own count is 0, the social proof boomerangs into "this app is full of fake numbers." Fix at the data layer: either (a) ship with the real seeded counts from your TheMealDB+USDA ingest, or (b) hide cook counts under `cook_count >= 50` until organic.

### 15. The Scan demo has no actual scan UI

The "scan" fullscreen (line 1131) shows a black box with a camera icon. Pressing the simulate button magically adds three items. Beta testers shown the demo will reasonably assume the real app does this. If your closed-testing AAB is more polished here, ignore — but the single-file demo is what most people will see first (it's in `demo/`, called out in the README), and "imagine an actual scan" hurts more than it helps.

### 16. No empty state on Pantry tab

If a real new user lands on Pantry with no items, the "What you have on hand" hero shows nothing below the scan CTA. The empty state pattern exists (`emptyP` helper, line 891) — wire it up: "Empty pantry · Tap scan to populate."

---

## P2 — polish (don't block, but don't ship without)

### 17. Accessibility: `maximum-scale=1, user-scalable=no`

Line 5. Disables pinch-zoom. Visually impaired users cannot enlarge text. Apple has actively rejected apps for this; Play Store doesn't reject but it shows up in pre-launch reports. Drop the two restrictions, keep `width=device-width, initial-scale=1`.

### 18. 35 cuisines, 12 cuisine tints

`CUISINES` constant at line 436 has 35 entries. `tint-*` classes (line 92) cover only 12. Recipes from Vietnam, Korea, Lebanon, Ethiopia, etc. all fall through to `tint-other` and look identical on cards. Either add the missing tints or rotate by hash.

### 19. `recommendedGroceries` doesn't subtract dismissed recipes

Line 538. The unlock-score calculation excludes `cooked` recipes but not `dismissed` ones. A user who swiped left on Carbonara still sees "Pancetta — unlocks Spaghetti Carbonara" in their shopping list. Add `S.interactions[r.id]?.status==='dismissed'` to the skip list.

### 20. Settings footer says "v0.4 mockup"

Line 988. Bump the version with each demo iteration so screenshots don't all look like they're from the same week.

### 21. The "Community" tab promised in BETA_RUNBOOK isn't a tab

`BETA_RUNBOOK.md` says testers see a "Community tab (5th bottom-nav tab)" with anonymized cook tickers. The demo's 5th tab is "Mine" with a sub-tab for Feed. Either update the runbook or split out Community as the 5th tab the runbook describes — testers reading the runbook before opening the app will be confused by the discrepancy.

### 22. Floating Feedback button doesn't exist in the demo

Same runbook says "floating Feedback button (bottom-right on every screen except Login)". Not in demo. Probably exists in the closed-testing AAB, but if anyone references the demo for feedback, they'll have nowhere to file it.

---

## What's already great (don't break it)

- The Fraunces serif + Mulish + cream/terracotta palette is genuinely distinctive. None of your competitors look like this. Protect it through the rebrand.
- Card flip on the deck is a gorgeous interaction — front shows the pitch, back shows ingredients-on-hand + first 3 steps. Better than Tinder's flip.
- "Plan it for me" with reasoning sentences ("Uses your expiring spinach") is the kind of small touch that makes the AI feel honest. Keep it; add a "why this and not that" expandable for power users.
- Recommended-Groceries unlock score (`recommendedGroceries()` at line 538) is the smartest feature in the demo. It's the thing competitors don't do at all. Promote it harder in the listing copy — right now it's not even mentioned.
- Auto-add missing ingredients to shopping on swipe-right is the loop closer that no one else nails. Carbonara gets saved, pancetta hits the list, no friction. That single behavior is the "$4.99/mo well-spent" moment.

---

## Recommended order of operations

1. Day 1: ship icons (P0 #2), brand normalization (#1), and fix cook-mode swipe-finish (#6). All three are 1-hour fixes and the most embarrassing items to be caught by testers.
2. Week 1: implement the 10/day swipe cap (#3) and a plausible waste tracker view (#5). These are your two biggest moat-credibility holes.
3. Week 2: CORE ingredients (#4) — needs schema, but this is your single biggest competitive differentiator. Until it ships, lean less on it in copy.
4. Pre-open-beta: onboarding flow (#8), paywall surfaces (#9), `maxServings` quantity check (#10), filter visibility (#11).
5. Polish pass before production: substitution wiring (#12), social proof gates (#13/#14), accessibility (#17).

# pan-tree — product north star

_see it. save it. savor it._

> **pan-tree is the pantry-first cooking app that actually tracks what's in your kitchen, nudges you to cook before food dies, and serves real, human-cooked recipes — not AI slop. We turn your fridge into your cookbook.**

Every feature, every screen, every dollar spent has to justify itself against that sentence.

## Who it's for

Anyone who cooks at home and throws food out. No persona narrowing. Single adults in studios, couples, families of six — if you own a fridge, pan-tree is for you.

## What pan-tree is NOT

- A recipe library (Yummly, NYT Cooking, AllRecipes already win that)
- A grocery delivery app (Instacart owns that)
- A nutrition tracker (MyFitnessPal)
- TikTok for food (endless-scroll is anti-mission)

## The one loop that matters

```
Scan fridge → See expiring items → Tonight's 10 prioritize those →
Swipe one → Missing ingredients auto-add to shop → Cook mode walks you through →
Waste tracker logs the $ saved
```

If this loop is tight and magical, nothing else matters.

## Three moats no competitor has

### 1. CORE ingredients (nobody does this)
Week-over-week pantry analysis. Ingredients that appear in pantry ≥3 weeks running = "CORE" for that household. When a CORE item runs low / expires, auto-suggest to shopping list. "You always have eggs — you're down to 2. Restock?"

### 2. Expiring-first ranking + 10/day swipe cap
Anti-doom-scroll is the feature. Finite attention = real decisions. Competitors drown users in recipes; pan-tree shows you 10 tonight, picked for what's about to die in your fridge.

### 3. Real recipes, not AI slop
Our catalog = 3,250 vetted today (TheMealDB + Wikibooks + corbt/all-recipes), growing to 2M+. Every card shows **cook count + user rating**. Social proof > AI generation. (CNN + Fortune have both called out "AI slop recipes" as a 2025-2026 crisis; we benefit from that fatigue.)

## Pricing (locked)

| Tier | Price | What you get |
|---|---|---|
| **pan-tree Free** | $0 forever | 10 swipes/day · **1 scan/day** · 5 barcode lookups/day · 1 receipt scan/week · full pantry · full shopping · basic cook mode · 1 ad between swipes |
| **pan-tree Pro** | **$4.99/mo or $39/yr** | 40 swipes/day · 20 scans/day (max 100/mo) · unlimited barcode · 4 receipts/week · family sharing · voice cook mode · full meal planner · waste-dollar dashboard · recipe URL import · no ads |

**Why $4.99?** Undercuts Mealime ($5.99) and Samsung Food ($6.99). Premium over ChefGPT ($2.99). Healthy 80%+ gross margin after Play Store 15% cut + variable costs.

## Unit economics (verified)

| Metric | Value |
|---|---|
| Fixed infrastructure cost | ~$75/mo (Cloudflare + domain + tools) |
| Variable cost per Free user/mo | $0.05–0.15 (capped at 1 scan/day) |
| Variable cost per Pro user/mo (realistic) | $0.30–0.80 |
| Variable cost per Pro user/mo (absolute max) | $2.00 (100-scan monthly cap) |
| Net revenue per Pro user/mo after Play cut | $4.24 |
| **Gross margin per Pro user** | **~83%** |
| **Break-even Pro users** | **~22** |

**Worst-case at 200k installs, 0% conversion:** ~$3,600/mo Anthropic cost. Fundable with one small round or supportable by 1,500 Pro subs.

## Growth strategy — organic-first

**Phase 1 — 0 to 500 Pro users (Launch → month 4)**
- **TikTok + YouTube Shorts**: cooking waste content, "I used this before it died" videos, expiring-food countdowns
- **ASO on Play Store**: target "pantry app," "food waste tracker," "what to cook with," "recipe from ingredients"
- **Press outreach**: USA Today, Lifehacker, AppAdvice, HuffPost Food — "App that stops you throwing out food" is a reporter-friendly angle
- **Zero paid ads.** CAC math doesn't work against Samsung Food's budget.

**Phase 2 — 500 to 5k Pro users (month 5 → 10)**
- **Referral program**: "Give 1 month of Pro, get 1 month free." ~$5 CAC per new Pro user.
- Continue organic content.

**Phase 3 — 5k+ Pro users (month 10+)**
- **Selective affiliate program**: hand-pick 10-20 food creators. **30% year 1, 10% year 2** (prevents pump-and-dump). Use Rewardful ($99/mo) to track.
- Never open the affiliate floodgates — that's abuse farm bait.

## Competitive positioning

| App | Their strength | Their weakness | Our play |
|---|---|---|---|
| Samsung Food | Recipe discovery, smart-home | "Pantry AI is manual theater", edited recipes don't save, sleazy diet-culture ads | Our pantry is bulletproof. No diet shaming. |
| SuperCook | Pantry-first, free, voice input | Pantry data vanishes on login, ingredient matching fails (shredded ≠ grated), pushes users to ad-blog external sites | Our data persists (encrypted local + synced). No external redirects. |
| ChefGPT/DishGen/AI chefs | Personalization prompts | AI hallucinates ingredients, bland/dangerous recipes, aggressive paywalls at 2 minutes | Real recipes only. Cook-count social proof. Real free tier. |
| Paprika | Recipe import, offline | Meal planner doesn't add to shopping (cardinal sin), clunky workflow | Our round-trip is seamless. |
| Mealime | Speed, simple UX, hands-free cook | 1,200 recipe ceiling (users burn through in weeks), no pantry, 2/4/6 servings only | 2M+ recipes, full pantry, flexible servings |

## Roadmap

### Wave 1 — defining UX (shipping now)
- Tinder swipe deck (drag gestures, overlays, flip-for-ingredients)
- Cook count + user rating on every card (social proof)
- Scan caps enforced (1 free/day, 20 Pro/day with 100/mo ceiling)

### Wave 2 — CORE + Cook
- CORE ingredients algorithm (week-over-week pantry history)
- Auto-restock CORE items on low/expire
- Cook mode with auto-advance + countdown timer

### Wave 3 — Pro value + monetization
- Play Billing wire (actual $ flow)
- Family sharing (shared pantry/list real-time)
- Settings (34 cuisines, 12 diets, 10 allergens)
- Waste-dollar dashboard M-o-M trends

### Wave 4 — scale & polish
- Recipe URL import (Haiku normalizes any blog URL)
- Voice cook mode
- Nutrition goal tracking
- Ad network integration (tasteful only — no diet culture)

### Wave 5 — post-launch flywheel
- User review feedback loop (cook → rate → feeds ranking algorithm)
- "Popular this week" on home screen
- ASO iteration based on Play Console data
- Referral program
- First paid-ad experiments
- iOS (SwiftUI, same backend)

## Launch plan

- **T-14 days:** All waves 1-3 complete. Internal testing with 5-10 people.
- **T-7 days:** Closed testing track on Play Console with 20-30 testers.
- **T+0 (public launch):** Free + Pro live. Coordinated TikTok + press push.
- **T+30:** First retention numbers. Iterate on churn causes.
- **T+90:** If Pro conversion >2% and 30-day retention >30%, double down. If not, re-examine free tier value.

## Strategy notes — the "feels like winning" design patterns

These are intentional product decisions. If a user or reviewer asks about them, here's the honest reasoning so future-me doesn't forget.

### Plan screen "rotate alternatives"

When a user taps a scheduled meal they don't like, we offer **3 pre-scored alternatives** to swap in. This looks like "variety control" to the user — they feel in charge, they get more options, the app feels responsive. What it actually does behind the scenes:

- Each rotation shows 3 alternatives scored similarly to the displaced meal (same cuisine bucket, similar match %, adjacent cook time)
- This costs the user **zero swipes from their daily 20-cap**
- It gives the perception of abundance inside a deliberately-limited system
- Net effect: users feel like they're "winning" options without triggering the anti-doom-scroll guardrail, which would feel punitive

This is not manipulative — we're genuinely helping them plan. But the interaction is designed so that the constraint we built (20-swipe cap) doesn't feel like a constraint when they're in planning mode. If users start complaining "just let me see more recipes in Tonight," this feature is the release valve. Don't remove it.

### Super Adventurer button on Tonight

The default deck floors 0% matches out — if a user's pantry has nothing for a recipe, that recipe is noise. BUT: a button labeled **"Broaden your scope"** (or "Super Adventurer") surfaces 5 recipes at &lt;20% match as a one-tap opt-in. Reasoning:

- Removes low-match noise for the 90% case (users want to cook tonight, not dream)
- Preserves discovery for the curious tester who wants to explore
- Opt-in = intentional consent, not algorithm-pushed content
- Doesn't use the daily swipe budget — these are preview peeks, not committed picks

This is how we keep the deck tight-by-default without removing serendipity entirely.

### Community as review-feed, not activity-ticker

Original Community tab showed anonymous "Someone just cooked X". Nice but hollow. Switching to a **review/blog feed** where testers can publish opinions on recipes they've cooked — photo, rating, short paragraph. Reasoning:

- Gives testers a real reason to return (their review is seen)
- Builds user-generated content that's moderated (we review submissions anyway)
- Turns social proof from anonymous numbers into named experiences
- Sets up the eventual "known reviewer" reputation layer if we add one

Anonymity stays optional — users can post under a handle or fully anonymously. The key shift: activity → opinion.

## Red lines (never cross)

- No diet culture in copy or ads (learned from Samsung Food)
- No paywall in the first 10 minutes of use (learned from AI chef apps)
- No AI-generated recipe ever shipped without human validation
- No open affiliate program before 5k paying users
- No selling user data, ever
- No ads on the cooking flow itself (only between swipe sessions)

---

*Locked: 2026-04-23. Changes require explicit product decision in a dedicated session, not in passing.*

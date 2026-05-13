# Speakeater Kickstarter — Current Truths (2026-05-12 · game-first pivot)

Snapshot of everything that is actually true right now. Supersedes anything that contradicts this in older files.

**PIVOT NOTE (2026-05-12):** The Kickstarter angle pivoted from "cocktail menus + mystery nights as bonus" to "multiplayer mystery party games anchored in pre-Prohibition cocktail history." See `_PIVOT_2026-05-12.md` for the full pivot rationale. Pre-pivot doc set archived at `_archive/menu_first_v1_2026-05-12/`. The cocktail/cooking sides of the app remain unchanged — the game side is the new headline.

The backend now includes a deployed Cloudflare Durable Object (`GameRoom`) handling per-game state, WebSocket connections, beat-timeline scheduling, and per-character message routing. One DO per active game, keyed by a 4-letter code. 100+ concurrent games is the design target.

---

## D1 production numbers (queried 2026-05-12, post-slop-cleanup + post-ingest)

| Bucket | Count |
|---|---:|
| Food recipes | **23,743** |
| Cocktails | **5,036** |
| Mocktails | **1,361** |
| Modern non-alcoholic drinks | **142** |
| **Drinks total** | **6,539** |
| Pre-Prohibition cocktails (source_year < 1933) | **2,848** |
| With original-source text preserved | **2,846** |
| Free public Codex PDF | 50 hand-curated |
| Hand-reviewed (audit_status reviewed_*) | 110 |

**Source range:** 1862–1923 (Thomas 1862 → Engel 1878 → Johnson via Kappeler 1895 → Mahoney 1905 → Boothby 1908 / Grohusko 1908 → Straub 1914 → Bullock 1917 → Ensslin 1916 → MacElhone 1923).

**Ingest history:** 51 batches × ~80 recipes = 4,045 new entries from curated OCR'd manuscripts (ingest 2026-05-12). Previous slop (1,148 Wikipedia-stub drinks like Burukutu, Chhaang) deleted in the same session.

---

## Pricing (canonical)

- Free tier: $0 forever, ad-supported
- Pro monthly: **$4.99/mo**
- Pro yearly: **$45/yr** (post-launch retail)
- Pro yearly (Kickstarter founder rate, capped at 500): **$30/yr forever** — grandfathered for the operational lifetime of the app. A backer who pledges at the $30 tier or higher never pays the $45 retail rate as long as their subscription stays active.
- **No lifetime tier exists.** Anywhere. No "pay once, get forever." Older docs that reference `$59 lifetime` or `brimm_pro_lifetime` SKU are wrong and the SKU is deprecated in Play Console.
- Curate-a-Party menus: included in Pro · **$5 each** for non-Pro (post-launch single-menu retail). Kickstarter backers at the $4 tier or higher get redemption codes (SPEAK-XXXX-YYYY format). The $4 KS pledge is an early-bird discount on the eventual $5 retail price, not the retail price itself.

Older numbers in stray docs ($29.99/yr, $59 lifetime, $4.99 one-time, "half off") are all WRONG. The site was updated + redeployed 2026-05-12.

---

## Build time (canonical)

**Six months full-time** (per fact sheet). The "fourteen months" and "since 2025" variants on older docs are inconsistent and should be ignored.

---

## Brand and persona

- **Persona for the Kickstarter campaign:** `cocktail_history_nerd` (Persona 1 in the consolidated brand pack).
- **Tagline (cooking surface):** "Open your fridge. We'll figure out dinner."
- **Tagline (cocktail surface):** "Pre-Prohibition cocktails. From what's in your fridge."
- **Voice rules:** see `viral_research/_LIVE_SITE_BRAND_BRIEF.md` and `_CONSOLIDATED_BRAND_PACK.md` at `pantree-social/data/kyle_handoff/`. Period-stop rhythm. Founder-singular "I". Numbers replace adjectives. No em-dashes. Cocktail register hard-separated from cooking register.
- **Locked headline 2026-04-28:** "Open your fridge. We'll figure out dinner."
- **Hard bans:** "powerful," "seamless," "transform," "elevate," "AI-powered," "smart" (adj), "snap a pic," "users" (in user-facing copy), em-dashes anywhere.

---

## What's deployed and live

- **speakeater.com production:** all canonical numbers landed via wrangler pages deploy 2026-05-12 (fact-sheet, homepage, vs/* pages, press-kit press releases).
- **D1 pantrie-db-staging:** 6,539 drinks + 23,743 recipes. Slop deleted. New OCR ingested.
- **Android app v0.1.53 (versionCode 55):** AAB signed and ready at `android/app/build/outputs/bundle/release/app-release.aab`. Has the new $45/yr pricing baked in. Kyle pushes to Play Console Internal Testing.
- **Privacy policy + delete-account pages:** live at `speakeater.com/privacy` and `speakeater.com/delete-account`.

---

## Kickstarter draft state (project ID 1391540021)

URL: `https://www.kickstarter.com/projects/1082593906/1391540021/edit/story`

| Field | State |
|---|---|
| Project basics (title, blurb, goal $15k, 30-day duration, USD, category Games→Mobile Games / Food→Drinks) | Filled by Kyle ✓ |
| Project image (cover) | **STALE** — current `kickstarter-hero-v2.png` leads with cooking-pivot tagline. Game-first hero needs re-render. |
| Project video | Current `kickstarter-hero.mp4` is the 90-second Remotion render with cooking framing. Re-render for game-first hook (lower priority). |
| Story body (CKEditor on /edit/story) | **IN-PROGRESS** 2026-05-13 via `_kickstarter_fill_v3.cjs`. Source: cleaned `01-CAMPAIGN-PAGE.md` (0 em-dashes, doc metadata stripped). |
| Risks textarea (name="risks") | **IN-PROGRESS** 2026-05-13 via fill_v3. |
| FAQ entries (8 total post-pivot) | **NOT done.** 5 OLD menu-first FAQs currently in draft; `_kickstarter_faqs_v3.cjs` ready to delete-and-replace with 8 game-first FAQs from `_paste/03-faqs/*.txt`. |
| Reward tier $1 (Heads-Up) | Text in `_paste/04-rewards/01-1-the-heads-up.txt`. No image rendered. |
| Reward tier $4 (One Party Menu) | Text in `_paste/04-rewards/02-4-one-party-menu.txt`. No image rendered. |
| Reward tier $10 (Five Party Menus) | Text in `_paste/04-rewards/03-10-five-party-menus.txt`. No image rendered. |
| Reward tier $15 (One Mystery Night) — NEW POST-PIVOT | Text in `_paste/04-rewards/04-15-one-mystery-night.txt`. Brief at `_DESIGN_BRIEF_TIER_IMAGES_POST_PIVOT.md`. No image rendered. |
| Reward tier $25 (All Five Mystery Nights + Beta) — NEW POST-PIVOT | Text in `_paste/04-rewards/05-25-all-five-mystery-nights-beta.txt`. Brief at `_DESIGN_BRIEF_TIER_IMAGES_POST_PIVOT.md`. No image rendered. |
| Reward tier $30 (Pro Founder Rate) | Text in `_paste/04-rewards/06-30-pro-founder-rate.txt`. Brief: `_DESIGN_BRIEF_TIER_IMAGES.md` Tier 05 (brass key). No image rendered. |
| Reward tier $49 (Founding Set) | Text in `_paste/04-rewards/07-49-the-founding-set.txt` + **`out/tier-49-founding-set.png`** ✓ (pre-pivot render; copy on image OK for soft-launch but PARTIAL UPDATE needed — see post-pivot brief). |
| Reward tier $99 (Founding Member) | Text in `_paste/04-rewards/08-99-founding-member.txt` + **`out/tier-99-founding-member.png`** ✓ |
| Reward tier $250 (Founding Booklet) | Text in `_paste/04-rewards/09-250-the-founding-booklet.txt` + **`out/tier-250-founding-booklet.png`** ✓ |
| Reward tier $500 (Custom Mystery Night Commission) | Text in `_paste/04-rewards/10-500-custom-mystery-night-commission.txt`. Brief: `_DESIGN_BRIEF_TIER_IMAGES.md` Tier 09. No image rendered. |
| Reward tier $1,000 (Founder Dinner) | Text in `_paste/04-rewards/11-1-000-the-founder-dinner.txt` + **`out/tier-1000-founder-dinner.png`** ✓ (PARTIAL UPDATE — change "One menu" to "One mystery" on laptop overlay). |
| Stretch goals ($25k / $40k / $50k / $60k / $80k / $100k) | Pasteable text in `_paste/04-rewards/_stretch-goals.txt`. Lives inside Story body (no dedicated KS field). Includes the new $100k Video Clue Pack. |
| Profile + bio + photo | **NOT done.** Manual on Kickstarter — needs Kyle's photo. |
| Account verification (KYC) | **NOT done.** Manual. 1-3 day wait. |
| Kickstarter Payments / bank link | **NOT done.** Manual. |
| Submit for review | **NOT done.** Kyle clicks when ready. |

---

## What still needs work post-2026-05-13 session

1. **FAQ entries (in-progress).** 8 game-first FAQs queued in `_paste/03-faqs/01-...txt` through `08-...txt`. Script `_kickstarter_faqs_v3.cjs` runs after fill_v3 completes.
2. **Reward tiers (in-progress).** 11 tiers in `_paste/04-rewards/`. Rewards filler script written after baseline snap of `/edit/rewards` page DOM.
3. **Tier image renders (7 missing).** $1, $4, $10, $15 NEW, $25 NEW, $30, $500. Briefs: `_DESIGN_BRIEF_TIER_IMAGES.md` (existing 5) + `_DESIGN_BRIEF_TIER_IMAGES_POST_PIVOT.md` (2 new mystery tiers). PATH VALIDATION REQUIRED before any render per `feedback_no_render_without_path_validation.md`.
4. **Cover image refresh.** `/edit/basics` cover currently shows cooking-pivot tagline. Game-first hero needs new Remotion comp + render.
5. **$49 + $1000 tier image partial-update renders.** Existing renders use pre-pivot copy. Re-render with mystery-game framing per post-pivot brief.
6. **Visual storytelling for stretch goals in Story body.** Two paths: (A) rich-paste from `_paste/01-story-with-images.html` (6 base64 images embedded), or (B) manual drag-drop into KS CKEditor after fill_v3 text lands.
7. **Sample menus 02-05.** Originally flagged as old-voice; on re-read 2026-05-13 they appear brand-clean. Verify before campaign promises them.
8. **In-app Curate-a-Party feature.** Spec at `03-CURATE-A-PARTY-SPEC.md`. Not required for launch but backers may ask.
9. **In-app Mystery Nights engine.** Cloudflare Durable Object backend deployed per `project_mystery_nights_backend.md`. Client UI still building per game-agent track.

---

## Asset locations (all current)

| Asset | Path |
|---|---|
| Project cover (hero v2 — phones fanned) | `C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/kickstarter-hero-v2.png` |
| Project video | `C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/kickstarter-hero.mp4` |
| Curate-a-Party menu mockup | `C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/curate-menu-card.png` |
| Tier $49 Founding Set | `C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/tier-49-founding-set.png` |
| Tier $99 Founding Member | `C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/tier-99-founding-member.png` |
| Tier $250 Founding Booklet | `C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/tier-250-founding-booklet.png` |
| Tier $1,000 Founder Dinner | `C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/tier-1000-founder-dinner.png` |
| Signed AAB (Play Console upload) | `C:/Users/12566/Downloads/pantrie-build (1)/pantrie-build/android/app/build/outputs/bundle/release/app-release.aab` |
| Story copy with images embedded (paste into KS) | `.planning/kickstarter/_paste/01-story-with-images.html` |
| Plain-text paste files (Story/Risks/FAQs/Rewards) | `.planning/kickstarter/_paste/` |
| Brand brief | `pantree-social/data/kyle_handoff/viral_research/_LIVE_SITE_BRAND_BRIEF.md` |
| Consolidated brand pack | `pantree-social/data/kyle_handoff/viral_research/_CONSOLIDATED_BRAND_PACK.md` |
| App screenshots (for in-story use) | `pantree-social`... wait — `speakeater-site/app-screens/` |
| Tier image design brief | `.planning/kickstarter/_DESIGN_BRIEF_TIER_IMAGES.md` |
| Handoff doc with lessons learned | `.planning/kickstarter/HANDOFF.md` |

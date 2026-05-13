# Kickstarter Campaign: Live Status (2026-05-13, end of session)

Project ID: **1391540021** · Goal: **$15,000** · Duration: **30 days** · Category: **Games → Mobile Games** (primary)

---

## What's live in the draft right now

| Section | State | Detail |
|---|---|---|
| /edit/basics | Filled by Kyle | Cover image still uses cooking-pivot tagline ("Open your fridge"). Needs game-first re-render. |
| /edit/story (Story body) | ✅ **9,648 chars + 6 photographic images embedded** | Game-first content from cleaned `01-CAMPAIGN-PAGE.md`. Stretch goals section included. All em-dashes scrubbed. |
| /edit/story (Risks) | ✅ **935 chars** | Game-first "schedule risk is the obvious failure mode" framing. |
| /edit/story (Use of AI) | Not touched | Kyle to confirm "No" radio is set (per pre-pivot snap state). |
| /edit/story (FAQs) | ⚠️ **6 of 8 typed** | FAQs #3-#8 typed automatically. FAQs #1 + #2 need manual paste from `_FAQ_REMAINING_MANUAL.txt`. |
| /edit/rewards (Items) | Empty | 0 of ~13 items created. Manual. |
| /edit/rewards (Reward tiers) | Empty | 0 of 11 tiers created. Manual entry recommended due to React-form complexity. |
| /edit/rewards (Add-ons) | Empty | Skip for now; add post-launch if needed. |
| /edit/people | Not touched | Kyle: bio + photo. |
| /edit/payment | Not touched | Kyle: bank link + KYC. |
| /edit/promotion | Not touched | Optional. |

---

## What this session accomplished

### Source content hardened
- Scrubbed 25 em-dashes from `01-CAMPAIGN-PAGE.md` (3) + `02-REWARD-TIERS.md` (22). Per `feedback_brimm_writing_voice.md` hard ban.
- Patched `_build_paste_folder.cjs` regex to match new "$1." prefix instead of "$1 —".
- Regenerated all `_paste/*` files from clean sources. Verified 0 em-dashes in story/risks/faqs/rewards paste files.

### Story body
- fill_v3 re-typed 8,405 chars of clean game-first story (Story CKEditor on /edit/story).
- Patched fill_v3 to skip pre-`---` metadata header (no more "Speakeater — Campaign Page" leaked into the typed body).
- Risks textarea also re-typed (935 chars).
- Save click confirmed via post-save DOM read.

### Story body images
- Regenerated `_paste/01-story-with-images.html` (6.5MB, 6 base64 images).
- Injected HTML via CKEditor 5's `editor.setData()` accessed through `.ck-editor__editable_inline.ckeditorInstance`.
- Verified 6 `<img>` tags in editor DOM after Save. Visual storytelling done.

### FAQs (6 of 8)
- Deleted 5 old menu-first FAQs cleanly via scoped delete loop.
- Added 6 new game-first FAQs via React's `_valueTracker` pattern. Survived Save.
- FAQs #1 and #2 ("Is the app actually working?", "How does multiplayer work?") need manual paste — KS's React form keeps eating the first 2 in any batch.
- Manual paste file ready at `_FAQ_REMAINING_MANUAL.txt`.

### Documentation
- ✅ `ACTIONS-PLAN.md` — master plan with all paths surfaced.
- ✅ `_DESIGN_BRIEF_TIER_IMAGES_POST_PIVOT.md` — briefs for $15 + $25 Mystery Night tier images.
- ✅ `CURRENT_TRUTHS.md` — updated to 11-tier post-pivot mapping.
- ✅ `STATUS.md` (this file).
- ✅ `_FAQ_REMAINING_MANUAL.txt` — 2 FAQs to paste manually.

### Remotion comp
- ✅ `TierMysteryDossier.tsx` written + registered in `Root.tsx`. Ready to render for the $15 tier image (path validation gate pending).

---

## What Kyle does next (in priority order)

### NOW — 1 min
1. **Paste 2 missing FAQs.** Open `_FAQ_REMAINING_MANUAL.txt`. Add 2 FAQs at top of FAQ list on /edit/story. Click Save.

### NEXT — ~50 min
2. **Build rewards section manually.**
   - /edit/rewards → Items tab. Create ~13 items (title-only minimum). Suggested list:
     - Speakeater Pro Founder Rate ($30/yr forever)
     - One Curate-a-Party Menu (digital, choose 1 of 50)
     - All 50 Curate-a-Party Menus (digital)
     - One Mystery Night (digital, choose 1 of 5)
     - All Five Mystery Nights (digital)
     - Beta access (Internal Testing build)
     - Name in Backers screen (digital, perpetual)
     - Name engraved on Founding Members screen (digital, perpetual)
     - Direct email line to Kyle (12 months)
     - Printed Founding Mystery Booklet (physical, 60 pages)
     - Custom Mystery Night Commission (digital, bespoke)
     - Founder Dinner video call (1 hour)
     - Speakeasy World Tour menus (10 international, $25k stretch)
   - Reward tiers tab. Create 11 tiers. For each: paste title + description from `_paste/04-rewards/0X-...txt`, set price, link items, set shipping (Digital for most; "Ships to anywhere" for $250 only), set delivery (June 2026 for most), upload image if available.
   - Image paths (4 of 11 already rendered):
     - $49 Founding Set: `C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/tier-49-founding-set.png`
     - $99 Founding Member: `tier-99-founding-member.png`
     - $250 Founding Booklet: `tier-250-founding-booklet.png`
     - $1000 Founder Dinner: `tier-1000-founder-dinner.png`

### LATER — 30-60 min each
3. **Cover image refresh** (optional but recommended). New game-first hero render — currently shows cooking-pivot tagline.
4. **/edit/people** — bio + profile photo.
5. **/edit/payment** — bank link, KYC submission.
6. **Final review** via KS Preview button. Walk every section.
7. **Submit for review.** KS reviews 1-3 days. Then live.

---

## Path validation gates pending Kyle's OK

### Render 7 missing tier images
**Status:** $1, $4, $10, $15 NEW, $25 NEW, $30, $500 — all unrendered. Briefs exist. `TierMysteryDossier.tsx` ($15) is the only new comp built; the other 6 need comp files plus renders.
**Inputs:** comp sources at `C:/Users/12566/Desktop/AI_Auto_vid/remotion/src/compositions/Tier*.tsx`
**Outputs:** `C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/tier-*.png`
**Effort:** 4-6 hours to build remaining 6 comps + render all 7.
**Greenlight needed.**

### Re-render cover image with game-first hook
**Status:** stale cooking-pivot hero in /edit/basics.
**Inputs:** new comp TBW at `KickstarterHeroGame.tsx` based on existing `KickstarterHeroV2.tsx`.
**Outputs:** `out/kickstarter-hero-game.png`
**Effort:** 1-2 hours design + render.
**Greenlight needed.**

---

## Screenshots index

All snaps at `C:/Users/12566/projects/pantree-social/data/kyle_handoff/_kickstarter_inspect/`.

| Stage | File pattern |
|---|---|
| /edit/basics baseline | `snap-2026-05-13T00-21-46.png` |
| /edit/story baseline | `snap-2026-05-13T00-23-20.png` |
| fill_v3 before/after Story/Risks | `fill_v3-{00-before, 01-story-done, 02-risks-done, 03-saved}-*.png` |
| FAQ filler attempts (v3-v7) | `faqs_v{3,4,5,6,7}-*.png` |
| Story with images injected | `inject_img-{00-before, 01-after-inject, 02-saved}-*.png` |
| Rewards page probes | `rewards-probe-*.png`, `rewards-form-{new-item-form, tiers-tab, tier-form}-*.json+png` |
| Test reward fill | `rewards_test-{00, 01, 02, 03}-*.png` |
| Final state | `FINAL-story-*.png`, `FINAL-rewards-*.png`, `FINAL-stats-*.json` |

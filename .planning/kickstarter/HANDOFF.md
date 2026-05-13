# Kickstarter Fill — Handoff (2026-05-12, late)

For next-session-me. Read this before touching the Kickstarter draft again.

---

## Status snapshot

- **Draft URL:** `https://www.kickstarter.com/projects/1082593906/1391540021/edit/story`
- **All fields on one page** under `/edit/story` (Story body, Risks, Use of AI, FAQ). Sidebar nav buttons exist (Basics, Preferences, Rewards, Story, People, Payment, Promotion) but the URL pattern is `/edit/<section>` NOT `/build/<section>`.
- **Site (speakeater.com) shipped** with canonical numbers from D1 (6,539 drinks, 23,743 recipes, 2,848 pre-Prohibition, 2,846 with original-source text).
- **D1 ingest complete** — 51 batches, 4,045 new recipes from real OCR'd manuscripts. Slop (1,148 Wikipedia/disambiguation entries) deleted.
- **Hero image + video re-rendered** with the canonical numbers at `C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/`.

## What got typed into Kickstarter (and what didn't survive)

| Field | Status | Source |
|---|---|---|
| Project basics (title, blurb, goal, etc.) | Filled manually by Kyle | — |
| Story body (CKEditor) | **TYPED TWICE, clobbered both times by the FAQ script's bad selector. Re-running fill-v2 now to restore.** | `.planning/kickstarter/01-CAMPAIGN-PAGE.md` (everything before `## FAQ`) |
| Risks (textarea[name="risks"]) | Typed once, also got cleared on the second fill-v2 run (cosmetic — same content re-typed) | `.planning/kickstarter/01-CAMPAIGN-PAGE.md` (`## Risks and challenges` section) |
| FAQ entries | **NOT done.** Script clobbered Story instead of filling FAQs. See lesson below. | `.planning/kickstarter/01-CAMPAIGN-PAGE.md` (`## FAQ` section) — 7 Q/A pairs |
| Story images | **NOT done.** Need to drag-drop the rendered hero PNG (or other assets) into the CKEditor. | `C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/kickstarter-hero.png` |
| Reward tiers | **NOT done.** 9 tiers + 3 stretch goals. | `.planning/kickstarter/02-REWARD-TIERS.md` |
| Reward tier IMAGES | **NOT done.** First attempt was 10 near-identical sepia cards — design fail. Real brief is at `_DESIGN_BRIEF_TIER_IMAGES.md` — ten distinct theatric objects, ready for designer or fresh-context render. | `.planning/kickstarter/_DESIGN_BRIEF_TIER_IMAGES.md` |
| Profile / bio / photo | **NOT done.** Kyle's bio + photo. Manual on Kickstarter. | — |
| Account verification (KYC) | **NOT done.** Kickstarter flow — 1-3 day wait after submission. Manual. | — |
| Kickstarter Payments / bank link | **NOT done.** Manual. | — |
| Submit for review | **NOT done.** User clicks. | — |

## Hard-learned lessons (don't repeat these)

### 1. The Story page contains MULTIPLE sections in one scroll
The `/edit/story` URL renders: Project story (CKEditor) → Risks (textarea[name="risks"]) → Use of AI (radio + checkboxes) → FAQ ("Add another FAQ" button). There is **no separate `/edit/risks` page**. URL nav for risks/faqs will fail.

### 2. FAQ Q/A fields don't exist until you click "Add another FAQ"
The Add button is visible. The Q/A fields are not rendered to the DOM until after the click. Any script that searches for `[placeholder*="question"]` BEFORE clicking the Add button will find nothing.

### 3. The Story CKEditor is the ONLY `.ck-editor__editable_inline` on the page
This is the source of the FAQ script disaster from 2026-05-12. Naively searching for the "last" `.ck-editor__editable_inline` and treating it as the FAQ answer field hits the Story body CKEditor every time. **Targeting strategy for FAQ answer must be scoped to the just-added FAQ form** — query relative to the latest-added question input's parent, not the global document.

### 4. Page reload clears unsaved CKEditor content
Kickstarter does NOT auto-save the Story CKEditor as you type. A `page.reload()` after content was typed but before any explicit Save button click loses the content. There's a beforeunload dialog — auto-accept it with `page.on('dialog', d => d.accept())` but be aware accepting = data loss for unsaved fields.

### 5. CDP attach pattern (don't launch fresh browsers when user is hands-limited)
Launch `_kickstarter_hold.cjs` ONCE with `--remote-debugging-port=9222`. The user navigates. Subsequent scripts attach via `chromium.connectOverCDP('http://localhost:9222')`. NEVER launch a second persistent context against the same session dir — it'll fail with profile-locked.

## Working scripts

| Script | Purpose | Known to work |
|---|---|---|
| `_kickstarter_hold.cjs` | Open Chromium + CDP port 9222 + hold | ✓ |
| `_kickstarter_cdp_snap.cjs` | Attach via CDP, screenshot + DOM dump | ✓ |
| `_kickstarter_reload_snap.cjs` | Reload + dialog auto-accept + snap | ✓ |
| `_kickstarter_fill_v2.cjs` | Fill Story (CKEditor) + Risks (textarea) via CDP | ✓ — proven 2× |
| `_kickstarter_fill.cjs` | Interactive menu (user presses 1-5) | ✓ (manual mode) |
| `_kickstarter_auto.cjs` | Old multi-section auto — **uses WRONG URLs** (/build/ instead of /edit/). Patched once, still wrong model. Don't use without fixing URL slugs. | ✗ |
| `_kickstarter_faqs_v1.cjs` | FAQ filler — **BROKEN. Targets Story CKEditor instead of FAQ answer. Do not run.** | ✗ |

All scripts at `C:/Users/12566/projects/pantree-social/data/kyle_handoff/`.

## Correct FAQ flow (write this script next session, do not reuse v1)

```text
1. Scroll FAQ heading into view.
2. For each of the 7 Q/A from getFaqs():
   a. Click "Add another FAQ" button.
   b. WAIT for new DOM to appear (poll for new input element count to increase).
   c. Find the JUST-ADDED question input. Strategy: capture inputs count BEFORE click,
      then after click, use the input that did NOT exist before. (Closest ancestor approach.)
   d. Find the JUST-ADDED answer field IN THE SAME FAQ ROW. Use the latest-added
      question's closest parent fieldset/section/li, then querySelector inside it
      for textarea / contenteditable / .ck-editor__editable_inline.
   e. Type question. Tab/click to answer. Type answer.
3. After all FAQs, click any visible "Save" button.
```

The key fix is **scoped element finding** — never use `.last()` globally on a page with multiple CKEditors when one of them is the Story body.

## Content paths (canonical)

| File | What's in it |
|---|---|
| `.planning/kickstarter/01-CAMPAIGN-PAGE.md` | Story body + Risks + FAQ + brand-voice intro. Already brand-clean, single-persona (cocktail_history_nerd). |
| `.planning/kickstarter/02-REWARD-TIERS.md` | 9 tiers in brand voice. Each tier has price + cap + description. |
| `.planning/kickstarter/04-SAMPLE-MENUS/01-bees-knees-garden-party.md` | First sample menu (brand-voice rewrite done). The other 4 in this dir are old-voice and need rewrite. |
| `.planning/kickstarter/05-VIDEO-SCRIPT.md` | Original 90-second video script. The Remotion comp at `C:/Users/12566/Desktop/AI_Auto_vid/remotion/src/compositions/KickstarterHero.tsx` is in sync. |
| `.planning/kickstarter/06-PRELAUNCH-FUNNEL.md` | Prelaunch email sequence + social cadence (still in old voice, needs sweep). |
| `.planning/kickstarter/07-LAUNCH-SEQUENCE.md` | Day-by-day playbook for 30-day campaign (still in old voice). |

## D1 canonical numbers (use these in any new copy)

- Food recipes: **23,743**
- Drinks total: **6,539**
- Cocktails: **5,036**
- Mocktails: **1,361**
- Modern non-alcoholic drinks: **142**
- Pre-Prohibition cocktails: **2,848** (source_year < 1933)
- With original-source text preserved: **2,846**
- Public Codex (free PDF): **50** hand-curated
- Primary source range: **1862–1923** (Thomas, Engel, Johnson via Kappeler, Mahoney, Boothby, Grohusko, Straub, Bullock, Ensslin, MacElhone)

## Final paths

- Hero image (1200×675, 887KB): `C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/kickstarter-hero.png`
- Hero video (1080×1920, 7.9MB, 90s): `C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/kickstarter-hero.mp4`
- Both have new canonical numbers baked in (2,846 manuscript-sourced · 2,848 pre-Prohibition · 50 hand-built menus).

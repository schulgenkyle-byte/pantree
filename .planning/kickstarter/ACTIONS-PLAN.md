# Kickstarter Actions: Master Plan (2026-05-13)

Single source of truth for what the Kickstarter agent will do, in what order, with every input/output path surfaced. Survives across sessions.

Last updated: 2026-05-13. Pivoted 2026-05-12 to game-first multiplayer mystery party games.

---

## Phase order (must run sequentially; each owns the Chromium tab)

| # | Phase | Script | Approx runtime | Status |
|--:|---|---|---|---|
| 1 | Snap baseline of every KS section | `_ks_snap_smart.cjs` | <10s per section | basics ✓, story ✓ |
| 2 | Re-type Story body + Risks | `_kickstarter_fill_v3.cjs` | ~17 min | running |
| 3 | Delete old FAQs + type 8 new FAQs | `_kickstarter_faqs_v3.cjs` | ~12 min | pending |
| 4 | Paste 11 reward tiers (text only) | `_kickstarter_rewards_v1.cjs` (TBW) | ~15 min | pending |
| 5 | Upload tier images to reward cards | `_kickstarter_rewards_v1.cjs` (cont'd) | ~5 min | pending |
| 6 | Snap final /edit/story + /edit/rewards | `_ks_snap_smart.cjs` | <10s | pending |
| 7 | Build STATUS.md with embedded screenshots | inline | <5 min | pending |

**Manual hand-off to Kyle (cannot be automated):** Profile + bio + photo, account verification (KYC), Kickstarter Payments / bank link, Submit for review click.

---

## Source files (READ paths)

| Source | Used by | Notes |
|---|---|---|
| `.planning/kickstarter/01-CAMPAIGN-PAGE.md` | fill_v3 (Story + Risks) | em-dashes scrubbed 2026-05-13. fill_v3 slices off everything before first `---` to skip doc metadata. |
| `.planning/kickstarter/02-REWARD-TIERS.md` | rewards script | em-dashes scrubbed 2026-05-13. 11 tiers + stretch ladder. |
| `.planning/kickstarter/_paste/03-faqs/*.txt` | faqs_v3 | 8 FAQs (post-pivot). Regenerated 2026-05-13 from cleaned campaign-page.md. |
| `.planning/kickstarter/_paste/04-rewards/*.txt` | rewards script | 11 tier descriptions. Regenerated 2026-05-13 from cleaned 02-REWARD-TIERS.md. |
| `.planning/kickstarter/_paste/_stretch-goals.txt` | reference only | Stretch goals live inside Story body, not a dedicated KS field. |
| `C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/tier-*.png` | rewards image upload | 4 renders exist post-pivot ($49, $99, $250, $1000). 7 still to render. |
| `C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/kickstarter-hero-v2.png` | /edit/basics cover image | **STALE** — shows cooking-pivot tagline. Re-render needed for game-first hero. |

---

## Write/output paths

| Destination | Producer | Type |
|---|---|---|
| Kickstarter Story body CKEditor | fill_v3 | human-paced keystrokes |
| Kickstarter Risks textarea | fill_v3 | human-paced keystrokes |
| Kickstarter FAQ textareas (`project_faq_question_N` / `project_faq_answer_N`) | faqs_v3 | human-paced keystrokes |
| Kickstarter reward tier text fields | rewards_v1 | human-paced keystrokes |
| Kickstarter reward tier image uploads | rewards_v1 | file input — drag-drop emulation |
| `_kickstarter_inspect/*.png` | snap scripts | full-page screenshots |
| `_kickstarter_inspect/*.json` | snap scripts | DOM markers (headings, textareas, buttons) |
| `_kickstarter_fill_v3_log.json` | fill_v3 | step-by-step ts log |
| `_kickstarter_faqs_v3_log.json` | faqs_v3 | step-by-step ts log |

All snap + log paths live under `C:/Users/12566/projects/pantree-social/data/kyle_handoff/`.

---

## Path validation gates (per `feedback_no_render_without_path_validation.md`)

Before any tier image render fires, the agent MUST surface to Kyle:

**Inputs:**
- Composition source: `C:/Users/12566/Desktop/AI_Auto_vid/remotion/src/compositions/Tier*.tsx`
- Asset reuse: `C:/Users/12566/Desktop/AI_Auto_vid/remotion/public/app-pantry.png`, `app-recipes.png`, `app-bootlegger.png`
- Design brief: `.planning/kickstarter/_DESIGN_BRIEF_TIER_IMAGES.md` (Tier 01, 02, 03, 05, 08, 09) + `_DESIGN_BRIEF_TIER_IMAGES_POST_PIVOT.md` (Tier $15, $25)

**Outputs:**
- 7 PNGs: `C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/tier-1-headsup.png`, `tier-4-one-menu.png`, `tier-10-five-menus.png`, `tier-15-mystery-night.png`, `tier-25-mystery-nights-beta.png`, `tier-30-pro-founder.png`, `tier-500-custom-mystery.png`

Wait for Kyle's explicit "OK render" before running.

---

## Brand voice gates (per `feedback_brimm_writing_voice.md`)

Hard bans on Speakeater-facing copy: em-dashes, "powerful", "seamless", "transform", "elevate", "AI-powered", "smart" (adj), "users".

**Pre-flight check before any typing run:** `grep -c '—' <source>` must return 0 on:
- `01-CAMPAIGN-PAGE.md` (story + risks source)
- `02-REWARD-TIERS.md` (rewards source)
- `_paste/01-story.txt`, `_paste/02-risks.txt`
- `_paste/03-faqs/*.txt`, `_paste/04-rewards/*.txt`

Cleared 2026-05-13. If em-dashes reappear (e.g., from a regen against a dirty source), re-run `_fix_emdashes.js` then `_build_paste_folder.cjs`.

---

## Outstanding open questions (need Kyle's input)

1. **Cover image refresh.** Current /edit/basics cover is the cooking-pivot hero ("Open your fridge. We'll figure out dinner."). Should it be re-rendered to lead with the mystery-game framing? (Yes is the brand-correct answer. Renders a new Remotion comp.)
2. **Project title + blurb on /edit/basics.** Verify post-pivot wording is in place. Snap shows "Speakeater | A Storytelling Revolution" — that's probably right but should be confirmed.
3. **Visual storytelling for stretch goals inside Story body.** Two options:
   - A: Rich-paste from `_paste/01-story-with-images.html` (6 base64-embedded images). Requires a separate paste-via-CDP script. Story body is currently being typed as plain text by fill_v3.
   - B: After fill_v3 finishes the text, drag-drop images directly into the KS CKEditor at the relevant stretch goal positions. Manual but reliable.
4. **Save click cadence.** fill_v3 and faqs_v3 click Save once at end. KS may have form-level auto-save. If the Save button text changes to "Saving..." then back to "Save", the click worked. If it stays "Save", a second click may be needed.
5. **Tier image renders.** 7 tiers need NEW Remotion comps + renders. Building all 7 is 4-6 hours of design implementation. Lower priority than getting the text into the draft.

---

## Manual Kyle steps (cannot be automated)

After scripts finish, Kyle does these in the Kickstarter UI:

1. **/edit/people page:** Bio + profile photo.
2. **Account verification (KYC):** Identity check. 1-3 day Kickstarter review wait.
3. **Kickstarter Payments / bank link:** Connect bank account for fund disbursement.
4. **Final review:** Walk every section. Click Preview. Read the public-facing render.
5. **Submit for review:** When everything looks right, click Submit. KS reviews 1-3 days. Then live.

---

## Status (live)

See `STATUS.md` for current section-by-section completion + embedded screenshots.

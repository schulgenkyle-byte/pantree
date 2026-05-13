# Master Prompt for Next Session

Copy everything between the dashes below and paste into a fresh Claude Code session.

---

I'm Kyle, solo dev on Speakeater (1920s/Prohibition-themed cooking-and-cocktail Android app, launching on Google Play 2026-06-10). I'm running a Kickstarter campaign at `https://www.kickstarter.com/projects/1082593906/1391540021/edit/story` with a $15k floor goal, 30-day window, cocktail_history_nerd persona target. We've been working on it across multiple sessions.

**Before anything else, read these files in order:**

1. `C:/Users/12566/Downloads/pantrie-build (1)/pantrie-build/.planning/kickstarter/CURRENT_TRUTHS.md` — canonical numbers, deployed state, what's done vs not
2. `C:/Users/12566/Downloads/pantrie-build (1)/pantrie-build/.planning/kickstarter/HANDOFF.md` — lessons learned, working scripts vs broken ones
3. `C:/Users/12566/projects/pantree-social/data/kyle_handoff/viral_research/_LIVE_SITE_BRAND_BRIEF.md` — voice fingerprint, lexicon, banned phrases
4. `C:/Users/12566/projects/pantree-social/data/kyle_handoff/viral_research/_CONSOLIDATED_BRAND_PACK.md` — six personas, video render gates
5. `C:/Users/12566/Downloads/pantrie-build (1)/pantrie-build/.planning/kickstarter/_DESIGN_BRIEF_TIER_IMAGES.md` — spec for the 10 tier images (4 marquee tiers rendered, 6 still needed)
6. `C:/Users/12566/Downloads/pantrie-build (1)/pantrie-build/NAMING.md` — three names coexist by design (pantrie internal codename / Brimm legacy / Speakeater current). LOAD-BEARING. Don't "fix" the names.

**Honor these hard rules from memory:**

- No em-dashes in any Speakeater-facing copy. Use periods, commas, parens.
- Period-stop rhythm. Founder-singular "I". Numbers replace adjectives.
- Don't launch focus-grabbing Playwright/Chromium without confirming user is at the keyboard. Use CDP attach to an already-open Chromium (port 9222) when possible.
- Surface every input/output path before any render. Wait for explicit OK.
- Don't use `.last()` globally on a page with multiple `.ck-editor__editable_inline` elements — Kickstarter's Story page has ONE CKEditor (the Story body) AND adding FAQs adds more. Scope your finder to the just-added FAQ row.
- Kickstarter editor URLs use `/edit/<section>` NOT `/build/<section>`. The Story page contains ALL of Story + Risks + FAQ + Use-of-AI as one scroll.
- The `_kickstarter_auto.cjs` and `_kickstarter_faqs_v1.cjs` scripts at `C:/Users/12566/projects/pantree-social/data/kyle_handoff/` are BROKEN. Do not run them. Use `_kickstarter_fill_v2.cjs` (proven Story+Risks) or write a properly-scoped FAQ filler.
- The user already has the campaign-fill content typed into Story + Risks. Don't re-run fill-v2 unless user asks.

**Canonical numbers (do not invent others):**

- 23,743 food recipes · 5,036 cocktails · 1,361 mocktails · 142 modern drinks · 6,539 drinks total
- 2,848 pre-Prohibition cocktails · 2,846 with original-source text preserved · 50 in free public Codex
- Pricing: $4.99/mo or $45/yr (Kickstarter founder rate $30/yr capped at 500 backers)
- $15k floor / 30 days / cocktail_history_nerd persona
- Build time: six months full-time

**What I want you to do this session — TOP PRIORITY:**

**(PRIMARY)** Build the Curate-a-Party feature inside the Android app per `_CURATE_A_PARTY_BUILD_SPEC.md`. This is a 4-6 hour focused Compose build with photographic hero images (NOT SVG, NOT CSS shapes, NOT cheap-looking objects) + redemption-code paywall for Kickstarter backers ($5/menu retail, free via SPEAK-XXXX-YYYY codes). Output: 8-10 device screenshots that go into the Kickstarter campaign as visual proof the feature ships. Read `_CURATE_A_PARTY_BUILD_SPEC.md` BEFORE writing any code. The bar is "indistinguishable from a shipped, professionally-curated feature" — Kyle has explicitly rejected SVG-substitute-for-photography work as cheap. He'll plug in his phone and sideload to take real screenshots once the build is done.

**Secondary if context allows:**

- (A) Render the 6 remaining tier images per the design brief: $1 matchbook, $4 menu-peek, $10 fanned menus, $25 phone-on-stack, $30 brass key, $500 typed menu. Each its own Remotion composition. The 4 marquee tiers ($49 / $99 / $250 / $1000) are already rendered in `C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/`.
- (B) Rewrite sample menus 02-05 in brand voice. Originals archived at `.planning/kickstarter/_archive/sample_menus_v1_old_voice/`. Match the brand discipline of menu 01 at `.planning/kickstarter/04-SAMPLE-MENUS/01-bees-knees-garden-party.md` (source citations, period-stop rhythm, manuscript register, no Pinterest entertaining voice).
- (C) Fill the 7 FAQs in the Kickstarter draft via Playwright. URL `/edit/story`. The FAQ Add-Another button is visible. The Q/A fields only render AFTER clicking Add. Scope element finder to the just-added form, not globally. Content at `.planning/kickstarter/_paste/03-faqs/*.txt`.
- (D) Build the in-app Curate-a-Party feature per the spec at `.planning/kickstarter/03-CURATE-A-PARTY-SPEC.md`. New D1 tables (`party_menu` / `party_menu_item` / `party_menu_purchase`). New Android Parties tab. New backend endpoints. New PDF generator. Estimated 4-6 hours focused work.
- (E) Something else specific — describe.

Confirm the read-first list above is done before starting. Surface every file path you plan to touch before touching it. If context runs short before completion, update `HANDOFF.md` and `CURRENT_TRUTHS.md` with what was actually done, what's left, what broke. Archive any stale content rather than deleting it.

---

End of master prompt.

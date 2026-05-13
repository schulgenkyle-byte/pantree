# Tier Image Status (2026-05-12 audit)

Each of the 11 post-pivot tiers in `02-REWARD-TIERS.md` needs a card image for the Kickstarter reward column. This document is the source of truth for what exists, what's stale, what's missing, and exactly how to fix each gap.

Remotion project: `C:/Users/12566/Desktop/AI_Auto_vid/remotion/`
Render output dir: `C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/`

Source compositions that produce tier images:
- `TierCard.tsx` — generic price+title parchment (the "do NOT ship" pattern per `_DESIGN_BRIEF_TIER_IMAGES.md`)
- `TierMarquee.tsx` — distinct-object cards for $49 / $99 / $250 / $1000 (active)
- `TierMysteryDossier.tsx` — dossier card for $15 (built, not yet rendered)

---

## Per-tier breakdown

### $1 — The Heads-Up

| | |
|---|---|
| **File** | `out/tier-00--.png` |
| **State** | STALE — generic parchment template, no matchbook |
| **Brief** | `_DESIGN_BRIEF_TIER_IMAGES.md` Tier 01 (matchbook on bar towel) |
| **Composition** | Not built. `TierMatchbook.tsx` is the named comp the brief calls for. |
| **Action** | Build `TierMatchbook.tsx` in `remotion/src/compositions/`, register in `Root.tsx`, render. Effort: 120-180 lines of TSX. |

### $4 — One Party Menu

| | |
|---|---|
| **File** | `out/tier-01--.png` |
| **State** | STALE — generic template; brief calls for a menu card half-pulled from leather portfolio. Also: copy reads "One Menu" but post-pivot name is "One Party Menu" to disambiguate from Mystery Night. |
| **Brief** | `_DESIGN_BRIEF_TIER_IMAGES.md` Tier 02 (menu peek from portfolio) |
| **Composition** | Not built. `TierMenuCardPeek.tsx` per brief. |
| **Action** | Build, register, render. |

### $10 — Five Party Menus

| | |
|---|---|
| **File** | `out/tier-02--.png` |
| **State** | STALE — generic template; brief calls for five menu cards fanned on velvet. |
| **Brief** | `_DESIGN_BRIEF_TIER_IMAGES.md` Tier 03 (fanned menus) |
| **Composition** | Not built. `TierMenusFanned.tsx` per brief. |
| **Action** | Build, register, render. |

### $9 — One Mystery Night (POST-PIVOT, REPRICED 2026-05-12)

| | |
|---|---|
| **File** | `out/tier-9-mystery-night.png` |
| **State** | RENDERED. Reads "$9 · ONE MYSTERY NIGHT · UP TO EIGHT PHONES" at bottom. |
| **Brief** | `_DESIGN_BRIEF_TIER_IMAGES_POST_PIVOT.md` Tier $15 (mystery dossier + magnifying lens) — visual still applies, only price changed. |
| **Composition** | `TierMysteryDossier.tsx` (registered, hardcoded $9). |
| **Action** | None. Use as-is. |

```bash
# To re-render after copy edits:
cd "C:/Users/12566/Desktop/AI_Auto_vid/remotion"
npx remotion still src/index.ts TierMysteryDossier "out/tier-9-mystery-night.png" --overwrite
```

### $19 — All Five Mystery Nights + Beta (POST-PIVOT, REPRICED 2026-05-12)

| | |
|---|---|
| **File** | NONE on disk |
| **State** | MISSING. Old `out/tier-03--.png` says "$25 · Twenty Menus + Beta" — wrong on every axis. |
| **Brief** | `_DESIGN_BRIEF_TIER_IMAGES_POST_PIVOT.md` Tier $25 (five dossiers stacked + phone with lobby) — visual still applies, **update price tag to $19**. |
| **Composition** | Not built. `TierFiveDossiers.tsx` per brief. |
| **Action** | Build, register, render. Effort: 120-180 lines TSX (the phone-screen mockup adds ~40 lines). When building, set the bottom tag to `$19 · FIVE MYSTERIES · BETA ACCESS · MMXXVI`. |

### $30 — Pro Founder Rate

| | |
|---|---|
| **File** | `out/tier-04--.png` |
| **State** | STALE — generic template; brief calls for brass skeleton key on parchment with wax seal. |
| **Brief** | `_DESIGN_BRIEF_TIER_IMAGES.md` Tier 05 (brass key) |
| **Composition** | Not built. `TierBrassKey.tsx` per brief. |
| **Action** | Build, register, render. |

### $49 — The Founding Set

| | |
|---|---|
| **File** | `out/tier-49-founding-set.png` |
| **State** | ON-BRAND but COPY IS PRE-PIVOT. Composition (leather binder + phone) is good. Headline reads "Fifty menus. Hand-built." — post-pivot needs to mention Mystery Nights. |
| **Brief** | `_DESIGN_BRIEF_TIER_IMAGES_POST_PIVOT.md` OBSOLETE section, partial update for Tier 06 |
| **Composition** | `TierMarquee.tsx` → `FoundingSet` (lines 42-99) |
| **Action** | Edit two strings in `TierMarquee.tsx` then re-render: |

Strings to change in `TierMarquee.tsx`:
- Line ~60 eyebrow: "The Founding Set" → keep
- Line ~61 headline: `"Fifty menus.<br />Hand-built."` → `"Five mysteries.<br />Fifty menus."`
- Line ~63-64 subtitle: rewrite to lead with Mystery Nights, then menus
- Line ~92 bottom tag: `"$49 · ALL FIFTY MENUS · FOUNDER RATE"` → `"$49 · FIVE MYSTERIES · FIFTY MENUS · FOUNDER RATE"`
- The 5×10 thumbnail grid should arguably show 5 mystery dossier tiles + 50 menu thumbnails. Acceptable to ship as-is for soft launch; flag for v2.

```bash
cd "C:/Users/12566/Desktop/AI_Auto_vid/remotion"
npx remotion still src/index.ts TierMarquee "out/tier-49-founding-set.png" --props='{"tierKey":"founding-set"}' --overwrite
```

### $99 — Founding Member

| | |
|---|---|
| **File** | `out/tier-99-founding-member.png` |
| **State** | ON-BRAND — brass plaque with "Your Name Here". |
| **Brief** | `_DESIGN_BRIEF_TIER_IMAGES.md` Tier 07 |
| **Composition** | `TierMarquee.tsx` → `FoundingMember` |
| **Action** | None. Use as-is. |

### $250 — The Founding Booklet

| | |
|---|---|
| **File** | `out/tier-250-founding-booklet.png` |
| **State** | ON-BRAND — open booklet with Aviation cocktail spread. |
| **Brief** | `_DESIGN_BRIEF_TIER_IMAGES.md` Tier 08 |
| **Composition** | `TierMarquee.tsx` → `FoundingBooklet` |
| **Action** | None. Use as-is. Post-pivot the booklet content could shift to a Mystery Night spread instead of a cocktail page, but the current render still reads as "physical artifact". Acceptable. |

### $500 — Custom Mystery Night Commission

| | |
|---|---|
| **File** | `out/tier-09--.png` says "$1,000 · Founder Dinner" — completely wrong slot. |
| **State** | NO IMAGE for $500 actually exists. The placeholders only go to tier-09. The $500 was previously $1,000 in the pre-pivot tier ordering. |
| **Brief** | `_DESIGN_BRIEF_TIER_IMAGES.md` Tier 09 (half-typed menu on stationery) — needs post-pivot copy adjustment to say "Custom Mystery Night" not "Custom Menu". |
| **Composition** | Not built. `TierTypedMenu.tsx` per brief. |
| **Action** | Build, register, render. Update typed-content text to be a Mystery Night cast list draft, not a cocktail menu. |

### $1,000 — The Founder Dinner

| | |
|---|---|
| **File** | `out/tier-1000-founder-dinner.png` |
| **State** | ON-BRAND but COPY IS PRE-PIVOT. Headline reads "Two cocktails. One menu." Post-pivot brief says: "01:00 · One hour · One mystery · Two cocktails" |
| **Brief** | `_DESIGN_BRIEF_TIER_IMAGES_POST_PIVOT.md` Tier 10 partial update |
| **Composition** | `TierMarquee.tsx` → `FounderDinner` |
| **Action** | Edit headline + laptop-screen bottom-bar copy in `TierMarquee.tsx`, then re-render: |

```bash
cd "C:/Users/12566/Desktop/AI_Auto_vid/remotion"
npx remotion still src/index.ts TierMarquee "out/tier-1000-founder-dinner.png" --props='{"tierKey":"founder-dinner"}' --overwrite
```

---

## Summary

| Tier | State | Action |
|---|---|---|
| $1 Heads-Up | stale generic | build `TierMatchbook` (~3h) |
| $4 One Party Menu | stale generic | build `TierMenuCardPeek` (~3h) |
| $10 Five Party Menus | stale generic | build `TierMenusFanned` (~3h) |
| **$9 One Mystery Night** | **OK** | none (rendered) |
| $19 Five Mysteries + Beta | missing | build `TierFiveDossiers` (~4h) |
| $30 Pro Founder Rate | stale generic | build `TierBrassKey` (~3h) |
| **$49 Founding Set** | **OK** | none (rendered, post-pivot copy) |
| $99 Founding Member | OK | none |
| $250 Founding Booklet | OK | none |
| $500 Custom Mystery | missing | build `TierTypedMenu` (~3h) |
| **$1,000 Founder Dinner** | **OK** | none (rendered, post-pivot copy) |

**Status: 5 of 11 tier cards ready** (`$9, $49, $99, $250, $1,000`). The remaining 6 are all "build new composition" tasks following the briefs, estimated 19-22h of focused TSX work. Schedule as a separate session.

**Pricing change 2026-05-12:** the two Mystery Night tiers were repriced from $15 → $9 (One Mystery Night) and $25 → $19 (All Five + Beta) so Kickstarter pledges meaningfully beat post-launch retail. Image filenames now match the new prices.

---

## When ready, copy renders into the Kickstarter paste folder

```bash
# After rendering, mirror the cards into _paste/tier-images/ so they're
# alongside the other campaign artifacts.
mkdir -p "C:/Users/12566/Downloads/pantrie-build (1)/pantrie-build/.planning/kickstarter/_paste/tier-images"
cp "C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/tier-9-mystery-night.png" \
   "C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/tier-49-founding-set.png" \
   "C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/tier-99-founding-member.png" \
   "C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/tier-250-founding-booklet.png" \
   "C:/Users/12566/Desktop/AI_Auto_vid/remotion/out/tier-1000-founder-dinner.png" \
   "C:/Users/12566/Downloads/pantrie-build (1)/pantrie-build/.planning/kickstarter/_paste/tier-images/"
```

Then in Kickstarter's reward editor, upload each PNG to the matching tier's card image slot.

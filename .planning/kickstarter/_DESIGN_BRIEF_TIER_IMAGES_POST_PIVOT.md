# Tier Image Design Brief: Post-Pivot Additions (2026-05-12)

Two new tiers were added in the game-first pivot. Briefs follow the same conventions as `_DESIGN_BRIEF_TIER_IMAGES.md` (house rules, palette, type, texture, banned moves).

Also marks two old briefs as **OBSOLETE** post-pivot. See bottom of this doc.

---

## Tier $15: One Mystery Night

**The object:** A brass-bound mystery dossier, half-open on a writing desk, with a magnifying lens resting on the cast-card peek.

**Composition:** Top-down 5° angle. A dark leather-bound dossier center frame, slightly off-axis at 8° rotation. Brass corners on all four edges of the dossier, polished, catching candlelight. Cover stamped in goldBright with "MYSTERY NIGHT №01" and a small Speakeater monogram. The dossier is open just enough at the bottom-right corner to show ONE cast card peeking out: a photograph (sepia portrait), a name plate, a printed role. A round brass-rimmed magnifying lens rests across the open corner, the lens catching the cast-card photo and magnifying part of the name to be slightly readable. A fountain pen with cap off rests at the upper-left of the dossier. Faint smoke from an unseen cigar ghosts the upper-right corner.

**Type on the cover (engraved/embossed appearance):**
- Top eyebrow (Inter caps, letterspaced 10px): "SPEAKEATER · MYSTERY DOSSIER"
- Center title (Fraunces italic, 90pt): "Murder at the Algonquin"
- Below title (Fraunces 32pt): "Manhattan, 1924. The Round Table set."
- Bottom-right corner (mono 10pt): "Five characters. Three hours. One reveal."

**On the cast-card peek:**
- A sepia portrait silhouette (no face detail — outline only, the mystery)
- Type at the top: "GUEST №3"
- Center: "Dorothy Parker" (or whatever the placeholder name)
- Below: "Theatre critic. Knows everyone's secrets."

**Palette:** ink-deep leather, goldBright brass corners, sepia cast card, paperBright magnifying-lens glass, oxblood pen body. Background walnut wood with subtle grain.

**One imperfection:** a small inkblot near the pen tip, suggesting a real session of brief-writing in progress.

---

## Tier $25: All Five Mystery Nights + Beta

**The object:** Five mystery dossiers stacked on a marble bar top, the top one open and showing five cast cards in a partial fan. A phone (showing the Speakeater game lobby) leans against the stack.

**Composition:** 3/4 angle from above. Five dossiers stacked, each one a slightly different leather tone (ink, oxblood, sepiaInk, walnut, dark green). Each spine stamped in gold with a different title: "Algonquin", "Bootlegger's Wife", "Last Toast at the Ritz", "Heir to the Pendennis", "Vanishing Socialite". Brass corners on each. The TOP dossier is open, with five cast cards spread in a partial fan: each card a sepia portrait silhouette + name + role. Brass key resting on the top of the stack as a visual anchor.

A phone leans against the right edge of the stack at a 20° angle, screen on, displaying the Speakeater "Host a Game" lobby with a four-letter code: "RYAS" prominently shown, with five player slots filling in. The screen casts a faint cool blue light onto the marble.

**Type on the open dossier spread (left page):**
- Top eyebrow (Inter): "MYSTERY DOSSIER №01"
- Title (Fraunces italic, 60pt): "Murder at the Algonquin"
- Subtitle: "Manhattan, 1924. Three acts. Reveal at the last cocktail."

**Type on the cast cards (each, small):**
- GUEST №1 through GUEST №5
- Name + one-line role hint
- Tiny silhouette portrait

**On the phone screen (rendered as a clean mockup):**
- Top: "SPEAKEATER" wordmark
- Center: "ROOM CODE: RYAS"
- Below: five player slots, three filled with first names + sepia silhouettes, two empty
- Bottom: "Tap Begin when all five join"

**Tag (overlay, bottom-right, embossed appearance):** "FIVE MYSTERIES · BETA ACCESS · MMXXVI"

**Palette:** marble bar in pale paperBright tones, leather stack in mixed ink/oxblood/walnut/sepia, brass throughout (goldBright + goldHot), the phone screen the only cool-tone element (light blue glow). Candle off-frame implied by goldHot reflections on the brass corners.

**One imperfection:** A coffee cup ring stain on the marble, lower-left. Suggests this is Kyle's actual writing desk, not a studio shot.

---

## Implementation notes

**New compositions needed:**
- `TierMysteryDossier.tsx` (for $15)
- `TierFiveDossiers.tsx` (for $25)

**Estimated effort per new comp:** 120-180 lines of TSX. Heavy on CSS gradients, SVG for the brass corners + magnifying lens + cast-card layouts. The phone-screen mockup for $25 needs about 40 lines of nested div + flex composition.

**Render commands (once built):**
```bash
cd "C:/Users/12566/Desktop/AI_Auto_vid/remotion"
npx remotion still src/index.ts TierMysteryDossier "out/tier-15-mystery-night.png" --overwrite
npx remotion still src/index.ts TierFiveDossiers "out/tier-25-mystery-nights-beta.png" --overwrite
```

---

## OBSOLETE briefs (do not render)

The following briefs in `_DESIGN_BRIEF_TIER_IMAGES.md` are **superseded** by the post-pivot tier set:

### Tier 04 — $25 Twenty Menus + Beta (OBSOLETE)
The new $25 tier is "All Five Mystery Nights + Beta", not twenty menus. Use the new `Tier $25` brief above. The old phone-on-stack-of-menus composition does not communicate the multiplayer game story.

### Tier 06 — $49 The Founding Set (PARTIAL UPDATE)
The composition (leather binder + phone) is still good visually, but the engraved title should read **"THE FOUNDING SET · FIVE MYSTERIES · FIFTY MENUS · MMXXVI"** instead of "FIFTY MENUS · MMXXVI". The thumbnail grid on the right page should be tiles for the 5 Mystery Nights + a "+50 menus" indicator, not 50 menu thumbnails alone. The existing render at `out/tier-49-founding-set.png` does NOT reflect this — it predates the pivot. A re-render is preferred but not blocking (the old render still works as a placeholder for soft-launch).

### Tier 10 — $1,000 Founder Dinner (PARTIAL UPDATE)
The composition is still good. The bottom-bar text on the laptop screen should now read **"01:00 · One hour · One mystery · Two cocktails"** instead of "One menu · Two cocktails". Existing render at `out/tier-1000-founder-dinner.png` may need re-render for fidelity.

---

## Hard rule (unchanged from pre-pivot brief)

If any rendered card could be confused for any other tier's card, it has failed. Each card is an object from a different drawer.

The viewer should be able to identify which tier from 200px away by shape alone: matchbook, menu fan, dossier, dossiers-stacked, brass key, leather binder, plaque, booklet, typed page, video call. Color is secondary. SHAPE first.

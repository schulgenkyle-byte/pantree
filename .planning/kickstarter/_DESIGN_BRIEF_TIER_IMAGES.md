# Tier Image Design Brief — Speakeater Kickstarter

For next-session-me, a designer-for-hire, or anyone who picks up this campaign.

These are NOT ten identical price cards. They are ten distinct objects from inside a 1925 establishment. Each one is a small theater set. The viewer should be able to point at any tier and know what they get without reading a word.

---

## House rules (apply to all 10)

**Aspect ratio:** 1200×900 (Kickstarter renders tier images at roughly 4:3 in the reward column. Larger than 1024×768 and never wider than 16:9).

**Palette (use only these):**
- `paper` `#F2E6CC` — aged document
- `paperBright` `#FAF1D9` — newer cream
- `sepia` `#E6D3A7` — manuscript parchment
- `sepiaInk` `#3A2B1A` — bar-leather brown
- `ink` `#1A1410` — saloon shadow
- `inkDeep` `#070605` — true black for the highest-value tiers
- `gold` `#A16207` — engraved brass
- `goldBright` `#C9A961` — polished brass under candlelight
- `goldHot` `#E5C77A` — flame
- `oxblood` `#6B2C2E` — booth leather
- `terracotta` `#B5634A` — wax seal red

**Type:**
- Display: **Fraunces** italic (Cocktail register). 80-130pt for titles.
- Headline serif: **Fraunces** Roman, 28-48pt for benefit lines.
- Eyebrow / tags / monograms: **Inter** 700, 9-13pt, letter-spacing 8-12px, ALL CAPS.
- Numerals / lot numbers / years: **JetBrains Mono** 11-14pt.

**Texture requirements (every card):**
1. SVG paper-noise overlay (`baseFrequency=0.85` fractalNoise, opacity 0.15-0.22, blend mode `multiply`).
2. Radial vignette to corners (rgba(58,43,26,0.35-0.45)).
3. One imperfection per card: cigar burn, wax drip, inkblot, water ring, dog-eared corner. ONE per card. Never more.

**What's banned:**
- Centered-on-flat-color compositions (we did that — they read as price tags, not artifacts).
- Generic "TIER" word stamps.
- Identical layouts across tiers.
- Modern UI elements bleeding into period-set tiers.
- More than one focal object per card.
- Sans-serif anywhere except eyebrows + monograms.

**What every card MUST have:**
- An identifiable physical or digital object (matchbook, key, booklet, plaque, phone). The object IS the design.
- A specific year stamp somewhere small (the patron's year, 1862-1933 for period cards, 2026 for modern Pro cards).
- The Speakeater wordmark — but subtle. Edge of frame, embossed, stamped, etched. Never on a banner.

---

## Tier 01 — $1 The Heads-Up

**The object:** A struck matchbook on a wet bar towel.

**Composition:** Top-down 3/4 angle. Matchbook open in lower-right third. One match struck, charred head pointed left into negative space. Bar towel rumpled, slight water stain in upper-left. Wood grain visible at the bottom edge.

**On the matchbook cover (foiled letterpress effect):**
- "SPEAKEATER" in Inter caps, gold ink, embossed
- Small monogram "S" inside a circle
- Tiny address line: "Knock three times · Past nine"

**On the matchbook interior (the open half):**
- Hand-stamped serif text: "Welcome friend. We saved you a seat."
- Stamp date: "MMXXVI" (Roman 2026) in small mono
- Edge: "Patron № 0001-5000" mono lockup

**Type only on stamped surfaces — no floating type over the scene.**

**Palette:** sepia + sepiaInk + goldBright. Background ink-deep wood. Match flame hint of terracotta.

**One imperfection:** water-ring on the upper-left bar towel.

---

## Tier 02 — $4 One Menu

**The object:** A single Curate-a-Party menu card half-pulled from a leather portfolio.

**Composition:** Diagonal axis. Brown leather portfolio bottom-right, open at a 30° angle. One menu card (use the `curate-menu-card.png` design as the inset — but ROTATED 8° and partially occluded) emerging from the leather. Brass corner clip glints. A single coupe glass empty in the upper-left, the rim catching candlelight.

**Text on the protruding card edge:** "The Bee's Knees Garden Party · Long Island · 1928" — readable as sample type, not the focus.

**Tag at top:** Inter eyebrow "PATRON № 02 · ONE MENU" letterspaced 9px.

**Palette:** oxblood leather + sepia card + goldBright clip + paperBright glass highlight. Background ink.

**One imperfection:** dog-eared upper-right corner on the card.

---

## Tier 03 — $10 Five Menus

**The object:** Five menus dealt across a velvet bar mat like playing cards.

**Composition:** Top-down. Five Curate-a-Party menu cards fanned out at varying angles (slight overlap, 12-18° rotations). Each shows a different title visible at the top edge: "Bee's Knees Garden Party · Speakeasy Opening Night · The Roaring Rooftop · A Gatsby Affair · Bootlegger's Den". A brass jigger laid across the corner of one card. Bar spoon angling out of frame.

**Tag (bottom-right):** Inter eyebrow "FIVE OF FIFTY · TWO DOLLARS A MENU"

**Palette:** sepia cards on oxblood velvet. Brass jigger goldBright with edge glint. Spoon's bowl catching reflection.

**One imperfection:** a cigar ash smudge crossing two card corners.

---

## Tier 04 — $25 Twenty Menus + Beta

**The object:** A phone laying on a stack of menu cards, lit by candle.

**Composition:** 3/4 down-angle. Phone (use `app-recipes.png` as the screen content — the recipe match deck) rests at a slight rotation on top of a tower of 20 menu cards (just show the stack edge — viewer doesn't count, viewer feels). Candle to the right, real flame haze rendered as a soft radial glow. Brass jigger upright behind the candle.

**On-phone screen:** the existing `app-recipes.png` screenshot.

**Tag (top-left, vertical strip):** Inter rotated 90°: "TWENTY MENUS · SIX MONTHS PRO · INTERNAL TESTING"

**Palette:** candlelit. ink-deep base. goldHot accents on phone bezel and jigger. sepia menu stack. Smoke gradient overhead.

**One imperfection:** wax drip running down the side of the candle into a puddle on the menus.

---

## Tier 05 — $30 Pro Founder Year

**The object:** A brass skeleton key on parchment, wax-sealed.

**Composition:** Top-down. Aged parchment fills most of the frame. A solid brass skeleton key in the center-right, casting a hard shadow. A circle of red wax in the upper-left, pressed with an "S" monogram seal (the seal slightly off-center, like it was stamped in haste). A fountain pen resting in the lower-right corner with a small inkblot.

**Stamped text on the parchment (positioned around the key):**
- Top: "FOUNDER · AT THE RATE · FOREVER" in mono
- Below the key: "Thirty dollars a year. Grandfathered for the life of the app. Retail is forty-five." in Fraunces italic
- Lower edge: "Patron 05 · Cap 500" mono

**Palette:** sepia parchment, brass key in goldBright, oxblood + terracotta wax seal. Background ink shadow at the edges (the table the parchment sits on).

**One imperfection:** the inkblot from the pen.

---

## Tier 06 — $49 The Founding Set

**The object:** A leather binder open to reveal all 50 menu cards in miniature.

**Composition:** Top-down 5° angle. Brown leather binder open. Left page: a title plate engraved in goldBright "THE FOUNDING SET · FIFTY MENUS · MMXXVI". Right page: a 5×10 grid of menu thumbnails (each one a tiny 100×140 sepia card with a barely-readable title). Brass spine clasp visible at the binding edge.

**To the right of the binder, sitting on the same table:** a phone displaying the Speakeater app pantry screen (use `app-pantry.png`). The phone is angled, partially in the binder's shadow.

**Tag at the binder's bottom edge:** "$49 · ONE YEAR PRO · ALL FIFTY"

**Palette:** oxblood leather, sepia thumbnail grid, goldBright clasp + engraving. Phone introduces ink-deep modern.

**One imperfection:** a coffee ring on the binder's leather, lower-left.

---

## Tier 07 — $99 Founding Member

**The object:** An engraved brass plaque with the patron's name engraved.

**Composition:** Front-on, slightly tilted forward. Solid brass plaque rectangular, dark patina around the edges, the inscription itself catching candlelight. Engraved deeply (rendered as inset shadow + highlight on the type).

**Engraved text (the plaque IS the design):**
- Top eyebrow: "SPEAKEATER · FOUNDING MEMBER"
- Center: "[ YOUR NAME HERE ]" in Fraunces italic, dramatically large
- Below: "FOUNDER · MMXXVI"
- Bottom eyebrow: "Capped at two hundred and fifty"

> **Tier 07 semantics (post-2026-05-12 clarification):** No lifetime Pro tier exists. The $99 Founding Member tier delivers the same $30/yr Founder Rate as the $30 tier (grandfathered for the operational lifetime of the app), plus the name engraving represented by this plaque, plus a direct email line to Kyle for the first 12 months. The plaque is a prestige artifact, not a lifetime-Pro receipt. Do not add "LIFETIME PATRON" copy to this composition under any reading.

**Mounted on:** dark walnut wood with subtle grain. Below the plaque, a small Edison bulb or candle adds the warm light source for the brass glints.

**Palette:** goldBright + goldHot for the plaque under candlelight. Walnut sepiaInk wood. ink-deep background.

**One imperfection:** the engraving has one letter slightly misaligned, suggesting it was hand-set, not laser-cut.

---

## Tier 08 — $250 The Founding Booklet

**The object:** A perfect-bound sepia booklet, open on a marble bar top, next to a finished cocktail.

**Composition:** 3/4 angle from above. The 60-page booklet open to a sample manuscript page (think the Bee's Knees recipe spread). The spine reads "SPEAKEATER · MMXXVI" stamped in gold. To the right of the booklet, a coupe glass holding a finished cocktail (sepia hue, lemon twist resting on rim, bead of condensation). Marble bar top extends below. A small cigar with a faint ember in an ash tray at the edge.

**Open-spread content visible:**
- Left page: a facsimile of an Ensslin 1917 cocktail entry with the long-s "f"
- Right page: a modernized Bee's Knees recipe in Fraunces

**Tag (overlay, bottom-right, embossed appearance):** "PHYSICAL · MAILED WORLDWIDE · CAPPED AT FIFTY"

**Palette:** sepia booklet, oxblood-tinted marble, goldHot cocktail glass, paperBright manuscript pages. ink for the cigar ash.

**One imperfection:** cigar-burn ring on the corner of the right page.

---

## Tier 09 — $500 Custom Commission

**The object:** A half-typed menu draft on aged hotel stationery, with fountain pen.

**Composition:** Top-down on a writing desk. Sheet of cream stationery letterhead reading "SPEAKEATER · CUSTOM MENU SERVICE · ESTABLISHED MMXXVI" at the top in stamped serif. The body of the page has typewriter text — three drinks named, a fourth half-typed mid-word ("Brandy Cru—"). A black fountain pen with cap off resting at an angle across the page. A coffee cup with a ring stain visible in the upper-right. A typewriter ribbon spool out of frame, hinted at.

**Typed content (legible from medium distance, not the focus):**
- Type at top of page: "Menu for [ Your Event ] — [ Your City ] — [ Your Year ]"
- Three drinks listed by name, mid-typed fourth
- Bottom of page: "Composed by Kyle Schulgen · Huntsville, Alabama · MMXXVI"

**Tag (small wax stamp lower-right):** "CAPPED AT TWENTY · ONE BRIEF · ONE MENU"

**Palette:** paperBright stationery, ink typewriter text, oxblood pen body, sepia coffee ring, gold letterhead engraving.

**One imperfection:** typewriter strikethrough on one earlier word, suggesting a real draft in progress.

---

## Tier 10 — $1,000 Founder Dinner

**The object:** A coupe glass mid-pour, with a laptop open behind it showing the video call.

**Composition:** Foreground center: a coupe glass on a bar, being filled by an arm holding a brass jigger (in motion blur — the pour is happening). Behind it on the bar, a laptop angled toward the foreground showing a Zoom-style call interface with one frame labeled "Kyle · Huntsville" and a silhouette of a person at a kitchen bar. A handwritten menu draft sits on the bar to the right.

**On the laptop screen (rendered as a clean mockup, not a real screenshot):**
- One video tile showing a silhouette
- Bottom bar: "01:00 · One hour · One menu · Two cocktails"

**Tag (overhead, in mono):** "FOUNDER DINNER · CAPPED AT TEN · ONE HOUR LIVE"

**Palette:** ink-deep bar, goldBright glass under bar light, sepia menu draft, soft cool blue from the laptop screen (the only non-period color in the set — intentional, signals "this one is the modern reach").

**One imperfection:** a few drops of cocktail splash on the bar from the pour-in-progress.

---

## Implementation notes for the next-session render

**Composition strategy:** Each tier is its OWN Remotion composition. Don't parameterize one component — the layouts are fundamentally different. Build:

- `TierMatchbook.tsx` — $1
- `TierMenuCardPeek.tsx` — $4
- `TierMenusFanned.tsx` — $10
- `TierPhoneOnStack.tsx` — $25
- `TierBrassKey.tsx` — $30
- `TierBinderGrid.tsx` — $49
- `TierBrassPlaque.tsx` — $99
- `TierBookletAndCocktail.tsx` — $250
- `TierTypedMenu.tsx` — $500
- `TierFounderDinner.tsx` — $1,000

**Asset reuse from existing public/ dir:**
- `app-pantry.png`, `app-recipes.png`, `app-bootlegger.png` → tier 04, tier 06, tier 25 phone screens
- Newly required: marble texture, walnut texture, leather portfolio swatch, wax-seal SVG, fountain-pen SVG, brass-key SVG. Build these as CSS gradients + SVG inline. None require external image assets.

**Render command pattern:**
```bash
for COMP in TierMatchbook TierMenuCardPeek TierMenusFanned TierPhoneOnStack TierBrassKey TierBinderGrid TierBrassPlaque TierBookletAndCocktail TierTypedMenu TierFounderDinner; do
  npx remotion still src/index.ts "$COMP" "out/$(echo $COMP | sed 's/Tier/tier-/' | tr A-Z a-z).png" --overwrite
done
```

**Estimated effort:**
- Per composition: 80-180 lines of TSX. Heavy on CSS gradients + SVG for objects. No external image assets except the 3 app screenshots already in `public/`.
- All 10 from scratch: 4-6 hours of focused design implementation in a fresh context window.

**Quality bar:** if any rendered card could be confused for any other tier's card, it has failed. Each card is an object from a different drawer.

**The thing you are designing toward:** a backer scrolling the campaign sees one card and pauses. Says "wait, that's a brass plaque with my name on it." Backs at $99. Then sees the next card and goes "hold on, that's a physical booklet." Backs up to $250. The cards are the upsell ladder. Each one earns its own pause.

---

## What NOT to do (the mistake from this session)

Do not build a single `TierCard.tsx` with a price/title/lines schema and render it ten times. That's a table, not a portfolio. The thumbnails on Kickstarter's reward column are 200-300px wide — at that size, ten near-identical sepia parchment frames disappear into each other. The viewer's eye glides past. Conversion drops.

Each card must be visually distinct at the thumbnail size. The viewer's eye should be able to identify which tier from 200px away by shape alone — matchbook, menu fan, brass key, leather binder, plaque, booklet, typed page, video call. Color is secondary. SHAPE first.

That's the bar.

# Speakeater Android — Design System

> Source of truth: `C:\Users\12566\projects\speakeater-site\index.html`. When this
> document and the site disagree, the site wins. Update this file in the same PR.

The Android app is moving from a light cream Material 3 palette to the editorial
dark theme established on the marketing site: near-black "paper", warm aged-paper
ink, brass accents, vintage-serif headlines, monospace labels.

This is a phased redesign. Phase 1 (this document, plus `Color.kt`, `Type.kt`,
`Theme.kt`, font certs, build deps) lays the foundation. Phases 2-3 redesign the
individual screens. Bootlegger (vintage Mixology mode) is preserved unchanged.

---

## 1. Color Tokens

All Compose values live in `android/app/src/main/java/app/pantrie/ui/theme/Color.kt`.
Hex values mirror the site's `:root` CSS variables 1:1.

### Surfaces (paper)

| Token  | Compose                  | Hex      | CSS var      | Use                                        |
| ------ | ------------------------ | -------- | ------------ | ------------------------------------------ |
| Paper  | `Color(0xFF020203)`      | #020203  | `--paper`    | App background, scrims                     |
| Paper2 | `Color(0xFF07060A)`      | #07060A  | `--paper-2`  | Cards, nav bar, footer-equivalents         |
| Paper3 | `Color(0xFF0D0A0F)`      | #0D0A0F  | `--paper-3`  | Inset blocks, cocktail cards, pricing card |

### Foreground (ink)

| Token      | Compose                  | Hex / alpha       | CSS var          | Use                                    |
| ---------- | ------------------------ | ----------------- | ---------------- | -------------------------------------- |
| Ink        | `Color(0xFFF4ECD9)`      | #F4ECD9           | `--ink`          | Primary text, hero, headlines          |
| InkSoft    | `Color(0xB8F4ECD9)`      | #F4ECD9 @ 72%     | `--ink-soft`     | Secondary text, body in muted contexts |
| InkFaint   | `Color(0x6BF4ECD9)`      | #F4ECD9 @ 42%     | `--ink-faint`    | Captions, meta, disabled               |
| InkWhisper | `Color(0x2EF4ECD9)`      | #F4ECD9 @ 18%     | `--ink-whisper`  | Borders on interactive chrome (nav CTA) |
| Rule       | `Color(0x1FF4ECD9)`      | #F4ECD9 @ 12%     | `--rule`         | Hairline section dividers              |

### Accents

| Token         | Compose                  | Hex      | CSS var          | Use                                                |
| ------------- | ------------------------ | -------- | ---------------- | -------------------------------------------------- |
| Brass         | `Color(0xFFA16207)`      | #A16207  | `--brass`        | Primary CTA fill, hairline brass rules, dot in mark |
| BrassBright   | `Color(0xFFD4A04A)`      | #D4A04A  | `--brass-bright` | Italic emphasis, active chapter tick, link hover   |
| BrassDeep     | `Color(0xFF5E3A06)`      | #5E3A06  | `--brass-deep`   | Pressed brass, deep accent borders                 |
| Terracotta    | `Color(0xFFB5634A)`      | #B5634A  | `--terracotta`   | Warning, expiring badge, "Pro" recommended ribbon  |
| TerracottaDeep| `Color(0xFF8E4A37)`      | —        | (legacy)         | Pressed terracotta variant                         |
| Olive         | `Color(0xFF6B7148)`      | #6B7148  | `--olive`        | Neutral count badge (shopping list count)          |
| ProGold       | alias of `BrassBright`   | #D4A04A  | —                | Speakeater Pro tier accent                         |

### Legacy semantic aliases (kept so wildcard imports keep compiling)

| Old name | Now points at | Notes                              |
| -------- | ------------- | ---------------------------------- |
| Cream    | `Ink`         | The "primary foreground" semantic  |
| CreamAlt | `InkSoft`     | Soft variant                       |
| Beige    | `InkFaint`    | Faint variant                      |
| InkMuted | `InkFaint`    | (was identical role; collapsed)    |

Phase 2 retires these as each screen is redesigned. Treat new Phase 2 code as if
the legacy names didn't exist — use `Ink`/`InkSoft`/`InkFaint` directly.

### MaterialTheme.colorScheme mapping (dark)

| Slot                  | Token       |
| --------------------- | ----------- |
| primary               | BrassBright |
| onPrimary             | Paper       |
| primaryContainer      | Brass       |
| secondary             | Terracotta  |
| tertiary              | Olive       |
| background            | Paper       |
| onBackground          | Ink         |
| surface               | Paper2      |
| surfaceVariant        | Paper3      |
| outline               | Rule        |
| outlineVariant        | InkWhisper  |
| error                 | Terracotta  |
| errorContainer        | TerracottaDeep |

The app is dark-only. There is no `lightColorScheme`. The brand identity is a
low-light vintage bar room — light mode does not translate.

---

## 2. Typography

All Compose values live in `android/app/src/main/java/app/pantrie/ui/theme/Type.kt`.
Three families fetched at runtime via `androidx.compose.ui.text.googlefonts`:

| Token         | Family             | CSS var        | Use                                              |
| ------------- | ------------------ | -------------- | ------------------------------------------------ |
| SerifDisplay  | Playfair Display   | `--serif`      | Hero, headlines, italic emphasis, big numerals   |
| SerifBody     | Source Serif 4     | `--serif-body` | Body copy, captions, manifesto                   |
| Mono          | JetBrains Mono     | `--mono`       | Labels, chapter marks, CTA chrome, hairline meta |

### Fallback strategy

If the Google Play Services font provider is unavailable (rare — old emulators,
locked-down enterprise devices), each `FontFamily` falls back to platform
`FontFamily.Serif` / `Sans-Serif` / `Monospace` automatically. Cert array is at
`res/values/font_certs.xml`.

### Material 3 Typography mapping

The site uses fluid `clamp()` sizes. We pick a canonical mid value per role.

| Role          | Family       | Size | LH   | Weight   | Letter spacing | Site equivalent                    |
| ------------- | ------------ | ---- | ---- | -------- | -------------- | ---------------------------------- |
| displayLarge  | SerifDisplay | 56sp | 60sp | Normal   | -2.0sp         | h1 / wordmark                      |
| displayMedium | SerifDisplay | 44sp | 48sp | Normal   | -1.4sp         | h2 / cellar count                  |
| displaySmall  | SerifDisplay | 36sp | 42sp | Normal   | -1.0sp         | block__h                           |
| headlineLarge | SerifDisplay | 30sp | 36sp | Normal   | -0.7sp         | walk__step-h, screen titles        |
| headlineMedium| SerifDisplay | 24sp | 30sp | Medium   | -0.5sp         | h3                                 |
| headlineSmall | SerifDisplay | 20sp | 26sp | Medium   | -0.3sp         | walk__phone-card-h                 |
| titleLarge    | SerifDisplay | 18sp | 24sp | Medium   | -0.2sp         | recipe / cocktail name             |
| titleMedium   | SerifBody    | 15sp | 20sp | SemiBold | 0sp            | row primary text                   |
| titleSmall    | SerifBody    | 13sp | 18sp | SemiBold | 0.1sp          | dense list rows                    |
| bodyLarge     | SerifBody    | 16sp | 26sp | Normal   | 0.1sp          | manifesto body, block__body        |
| bodyMedium    | SerifBody    | 14sp | 22sp | Normal   | 0.15sp         | secondary body                     |
| bodySmall     | SerifBody    | 12sp | 18sp | Normal   | 0.2sp          | captions                           |
| labelLarge    | Mono         | 12sp | 16sp | Medium   | 2.5sp          | btn-primary, btn-ghost CTA chrome  |
| labelMedium   | Mono         | 11sp | 14sp | Medium   | 2.0sp          | block__cap, nav__links             |
| labelSmall    | Mono         | 10sp | 14sp | Medium   | 3.2sp          | hero__pron, foot__bottom           |

### Italic emphasis convention

The site reserves italic Playfair Display in `BrassBright` for the rhetorical
emphasis word inside a headline (`<em>`). Mirror this in Compose:

```kotlin
Text(
  buildAnnotatedString {
    append("Photograph the shelf. ")
    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = BrassBright)) {
      append("That's the work.")
    }
  },
  style = MaterialTheme.typography.displaySmall,
)
```

---

## 3. Spacing

The site uses `--gutter: clamp(20px, 4vw, 56px)` and `--max: 1320px`. Android
phones are narrow, so we collapse to a fixed scale:

| Token        | dp     | Use                                                  |
| ------------ | ------ | ---------------------------------------------------- |
| GutterTight  | 16.dp  | Phone screens, tight rows                            |
| Gutter       | 20.dp  | Default screen horizontal padding                    |
| GutterWide   | 28.dp  | Hero / chapter sections                              |
| Section      | 64.dp  | Vertical between major sections                      |
| SectionLg    | 96.dp  | Vertical breathing room between editorial blocks    |

(These tokens are recommended for Phase 2 — not yet codified in `Color.kt` /
`Type.kt`. Add them as `Spacing.kt` when the first redesigned screen needs them.)

### Card / row rules
- Cards: `Paper3` background, `Brass @ 18%` border (1.dp), 0.dp corner radius
  (the site uses sharp corners — editorial, not Material rounded).
- List rows: `Paper2` with `Rule` 1.dp bottom border, 14.dp vertical padding.
- Buttons primary: `Brass` fill, `Paper` text, Mono labelLarge, 18×30.dp padding,
  0.dp corner radius, no elevation.
- Buttons ghost: transparent, `Ink` text, `InkWhisper` 1.dp border, hovers to
  `Brass` border + `Brass @ 8%` fill.
- Hairline rules: 1.dp `Rule`. The 32-dp brass tick (`cellar__card-rule`) is
  1.dp `Brass` for accent dividers between meta and authorship.

---

## 4. Component-level rules

### Nav (top app bar)
- Background: `Paper @ 85%` with `blur(8.dp)` (use `Modifier.background` plus a
  scrim — full blur isn't trivial on Android, accept as `Paper @ 94%` solid for v1).
- 22.dp vertical padding default; 14.dp when collapsed/scrolled.
- Title: SerifDisplay, 22.sp, `-0.03em` tracking, brass dot for the period
  (apply `withStyle(color = Brass)` on the trailing `.`).
- Links + CTA: Mono labelMedium @ `InkSoft`, hover `BrassBright`.

### Bottom nav (existing, keep behavior)
- Background: `Paper2` for non-Mixology, the existing `darkBg #0D0D0E` for Mixology.
- Selected icon/text: `Ink` (was `MaterialTheme.colorScheme.onSurface`).
- Unselected: `InkFaint`.
- Shopping badge: `Olive` neutral, `Terracotta` when expiring items present.

### Chapter mark (section header pattern)
```
[01]  ──  PANTRY                                              N°  01
```
- Number: SerifDisplay 28-32.sp italic `BrassBright`.
- Title: Mono labelMedium `Ink`.
- Right slug: Mono labelSmall `InkFaint`.
- 1.dp `Rule` underline, 28.dp padding above, 18.dp below, 60-100.dp gap to body.

### Cocktail / recipe card
- 3:4.4 aspect ratio (matches site cellar cards).
- `Paper3` background with image overlay at 18% opacity, sepia-filtered.
- Top-left: `№ 01` style label + year in BrassBright SerifDisplay 48-72.sp.
- Bottom: 32.dp brass rule, author italic, title body, meta row in Mono.

### Pricing / paywall card
- `Paper2` background, `Rule` 1.dp border. Pro card: `Brass @ 42%` border with
  a tiny `Brass` ribbon top-right reading "Recommended" (Mono 9.sp letter-spacing 0.24em).

---

## 5. Motion

The site uses GSAP + ScrollTrigger + Lenis for smooth scroll. We use Compose's
built-ins (no extra libs):

| Site behavior                          | Compose equivalent                                   |
| -------------------------------------- | ---------------------------------------------------- |
| Door letters breathe (4.6s)            | `rememberInfiniteTransition` + `animateColor`        |
| Scroll-driven cross-fade between rooms | `AnimatedContent` with `fadeIn() togetherWith fadeOut()` |
| Sticky horizontal cellar scroll        | `LazyRow` with `snapFlingBehavior`                   |
| Hover color shift (link → brass)       | `animateColorAsState`                                |
| Section reveal on scroll               | `AnimatedVisibility` triggered by `LazyListState.firstVisibleItemIndex` |
| Default easing                         | `tween(durationMillis = 400, easing = EaseInOut)`    |
| Springy interactive bounce             | `spring(stiffness = StiffnessMediumLow)`             |

Default page transition: 320ms cross-fade. No slide. The site's grammar is
"a curtain lifts," not "a card slides in."

---

## 6. Voice / tone

Site copy patterns to mirror in app strings:

- **Section labels**: numeric + name. `"01 / Pantry"`, `"04 / Cellar · Mixology"`.
- **Captions** above headlines: short Mono in BrassBright. `"A note from the founder"`,
  `"Two ways in"`.
- **Headlines** end with a period, then italic emphasis: `Photograph the shelf. That's the work.`
- **Italic em-phrase** is always inside the headline, never a banner. Brass-bright color.
- **CTAs are verbs**: `Open a tab`, `Begin the menu`, `Start cooking`, `Pour one`.
- **Numerals are featured**: `4,037 drinks`, `1882`, `№ 03` — large SerifDisplay,
  italic when they're the rhetorical center.
- **Banned tells**: em-dashes (per the user's writing-voice rules — use periods,
  commas, colons), bold mid-sentence, list-of-specs marketing copy ("✓ unlimited
  X"), AI-tells like "leverage", "delve", "tapestry".
- **Manuscript references**: name the bartender + year. `Jerry Thomas, 1862`.

---

## 7. Per-screen redesign target table

Phase 1 = no screen-level edits. This table tells Phase 2 where to start.

| Screen                                          | New look                                                                 |
| ----------------------------------------------- | ------------------------------------------------------------------------ |
| `feature/auth/LoginScreen.kt`                   | Hero wordmark on `Paper`, brass dot in mark, Mono CTA buttons            |
| `feature/onboarding/OnboardingScreen.kt`        | Chapter-marked steps, SerifDisplay headlines, italic brass emphasis      |
| `feature/deck/DeckScreen.kt` (Tonight)          | Editorial card stack, recipe name in titleLarge serif, Mono meta row     |
| `feature/pantry/PantryScreen.kt`                | Dark `Paper` surface, photographs gain weight, section dividers in Rule  |
| `feature/shopping/ShoppingScreen.kt`            | Mono aisle labels, serif item names, BrassBright check state             |
| `feature/saved/SavedScreen.kt` (Cookbook)       | Manuscript-style cards, year/source badge, brass hairlines               |
| `feature/plan/PlanScreen.kt`                    | Week grid as menu card, italic emphasis on day-of-the-week               |
| `feature/settings/SettingsScreen.kt`            | Pricing-card-style row groups, Mono section heads, ghost CTA rows        |
| `billing/PaywallScreen.kt`                      | Tasting-menu pricing layout, "Recommended" brass ribbon                  |
| `feature/mixology/MixologyScreen.kt` (MIXOLOGIST)| Same dark editorial palette, BrassBright accents                         |
| `feature/mixology/MixologyScreen.kt` (BOOTLEGGER)| **UNCHANGED** — preserves vintage parchment / dark-wood sepia mode      |
| `feature/search/SearchSheet.kt`                 | Dark Paper2 sheet, Mono search field placeholder, serif results          |
| `feature/recipe/RecipeDetailScreen.kt`          | Editorial article layout, manifesto-style dropcap on description         |
| `feature/cook/CookModeScreen.kt`                | Single-step focus, large SerifDisplay step number, Mono ingredient row   |
| `feature/scan/ScanScreen.kt`                    | Dark camera UI, brass viewfinder corners, Mono mode label                |
| `feature/barcode/BarcodeScreen.kt`              | Same as scan — minimal chrome, brass scanline                            |
| `feature/beta/CommunityScreen.kt` (Feed)        | Editorial post cards, italic author name, Mono timestamp                 |
| `feature/beta/BetaFeedbackSheet.kt`             | Dark Paper2 sheet, Mono labels, ghost CTA                                |
| `feature/submit/SubmitRecipeScreen.kt`          | Manuscript form layout, serif body inputs, Mono field labels             |
| `feature/submit/MySubmissionsScreen.kt`         | Manuscript card list                                                     |
| `feature/mealprep/MealPrepScreen.kt`            | Plan-style menu layout                                                   |
| `MainActivity.kt` PantrieNav (chrome)           | Bottom nav switches `surface` → `Paper2`, retains Mixology dark override |

### Bootlegger boundary (do not touch)

`feature/mixology/MixologyScreen.kt` defines `vintageMode: Boolean` state. When
`vintageMode == true`, the screen overrides every theme color with private
`Sepia (#E6D3A7)` and `SepiaInk (#3A2B1A)` constants defined at lines 75-76 of
that file. The new dark editorial theme applies ONLY to the modern half
(`vintageMode == false`). All Bootlegger code paths read `Sepia` / `SepiaInk`
directly and are unaffected by Color.kt / Theme.kt changes.

---

## 8. Phase status

- Phase 1 — DONE: tokens, typography, theme wiring, font certs, build config.
- Phase 2 — TODO: per-screen redesign (start with LoginScreen + DeckScreen for
  first-launch impact, then Mixology MIXOLOGIST, then PaywallScreen).
- Phase 3 — TODO: animation polish, AnimatedContent transitions, scrim layering,
  any custom drawables needed for door / chapter marks.

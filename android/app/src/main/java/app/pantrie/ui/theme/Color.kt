package app.pantrie.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Speakeater editorial palette — sourced 1:1 from speakeater-site/index.html `:root`
 * CSS variables. The site is the canonical design spec; these constants mirror it so
 * any divergence between web and Android is caught at code review time.
 *
 * NAMING STABILITY: the legacy cream-palette names (Cream, Paper, Beige, Ink, etc.)
 * are intentionally preserved as identifiers so the 19 feature screens that wildcard-
 * import this package keep compiling. Their VALUES have been re-mapped to the dark
 * editorial palette — what was once "Cream" (#F5EFE4) is now the warm off-white ink
 * (#F4ECD9) and serves the same semantic role (primary foreground on dark).
 *
 * Phase 2 will rename + retire these legacy aliases screen-by-screen as those
 * screens are redesigned. Until then, treat the legacy names as semantic slots:
 *   Cream / Paper      → primary foreground ink (warm off-white)
 *   CreamAlt / Beige   → soft / faint variants of the foreground
 *   Ink                → primary background (near-black "paper")
 *   InkSoft            → secondary text (foreground @ 72%)
 *   InkMuted/InkFaint  → tertiary / disabled text
 *   Terracotta / Olive → accent + caution colors (kept on the dark side)
 *
 * Bootlegger (vintage Mixology mode) defines its own Sepia/SepiaInk privately inside
 * MixologyScreen.kt and is intentionally NOT touched here.
 */

// --- Speakeater editorial dark palette -------------------------------------------

/** Near-black "paper" background. Site: --paper #020203 */
val Paper = Color(0xFF020203)

/** Slightly lifted paper for cards / nav surfaces. Site: --paper-2 #07060A */
val Paper2 = Color(0xFF07060A)

/** Tertiary paper for inset blocks / cocktail cards. Site: --paper-3 #0D0A0F */
val Paper3 = Color(0xFF0D0A0F)

/** Primary foreground — warm aged-paper off-white. Site: --ink #F4ECD9 */
val Ink = Color(0xFFF4ECD9)

/** Secondary text — ink @ 72% on dark. Site: --ink-soft */
val InkSoft = Color(0xB8F4ECD9)

/** Tertiary text — ink @ 42% on dark. Site: --ink-faint */
val InkFaint = Color(0x6BF4ECD9)

/** Whisper-thin text / hairline rules — ink @ 18% on dark. Site: --ink-whisper */
val InkWhisper = Color(0x2EF4ECD9)

/** Hairline divider rule — ink @ 12% on dark. Site: --rule */
val Rule = Color(0x1FF4ECD9)

/** Brass — primary accent. Site: --brass #A16207 */
val Brass = Color(0xFFA16207)

/** Brass bright — italic-emphasis + active state accent. Site: --brass-bright #D4A04A */
val BrassBright = Color(0xFFD4A04A)

/** Brass deep — pressed / hover-burn accent. Site: --brass-deep #5E3A06 */
val BrassDeep = Color(0xFF5E3A06)

/** Terracotta — warning / Pro badge / shopping-needs-attention. Site: --terracotta #B5634A */
val Terracotta = Color(0xFFB5634A)

/** Terracotta deep — pressed terracotta variant. Pre-existing app token. */
val TerracottaDeep = Color(0xFF8E4A37)

/** Olive — neutral count badge / secondary accent. Site: --olive #6B7148 */
val Olive = Color(0xFF6B7148)

// --- Legacy semantic aliases (kept for wildcard imports across feature screens) --

/** @deprecated semantic alias — use Ink instead. Maps to the warm off-white foreground. */
val Cream = Ink

/** @deprecated semantic alias — use InkSoft instead. */
val CreamAlt = InkSoft

/** @deprecated semantic alias — use InkFaint instead. */
val Beige = InkFaint

/** @deprecated semantic alias — use InkFaint instead. */
val InkMuted = InkFaint

// --- Pro / billing accent ---------------------------------------------------------

/** Pro tier accent. Aliased to BrassBright — Pro = "the brass tier" in editorial language. */
val ProGold = BrassBright

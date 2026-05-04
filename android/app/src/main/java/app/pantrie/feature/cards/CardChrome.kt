package app.pantrie.feature.cards

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Single source of truth for card-surface theming. Three variants today:
 *   FOOD              — cream paper, brass + olive accents, sans-serif
 *   MIXOLOGY_MODERN   — speakeasy black, gold accent, sans-serif
 *   MIXOLOGY_VINTAGE  — sepia parchment, terracotta accent, serif
 *
 * Drives EVERY shared chrome piece (banner / overlay / match badge / social
 * footer) so all three card types render the same furniture in their own
 * palette. Card-specific BODY layouts (food's ingredient chips, mixology's
 * pager, vintage's full-text recipe) stay in their own files.
 */
data class CardPalette(
  val surface: Color,
  val ink: Color,
  val inkSoft: Color,
  val inkFaint: Color,
  val accent: Color,
  val accentSoft: Color,
  val saveColor: Color,        // SAVE overlay + save-icon active state
  val skipColor: Color,        // SKIP overlay
  val matchHi: Color,          // 80%+ match
  val matchMid: Color,         // 50-79%
  val titleFont: FontFamily,
  val bodyFont: FontFamily,
  val elevation: Dp,
)

// Speakeasy palette tokens — duplicate of theme tokens to keep this file
// dependency-free. If theme.kt evolves, mirror the value here.
private val Paper       = Color(0xFFF5EFE4)
private val Ink         = Color(0xFF2B2621)
private val InkSoft     = Color(0xFF5A5148)
private val InkFaint    = Color(0xFFB7AE9E)
private val Brass       = Color(0xFFC9A554)
private val BrassBright = Color(0xFFD4A04A)
private val Terracotta  = Color(0xFFB5634A)
private val Olive       = Color(0xFF7A8664)

// Mixology modern (black + gold)
private val ModernBg        = Color(0xFF0D0D0E)
private val ModernCard      = Color(0xFF18181B)
private val ModernInk       = Color(0xFFE8E3D9)
private val ModernGold      = Color(0xFFC9A554)
private val ModernInkMuted  = Color(0xFF8B8578)

// Vintage parchment
private val Sepia    = Color(0xFFE6D3A7)
private val SepiaInk = Color(0xFF3A2B1A)

val FOOD_PALETTE = CardPalette(
  surface = Paper,
  ink = Ink,
  inkSoft = InkSoft,
  inkFaint = InkFaint,
  accent = BrassBright,
  accentSoft = Brass,
  saveColor = Olive,
  skipColor = Terracotta,
  matchHi = Olive,
  matchMid = BrassBright,
  titleFont = FontFamily.Default,
  bodyFont = FontFamily.Default,
  elevation = 6.dp,
)

val MIXOLOGY_MODERN_PALETTE = CardPalette(
  surface = ModernCard,
  ink = ModernInk,
  inkSoft = ModernInkMuted,
  inkFaint = Color(0xFF4A453F),
  accent = ModernGold,
  accentSoft = Color(0xFF8B7B45),
  saveColor = ModernGold,
  skipColor = Terracotta,
  matchHi = Olive,
  matchMid = Color(0xFFD4A017),
  titleFont = FontFamily.Default,
  bodyFont = FontFamily.Default,
  elevation = 6.dp,
)

val MIXOLOGY_VINTAGE_PALETTE = CardPalette(
  surface = Sepia,
  ink = SepiaInk,
  inkSoft = SepiaInk.copy(alpha = 0.65f),
  inkFaint = SepiaInk.copy(alpha = 0.35f),
  accent = Terracotta,
  accentSoft = Color(0xFF8B1E1E),
  saveColor = SepiaInk,
  skipColor = Terracotta,
  matchHi = Olive,
  matchMid = Color(0xFFB58A3C),
  titleFont = FontFamily.Serif,
  bodyFont = FontFamily.Serif,
  elevation = 10.dp,
)

/** Pick the right palette for a recipe + mode. */
fun paletteFor(contentType: String?, vintageMode: Boolean = false): CardPalette = when {
  vintageMode -> MIXOLOGY_VINTAGE_PALETTE
  contentType == "cocktail" -> MIXOLOGY_MODERN_PALETTE
  else -> FOOD_PALETTE
}

/**
 * Edge-to-edge allergen banner. Tri-state, server-driven. Sits at the very
 * top of the card so the user sees it before any content. Uses palette-aware
 * colors so the same banner looks intentional on cream, black, AND sepia.
 *
 *   "red"    → one matched allergen has no real sub → solid red, white text
 *   "yellow" → all matched allergens have a sub on file → palette accent, ink text
 *   else     → no banner
 */
@Composable
fun CardAllergenBanner(status: String, labels: List<String>, palette: CardPalette) {
  if (status != "red" && status != "yellow") return
  val isRed = status == "red"
  val red = Color(0xFFC54B3C)
  val bg = if (isRed) red else palette.accent
  val fg = if (isRed) Color.White else palette.ink
  val labelText = labels.joinToString(" · ") { it.uppercase() }
  val prefix = if (isRed) "Allergen, no swap" else "Allergen, sub available"
  // Report this banner's bounds to the walkthrough anchor registry so the
  // allergen mini-tour can spotlight the actual banner the user just learned
  // to read. Looked up via a Hilt-scoped VM; outside a tour the registry
  // simply records and ignores the bounds.
  val tourVm: app.pantrie.feature.walkthrough.WalkthroughViewModel =
    androidx.hilt.navigation.compose.hiltViewModel()
  Surface(
    color = bg,
    modifier = Modifier
      .fillMaxWidth()
      .onGloballyPositioned { coords ->
        tourVm.reportAnchor(
          app.pantrie.feature.walkthrough.TourAnchors.DECK_ALLERGEN_BANNER,
          coords.boundsInWindow(),
        )
      },
  ) {
    Row(
      Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Outlined.Warning,
        contentDescription = null,
        tint = fg,
        modifier = Modifier.size(16.dp),
      )
      Spacer(Modifier.width(10.dp))
      Text(
        if (labelText.isNotBlank()) "$prefix · $labelText" else prefix,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = fg,
        letterSpacing = 1.0.sp,
        maxLines = 2,
      )
    }
  }
}

/**
 * SAVE / SKIP overlays — the rotated bordered text that fades in as the user
 * drags. Standardized across all card types so the gesture feel is identical.
 * Caller passes the BoxScope it lives in; we align ourselves.
 */
@Composable
fun BoxScope.SwipeOverlay(saveProgress: Float, skipProgress: Float, palette: CardPalette) {
  if (saveProgress > 0.1f) {
    Text(
      "SAVE",
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(24.dp)
        .rotate(-12f)
        .border(3.dp, palette.saveColor, RoundedCornerShape(4.dp))
        .padding(horizontal = 10.dp, vertical = 4.dp)
        .alpha(saveProgress),
      color = palette.saveColor,
      fontWeight = FontWeight.ExtraBold,
      style = MaterialTheme.typography.headlineSmall,
    )
  }
  if (skipProgress > 0.1f) {
    Text(
      "SKIP",
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(24.dp)
        .rotate(12f)
        .border(3.dp, palette.skipColor, RoundedCornerShape(4.dp))
        .padding(horizontal = 10.dp, vertical = 4.dp)
        .alpha(skipProgress),
      color = palette.skipColor,
      fontWeight = FontWeight.ExtraBold,
      style = MaterialTheme.typography.headlineSmall,
    )
  }
}

/** Pantry-match % badge — same shape across all cards, palette-tinted bg. */
@Composable
fun MatchPercentBadge(percent: Int, palette: CardPalette) {
  val bg = when {
    percent >= 80 -> palette.matchHi
    percent >= 50 -> palette.matchMid
    percent > 0 -> palette.ink.copy(alpha = 0.45f)
    else -> palette.inkFaint
  }
  Surface(color = bg, shape = RoundedCornerShape(4.dp)) {
    Text(
      "$percent%",
      color = Color.White,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    )
  }
}

/** Generic palette-aware pill (cuisine eyebrow, expiring, ABV, glass type). */
@Composable
fun ChromeBadge(text: String, palette: CardPalette, bg: Color = palette.ink.copy(alpha = 0.55f)) {
  Surface(color = bg, shape = RoundedCornerShape(4.dp)) {
    Text(
      text,
      color = Color.White,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    )
  }
}

/**
 * Shared social footer — Pan (left) · optional center hint · Save (right).
 * Used on both food cards (where center is empty) and mixology cards (where
 * center is "flip for the story →"). Matching the icon, count format, and
 * spacing makes the feet of every card type read as the same product.
 */
@Composable
fun CardSocialFooter(
  panCount: Int,
  saveCount: Int,
  panned: Boolean,
  saved: Boolean,
  palette: CardPalette,
  onPan: () -> Unit,
  onSave: () -> Unit,
  centerHint: String? = null,
  onCenterTap: (() -> Unit)? = null,
) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    SocialIconStack(
      icon = if (panned) Icons.Filled.Favorite else Icons.Outlined.Favorite,
      count = panCount,
      tint = if (panned) palette.skipColor else palette.accent,
      countColor = palette.accent,
      countFont = palette.bodyFont,
      onClick = onPan,
      contentDesc = "Pan it",
    )
    Spacer(Modifier.weight(1f))
    if (centerHint != null) {
      val source = remember { MutableInteractionSource() }
      val clickMod = if (onCenterTap != null) {
        Modifier.clickable(interactionSource = source, indication = null, onClick = onCenterTap)
      } else Modifier
      Text(
        centerHint,
        style = MaterialTheme.typography.labelMedium,
        color = palette.inkSoft,
        fontFamily = palette.titleFont,
        modifier = Modifier.padding(horizontal = 8.dp).then(clickMod),
      )
      Spacer(Modifier.weight(1f))
    }
    SocialIconStack(
      icon = if (saved) Icons.Filled.Bookmark else Icons.Outlined.Bookmark,
      count = saveCount,
      tint = palette.accent,
      countColor = palette.accent,
      countFont = palette.bodyFont,
      onClick = onSave,
      contentDesc = "Save",
    )
  }
}

@Composable
private fun SocialIconStack(
  icon: ImageVector,
  count: Int,
  tint: Color,
  countColor: Color,
  countFont: FontFamily,
  onClick: () -> Unit,
  contentDesc: String,
) {
  val source = remember { MutableInteractionSource() }
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.clickable(interactionSource = source, indication = null, onClick = onClick),
  ) {
    Icon(icon, contentDescription = contentDesc, tint = tint, modifier = Modifier.size(28.dp))
    Spacer(Modifier.height(2.dp))
    Text(
      formatSocialCount(count),
      style = MaterialTheme.typography.labelSmall,
      color = countColor,
      fontFamily = countFont,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

/** TikTok-ish 1.2k / 4.5M number formatting — same on every card type. */
fun formatSocialCount(n: Int): String = when {
  n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
  n >= 1_000 -> "%.1fk".format(n / 1_000.0)
  else -> n.toString()
}

/**
 * Cocktail era eyebrow — small uppercase strip above the title that tells the
 * user what they're looking at. Mixology cards before this were unmarked, so a
 * Boothby 1908 cocktail and a 2024 craft cocktail looked identical until you
 * read the year tucked at the bottom.
 *
 *   isHistoric == true  → "BOOTLEGGER · 1908" (or whatever sourceYear is)
 *   isHistoric == false → "MODERN MIXOLOGY"
 *
 * Renders nothing for food recipes (the call site gates by content_type).
 */
@Composable
fun CocktailEraEyebrow(
  isHistoric: Boolean,
  sourceYear: Int?,
  palette: CardPalette,
) {
  val label = if (isHistoric) {
    if (sourceYear != null && sourceYear in 1700..1929) "BOOTLEGGER · $sourceYear"
    else "BOOTLEGGER"
  } else {
    "MODERN MIXOLOGY"
  }
  Text(
    label,
    style = MaterialTheme.typography.labelSmall,
    fontWeight = FontWeight.Bold,
    color = palette.accent,
    letterSpacing = 2.0.sp,
    fontFamily = palette.titleFont,
  )
}

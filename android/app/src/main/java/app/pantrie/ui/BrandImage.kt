package app.pantrie.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantrie.ui.theme.BrassBright
import app.pantrie.ui.theme.InkFaint
import app.pantrie.ui.theme.Paper2
import app.pantrie.ui.theme.Rule

/**
 * Render an ingredient as a photorealistic image when available, falling back to a minimal
 * brass-monochrome placeholder.
 *
 * Resolution order:
 *  1. [IngredientImage.forName] — looks up `R.drawable.brimm_<slug>` via runtime getIdentifier.
 *  2. Brass dot placeholder — compact, on-brand, no emoji noise.
 *
 * The PNG path is tinted-friendly (square crop) — callers should treat [size] as the bounding box.
 *
 * Phase 4 cleanup: the legacy `IngredientEmoji` helper has been deleted entirely. Callers
 * with the `emojiStyle` / `emojiFontSize` params should drop those args next time they're
 * touched — they're kept @Suppress'd here to avoid a churn-only change to every call site.
 */
@Composable
fun IngredientImageOrEmoji(
  name: String,
  size: Dp = 28.dp,
  modifier: Modifier = Modifier,
  @Suppress("UNUSED_PARAMETER") emojiStyle: TextStyle = MaterialTheme.typography.titleLarge,
  @Suppress("UNUSED_PARAMETER") emojiFontSize: TextUnit = TextUnit.Unspecified,
) {
  val context = LocalContext.current
  // Resolve once per (name, context) — getIdentifier is cheap but the regex walk isn't free.
  val resId = remember(name) { IngredientImage.forName(context, name) }
  if (resId != 0) {
    Image(
      painter = painterResource(id = resId),
      contentDescription = name,
      contentScale = ContentScale.Crop,
      modifier = modifier
        .size(size)
        .clip(RoundedCornerShape(6.dp)),
    )
  } else {
    // Compact brass dot — small visual anchor that reads as "ingredient" without an emoji.
    BrassDotPlaceholder(size = size, modifier = modifier)
  }
}

/** Cuisine variant — uses HERO crops, falls back to a lettered outlined placeholder. */
@Composable
fun CuisineImageOrGlyph(
  cuisine: String?,
  @Suppress("UNUSED_PARAMETER") fallbackGlyph: String = "",
  size: Dp = 44.dp,
  modifier: Modifier = Modifier,
  @Suppress("UNUSED_PARAMETER") emojiFontSize: TextUnit = 32.sp,
) {
  val context = LocalContext.current
  val resId = remember(cuisine) { IngredientImage.cuisineImage(context, cuisine) }
  if (resId != 0) {
    Image(
      painter = painterResource(id = resId),
      contentDescription = cuisine,
      contentScale = ContentScale.Crop,
      modifier = modifier
        .size(size)
        .clip(RoundedCornerShape(8.dp)),
    )
  } else {
    LetterPlaceholder(label = cuisine, size = size, modifier = modifier)
  }
}

/** Glass variant — used by Mixology recipe header + search placeholders. */
@Composable
fun GlassImageOrGlyph(
  glassType: String?,
  @Suppress("UNUSED_PARAMETER") fallbackGlyph: String = "",
  size: Dp = 32.dp,
  modifier: Modifier = Modifier,
  @Suppress("UNUSED_PARAMETER") emojiFontSize: TextUnit = 28.sp,
) {
  val context = LocalContext.current
  val resId = remember(glassType) { IngredientImage.glassImage(context, glassType) }
  if (resId != 0) {
    Image(
      painter = painterResource(id = resId),
      contentDescription = glassType ?: "glass",
      contentScale = ContentScale.Crop,
      modifier = modifier
        .size(size)
        .clip(RoundedCornerShape(6.dp)),
    )
  } else {
    LetterPlaceholder(label = glassType, size = size, modifier = modifier)
  }
}

/** Aisle variant — Shopping list section headers. */
@Composable
fun AisleImageOrGlyph(
  aisleKey: String,
  @Suppress("UNUSED_PARAMETER") fallbackGlyph: String = "",
  size: Dp = 24.dp,
  modifier: Modifier = Modifier,
  @Suppress("UNUSED_PARAMETER") emojiFontSize: TextUnit = 20.sp,
) {
  val context = LocalContext.current
  val resId = remember(aisleKey) { IngredientImage.aisleImage(context, aisleKey) }
  if (resId != 0) {
    Image(
      painter = painterResource(id = resId),
      contentDescription = aisleKey,
      contentScale = ContentScale.Crop,
      modifier = modifier
        .size(size)
        .clip(RoundedCornerShape(4.dp)),
    )
  } else {
    LetterPlaceholder(label = aisleKey, size = size, modifier = modifier)
  }
}

/**
 * Compact brass-coloured dot placeholder. Used for ingredient row icons where space is at
 * a premium and the row already has a label. Centered inside a [size] bounding box to keep
 * vertical alignment consistent with a real PNG.
 */
@Composable
private fun BrassDotPlaceholder(size: Dp, modifier: Modifier = Modifier) {
  val dotSize = if (size <= 20.dp) 6.dp else 8.dp
  Box(
    modifier = modifier.size(size),
    contentAlignment = Alignment.Center,
  ) {
    Box(Modifier.size(dotSize).background(BrassBright, CircleShape))
  }
}

/**
 * Outlined square containing the first letter of [label] in mono-style serif. Used for
 * cuisine / glass / aisle headers — informative but quiet, no emoji.
 */
@Composable
private fun LetterPlaceholder(label: String?, size: Dp, modifier: Modifier = Modifier) {
  val ch = label?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "·"
  val fontSizeSp = (size.value * 0.45f).sp
  Box(
    modifier = modifier
      .size(size)
      .clip(RoundedCornerShape(6.dp))
      .background(Paper2)
      .border(1.dp, Rule, RoundedCornerShape(6.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      ch,
      color = InkFaint,
      fontSize = fontSizeSp,
      fontWeight = FontWeight.SemiBold,
      fontFamily = FontFamily.Serif,
    )
  }
}

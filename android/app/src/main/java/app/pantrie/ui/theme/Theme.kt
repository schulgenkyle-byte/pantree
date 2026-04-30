package app.pantrie.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Speakeater theme entry point. Mirrors the editorial dark palette + serif-stack
 * typography defined by speakeater-site/index.html.
 *
 * Currently dark-only. The site has no light mode and the brand identity (vintage
 * speakeasy, low-light bar room) doesn't translate to light surfaces, so the
 * `darkTheme` parameter is intentionally absent until/unless we ever ship one.
 *
 * Color tokens live in Color.kt. Typography lives in Type.kt. This file is the wiring.
 *
 * Bootlegger mode in MixologyScreen.kt overrides locally with its own Sepia/SepiaInk —
 * MaterialTheme references inside those composables still pick up these values, so the
 * Bootlegger code paths actively force-override per-component. See the Phase 1 brief.
 */

private val SpeakeaterDarkColors = darkColorScheme(
  primary = BrassBright,
  onPrimary = Paper,
  primaryContainer = Brass,
  onPrimaryContainer = Ink,
  secondary = Terracotta,
  onSecondary = Ink,
  secondaryContainer = BrassDeep,
  onSecondaryContainer = Ink,
  tertiary = Olive,
  onTertiary = Ink,
  background = Paper,
  onBackground = Ink,
  surface = Paper2,
  onSurface = Ink,
  surfaceVariant = Paper3,
  onSurfaceVariant = InkSoft,
  surfaceTint = Brass,
  inverseSurface = Ink,
  inverseOnSurface = Paper,
  outline = Rule,
  outlineVariant = InkWhisper,
  error = Terracotta,
  onError = Paper,
  errorContainer = TerracottaDeep,
  onErrorContainer = Ink,
  scrim = Paper,
)

@Composable
fun PantrieTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = SpeakeaterDarkColors,
    typography = SpeakeaterTypography,
    content = content,
  )
}

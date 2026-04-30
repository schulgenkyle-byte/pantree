package app.pantrie.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import app.pantrie.R

/**
 * Typography stack for the Speakeater editorial theme.
 *
 * Mirrors the site's three-family hierarchy:
 *   --serif       Playfair Display    headlines, hero, italic emphasis
 *   --serif-body  Source Serif 4      long-form body, captions
 *   --mono        JetBrains Mono      labels, chapter marks, CTA chrome
 *
 * Fonts are pulled at runtime via androidx.compose.ui.text.googlefonts so we don't
 * ship 4-weight families inside the APK. If the Google Play Services font provider
 * is unavailable (rare — emulators without GMS, locked-down devices), each FontFamily
 * falls back via its `fallback` parameter to the platform serif/sans-serif/monospace
 * so text always renders. Cert array lives at res/values/font_certs.xml.
 */

private val provider = GoogleFont.Provider(
  providerAuthority = "com.google.android.gms.fonts",
  providerPackage = "com.google.android.gms",
  certificates = R.array.com_google_android_gms_fonts_certs,
)

private val playfairDisplay = GoogleFont("Playfair Display")
private val sourceSerif4 = GoogleFont("Source Serif 4")
private val jetBrainsMono = GoogleFont("JetBrains Mono")

/** Serif display family — Playfair Display. Headlines, hero, italic emphasis. */
val SerifDisplay = FontFamily(
  Font(googleFont = playfairDisplay, fontProvider = provider, weight = FontWeight.Normal),
  Font(googleFont = playfairDisplay, fontProvider = provider, weight = FontWeight.Normal, style = FontStyle.Italic),
  Font(googleFont = playfairDisplay, fontProvider = provider, weight = FontWeight.Medium),
  Font(googleFont = playfairDisplay, fontProvider = provider, weight = FontWeight.SemiBold),
  Font(googleFont = playfairDisplay, fontProvider = provider, weight = FontWeight.Bold),
  Font(googleFont = playfairDisplay, fontProvider = provider, weight = FontWeight.Bold, style = FontStyle.Italic),
)

/** Serif body family — Source Serif 4. Long-form copy, captions, manifesto body. */
val SerifBody = FontFamily(
  Font(googleFont = sourceSerif4, fontProvider = provider, weight = FontWeight.Light),
  Font(googleFont = sourceSerif4, fontProvider = provider, weight = FontWeight.Normal),
  Font(googleFont = sourceSerif4, fontProvider = provider, weight = FontWeight.Normal, style = FontStyle.Italic),
  Font(googleFont = sourceSerif4, fontProvider = provider, weight = FontWeight.Medium),
  Font(googleFont = sourceSerif4, fontProvider = provider, weight = FontWeight.SemiBold),
)

/** Mono family — JetBrains Mono. Labels, chapter marks, CTA chrome, hairline meta rows. */
val Mono = FontFamily(
  Font(googleFont = jetBrainsMono, fontProvider = provider, weight = FontWeight.Normal),
  Font(googleFont = jetBrainsMono, fontProvider = provider, weight = FontWeight.Medium),
  Font(googleFont = jetBrainsMono, fontProvider = provider, weight = FontWeight.SemiBold),
)

/**
 * Material 3 Typography mapped to the site's hierarchy.
 *
 * Site → Compose role mapping:
 *   h1 (serif, 64–180px, w400, -0.045em)  → displayLarge
 *   h2 (serif, 40–88px,  w400, -0.025em)  → displayMedium / headlineLarge
 *   h3 (serif, 28–44px,  w500, -0.02em)   → headlineMedium / titleLarge
 *   manifesto lede (serif, 28–52px italic) → headlineSmall (use italic at call site)
 *   body (serif-body, 17–19px, 1.65 lh)   → bodyLarge
 *   caption (serif-body, 12–15px)         → bodyMedium / bodySmall
 *   .mono (mono, 11px, 0.18em tracking)   → labelMedium
 *   .mono small (mono, 9–10px, 0.32em)    → labelSmall
 *
 * Letter-spacing in CSS em → Compose sp: at 16sp body, 0.18em ≈ 2.88sp. We round
 * to 0.5sp increments for legibility; the eye doesn't pick up finer differences.
 */
val SpeakeaterTypography = Typography(
  // Editorial hero — wordmark, hero numerals
  displayLarge = TextStyle(
    fontFamily = SerifDisplay,
    fontWeight = FontWeight.Normal,
    fontSize = 56.sp,
    lineHeight = 60.sp,
    letterSpacing = (-2.0).sp,
  ),
  displayMedium = TextStyle(
    fontFamily = SerifDisplay,
    fontWeight = FontWeight.Normal,
    fontSize = 44.sp,
    lineHeight = 48.sp,
    letterSpacing = (-1.4).sp,
  ),
  displaySmall = TextStyle(
    fontFamily = SerifDisplay,
    fontWeight = FontWeight.Normal,
    fontSize = 36.sp,
    lineHeight = 42.sp,
    letterSpacing = (-1.0).sp,
  ),
  // Section heads — block__h, chapter titles
  headlineLarge = TextStyle(
    fontFamily = SerifDisplay,
    fontWeight = FontWeight.Normal,
    fontSize = 30.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.7).sp,
  ),
  headlineMedium = TextStyle(
    fontFamily = SerifDisplay,
    fontWeight = FontWeight.Medium,
    fontSize = 24.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.5).sp,
  ),
  headlineSmall = TextStyle(
    fontFamily = SerifDisplay,
    fontWeight = FontWeight.Medium,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.3).sp,
  ),
  // Card / row titles — recipe name, cocktail name
  titleLarge = TextStyle(
    fontFamily = SerifDisplay,
    fontWeight = FontWeight.Medium,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    letterSpacing = (-0.2).sp,
  ),
  titleMedium = TextStyle(
    fontFamily = SerifBody,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp,
  ),
  titleSmall = TextStyle(
    fontFamily = SerifBody,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.1.sp,
  ),
  // Body copy — site uses 17–19sp serif-body at 1.65 line-height
  bodyLarge = TextStyle(
    fontFamily = SerifBody,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.1.sp,
  ),
  bodyMedium = TextStyle(
    fontFamily = SerifBody,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.15.sp,
  ),
  bodySmall = TextStyle(
    fontFamily = SerifBody,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.2.sp,
  ),
  // Mono labels — chapter marks, button CTAs, caption-mono
  labelLarge = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 2.5.sp,
  ),
  labelMedium = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 2.0.sp,
  ),
  labelSmall = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    letterSpacing = 3.2.sp,
  ),
)

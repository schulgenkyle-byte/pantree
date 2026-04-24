package app.pantrie.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Warm editorial palette
val Cream = Color(0xFFF5EFE4)
val CreamAlt = Color(0xFFEDE5D5)
val Paper = Color(0xFFFAF6ED)
val Beige = Color(0xFFD9CFB8)
val Terracotta = Color(0xFFB5634A)
val TerracottaDeep = Color(0xFF975240)
val Olive = Color(0xFF7A8664)
val Ink = Color(0xFF2B2621)
val InkSoft = Color(0xFF5A5148)
val InkMuted = Color(0xFF8F877C)
val InkFaint = Color(0xFFBDB5A8)

private val LightColors = lightColorScheme(
  primary = Ink,
  onPrimary = Paper,
  secondary = Terracotta,
  onSecondary = Paper,
  tertiary = Olive,
  background = Cream,
  onBackground = Ink,
  surface = Paper,
  onSurface = Ink,
  surfaceVariant = CreamAlt,
  onSurfaceVariant = InkSoft,
  outline = InkFaint,
  error = TerracottaDeep,
)

private val DarkColors = darkColorScheme(
  primary = Paper,
  onPrimary = Ink,
  background = Ink,
  surface = InkSoft,
)

private val Typography = Typography(
  displayLarge = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.8).sp),
  displayMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.6).sp),
  headlineLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium),
  headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
  titleLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
  titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
  bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
  bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal),
  labelLarge = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
  labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
)

@Composable
fun PantrieTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColors else LightColors,
    typography = Typography,
    content = content,
  )
}

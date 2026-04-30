package app.pantrie.feature.onboarding

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantrie.feature.settings.LocalSettingsStore
import app.pantrie.ui.theme.*

/**
 * First-launch age gate. Required by Google Play regulated-goods policy because
 * Speakeater includes alcoholic-cocktail content (the Mixology layer + 4,000
 * pre-Prohibition cocktails).
 *
 * Flow:
 *   onCreate → if !hasConfirmedAge → AgeGateScreen → user taps "Yes, I'm 21+"
 *   → settings.setHasConfirmedAge(true) → onConfirmed() → resume normal nav.
 *   "No" exits the activity without setting the flag, so re-launch shows it again.
 *
 * Wire this in MainActivity.kt before the existing onboarded/login routing:
 *
 *   val hasConfirmedAge by localSettings.hasConfirmedAge.collectAsState()
 *   if (!hasConfirmedAge) {
 *     AgeGateScreen(
 *       onConfirmed = { localSettings.setHasConfirmedAge(true) },
 *       onDeclined  = { finish() },
 *     )
 *     return@setContent
 *   }
 *
 * Keep this composable visually consistent with OnboardingScreen so the launch
 * experience flows: Splash → AgeGate → Onboarding → DeckScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgeGateScreen(
  onConfirmed: () -> Unit,
  onDeclined: () -> Unit,
) {
  Scaffold(containerColor = Paper) { padding ->
    Column(
      Modifier.padding(padding).fillMaxSize().padding(horizontal = 32.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Wordmark
      Text(
        text = app.pantrie.Brand.APP_NAME,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        fontSize = 36.sp,
        color = Ink,
      )
      Spacer(Modifier.height(6.dp))
      Text(
        text = "The kitchen has a back room.",
        color = InkSoft,
        fontSize = 13.sp,
        letterSpacing = 0.18.sp,
      )

      Spacer(Modifier.height(56.dp))

      Text(
        text = "First, the door rules.",
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        color = Ink,
        textAlign = TextAlign.Center,
      )

      Spacer(Modifier.height(20.dp))

      Text(
        text = "Speakeater includes a cocktail layer with about four thousand drinks, " +
          "many transcribed from pre-Prohibition manuscripts. To enter, you have to be " +
          "of legal drinking age in your country.",
        color = InkSoft,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
        lineHeight = 22.sp,
      )

      Spacer(Modifier.height(36.dp))

      Button(
        onClick = onConfirmed,
        colors = ButtonDefaults.buttonColors(containerColor = Terracotta),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.fillMaxWidth().height(54.dp),
      ) {
        Text(
          "I am 21 or older",
          color = Paper,
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
        )
      }

      Spacer(Modifier.height(12.dp))

      OutlinedButton(
        onClick = onDeclined,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, InkFaint),
        modifier = Modifier.fillMaxWidth().height(54.dp),
      ) {
        Text("Not yet", color = Ink, fontSize = 15.sp)
      }

      Spacer(Modifier.height(28.dp))

      Text(
        text = "We don't store your birthday or any personal age data — only the fact " +
          "that you confirmed. The check repeats if you reinstall.",
        color = InkMuted,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        lineHeight = 16.sp,
      )
    }
  }
}

package app.pantrie.billing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Test medium-rectangle ad unit. Google's official sample. Replace with a real unit before prod.
 * Medium rectangles (300x250) read better as a "card" in the swipe stack than 320x50 banners.
 */
const val TEST_MEDIUM_RECT_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"

/**
 * Inline ad rendered as a swipe-stack card. Sized to roughly match a recipe card so it doesn't
 * jar visually. Includes its own "Continue" button (no swipe gesture — taps a real ad would
 * count as a click and inflate CPC fraud signals).
 *
 * Used in MixologyScreen every N cocktail swipes to monetize the swipe deck without converting
 * users to interstitials (which feel intrusive in a sustained browsing session).
 */
@Composable
fun InlineAdCard(
  onContinue: () -> Unit,
  vintageMode: Boolean = false,
  vm: BannerAdViewModel = hiltViewModel(),
) {
  val isPro by vm.isPro.collectAsState()
  if (isPro) {
    onContinue()
    return
  }

  val cardBg = if (vintageMode) Color(0xFFF6E9CF) else Color(0xFF18181B)
  val accent = if (vintageMode) Color(0xFF6B4423) else Color(0xFFC9A554)
  val ink = if (vintageMode) Color(0xFF3E2C1B) else Color(0xFFE8E3D9)
  val muted = if (vintageMode) Color(0xFF7A6F62) else Color(0xFF8B8578)

  val context = LocalContext.current
  val adView = remember {
    AdView(context).apply {
      setAdSize(AdSize.MEDIUM_RECTANGLE)
      setAdUnitId(TEST_MEDIUM_RECT_AD_UNIT_ID)
      loadAd(AdRequest.Builder().build())
    }
  }

  Card(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    colors = CardDefaults.cardColors(containerColor = cardBg),
    shape = RoundedCornerShape(16.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
  ) {
    Column(
      Modifier.fillMaxSize().padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Top "Sponsored" pill — required by AdMob policy + clarifies it's an ad.
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.18f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
      ) {
        Text(
          "Sponsored",
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
          color = accent,
        )
      }

      Spacer(Modifier.weight(0.2f))

      // The ad itself.
      Box(
        Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(Color.Black.copy(alpha = 0.04f))
          .padding(8.dp),
      ) {
        AndroidView(
          factory = { adView },
          modifier = Modifier.size(width = 320.dp, height = 270.dp),
        )
      }

      Spacer(Modifier.weight(0.4f))

      Text(
        "Brimm Pro removes ads",
        color = ink,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
      )
      Spacer(Modifier.height(2.dp))
      Text(
        "Tap below when you're ready",
        color = muted,
        fontSize = 12.sp,
      )

      Spacer(Modifier.height(16.dp))

      Button(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = if (vintageMode) Color.White else Color.Black),
        shape = RoundedCornerShape(12.dp),
      ) {
        Text("Continue", fontWeight = FontWeight.Bold, fontSize = 15.sp)
      }
    }
  }
}

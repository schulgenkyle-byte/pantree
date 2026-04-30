@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantrie.billing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import app.pantrie.ui.theme.BrassBright
import app.pantrie.ui.theme.Ink
import app.pantrie.ui.theme.InkFaint
import app.pantrie.ui.theme.InkSoft
import app.pantrie.ui.theme.Paper
import app.pantrie.ui.theme.Paper2
import app.pantrie.ui.theme.Terracotta
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// Editorial dark palette — same tokens the rest of the app uses.
private val WallBg = Paper
private val WallInk = Ink
private val WallMuted = InkFaint
private val WallAccent = Terracotta
private val WallGold = BrassBright

/**
 * Modal sheet shown when the user hits the daily swipe wall (free tier, swipe 41+).
 *
 * Two clear paths: watch a rewarded ad for +10 swipes (recurring revenue), or upgrade to Pro
 * (subscription revenue). Soft third option: come back tomorrow when the counter resets.
 */
@Composable
fun SwipeWallSheet(
  onWatchAd: () -> Unit,
  onGoPro: () -> Unit,
  onDismiss: () -> Unit,
) {
  // Non-dismissible by design: once free quota is exhausted, the only paths off the deck
  // are (a) watch a rewarded ad for +10, (b) upgrade to Pro, or (c) leave the Tonight tab
  // via the bottom nav. No "come back tomorrow" soft escape — that's what tomorrow's
  // counter reset is for. ModalBottomSheet's onDismissRequest still fires on back-press,
  // so we no-op it here to lock the user on the wall until they convert or navigate away.
  ModalBottomSheet(
    onDismissRequest = { /* intentionally empty — wall is sticky */ },
    containerColor = WallBg,
    dragHandle = null,  // no drag handle = no swipe-down-to-dismiss affordance
    sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    properties = androidx.compose.material3.ModalBottomSheetProperties(
      shouldDismissOnBackPress = false,
    ),
  ) {
    Column(
      Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        "Out of free swipes today",
        color = WallInk,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.height(6.dp))
      Text(
        "You've burned through 20 cards. Resets at midnight.",
        color = WallMuted,
        fontSize = 14.sp,
      )

      Spacer(Modifier.height(20.dp))

      // Primary CTA — rewarded ad. Highest engagement, recurring revenue, lowest friction.
      Surface(
        onClick = onWatchAd,
        shape = RoundedCornerShape(14.dp),
        color = WallAccent,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Filled.OndemandVideo, contentDescription = null, tint = Ink)
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text("Watch a quick ad", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("+10 more swipes", color = InkSoft, fontSize = 12.sp)
          }
        }
      }

      Spacer(Modifier.height(10.dp))

      // Secondary CTA — paywall. Higher LTV, recurring subscription.
      Surface(
        onClick = onGoPro,
        shape = RoundedCornerShape(14.dp),
        color = Paper2,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Filled.Star, contentDescription = null, tint = WallGold)
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text(app.pantrie.Brand.PRO_NAME, color = WallGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Unlimited swipes · ad-free · vision scan", color = InkSoft, fontSize = 12.sp)
          }
        }
      }

      // No "Come back tomorrow" — the wall is non-dismissible by design. To leave the
      // Tonight tab without watching an ad or paying, users tap the bottom nav.
      Spacer(Modifier.height(8.dp))
    }
  }
}

/** Holds AdManager so composables can call it without becoming Hilt-aware themselves. */
@HiltViewModel
class AdHostViewModel @Inject constructor(
  val adManager: AdManager,
) : ViewModel()

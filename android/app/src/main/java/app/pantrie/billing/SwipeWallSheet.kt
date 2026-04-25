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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

private val WallBg = Color(0xFFFFF8EC)
private val WallInk = Color(0xFF1F1B16)
private val WallMuted = Color(0xFF7A6F62)
private val WallAccent = Color(0xFFC9763A)
private val WallGold = Color(0xFFC9A554)

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
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = WallBg,
    dragHandle = { BottomSheetDefaults.DragHandle(color = WallMuted) },
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
          Icon(Icons.Filled.OndemandVideo, contentDescription = null, tint = Color.White)
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text("Watch a quick ad", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("+10 more swipes", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
          }
        }
      }

      Spacer(Modifier.height(10.dp))

      // Secondary CTA — paywall. Higher LTV, recurring subscription.
      Surface(
        onClick = onGoPro,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF18181B),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Filled.Star, contentDescription = null, tint = WallGold)
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text("Brimm Pro", color = WallGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Unlimited swipes · ad-free · vision scan", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
          }
        }
      }

      Spacer(Modifier.height(8.dp))
      TextButton(onClick = onDismiss) {
        Text("Come back tomorrow", color = WallMuted)
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}

/** Holds AdManager so composables can call it without becoming Hilt-aware themselves. */
@HiltViewModel
class AdHostViewModel @Inject constructor(
  val adManager: AdManager,
) : ViewModel()

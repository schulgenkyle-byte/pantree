@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantrie.billing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import app.pantrie.Brand
import app.pantrie.ui.theme.BrassBright
import app.pantrie.ui.theme.Ink
import app.pantrie.ui.theme.InkFaint
import app.pantrie.ui.theme.InkSoft
import app.pantrie.ui.theme.Mono
import app.pantrie.ui.theme.Paper
import app.pantrie.ui.theme.Paper2
import app.pantrie.ui.theme.Terracotta

/**
 * Bootlegger sepia palette duplicates — Mixology defines these privately so the values
 * live here too. Keep these in sync with MixologyScreen.kt's `Sepia` / `SepiaInk`.
 */
private val SepiaParchment = Color(0xFFE6D3A7)
private val SepiaInk = Color(0xFF3A2B1A)
private val SepiaBronze = Color(0xFF6B4423)
private val SepiaInkSoft = Color(0xCC3A2B1A)
private val SepiaInkFaint = Color(0x803A2B1A)

/**
 * Self-contained one-tap "membership" card.
 *
 * Flow: user taps a tier mini-card to select it, then taps "Continue" to launch
 * the Play Store purchase sheet directly. NO intermediate PaywallScreen — the
 * card IS the paywall.
 *
 * Hides itself when the user is already Pro. While a purchase is in-flight the
 * Continue button shows a spinner. Errors / cancellations render inline below.
 *
 * vintageMode skins the card in Bootlegger sepia/parchment; modern uses the
 * editorial dark palette.
 */
@Composable
fun ProUpgradeCard(
  vintageMode: Boolean = false,
  modifier: Modifier = Modifier,
  vm: PaywallViewModel = hiltViewModel(),
) {
  val isPro by vm.isPro.collectAsState()
  val purchasing by vm.purchasing.collectAsState()
  val purchaseEvent by vm.purchaseEvents.collectAsState()

  // Hide entirely when Pro — no upgrade card for paying users.
  if (isPro) return

  // Yearly is the default selection (highlighted tier in TIERS list).
  var selectedSku by remember { mutableStateOf(SKU_PRO_YEARLY) }

  // Activity walk — same ContextWrapper-chain trick PaywallScreen uses.
  val context = androidx.compose.ui.platform.LocalContext.current
  val activity = remember(context) {
    var c: android.content.Context? = context
    while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
    c as? android.app.Activity
  }

  // Reset purchasing state on entry — recovers from stuck state if a previous
  // purchase attempt left the flag set after a process kill.
  LaunchedEffect(Unit) { vm.resetPurchasing() }

  // Theme tokens.
  val cardBg = if (vintageMode) SepiaParchment else Paper2
  val accent = if (vintageMode) SepiaBronze else BrassBright
  val ink = if (vintageMode) SepiaInk else Ink
  val inkSoft = if (vintageMode) SepiaInkSoft else InkSoft
  val inkFaint = if (vintageMode) SepiaInkFaint else InkFaint
  val ctaContent = if (vintageMode) SepiaParchment else Paper

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = cardBg,
    border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
  ) {
    Column(Modifier.padding(20.dp)) {
      Text(
        text = "MEMBERSHIP",
        color = accent,
        fontFamily = Mono,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp,
      )

      Spacer(Modifier.height(8.dp))

      Text(
        text = Brand.PRO_NAME,
        style = MaterialTheme.typography.headlineSmall,
        color = ink,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
      )

      Spacer(Modifier.height(6.dp))

      Text(
        text = "All five Mystery Nights. 50 party menus. 7,225 cocktails. No ads.",
        color = inkSoft,
        fontSize = 13.sp,
        lineHeight = 18.sp,
      )

      Spacer(Modifier.height(16.dp))

      // Three tappable tier mini-cards. Tap one to select it; Continue uses the selection.
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TIERS.forEach { tier ->
          TierMiniCard(
            tier = tier,
            selected = tier.sku == selectedSku,
            onSelect = { selectedSku = tier.sku },
            accent = accent,
            ink = ink,
            inkSoft = inkSoft,
            inkFaint = inkFaint,
            cardBg = cardBg,
            modifier = Modifier.weight(1f),
          )
        }
      }

      Spacer(Modifier.height(16.dp))

      // Continue → straight to Play Store purchase sheet for the selected tier.
      Button(
        onClick = {
          val act = activity
          if (act != null) {
            vm.purchase(act, selectedSku)
          } else {
            vm.surfaceActivityError()
          }
        },
        enabled = !purchasing && activity != null,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = accent,
          contentColor = ctaContent,
          disabledContainerColor = accent.copy(alpha = 0.55f),
          disabledContentColor = ctaContent,
        ),
        shape = RoundedCornerShape(12.dp),
      ) {
        if (purchasing) {
          CircularProgressIndicator(
            color = ctaContent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp),
          )
        } else {
          Text(
            text = "Continue",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
          )
        }
      }

      // Inline event surface — error or cancellation shows here, success silently
      // flips isPro which hides the whole card next recomposition.
      val ev = purchaseEvent
      if (ev is PurchaseEvent.Error) {
        Spacer(Modifier.height(8.dp))
        Text(
          text = ev.message,
          color = if (vintageMode) Color(0xFF8B1E1E) else Terracotta,
          fontSize = 12.sp,
        )
      } else if (ev is PurchaseEvent.Cancelled) {
        Spacer(Modifier.height(8.dp))
        Text(
          text = "Purchase cancelled.",
          color = inkFaint,
          fontSize = 12.sp,
        )
      }

      Spacer(Modifier.height(8.dp))
      Text(
        text = "Auto-renews unless cancelled. Manage in Play Store.",
        color = inkFaint,
        fontSize = 10.sp,
      )
    }
  }
}

@Composable
private fun TierMiniCard(
  tier: PriceTier,
  selected: Boolean,
  onSelect: () -> Unit,
  accent: Color,
  ink: Color,
  inkSoft: Color,
  inkFaint: Color,
  cardBg: Color,
  modifier: Modifier = Modifier,
) {
  val borderColor = if (selected) accent else inkFaint.copy(alpha = 0.5f)
  val borderWidth = if (selected) 2.dp else 1.dp
  val tintBg = if (selected) accent.copy(alpha = 0.12f) else cardBg

  Surface(
    onClick = onSelect,
    modifier = modifier,
    shape = RoundedCornerShape(10.dp),
    color = tintBg,
    border = BorderStroke(borderWidth, borderColor),
  ) {
    Column(
      Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = tier.title.uppercase(),
        color = if (selected) accent else inkSoft,
        fontFamily = Mono,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
      )
      Spacer(Modifier.height(6.dp))
      Text(
        text = compactPrice(tier.price),
        color = ink,
        fontFamily = Mono,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
      )
      val suffix = priceSuffix(tier.price)
      if (suffix != null) {
        Text(
          text = suffix,
          color = inkSoft,
          fontFamily = Mono,
          fontSize = 9.sp,
          fontWeight = FontWeight.Medium,
        )
      }
      tier.savings?.let { savings ->
        Spacer(Modifier.height(6.dp))
        Text(
          text = savings,
          color = accent,
          fontFamily = Mono,
          fontSize = 8.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.5.sp,
        )
      }
    }
  }
}

private fun compactPrice(raw: String): String = raw.substringBefore('/').trim()

private fun priceSuffix(raw: String): String? {
  val idx = raw.indexOf('/')
  return if (idx < 0) null else raw.substring(idx).trim()
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantrie.billing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private val ProGold = Color(0xFFC9A554)
private val ProBg = Color(0xFF0D0D0E)
private val ProCard = Color(0xFF18181B)
private val ProInk = Color(0xFFE8E3D9)
private val ProMuted = Color(0xFF8B8578)

data class PriceTier(
  val sku: String,
  val title: String,
  val price: String,
  val perMonth: String?,
  val savings: String?,
  val highlight: Boolean = false,
)

private val TIERS = listOf(
  PriceTier(
    sku = "brimm_pro_monthly",
    title = "Monthly",
    price = "$4.99/mo",
    perMonth = null,
    savings = null,
  ),
  PriceTier(
    sku = "brimm_pro_yearly",
    title = "Yearly",
    price = "$29.99/yr",
    perMonth = "$2.50/mo",
    savings = "Save 50%",
    highlight = true,
  ),
  PriceTier(
    sku = "brimm_pro_lifetime",
    title = "Lifetime",
    price = "$59.99",
    perMonth = "one-time",
    savings = "Best value",
  ),
)

private val PRO_FEATURES = listOf(
  "Unlimited pantry items",
  "Vision pantry scan (snap → ingredients)",
  "Smart shopping list",
  "Meal prep planner",
  "No ads, ever",
  "Submit your own recipes",
  "Priority support",
)

@Composable
fun PaywallScreen(
  onClose: () -> Unit,
  vm: PaywallViewModel = hiltViewModel(),
) {
  val isPro by vm.isPro.collectAsState()
  val purchasing by vm.purchasing.collectAsState()
  val purchaseEvent by vm.purchaseEvents.collectAsState()
  var selectedSku by remember { mutableStateOf("brimm_pro_yearly") }
  val context = androidx.compose.ui.platform.LocalContext.current
  // Walk the ContextWrapper chain to find the Activity — LocalContext sometimes returns
  // a ContextWrapper instead of the Activity directly (varies by phone OEM / launcher),
  // which made the Continue button render as disabled (invisible on dark theme) on
  // some testers' phones while working on others. Walking the chain handles every case.
  val activity = remember(context) {
    var c: android.content.Context? = context
    while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
    c as? android.app.Activity
  }

  // Reset purchasing state on screen entry — if a previous attempt got stuck (e.g. Play Store
  // process killed mid-purchase), the button could be permanently disabled until process restart.
  androidx.compose.runtime.LaunchedEffect(Unit) { vm.resetPurchasing() }

  // Auto-close on successful purchase, surface errors via a snackbar-style toast.
  androidx.compose.runtime.LaunchedEffect(purchaseEvent) {
    val ev = purchaseEvent
    if (ev is PurchaseEvent.Success) {
      kotlinx.coroutines.delay(800)
      onClose()
    }
  }

  Scaffold(containerColor = ProBg) { padding ->
    // Two-section layout — scrollable content (features + tiers) on top with weight(1f),
    // sticky CTA pinned at the bottom. Without this, on smaller screens or larger system
    // font scales, the Continue button rendered below the visible viewport and was
    // physically untappable. Reported on a Samsung S938U with default font size.
    Column(
      Modifier
        .padding(padding)
        .fillMaxSize()
        .background(ProBg),
    ) {
      // Scrollable upper section.
      Column(
        Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp),
      ) {
      // Header
      Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onClose) {
          Icon(Icons.Outlined.Close, contentDescription = "Close", tint = ProInk)
        }
        Spacer(Modifier.weight(1f))
      }

      Spacer(Modifier.height(16.dp))
      Text("Brimm Pro", color = ProGold, fontSize = 32.sp, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(4.dp))
      Text(
        "Unlock everything. Cancel anytime.",
        color = ProMuted,
        fontSize = 15.sp,
      )

      Spacer(Modifier.height(24.dp))

      // Feature list
      PRO_FEATURES.forEach { feature ->
        Row(
          Modifier.fillMaxWidth().padding(vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = ProGold,
            modifier = Modifier.size(20.dp),
          )
          Spacer(Modifier.width(12.dp))
          Text(feature, color = ProInk, fontSize = 15.sp)
        }
      }

      Spacer(Modifier.height(24.dp))

      // Tiers
      TIERS.forEach { tier ->
        TierCard(
          tier = tier,
          selected = tier.sku == selectedSku,
          onSelect = { selectedSku = tier.sku },
        )
        Spacer(Modifier.height(10.dp))
      }

      Spacer(Modifier.height(16.dp))
      } // end scrollable upper section

      // ===== Sticky bottom CTA section — pinned regardless of screen size / font scale. =====
      Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).background(ProBg)) {
      // CTA — always rendered with full-contrast colors so it's never invisible on dark bg.
      // Disabled only while a purchase is actively in flight (purchasing) or already Pro.
      Button(
        onClick = {
          val act = activity
          if (act != null) {
            vm.purchase(act, selectedSku)
          } else {
            vm.surfaceActivityError()
          }
        },
        enabled = !purchasing && !isPro,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = ProGold,
          contentColor = ProBg,
          // Force the disabled colors to STILL be high-contrast — Material3's default
          // disabled palette fades to ~38% alpha which renders nearly invisible on dark bg.
          disabledContainerColor = ProGold.copy(alpha = 0.6f),
          disabledContentColor = ProBg,
        ),
        shape = RoundedCornerShape(14.dp),
      ) {
        when {
          isPro -> Text("You're already Pro 🎉", fontWeight = FontWeight.Bold)
          purchasing -> CircularProgressIndicator(color = ProBg, modifier = Modifier.size(20.dp))
          else -> Text("Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
      }

      // Error / cancel surfacing — small inline message under the CTA.
      val ev = purchaseEvent
      if (ev is PurchaseEvent.Error) {
        Spacer(Modifier.height(6.dp))
        Text(ev.message, color = Color(0xFFE07060), fontSize = 12.sp)
      } else if (ev is PurchaseEvent.Cancelled) {
        Spacer(Modifier.height(6.dp))
        Text("Purchase cancelled.", color = ProMuted, fontSize = 12.sp)
      }

      Spacer(Modifier.height(8.dp))
      Text(
        "Auto-renews unless cancelled. Manage in Play Store. " +
          "Restore purchases via Settings.",
        color = ProMuted,
        fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 16.dp),
      )
      } // end sticky bottom CTA section
    }
  }
}

@Composable
private fun TierCard(tier: PriceTier, selected: Boolean, onSelect: () -> Unit) {
  Surface(
    onClick = onSelect,
    shape = RoundedCornerShape(14.dp),
    color = if (selected) ProGold.copy(alpha = 0.15f) else ProCard,
    border = androidx.compose.foundation.BorderStroke(
      width = if (selected) 2.dp else 1.dp,
      color = if (selected) ProGold else ProMuted.copy(alpha = 0.3f),
    ),
  ) {
    Row(
      Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(tier.title, color = ProInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
          tier.savings?.let {
            Spacer(Modifier.width(8.dp))
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = ProGold,
            ) {
              Text(
                it,
                color = ProBg,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              )
            }
          }
        }
        tier.perMonth?.let {
          Spacer(Modifier.height(2.dp))
          Text(it, color = ProMuted, fontSize = 12.sp)
        }
      }
      Text(tier.price, color = ProGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
  }
}

@HiltViewModel
class PaywallViewModel @Inject constructor(
  private val entitlement: EntitlementRepository,
  private val billing: BillingManager,
) : ViewModel() {
  val isPro = entitlement.isPro

  private val _purchasing = MutableStateFlow(false)
  val purchasing = _purchasing.asStateFlow()

  /** Forwarded from BillingManager so the paywall UI can react to success/error. */
  val purchaseEvents = billing.purchaseEvents

  /**
   * Real Play Billing flow:
   *   1. BillingManager.launchPurchase pops Google's purchase sheet
   *   2. PurchasesUpdatedListener fires with the result
   *   3. BillingManager forwards purchase token to backend (api.verifyPurchase)
   *   4. Backend validates with Google Play Developer API + writes entitlement row
   *   5. entitlement.markProAfterPurchase() flips isPro locally
   *   6. Paywall auto-closes via the LaunchedEffect on Success
   */
  fun purchase(activity: android.app.Activity, sku: String) {
    viewModelScope.launch {
      _purchasing.value = true
      val result = billing.launchPurchase(activity, sku)
      result.onFailure { e ->
        // Surface the failure so the paywall shows it under the Continue button.
        // Most common cause: Play Console product not created/activated yet
        // ("Product brimm_pro_X not found in Play Console — verify it's created and active").
        billing.surfaceError(
          "Couldn't open Google's purchase sheet: ${e.message ?: e::class.simpleName ?: "unknown"}"
        )
      }
      _purchasing.value = false
    }
  }

  /** Force-clear the purchasing flag on screen entry — recovers from a stuck state where
   * a previous purchase attempt left _purchasing.value = true forever (process killed mid-flow). */
  fun resetPurchasing() { _purchasing.value = false }

  /** Surface an error when the Continue button can't resolve the host Activity from
   * LocalContext (rare — usually the ContextWrapper-walk in PaywallScreen handles it). */
  fun surfaceActivityError() {
    billing.surfaceError("Couldn't open the purchase sheet — close Brimm and reopen, then try again.")
  }
}

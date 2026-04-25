@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantrie.billing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
  var selectedSku by remember { mutableStateOf("brimm_pro_yearly") }

  Scaffold(containerColor = ProBg) { padding ->
    Column(
      Modifier
        .padding(padding)
        .fillMaxSize()
        .background(ProBg)
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

      Spacer(Modifier.weight(1f))

      // CTA
      Button(
        onClick = { vm.purchase(selectedSku) },
        enabled = !purchasing && !isPro,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ProGold, contentColor = ProBg),
        shape = RoundedCornerShape(14.dp),
      ) {
        when {
          isPro -> Text("You're already Pro 🎉", fontWeight = FontWeight.Bold)
          purchasing -> CircularProgressIndicator(color = ProBg, modifier = Modifier.size(20.dp))
          else -> Text("Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
      }

      Spacer(Modifier.height(8.dp))
      Text(
        "Auto-renews unless cancelled. Manage in Play Store. " +
          "Restore purchases via Settings.",
        color = ProMuted,
        fontSize = 11.sp,
        modifier = Modifier.padding(bottom = 16.dp),
      )
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
) : ViewModel() {
  val isPro = entitlement.isPro

  private val _purchasing = MutableStateFlow(false)
  val purchasing = _purchasing.asStateFlow()

  /**
   * Stub purchase flow. Real implementation will:
   *   1. BillingClient.queryProductDetails(sku)
   *   2. BillingClient.launchBillingFlow(...)
   *   3. On success → api.verifyPurchase(token) → entitlement.refresh()
   *
   * This requires the Brimm Pro subscription products to be created in Play Console first.
   * For now we just refresh entitlement so the screen wiring is exercisable end-to-end.
   */
  fun purchase(sku: String) {
    viewModelScope.launch {
      _purchasing.value = true
      // TODO(billing): wire BillingClient.launchBillingFlow once products are provisioned.
      entitlement.refresh()
      _purchasing.value = false
    }
  }
}

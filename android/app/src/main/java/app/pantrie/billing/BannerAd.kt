package app.pantrie.billing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Test banner ad unit. Google's official sample — safe to ship in debug, replace before prod.
 * Real ad units come from your AdMob console after creating ad units there.
 */
const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"

/**
 * Adaptive-banner ad. Shows nothing for Pro users. The banner takes ~50dp of vertical space.
 *
 * Place at the bottom of feed-style screens (Culinary, Mixology, Search, Shopping). Don't place
 * inside scrolling list items — that violates AdMob policy on overlap with content.
 */
@Composable
fun BannerAd(
  modifier: Modifier = Modifier,
  adUnitId: String = TEST_BANNER_AD_UNIT_ID,
  vm: BannerAdViewModel = hiltViewModel(),
) {
  val isPro by vm.isPro.collectAsState()
  if (isPro) return

  val context = LocalContext.current
  val adView = remember {
    AdView(context).apply {
      setAdSize(AdSize.BANNER)
      setAdUnitId(adUnitId)
      loadAd(AdRequest.Builder().build())
    }
  }
  Box(modifier.fillMaxWidth().height(50.dp)) {
    AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth())
  }
}

@HiltViewModel
class BannerAdViewModel @Inject constructor(
  entitlement: EntitlementRepository,
) : ViewModel() {
  val isPro = entitlement.isPro
}

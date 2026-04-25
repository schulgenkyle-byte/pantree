package app.pantrie

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.pantrie.billing.AdManager
import app.pantrie.billing.EntitlementRepository
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PantrieApplication : Application(), Configuration.Provider {
  @Inject lateinit var workerFactory: HiltWorkerFactory
  @Inject lateinit var entitlement: EntitlementRepository
  @Inject lateinit var adManager: AdManager

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder()
      .setWorkerFactory(workerFactory)
      .build()

  override fun onCreate() {
    super.onCreate()
    // Init AdMob in the background — first call can take ~100ms otherwise.
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    scope.launch {
      MobileAds.initialize(this@PantrieApplication) {
        // Once SDK is up, preload one interstitial + one rewarded so the first show is instant.
        adManager.warmUp()
      }
    }
    // Refresh entitlement so isPro is fresh by the time the user reaches a Pro-gated screen.
    scope.launch { entitlement.refresh() }
  }
}

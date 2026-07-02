package app.pantrie.billing

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Production interstitial ad unit. Created in AdMob 2026-05-03 for app.brimm/Speakeater.
 *  Was test sample ca-app-pub-3940256099942544/1033173712 before. */
const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-8540719149057182/6475646726"
/** Rewarded unit still uses Google's test sample — no rewarded unit created yet in AdMob.
 *  When you add one, swap this value. */
const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

/**
 * Lazy preloader for interstitial + rewarded ads. Both formats need to be loaded *before* you
 * want to show them, otherwise tapping "show" results in an empty cache and a delay.
 *
 * Strategy: preload on app start, reload immediately after each show. That keeps an ad
 * warmed up and ready when the user crosses the next 10-swipe boundary or hits the wall.
 */
@Singleton
class AdManager @Inject constructor(
  @ApplicationContext private val context: Context,
) {
  private var interstitial: InterstitialAd? = null
  private var rewarded: RewardedAd? = null

  fun warmUp() {
    loadInterstitial()
    loadRewarded()
  }

  private fun loadInterstitial() {
    InterstitialAd.load(
      context,
      TEST_INTERSTITIAL_AD_UNIT_ID,
      AdRequest.Builder().build(),
      object : InterstitialAdLoadCallback() {
        override fun onAdLoaded(ad: InterstitialAd) {
          interstitial = ad
        }
        override fun onAdFailedToLoad(err: LoadAdError) {
          interstitial = null
        }
      },
    )
  }

  private fun loadRewarded() {
    RewardedAd.load(
      context,
      TEST_REWARDED_AD_UNIT_ID,
      AdRequest.Builder().build(),
      object : RewardedAdLoadCallback() {
        override fun onAdLoaded(ad: RewardedAd) {
          rewarded = ad
        }
        override fun onAdFailedToLoad(err: LoadAdError) {
          rewarded = null
        }
      },
    )
  }

  /** Show interstitial if loaded. Always reloads after, so next slot has an ad warmed up. */
  fun showInterstitial(activity: Activity, onClosed: () -> Unit) {
    val ad = interstitial
    if (ad == null) {
      // No ad cached — don't block the user. Just continue and start preloading for next time.
      loadInterstitial()
      onClosed()
      return
    }
    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
      override fun onAdDismissedFullScreenContent() {
        interstitial = null
        loadInterstitial()
        onClosed()
      }
      override fun onAdFailedToShowFullScreenContent(err: AdError) {
        interstitial = null
        loadInterstitial()
        onClosed()
      }
    }
    ad.show(activity)
  }

  /**
   * Show rewarded ad. `onReward` fires only if the user watched long enough to earn the reward.
   * `onClosed` always fires when the ad is dismissed (with or without reward).
   */
  fun showRewarded(activity: Activity, onReward: () -> Unit, onClosed: () -> Unit) {
    val ad = rewarded
    if (ad == null) {
      loadRewarded()
      onClosed()
      return
    }
    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
      override fun onAdDismissedFullScreenContent() {
        rewarded = null
        loadRewarded()
        onClosed()
      }
      override fun onAdFailedToShowFullScreenContent(err: AdError) {
        rewarded = null
        loadRewarded()
        onClosed()
      }
    }
    ad.show(activity, OnUserEarnedRewardListener { onReward() })
  }
}

@Module
@InstallIn(SingletonComponent::class)
object AdManagerModule

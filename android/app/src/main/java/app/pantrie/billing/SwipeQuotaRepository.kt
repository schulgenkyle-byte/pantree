package app.pantrie.billing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Daily swipe quota for free users. Resets at local midnight.
 *
 * Free tier: 40 swipes/day. Ad shown every 10th swipe (positions 10/20/30/40). At swipe 41+,
 * deck is gated — user can either watch a rewarded ad for +10 more or upgrade to Pro.
 *
 * Pro users: unlimited (controlled at the call site by checking entitlement.isPro).
 */
@Singleton
class SwipeQuotaRepository @Inject constructor(
  private val dataStore: DataStore<Preferences>,
) {
  private val _swipesToday = MutableStateFlow(0)
  val swipesToday: StateFlow<Int> = _swipesToday.asStateFlow()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  init {
    scope.launch { hydrate() }
  }

  private suspend fun hydrate() {
    runCatching {
      val prefs = dataStore.data.first()
      val storedDay = prefs[KEY_DAY] ?: 0L
      val today = todayEpochDay()
      if (storedDay != today) {
        // New day: reset counter, persist new day key.
        dataStore.edit {
          it[KEY_DAY] = today
          it[KEY_COUNT] = 0
        }
        _swipesToday.value = 0
      } else {
        _swipesToday.value = prefs[KEY_COUNT] ?: 0
      }
    }
  }

  /** Increment by one swipe. Returns the new count. */
  suspend fun increment(): Int {
    val today = todayEpochDay()
    var newCount = 0
    runCatching {
      dataStore.edit { p ->
        val storedDay = p[KEY_DAY] ?: 0L
        val current = if (storedDay == today) (p[KEY_COUNT] ?: 0) else 0
        newCount = current + 1
        p[KEY_DAY] = today
        p[KEY_COUNT] = newCount
      }
    }
    _swipesToday.value = newCount
    return newCount
  }

  /** Bonus swipes from rewarded-ad watch. */
  suspend fun grantBonusSwipes(n: Int) {
    runCatching {
      dataStore.edit { p ->
        val current = p[KEY_COUNT] ?: 0
        // Decrement counter by `n` so user gets `n` more before hitting the wall again.
        p[KEY_COUNT] = (current - n).coerceAtLeast(0)
      }
    }
    val cur = _swipesToday.value
    _swipesToday.value = (cur - n).coerceAtLeast(0)
  }

  companion object {
    const val FREE_DAILY_LIMIT = 20
    const val AD_EVERY_N_SWIPES = 10
    const val REWARDED_BONUS = 10

    private val KEY_COUNT = intPreferencesKey("swipes_today_count")
    private val KEY_DAY = longPreferencesKey("swipes_today_day")

    private fun todayEpochDay(): Long {
      val cal = Calendar.getInstance()
      cal.set(Calendar.HOUR_OF_DAY, 0)
      cal.set(Calendar.MINUTE, 0)
      cal.set(Calendar.SECOND, 0)
      cal.set(Calendar.MILLISECOND, 0)
      return cal.timeInMillis / 86_400_000L
    }
  }
}

/** Result of an outgoing swipe — instructs UI what to show next. */
sealed interface SwipeOutcome {
  /** Allow the next card to render normally. */
  data object Continue : SwipeOutcome
  /** Show an interstitial ad before the next card. */
  data object ShowAd : SwipeOutcome
  /** Free quota exhausted — render the paywall/rewarded-ad gate instead of the next card. */
  data object Wall : SwipeOutcome
}

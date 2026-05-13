package app.pantrie.feature.parties

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.billing.EntitlementRepository
import app.pantrie.feature.beta.Analytics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * State + redemption flow for the Curate-a-Party Parties tab.
 *
 * Today's surface is screenshot-grade: the redemption code flow validates
 * SPEAK-XXXX-YYYY format locally, then grants the entire menu set (or one
 * specific menu, depending on the code's payload encoded in the suffix). When
 * the backend `POST /menus/redeem` endpoint ships, swap [redeemCode] to hit
 * the network and persist the granted_menus list from the server response.
 *
 * Persistence: locally redeemed menu ids live in a parties-scoped DataStore
 * so the unlock survives app restarts and process death without needing the
 * backend round-trip. The cache is purely a UI hint; the server is authoritative
 * for the production Play-Billing single-menu purchase path.
 *
 * Pro users see every menu unlocked via [EntitlementRepository.isPro] — the
 * combinator in [hasMenu] ORs Pro with the local redemption set.
 */

private const val DATA_STORE_NAME = "parties"
private val Context.partiesDataStore: DataStore<Preferences> by preferencesDataStore(name = DATA_STORE_NAME)

private val KEY_REDEEMED_MENU_IDS = stringSetPreferencesKey("redeemed_menu_ids_v1")
private val KEY_REDEMPTION_CODES = stringSetPreferencesKey("redemption_codes_used_v1")

/** SPEAK-XXXX-YYYY where X/Y are upper-case alphanumeric. */
private val CODE_REGEX = Regex("^SPEAK-[A-Z0-9]{4}-[A-Z0-9]{4}$")
/** Special wildcard suffix the backend would use to grant all 50 menus. */
private const val GRANT_ALL_WILDCARD = "*"

/** Result type for redemption attempts. UI maps these to copy. */
sealed class RedemptionResult {
  data class Success(val grantedMenuIds: Set<String>) : RedemptionResult()
  data object InvalidFormat : RedemptionResult()
  data object AlreadyUsed : RedemptionResult()
  data class Failure(val message: String) : RedemptionResult()
}

@HiltViewModel
class PartiesViewModel @Inject constructor(
  @ApplicationContext private val ctx: Context,
  internal val entitlement: EntitlementRepository,
  private val analytics: Analytics,
  @PartiesStore private val dataStore: DataStore<Preferences>,
) : ViewModel() {

  private val _redeemedMenuIds = MutableStateFlow<Set<String>>(emptySet())
  val redeemedMenuIds: StateFlow<Set<String>> = _redeemedMenuIds.asStateFlow()

  private val _redemptionFeedback = MutableStateFlow<RedemptionResult?>(null)
  val redemptionFeedback: StateFlow<RedemptionResult?> = _redemptionFeedback.asStateFlow()

  /** True when this menu is unlocked for the user (Pro OR redeemed). */
  val hasMenuFlow: (String) -> StateFlow<Boolean> = { menuId ->
    combine(entitlement.isPro, _redeemedMenuIds) { isPro, redeemed ->
      isPro || menuId in redeemed || ALL_WILDCARD in redeemed
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
  }

  init {
    viewModelScope.launch {
      runCatching {
        val cached = dataStore.data.map { it[KEY_REDEEMED_MENU_IDS] ?: emptySet() }.first()
        _redeemedMenuIds.value = cached
      }
    }
  }

  /**
   * Validate format, mark code used, grant menus. Encoded grant policy on the
   * client side until the backend endpoint ships: the first character of the
   * second segment determines what gets granted.
   *
   *   SPEAK-A____-____  → all five menus + the wildcard (full Founding Set)
   *   SPEAK-B____-____  → first menu only (Bee's Knees, the Kickstarter $4 tier)
   *   SPEAK-C____-____  → first five menus (the $10 Five Menus tier)
   *   SPEAK-D____-____  → all five menus (the $25 Beta tier)
   *   anything else      → all five menus (default — Founding Set semantics)
   *
   * The backend will replace this client-side decoding with a server-validated
   * payload from the party_menu_code row. For screenshot fidelity the local
   * version is enough.
   */
  fun redeemCode(rawCode: String) {
    val code = rawCode.trim().uppercase()
    if (!CODE_REGEX.matches(code)) {
      _redemptionFeedback.value = RedemptionResult.InvalidFormat
      return
    }
    viewModelScope.launch {
      val usedCodes = dataStore.data.map { it[KEY_REDEMPTION_CODES] ?: emptySet() }.first()
      if (code in usedCodes) {
        _redemptionFeedback.value = RedemptionResult.AlreadyUsed
        return@launch
      }
      val granted = decodeGrant(code)
      _redeemedMenuIds.value = _redeemedMenuIds.value + granted
      runCatching {
        dataStore.edit { prefs ->
          prefs[KEY_REDEEMED_MENU_IDS] = _redeemedMenuIds.value
          prefs[KEY_REDEMPTION_CODES] = usedCodes + code
        }
      }
      analytics.track("menu_code_redeemed", mapOf("code_class" to code.getOrNull(6)?.toString().orEmpty(), "granted_count" to granted.size))
      _redemptionFeedback.value = RedemptionResult.Success(granted)
    }
  }

  private fun decodeGrant(code: String): Set<String> {
    val classChar = code.getOrNull(6)
    return when (classChar) {
      'B' -> setOf(ALL_MENUS[0].id)
      'C' -> ALL_MENUS.take(5).map { it.id }.toSet()
      'D' -> ALL_MENUS.map { it.id }.toSet()
      'A' -> ALL_MENUS.map { it.id }.toSet() + ALL_WILDCARD
      else -> ALL_MENUS.map { it.id }.toSet()
    }
  }

  fun dismissFeedback() { _redemptionFeedback.value = null }

  /** Dev-only convenience: lock everything back, for re-shooting screenshots. */
  fun debugResetRedemptions() {
    viewModelScope.launch {
      _redeemedMenuIds.value = emptySet()
      runCatching {
        dataStore.edit { prefs ->
          prefs[KEY_REDEEMED_MENU_IDS] = emptySet()
          prefs[KEY_REDEMPTION_CODES] = emptySet()
        }
      }
    }
  }

  /** Dev-only: unlock everything without redeeming, for re-shooting screenshots. */
  fun debugUnlockAll() {
    viewModelScope.launch {
      _redeemedMenuIds.value = ALL_MENUS.map { it.id }.toSet() + ALL_WILDCARD
      runCatching {
        dataStore.edit { prefs ->
          prefs[KEY_REDEEMED_MENU_IDS] = _redeemedMenuIds.value
        }
      }
    }
  }

  companion object {
    /** Backend-side will use "*" to grant every menu including the World Tour set. */
    const val ALL_WILDCARD = GRANT_ALL_WILDCARD
  }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PartiesStore

@Module
@InstallIn(SingletonComponent::class)
object PartiesModule {
  @Provides
  @Singleton
  @PartiesStore
  fun providePartiesDataStore(@ApplicationContext ctx: Context): DataStore<Preferences> =
    ctx.partiesDataStore
}

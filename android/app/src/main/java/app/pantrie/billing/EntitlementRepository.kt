package app.pantrie.billing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import app.pantrie.network.PantrieApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.entitlementDataStore: DataStore<Preferences> by preferencesDataStore(name = "entitlement")

/**
 * Single source of truth for "is this user a Speakeater Pro subscriber?"
 *
 * Strategy: cache last-known entitlement in DataStore so the app boots offline knowing the user's
 * Pro status (no flicker on cold start). Refresh from the server whenever called and on user
 * actions that depend on it (e.g. opening a paywall, attempting a Pro-gated feature).
 *
 * The server is authoritative — we never trust the cached value on Pro-gated *server* calls
 * (those are validated server-side via JWT + entitlement table). The cache is purely a UI hint
 * to avoid Pro features briefly flashing before entitlement loads.
 */
@Singleton
class EntitlementRepository @Inject constructor(
  private val api: PantrieApi,
  private val dataStore: DataStore<Preferences>,
) {
  private val _isPro = MutableStateFlow(false)
  val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  init {
    // Load cached entitlement immediately so the UI doesn't flicker free→Pro.
    scope.launch {
      runCatching {
        val cached = dataStore.data.map { it[KEY_IS_PRO] ?: false }.first()
        _isPro.value = cached
      }
    }
  }

  /** Refresh from server. Call on app foreground, after purchase, before showing paywall. */
  suspend fun refresh() {
    runCatching { api.entitlement() }
      .onSuccess { resp ->
        _isPro.value = resp.active
        runCatching {
          dataStore.edit { it[KEY_IS_PRO] = resp.active }
        }
      }
      // On failure, keep last-known value — better to not lock out an offline Pro user.
  }

  /** Manually flip to Pro after a successful purchase verification. */
  suspend fun markProAfterPurchase() {
    _isPro.value = true
    runCatching {
      dataStore.edit { it[KEY_IS_PRO] = true }
    }
  }

  /** Dev-only: ask the server to grant Pro to the calling user, then refresh.
   *  Server endpoint is gated on ENVIRONMENT=dev — returns 404 in production. */
  suspend fun debugGrantPro() {
    runCatching {
      val resp = api.debugGrantPro()
      _isPro.value = resp.active
      dataStore.edit { it[KEY_IS_PRO] = resp.active }
    }
  }

  /** Dev-only counterpart to [debugGrantPro]. Removes the entitlement row. */
  suspend fun debugRevokePro() {
    runCatching {
      val resp = api.debugRevokePro()
      _isPro.value = resp.active
      dataStore.edit { it[KEY_IS_PRO] = resp.active }
    }
  }

  companion object {
    private val KEY_IS_PRO = booleanPreferencesKey("is_pro_v1")
  }
}

@Module
@InstallIn(SingletonComponent::class)
object EntitlementModule {
  @Provides
  @Singleton
  fun provideEntitlementDataStore(@ApplicationContext ctx: Context): DataStore<Preferences> =
    ctx.entitlementDataStore
}

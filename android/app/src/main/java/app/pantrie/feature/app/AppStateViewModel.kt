package app.pantrie.feature.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.network.PantrieApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Process-wide UI state shared across screens — powers nav-bar badges.
 * shoppingCount = unchecked items on the shopping list (what the Shop tab badge shows).
 * expiringCount = pantry items expiring soon or already expired (surfaces on Pantry + inside Shop).
 */
@HiltViewModel
class AppStateViewModel @Inject constructor(
  private val api: PantrieApi,
  private val refreshBus: RefreshBus,
) : ViewModel() {

  private val _expiringCount = MutableStateFlow(0)
  val expiringCount = _expiringCount.asStateFlow()

  private val _shoppingCount = MutableStateFlow(0)
  val shoppingCount = _shoppingCount.asStateFlow()

  init {
    refresh()
    // Refresh badges automatically when another screen mutates shopping/pantry state.
    viewModelScope.launch { refreshBus.shopping.collect { refresh() } }
    viewModelScope.launch { refreshBus.pantry.collect { refresh() } }
  }

  fun refresh() {
    viewModelScope.launch {
      runCatching { api.homeStats() }.onSuccess { h ->
        _expiringCount.value = h.expiringSoon + h.expired
      }
      runCatching { api.shopping() }.onSuccess { resp ->
        _shoppingCount.value = resp.items.count { !it.checked }
      }
    }
  }
}

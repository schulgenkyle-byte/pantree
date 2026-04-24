package app.pantrie.feature.barcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.BarcodeLookupRequest
import app.pantrie.network.dto.BarcodeProduct
import app.pantrie.network.dto.PantryAddRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BarcodeUiState {
  data object Scanning : BarcodeUiState
  data object LookingUp : BarcodeUiState
  data class Found(val barcode: String, val product: BarcodeProduct) : BarcodeUiState
  data class NotFound(val barcode: String) : BarcodeUiState
  data class Error(val message: String) : BarcodeUiState
  data object Added : BarcodeUiState
}

@HiltViewModel
class BarcodeViewModel @Inject constructor(
  private val api: PantrieApi,
) : ViewModel() {

  private val _state = MutableStateFlow<BarcodeUiState>(BarcodeUiState.Scanning)
  val state = _state.asStateFlow()

  private var lastCode: String? = null

  fun onCodeDetected(raw: String) {
    val code = raw.filter { it.isDigit() }
    if (code.length !in 8..14) return
    if (code == lastCode) return
    lastCode = code

    viewModelScope.launch {
      _state.value = BarcodeUiState.LookingUp
      try {
        val resp = api.lookupBarcode(BarcodeLookupRequest(code))
        val product = resp.product
        if (resp.ok && product != null) {
          _state.value = BarcodeUiState.Found(code, product)
        } else {
          _state.value = BarcodeUiState.NotFound(code)
        }
      } catch (e: Exception) {
        _state.value = BarcodeUiState.Error(e.message ?: "Lookup failed")
      }
    }
  }

  fun confirmAdd(product: BarcodeProduct, quantity: Double = 1.0) {
    viewModelScope.launch {
      runCatching {
        api.addPantryItem(
          PantryAddRequest(
            name = product.name,
            category = product.category,
            quantity = quantity,
            unit = "count",
            expiresAt = null, // server fills via smart-expiry default
          )
        )
      }.onSuccess { _state.value = BarcodeUiState.Added }
        .onFailure { _state.value = BarcodeUiState.Error(it.message ?: "Add failed") }
    }
  }

  fun reset() {
    lastCode = null
    _state.value = BarcodeUiState.Scanning
  }

  fun addManual(barcode: String, name: String) {
    viewModelScope.launch {
      runCatching {
        api.addPantryItem(PantryAddRequest(name = name, category = "other", quantity = 1.0, unit = "count"))
      }.onSuccess { _state.value = BarcodeUiState.Added }
        .onFailure { _state.value = BarcodeUiState.Error(it.message ?: "Add failed") }
    }
  }
}

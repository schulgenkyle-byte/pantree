package app.pantrie.feature.mealprep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.feature.beta.Analytics
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.MealPrepRecipe
import app.pantrie.network.dto.MealPrepRequest
import app.pantrie.network.dto.MealPrepResponse
import app.pantrie.network.dto.MealPrepShoppingItem
import app.pantrie.network.dto.MealPrepWeek
import app.pantrie.network.dto.ShoppingAddRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject

sealed interface MealPrepUiState {
  data object Idle : MealPrepUiState
  data object Loading : MealPrepUiState
  data class Ready(
    val tier: String,
    val weeks: List<MealPrepWeek>,
    val shoppingList: List<MealPrepShoppingItem>,
  ) : MealPrepUiState
  data class PaywallPreview(
    val upgrade: String,
    val preview: MealPrepWeek?,
    val shoppingList: List<MealPrepShoppingItem>,
  ) : MealPrepUiState
  data class Error(val message: String) : MealPrepUiState
}

data class MealPrepInput(
  val dailyCalories: Int = 2200,
  val dailyProtein:  Int = 140,
  val servingsPerDay: Int = 3,
  val recipesPerWeek: Int = 5,
  val weeks: Int = 2,
)

@HiltViewModel
class MealPrepViewModel @Inject constructor(
  private val api: PantrieApi,
  private val analytics: Analytics,
) : ViewModel() {

  private val _input = MutableStateFlow(MealPrepInput())
  val input = _input.asStateFlow()

  private val _state = MutableStateFlow<MealPrepUiState>(MealPrepUiState.Idle)
  val state = _state.asStateFlow()

  private val _bannerMessage = MutableStateFlow<String?>(null)
  val bannerMessage = _bannerMessage.asStateFlow()

  fun update(transform: (MealPrepInput) -> MealPrepInput) {
    _input.value = transform(_input.value)
  }

  fun clearBanner() { _bannerMessage.value = null }

  fun generate() {
    val cur = _input.value
    _state.value = MealPrepUiState.Loading
    analytics.track(
      "mealprep_generated",
      mapOf(
        "target_calories" to cur.dailyCalories,
        "target_protein" to cur.dailyProtein,
        "servings_per_day" to cur.servingsPerDay,
        "recipes_per_week" to cur.recipesPerWeek,
        "weeks" to cur.weeks,
      ),
    )
    viewModelScope.launch {
      runCatching {
        api.mealPrep(
          MealPrepRequest(
            targetCalories = cur.dailyCalories.toDouble(),
            targetProtein = cur.dailyProtein.toDouble(),
            servingsPerDay = cur.servingsPerDay,
            recipesPerWeek = cur.recipesPerWeek,
            weeks = cur.weeks,
          ),
        )
      }.onSuccess { resp ->
        _state.value = renderResponse(resp)
      }.onFailure { t ->
        val paywall = extractPaywall(t)
        if (paywall != null) {
          _state.value = MealPrepUiState.PaywallPreview(
            upgrade = paywall.upgrade ?: "Upgrade to ${app.pantrie.Brand.PRO_NAME} for full meal-prep planning.",
            preview = paywall.preview,
            shoppingList = paywall.shoppingList,
          )
        } else {
          _state.value = MealPrepUiState.Error(t.message ?: "Failed to generate plan")
        }
      }
    }
  }

  fun addAllToShop() {
    val ready = _state.value as? MealPrepUiState.Ready ?: return
    if (ready.shoppingList.isEmpty()) return
    viewModelScope.launch {
      var added = 0
      for (item in ready.shoppingList) {
        runCatching {
          api.addShopping(
            ShoppingAddRequest(
              name = item.name.take(80),
              quantity = item.quantity,
              unit = item.unit,
              aisle = item.aisle,
              source = "mealprep",
            ),
          )
        }.onSuccess { added++ }
      }
      analytics.track("mealprep_shop_added", mapOf("count" to added))
      _bannerMessage.value = "Added $added item${if (added == 1) "" else "s"} to shopping list"
    }
  }

  fun reset() { _state.value = MealPrepUiState.Idle }

  private fun renderResponse(resp: MealPrepResponse): MealPrepUiState {
    if (resp.ok == false && resp.tier == "free") {
      return MealPrepUiState.PaywallPreview(
        upgrade = resp.upgrade ?: "Upgrade to ${app.pantrie.Brand.PRO_NAME}.",
        preview = resp.preview,
        shoppingList = resp.shoppingList,
      )
    }
    return MealPrepUiState.Ready(
      tier = resp.tier ?: "pro",
      weeks = resp.weeks,
      shoppingList = resp.shoppingList,
    )
  }

  // 402 Payment Required comes back as an HttpException with the full JSON body.
  // Parse it so we can render the preview card above the paywall.
  private fun extractPaywall(t: Throwable): MealPrepResponse? {
    val http = t as? HttpException ?: return null
    if (http.code() != 402) return null
    val body = http.response()?.errorBody()?.string().orEmpty()
    return runCatching {
      Json { ignoreUnknownKeys = true; coerceInputValues = true }
        .decodeFromString(MealPrepResponse.serializer(), body)
    }.getOrNull()
  }
}

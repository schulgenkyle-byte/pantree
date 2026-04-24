package app.pantrie.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.feature.beta.Analytics
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.PutPreferencesRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Three-step onboarding quiz state.
 * Held entirely in memory: once the user hits "Done" we PUT /me/preferences with onboarded=true.
 */
data class OnboardingState(
  val step: Int = 0,                       // 0 cuisines, 1 diet/allergens, 2 heat/adventure
  val cuisines: Set<String> = emptySet(),
  val avoidText: String = "",              // comma- or newline-separated
  val diet: String = "none",
  val allergens: Set<String> = emptySet(),
  val heat: Int = 1,
  val adventure: Int = 1,
  val saving: Boolean = false,
  val errorMessage: String? = null,
  val done: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
  private val api: PantrieApi,
  private val analytics: Analytics,
) : ViewModel() {
  private val _state = MutableStateFlow(OnboardingState())
  val state = _state.asStateFlow()

  fun toggleCuisine(c: String) {
    val cur = _state.value.cuisines
    _state.value = _state.value.copy(
      cuisines = if (cur.contains(c)) cur - c else cur + c
    )
  }

  fun setAvoidText(v: String) { _state.value = _state.value.copy(avoidText = v) }
  fun setDiet(d: String)      { _state.value = _state.value.copy(diet = d) }

  fun toggleAllergen(a: String) {
    val cur = _state.value.allergens
    _state.value = _state.value.copy(
      allergens = if (cur.contains(a)) cur - a else cur + a
    )
  }

  fun setHeat(h: Int)      { _state.value = _state.value.copy(heat = h.coerceIn(0, 3)) }
  fun setAdventure(a: Int) { _state.value = _state.value.copy(adventure = a.coerceIn(0, 3)) }

  fun next() { _state.value = _state.value.copy(step = (_state.value.step + 1).coerceAtMost(2)) }
  fun back() { _state.value = _state.value.copy(step = (_state.value.step - 1).coerceAtLeast(0)) }

  fun submit() {
    if (_state.value.saving) return
    val s = _state.value
    _state.value = s.copy(saving = true, errorMessage = null)
    viewModelScope.launch {
      val avoidList = s.avoidText.split(',', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(40)
      val req = PutPreferencesRequest(
        cuisines = s.cuisines.toList(),
        avoid = avoidList,
        diet = s.diet,
        allergens = s.allergens.toList(),
        heat = s.heat,
        adventure = s.adventure,
        onboarded = true,
      )
      val putResult = runCatching { api.putPreferences(req) }
      // The PUT may have succeeded server-side but OkHttp can close the connection
      // mid-response on flaky mobile data → surface as a failure to the caller with
      // message "Closed". Verify with a follow-up GET: if `onboarded` came back true,
      // treat it as success.
      val ok = putResult.isSuccess || runCatching {
        api.getPreferences().onboarded
      }.getOrDefault(false)
      if (ok) {
        analytics.track(
          "onboarding_completed",
          mapOf(
            "cuisines" to s.cuisines.size,
            "allergens" to s.allergens.size,
            "avoid" to avoidList.size,
            "diet" to s.diet,
            "heat" to s.heat,
            "adventure" to s.adventure,
          ),
        )
        _state.value = _state.value.copy(saving = false, done = true)
      } else {
        val e = putResult.exceptionOrNull()
        _state.value = _state.value.copy(
          saving = false,
          errorMessage = e?.message?.take(120) ?: "Couldn't save — check connection and try again",
        )
      }
    }
  }
}

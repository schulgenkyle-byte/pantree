package app.pantrie.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.feature.beta.Analytics
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.PreferencesDto
import app.pantrie.network.dto.PutPreferencesRequest
import app.pantrie.network.dto.TasteProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
  val loading: Boolean = true,
  val saving: Boolean = false,
  val cuisines: Set<String> = emptySet(),
  val avoidText: String = "",
  val diet: String = "none",
  val allergens: Set<String> = emptySet(),
  val heat: Int = 1,
  val adventure: Int = 1,
  val taste: TasteProfile? = null,
  val savedFlash: Boolean = false,          // transient confirmation for a second
  val errorMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
  private val api: PantrieApi,
  private val analytics: Analytics,
  private val localSettings: LocalSettingsStore,
) : ViewModel() {
  private val _state = MutableStateFlow(SettingsState())
  val state = _state.asStateFlow()

  /** Exposed so the Settings screen can render the Mixology toggle row. */
  val showMixology = localSettings.showMixology

  fun setShowMixology(on: Boolean) {
    localSettings.setShowMixology(on)
    analytics.track("mixology_toggle", mapOf("on" to on))
  }

  init { load() }

  fun load() {
    _state.value = _state.value.copy(loading = true, errorMessage = null)
    viewModelScope.launch {
      val prefsResult  = runCatching { api.getPreferences() }
      val tasteResult  = runCatching { api.getTaste() }
      val prefs = prefsResult.getOrNull()
      _state.value = _state.value.copy(
        loading = false,
        cuisines = prefs?.cuisines?.toSet().orEmpty(),
        avoidText = prefs?.avoid?.joinToString(", ").orEmpty(),
        diet = prefs?.diet ?: "none",
        allergens = prefs?.allergens?.toSet().orEmpty(),
        heat = prefs?.heat ?: 1,
        adventure = prefs?.adventure ?: 1,
        taste = tasteResult.getOrNull(),
        errorMessage = prefsResult.exceptionOrNull()?.message,
      )
    }
  }

  fun toggleCuisine(c: String)  {
    val cur = _state.value.cuisines
    _state.value = _state.value.copy(cuisines = if (cur.contains(c)) cur - c else cur + c)
  }
  fun setAvoidText(v: String) { _state.value = _state.value.copy(avoidText = v) }
  fun setDiet(d: String)      { _state.value = _state.value.copy(diet = d) }
  fun toggleAllergen(a: String) {
    val cur = _state.value.allergens
    _state.value = _state.value.copy(allergens = if (cur.contains(a)) cur - a else cur + a)
  }
  fun setHeat(h: Int)      { _state.value = _state.value.copy(heat = h.coerceIn(0, 3)) }
  fun setAdventure(a: Int) { _state.value = _state.value.copy(adventure = a.coerceIn(0, 3)) }

  fun save() {
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
      runCatching { api.putPreferences(req) }
        .onSuccess {
          analytics.track("preferences_saved")
          _state.value = _state.value.copy(saving = false, savedFlash = true)
        }
        .onFailure { e ->
          _state.value = _state.value.copy(saving = false, errorMessage = e.message ?: "Couldn't save")
        }
    }
  }

  fun clearSavedFlash() { _state.value = _state.value.copy(savedFlash = false) }

  /** Mark onboarded=false and save so MainActivity routes back into the wizard. */
  fun redoOnboarding(onDone: () -> Unit) {
    viewModelScope.launch {
      val s = _state.value
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
        onboarded = false,
      )
      runCatching { api.putPreferences(req) }
      onDone()
    }
  }

  /** Helper to preview stored prefs without UI. */
  fun snapshot(): PreferencesDto = PreferencesDto(
    cuisines = _state.value.cuisines.toList(),
    avoid = _state.value.avoidText.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    diet = _state.value.diet,
    allergens = _state.value.allergens.toList(),
    heat = _state.value.heat,
    adventure = _state.value.adventure,
    onboarded = true,
    updatedAt = 0L,
  )
}

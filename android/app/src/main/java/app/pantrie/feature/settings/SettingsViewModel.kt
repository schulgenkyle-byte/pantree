package app.pantrie.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.billing.EntitlementRepository
import app.pantrie.billing.SwipeQuotaRepository
import app.pantrie.feature.beta.Analytics
import app.pantrie.feature.walkthrough.TourRepository
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
  private val tourRepo: TourRepository,
  private val swipeQuota: SwipeQuotaRepository,
  private val entitlement: EntitlementRepository,
) : ViewModel() {

  /** Live Pro state for the debug Settings toggle. */
  val isPro = entitlement.isPro

  /** Debug-only: reset today's swipe count to zero so the Tonight deck refills.
   *  Wired to a Settings button visible only on BuildConfig.DEBUG. */
  fun resetSwipeQuotaForTesting() {
    viewModelScope.launch { swipeQuota.resetForTesting() }
  }

  /** Debug-only: flip Pro on/off via the dev-gated server endpoint + local cache. */
  fun debugTogglePro(grant: Boolean) {
    viewModelScope.launch {
      if (grant) entitlement.debugGrantPro() else entitlement.debugRevokePro()
    }
  }
  private val _state = MutableStateFlow(SettingsState())
  val state = _state.asStateFlow()

  /** Exposed so the Settings screen can render the Mixology toggle row. */
  val showMixology = localSettings.showMixology

  fun setShowMixology(on: Boolean) {
    localSettings.setShowMixology(on)
    analytics.track("mixology_toggle", mapOf("on" to on))
  }

  /** Animation toggles in the new Features section. */
  val libraryAnimEnabled = localSettings.libraryAnimEnabled
  val ingredientFallEnabled = localSettings.ingredientFallEnabled

  fun setLibraryAnimEnabled(on: Boolean) {
    localSettings.setLibraryAnimEnabled(on)
    analytics.track("library_anim_toggle", mapOf("on" to on))
  }

  fun setIngredientFallEnabled(on: Boolean) {
    localSettings.setIngredientFallEnabled(on)
    analytics.track("ingredient_fall_toggle", mapOf("on" to on))
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

  /**
   * Reset the first-launch tour flag so the walkthrough overlay re-triggers on the
   * next eligible screen render. Settings calls this from the "Show app tour again"
   * row; the actual UI re-show is owned by [WalkthroughViewModel] which listens to
   * [TourRepository.completed] and flips visibility itself.
   */
  fun replayTour() {
    analytics.track("tour_replay_requested")
    viewModelScope.launch { tourRepo.reset() }
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

package app.pantrie.feature.settings

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-local settings store for non-sensitive, per-install toggles.
 * Lives separately from the server-synced preferences in [SettingsViewModel] —
 * these are UI shape flags (which tabs to show, local notes, etc.) that don't
 * need to round-trip to the backend.
 *
 * Currently hosts:
 *   - showMixology — reveals the cocktail deck tab (hidden by default; 21+).
 *   - mixology notes, keyed by recipe_id (Page 4 of the flipped card back).
 */
@Singleton
class LocalSettingsStore @Inject constructor(@ApplicationContext ctx: Context) {
  private val prefs: SharedPreferences =
    ctx.getSharedPreferences("pantrie_local_settings", Context.MODE_PRIVATE)

  private val _showMixology = MutableStateFlow(prefs.getBoolean(KEY_SHOW_MIXOLOGY, false))
  val showMixology: StateFlow<Boolean> = _showMixology.asStateFlow()

  /**
   * Age-gate confirmation. Required by Play Store regulated-goods policy because
   * Speakeater includes alcoholic-cocktail content. Set to true only after the
   * user confirms they are 21+ on first launch via [AgeGateScreen].
   * Persists across reinstalls within the device's SharedPreferences scope.
   */
  private val _hasConfirmedAge = MutableStateFlow(prefs.getBoolean(KEY_HAS_CONFIRMED_AGE, false))
  val hasConfirmedAge: StateFlow<Boolean> = _hasConfirmedAge.asStateFlow()

  // Animation toggles. Default ON because they're the marquee feedback for
  // "your save landed somewhere." Users who hate motion can flip them off in
  // Settings > Features. Both default true; user explicitly opts out.
  private val _libraryAnimEnabled = MutableStateFlow(prefs.getBoolean(KEY_LIB_ANIM, true))
  val libraryAnimEnabled: StateFlow<Boolean> = _libraryAnimEnabled.asStateFlow()

  private val _ingredientFallEnabled = MutableStateFlow(prefs.getBoolean(KEY_FALL_ANIM, true))
  val ingredientFallEnabled: StateFlow<Boolean> = _ingredientFallEnabled.asStateFlow()

  fun setShowMixology(on: Boolean) {
    prefs.edit().putBoolean(KEY_SHOW_MIXOLOGY, on).apply()
    _showMixology.value = on
  }

  fun setHasConfirmedAge(on: Boolean) {
    prefs.edit().putBoolean(KEY_HAS_CONFIRMED_AGE, on).apply()
    _hasConfirmedAge.value = on
  }

  fun setLibraryAnimEnabled(on: Boolean) {
    prefs.edit().putBoolean(KEY_LIB_ANIM, on).apply()
    _libraryAnimEnabled.value = on
  }

  fun setIngredientFallEnabled(on: Boolean) {
    prefs.edit().putBoolean(KEY_FALL_ANIM, on).apply()
    _ingredientFallEnabled.value = on
  }

  fun getMixologyNote(recipeId: String): String =
    prefs.getString("$KEY_NOTE_PREFIX$recipeId", "") ?: ""

  fun setMixologyNote(recipeId: String, note: String) {
    prefs.edit().putString("$KEY_NOTE_PREFIX$recipeId", note).apply()
  }

  private companion object {
    const val KEY_SHOW_MIXOLOGY = "show_mixology"
    const val KEY_HAS_CONFIRMED_AGE = "has_confirmed_age"
    const val KEY_NOTE_PREFIX = "mix_note_"
    const val KEY_LIB_ANIM = "library_anim_enabled"
    const val KEY_FALL_ANIM = "ingredient_fall_enabled"
  }
}

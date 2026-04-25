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

  fun setShowMixology(on: Boolean) {
    prefs.edit().putBoolean(KEY_SHOW_MIXOLOGY, on).apply()
    _showMixology.value = on
  }

  fun getMixologyNote(recipeId: String): String =
    prefs.getString("$KEY_NOTE_PREFIX$recipeId", "") ?: ""

  fun setMixologyNote(recipeId: String, note: String) {
    prefs.edit().putString("$KEY_NOTE_PREFIX$recipeId", note).apply()
  }

  private companion object {
    const val KEY_SHOW_MIXOLOGY = "show_mixology"
    const val KEY_NOTE_PREFIX = "mix_note_"
  }
}

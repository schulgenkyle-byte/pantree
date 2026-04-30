package app.pantrie.feature.walkthrough

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

/**
 * Tracks whether the user has completed (or skipped) the first-launch guided tour.
 *
 * Mirrors the EntitlementRepository pattern — read once on cold boot, keep an in-memory
 * StateFlow, and write through to DataStore whenever the value changes. We piggy-back on
 * the same DataStore<Preferences> that EntitlementModule already provides so we don't
 * have to add a second Hilt binding for the same type.
 *
 * Three states the UI cares about:
 *   - hydrated == false        : DataStore hasn't reported yet, render nothing
 *   - completed == false       : show the walkthrough overlay on the next eligible screen
 *   - completed == true        : stay out of the way until the user explicitly resets
 *
 * Mini-tour completion is tracked separately via [completedMiniTours] — a Set<String> of
 * mini-tour ids the user has finished. Reset along with [completed] when the user replays
 * the tour from Settings so the Hub starts clean.
 */
@Singleton
class TourRepository @Inject constructor(
  private val dataStore: DataStore<Preferences>,
) {
  private val _completed = MutableStateFlow(false)
  val completed: StateFlow<Boolean> = _completed.asStateFlow()

  private val _hydrated = MutableStateFlow(false)
  val hydrated: StateFlow<Boolean> = _hydrated.asStateFlow()

  private val _completedMiniTours = MutableStateFlow<Set<String>>(emptySet())
  val completedMiniTours: StateFlow<Set<String>> = _completedMiniTours.asStateFlow()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  init {
    scope.launch {
      runCatching {
        val cached = dataStore.data.map { prefs ->
          val done = prefs[KEY_TOUR_COMPLETED] ?: false
          val mini = prefs[KEY_MINI_TOURS_COMPLETED] ?: emptySet()
          done to mini
        }.first()
        _completed.value = cached.first
        _completedMiniTours.value = cached.second
      }
      _hydrated.value = true
    }
  }

  /** Persist completion. Called when the user finishes the final step OR taps Skip. */
  suspend fun markCompleted() {
    _completed.value = true
    runCatching { dataStore.edit { it[KEY_TOUR_COMPLETED] = true } }
  }

  /** Wipe both the tour-completed flag AND mini-tour completions so a Settings replay
   *  shows a clean Hub with no checkmarks. */
  suspend fun reset() {
    _completed.value = false
    _completedMiniTours.value = emptySet()
    runCatching {
      dataStore.edit {
        it[KEY_TOUR_COMPLETED] = false
        it[KEY_MINI_TOURS_COMPLETED] = emptySet()
      }
    }
  }

  /** Mark a single mini-tour as done. Idempotent — adding the same id twice is a no-op. */
  suspend fun markMiniTourCompleted(id: String) {
    val updated = _completedMiniTours.value + id
    _completedMiniTours.value = updated
    runCatching { dataStore.edit { it[KEY_MINI_TOURS_COMPLETED] = updated } }
  }

  companion object {
    private val KEY_TOUR_COMPLETED = booleanPreferencesKey("tour_completed_v1")
    private val KEY_MINI_TOURS_COMPLETED = stringSetPreferencesKey("tour_mini_completed_v1")
  }
}

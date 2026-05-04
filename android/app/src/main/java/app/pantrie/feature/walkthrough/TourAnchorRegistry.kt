package app.pantrie.feature.walkthrough

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anchor keys reported by tour-target Composables. The string values are stable across
 * builds because they're persisted through analytics and (potentially) future on-disk
 * tour state — change them and you orphan past tour steps.
 */
internal object TourAnchors {
  const val NAV_CULINARY = "nav_culinary"
  const val NAV_FEED = "nav_feed"
  const val NAV_YOU = "nav_you"
  const val NAV_MIXOLOGY = "nav_mixology"

  // Quick-action tile on DeckScreen that opens the pantry. Main tour step 1 + the
  // pantry-photo mini-tour spotlight this.
  const val DECK_PANTRY_TILE = "deck_pantry_tile"

  // The visible swipeable card area on DeckScreen. Main tour step 4 spotlights this so
  // the user knows EXACTLY what to swipe — pointing at the Culinary tab for "swipe right
  // to save" was confusing because the tab isn't where the swipe happens.
  const val DECK_CARD_AREA = "deck_card_area"

  // Edge-to-edge allergen banner across the top of any card whose ingredients
  // match the user's saved allergens. Allergen mini-tour anchors here so the
  // first spotlight points at the actual warning the user just learned to read.
  const val DECK_ALLERGEN_BANNER = "deck_allergen_banner"

  // Quick-action tiles on DeckScreen for shopping / plan. Used by the shopping +
  // plan mini-tours respectively.
  const val DECK_SHOPPING_TILE = "deck_shopping_tile"
  const val DECK_PLAN_TILE = "deck_plan_tile"

  // Big "Snap your shelves" button on PantryScreen. Main tour step 2 + pantry-photo
  // mini-tour both spotlight this.
  const val PANTRY_SCAN_BUTTON = "pantry_scan_button"

  // Settings → Mixology toggle row. Mini-tour: cocktails step 2 spotlights this
  // so the user knows where to flip the switch that reveals the cocktail tab.
  const val SETTINGS_MIXOLOGY_TOGGLE = "settings_mixology_toggle"

  // Settings → "My submissions" outlined button. Mini-tour: add-recipe step 2.
  const val SETTINGS_SUBMISSIONS_BUTTON = "settings_submissions_button"

  // BOOTLEGGER ↔ MIXOLOGIST segmented control on MixologyScreen. Mini-tour: cocktails
  // step 4 spotlights this to teach the sub-mode switch.
  const val MIXOLOGY_MODE_TOGGLE = "mixology_mode_toggle"

  // Top-right Share icon on ShoppingScreen. Mini-tour: shopping step 2.
  const val SHOPPING_SHARE_BUTTON = "shopping_share_button"

  // "Plan it for me" Generate button on PlanScreen (Empty state). Mini-tour: plan step 2.
  const val PLAN_GENERATE_BUTTON = "plan_generate_button"

  // "Open Library" / new-book affordance on SavedScreen. Mini-tour: library step 2.
  const val SAVED_LIBRARY_BUTTON = "saved_library_button"
}

/**
 * Live registry mapping tour anchor keys → measured composable bounds (window coords).
 *
 * Composables that are tour targets call [report] from `Modifier.onGloballyPositioned`
 * with their `coords.boundsInWindow()`. The walkthrough overlay reads the current step's
 * key from this registry and draws the spotlight at the exact center + radius of the
 * reported Rect. If a key has not been reported yet (composable not mounted) the overlay
 * falls back to a full-sheet card for that step instead of misaligning the spotlight.
 *
 * Singleton-scoped so the registry survives recomposition and is shared between the
 * NavigationBar items reporting anchors and the WalkthroughOverlay reading them. The
 * MutableStateFlow drives recomposition on the overlay side when bounds change (e.g.
 * device rotation, nav bar resize from system insets).
 */
@Singleton
class TourAnchorRegistry @Inject constructor() {
  private val _anchors = MutableStateFlow<Map<String, Rect>>(emptyMap())
  val anchors: StateFlow<Map<String, Rect>> = _anchors.asStateFlow()

  /**
   * Report (or update) the bounds for [key]. Safe to call from `onGloballyPositioned`
   * on every layout pass — we no-op when the value hasn't changed to avoid emitting
   * spurious StateFlow updates that would re-trigger overlay recomposition.
   */
  fun report(key: String, bounds: Rect) {
    _anchors.update { current ->
      if (current[key] == bounds) current else current + (key to bounds)
    }
  }

  /** Forget [key]. Called when a tour target leaves composition (e.g. tab swap). */
  fun clear(key: String) {
    _anchors.update { current ->
      if (key !in current) current else current - key
    }
  }

  fun get(key: String): Rect? = _anchors.value[key]
}

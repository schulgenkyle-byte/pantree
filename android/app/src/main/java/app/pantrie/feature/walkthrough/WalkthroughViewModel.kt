package app.pantrie.feature.walkthrough

import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.data.entities.PantryDao
import app.pantrie.feature.app.RefreshBus
import app.pantrie.feature.beta.Analytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Snapshot UI state for the walkthrough overlay. The overlay reads only this object so
 * adding new fields here doesn't require plumbing each one through onComposable args.
 */
internal data class WalkthroughUiState(
  val visible: Boolean = false,
  val segment: TourSegment = TourSteps.mainTour,
  val stepIndex: Int = 0,
  val completedMiniTours: Set<String> = emptySet(),
)

/**
 * Owns the visible-state of the GUIDED walkthrough.
 *
 * The tour is now structured as one main linear segment (steps 0..4 + a Hub at step 5)
 * plus five opt-in mini-tours fanning out from the Hub. The user lands on the Hub after
 * saving their first recipe, picks a mini-tour or "Self explore", and either way the
 * VM owns transitioning between segments.
 *
 * State sources:
 *   - currentRoute: pushed in from MainActivity via [reportCurrentRoute]
 *   - pantryCount:  observed directly off [PantryDao.observeAll]
 *   - savedSinceTourStarted: counted off [RefreshBus.recipeSaved] from the moment a
 *     fresh tour opens
 *
 * Segment & step-within-segment are tracked separately so that finishing a mini-tour
 * pops back to the Hub instead of leaving the global step index pointing past the end
 * of one segment but not the start of another.
 */
@HiltViewModel
class WalkthroughViewModel @Inject constructor(
  private val repo: TourRepository,
  private val analytics: Analytics,
  private val anchorRegistry: TourAnchorRegistry,
  private val pantryDao: PantryDao,
  private val refreshBus: RefreshBus,
) : ViewModel() {

  // Internal segment + step. The overlay reads [uiState] (a derived StateFlow) so it
  // doesn't have to know about TourSegment internals.
  private val _segment = MutableStateFlow<TourSegment>(TourSteps.mainTour)
  private val _step = MutableStateFlow(0)
  private val _visible = MutableStateFlow(false)

  /** Combined snapshot for the overlay. */
  internal val uiState: StateFlow<WalkthroughUiState> =
    combine(_visible, _segment, _step, repo.completedMiniTours) { vis, seg, step, mini ->
      WalkthroughUiState(visible = vis, segment = seg, stepIndex = step, completedMiniTours = mini)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WalkthroughUiState())

  // Backwards-compat slices the existing call sites (MainActivity, etc.) still read.
  val visible: StateFlow<Boolean> = _visible.asStateFlow()
  val step: StateFlow<Int> = _step.asStateFlow()

  /** Live map of anchor key → measured Composable bounds (window coords). */
  val anchors: StateFlow<Map<String, Rect>> = anchorRegistry.anchors

  // Live current-route, pushed in from MainActivity. RouteIs / RouteIn triggers read this.
  private val _currentRoute = MutableStateFlow<String?>(null)

  // Live pantry-item count from Room — drives PantryCountAtLeast trigger.
  private val pantryCount: StateFlow<Int> =
    pantryDao.observeAll()
      .map { it.size }
      .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

  // Saves since this tour opened — incremented each time RefreshBus emits recipeSaved
  // while the overlay is visible.
  private val _savedSinceTourStarted = MutableStateFlow(0)

  // One-shot navigation requests — MainActivity collects these and calls nav.navigate().
  // Used both at tour-start (plant on /deck) and during mini-tours (plant on /settings,
  // /mixology, etc. so the spotlight has a target).
  private val _navigationRequests = Channel<String>(Channel.BUFFERED)
  val navigationRequests: Flow<String> = _navigationRequests.receiveAsFlow()

  init {
    // Two reasons to open the overlay:
    //   1. First launch — repo hydrates with completed == false.
    //   2. Settings → "Show app tour again" — sets completed back to false at runtime.
    viewModelScope.launch {
      repo.completed.collect { done ->
        if (repo.hydrated.value && !done) {
          if (!_visible.value) {
            analytics.track("tour_opened")
            _navigationRequests.trySend("deck")
          }
          _segment.value = TourSteps.mainTour
          _step.value = 0
          _savedSinceTourStarted.value = 0
          _visible.value = true
        }
      }
    }
    viewModelScope.launch {
      repo.hydrated.collect { ready ->
        if (ready && !repo.completed.value && !_visible.value) {
          _segment.value = TourSteps.mainTour
          _visible.value = true
          _savedSinceTourStarted.value = 0
          _navigationRequests.trySend("deck")
          analytics.track("tour_shown_first_launch")
        }
      }
    }

    // Count recipe-saves while the tour is visible. Outside the tour the increments
    // are harmless because the SaveCountAtLeast trigger only fires when the relevant
    // step is current.
    viewModelScope.launch {
      refreshBus.recipeSaved.collect {
        if (_visible.value) _savedSinceTourStarted.value = _savedSinceTourStarted.value + 1
      }
    }

    // Trigger watcher: any time the route, pantry count, save count, segment, or step
    // changes, re-evaluate whether the current step's trigger has fired. Auto-advance
    // if so. Reads the CURRENT segment's steps list, not a global one — so a mini-tour
    // step's RouteIs("settings") only matches when the user is in the mini-tour.
    //
    // kotlinx.coroutines combine() typed-overload caps at 5 flows; we pre-combine the
    // (route, pantry, saves) triplet then merge with the (segment, step, visible) triple
    // so the outer combine stays inside the supported arity.
    viewModelScope.launch {
      val sources = combine(_currentRoute, pantryCount, _savedSinceTourStarted) { r, p, s ->
        Triple(r, p, s)
      }
      combine(_segment, _step, _visible, sources) { seg, stepIdx, vis, src ->
        TriggerEval(seg, stepIdx, vis, src.first, src.second, src.third)
      }.collectLatest { eval ->
        if (!eval.visible) return@collectLatest
        val s = eval.segment.steps.getOrNull(eval.stepIdx) ?: return@collectLatest
        val fired = when (val t = s.trigger) {
          TourTrigger.None -> false
          TourTrigger.TooltipTap -> false
          is TourTrigger.RouteIs -> eval.route == t.route
          is TourTrigger.RouteIn -> eval.route in t.routes
          is TourTrigger.PantryCountAtLeast -> eval.pantry >= t.n
          is TourTrigger.SaveCountAtLeast -> eval.saves >= t.n
        }
        if (fired) {
          analytics.track(
            "tour_step_auto_advance",
            mapOf(
              "segment" to eval.segment.id,
              "from_step" to eval.stepIdx,
              "trigger" to s.trigger::class.simpleName.orEmpty(),
            ),
          )
          advance()
        }
      }
    }

    // Whenever the segment OR step changes, request navigation to the step's
    // requiredRoute (if any). This is what plants the user on /settings when they pick
    // the "add recipe" mini-tour, on /pantry when they pick pantry-photo, etc.
    viewModelScope.launch {
      combine(_segment, _step, _visible) { seg, idx, vis -> Triple(seg, idx, vis) }
        .collectLatest { (seg, idx, vis) ->
          if (!vis) return@collectLatest
          val step = seg.steps.getOrNull(idx) ?: return@collectLatest
          val route = step.requiredRoute ?: return@collectLatest
          if (_currentRoute.value != route) {
            _navigationRequests.trySend(route)
          }
        }
    }

    // Pantry-photo mini-tour shares its goal with main tour step 2. If the user already
    // has pantry items by the time they hit the Hub, auto-mark that mini-tour completed
    // so the Hub shows ✓ instead of nudging them to redo something they did 30 seconds
    // ago. Fires only when entering the Hub specifically (not on every state change).
    viewModelScope.launch {
      combine(_segment, _step, _visible, pantryCount, repo.completedMiniTours) { seg, idx, vis, pantry, done ->
        AutoCompleteEval(seg, idx, vis, pantry, done)
      }.collectLatest { eval ->
        if (!eval.visible) return@collectLatest
        if (eval.segment !is MainTour) return@collectLatest
        val step = eval.segment.steps.getOrNull(eval.stepIdx) ?: return@collectLatest
        if (step.kind != TourStepKind.Hub) return@collectLatest
        if (eval.pantry >= 1 && TourSteps.MINI_PANTRY_PHOTO !in eval.completedMiniTours) {
          repo.markMiniTourCompleted(TourSteps.MINI_PANTRY_PHOTO)
          analytics.track("tour_mini_auto_completed", mapOf("id" to TourSteps.MINI_PANTRY_PHOTO))
        }
      }
    }
  }

  /**
   * Record the measured bounds of a tour-target Composable. Call from
   * `Modifier.onGloballyPositioned { coords -> reportAnchor(KEY, coords.boundsInWindow()) }`.
   */
  fun reportAnchor(key: String, bounds: Rect) = anchorRegistry.report(key, bounds)

  /**
   * Push the current nav route into the tour state machine. MainActivity calls this
   * from a LaunchedEffect on currentBackStackEntryAsState.
   */
  fun reportCurrentRoute(route: String?) {
    _currentRoute.value = route
  }

  /** User-driven advance — used by FullSheet primary buttons and TooltipTap fallback. */
  fun next() = advance()

  private fun advance() {
    val seg = _segment.value
    val current = _step.value
    val last = seg.steps.lastIndex
    if (current >= last) {
      // End of segment. Behavior depends on which segment.
      when (seg) {
        is MainTour -> {
          // Past the Hub step is impossible — Hub never auto-advances. If we somehow
          // got here treat it as a "self explore" exit.
          finish(reason = "main_done")
        }
        is MiniTour -> {
          // Mini-tour wrapped — mark completed + return to Hub.
          markMiniTourCompleted(seg.id)
        }
      }
    } else {
      _step.value = current + 1
      analytics.track(
        "tour_step",
        mapOf("segment" to seg.id, "step" to (current + 1)),
      )
    }
  }

  /**
   * Hub button → enter the named mini-tour. Resets step index, segment swap. The
   * combine() above will request navigation to the first step's requiredRoute.
   */
  fun enterMiniTour(id: String) {
    val mini = TourSteps.miniTourById(id) ?: return
    analytics.track("tour_mini_entered", mapOf("id" to id))
    _segment.value = mini
    _step.value = 0
  }

  /**
   * Pop back to the Hub (the last step of the main tour). Used both when a mini-tour
   * finishes and when the user taps a "back to hub" affordance during one.
   */
  fun exitToHub() {
    _segment.value = TourSteps.mainTour
    _step.value = TourSteps.mainTour.steps.lastIndex
    analytics.track("tour_returned_to_hub")
  }

  /**
   * Hub button → "Self explore". Finishes the entire tour. User can replay from
   * Settings → Show app tour again.
   */
  fun selfExplore() {
    analytics.track("tour_self_explore")
    finish(reason = "self_explore")
  }

  /** Mark a mini-tour completed in DataStore + return to Hub. */
  fun markMiniTourCompleted(id: String) {
    viewModelScope.launch {
      repo.markMiniTourCompleted(id)
      analytics.track("tour_mini_completed", mapOf("id" to id))
      // After persistence, pop back to the Hub. The Hub re-renders with the new ✓.
      exitToHub()
    }
  }

  fun skip() = finish(reason = "skipped")

  fun finish(reason: String) {
    analytics.track(
      "tour_finished",
      mapOf("reason" to reason, "segment" to _segment.value.id, "last_step" to _step.value),
    )
    _visible.value = false
    _segment.value = TourSteps.mainTour
    _step.value = 0
    viewModelScope.launch { repo.markCompleted() }
  }

  /**
   * Settings entry point — wipes the DataStore flag (and mini-tour completions). The
   * [TourRepository.completed] flow transitions back to false and our collector flips
   * visibility to true with a clean Hub.
   */
  fun restart() {
    analytics.track("tour_restarted")
    viewModelScope.launch { repo.reset() }
  }

  // 6-tuple holder for the trigger-evaluation combine() — Kotlin's Triple stops at 3.
  private data class TriggerEval(
    val segment: TourSegment,
    val stepIdx: Int,
    val visible: Boolean,
    val route: String?,
    val pantry: Int,
    val saves: Int,
  )

  private data class AutoCompleteEval(
    val segment: TourSegment,
    val stepIdx: Int,
    val visible: Boolean,
    val pantry: Int,
    val completedMiniTours: Set<String>,
  )
}

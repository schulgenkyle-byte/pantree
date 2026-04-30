@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.pantrie.feature.deck

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SoupKitchen
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.DeckResponse
import app.pantrie.network.dto.Ingredient
import app.pantrie.network.dto.InteractRequest
import app.pantrie.network.dto.Recipe
import app.pantrie.network.dto.UndoCookRequest
import app.pantrie.ui.IngredientImageOrEmoji
import app.pantrie.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import javax.inject.Inject

private const val SWIPE_THRESHOLD_DP = 110f

/** One-shot UI events emitted after a swipe consumes a quota slot. */
sealed interface SwipeOutcomeEvent {
  data object ShowAd : SwipeOutcomeEvent
  data object Wall : SwipeOutcomeEvent
}

/**
 * All Tonight cards now sit on the editorial dark surface (Paper2). The previous palette of
 * pastel cuisine tints was the loudest "Phase 1 isn't fully dark yet" tell on the home screen,
 * so the per-cuisine differentiation moved to the photo + text. tintFor() is preserved as a
 * single-color helper in case Phase 3 wants to re-introduce tonal accents on the depth cards.
 */
@Suppress("UNUSED_PARAMETER")
private fun tintFor(cuisine: String?): Color = Paper2

data class ToastState(val message: String, val undoId: String? = null, val undoAction: (() -> Unit)? = null)

@HiltViewModel
class DeckViewModel @Inject constructor(
  private val api: PantrieApi,
  private val analytics: app.pantrie.feature.beta.Analytics,
  private val refreshBus: app.pantrie.feature.app.RefreshBus,
  private val quota: app.pantrie.billing.SwipeQuotaRepository,
  private val entitlement: app.pantrie.billing.EntitlementRepository,
  private val localSettings: app.pantrie.feature.settings.LocalSettingsStore,
) : ViewModel() {

  /** Animation toggles, hoisted from LocalSettingsStore so DeckScreen can read
   *  without injecting another dependency at the screen level. */
  val libraryAnimEnabled = localSettings.libraryAnimEnabled
  val ingredientFallEnabled = localSettings.ingredientFallEnabled

  /** Pro users skip all quota gating. */
  val isPro = entitlement.isPro
  val swipesToday = quota.swipesToday

  // Channel for one-shot UI events: show interstitial, show wall.
  private val _swipeEvent = kotlinx.coroutines.channels.Channel<SwipeOutcomeEvent>(kotlinx.coroutines.channels.Channel.BUFFERED)
  val swipeEvents = _swipeEvent.receiveAsFlow()

  /** Called by save()/dismiss() after the swipe lands. Emits the right outcome. */
  private fun trackSwipeAndEmit() {
    if (entitlement.isPro.value) return  // unlimited for Pro — no counter, no ads
    viewModelScope.launch {
      val n = quota.increment()
      val outcome = when {
        n > app.pantrie.billing.SwipeQuotaRepository.FREE_DAILY_LIMIT -> SwipeOutcomeEvent.Wall
        n % app.pantrie.billing.SwipeQuotaRepository.AD_EVERY_N_SWIPES == 0 -> SwipeOutcomeEvent.ShowAd
        else -> return@launch  // no event needed
      }
      _swipeEvent.send(outcome)
    }
  }

  fun grantBonusSwipes() {
    viewModelScope.launch {
      quota.grantBonusSwipes(app.pantrie.billing.SwipeQuotaRepository.REWARDED_BONUS)
    }
  }
  private val _state = MutableStateFlow<DeckResponse?>(null)
  val state = _state.asStateFlow()
  private val _toast = MutableStateFlow<ToastState?>(null)
  val toast = _toast.asStateFlow()
  private val _error = MutableStateFlow<String?>(null)
  val error = _error.asStateFlow()

  /** User's allergens. Loaded once and reused for every card. Drives the
   *  edge-to-edge banner across the top of the swipe card. Empty until the
   *  first prefs fetch completes; the banner only renders when both
   *  allergens AND a matching ingredient are present. */
  private val _userAllergens = MutableStateFlow<List<String>>(emptyList())
  val userAllergens = _userAllergens.asStateFlow()

  // Declared BEFORE init{} — Kotlin initializes fields in declaration order, and init{}
  // calls refresh() which reads these. Moving them below the init block caused a NPE
  // crash on Tonight-tab open (late-init of StateFlow read during construction).
  private val _adventurous = MutableStateFlow(false)
  val adventurous = _adventurous.asStateFlow()

  private val _filter = MutableStateFlow<String?>(null)
  val filter = _filter.asStateFlow()

  private val _refreshing = MutableStateFlow(false)
  // Counter that increments on every successful save. Drives the "sucked into
  // Library" overlay animation in DeckScreen. Value itself is meaningless;
  // changing it triggers the animation.
  private val _saveCelebration = MutableStateFlow(0)
  val saveCelebration = _saveCelebration.asStateFlow()

  // Pellets-fly-to-Shop celebration. tick bumps per save with missing ingredients;
  // names is the (capped) list of missing ingredient labels rendered as falling
  // chips that arc into the Shop tile.
  data class ShoppingFall(val tick: Int, val names: List<String>)
  private val _shoppingFall = MutableStateFlow(ShoppingFall(0, emptyList()))
  val shoppingFall = _shoppingFall.asStateFlow()

  val refreshing = _refreshing.asStateFlow()

  /**
   * Timestamp of the last in-deck optimistic pantry add. The pantry refresh-bus
   * collector below skips refresh() if this is recent — otherwise the server
   * refetch races our optimistic state update and overwrites the chip we just
   * removed (it visually pops back for a frame). Pantry-tab additions still
   * refresh the deck normally.
   */
  private var lastOptimisticAddMs = 0L

  init {
    refresh()
    // Load user allergens once. Cheap call, drives the banner across all
    // deck cards. We re-fetch on refreshBus.pantry too in case the user
    // edited prefs in another tab; not strictly necessary today since
    // SettingsViewModel doesn't broadcast on save, but cheap insurance.
    viewModelScope.launch {
      runCatching { api.getPreferences() }
        .onSuccess { _userAllergens.value = it.allergens }
    }
    viewModelScope.launch {
      refreshBus.pantry.collect {
        if (System.currentTimeMillis() - lastOptimisticAddMs > 5000) {
          refresh()
        }
      }
    }
  }

  fun setFilter(v: String?) {
    _filter.value = v
    analytics.track("deck_filter_changed", mapOf("filter" to (v ?: "all")))
    refresh()
  }

  fun toggleAdventurous() {
    _adventurous.value = !_adventurous.value
    analytics.track("adventurous_toggled", mapOf("on" to _adventurous.value))
    refresh()
  }

  fun refresh() {
    viewModelScope.launch {
      _error.value = null
      _refreshing.value = true
      val advFlag = if (_adventurous.value) 1 else null
      // require_photo=1 — surface only photo-having recipes in the Tonight deck.
      // Audit (2026-04-25) showed 94% of HuggingFace recipes have no image; without
      // this guard the deck is mostly photoless garbage. Mixology has its own client-side
      // photo guard for Mixologist mode (Bootlegger vintage cards intentionally photoless).
      //
      // One-shot auto-retry so a transient flake (cold-start race against the auth-refresh
      // interceptor, brief network blip) doesn't leave the user looking at "Couldn't load"
      // until they manually tap Retry. Second call after a 1.2s pause gives the refresh
      // interceptor time to swap a fresh access token onto the queue.
      val first = runCatching { api.deck(adventurous = advFlag, filter = _filter.value, requirePhoto = 1) }
      val finalResult = if (first.isFailure) {
        kotlinx.coroutines.delay(1200)
        runCatching { api.deck(adventurous = advFlag, filter = _filter.value, requirePhoto = 1) }
      } else first
      finalResult
        .onSuccess { resp ->
          _state.value = resp
        }
        .onFailure { e ->
          _error.value = e.message ?: "Couldn't load recipes"
        }
      _refreshing.value = false
    }
  }

  /** User taps a missing ingredient on the card — assumption: scanner missed it. Add to pantry + optimistically mark as "have" on this card. */
  fun addToPantry(recipe: Recipe, ing: Ingredient) {
    // Snapshot for rollback — if the API call fails, restore exactly what was there
    // so the chip + match% don't visually lie about a failed add.
    val previousState = _state.value
    lastOptimisticAddMs = System.currentTimeMillis()
    _state.value = previousState?.let { s ->
      s.copy(deck = s.deck.map { r ->
        if (r.id != recipe.id) r
        else {
          val updatedIngredients = r.ingredients.map { i ->
            if (i.name == ing.name) i.copy(have = true) else i
          }
          val haveCount = updatedIngredients.count { it.have }
          val newMatchPercent = if (updatedIngredients.isEmpty()) 0
            else (haveCount * 100) / updatedIngredients.size
          r.copy(ingredients = updatedIngredients, pantryMatchPercent = newMatchPercent)
        }
      })
    }
    analytics.track("pantry_quick_add_from_deck", mapOf("recipeId" to recipe.id, "name" to ing.name))
    viewModelScope.launch {
      runCatching {
        api.addPantryItem(app.pantrie.network.dto.PantryAddRequest(
          name = ing.name, quantity = ing.quantity, unit = ing.unit, category = ing.aisle,
        ))
      }.onSuccess {
        refreshBus.bumpPantry()
        // No toast on success — chip vanishing + match% climbing + +1 float animation
        // already convey the action. Toasts on every action become noise.
      }.onFailure { e ->
        // Roll back optimistic update so UI matches reality on failure.
        _state.value = previousState
        val raw = e.message.orEmpty()
        val msg = when {
          // 402 = real paywall (we don't actually gate pantry adds on free tier yet,
          // but keep this branch for when we do).
          raw.contains("402") || raw.contains("payment", ignoreCase = true) ->
            "Pantry full on free tier. Go ${app.pantrie.Brand.PRO_NAME} for unlimited."
          // 429 = transient rate limit. Tell the user it'll work in a moment.
          raw.contains("429") || raw.contains("rate limit", ignoreCase = true) ->
            "Slow down a sec — try again in a moment."
          raw.contains("401") -> "Sign-in expired. Reopen the app."
          else -> "Couldn't add — try again"
        }
        _toast.value = ToastState(msg)
      }
    }
  }

  fun save(r: Recipe) {
    // Optimistic: drop card from deck immediately for snappy UX
    _state.value = _state.value?.let { s ->
      s.copy(deck = s.deck.filter { it.id != r.id }, remaining = (s.remaining - 1).coerceAtLeast(0))
    }
    analytics.track("recipe_saved", mapOf("recipeId" to r.id, "match" to r.pantryMatchPercent))
    refreshBus.bumpRecipeSaved()
    trackSwipeAndEmit()
    // Trigger the "sucked into Library" overlay animation so the user has a
    // visual answer to "where did this just go." Bumping a counter (rather
    // than emitting a SharedFlow event) keeps it cheap and lets repeated rapid
    // saves stack the same animation pipeline.
    _saveCelebration.value = _saveCelebration.value + 1
    // Missing-ingredient pellets fly out of the card and arc into the Shop
    // tile. We compute "missing" optimistically from the recipe's ingredient
    // have-flags (server already filtered against pantry) so the animation
    // fires the same instant the card flies, no waiting on the API round-trip.
    val missing = r.ingredients
      .filter { ing -> ing.have != true }
      .map { it.name.trim() }
      .filter { it.isNotEmpty() }
      .distinct()
      .take(6)
    if (missing.isNotEmpty()) {
      _shoppingFall.value = ShoppingFall(_shoppingFall.value.tick + 1, missing)
    }
    viewModelScope.launch {
      val resp = runCatching { api.interact(InteractRequest(recipeId = r.id, status = "saved")) }.getOrNull()
      val added = resp?.addedToShopping ?: 0
      if (added > 0) refreshBus.bumpShopping()
      // No "Saved" toast. The library-suck capsule + ingredients-fall chips
      // already give a visceral answer to "where did this go." A redundant
      // text banner reads as tacky and competes with the brass animation.
      // Reconcile remaining count with server (may differ slightly if another device ate tokens)
      resp?.remaining?.let { srv ->
        _state.value = _state.value?.copy(remaining = srv)
      }
    }
  }

  fun dismiss(r: Recipe) {
    _state.value = _state.value?.let { s ->
      s.copy(deck = s.deck.filter { it.id != r.id }, remaining = (s.remaining - 1).coerceAtLeast(0))
    }
    analytics.track("recipe_dismissed", mapOf("recipeId" to r.id, "match" to r.pantryMatchPercent))
    trackSwipeAndEmit()
    viewModelScope.launch {
      val resp = runCatching { api.interact(InteractRequest(recipeId = r.id, status = "dismissed")) }.getOrNull()
      // No "Skipped" toast — the card animating away is the feedback.
      resp?.remaining?.let { srv -> _state.value = _state.value?.copy(remaining = srv) }
    }
  }

  fun markCooked(r: Recipe) {
    _state.value = _state.value?.let { s -> s.copy(deck = s.deck.filter { it.id != r.id }) }
    analytics.track("recipe_cooked", mapOf("recipeId" to r.id, "match" to r.pantryMatchPercent))
    viewModelScope.launch {
      val resp = runCatching { api.interact(InteractRequest(recipeId = r.id, status = "cooked")) }.getOrNull()
      if (resp?.cookUndoId != null) {
        _toast.value = ToastState(
          message = "Cooked · pantry updated",
          undoId = resp.cookUndoId,
          undoAction = { undoCook(resp.cookUndoId) },
        )
      }
      // (No fallback "Marked cooked" toast — cooked card vanishing is the feedback.
      // The undoable toast above ONLY fires when there's a real undo affordance to surface.)
    }
  }

  private fun undoCook(undoId: String) {
    viewModelScope.launch {
      runCatching { api.undoCook(UndoCookRequest(undoId)) }
      _toast.value = ToastState("Undone — pantry restored")
      refresh()
    }
  }

  fun clearToast() { _toast.value = null }
}

@Composable
fun DeckScreen(
  onOpenRecipe: (String) -> Unit = {},
  onStartCook: (String) -> Unit = {},
  onOpenSaved: () -> Unit = {},
  onOpenPantry: () -> Unit = {},
  onOpenShopping: () -> Unit = {},
  onOpenPlan: () -> Unit = {},
  onOpenSearch: () -> Unit = {},
  onOpenPaywall: () -> Unit = {},
  onContributePhoto: (String) -> Unit = {},
  vm: DeckViewModel = hiltViewModel(),
) {
  val state by vm.state.collectAsState()
  val toast by vm.toast.collectAsState()
  val error by vm.error.collectAsState()
  val s = state

  // Walkthrough VM — used to report the Pantry quick-action tile's bounds so the
  // first-launch tour can spotlight it (step 1: "open your pantry").
  val tourVm: app.pantrie.feature.walkthrough.WalkthroughViewModel = hiltViewModel()

  // ===== Ad / quota plumbing =====
  // We observe one-shot swipe events from the VM and either fire an interstitial or pop the wall.
  val adHost: app.pantrie.billing.AdHostViewModel = hiltViewModel()
  val context = androidx.compose.ui.platform.LocalContext.current
  var showWall by remember { mutableStateOf(false) }
  androidx.compose.runtime.LaunchedEffect(Unit) {
    vm.swipeEvents.collect { ev ->
      val activity = context as? android.app.Activity ?: return@collect
      when (ev) {
        app.pantrie.feature.deck.SwipeOutcomeEvent.ShowAd -> {
          adHost.adManager.showInterstitial(activity) {}
        }
        app.pantrie.feature.deck.SwipeOutcomeEvent.Wall -> {
          showWall = true
        }
      }
    }
  }
  if (showWall) {
    app.pantrie.billing.SwipeWallSheet(
      onWatchAd = {
        val activity = context as? android.app.Activity ?: return@SwipeWallSheet
        adHost.adManager.showRewarded(
          activity = activity,
          onReward = { vm.grantBonusSwipes() },
          onClosed = { showWall = false },
        )
      },
      onGoPro = {
        showWall = false
        onOpenPaywall()
      },
      onDismiss = { showWall = false },
    )
  }

  // Intentionally NOT re-fetching on every tab return — doing so was rerolling the
  // random 75-recipe sample each visit, which felt buggy (different cards each switch)
  // AND — because KV swipe counter was fail-opening — effectively gave unlimited swipes.
  // The deck now refreshes only on init, on swipe, and when pantry changes via the
  // RefreshBus (see PantryViewModel → refreshBus.bumpPantry() → collected here).
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val _unused = lifecycle // keep import usage — pantry-triggered refresh wiring lives on the VM

  Scaffold(containerColor = Paper) { padding ->
    Box(Modifier.padding(padding).fillMaxSize()) {
      Column(Modifier.fillMaxSize()) {
        // Compact banner: Adventurous icon (left) • collapsible filter pill (center) • Cookbook icon (right).
        // No "Tonight" title — the card itself is the page. Tapping the filter pill expands the chip row.
        val currentFilter by vm.filter.collectAsState()
        val isAdventurous by vm.adventurous.collectAsState()
        val chips = listOf(
          null to "All",
          "quick" to "Quick",
          "comfort" to "Comfort",
          "healthy" to "Healthy",
          "breakfast" to "Breakfast",
          "lunch" to "Lunch",
          "dinner" to "Dinner",
          "vegetarian" to "Vegetarian",
          "baking" to "Baking",
        )
        val currentLabel = chips.firstOrNull { it.first == currentFilter }?.second ?: "All"
        var filterExpanded by rememberSaveable { mutableStateOf(false) }

        Row(
          Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          NavPngIconButton(
            iconRes = app.pantrie.R.drawable.brimm_nav_adventurous,
            contentDescription = "Adventurous",
            highlighted = isAdventurous,
            onClick = vm::toggleAdventurous,
          )
          // Collapsible filter pill — shows current selection; tap to expand chip row below.
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (currentFilter != null) Olive.copy(alpha = 0.18f) else Paper,
            modifier = Modifier.weight(1f).clickable { filterExpanded = !filterExpanded },
          ) {
            Row(
              Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
            ) {
              Text(
                currentLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = if (currentFilter != null) Olive else Ink,
                maxLines = 1,
                softWrap = false,
              )
              Spacer(Modifier.width(4.dp))
              Icon(
                if (filterExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (filterExpanded) "Hide filters" else "Show filters",
                tint = if (currentFilter != null) Olive else InkMuted,
                modifier = Modifier.size(18.dp),
              )
            }
          }
          NavPngIconButton(
            iconRes = app.pantrie.R.drawable.brimm_nav_search,
            contentDescription = "Search recipes",
            highlighted = false,
            onClick = onOpenSearch,
          )
          // Library icon with a "+1 new" terracotta badge that pops on every
          // save. The badge auto-dismisses after 2.5s so it doesn't linger.
          // Also reports its bounds so the LibrarySuckOverlay can fly the
          // brass capsule to the exact center of this icon instead of a
          // hard-coded top-right corner.
          val libraryCelebrationTick by vm.saveCelebration.collectAsState()
          var libraryBadgeVisible by remember { mutableStateOf(false) }
          var libraryNewCount by remember { mutableStateOf(0) }
          LaunchedEffect(libraryCelebrationTick) {
            if (libraryCelebrationTick > 0) {
              libraryNewCount += 1
              libraryBadgeVisible = true
              kotlinx.coroutines.delay(2500)
              libraryBadgeVisible = false
              libraryNewCount = 0
            }
          }
          Box(
            modifier = Modifier.onGloballyPositioned { coords ->
              tourVm.reportAnchor(
                "deck_library_icon",
                coords.boundsInWindow(),
              )
            },
          ) {
            NavPngIconButton(
              iconRes = app.pantrie.R.drawable.brimm_nav_library,
              contentDescription = "Library",
              highlighted = true,
              onClick = onOpenSaved,
            )
            if (libraryBadgeVisible) {
              Surface(
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .offset(x = (-2).dp, y = 2.dp)
                  .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp),
                shape = RoundedCornerShape(9.dp),
                color = Terracotta,
                shadowElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Paper),
              ) {
                Box(
                  modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = "+$libraryNewCount",
                    color = Paper,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                  )
                }
              }
            }
          }
        }

        // Accordion body — horizontally scrollable chip row, only rendered when expanded.
        if (filterExpanded) {
          LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
          ) {
            items(chips.size) { i ->
              val (key, label) = chips[i]
              val selected = currentFilter == key
              FilterChip(
                selected = selected,
                onClick = {
                  vm.setFilter(if (selected) null else key)
                  filterExpanded = false  // auto-collapse on selection
                },
                label = { Text(label, maxLines = 1, softWrap = false) },
                leadingIcon = if (key == "quick") {
                  { Icon(Icons.Outlined.AccessTime, null, modifier = Modifier.size(16.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = Olive.copy(alpha = 0.25f),
                  selectedLabelColor = Olive,
                  selectedLeadingIconColor = Olive,
                  containerColor = Paper2,
                  labelColor = Ink,
                ),
              )
            }
          }
        }

        // Card stack area — takes all remaining vertical space between filter banner and
        // the quick-action row at the bottom. weight(1f) lets the quick-action row claim a
        // fixed strip at the bottom instead of floating dead space.
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp), contentAlignment = Alignment.TopCenter) {
          // Swipe counter removed — daily-cap exhaustion still surfaces via the empty-deck message.
          when {
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Couldn't load", style = MaterialTheme.typography.titleMedium, color = Terracotta, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = InkMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = vm::refresh, colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Retry", color = Paper) }
              }
            }
            s == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(color = Ink)
            }
            s.deck.isEmpty() && s.remaining == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              UpsellCard(message = s.message ?: "Come back tomorrow.", tier = s.tier, onOpenPaywall = onOpenPaywall)
            }
            s.deck.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              EmptyDeckMessage()
            }
            else -> {
              val allergens by vm.userAllergens.collectAsState()
              CardStack(
                cards = s.deck,
                onSave = vm::save,
                onDismiss = vm::dismiss,
                onOpen = onOpenRecipe,
                onCook = { r -> vm.markCooked(r); onStartCook(r.id) },
                onAddToPantry = vm::addToPantry,
                onContributePhoto = onContributePhoto,
                userAllergens = allergens,
              )
            }
          }

          // Loading overlay — shown when re-fetching the deck (e.g. filter changed) over an existing deck.
          val isRefreshing by vm.refreshing.collectAsState()
          if (isRefreshing && s != null && error == null) {
            Box(
              Modifier
                .fillMaxSize()
                .background(Paper.copy(alpha = 0.6f)),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator(color = Ink)
            }
          }

        }

        // Quick actions — Pantry / Shop / Plan row that replaces the cluttered bottom nav tabs.
        // Sits between the card and the system nav bar. Same height the dead space used to be.
        Row(
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          QuickActionButton(
            iconRes = app.pantrie.R.drawable.brimm_nav_pantry,
            label = "Pantry",
            onClick = onOpenPantry,
            modifier = Modifier
              .weight(1f)
              // Walkthrough step 1 spotlights this tile. Report bounds for the overlay.
              .onGloballyPositioned { coords ->
                tourVm.reportAnchor(
                  app.pantrie.feature.walkthrough.TourAnchors.DECK_PANTRY_TILE,
                  coords.boundsInWindow(),
                )
              },
          )
          // Shop tile + count badge. Shows the unchecked-item count from
          // AppStateViewModel.shoppingCount in the top-right corner. Wrapping
          // the QuickActionButton in a Box lets the badge float over the tile
          // without affecting the tile's own layout/weight calculation.
          val appVmForBadge: app.pantrie.feature.app.AppStateViewModel = hiltViewModel()
          val shoppingCount by appVmForBadge.shoppingCount.collectAsState()
          Box(modifier = Modifier.weight(1f)) {
            QuickActionButton(
              iconRes = app.pantrie.R.drawable.brimm_nav_shop,
              label = "Shop",
              onClick = onOpenShopping,
              modifier = Modifier
                .fillMaxWidth()
                // Shopping mini-tour step 1 spotlights this tile.
                .onGloballyPositioned { coords ->
                  tourVm.reportAnchor(
                    app.pantrie.feature.walkthrough.TourAnchors.DECK_SHOPPING_TILE,
                    coords.boundsInWindow(),
                  )
                },
            )
            if (shoppingCount > 0) {
              Surface(
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .offset(x = (-6).dp, y = 6.dp)
                  .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp),
                shape = RoundedCornerShape(11.dp),
                color = Terracotta,
                shadowElevation = 2.dp,
              ) {
                Box(
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = if (shoppingCount > 99) "99+" else shoppingCount.toString(),
                    color = Paper,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                  )
                }
              }
            }
          }
          QuickActionButton(
            iconRes = app.pantrie.R.drawable.brimm_nav_plan,
            label = "Plan",
            onClick = onOpenPlan,
            modifier = Modifier
              .weight(1f)
              // Plan mini-tour step 1 spotlights this tile.
              .onGloballyPositioned { coords ->
                tourVm.reportAnchor(
                  app.pantrie.feature.walkthrough.TourAnchors.DECK_PLAN_TILE,
                  coords.boundsInWindow(),
                )
              },
          )
        }
      }

      // Save celebrations — placed at the OUTER Box level so they can travel
      // the full screen height. Order matters: Shopping pellets are drawn
      // FIRST so the Library capsule paints ON TOP. Each is gated on a
      // user-facing toggle in Settings > Features so a motion-sensitive user
      // can turn either off without losing the rest of the app.
      val libraryAnimEnabled by vm.libraryAnimEnabled.collectAsState()
      val ingredientFallEnabled by vm.ingredientFallEnabled.collectAsState()

      val shopFall by vm.shoppingFall.collectAsState()
      ShoppingFallOverlay(tick = shopFall.tick, names = shopFall.names, enabled = ingredientFallEnabled)

      val celebrationTick by vm.saveCelebration.collectAsState()
      LibrarySuckOverlay(tick = celebrationTick, enabled = libraryAnimEnabled)

      // Toast overlay
      toast?.let { t ->
        LaunchedEffect(t) { kotlinx.coroutines.delay(4500); vm.clearToast() }
        Box(Modifier.fillMaxSize().padding(bottom = 90.dp), contentAlignment = Alignment.BottomCenter) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = Ink,
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
          ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
              Text(t.message, color = Paper, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
              if (t.undoId != null && t.undoAction != null) {
                TextButton(onClick = { t.undoAction.invoke() }) {
                  // Toast is the one inverse-surface block in the dark theme; Brass reads
                  // as the accent action on the off-white bg.
                  Text("Undo", color = Brass, fontWeight = FontWeight.SemiBold)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CardStack(
  cards: List<Recipe>,
  onSave: (Recipe) -> Unit,
  onDismiss: (Recipe) -> Unit,
  onOpen: (String) -> Unit,
  onCook: (Recipe) -> Unit,
  onAddToPantry: (Recipe, Ingredient) -> Unit = { _, _ -> },
  onContributePhoto: (String) -> Unit = {},
  userAllergens: List<String> = emptyList(),
) {
  val visible = cards.take(3)

  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    // Back cards (visual depth)
    visible.drop(1).reversed().forEachIndexed { idx, r ->
      val depth = visible.drop(1).size - idx
      val scale = 1f - (depth * 0.04f)
      val offsetY = (depth * 10).dp
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .fillMaxHeight()
          .padding(top = offsetY)
          .scale(scale)
          .alpha(0.8f - depth * 0.2f),
        colors = CardDefaults.cardColors(containerColor = tintFor(r.cuisine)),
        shape = RoundedCornerShape(16.dp),
      ) { }
    }

    // Top draggable card
    visible.firstOrNull()?.let { top ->
      key(top.id) {
        DraggableCard(
          recipe = top,
          onSave = { onSave(top) },
          onDismiss = { onDismiss(top) },
          onOpen = { onOpen(top.id) },
          onCook = { onCook(top) },
          onAddToPantry = { ing -> onAddToPantry(top, ing) },
          onContributePhoto = onContributePhoto,
          userAllergens = userAllergens,
        )
      }
    }
  }
}

@Composable
private fun DraggableCard(
  recipe: Recipe,
  onSave: () -> Unit,
  onDismiss: () -> Unit,
  onOpen: () -> Unit,
  onCook: () -> Unit,
  onAddToPantry: (Ingredient) -> Unit = {},
  onContributePhoto: (String) -> Unit = {},
  userAllergens: List<String> = emptyList(),
) {
  val tourVm: app.pantrie.feature.walkthrough.WalkthroughViewModel = hiltViewModel()
  val density = LocalDensity.current
  var offsetX by remember { mutableStateOf(0f) }
  var offsetY by remember { mutableStateOf(0f) }
  var flipped by remember { mutableStateOf(false) }
  var exitDir by remember { mutableStateOf(0) }
  val thresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }

  val animatedOffsetX by animateFloatAsState(
    targetValue = if (exitDir != 0) exitDir * 1800f else offsetX,
    animationSpec = tween(durationMillis = if (exitDir != 0) 240 else 0),
    finishedListener = {
      if (exitDir == 1) onSave()
      else if (exitDir == -1) onDismiss()
    },
    label = "cardX",
  )
  val rotation = (animatedOffsetX / 30f).coerceIn(-18f, 18f)
  val saveOverlay = (offsetX / thresholdPx).coerceIn(0f, 1f)
  val skipOverlay = (-offsetX / thresholdPx).coerceIn(0f, 1f)

  val rotationYAnim by animateFloatAsState(
    targetValue = if (flipped) 180f else 0f,
    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
    label = "flipY",
  )

  Box(
    Modifier
      .fillMaxWidth()
      .fillMaxHeight()
      // Report bounds for the tour's "swipe this card" spotlight (main tour step 4).
      .onGloballyPositioned { coords ->
        tourVm.reportAnchor(
          app.pantrie.feature.walkthrough.TourAnchors.DECK_CARD_AREA,
          coords.boundsInWindow(),
        )
      }
      .offset { IntOffset(animatedOffsetX.roundToInt(), offsetY.roundToInt()) }
      .rotate(rotation)
      .graphicsLayer { rotationY = rotationYAnim; cameraDistance = 12f * density.density }
      .pointerInput(recipe.id) {
        detectDragGestures(
          onDrag = { change, drag ->
            change.consume()
            offsetX += drag.x
            offsetY += drag.y * 0.3f
          },
          onDragEnd = {
            when {
              offsetX > thresholdPx -> exitDir = 1
              offsetX < -thresholdPx -> exitDir = -1
              else -> { offsetX = 0f; offsetY = 0f }
            }
          },
          onDragCancel = { offsetX = 0f; offsetY = 0f },
        )
      },
  ) {
    if (rotationYAnim < 90f) {
      CardFront(
        recipe = recipe,
        saveProgress = saveOverlay,
        skipProgress = skipOverlay,
        onFlip = { flipped = true },
        onAddToPantry = onAddToPantry,
        onContributePhoto = { id -> onContributePhoto(id) },
        userAllergens = userAllergens,
      )
    } else {
      Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
        CardBack(
          recipe = recipe,
          onFlipBack = { flipped = false },
          onOpen = onOpen,
          onCook = onCook,
        )
      }
    }

    // Drag overlay labels
    if (saveOverlay > 0.1f && rotationYAnim < 90f) {
      Text(
        "SAVE",
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(24.dp)
          .rotate(-12f)
          .border(3.dp, Olive, RoundedCornerShape(4.dp))
          .padding(horizontal = 10.dp, vertical = 4.dp)
          .alpha(saveOverlay),
        color = Olive, fontWeight = FontWeight.ExtraBold,
        style = MaterialTheme.typography.headlineSmall,
      )
    }
    if (skipOverlay > 0.1f && rotationYAnim < 90f) {
      Text(
        "SKIP",
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(24.dp)
          .rotate(12f)
          .border(3.dp, Terracotta, RoundedCornerShape(4.dp))
          .padding(horizontal = 10.dp, vertical = 4.dp)
          .alpha(skipOverlay),
        color = Terracotta, fontWeight = FontWeight.ExtraBold,
        style = MaterialTheme.typography.headlineSmall,
      )
    }
  }
}

@Composable
private fun CardFront(
  recipe: Recipe,
  saveProgress: Float,
  skipProgress: Float,
  onFlip: () -> Unit,
  onAddToPantry: (Ingredient) -> Unit = {},
  onContributePhoto: (String) -> Unit = {},
  userAllergens: List<String> = emptyList(),
) {
  val bg = tintFor(recipe.cuisine)
  val hasImage = !recipe.imageUrl.isNullOrBlank()
  Card(
    modifier = Modifier.fillMaxSize(),
    colors = CardDefaults.cardColors(containerColor = bg),
    shape = RoundedCornerShape(16.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
  ) {
    Column(Modifier.fillMaxSize()) {

      // Edge-to-edge allergen banner. Server tells us status per recipe so the
      // client doesn't need to know which allergens have substitutes.
      //   "red"    → at least one matched allergen has no real sub → SKIP
      //   "yellow" → every matched allergen has a known sub → block w/ workaround
      //   "none"   → no allergen → no banner
      if (recipe.allergenStatus == "red" || recipe.allergenStatus == "yellow") {
        DeckAllergenBanner(
          status = recipe.allergenStatus,
          labels = recipe.allergenLabels,
        )
      }
      // IMAGE — fills ALL remaining vertical space via weight(1f). Text blocks below have intrinsic
      // heights, so image grows to eat everything else. No scroll, no dead space.
      if (hasImage) {
        AsyncImage(
          model = recipe.imageUrl,
          contentDescription = recipe.title,
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        )
      } else {
        // No photo placeholder. Two stacked CTAs:
        //   1. First-cook claim ("Cook this first · Your name. Forever.")
        //   2. First-photo claim ("Take the first photo · Yours forever if approved.")
        // Both are real value props: cook = top-of-card credit; photo = the recipe's
        // canonical image. Stacked because either action is independently valuable
        // and a user might do one without the other. .clickable on the photo CTA
        // is OK alongside the parent swipe — quick taps register, drags pass through.
        Box(
          Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(bg.copy(alpha = 0.55f)),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 18.dp),
          ) {
            // Block 1 — first-cook claim. No tap target; user claims by Cook-Mode finish.
            Icon(
              imageVector = Icons.Outlined.LocalFireDepartment,
              contentDescription = null,
              tint = Ink.copy(alpha = 0.85f),
              modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(10.dp))
            if (recipe.firstCookedBy.isNullOrBlank()) {
              Text(
                "Cook this first",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
              )
              Spacer(Modifier.height(2.dp))
              Text(
                "Your name on the card. Forever.",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.copy(alpha = 0.78f),
              )
            } else {
              Text(
                "FIRST COOKED BY",
                style = MaterialTheme.typography.labelSmall,
                color = Ink.copy(alpha = 0.65f),
                letterSpacing = 1.5.sp,
              )
              Spacer(Modifier.height(2.dp))
              Text(
                recipe.firstCookedBy,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = BrassBright,
              )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(
              modifier = Modifier.fillMaxWidth(0.4f),
              color = Ink.copy(alpha = 0.2f),
            )
            Spacer(Modifier.height(20.dp))

            // Block 2 — first-photo claim. Tappable surface.
            // Routes to recipe/<id>/photo-contribute, which opens the system camera.
            // Approved photo becomes the canonical image and the user gets photo credit.
            androidx.compose.material3.Surface(
              shape = RoundedCornerShape(28.dp),
              color = Ink,
              modifier = Modifier.clickable { onContributePhoto(recipe.id) },
            ) {
              Row(
                Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Outlined.CameraAlt,
                  contentDescription = null,
                  tint = BrassBright,
                  modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                  "Take the first photo",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.SemiBold,
                  color = androidx.compose.ui.graphics.Color(0xFFF5EFE4),
                )
              }
            }
            Spacer(Modifier.height(8.dp))
            Text(
              "Yours forever if approved.",
              style = MaterialTheme.typography.labelSmall,
              color = Ink.copy(alpha = 0.7f),
              fontStyle = FontStyle.Italic,
            )
          }
        }
      }

      // Text block — compact, fits all info. Fixed heights mean the image absorbs all extra space.
      Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        // Eyebrow — cuisine tag + match badge + expiring badge + cost, one line.
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (!recipe.cuisine.isNullOrBlank()) {
            Text(
              recipe.cuisine.uppercase(),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = Ink.copy(alpha = 0.7f),
              letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.width(10.dp))
          }
          Badge(
            text = "${recipe.pantryMatchPercent}%",
            bg = when {
              recipe.pantryMatchPercent >= 80 -> Olive
              recipe.pantryMatchPercent >= 50 -> BrassBright
              recipe.pantryMatchPercent > 0 -> Ink.copy(alpha = 0.45f)
              else -> InkFaint
            },
          )
          if (recipe.usesExpiring.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Badge(text = "Expiring", bg = Terracotta, icon = Icons.Outlined.LocalFireDepartment)
          }
          if (recipe.costPerServing > 0) {
            Spacer(Modifier.width(6.dp))
            Badge(text = "$${"%.2f".format(recipe.costPerServing)}/srv", bg = Ink.copy(alpha = 0.55f))
          }
        }

        Spacer(Modifier.height(8.dp))

        // TITLE — editorial, up to 2 lines to keep the image bigger.
        Text(
          recipe.title,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.SemiBold,
          color = Ink,
          lineHeight = 28.sp,
          maxLines = 2,
        )

        // Description — italic, 2 lines.
        val desc = recipe.description ?: firstStepDescription(recipe)
        if (!desc.isNullOrBlank()) {
          Spacer(Modifier.height(6.dp))
          Text(
            desc,
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = InkSoft,
            maxLines = 2,
          )
        }

        // Max servings callout — one-liner.
        if (recipe.maxServings != null && recipe.maxServings > 0) {
          Spacer(Modifier.height(8.dp))
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Olive.copy(alpha = 0.18f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Filled.Restaurant, null, tint = Olive, modifier = Modifier.size(14.dp))
              Spacer(Modifier.width(6.dp))
              Text(
                "Up to ${recipe.maxServings} serving${if (recipe.maxServings == 1) "" else "s"} from your pantry",
                style = MaterialTheme.typography.labelMedium,
                color = Olive, fontWeight = FontWeight.SemiBold,
                maxLines = 1,
              )
            }
          }
        }

        Spacer(Modifier.height(8.dp))

        // Stats row — full info preserved: time · serves · X cooks · rating (count).
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (recipe.totalMin > 0) {
            StatCell(icon = Icons.Outlined.AccessTime, value = "${recipe.totalMin}m")
            Spacer(Modifier.width(14.dp))
          }
          StatCell(icon = Icons.Outlined.People, value = "${recipe.serves}")
          if (recipe.cookCount > 0) {
            Spacer(Modifier.width(14.dp))
            StatCell(icon = Icons.Outlined.LocalFireDepartment, value = "${formatCount(recipe.cookCount)} cooks")
          }
          if (recipe.rating > 0) {
            Spacer(Modifier.width(14.dp))
            StatCell(
              icon = Icons.Outlined.Star,
              value = "%.1f".format(recipe.rating) + (if (recipe.ratingCount > 0) " (${recipe.ratingCount})" else ""),
              iconTint = BrassBright,
            )
          }
        }

        // Missing-ingredients chip row — up to 6 chips, 24-char names (same as original).
        val missing = recipe.ingredients.filter { !it.have }.take(6)
        if (missing.isNotEmpty()) {
          Spacer(Modifier.height(8.dp))
          androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            missing.forEach { ing ->
              // Local "+1" float animation — runs for ~500ms before the chip vanishes via
              // the optimistic state update. Visual confirmation that "you added that" without
              // a popup toast cluttering the screen.
              var bumping by remember(ing.name) { mutableStateOf(false) }
              androidx.compose.runtime.LaunchedEffect(bumping) {
                if (bumping) {
                  // Let the +1 animation play before the chip disappears.
                  kotlinx.coroutines.delay(220)
                  onAddToPantry(ing)
                }
              }
              val plusAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (bumping) 0f else 1f,
                animationSpec = tween(500, easing = FastOutSlowInEasing),
                label = "plus-alpha",
              )
              val plusOffsetY by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (bumping) -36f else 0f,
                animationSpec = tween(500, easing = FastOutSlowInEasing),
                label = "plus-offset",
              )
              Box {
                Surface(
                  modifier = Modifier.pointerInput(ing.name) {
                    awaitEachGesture {
                      val down = awaitFirstDown(requireUnconsumed = false)
                      down.consume()
                      var fired = false
                      while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.all { !it.pressed }) {
                          if (!fired) {
                            fired = true
                            // Trigger the +1 float; LaunchedEffect above schedules the
                            // actual onAddToPantry call after the animation kicks off.
                            bumping = true
                          }
                          break
                        }
                      }
                    }
                  },
                  shape = RoundedCornerShape(14.dp),
                  color = Paper3,
                  border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
                ) {
                Row(
                  Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text("+", color = Olive, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                  Spacer(Modifier.width(3.dp))
                  Text(
                    ing.name.take(24),
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink, maxLines = 1,
                  )
                }
              }
                // The floating "+1" rises out of the chip on tap, fading as it goes.
                if (bumping) {
                  Text(
                    "+1",
                    color = BrassBright,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                      .align(Alignment.TopCenter)
                      .offset { IntOffset(0, plusOffsetY.roundToInt()) }
                      .alpha(plusAlpha),
                  )
                }
              }
            }
          }
        }

        Spacer(Modifier.height(10.dp))

        // CTA
        OutlinedButton(
          onClick = onFlip,
          modifier = Modifier.fillMaxWidth().height(40.dp),
          shape = RoundedCornerShape(4.dp),
        ) { Text("See ingredients & reviews", style = MaterialTheme.typography.labelLarge) }
      }
    }
  }
}

@Composable
private fun CardBack(
  recipe: Recipe,
  onFlipBack: () -> Unit,
  onOpen: () -> Unit,
  onCook: () -> Unit,
) {
  val bg = tintFor(recipe.cuisine)
  Card(
    modifier = Modifier.fillMaxSize(),
    colors = CardDefaults.cardColors(containerColor = bg),
    shape = RoundedCornerShape(16.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
  ) {
    // Recipes with long ingredient lists or many steps used to overflow off the
    // bottom of the card and the Cook / Save buttons became unreachable. The
    // verticalScroll modifier lets the user scroll through the back-of-card
    // content while the card itself stays fixed.
    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(24.dp),
    ) {
      Text(recipe.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Ink)
      Spacer(Modifier.height(12.dp))

      val shown = recipe.ingredients.take(10)
      Text("Ingredients", style = MaterialTheme.typography.labelMedium, color = InkMuted, fontWeight = FontWeight.Medium)
      Spacer(Modifier.height(8.dp))
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { IngredientLine(it) }
        if (recipe.ingredients.size > 10) {
          Text("+${recipe.ingredients.size - 10} more…", style = MaterialTheme.typography.labelSmall, color = InkMuted)
        }
      }

      Spacer(Modifier.height(16.dp))
      HorizontalDivider(color = InkFaint.copy(alpha = 0.5f))
      Spacer(Modifier.height(12.dp))

      Text("Reviews", style = MaterialTheme.typography.labelMedium, color = InkMuted, fontWeight = FontWeight.Medium)
      Spacer(Modifier.height(6.dp))
      // REAL reviews only. If none, show an honest empty state — no fake "4.5 ★ from N users".
      if (recipe.ratingCount > 0 && recipe.rating > 0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Outlined.Star, null, tint = BrassBright, modifier = Modifier.size(14.dp))
          Spacer(Modifier.width(4.dp))
          Text(
            // String template doesn't resolve %.1f on its own — must call .format() explicitly,
            // otherwise the placeholder ships to the user as the literal "%.1f average ..." string.
            "${"%.1f".format(recipe.rating)} average · ${recipe.ratingCount} review${if (recipe.ratingCount == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyMedium, color = Ink,
          )
        }
      } else {
        Text(
          "No reviews yet — cook it and be the first.",
          style = MaterialTheme.typography.bodyMedium, color = InkMuted,
          fontStyle = FontStyle.Italic,
        )
      }

      Spacer(Modifier.weight(1f))

      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = onFlipBack,
          modifier = Modifier.weight(1f).height(48.dp),
          shape = RoundedCornerShape(4.dp),
        ) { Text("Back") }
        Button(
          onClick = onCook,
          modifier = Modifier.weight(1.2f).height(48.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Ink),
          shape = RoundedCornerShape(4.dp),
        ) { Text("Cook it", color = Paper, fontWeight = FontWeight.SemiBold) }
        OutlinedButton(
          onClick = onOpen,
          modifier = Modifier.weight(1f).height(48.dp),
          shape = RoundedCornerShape(4.dp),
        ) { Text("Full") }
      }
    }
  }
}

@Composable
private fun IngredientLine(ing: Ingredient) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(8.dp).background(if (ing.have) Olive else InkFaint, CircleShape))
    Spacer(Modifier.width(8.dp))
    IngredientImageOrEmoji(
      name = ing.name,
      size = 20.dp,
      emojiStyle = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.width(6.dp))
    Text(
      listOfNotNull(
        ing.quantity?.takeIf { it > 0.0 }?.let { q -> if (q == q.toInt().toDouble()) q.toInt().toString() else "%.1f".format(q) },
        ing.unit?.takeIf { it.isNotBlank() },
        ing.name,
      ).joinToString(" "),
      style = MaterialTheme.typography.bodyMedium,
      color = if (ing.have) Ink else InkMuted,
    )
  }
}

@Composable
private fun StatCell(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, iconTint: Color = InkSoft) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(icon, null, tint = iconTint, modifier = Modifier.size(14.dp))
    Spacer(Modifier.width(4.dp))
    Text(value, style = MaterialTheme.typography.labelMedium, color = InkSoft, fontWeight = FontWeight.Medium)
  }
}

/** Edge-to-edge allergen banner for the deck card. Sits above the image
 *  on the front of the card. Tri-state:
 *    red    → at least one matched allergen has no real substitute → skip
 *    yellow → all matched allergens have known subs → can work around
 *    (none) → no banner; status="none" rendered as nothing
 *
 *  Compact (~36dp) so it doesn't eat the image. Long-press ingredients on
 *  the detail screen for sub options — the deck card is too small for subs
 *  inline. Server computes status; client just renders. */
@Composable
private fun DeckAllergenBanner(status: String, labels: List<String>) {
  val red = Color(0xFFC54B3C)
  val amber = Color(0xFFD4A04A)   // BrassBright — on-brand "warning, workable"
  val ink = Color(0xFF2B2621)
  val isRed = status == "red"
  val bg = if (isRed) red else amber
  // Red text white-on-red for max contrast; yellow ink-on-brass to keep
  // the speakeasy palette clean (white on brass looks washed out).
  val fg = if (isRed) Color.White else ink
  val labelText = labels.joinToString(" · ") { it.uppercase() }
  val prefix = if (isRed) "Allergen, no swap" else "Allergen, sub available"
  Surface(
    color = bg,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Outlined.Warning,
        contentDescription = null,
        tint = fg,
        modifier = Modifier.size(16.dp),
      )
      Spacer(Modifier.width(10.dp))
      Text(
        if (labelText.isNotBlank()) "$prefix · $labelText" else prefix,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = fg,
        letterSpacing = 1.0.sp,
        maxLines = 2,
      )
    }
  }
}

@Composable
private fun Badge(text: String, bg: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
  Surface(color = bg, shape = RoundedCornerShape(4.dp)) {
    Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
      if (icon != null) {
        Icon(icon, null, tint = Paper, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
      }
      Text(text, color = Paper, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
  }
}

@Composable
private fun EmptyDeckMessage() {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text("You've seen them all.", style = MaterialTheme.typography.titleLarge, color = Ink, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Text(
      "Add items to your pantry for fresh matches tomorrow.",
      style = MaterialTheme.typography.bodyMedium, color = InkMuted,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
  }
}

@Composable
private fun UpsellCard(message: String, tier: String, onOpenPaywall: () -> Unit) {
  // Free users: the empty-deck state is the highest-intent moment for a Pro upsell — they
  // burned through their daily allowance, so swap the old single-line button for the full
  // ProUpgradeCard with all 3 tiers visible inline. Pro / past-paywall users keep the plain
  // "come back tomorrow" message since they have no upgrade left to do.
  Column(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Surface(shape = RoundedCornerShape(12.dp), color = Paper2) {
      Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("That's your 10 for today.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
          message,
          style = MaterialTheme.typography.bodyMedium, color = InkMuted,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
      }
    }
    if (tier == "free") {
      Spacer(Modifier.height(16.dp))
      app.pantrie.billing.ProUpgradeCard(vintageMode = false)
    }
  }
}

private fun formatCount(n: Int): String = when {
  n >= 1_000_000 -> "${n / 1_000_000}M"
  n >= 1_000 -> "${n / 1_000}k"
  else -> n.toString()
}

private fun firstStepDescription(r: Recipe): String? {
  val step = r.steps.firstOrNull()?.text?.trim() ?: return null
  if (step.isBlank()) return null
  // Take the first sentence, capped at 200 chars
  val firstSentence = step.split(Regex("(?<=[.!?])\\s+")).firstOrNull()?.trim() ?: step
  return firstSentence.take(200)
}

@Composable
private fun QuickActionButton(
  iconRes: Int,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(12.dp),
    color = Paper2,
    border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
    modifier = modifier.height(68.dp),
  ) {
    Row(
      Modifier.fillMaxSize().padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Image(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier
          .size(42.dp)
          .clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop,
      )
      Spacer(Modifier.width(8.dp))
      Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = Ink,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
      )
    }
  }
}

// Boxed PNG icon button — replaces IconButton+Icon for top-row affordances. Brass
// border + dark surface so the photorealistic asset reads as a tappable square,
// not a floating image. `highlighted` lifts the border to BrassBright for active
// states (e.g., Adventurous toggle on, Library affordance).
@Composable
private fun NavPngIconButton(
  iconRes: Int,
  contentDescription: String,
  highlighted: Boolean,
  onClick: () -> Unit,
) {
  val borderCol = if (highlighted) BrassBright else Rule
  val bgCol = if (highlighted) Paper3 else Paper2
  Box(
    modifier = Modifier
      .size(50.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(bgCol)
      .border(1.dp, borderCol, RoundedCornerShape(10.dp))
      .clickable(onClick = onClick)
      .padding(4.dp),
    contentAlignment = Alignment.Center,
  ) {
    Image(
      painter = painterResource(iconRes),
      contentDescription = contentDescription,
      modifier = Modifier
        .size(40.dp)
        .clip(RoundedCornerShape(8.dp)),
      contentScale = ContentScale.Crop,
    )
  }
}


/**
 * "Sucked into Library" celebration. Triggered when DeckViewModel.saveCelebration
 * counter increments. Shows a single-shot 700ms animation: brass capsule with
 * a checkmark + "Saved → Library" appears at card center, scales down 1.0 → 0.4
 * while sliding to the bottom-center of the screen, fading out at the end.
 *
 * Visual answer to "where did this just go" — gives the user a directional
 * cue that the recipe went into their saved/Library area, which on the bottom
 * nav lives behind the middle (Feed) tab.
 *
 * No-op on the very first composition (tick = 0). Each subsequent change to
 * tick fires the animation. We ignore the actual value, only the change.
 */
@Composable
private fun LibrarySuckOverlay(tick: Int, enabled: Boolean = true) {
  if (tick == 0) return
  // Replay guard. The StateFlow tick value persists across navigation, so
  // when the user leaves and returns to the deck the overlay would re-mount,
  // see a non-zero tick, and replay the last save's animation. Tracking the
  // consumed tick blocks that: only fresh increments after mount animate.
  var lastConsumed by remember { mutableStateOf(tick) }
  // User's toggle in Settings > Features. When off, mark this tick consumed
  // so a future re-enable doesn't replay backlog.
  if (!enabled) {
    lastConsumed = tick
    return
  }
  if (tick == lastConsumed) return
  // Read the reported bounds of the Library icon (top-right of the deck
  // header). We aim the brass capsule there so the user sees a clear path:
  // card center → Library icon, where the +1 badge lights up.
  val anchorRegistry = androidx.hilt.navigation.compose.hiltViewModel<app.pantrie.feature.walkthrough.WalkthroughViewModel>().anchors
  val anchors by anchorRegistry.collectAsState()
  val libraryBounds = anchors["deck_library_icon"]

  // Two-phase animation, total 1500ms:
  //   pop  (0..200ms)  — brass capsule scales 0 → 1.15 → 1.0 at card center,
  //                      grabbing the eye after the swipe-exit completes
  //   suck (200..1500ms) — capsule travels from card center to bottom-nav
  //                        region, scaling 1.0 → 0.35, fading at the tail
  //
  // Delaying 200ms before the suck means the user's eye is back on the deck
  // by the time the brass starts moving. Previously the capsule fired at the
  // same instant the card flew off-screen and got lost in peripheral motion.
  val progress = remember { androidx.compose.animation.core.Animatable(0f) }
  LaunchedEffect(tick) {
    progress.snapTo(0f)
    progress.animateTo(
      targetValue = 1f,
      animationSpec = androidx.compose.animation.core.tween(
        durationMillis = 1500,
        easing = androidx.compose.animation.core.LinearEasing,
      ),
    )
    // Mark this tick as fully shown. Next recomposition with the same value
    // will hit the early-return at the top, so the animation doesn't replay
    // when the user navigates away and back.
    lastConsumed = tick
  }

  val p = progress.value
  if (p >= 1f) return

  // Phase A: pop in (first 13% of timeline).
  // Phase B: suck (remaining 87% of timeline).
  val popPhase = (p / 0.13f).coerceIn(0f, 1f)
  val suckPhase = ((p - 0.13f) / 0.87f).coerceIn(0f, 1f)

  // Pop scale — 0 → 1.15 → 1.0 with a small overshoot for "magnetic snap" feel.
  val popScale = when {
    popPhase < 0.6f -> popPhase / 0.6f * 1.15f
    else -> 1.15f - ((popPhase - 0.6f) / 0.4f) * 0.15f
  }

  // Suck-phase eased travel + shrink. Easing is the standard fast-out-slow-in
  // applied to the suck phase only so the pop feels snappy and the suck feels
  // gravitational.
  val easedSuck = androidx.compose.animation.core.FastOutSlowInEasing.transform(suckPhase)
  val travelScale = 1f - 0.65f * easedSuck
  val finalScale = popScale * travelScale
  val alpha = if (suckPhase < 0.85f) 1f else (1f - (suckPhase - 0.85f) / 0.15f)

  Box(modifier = Modifier.fillMaxSize()) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val w = maxWidth
      val h = maxHeight
      val density = LocalDensity.current
      val startX = w / 2f
      val startY = h * 0.40f                 // visually center of the card area

      // Destination: the dead center of the Library icon at the top-right of
      // the deck header. If the icon hasn't reported its bounds yet (rare
      // pre-layout state), fall back to a top-right corner.
      val (endX, endY) = if (libraryBounds != null) {
        val cx = with(density) { libraryBounds.center.x.toDp() }
        val cy = with(density) { libraryBounds.center.y.toDp() }
        cx to cy
      } else {
        (w - 32.dp) to 64.dp
      }
      val curX = androidx.compose.ui.unit.lerp(startX, endX, easedSuck)
      val curY = androidx.compose.ui.unit.lerp(startY, endY, easedSuck)

      Box(
        modifier = Modifier
          .offset(x = curX - 96.dp, y = curY - 28.dp)
          .graphicsLayer {
            scaleX = finalScale
            scaleY = finalScale
            this.alpha = alpha
          },
      ) {
        // Inverse palette vs the pellets — DARK Ink body with BRASS text and
        // bookmark icon, plus a brass border ring. Cannot be confused with
        // the bright-brass falling chips. Reads as a single weighty object
        // sliding down vs the swarm of small chips going to Shop.
        Surface(
          shape = RoundedCornerShape(28.dp),
          color = Ink,
          shadowElevation = 16.dp,
          border = androidx.compose.foundation.BorderStroke(2.dp, BrassBright),
        ) {
          Row(
            Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            Icon(
              imageVector = Icons.Outlined.Bookmark,
              contentDescription = null,
              tint = BrassBright,
              modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
              "Saved → Library",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = BrassBright,
            )
          }
        }
      }
    }
  }
}

/**
 * Missing-ingredients celebration. Each saved recipe with un-pantried ingredients
 * spawns up to 6 brass chips at card center; each chip arcs down to the Shop
 * tile (bottom-center of the deck where the Shop quick-action button sits) on
 * a staggered timeline. Visually answers "where did the missing stuff just go."
 *
 * Reads its trigger from DeckViewModel.shoppingFall.tick. Names list is the
 * truncated label set, max 6 chips. tick=0 = idle, no render.
 */
@Composable
private fun ShoppingFallOverlay(tick: Int, names: List<String>, enabled: Boolean = true) {
  if (tick == 0 || names.isEmpty()) return
  // Replay guard — same pattern as LibrarySuckOverlay. Without it the pellets
  // re-cascade every time the user returns to the deck because the StateFlow
  // value persists across composition. Initialize to current tick so first
  // mount is a no-op; subsequent fresh increments fire the cascade.
  var lastConsumed by remember { mutableStateOf(tick) }
  if (!enabled) {
    lastConsumed = tick
    return
  }
  if (tick == lastConsumed) return

  // Per-pellet animatables, capped at the actual list size. We re-create them
  // each tick so a rapid second save resets the animation cleanly.
  val animatables = remember(tick) {
    names.map { androidx.compose.animation.core.Animatable(0f) }
  }
  LaunchedEffect(tick) {
    animatables.forEachIndexed { i, anim ->
      launch {
        // Stagger the start of each pellet by 140ms (was 80) and slow each
        // pellet to 1100ms (was 750) so the cascade is unmistakable. Total
        // animation now spans ~1.7s for 6 pellets instead of ~1.2s.
        kotlinx.coroutines.delay(i * 140L)
        anim.snapTo(0f)
        anim.animateTo(
          targetValue = 1f,
          animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 1100,
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
          ),
        )
      }
    }
  }

  // Skip rendering when every pellet finished. Prevents a permanent invisible
  // overlay (which would block touches if zIndex were higher).
  val allDone = animatables.all { it.value >= 1f }
  if (allDone) {
    // Mark this tick consumed so navigating back doesn't replay the cascade.
    lastConsumed = tick
    return
  }

  androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val w = maxWidth
    val h = maxHeight
    // Card center as start; Shop tile sits middle-bottom of deck (Pantry/Shop/Plan).
    val startX = w / 2f
    val startY = h * 0.30f
    val endX = w / 2f
    // Pellets land near the very bottom of the screen so they visually exit
    // the deck content and "fall into" the bottom-nav region. Previously
    // they stopped 36dp above the bottom and looked like they were falling
    // into nothing.
    val endY = h - 4.dp

    names.forEachIndexed { i, name ->
      val p = animatables.getOrNull(i)?.value ?: 0f
      if (p <= 0f || p >= 1f) return@forEachIndexed
      // Lateral jitter so chips don't stack on a single column. Spreads chips
      // ±60dp around start, all converging onto the Shop tile.
      val lateralOffsetDp = (((i % 3) - 1) * 60 + (i / 3) * 16).dp
      val curX = androidx.compose.ui.unit.lerp(startX + lateralOffsetDp, endX, p)
      // Parabolic Y: pellet rises slightly before falling toward the Shop tile,
      // giving the visceral "out of the card and down" feel.
      val arcLiftDp = (-32 * 4f * p * (1f - p)).dp
      val curY = androidx.compose.ui.unit.lerp(startY, endY, p) + arcLiftDp
      val scale = 1f - 0.35f * p
      val alpha = if (p < 0.85f) 1f else (1f - (p - 0.85f) / 0.15f)

      Box(
        modifier = Modifier
          .offset(x = curX - 60.dp, y = curY - 14.dp)
          .graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
          },
      ) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = BrassBright,
          shadowElevation = 4.dp,
        ) {
          Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Outlined.ShoppingCart,
              contentDescription = null,
              tint = Ink,
              modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              name.take(18),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = Ink,
              maxLines = 1,
              softWrap = false,
            )
          }
        }
      }
    }
  }
}

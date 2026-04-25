@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.pantrie.feature.deck

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
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
import app.pantrie.ui.IngredientEmoji
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

/** Soft background tints per cuisine — gives each card visual character. */
// Cuisine-specific card tints. Distinct enough to read at a glance, still editorial (no neon).
// Palette keyed to each cuisine's visual vocabulary: olive/sage for Italian, warm adobe for Mexican,
// turmeric for Indian, plum for Japanese, etc.
private val CUISINE_TINTS: Map<String, Color> = mapOf(
  "american" to Color(0xFFEED9CB),        // cream peach
  "italian" to Color(0xFFD4DCB6),         // basil/sage
  "mexican" to Color(0xFFECC4A8),         // warm adobe
  "thai" to Color(0xFFBFDCB5),            // lime/mint
  "chinese" to Color(0xFFEACB95),         // wheat/gold
  "japanese" to Color(0xFFE5C9D2),        // soft plum
  "indian" to Color(0xFFE8C58A),          // turmeric
  "french" to Color(0xFFD6C7E0),          // dusty lavender
  "mediterranean" to Color(0xFFB9D4DB),   // aegean blue
  "korean" to Color(0xFFE0B9B0),          // rose clay
  "middle eastern" to Color(0xFFE0CF9C),  // saffron
  "moroccan" to Color(0xFFE3AE7F),        // spice orange
  "spanish" to Color(0xFFE0A78B),         // paprika
  "greek" to Color(0xFFB8D2D3),           // white-blue
  "british" to Color(0xFFD0CCBB),         // oat
  "vietnamese" to Color(0xFFC7DDB8),      // herb green
  "caribbean" to Color(0xFFEBC88A),       // mango
)

private fun tintFor(cuisine: String?): Color {
  if (cuisine.isNullOrBlank()) return Paper
  return CUISINE_TINTS[cuisine.trim().lowercase()] ?: Paper
}

data class ToastState(val message: String, val undoId: String? = null, val undoAction: (() -> Unit)? = null)

@HiltViewModel
class DeckViewModel @Inject constructor(
  private val api: PantrieApi,
  private val analytics: app.pantrie.feature.beta.Analytics,
  private val refreshBus: app.pantrie.feature.app.RefreshBus,
  private val quota: app.pantrie.billing.SwipeQuotaRepository,
  private val entitlement: app.pantrie.billing.EntitlementRepository,
) : ViewModel() {

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

  // Declared BEFORE init{} — Kotlin initializes fields in declaration order, and init{}
  // calls refresh() which reads these. Moving them below the init block caused a NPE
  // crash on Tonight-tab open (late-init of StateFlow read during construction).
  private val _adventurous = MutableStateFlow(false)
  val adventurous = _adventurous.asStateFlow()

  private val _filter = MutableStateFlow<String?>(null)
  val filter = _filter.asStateFlow()

  private val _refreshing = MutableStateFlow(false)
  val refreshing = _refreshing.asStateFlow()

  init {
    refresh()
    // Re-fetch the deck whenever the pantry changes (new scan, manual add, clear, etc.).
    viewModelScope.launch {
      refreshBus.pantry.collect { refresh() }
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
      val t0 = System.currentTimeMillis()
      _error.value = null
      _refreshing.value = true
      val advFlag = if (_adventurous.value) 1 else null
      runCatching { api.deck(adventurous = advFlag, filter = _filter.value) }
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
    // Optimistic update — flip the `have` flag on this ingredient in the rendered deck so it
    // disappears from the missing-ingredients panel immediately.
    _state.value = _state.value?.let { s ->
      s.copy(deck = s.deck.map { r ->
        if (r.id != recipe.id) r
        else r.copy(ingredients = r.ingredients.map { i ->
          if (i.name == ing.name) i.copy(have = true) else i
        })
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
        _toast.value = ToastState("Added ${ing.name} to pantry")
      }.onFailure {
        _toast.value = ToastState("Couldn't add — try again")
      }
    }
  }

  fun save(r: Recipe) {
    // Optimistic: drop card from deck immediately for snappy UX
    _state.value = _state.value?.let { s ->
      s.copy(deck = s.deck.filter { it.id != r.id }, remaining = (s.remaining - 1).coerceAtLeast(0))
    }
    analytics.track("recipe_saved", mapOf("recipeId" to r.id, "match" to r.pantryMatchPercent))
    trackSwipeAndEmit()
    viewModelScope.launch {
      val resp = runCatching { api.interact(InteractRequest(recipeId = r.id, status = "saved")) }.getOrNull()
      val added = resp?.addedToShopping ?: 0
      if (added > 0) refreshBus.bumpShopping()
      _toast.value = ToastState(
        message = if (added > 0) "Saved · $added ingredient${if (added == 1) "" else "s"} added to Shop" else "Saved",
      )
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
      _toast.value = ToastState("Skipped")
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
      } else {
        _toast.value = ToastState("Marked cooked")
      }
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
  vm: DeckViewModel = hiltViewModel(),
) {
  val state by vm.state.collectAsState()
  val toast by vm.toast.collectAsState()
  val error by vm.error.collectAsState()
  val s = state

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
        // TODO(paywall): navigate to PaywallScreen route once added to MainActivity nav graph.
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

  Scaffold(containerColor = Cream) { padding ->
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
          IconButton(onClick = vm::toggleAdventurous, modifier = Modifier.size(40.dp)) {
            Icon(
              Icons.Filled.Restaurant,
              contentDescription = "Adventurous",
              tint = if (isAdventurous) Olive else InkMuted,
              modifier = Modifier.size(22.dp),
            )
          }
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
          IconButton(onClick = onOpenSearch, modifier = Modifier.size(40.dp)) {
            Icon(
              Icons.Outlined.Search,
              contentDescription = "Search recipes",
              tint = InkMuted,
              modifier = Modifier.size(22.dp),
            )
          }
          IconButton(onClick = onOpenSaved, modifier = Modifier.size(40.dp)) {
            Icon(
              Icons.Outlined.SoupKitchen,
              contentDescription = "Library",
              tint = Terracotta,
              modifier = Modifier.size(22.dp),
            )
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
                  containerColor = Paper,
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
              UpsellCard(message = s.message ?: "Come back tomorrow.", tier = s.tier)
            }
            s.deck.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              EmptyDeckMessage()
            }
            else -> CardStack(
              cards = s.deck,
              onSave = vm::save,
              onDismiss = vm::dismiss,
              onOpen = onOpenRecipe,
              onCook = { r -> vm.markCooked(r); onStartCook(r.id) },
              onAddToPantry = vm::addToPantry,
            )
          }

          // Loading overlay — shown when re-fetching the deck (e.g. filter changed) over an existing deck.
          val isRefreshing by vm.refreshing.collectAsState()
          if (isRefreshing && s != null && error == null) {
            Box(
              Modifier
                .fillMaxSize()
                .background(Cream.copy(alpha = 0.5f)),
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
            icon = Icons.Outlined.Kitchen,
            label = "Pantry",
            onClick = onOpenPantry,
            modifier = Modifier.weight(1f),
          )
          QuickActionButton(
            icon = Icons.Outlined.ShoppingCart,
            label = "Shop",
            onClick = onOpenShopping,
            modifier = Modifier.weight(1f),
          )
          QuickActionButton(
            icon = Icons.Outlined.CalendarMonth,
            label = "Plan",
            onClick = onOpenPlan,
            modifier = Modifier.weight(1f),
          )
        }
      }

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
                  Text("Undo", color = Color(0xFFF5E9E2), fontWeight = FontWeight.SemiBold)
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
) {
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
        // No photo: keep the top feeling intentional with a tinted band proportional to the card.
        Spacer(Modifier.weight(1f))
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
              recipe.pantryMatchPercent >= 50 -> Color(0xFFD4A017)
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
              iconTint = Color(0xFFD4A017),
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
              Surface(
                onClick = { onAddToPantry(ing) },
                shape = RoundedCornerShape(14.dp),
                color = Paper.copy(alpha = 0.8f),
                border = androidx.compose.foundation.BorderStroke(1.dp, InkFaint),
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
    Column(Modifier.fillMaxSize().padding(24.dp)) {
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
          Icon(Icons.Outlined.Star, null, tint = Color(0xFFD4A017), modifier = Modifier.size(14.dp))
          Spacer(Modifier.width(4.dp))
          Text(
            "%.1f average · ${recipe.ratingCount} review${if (recipe.ratingCount == 1) "" else "s"}",
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
    val emoji = IngredientEmoji.forName(ing.name)
    if (emoji != null) {
      Text(emoji, style = MaterialTheme.typography.bodyLarge)
      Spacer(Modifier.width(6.dp))
    }
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
private fun UpsellCard(message: String, tier: String) {
  Surface(shape = RoundedCornerShape(12.dp), color = Paper) {
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Text("That's your 10 for today.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      Text(
        message,
        style = MaterialTheme.typography.bodyMedium, color = InkMuted,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      )
      if (tier == "free") {
        Spacer(Modifier.height(16.dp))
        Button(
          onClick = { /* TODO: Play Billing */ },
          colors = ButtonDefaults.buttonColors(containerColor = Ink),
          shape = RoundedCornerShape(4.dp),
        ) { Text("Brimm Pro · unlimited swipes", color = Paper) }
      }
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
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(12.dp),
    color = Paper,
    border = androidx.compose.foundation.BorderStroke(1.dp, InkFaint),
    modifier = modifier.height(56.dp),
  ) {
    Row(
      Modifier.fillMaxSize().padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(8.dp))
      Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = Ink,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

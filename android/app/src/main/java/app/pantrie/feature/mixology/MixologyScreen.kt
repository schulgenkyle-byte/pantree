@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.pantrie.feature.mixology

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.feature.beta.Analytics
import app.pantrie.feature.settings.LocalSettingsStore
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.DeckResponse
import app.pantrie.network.dto.Ingredient
import app.pantrie.network.dto.InteractRequest
import app.pantrie.network.dto.Recipe
import app.pantrie.network.dto.Review
import app.pantrie.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

// Mixology uses the same swipe threshold as the Tonight deck for muscle-memory consistency.
private const val SWIPE_THRESHOLD_DP = 110f

// Sepia tint for the TRUE VINTAGE parchment background. Warmer than Cream, richer than Paper.
private val Sepia = Color(0xFFE6D3A7)
private val SepiaInk = Color(0xFF3A2B1A)

// Modern Mixology palette — full speakeasy black with gold accent. Classy, moody, bar-feel.
private val ModernBg = Color(0xFF0D0D0E)            // near-black with a hint of warmth
private val ModernCard = Color(0xFF18181B)          // slightly lighter for card vs bg separation
private val ModernInk = Color(0xFFE8E3D9)           // warm off-white text
private val ModernGold = Color(0xFFC9A554)          // aged brass accent
private val ModernInkMuted = Color(0xFF8B8578)

@HiltViewModel
class MixologyViewModel @Inject constructor(
  private val api: PantrieApi,
  private val analytics: Analytics,
  internal val localSettings: LocalSettingsStore,
  private val refreshBus: app.pantrie.feature.app.RefreshBus,
  internal val entitlement: app.pantrie.billing.EntitlementRepository,
) : ViewModel() {
  private val _state = MutableStateFlow<DeckResponse?>(null)
  val state = _state.asStateFlow()

  private val _error = MutableStateFlow<String?>(null)
  val error = _error.asStateFlow()

  private val _refreshing = MutableStateFlow(false)
  val refreshing = _refreshing.asStateFlow()

  private val _toast = MutableStateFlow<String?>(null)
  val toast = _toast.asStateFlow()

  // Cache of reviews per recipe — fetched lazily on page-3 view. Keyed by recipe_id.
  private val _reviewsByRecipe = MutableStateFlow<Map<String, List<Review>>>(emptyMap())
  val reviewsByRecipe = _reviewsByRecipe.asStateFlow()

  init { refresh() }

  fun refresh() {
    viewModelScope.launch {
      _refreshing.value = true
      _error.value = null
      // Pull cocktails — backend returns both historic + modern; client filters by mode.
      runCatching { api.deck(contentType = "cocktail") }
        .onSuccess { _state.value = it }
        .onFailure { _error.value = it.message ?: "Couldn't load cocktails" }
      _refreshing.value = false
    }
  }

  /** "Pan it" — backend endpoint TBD; for now we just fire analytics + toast. */
  fun panIt(r: Recipe) {
    analytics.track("pan_clicked", mapOf("recipe_id" to r.id, "content_type" to r.contentType))
    // TODO: wire real pan-it backend endpoint once available.
    // No toast — the icon state change (empty → filled) is the confirmation.
  }

  fun save(r: Recipe) {
    _state.value = _state.value?.let { s ->
      s.copy(deck = s.deck.filter { it.id != r.id }, remaining = (s.remaining - 1).coerceAtLeast(0))
    }
    analytics.track("recipe_saved", mapOf("recipe_id" to r.id, "content_type" to r.contentType))
    viewModelScope.launch {
      runCatching { api.interact(InteractRequest(recipeId = r.id, status = "saved")) }
      // Backend auto-adds missing cocktail ingredients to shopping. Tell other tabs to refresh.
      refreshBus.bumpShopping()
    }
  }

  fun dismiss(r: Recipe) {
    _state.value = _state.value?.let { s ->
      s.copy(deck = s.deck.filter { it.id != r.id }, remaining = (s.remaining - 1).coerceAtLeast(0))
    }
    analytics.track("recipe_dismissed", mapOf("recipe_id" to r.id, "content_type" to r.contentType))
    viewModelScope.launch {
      runCatching { api.interact(InteractRequest(recipeId = r.id, status = "dismissed")) }
    }
  }

  fun onFlip(r: Recipe) {
    analytics.track("recipe_flipped", mapOf("recipe_id" to r.id, "content_type" to r.contentType))
  }

  fun onPageTurn(r: Recipe, pageIndex: Int) {
    analytics.track("page_turned", mapOf("recipe_id" to r.id, "page_index" to pageIndex))
  }

  fun onVintageToggle(r: Recipe, on: Boolean) {
    analytics.track(
      if (on) "vintage_toggle_on" else "vintage_toggle_off",
      mapOf("recipe_id" to r.id),
    )
  }

  fun onStoryDwell(r: Recipe, pageIndex: Int, dwellSeconds: Int) {
    if (dwellSeconds <= 0) return
    analytics.track(
      "story_page_view",
      mapOf("recipe_id" to r.id, "page_index" to pageIndex, "dwell_seconds" to dwellSeconds),
    )
  }

  fun loadReviewsFor(recipeId: String) {
    if (_reviewsByRecipe.value.containsKey(recipeId)) return
    viewModelScope.launch {
      runCatching { api.reviewFeed() }
        .onSuccess { resp ->
          val filtered = resp.reviews.filter { it.recipeId == recipeId }
          _reviewsByRecipe.value = _reviewsByRecipe.value + (recipeId to filtered)
        }
        .onFailure {
          _reviewsByRecipe.value = _reviewsByRecipe.value + (recipeId to emptyList())
        }
    }
  }

  fun getNote(recipeId: String): String = localSettings.getMixologyNote(recipeId)
  fun setNote(recipeId: String, note: String) = localSettings.setMixologyNote(recipeId, note)

  fun clearToast() { _toast.value = null }
}

@Composable
fun MixologyScreen(
  onOpenSaved: () -> Unit = {},
  onScanBar: () -> Unit = {},
  onOpenShopping: () -> Unit = {},
  onOpenPlan: () -> Unit = {},
  onOpenSearch: () -> Unit = {},
  onOpenPaywall: () -> Unit = {},
  /** "Photo your pour" tap on the mixology back-card NotesPage. Receives the
   *  recipe id so the route can pre-fill /recipes/:id/contribute-photo. */
  onSubmitDrinkPhoto: (String) -> Unit = {},
  vm: MixologyViewModel = hiltViewModel(),
) {
  val state by vm.state.collectAsState()
  val error by vm.error.collectAsState()
  val toast by vm.toast.collectAsState()
  val s = state

  // Inline ad cadence: insert an "Ad" card overlay after every 7th swipe (Pro skips this).
  // Counter survives rotation but resets on full app kill — by design, since launching the app
  // is a fresh session and re-counting is fine.
  var swipeCount by rememberSaveable { mutableStateOf(0) }
  var showAdOverlay by remember { mutableStateOf(false) }
  val isPro by vm.entitlement.isPro.collectAsState()
  fun onSwipeCounted() {
    if (isPro) return
    swipeCount += 1
    if (swipeCount > 0 && swipeCount % 7 == 0) showAdOverlay = true
  }

  // Tab-level VINTAGE/MODERN mode. Flips the entire UI theme + card content.
  // VINTAGE = sepia parchment + serif + verbatim original_text. MODERN = current clean theme + modernized.
  var vintageMode by rememberSaveable { mutableStateOf(true) }

  val bgColor = if (vintageMode) Sepia else ModernBg
  val titleColor = if (vintageMode) SepiaInk else ModernGold
  val titleFont = if (vintageMode) FontFamily.Serif else FontFamily.Default

  Scaffold(containerColor = bgColor) { padding ->
    Box(Modifier.padding(padding).fillMaxSize()) {
      Column(Modifier.fillMaxSize()) {
        // Top bar — bar icon, section title, cookbook button.
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            Icons.Outlined.LocalBar,
            contentDescription = null,
            tint = if (vintageMode) SepiaInk else ModernGold,
            modifier = Modifier.size(24.dp),
          )
          Spacer(Modifier.width(8.dp))
          Text(
            "Mixology",
            style = MaterialTheme.typography.titleLarge,
            fontFamily = titleFont,
            fontWeight = FontWeight.SemiBold,
            color = titleColor,
            modifier = Modifier.weight(1f),
          )
          IconButton(onClick = onOpenSearch) {
            Icon(
              Icons.Outlined.Search,
              contentDescription = "Search cocktails",
              tint = if (vintageMode) SepiaInk else ModernGold,
            )
          }
          IconButton(onClick = onOpenSaved) {
            Icon(
              Icons.Outlined.LocalBar,
              contentDescription = "Saved cocktails",
              tint = if (vintageMode) SepiaInk else ModernGold,
            )
          }
        }

        // VINTAGE ↔ MODERN segmented toggle — flips whole UI theme AND card content.
        Row(
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.Center,
        ) {
          Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (vintageMode) SepiaInk.copy(alpha = 0.12f) else Ink.copy(alpha = 0.08f),
            border = androidx.compose.foundation.BorderStroke(
              1.dp,
              if (vintageMode) SepiaInk.copy(alpha = 0.3f) else InkFaint,
            ),
          ) {
            Row(Modifier.padding(4.dp)) {
              // VINTAGE half
              Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (vintageMode) SepiaInk else Color.Transparent,
                modifier = Modifier.clickable { vintageMode = true },
              ) {
                Text(
                  "BOOTLEGGER",
                  modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                  style = MaterialTheme.typography.labelLarge,
                  fontFamily = FontFamily.Serif,
                  fontWeight = FontWeight.SemiBold,
                  color = if (vintageMode) Sepia else SepiaInk.copy(alpha = 0.7f),
                  letterSpacing = 1.sp,
                )
              }
              // MODERN half
              Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (!vintageMode) Ink else Color.Transparent,
                modifier = Modifier.clickable { vintageMode = false },
              ) {
                Text(
                  "MIXOLOGIST",
                  modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.SemiBold,
                  color = if (!vintageMode) Paper else Ink.copy(alpha = 0.55f),
                  letterSpacing = 1.sp,
                )
              }
            }
          }
        }

        // VINTAGE mode = only historic cocktails with verbatim original_text.
        // MODERN mode = only modern cocktails WITH a real photo (no photo → don't ship). The user
        // saw too many sparse cards; gating on imageUrl forces visual quality.
        val filteredDeck = remember(s?.deck, vintageMode) {
          // Defensive content_type guard — server should already filter to cocktail/mocktail
          // when called with contentType="cocktail", but guarantee it client-side too so any
          // future ingest mis-tagging (food rows accidentally tagged 'cocktail') can't leak
          // into Mixology and render as a broken cocktail card.
          val deck = (s?.deck ?: emptyList()).filter {
            it.contentType == "cocktail" || it.contentType == "mocktail"
          }
          if (vintageMode) deck.filter { it.isHistoric && !it.originalText.isNullOrBlank() }
          else deck.filter { !it.isHistoric && !it.imageUrl.isNullOrBlank() }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp), contentAlignment = Alignment.TopCenter) {
          // Ad overlay sits on top of the deck when swipeCount hits a multiple of 7.
          // User taps "Continue" to dismiss; ad never blocks the underlying state.
          if (showAdOverlay) {
            app.pantrie.billing.InlineAdCard(
              onContinue = { showAdOverlay = false },
              vintageMode = vintageMode,
            )
            return@Box
          }
          // Swipe counter removed — felt nag-y. Empty-deck state still informs when daily cap is hit.
          when {
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Couldn't load", style = MaterialTheme.typography.titleMedium, color = Terracotta, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                Spacer(Modifier.height(16.dp))
                Button(onClick = vm::refresh, colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
                  Text("Retry", color = Paper)
                }
              }
            }
            s == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(color = Ink)
            }
            filteredDeck.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                  if (vintageMode) "No more vintage cocktails." else "The bar is closed.",
                  style = MaterialTheme.typography.titleLarge,
                  fontFamily = if (vintageMode) FontFamily.Serif else FontFamily.Default,
                  color = if (vintageMode) SepiaInk else Ink,
                  fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                  "Add spirits or mixers to your pantry for fresh cocktails.",
                  style = MaterialTheme.typography.bodyMedium, color = InkMuted,
                )
              }
            }
            else -> MixCardStack(
              cards = filteredDeck,
              vintageMode = vintageMode,
              onSave = { r -> vm.save(r); onSwipeCounted() },
              onDismiss = { r -> vm.dismiss(r); onSwipeCounted() },
              onFlip = vm::onFlip,
              onPageTurn = vm::onPageTurn,
              onVintageToggle = vm::onVintageToggle,
              onStoryDwell = vm::onStoryDwell,
              onPanIt = vm::panIt,
              loadReviewsFor = vm::loadReviewsFor,
              reviewsByRecipe = vm.reviewsByRecipe.collectAsState().value,
              getNote = vm::getNote,
              setNote = vm::setNote,
              onSubmitDrinkPhoto = onSubmitDrinkPhoto,
            )
          }
        }

        // Quick actions: Snap bar · Shop · Plan — same pattern as Home, themed sepia.
        MixologyQuickActions(
          vintageMode = vintageMode,
          onScanBar = onScanBar,
          onOpenShopping = onOpenShopping,
          onOpenPlan = onOpenPlan,
        )
      }

      toast?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(2500); vm.clearToast() }
        Box(Modifier.fillMaxSize().padding(bottom = 90.dp), contentAlignment = Alignment.BottomCenter) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = Ink,
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
          ) {
            Text(
              msg, color = Paper, style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(14.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MixCardStack(
  cards: List<Recipe>,
  vintageMode: Boolean,
  onSave: (Recipe) -> Unit,
  onDismiss: (Recipe) -> Unit,
  onFlip: (Recipe) -> Unit,
  onPageTurn: (Recipe, Int) -> Unit,
  onVintageToggle: (Recipe, Boolean) -> Unit,
  onStoryDwell: (Recipe, Int, Int) -> Unit,
  onPanIt: (Recipe) -> Unit,
  loadReviewsFor: (String) -> Unit,
  reviewsByRecipe: Map<String, List<Review>>,
  getNote: (String) -> String,
  setNote: (String, String) -> Unit,
  onSubmitDrinkPhoto: (String) -> Unit = {},
) {
  val visible = cards.take(3)
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    // Depth cards behind the top.
    visible.drop(1).reversed().forEachIndexed { idx, _ ->
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
        colors = CardDefaults.cardColors(
          containerColor = if (vintageMode) Sepia else CreamAlt,
        ),
        shape = RoundedCornerShape(16.dp),
      ) { }
    }

    visible.firstOrNull()?.let { top ->
      key(top.id) {
        DraggableMixCard(
          recipe = top,
          vintageMode = vintageMode,
          onSave = { onSave(top) },
          onDismiss = { onDismiss(top) },
          onFlip = { onFlip(top) },
          onPageTurn = { page -> onPageTurn(top, page) },
          onVintageToggle = { on -> onVintageToggle(top, on) },
          onStoryDwell = { page, secs -> onStoryDwell(top, page, secs) },
          onPanIt = { onPanIt(top) },
          loadReviews = { loadReviewsFor(top.id) },
          reviewsForThis = reviewsByRecipe[top.id] ?: emptyList(),
          initialNote = getNote(top.id),
          onNoteChange = { setNote(top.id, it) },
          onSubmitDrinkPhoto = onSubmitDrinkPhoto,
        )
      }
    }
  }
}

@Composable
private fun DraggableMixCard(
  recipe: Recipe,
  vintageMode: Boolean,
  onSave: () -> Unit,
  onDismiss: () -> Unit,
  onFlip: () -> Unit,
  onPageTurn: (Int) -> Unit,
  onVintageToggle: (Boolean) -> Unit,
  onStoryDwell: (Int, Int) -> Unit,
  onPanIt: () -> Unit,
  loadReviews: () -> Unit,
  reviewsForThis: List<Review>,
  initialNote: String,
  onNoteChange: (String) -> Unit,
  onSubmitDrinkPhoto: (String) -> Unit = {},
) {
  val density = LocalDensity.current
  var offsetX by remember { mutableFloatStateOf(0f) }
  var offsetY by remember { mutableFloatStateOf(0f) }
  var flipped by remember { mutableStateOf(false) }
  var exitDir by remember { mutableIntStateOf(0) }
  val thresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }

  val animatedOffsetX by animateFloatAsState(
    targetValue = if (exitDir != 0) exitDir * 1800f else offsetX,
    animationSpec = tween(durationMillis = if (exitDir != 0) 240 else 0),
    finishedListener = {
      if (exitDir == 1) onSave()
      else if (exitDir == -1) onDismiss()
    },
    label = "mixCardX",
  )
  val rotation = (animatedOffsetX / 30f).coerceIn(-18f, 18f)
  val saveOverlay = (offsetX / thresholdPx).coerceIn(0f, 1f)
  val skipOverlay = (-offsetX / thresholdPx).coerceIn(0f, 1f)

  val rotationYAnim by animateFloatAsState(
    targetValue = if (flipped) 180f else 0f,
    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
    label = "mixFlipY",
  )

  Box(
    Modifier
      .fillMaxWidth()
      .fillMaxHeight()
      .offset { IntOffset(animatedOffsetX.roundToInt(), offsetY.roundToInt()) }
      .rotate(rotation)
      .graphicsLayer { rotationY = rotationYAnim; cameraDistance = 12f * density.density }
      .pointerInput(recipe.id, flipped) {
        // Disable swipe-to-dismiss once flipped — the back face is a pager and needs
        // horizontal drags for page turns. Pager will consume, but we also gate here.
        if (!flipped) {
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
        }
      },
  ) {
    if (rotationYAnim < 90f) {
      MixCardFront(
        recipe = recipe,
        vintageMode = vintageMode,
        onFlip = {
          flipped = true
          onFlip()
        },
        onPanIt = onPanIt,
        onSave = { exitDir = 1 },
      )
    } else {
      Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
        MixCardBack(
          recipe = recipe,
          onFlipBack = { flipped = false },
          onPageTurn = onPageTurn,
          onVintageToggle = onVintageToggle,
          onStoryDwell = onStoryDwell,
          onPanIt = onPanIt,
          onSave = onSave,
          loadReviews = loadReviews,
          reviewsForThis = reviewsForThis,
          initialNote = initialNote,
          onNoteChange = onNoteChange,
          onSubmitDrinkPhoto = onSubmitDrinkPhoto,
        )
      }
    }

    // Swipe overlays
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

/** Quick-action row on Mixology — parallel to Home. Snap bar · Shop · Plan.
 *  Sepia-themed when in vintage mode so it doesn't break the aesthetic. */
@Composable
private fun MixologyQuickActions(
  vintageMode: Boolean,
  onScanBar: () -> Unit,
  onOpenShopping: () -> Unit,
  onOpenPlan: () -> Unit,
) {
  val bg = if (vintageMode) Sepia else ModernCard
  val ink = if (vintageMode) SepiaInk else ModernGold
  val borderCol = if (vintageMode) SepiaInk.copy(alpha = 0.3f) else ModernGold.copy(alpha = 0.3f)
  Row(
    Modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    MixologyActionButton(
      icon = Icons.Outlined.CameraAlt, label = "Snap bar",
      bg = bg, ink = ink, border = borderCol,
      onClick = onScanBar, modifier = Modifier.weight(1f),
      vintageMode = vintageMode,
    )
    MixologyActionButton(
      icon = Icons.Outlined.ShoppingCart, label = "Shop",
      bg = bg, ink = ink, border = borderCol,
      onClick = onOpenShopping, modifier = Modifier.weight(1f),
      vintageMode = vintageMode,
    )
    MixologyActionButton(
      icon = Icons.Outlined.CalendarMonth, label = "Plan",
      bg = bg, ink = ink, border = borderCol,
      onClick = onOpenPlan, modifier = Modifier.weight(1f),
      vintageMode = vintageMode,
    )
  }
}

@Composable
private fun MixologyActionButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  bg: Color,
  ink: Color,
  border: Color,
  onClick: () -> Unit,
  vintageMode: Boolean,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(12.dp),
    color = bg,
    border = androidx.compose.foundation.BorderStroke(1.dp, border),
    modifier = modifier.height(52.dp),
  ) {
    Row(
      Modifier.fillMaxSize().padding(horizontal = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(6.dp))
      Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = ink,
        fontFamily = if (vintageMode) FontFamily.Serif else FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

/** Format social counts TikTok-style: 999 → "999", 1.2k, 4.5k, 12k, 1.2M. */
private fun formatSocialCount(n: Int): String = when {
  n < 1_000 -> n.toString()
  n < 10_000 -> "%.1fk".format(n / 1000.0)
  n < 1_000_000 -> "${n / 1000}k"
  else -> "%.1fM".format(n / 1_000_000.0)
}

/** Vintage wear overlay — burnt edges + bookworm holes, randomized per-card by recipe.id
 *  so every card feels like a different aged page, but each specific card always looks the same. */
@Composable
private fun VintageWearOverlay(recipeId: String, modifier: Modifier = Modifier) {
  androidx.compose.foundation.Canvas(modifier = modifier) {
    val rng = java.util.Random(recipeId.hashCode().toLong())
    val burnColor = Color(0xFF2A1810)
    val burnSoft = Color(0xFF4A2D18)

    // Burnt edges — 4 to 7 overlapping dark blobs along random edges
    val numBurns = 4 + rng.nextInt(4)
    repeat(numBurns) {
      val edge = rng.nextInt(4)
      val along = rng.nextFloat()
      val depth = 18f + rng.nextFloat() * 50f
      val (cx, cy) = when (edge) {
        0 -> along * size.width to rng.nextFloat() * depth
        1 -> size.width - rng.nextFloat() * depth to along * size.height
        2 -> along * size.width to size.height - rng.nextFloat() * depth
        else -> rng.nextFloat() * depth to along * size.height
      }
      // Overlapping circles = irregular scorched blob
      repeat(6) {
        val offX = (rng.nextFloat() - 0.5f) * 40f
        val offY = (rng.nextFloat() - 0.5f) * 40f
        val r = 12f + rng.nextFloat() * 28f
        val alpha = 0.18f + rng.nextFloat() * 0.22f
        drawCircle(
          color = burnColor.copy(alpha = alpha),
          radius = r,
          center = androidx.compose.ui.geometry.Offset(cx + offX, cy + offY),
        )
      }
      // Softer scorch halo
      drawCircle(
        color = burnSoft.copy(alpha = 0.12f),
        radius = 40f + rng.nextFloat() * 30f,
        center = androidx.compose.ui.geometry.Offset(cx, cy),
      )
    }

    // Bookworm holes — 2 to 5 small dark punctures, only in outer margins to avoid text
    val holeColor = Color(0xFF1A1008)
    val marginFrac = 0.07f
    val numHoles = 2 + rng.nextInt(4)
    repeat(numHoles) {
      val onLeftSide = rng.nextBoolean()
      val x = if (onLeftSide) rng.nextFloat() * size.width * marginFrac
              else size.width - rng.nextFloat() * size.width * marginFrac
      val y = rng.nextFloat() * size.height
      val radius = 3f + rng.nextFloat() * 4f
      // Darker ring around hole = worn paper
      drawCircle(
        color = Color(0xFF6A4A2A).copy(alpha = 0.35f),
        radius = radius * 1.9f,
        center = androidx.compose.ui.geometry.Offset(x, y),
      )
      // The hole itself
      drawCircle(
        color = holeColor.copy(alpha = 0.75f),
        radius = radius,
        center = androidx.compose.ui.geometry.Offset(x, y),
      )
    }
  }
}

/** Plain-English description for a cocktail method. Shown on the card so users don't need
 *  to Google "what does 'built' mean in a cocktail?" — which they absolutely would. */
private fun methodDescription(method: String?): String = when (method?.trim()?.lowercase()) {
  "built" -> "Mix straight in the serving glass — no shaker needed"
  "stirred" -> "Stir over ice in a mixing glass, then strain to serve"
  "shaken" -> "Shake hard with ice, then strain into a chilled glass"
  "blended" -> "Blend with crushed ice until smooth"
  "muddled" -> "Muddle the ingredients in the glass before adding ice"
  "layered" -> "Pour each spirit slowly over a bar spoon to form layers"
  "rolled" -> "Pour back and forth between two tins to mix without aerating"
  "thrown" -> "Pour from one tin to another at height to gently aerate"
  "flash blended" -> "Briefly blend 3-5 seconds to combine"
  "swizzled" -> "Stir vigorously with a swizzle stick in crushed ice"
  "floated" -> "Float the final ingredient on top by pouring over a spoon"
  "hot" -> "Heat the liquid gently — do not boil — and pour into a warmed glass"
  null, "" -> ""
  else -> method  // fall back to original if we don't recognize it
}

/** Emoji glyph for a given glass type. Emoji is fine for MVP. */
private fun glassEmoji(glass: String?): String = when (glass?.trim()?.lowercase()) {
  "rocks", "old fashioned", "lowball" -> "🥃"    // tumbler
  "coupe" -> "🍸"                                 // cocktail
  "highball", "collins" -> "🥤"                   // cup
  "flute", "champagne" -> "🍾"                    // bottle
  "martini" -> "🍸"                                // cocktail
  "shot" -> "🥃"                                   // tumbler (shot reads similarly)
  else -> "🍸"
}

@Composable
private fun MixCardFront(
  recipe: Recipe,
  vintageMode: Boolean,
  onFlip: () -> Unit,
  onPanIt: () -> Unit,
  onSave: () -> Unit,
) {
  val cardBg = if (vintageMode) Sepia else ModernCard
  val textInk = if (vintageMode) SepiaInk else ModernInk
  val titleFont = if (vintageMode) FontFamily.Serif else FontFamily.Default
  Card(
    modifier = Modifier.fillMaxSize(),
    colors = CardDefaults.cardColors(containerColor = cardBg),
    shape = RoundedCornerShape(16.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = if (vintageMode) 10.dp else 6.dp),
  ) {
    // Vintage mode: layer aged-paper texture + per-card wear overlay (burnt edges +
    // bookworm holes) so every card feels like a uniquely damaged page.
    Box(Modifier.fillMaxSize()) {
      if (vintageMode) {
        Image(
          painter = painterResource(id = app.pantrie.R.drawable.vintage_paper),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
        VintageWearOverlay(recipeId = recipe.id, modifier = Modifier.fillMaxSize())
      }
    Column(
      Modifier
        .fillMaxSize()
        .then(
          if (vintageMode)
            Modifier.border(
              width = 1.dp,
              color = SepiaInk.copy(alpha = 0.25f),
              shape = RoundedCornerShape(16.dp),
            )
          else Modifier
        )
        .padding(if (vintageMode) 16.dp else 20.dp),
    ) {
      if (vintageMode) {
        // VINTAGE title — tight, no wasted space, recipe body gets the stage
        Text(
          recipe.title.uppercase(),
          style = MaterialTheme.typography.titleMedium,
          fontFamily = FontFamily.Serif,
          fontWeight = FontWeight.Bold,
          color = SepiaInk,
          lineHeight = 22.sp,
          letterSpacing = 1.sp,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
      } else {
        // MODERN header — glass emoji + title, one line. Editorial.
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(glassEmoji(recipe.glassType), fontSize = 28.sp)
          Spacer(Modifier.width(10.dp))
          Text(
            recipe.title,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = titleFont,
            fontWeight = FontWeight.SemiBold,
            color = textInk,
            lineHeight = 26.sp,
            maxLines = 2,
            modifier = Modifier.weight(1f),
          )
        }
      }

      Spacer(Modifier.height(8.dp))

      // Badge row — match %, ABV (if alcoholic), glass name.
      // Hidden in VINTAGE mode: badges break the book-page feel.
      if (!vintageMode) Row(verticalAlignment = Alignment.CenterVertically) {
        MixBadge(
          text = "${recipe.pantryMatchPercent}%",
          bg = when {
            recipe.pantryMatchPercent >= 80 -> Olive
            recipe.pantryMatchPercent >= 50 -> Color(0xFFD4A017)
            recipe.pantryMatchPercent > 0 -> Ink.copy(alpha = 0.45f)
            else -> InkFaint
          },
        )
        if (recipe.isAlcoholic && recipe.abvPercent != null && recipe.abvPercent > 0) {
          Spacer(Modifier.width(6.dp))
          MixBadge(text = "${"%.0f".format(recipe.abvPercent)}% ABV", bg = TerracottaDeep)
        }
        if (!recipe.glassType.isNullOrBlank()) {
          Spacer(Modifier.width(6.dp))
          MixBadge(text = recipe.glassType.uppercase(), bg = Ink.copy(alpha = 0.55f))
        }
      }

      Spacer(Modifier.height(16.dp))

      if (vintageMode && !recipe.originalText.isNullOrBlank()) {
        // VINTAGE body — recipe gets ~90% of card. Single tiny eyebrow, no ornament rule,
        // source credit is one italic line at the end.
        val yearLine = recipe.sourceYear?.toString()
        if (!yearLine.isNullOrBlank()) {
          Text(
            yearLine,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Serif,
            color = SepiaInk.copy(alpha = 0.6f),
            fontStyle = FontStyle.Italic,
            letterSpacing = 1.5.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(Modifier.height(6.dp))
        }

        // The recipe body — takes ALL remaining vertical space
        Column(
          Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        ) {
          Text(
            recipe.originalText!!.trim(),
            fontSize = 17.sp,
            lineHeight = 28.sp,
            fontFamily = FontFamily.Serif,
            color = SepiaInk,
            textAlign = androidx.compose.ui.text.style.TextAlign.Justify,
          )
          // Tiny source credit at the very bottom of the scroll column
          if (!recipe.sourceBook.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
              "— ${recipe.sourceBook}",
              style = MaterialTheme.typography.labelSmall,
              fontFamily = FontFamily.Serif,
              fontStyle = FontStyle.Italic,
              color = SepiaInk.copy(alpha = 0.5f),
              textAlign = androidx.compose.ui.text.style.TextAlign.End,
              maxLines = 1,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      } else {
        // MODERNIZED body — image at top (the cocktail photo), then ingredients + method.
        if (!recipe.imageUrl.isNullOrBlank()) {
          AsyncImage(
            model = recipe.imageUrl,
            contentDescription = recipe.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp)
              .clip(RoundedCornerShape(10.dp)),
          )
          Spacer(Modifier.height(12.dp))
        }
        // Theme-aware text colors so nothing reads as dark-on-dark.
        val labelColor = if (vintageMode) SepiaInk.copy(alpha = 0.7f) else ModernGold
        val bodyColor = if (vintageMode) SepiaInk else ModernInk
        val mutedColor = if (vintageMode) SepiaInk.copy(alpha = 0.55f) else ModernInkMuted
        val accentColor = if (vintageMode) Terracotta else ModernGold
        Text(
          "Ingredients",
          style = MaterialTheme.typography.labelMedium,
          color = labelColor, fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        val ingShown = recipe.ingredients.take(6)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          ingShown.forEach { MixIngredientLine(it, vintageMode) }
          if (recipe.ingredients.size > 6) {
            Text(
              "+${recipe.ingredients.size - 6} more on the back",
              style = MaterialTheme.typography.labelSmall,
              color = mutedColor,
              fontStyle = FontStyle.Italic,
            )
          }
        }

        Spacer(Modifier.height(14.dp))

        Text(
          "Method",
          style = MaterialTheme.typography.labelMedium,
          color = labelColor, fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        val stepsShown = recipe.steps.take(4)
        if (stepsShown.isEmpty() && !recipe.method.isNullOrBlank()) {
          Text(methodDescription(recipe.method), style = MaterialTheme.typography.bodyMedium, color = bodyColor)
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            stepsShown.forEachIndexed { i, step ->
              Row {
                Text(
                  "${i + 1}.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = accentColor,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.width(20.dp),
                )
                Text(
                  step.text,
                  style = MaterialTheme.typography.bodyMedium,
                  color = bodyColor,
                )
              }
            }
          }
        }
      }

      Spacer(Modifier.height(10.dp))

      // TikTok-style action footer: Pan on the LEFT edge with live count under it,
      // Save on the RIGHT edge with count under, flip hint dead center.
      var panned by remember(recipe.id) { mutableStateOf(false) }
      var saved by remember(recipe.id) { mutableStateOf(false) }
      val barIconColor = if (vintageMode) SepiaInk else ModernGold
      val panCount = recipe.panCount + (if (panned) 1 else 0)
      val saveCount = recipe.saveCount + (if (saved) 1 else 0)

      Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // PAN — left edge: heart icon stacked over count
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = { panned = !panned; onPanIt() },
          ),
        ) {
          Icon(
            if (panned) Icons.Filled.Favorite else Icons.Outlined.Favorite,
            contentDescription = "Pan it",
            tint = if (panned) (if (vintageMode) Color(0xFF8B1E1E) else Terracotta) else barIconColor,
            modifier = Modifier.size(28.dp),
          )
          Spacer(Modifier.height(2.dp))
          Text(
            formatSocialCount(panCount),
            style = MaterialTheme.typography.labelSmall,
            color = barIconColor,
            fontFamily = if (vintageMode) FontFamily.Serif else FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
          )
        }

        Spacer(Modifier.weight(1f))

        // FLIP hint — center, no ripple
        Text(
          "flip for the story →",
          style = MaterialTheme.typography.labelMedium,
          color = if (vintageMode) SepiaInk.copy(alpha = 0.7f) else InkMuted,
          fontFamily = if (vintageMode) FontFamily.Serif else FontFamily.Default,
          fontStyle = FontStyle.Italic,
          modifier = Modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = onFlip,
          ),
        )

        Spacer(Modifier.weight(1f))

        // SAVE — right edge: bookmark icon stacked over count
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = { saved = !saved; onSave() },
          ),
        ) {
          Icon(
            if (saved) Icons.Filled.Bookmark else Icons.Outlined.Bookmark,
            contentDescription = "Save to Cookbook",
            tint = barIconColor,
            modifier = Modifier.size(28.dp),
          )
          Spacer(Modifier.height(2.dp))
          Text(
            formatSocialCount(saveCount),
            style = MaterialTheme.typography.labelSmall,
            color = barIconColor,
            fontFamily = if (vintageMode) FontFamily.Serif else FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
      }  // end Column
    }  // end Box (vintage paper texture wrapper)
  }
}

@Composable
private fun MixIngredientLine(ing: Ingredient, vintageMode: Boolean = false) {
  val textColor = when {
    vintageMode -> if (ing.have) SepiaInk else SepiaInk.copy(alpha = 0.7f)
    else -> if (ing.have) ModernInk else ModernInk.copy(alpha = 0.65f)
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(6.dp).background(if (ing.have) Olive else InkFaint, CircleShape))
    Spacer(Modifier.width(8.dp))
    Text(
      listOfNotNull(
        ing.quantity?.takeIf { it > 0.0 }?.let { q ->
          if (q == q.toInt().toDouble()) q.toInt().toString() else "%.1f".format(q)
        },
        ing.unit?.takeIf { it.isNotBlank() },
        ing.name,
      ).joinToString(" "),
      style = MaterialTheme.typography.bodyMedium,
      color = textColor,
    )
  }
}

@Composable
private fun MixBadge(text: String, bg: Color) {
  Surface(color = bg, shape = RoundedCornerShape(4.dp)) {
    Text(
      text,
      color = Paper,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    )
  }
}

// ---------------- BACK FACE ----------------

@Composable
private fun MixCardBack(
  recipe: Recipe,
  onFlipBack: () -> Unit,
  onPageTurn: (Int) -> Unit,
  onVintageToggle: (Boolean) -> Unit,
  onStoryDwell: (Int, Int) -> Unit,
  onPanIt: () -> Unit,
  onSave: () -> Unit,
  loadReviews: () -> Unit,
  reviewsForThis: List<Review>,
  initialNote: String,
  onNoteChange: (String) -> Unit,
  onSubmitDrinkPhoto: (String) -> Unit = {},
) {
  val pagerState = rememberPagerState(pageCount = { 4 })

  // Fire page_turned on each settled page change (skip first emission at page 0).
  LaunchedEffect(pagerState) {
    var last = pagerState.currentPage
    snapshotFlow { pagerState.currentPage }.collect { page ->
      if (page != last) {
        onPageTurn(page)
        last = page
      }
    }
  }

  // Dwell timer per page. Every second we increment; on page change we flush.
  DwellTracker(currentPage = pagerState.currentPage, onDwell = { page, secs -> onStoryDwell(page, secs) })

  Card(
    modifier = Modifier.fillMaxSize(),
    colors = CardDefaults.cardColors(containerColor = Cream),
    shape = RoundedCornerShape(16.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
  ) {
    Column(Modifier.fillMaxSize()) {
      // Top bar on the back face — title + close/flip-back.
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          recipe.title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = Ink,
          maxLines = 1,
          modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onFlipBack) {
          Text("Back", color = Terracotta, fontWeight = FontWeight.Medium)
        }
      }
      HorizontalDivider(color = InkFaint.copy(alpha = 0.5f))

      // Pager fills everything between the top bar and the sticky bottom action bar.
      HorizontalPager(
        state = pagerState,
        pageSize = PageSize.Fill,
        modifier = Modifier.weight(1f).fillMaxWidth(),
      ) { pageIndex ->
        when (pageIndex) {
          0 -> StoryHeadlinePage(recipe, onVintageToggle)
          1 -> FullStoryPage(recipe)
          2 -> {
            LaunchedEffect(recipe.id) { loadReviews() }
            ReviewsPage(reviewsForThis)
          }
          3 -> NotesPage(
            recipeId = recipe.id,
            initialNote = initialNote,
            onNoteChange = onNoteChange,
            onSubmitDrinkPhoto = onSubmitDrinkPhoto,
          )
        }
      }

      // Page dots.
      Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
      ) {
        repeat(4) { i ->
          val active = pagerState.currentPage == i
          Box(
            Modifier
              .padding(horizontal = 4.dp)
              .size(if (active) 8.dp else 6.dp)
              .background(if (active) Terracotta else InkFaint, CircleShape),
          )
        }
      }

      // Persistent action footer — icon-only toggles matching the front card.
      var panned by remember(recipe.id) { mutableStateOf(false) }
      var saved by remember(recipe.id) { mutableStateOf(false) }
      val panCount = recipe.panCount + (if (panned) 1 else 0)
      val saveCount = recipe.saveCount + (if (saved) 1 else 0)
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = { panned = !panned; onPanIt() },
          ),
        ) {
          Icon(
            if (panned) Icons.Filled.Favorite else Icons.Outlined.Favorite,
            contentDescription = "Pan it",
            tint = if (panned) Terracotta else Ink,
            modifier = Modifier.size(26.dp),
          )
          Text(
            formatSocialCount(panCount),
            style = MaterialTheme.typography.labelSmall,
            color = Ink, fontWeight = FontWeight.SemiBold,
          )
        }
        Spacer(Modifier.weight(1f))
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = { saved = !saved; onSave() },
          ),
        ) {
          Icon(
            if (saved) Icons.Filled.Bookmark else Icons.Outlined.Bookmark,
            contentDescription = "Save",
            tint = Ink,
            modifier = Modifier.size(26.dp),
          )
          Text(
            formatSocialCount(saveCount),
            style = MaterialTheme.typography.labelSmall,
            color = Ink, fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
  }
}

@Composable
private fun DwellTracker(currentPage: Int, onDwell: (Int, Int) -> Unit) {
  // Count seconds on each page. On page change or unmount, flush accumulated seconds.
  // We use a wall-clock delta computed at dispose time — counting via a tick state
  // that's reset by `remember(currentPage)` would race with the new state holder
  // being installed before the old DisposableEffect's onDispose closure runs.
  val pageEnteredAt = remember(currentPage) { System.currentTimeMillis() }
  DisposableEffect(currentPage) {
    onDispose {
      val secs = ((System.currentTimeMillis() - pageEnteredAt) / 1000L).toInt()
      onDwell(currentPage, secs)
    }
  }
}

// ---------- Pages ----------

@Composable
private fun StoryHeadlinePage(recipe: Recipe, onVintageToggle: (Boolean) -> Unit) {
  // Per-card toggle removed — mode is global at the top of the Mixology tab.
  Column(
    Modifier
      .fillMaxSize()
      .padding(20.dp)
      .verticalScroll(rememberScrollState()),
  ) {
    // Year + region eyebrow.
    val eyebrowBits = listOfNotNull(
      recipe.sourceYear?.toString(),
      recipe.sourceRegion?.takeIf { it.isNotBlank() },
    )
    if (eyebrowBits.isNotEmpty()) {
      Text(
        eyebrowBits.joinToString(" · ").uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = InkMuted,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Bold,
      )
    }

    Spacer(Modifier.height(12.dp))

    // Headline — first sentence of contributor_story or description.
    val headline = recipe.contributorStory?.split(". ")?.firstOrNull()?.trim()
      ?: recipe.description
      ?: recipe.title
    Text(
      headline,
      style = MaterialTheme.typography.titleLarge,
      color = Ink,
      fontWeight = FontWeight.SemiBold,
      lineHeight = 26.sp,
    )

    Spacer(Modifier.height(16.dp))

    run {
      val modern = recipe.modernizedText?.takeIf { it.isNotBlank() }
      if (modern != null) {
        Text(
          modern,
          style = MaterialTheme.typography.bodyLarge,
          color = Ink,
          lineHeight = 22.sp,
        )
      } else {
        // Fall back to structured steps — tight, numbered.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          recipe.steps.forEachIndexed { i, s ->
            Row {
              Text(
                "${i + 1}.",
                style = MaterialTheme.typography.bodyMedium,
                color = Terracotta, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(20.dp),
              )
              Text(s.text, style = MaterialTheme.typography.bodyMedium, color = Ink)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FullStoryPage(recipe: Recipe) {
  Column(
    Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
  ) {
    Text(
      "Origin",
      style = MaterialTheme.typography.labelMedium,
      color = InkMuted, fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(6.dp))
    val story = recipe.contributorStory?.takeIf { it.isNotBlank() }
      ?: "No origin story recorded for this drink yet."
    Text(
      story,
      style = MaterialTheme.typography.bodyLarge,
      color = Ink,
      lineHeight = 22.sp,
    )

    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = InkFaint.copy(alpha = 0.5f))
    Spacer(Modifier.height(12.dp))

    val metaBits = listOfNotNull(
      recipe.contributorName?.takeIf { it.isNotBlank() }?.let { "Contributor: $it" },
      recipe.sourceBook?.takeIf { it.isNotBlank() }?.let { "Book: $it" },
      recipe.sourceYear?.let { "Year: $it" },
      recipe.sourceRegion?.takeIf { it.isNotBlank() }?.let { "Region: $it" },
    )
    if (metaBits.isEmpty()) {
      Text(
        "Source details unknown.",
        style = MaterialTheme.typography.bodySmall,
        color = InkMuted,
        fontStyle = FontStyle.Italic,
      )
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        metaBits.forEach {
          Text(it, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
        }
      }
    }
  }
}

@Composable
private fun ReviewsPage(reviews: List<Review>) {
  Column(
    Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
  ) {
    Text(
      "What others say",
      style = MaterialTheme.typography.labelMedium,
      color = InkMuted, fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(10.dp))

    if (reviews.isEmpty()) {
      Text(
        "Be the first to share your take.",
        style = MaterialTheme.typography.bodyMedium,
        color = InkMuted,
        fontStyle = FontStyle.Italic,
      )
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        reviews.take(10).forEach { r ->
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Paper,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(Modifier.padding(12.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  "★".repeat(r.ratingPots.coerceIn(0, 5)),
                  color = Color(0xFFD4A017),
                  style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.weight(1f))
              }
              if (!r.notes.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(r.notes, style = MaterialTheme.typography.bodyMedium, color = Ink)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun NotesPage(
  recipeId: String,
  initialNote: String,
  onNoteChange: (String) -> Unit,
  onSubmitDrinkPhoto: (String) -> Unit = {},
) {
  var text by remember(recipeId) { mutableStateOf(initialNote) }
  var shareAsReview by remember(recipeId) { mutableStateOf(false) }
  Column(Modifier.fillMaxSize().padding(20.dp)) {
    Text(
      "Your notes",
      style = MaterialTheme.typography.labelMedium,
      color = InkMuted, fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(2.dp))
    Text(
      if (shareAsReview) "Will post to What Others Are Saying after moderation."
      else "Private — stored on this device only.",
      style = MaterialTheme.typography.labelSmall,
      color = InkFaint,
      fontStyle = FontStyle.Italic,
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
      value = text,
      onValueChange = {
        text = it
        onNoteChange(it)
      },
      modifier = Modifier.fillMaxWidth().weight(1f),
      placeholder = { Text("Tasting notes, tweaks, verdict…", color = InkMuted) },
      shape = RoundedCornerShape(8.dp),
    )

    Spacer(Modifier.height(10.dp))

    // Photo of your pour — opens system camera, runs through SafeSearch + drink-detection
    // moderation, then attaches to the recipe (and to your review if you toggle Share below).
    Row(
      Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Surface(
        onClick = { onSubmitDrinkPhoto(recipeId) },
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, InkFaint),
        modifier = Modifier.weight(1f).height(48.dp),
      ) {
        Row(
          Modifier.fillMaxSize().padding(horizontal = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Icon(Icons.Outlined.CameraAlt, null, tint = Ink, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(8.dp))
          Text("Photo your pour", color = Ink, style = MaterialTheme.typography.labelLarge)
        }
      }
    }

    Spacer(Modifier.height(8.dp))

    // Toggle to publish note + photo as a public review on this recipe
    Row(verticalAlignment = Alignment.CenterVertically) {
      Switch(
        checked = shareAsReview,
        onCheckedChange = { shareAsReview = it },
      )
      Spacer(Modifier.width(8.dp))
      Text(
        "Share to What Others Are Saying",
        style = MaterialTheme.typography.labelMedium,
        color = Ink,
      )
    }
  }
}

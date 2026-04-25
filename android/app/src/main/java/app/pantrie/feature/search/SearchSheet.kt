@file:OptIn(
  androidx.compose.material3.ExperimentalMaterial3Api::class,
  androidx.compose.ui.ExperimentalComposeUiApi::class,
  kotlinx.coroutines.FlowPreview::class,
)

package app.pantrie.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.SearchHit
import app.pantrie.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

// Mixology palette mirrors what MixologyScreen uses; keep in sync if those constants move.
private val ModernBg = Color(0xFF0D0D0E)
private val ModernCard = Color(0xFF18181B)
private val ModernInk = Color(0xFFE8E3D9)
private val ModernGold = Color(0xFFC9A554)
private val ModernInkMuted = Color(0xFF8B8578)

sealed interface SearchUiState {
  data object Idle : SearchUiState
  data object Loading : SearchUiState
  data class Results(val hits: List<SearchHit>, val total: Int) : SearchUiState
  data object Empty : SearchUiState
  data class Error(val message: String) : SearchUiState
}

@HiltViewModel
class SearchViewModel @Inject constructor(
  private val api: PantrieApi,
  internal val entitlement: app.pantrie.billing.EntitlementRepository,
) : ViewModel() {

  private val _query = MutableStateFlow("")
  val query = _query.asStateFlow()

  private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
  val state = _state.asStateFlow()

  private var contentType: String? = null

  // Debounce 300ms — typing "Margarita" fires one query at the end, not 9.
  // Alcohol filter: null = all, 1 = alcoholic only, 0 = mocktails only.
  private val _alcoholFilter = MutableStateFlow<Int?>(null)
  val alcoholFilter = _alcoholFilter.asStateFlow()

  /**
   * Search counter — every 4th search triggers an interstitial. We count distinct submitted
   * queries (debounced trailing edge) to avoid showing an ad while the user is still typing.
   */
  private var searchesThisSession = 0
  /** Set to true when a search just completed AND it's a "show ad now" slot. UI consumes + resets. */
  private val _shouldShowAd = MutableStateFlow(false)
  val shouldShowAd = _shouldShowAd.asStateFlow()
  fun consumeAdTrigger() { _shouldShowAd.value = false }

  init {
    viewModelScope.launch {
      _query
        .debounce(300L)
        .distinctUntilChanged()
        .filter { it.isNotBlank() && it.trim().length >= 2 }
        .collect { q -> performSearch(q.trim()) }
    }
  }

  fun setContentType(type: String?) {
    contentType = type
  }

  fun setAlcoholFilter(v: Int?) {
    _alcoholFilter.value = v
    val q = _query.value.trim()
    if (q.isNotBlank()) viewModelScope.launch { performSearch(q) }
  }

  fun onQueryChange(q: String) {
    _query.value = q
    if (q.isBlank()) _state.value = SearchUiState.Idle
  }

  /** Forced search bypassing debounce — wired to the keyboard search action. */
  fun submit() {
    val q = _query.value.trim()
    if (q.isNotBlank()) {
      viewModelScope.launch { performSearch(q) }
    }
  }

  private suspend fun performSearch(q: String) {
    _state.value = SearchUiState.Loading
    runCatching { api.searchRecipes(query = q, contentType = contentType, alcoholic = _alcoholFilter.value, limit = 50) }
      .onSuccess { resp ->
        _state.value = if (resp.results.isEmpty()) SearchUiState.Empty
        else SearchUiState.Results(resp.results, resp.total)
        // Pro skips ad gating entirely. Otherwise, every 4th search → interstitial.
        if (!entitlement.isPro.value) {
          searchesThisSession += 1
          if (searchesThisSession % 4 == 0) _shouldShowAd.value = true
        }
      }
      .onFailure { e ->
        _state.value = SearchUiState.Error(e.message ?: "Search failed")
      }
  }
}

@Composable
fun SearchSheet(
  contentType: String?,
  onBack: () -> Unit,
  onPick: (String) -> Unit,
  vm: SearchViewModel = hiltViewModel(),
) {
  // Cocktail mode = dark speakeasy; food mode = warm cream. Single switch governs theme.
  val cocktailMode = contentType == "cocktail"
  val bg = if (cocktailMode) ModernBg else Cream
  val ink = if (cocktailMode) ModernInk else Ink
  val accent = if (cocktailMode) ModernGold else Terracotta
  val muted = if (cocktailMode) ModernInkMuted else InkMuted
  val cardBg = if (cocktailMode) ModernCard else Paper

  LaunchedEffect(contentType) { vm.setContentType(contentType) }

  // Search-gated interstitial — fires when VM signals (every 4th search).
  val adHost: app.pantrie.billing.AdHostViewModel = hiltViewModel()
  val ctx = androidx.compose.ui.platform.LocalContext.current
  val shouldShowAd by vm.shouldShowAd.collectAsState()
  LaunchedEffect(shouldShowAd) {
    if (shouldShowAd) {
      val activity = ctx as? android.app.Activity
      if (activity != null) {
        adHost.adManager.showInterstitial(activity) { vm.consumeAdTrigger() }
      } else {
        vm.consumeAdTrigger()
      }
    }
  }

  // Survives rotation — vital for users mid-typing when the keyboard rotation occurs.
  val savedQuery = rememberSaveable { mutableStateOf("") }
  val state by vm.state.collectAsState()
  val query by vm.query.collectAsState()
  LaunchedEffect(savedQuery.value) {
    if (savedQuery.value != query) vm.onQueryChange(savedQuery.value)
  }

  val focus = remember { FocusRequester() }
  val keyboard = LocalSoftwareKeyboardController.current
  LaunchedEffect(Unit) { focus.requestFocus() }

  Scaffold(
    containerColor = bg,
    topBar = {
      Surface(color = bg, tonalElevation = 0.dp) {
        Row(
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(onClick = onBack) {
            Icon(Icons.Outlined.Close, contentDescription = "Close search", tint = ink)
          }
          // Inline search field — uses the topbar row directly so it feels like a search header,
          // not a form field stuck under a header.
          OutlinedTextField(
            value = savedQuery.value,
            onValueChange = {
              savedQuery.value = it
              vm.onQueryChange(it)
            },
            modifier = Modifier
              .weight(1f)
              .focusRequester(focus),
            placeholder = {
              Text(
                if (cocktailMode) "Search cocktails…" else "Search recipes…",
                color = muted,
              )
            },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = muted) },
            trailingIcon = {
              if (savedQuery.value.isNotEmpty()) {
                IconButton(onClick = {
                  savedQuery.value = ""
                  vm.onQueryChange("")
                }) {
                  Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = muted)
                }
              }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
              vm.submit()
              keyboard?.hide()
            }),
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = ink,
              unfocusedTextColor = ink,
              cursorColor = accent,
              focusedBorderColor = accent,
              unfocusedBorderColor = muted.copy(alpha = 0.5f),
              focusedContainerColor = cardBg,
              unfocusedContainerColor = cardBg,
            ),
            shape = RoundedCornerShape(12.dp),
          )
        }
      }
    },
  ) { padding ->
    Column(Modifier.padding(padding).fillMaxSize().background(bg)) {
      // Alcohol filter chips — only when searching cocktails. Three states: All / Spirited / Zero-Proof.
      if (cocktailMode) {
        val alcohol by vm.alcoholFilter.collectAsState()
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          AlcoholChip(label = "All",       active = alcohol == null, accent = accent, ink = ink, muted = muted, onClick = { vm.setAlcoholFilter(null) })
          AlcoholChip(label = "Spirited",  active = alcohol == 1,    accent = accent, ink = ink, muted = muted, onClick = { vm.setAlcoholFilter(1) })
          AlcoholChip(label = "Zero-Proof", active = alcohol == 0,    accent = accent, ink = ink, muted = muted, onClick = { vm.setAlcoholFilter(0) })
        }
      }
      Box(Modifier.fillMaxSize()) {
      when (val s = state) {
        SearchUiState.Idle -> SearchHint(cocktailMode = cocktailMode, ink = ink, muted = muted)
        SearchUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = accent)
        }
        SearchUiState.Empty -> EmptyState(query = query, cocktailMode = cocktailMode, ink = ink, muted = muted)
        is SearchUiState.Error -> ErrorState(message = s.message, ink = ink, muted = muted, accent = accent, onRetry = vm::submit)
        is SearchUiState.Results -> ResultsGrid(
          hits = s.hits,
          cocktailMode = cocktailMode,
          cardBg = cardBg,
          ink = ink,
          muted = muted,
          accent = accent,
          onPick = onPick,
        )
      }
      }
    }
  }
}

@Composable
private fun SearchHint(cocktailMode: Boolean, ink: Color, muted: Color) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(
        if (cocktailMode) Icons.Outlined.LocalBar else Icons.Outlined.Restaurant,
        contentDescription = null,
        tint = muted,
        modifier = Modifier.size(48.dp),
      )
      Spacer(Modifier.height(12.dp))
      Text(
        if (cocktailMode) "Search by cocktail name" else "Search by recipe name",
        style = MaterialTheme.typography.titleMedium,
        color = ink,
        fontWeight = FontWeight.SemiBold,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        "e.g. Margarita, Negroni, Old Fashioned",
        style = MaterialTheme.typography.bodySmall,
        color = muted,
      )
    }
  }
}

@Composable
private fun EmptyState(query: String, cocktailMode: Boolean, ink: Color, muted: Color) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
      Text(
        "No matches",
        style = MaterialTheme.typography.titleLarge,
        color = ink,
        fontWeight = FontWeight.SemiBold,
      )
      Spacer(Modifier.height(6.dp))
      Text(
        if (query.isBlank()) "Try a different term."
        else "We couldn't find any ${if (cocktailMode) "cocktails" else "recipes"} for \"$query\".",
        style = MaterialTheme.typography.bodyMedium,
        color = muted,
      )
    }
  }
}

@Composable
private fun ErrorState(message: String, ink: Color, muted: Color, accent: Color, onRetry: () -> Unit) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
      Text("Couldn't search", style = MaterialTheme.typography.titleMedium, color = Terracotta, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(8.dp))
      Text(message, style = MaterialTheme.typography.bodySmall, color = muted)
      Spacer(Modifier.height(16.dp))
      Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
        Text("Retry", color = Color.White)
      }
    }
  }
}

@Composable
private fun ResultsGrid(
  hits: List<SearchHit>,
  cocktailMode: Boolean,
  cardBg: Color,
  ink: Color,
  muted: Color,
  accent: Color,
  onPick: (String) -> Unit,
) {
  LazyVerticalGrid(
    columns = GridCells.Fixed(2),
    contentPadding = PaddingValues(12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    items(items = hits, key = { it.id }) { hit ->
      SearchTile(
        hit = hit,
        cocktailMode = cocktailMode,
        cardBg = cardBg,
        ink = ink,
        muted = muted,
        accent = accent,
        onClick = { onPick(hit.id) },
      )
    }
  }
}

@Composable
private fun SearchTile(
  hit: SearchHit,
  cocktailMode: Boolean,
  cardBg: Color,
  ink: Color,
  muted: Color,
  accent: Color,
  onClick: () -> Unit,
) {
  Card(
    onClick = onClick,
    colors = CardDefaults.cardColors(containerColor = cardBg),
    shape = RoundedCornerShape(12.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = if (cocktailMode) 0.dp else 2.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.fillMaxWidth()) {
      // 1:1 image area on top — placeholder when no image.
      Box(
        Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
          .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
          .background(if (cocktailMode) Color(0xFF222226) else CreamAlt),
        contentAlignment = Alignment.Center,
      ) {
        if (!hit.imageUrl.isNullOrBlank()) {
          AsyncImage(
            model = hit.imageUrl,
            contentDescription = hit.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          // Tile-without-image placeholder: cuisine emoji over a tinted square. Cheap visual
          // anchor so a no-photo tile doesn't read as broken.
          Text(
            placeholderGlyph(hit),
            fontSize = 44.sp,
          )
        }
      }
      Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(
          hit.title,
          style = MaterialTheme.typography.titleSmall,
          color = ink,
          fontFamily = if (cocktailMode) FontFamily.Default else FontFamily.Default,
          fontWeight = FontWeight.SemiBold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          lineHeight = 18.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(
          subtitleFor(hit),
          style = MaterialTheme.typography.labelSmall,
          color = muted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

/** Build a 1-line subtitle: "1862 · NYC" for historic cocktails, otherwise cuisine/glass. */
private fun subtitleFor(hit: SearchHit): String {
  val isCocktail = hit.contentType == "cocktail"
  return when {
    hit.isHistoric && hit.sourceYear != null -> {
      // "1862 · TheCocktailDB" reads as "year · place/source" — historic-cocktail vibe.
      val tail = hit.sourceBook?.takeIf { it.isNotBlank() } ?: hit.cuisine ?: "Vintage"
      "${hit.sourceYear} · $tail"
    }
    isCocktail -> {
      val parts = listOfNotNull(
        hit.glassType?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercaseChar() },
        hit.abvPercent?.takeIf { it > 0 }?.let { "${it.toInt()}% ABV" },
      )
      if (parts.isEmpty()) hit.cuisine?.replaceFirstChar { it.uppercaseChar() } ?: "Cocktail"
      else parts.joinToString(" · ")
    }
    else -> hit.cuisine?.replaceFirstChar { it.uppercaseChar() } ?: "Recipe"
  }
}

@Composable
private fun AlcoholChip(
  label: String,
  active: Boolean,
  accent: Color,
  ink: Color,
  muted: Color,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(20.dp),
    color = if (active) accent.copy(alpha = 0.15f) else Color.Transparent,
    border = androidx.compose.foundation.BorderStroke(
      width = if (active) 1.5.dp else 1.dp,
      color = if (active) accent else muted.copy(alpha = 0.5f),
    ),
  ) {
    Text(
      text = label,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
      color = if (active) accent else ink,
      fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
      fontSize = 13.sp,
    )
  }
}

private fun placeholderGlyph(hit: SearchHit): String {
  if (hit.contentType == "cocktail") {
    return when (hit.glassType?.lowercase()) {
      "rocks", "old fashioned", "lowball", "shot" -> "🥃"
      "flute", "champagne" -> "🍾"
      "highball", "collins" -> "🥤"
      else -> "🍸"
    }
  }
  return when (hit.cuisine?.lowercase()) {
    "italian" -> "🍝"
    "mexican" -> "🌮"
    "japanese" -> "🍣"
    "chinese" -> "🥡"
    "indian" -> "🍛"
    "thai" -> "🍜"
    "french" -> "🥐"
    "american" -> "🍔"
    "mediterranean", "greek" -> "🥗"
    "korean" -> "🍱"
    else -> "🍽"
  }
}

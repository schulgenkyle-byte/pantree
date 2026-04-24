package app.pantrie.feature.saved

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.feature.app.RefreshBus
import app.pantrie.feature.beta.Analytics
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.ReshopRequest
import app.pantrie.network.dto.SavedRecipe
import app.pantrie.ui.theme.*
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavedViewModel @Inject constructor(
  private val api: PantrieApi,
  private val analytics: Analytics,
  private val refreshBus: RefreshBus,
) : ViewModel() {
  private val _saved = MutableStateFlow<List<SavedRecipe>>(emptyList())
  val saved = _saved.asStateFlow()
  private val _loading = MutableStateFlow(true)
  val loading = _loading.asStateFlow()
  private val _banner = MutableStateFlow<String?>(null)
  val banner = _banner.asStateFlow()

  init {
    refresh()
    viewModelScope.launch { refreshBus.pantry.collect { refresh() } }
  }

  fun refresh() {
    viewModelScope.launch {
      _loading.value = true
      runCatching { api.saved() }.onSuccess { _saved.value = it.saved }
      _loading.value = false
    }
  }

  fun reshop(r: SavedRecipe) {
    viewModelScope.launch {
      val resp = runCatching { api.reshop(ReshopRequest(r.id)) }.getOrNull()
      val added = resp?.added ?: 0
      analytics.track("reshop", mapOf("recipeId" to r.id, "added" to added))
      _banner.value = if (added > 0) "Added $added ingredient${if (added == 1) "" else "s"} to Shop"
                      else "Nothing new to add — you have everything for ${r.title}"
      if (added > 0) refreshBus.bumpShopping()
    }
  }

  fun dismissBanner() { _banner.value = null }
}

/** Cookbook filter buckets — client-side only, no new DTOs. */
private enum class CookbookFilter(val label: String) {
  CanCookNow("Can cook now"),
  AlmostThere("Almost there"),
  TrySoon("Try soon"),
}

private fun SavedRecipe.matchesFilter(f: CookbookFilter?): Boolean = when (f) {
  null -> true
  CookbookFilter.CanCookNow -> missingCount == 0
  CookbookFilter.AlmostThere -> missingCount in 1..2
  CookbookFilter.TrySoon -> missingCount > 2
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
  onBack: () -> Unit,
  onOpenRecipe: (String) -> Unit,
  onStartCook: (String) -> Unit,
  vm: SavedViewModel = hiltViewModel(),
) {
  val saved by vm.saved.collectAsState()
  val loading by vm.loading.collectAsState()
  val banner by vm.banner.collectAsState()
  val snackbarHost = remember { SnackbarHostState() }
  LaunchedEffect(banner) {
    banner?.let { snackbarHost.showSnackbar(it); vm.dismissBanner() }
  }

  Scaffold(
    containerColor = Cream,
    topBar = {
      TopAppBar(
        title = { Text("Cookbook", fontWeight = FontWeight.Normal) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream),
      )
    },
    snackbarHost = { SnackbarHost(snackbarHost) },
  ) { padding ->
    var query by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf<CookbookFilter?>(null) }

    val filtered = remember(saved, query, activeFilter) {
      val byFilter = if (activeFilter == null) saved else saved.filter { it.matchesFilter(activeFilter) }
      if (query.isBlank()) byFilter
      else byFilter.filter { r ->
        val q = query.lowercase()
        r.title.lowercase().contains(q) ||
          (r.cuisine?.lowercase()?.contains(q) == true)
      }
    }
    val grouped = remember(filtered) {
      filtered.groupBy { (it.cuisine?.takeIf { c -> c.isNotBlank() } ?: "Other").replaceFirstChar { it.uppercase() } }
        .toSortedMap()
    }

    LazyColumn(
      Modifier.padding(padding).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        Text(
          "Your personal cookbook — recipes you've swiped right on. Tap 'Add to shop' to auto-add what's still missing.",
          style = MaterialTheme.typography.bodyMedium, color = InkMuted,
          modifier = Modifier.padding(bottom = 6.dp),
        )
      }
      item {
        // Filter bar — single-select, tapping active clears to All.
        Row(
          Modifier.fillMaxWidth().padding(bottom = 2.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FilterChip(
            selected = activeFilter == null,
            onClick = { activeFilter = null },
            label = { Text("All") },
          )
          CookbookFilter.values().forEach { f ->
            val selected = activeFilter == f
            FilterChip(
              selected = selected,
              onClick = { activeFilter = if (selected) null else f },
              label = { Text(f.label) },
            )
          }
        }
      }
      item {
        OutlinedTextField(
          value = query, onValueChange = { query = it.take(40) },
          placeholder = { Text("Search saved by title or cuisine") },
          leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
          trailingIcon = if (query.isNotEmpty()) ({
            IconButton(onClick = { query = "" }) {
              Icon(Icons.Outlined.Close, contentDescription = "clear")
            }
          }) else null,
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(4.dp),
        )
      }
      if (loading && saved.isEmpty()) {
        item {
          Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Ink, strokeWidth = 2.dp)
          }
        }
      } else if (saved.isEmpty()) {
        item {
          Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Your cookbook's empty", style = MaterialTheme.typography.titleMedium)
              Spacer(Modifier.height(4.dp))
              Text(
                "Swipe right on Tonight to start your collection.",
                style = MaterialTheme.typography.bodyMedium, color = InkMuted,
              )
            }
          }
        }
      } else if (filtered.isEmpty()) {
        item {
          Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            val msg = when {
              query.isNotBlank() && activeFilter != null ->
                "Nothing in \"${activeFilter!!.label}\" matches \"${query}\""
              query.isNotBlank() -> "Nothing matches \"${query}\""
              activeFilter != null -> "No recipes in \"${activeFilter!!.label}\" yet"
              else -> "Nothing to show"
            }
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
          }
        }
      } else {
        for ((cuisine, rows) in grouped) {
          item {
            Text(
              "$cuisine · ${rows.size}",
              style = MaterialTheme.typography.labelMedium,
              color = InkSoft, fontWeight = FontWeight.Medium,
              modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp),
            )
          }
          items(rows, key = { it.id }) { r ->
            SavedRow(r = r, onOpen = { onOpenRecipe(r.id) }, onCook = { onStartCook(r.id) }, onReshop = { vm.reshop(r) })
          }
        }
      }
    }
  }
}

@Composable
private fun SavedRow(
  r: SavedRecipe,
  onOpen: () -> Unit,
  onCook: () -> Unit,
  onReshop: () -> Unit,
) {
  Card(
    onClick = onOpen,
    colors = CardDefaults.cardColors(containerColor = Paper),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (!r.imageUrl.isNullOrBlank()) {
        AsyncImage(
          model = r.imageUrl, contentDescription = r.title,
          contentScale = ContentScale.Crop,
          modifier = Modifier.size(96.dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(12.dp))
      }
      Column(Modifier.weight(1f)) {
        Text(r.title, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 2)
        Spacer(Modifier.height(4.dp))
        val sub = buildString {
          append("${r.pantryMatchPercent}% match")
          r.cuisine?.let { if (it.isNotBlank()) append(" · $it") }
        }
        Text(sub, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        Spacer(Modifier.height(6.dp))
        // Status badge — cook-ready vs missing-count, colored to match the app palette.
        if (r.missingCount == 0) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = Olive.copy(alpha = 0.18f),
          ) {
            Text(
              "🍳 Can cook",
              style = MaterialTheme.typography.labelSmall,
              color = Olive,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        } else {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = Terracotta.copy(alpha = 0.15f),
          ) {
            Text(
              "Missing ${r.missingCount}",
              style = MaterialTheme.typography.labelSmall,
              color = Terracotta,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        }
      }
      Spacer(Modifier.width(8.dp))
      Column(horizontalAlignment = Alignment.End) {
        if (r.missingCount > 0) {
          Button(
            onClick = onReshop,
            colors = ButtonDefaults.buttonColors(containerColor = Olive),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          ) {
            Icon(Icons.Outlined.AddShoppingCart, null, modifier = Modifier.size(14.dp), tint = Paper)
            Spacer(Modifier.width(4.dp))
            Text("Add to shop", color = Paper, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
          }
        } else {
          OutlinedButton(
            onClick = onCook,
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          ) {
            Text("Cook it", color = Ink, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
          }
        }
      }
    }
  }
}

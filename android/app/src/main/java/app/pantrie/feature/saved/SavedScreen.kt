package app.pantrie.feature.saved

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.feature.app.RefreshBus
import app.pantrie.feature.beta.Analytics
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.InteractRequest
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

  /** Remove a recipe from the user's library. Mirrors a swipe-left "pass" on the deck. */
  fun unsave(r: SavedRecipe) {
    viewModelScope.launch {
      // Optimistic: drop locally so the UI updates instantly; refresh after server confirms.
      _saved.value = _saved.value.filterNot { it.id == r.id }
      runCatching { api.interact(InteractRequest(recipeId = r.id, status = "dismissed")) }
      analytics.track("library_unsave", mapOf("recipeId" to r.id))
      _banner.value = "Removed \"${r.title}\" from Library"
    }
  }

  fun dismissBanner() { _banner.value = null }
}

/**
 * A "book" on the library shelf — one accordion spine grouping a subset of saved recipes.
 * V1 ships with two fixed books: Cooking (food) and Mixology (cocktails/mocktails).
 */
private data class LibraryBook(
  val id: String,
  val title: String,
  val spineColor: Color,
  val matches: (SavedRecipe) -> Boolean,
)

private val CookingSpine = Color(0xFF6D2C2C)     // burgundy leather
private val MixologySpine = Color(0xFF2F4F3A)    // deep bottle-green leather
private val GoldEmboss = Color(0xFFD4A017)       // embossed gold title / chevron

private val LibraryBooks = listOf(
  LibraryBook(
    id = "Cooking",
    title = "Cooking",
    spineColor = CookingSpine,
    matches = { it.contentType.isBlank() || it.contentType.equals("food", ignoreCase = true) },
  ),
  LibraryBook(
    id = "Mixology",
    title = "Mixology",
    spineColor = MixologySpine,
    matches = { ct ->
      ct.contentType.equals("cocktail", ignoreCase = true) ||
        ct.contentType.equals("mocktail", ignoreCase = true)
    },
  ),
)

/** Saver for a Set<String> so expanded-book state survives rotation + process death. */
private val ExpandedBooksSaver: Saver<MutableState<Set<String>>, ArrayList<String>> = Saver(
  save = { ArrayList(it.value) },
  restore = { mutableStateOf(it.toSet()) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
  onBack: () -> Unit,
  onOpenRecipe: (String) -> Unit,
  @Suppress("UNUSED_PARAMETER") onStartCook: (String) -> Unit,
  vm: SavedViewModel = hiltViewModel(),
) {
  val saved by vm.saved.collectAsState()
  val loading by vm.loading.collectAsState()
  val banner by vm.banner.collectAsState()
  val snackbarHost = remember { SnackbarHostState() }
  LaunchedEffect(banner) {
    banner?.let { snackbarHost.showSnackbar(it); vm.dismissBanner() }
  }

  // Default: Cooking expanded. Use rememberSaveable with a custom Saver for Set<String>.
  val expandedBooks = rememberSaveable(saver = ExpandedBooksSaver) { mutableStateOf(setOf("Cooking")) }

  // Bottom sheet state for long-press "Remove from Library".
  var sheetTarget by remember { mutableStateOf<SavedRecipe?>(null) }

  Scaffold(
    containerColor = Cream,
    topBar = {
      TopAppBar(
        title = { Text("Library", fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold) },
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
    // Precompute per-book recipe lists so UI code is just rendering.
    val grouped = remember(saved) {
      LibraryBooks.associate { book -> book.id to saved.filter(book.matches) }
    }

    LazyColumn(
      Modifier.padding(padding).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (loading && saved.isEmpty()) {
        item {
          Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Ink, strokeWidth = 2.dp)
          }
        }
      }

      LibraryBooks.forEach { book ->
        val recipes = grouped[book.id].orEmpty()
        val expanded = book.id in expandedBooks.value
        item(key = "spine-${book.id}") {
          BookSpine(
            title = book.title,
            spineColor = book.spineColor,
            count = recipes.size,
            expanded = expanded,
            onToggle = {
              expandedBooks.value = if (expanded) expandedBooks.value - book.id
                                    else expandedBooks.value + book.id
            },
          )
        }
        item(key = "panel-${book.id}") {
          AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
          ) {
            BookContents(
              recipes = recipes,
              onOpenRecipe = onOpenRecipe,
              onLongPress = { sheetTarget = it },
            )
          }
        }
      }

      // v1: disabled "New book" affordance — hints at upcoming user-created books.
      item(key = "new-book") {
        Surface(
          color = CreamAlt,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
          Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = InkMuted)
            Spacer(Modifier.width(8.dp))
            Text("New book", color = InkMuted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(10.dp))
            Text("(coming soon)", color = InkFaint, style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
  }

  // Long-press bottom sheet: only "Remove from Library" in v1.
  val target = sheetTarget
  if (target != null) {
    RemoveFromLibrarySheet(
      recipe = target,
      onDismiss = { sheetTarget = null },
      onRemove = {
        vm.unsave(target)
        sheetTarget = null
      },
    )
  }
}

/**
 * Leather-bound book spine: 64dp tall, colored band with gold embossed serif title,
 * recipe count on right, chevron toggles expansion.
 */
@Composable
private fun BookSpine(
  title: String,
  spineColor: Color,
  count: Int,
  expanded: Boolean,
  onToggle: () -> Unit,
) {
  Surface(
    color = spineColor,
    shape = RoundedCornerShape(8.dp),
    shadowElevation = 3.dp,
    tonalElevation = 0.dp,
    modifier = Modifier
      .fillMaxWidth()
      .height(64.dp)
      .clip(RoundedCornerShape(8.dp))
      .clickable(onClick = onToggle),
  ) {
    Row(
      Modifier.fillMaxSize().padding(horizontal = 18.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Small decorative icon in gold — differentiates the two V1 books at a glance.
      Icon(
        imageVector = if (title == "Mixology") Icons.Outlined.LocalBar else Icons.Outlined.RestaurantMenu,
        contentDescription = null,
        tint = GoldEmboss,
        modifier = Modifier.size(22.dp),
      )
      Spacer(Modifier.width(12.dp))
      Text(
        title,
        color = GoldEmboss,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        modifier = Modifier.weight(1f),
      )
      Text(
        "$count",
        color = GoldEmboss,
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
      )
      Spacer(Modifier.width(8.dp))
      Icon(
        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
        contentDescription = if (expanded) "Collapse $title" else "Expand $title",
        tint = GoldEmboss,
      )
    }
  }
}

/** Renders the body of an open book: either the recipe list or an empty hint. */
@Composable
private fun BookContents(
  recipes: List<SavedRecipe>,
  onOpenRecipe: (String) -> Unit,
  onLongPress: (SavedRecipe) -> Unit,
) {
  Surface(
    color = Paper,
    shape = RoundedCornerShape(10.dp),
    tonalElevation = 0.dp,
    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
  ) {
    if (recipes.isEmpty()) {
      Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          "No saves yet — swipe right on a recipe",
          style = MaterialTheme.typography.bodyMedium,
          color = InkMuted,
        )
      }
    } else {
      // Fixed-height rows inside a Column (not a nested LazyColumn — avoids
      // infinite-height crashes when nested in a parent LazyColumn).
      Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        recipes.forEach { r ->
          LibraryRecipeRow(
            r = r,
            onOpen = { onOpenRecipe(r.id) },
            onLongPress = { onLongPress(r) },
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryRecipeRow(
  r: SavedRecipe,
  onOpen: () -> Unit,
  onLongPress: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(80.dp)
      .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Thumbnail (AsyncImage falls back to a tinted block if image URL missing).
    Box(
      Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(Beige),
      contentAlignment = Alignment.Center,
    ) {
      if (!r.imageUrl.isNullOrBlank()) {
        AsyncImage(
          model = r.imageUrl,
          contentDescription = r.title,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        Icon(
          Icons.Outlined.RestaurantMenu,
          contentDescription = null,
          tint = InkMuted,
          modifier = Modifier.size(24.dp),
        )
      }
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(
        r.title,
        style = MaterialTheme.typography.titleMedium,
        color = Ink,
        maxLines = 2,
        fontFamily = FontFamily.SansSerif,
      )
      val sub = r.cuisine?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
      if (sub != null) {
        Spacer(Modifier.height(2.dp))
        Text(sub, style = MaterialTheme.typography.bodySmall, color = InkMuted, fontFamily = FontFamily.SansSerif)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoveFromLibrarySheet(
  recipe: SavedRecipe,
  onDismiss: () -> Unit,
  onRemove: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Paper) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
      Text(
        recipe.title,
        style = MaterialTheme.typography.titleMedium,
        color = Ink,
        fontFamily = FontFamily.Serif,
      )
      Spacer(Modifier.height(16.dp))
      Surface(
        color = Cream,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onRemove),
      ) {
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = Terracotta)
          Spacer(Modifier.width(12.dp))
          Text("Remove from Library", color = Terracotta, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
      }
      Spacer(Modifier.height(12.dp))
    }
  }
}

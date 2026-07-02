package app.pantrie.feature.shopping

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.*
import app.pantrie.ui.AisleImageOrGlyph
import app.pantrie.ui.IngredientImageOrEmoji
import app.pantrie.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShoppingViewModel @Inject constructor(
  private val api: PantrieApi,
  private val refreshBus: app.pantrie.feature.app.RefreshBus,
) : ViewModel() {
  private val _smart = MutableStateFlow<SmartShoppingResponse?>(null)
  val smart = _smart.asStateFlow()
  private val _loading = MutableStateFlow(true)
  val loading = _loading.asStateFlow()

  /** Local-only set of expiring-item ids the user has dismissed or restocked
   *  this session. Filtered out of the displayed "Use this week" list so the
   *  same item doesn't sit there forever after the user has acted on it. Not
   *  persisted — a refresh that pulls fresh server data clears it, which is
   *  fine since the underlying pantry hasn't changed and the warning was
   *  acted on, not invalidated. */
  private val _dismissedExpiring = MutableStateFlow<Set<String>>(emptySet())
  val dismissedExpiring = _dismissedExpiring.asStateFlow()

  fun dismissExpiring(item: ExpiringItem) {
    _dismissedExpiring.value = _dismissedExpiring.value + item.id
  }

  init {
    refresh()
    // React to cross-screen shopping mutations (deck swipe-saves auto-add ingredients).
    viewModelScope.launch {
      refreshBus.shopping.collect { refresh() }
    }
  }

  fun refresh() {
    viewModelScope.launch {
      _loading.value = true
      runCatching { api.shoppingSmart() }.onSuccess { _smart.value = it }
      _loading.value = false
    }
  }

  fun toggleChecked(item: ShoppingItem) {
    viewModelScope.launch {
      runCatching { api.updateShopping(item.id, ShoppingUpdateRequest(checked = !item.checked)) }
      refresh()
    }
  }

  fun delete(item: ShoppingItem) {
    viewModelScope.launch {
      runCatching { api.deleteShopping(item.id) }
      refresh()
    }
  }

  fun clearChecked() {
    viewModelScope.launch {
      val toDelete = _smart.value?.items?.filter { it.checked } ?: emptyList()
      for (it in toDelete) runCatching { api.deleteShopping(it.id) }
      refresh()
      refreshBus.bumpShopping()
    }
  }

  fun clearAll() {
    viewModelScope.launch {
      runCatching { api.clearShopping() }
      refresh()
      refreshBus.bumpShopping()
    }
  }

  fun addManual(name: String) {
    if (name.isBlank()) return
    viewModelScope.launch {
      runCatching { api.addShopping(ShoppingAddRequest(name = name.trim().take(80))) }
      refresh()
    }
  }

  fun restock(item: ExpiringItem) {
    // Optimistic dismiss — drop it from "Use this week" immediately so the
    // user gets feedback before the API round-trip lands. Server refresh
    // doesn't re-add it because dismissedExpiring is OR'd into the filter.
    _dismissedExpiring.value = _dismissedExpiring.value + item.id
    viewModelScope.launch {
      runCatching {
        api.addShopping(ShoppingAddRequest(
          name = item.name, quantity = item.quantity, unit = item.unit,
          aisle = item.category ?: "other", source = "restock",
        ))
      }
      refresh()
      refreshBus.bumpShopping()
    }
  }

  fun restockAll(items: List<ExpiringItem>) {
    _dismissedExpiring.value = _dismissedExpiring.value + items.map { it.id }
    viewModelScope.launch {
      for (item in items) {
        runCatching {
          api.addShopping(ShoppingAddRequest(
            name = item.name, quantity = item.quantity, unit = item.unit,
            aisle = item.category ?: "other", source = "restock",
          ))
        }
      }
      refresh()
      refreshBus.bumpShopping()
    }
  }

  /** Build a vendor search URL from the current unchecked shopping list and
   *  fire the handoff analytics in parallel. Caller (ShoppingScreen) consumes
   *  the URL and opens it via Intent.ACTION_VIEW. */
  fun handoffUrl(vendorId: String): String? {
    val items = _smart.value?.items?.filter { !it.checked }?.map { it.name }?.distinct() ?: emptyList()
    if (items.isEmpty()) return null
    // Encode the joined item list as the vendor's search query. Each vendor's
    // own search infers categories + matches stocked SKUs from raw text.
    val q = java.net.URLEncoder.encode(items.joinToString(" "), "UTF-8")
    val url = when (vendorId) {
      "amazon" -> "https://www.amazon.com/alm/search?almBrandId=QW1hem9uIEZyZXNo&k=$q"
      "walmart" -> "https://www.walmart.com/search?q=$q"
      "instacart" -> "https://www.instacart.com/store/search-v3/term?term=$q"
      else -> return null
    }
    // Fire-and-forget capture.
    val cats = _smart.value?.items?.filter { !it.checked }?.mapNotNull { it.aisle }?.distinct()?.size
    viewModelScope.launch {
      runCatching {
        api.shoppingVendorHandoff(VendorHandoffRequest(
          vendorId = vendorId,
          itemCount = items.size,
          categoryCount = cats,
        ))
      }
    }
    return url
  }

  fun addUnlock(sug: UnlockSuggestion) {
    viewModelScope.launch {
      runCatching { api.addShopping(ShoppingAddRequest(name = sug.ingredient, source = "unlock")) }
      refresh()
    }
  }

  /**
   * Per-item aisle override. User's store layout varies (e.g. canned tomatoes live in produce at
   * some Trader Joe's) — let them re-file a specific row without touching the global default.
   * No-op if the target matches the current aisle. Refreshes + bumps the bus so other tabs re-read.
   */
  fun moveToAisle(item: ShoppingItem, newAisle: String) {
    if (newAisle == (item.aisle ?: "other")) return
    viewModelScope.launch {
      runCatching {
        api.updateShopping(item.id, ShoppingUpdateRequest(aisle = newAisle))
      }.onSuccess {
        refresh()
        refreshBus.bumpShopping()
      }
    }
  }
}

// Must match backend AISLE_ALLOW. Order mirrors the typical store walk order for nicer UX
// in the picker, so frequently-used sections land near the top.
private val AISLES = listOf(
  "produce", "bakery", "deli", "protein", "dairy",
  "frozen", "grain", "pantry", "spice", "condiment",
  "bar", "beverage", "other",
)

// Walk-the-store order. Each aisle = colored accordion spine, like Library books.
// Aisle colors stay saturated so the section headers read at a glance even on dark — this is
// the one place where "category color" wins over editorial monotone. Emoji glyphs were stripped
// in Phase 2; the AisleImageOrGlyph helper now renders a brass-monochrome lettered placeholder
// when no PNG exists yet.
private data class AisleMeta(val key: String, val label: String, val color: Color)
private val AISLE_WALK_ORDER = listOf(
  AisleMeta("produce",   "Produce",   Color(0xFF7A8450)),  // olive
  AisleMeta("bakery",    "Bakery",    Color(0xFFB58A4D)),  // wheat
  AisleMeta("deli",      "Deli",      Color(0xFFD4A017)),  // amber
  AisleMeta("protein",   "Protein",   Color(0xFFB04A3C)),  // terracotta
  AisleMeta("dairy",     "Dairy",     Color(0xFF6F8FB5)),  // soft blue
  AisleMeta("frozen",    "Frozen",    Color(0xFF4A7BA3)),  // ice blue
  AisleMeta("grain",     "Grain",     Color(0xFF8C6E3F)),  // tan
  AisleMeta("pantry",    "Pantry",    Color(0xFF6E5638)),  // brown
  AisleMeta("spice",     "Spice",     Color(0xFFB55D2F)),  // paprika
  AisleMeta("condiment", "Condiment", Color(0xFFB58F2E)),  // honey
  AisleMeta("bar",       "Bar",       Color(0xFF2F2A22)),  // speakeasy black
  AisleMeta("beverage",  "Beverage",  Color(0xFF5C8A7A)),  // sea
  AisleMeta("other",     "Other",     Color(0xFF6A6864)),  // muted ink
)

private val ExpandedAislesSaver: Saver<MutableState<Set<String>>, ArrayList<String>> = Saver(
  save = { ArrayList(it.value) },
  restore = { mutableStateOf(it.toSet()) },
)

/** Builds plain-text shopping list grouped by aisle for the system share sheet.
 *  Shows unchecked items only — no point sharing things you've already grabbed. */
private fun buildShoppingShareText(items: List<ShoppingItem>): String {
  val unchecked = items.filter { !it.checked }
  if (unchecked.isEmpty()) return ""
  val byAisle = unchecked.groupBy { it.aisle ?: "other" }
  val ordered = AISLE_WALK_ORDER.mapNotNull { aisle ->
    byAisle[aisle.key]?.let { aisle.label to it }
  } + (byAisle.keys - AISLE_WALK_ORDER.map { it.key }.toSet())
    .mapNotNull { key -> byAisle[key]?.let { key.replaceFirstChar { c -> c.uppercase() } to it } }
  return buildString {
    append("Shopping list\n\n")
    ordered.forEach { (label, rows) ->
      append(label.uppercase()).append('\n')
      rows.forEach { item ->
        append("  · ")
        if (item.quantity != null && item.unit != null) append("${item.quantity} ${item.unit} ")
        else if (item.quantity != null) append("${item.quantity} ")
        append(item.name).append('\n')
      }
      append('\n')
    }
    append("via Speakeater")
  }.trim()
}

@Composable
fun ShoppingScreen(vm: ShoppingViewModel = hiltViewModel()) {
  val smart by vm.smart.collectAsState()
  val loading by vm.loading.collectAsState()
  val dismissedExpiring by vm.dismissedExpiring.collectAsState()
  // Walkthrough VM — used by the shopping mini-tour to spotlight the Share button.
  val tourVm: app.pantrie.feature.walkthrough.WalkthroughViewModel = hiltViewModel()
  // All aisles default expanded (matches user expectation when shopping). State survives rotation.
  val expandedAisles = rememberSaveable(saver = ExpandedAislesSaver) {
    mutableStateOf(AISLE_WALK_ORDER.map { it.key }.toSet())
  }
  // Refresh the nav badge when the expiring count may have changed
  val appVm: app.pantrie.feature.app.AppStateViewModel = hiltViewModel()
  LaunchedEffect(smart) { appVm.refresh() }

  // Always re-fetch when the Shop tab becomes visible. With bottom nav + restoreState,
  // the composable tree is kept across tab switches so `init` only fires once — this
  // observer is what ensures swipe-adds and pantry-changes show up on re-entry.
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  DisposableEffect(lifecycle) {
    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
    }
    lifecycle.addObserver(observer)
    onDispose { lifecycle.removeObserver(observer) }
  }

  var newItem by remember { mutableStateOf("") }
  // Which row is being re-filed. Driven by long-press on any ShoppingRow.
  var moveTarget by remember { mutableStateOf<ShoppingItem?>(null) }
  val s = smart

  Scaffold(containerColor = Paper) { padding ->
   Column(Modifier.padding(padding).fillMaxSize()) {
    LazyColumn(
      Modifier.weight(1f).fillMaxWidth(),
      contentPadding = PaddingValues(bottom = 24.dp),
    ) {
      item {
        var confirmClearAll by remember { mutableStateOf(false) }
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
          verticalAlignment = Alignment.Top,
        ) {
          Column(Modifier.weight(1f)) {
            Text("Shopping", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Normal)
            val uncheck = s?.items?.count { !it.checked } ?: 0
            val check = s?.items?.count { it.checked } ?: 0
            Text("$uncheck to buy · $check done",
              style = MaterialTheme.typography.bodyMedium, color = InkMuted)
          }
          if ((s?.items?.size ?: 0) > 0) {
            // Share — Android system share sheet with the unchecked items grouped by aisle.
            // Useful for SMS/email/notes-app handoff to a partner running the actual errand.
            val ctx = LocalContext.current
            IconButton(
              onClick = {
                val text = buildShoppingShareText(s?.items.orEmpty())
                if (text.isNotBlank()) {
                  val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Speakeater · shopping list")
                  }
                  ctx.startActivity(android.content.Intent.createChooser(intent, "Share shopping list"))
                }
              },
              // Mini-tour: shopping step 2 spotlights the Share button.
              modifier = Modifier.onGloballyPositioned { coords ->
                tourVm.reportAnchor(
                  app.pantrie.feature.walkthrough.TourAnchors.SHOPPING_SHARE_BUTTON,
                  coords.boundsInWindow(),
                )
              },
            ) {
              Icon(Icons.Outlined.IosShare, contentDescription = "Share list", tint = Ink)
            }
            TextButton(onClick = { confirmClearAll = true }) {
              Text("Clear all", color = Terracotta, style = MaterialTheme.typography.labelMedium)
            }
          }
        }
        if (confirmClearAll) {
          AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Clear entire shopping list?") },
            text = { Text("Removes all ${s?.items?.size ?: 0} items. This can't be undone.") },
            confirmButton = {
              TextButton(onClick = { confirmClearAll = false; vm.clearAll() }) {
                Text("Clear all", color = Terracotta, fontWeight = FontWeight.SemiBold)
              }
            },
            dismissButton = { TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") } },
          )
        }
      }

      // Add row
      item {
        Row(
          Modifier.padding(horizontal = 24.dp).fillMaxWidth().padding(bottom = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = newItem,
            onValueChange = { newItem = it },
            placeholder = { Text("Add item") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(4.dp),
          )
          Spacer(Modifier.width(8.dp))
          Button(
            onClick = { vm.addManual(newItem); newItem = "" },
            enabled = newItem.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
            shape = RoundedCornerShape(4.dp),
          ) { Text("Add", color = Paper) }
        }
      }

      // Vendor row — greyed-out preview. Real partnership integrations land
      // when Amazon/Walmart/Instacart deals close; until then we surface the
      // intent (so users see the roadmap) without the disappointment of
      // tapping a row that just dumps them on a generic web search.
      val uncheckedCount = s?.items?.count { !it.checked } ?: 0
      if (uncheckedCount > 0) {
        item {
          Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 18.dp)) {
            Text(
              "SEND LIST TO · COMING SOON",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = InkMuted.copy(alpha = 0.6f),
              letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf("Amazon", "Walmart", "Instacart").forEach { name ->
                OutlinedButton(
                  onClick = {},
                  enabled = false,
                  modifier = Modifier.weight(1f).height(44.dp),
                  shape = RoundedCornerShape(6.dp),
                  colors = ButtonDefaults.outlinedButtonColors(
                    disabledContainerColor = Ink.copy(alpha = 0.04f),
                    disabledContentColor = Ink.copy(alpha = 0.35f),
                  ),
                ) {
                  Text(
                    name,
                    color = Ink.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                  )
                }
              }
            }
          }
        }
      }

      if (loading && s == null) {
        item {
          Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Ink, strokeWidth = 2.dp)
          }
        }
      }

      // Expiring section — red chrome, "Add all to shop" + per-item Add + per-item X dismiss.
      // Filter out anything the user has already dismissed locally so the optimistic
      // hide is honored even before the next backend refresh lands.
      val visibleExpiring = s?.expiring?.filter { it.id !in dismissedExpiring }.orEmpty()
      if (visibleExpiring.isNotEmpty()) {
        item {
          Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Outlined.AccessTime, null, tint = Terracotta, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
              "Use this week · ${visibleExpiring.size}",
              style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink,
              modifier = Modifier.weight(1f),
            )
            if (visibleExpiring.size > 1) {
              TextButton(onClick = { vm.restockAll(visibleExpiring) }) {
                Text("Add all", color = Terracotta, fontWeight = FontWeight.Medium)
              }
            }
          }
        }
        items(visibleExpiring, key = { "exp-${it.id}" }) { item ->
          ExpiringRow(
            item = item,
            onRestock = { vm.restock(item) },
            onDismiss = { vm.dismissExpiring(item) },
          )
        }
      }

      // Unlock more recipes
      if (!s?.unlocks.isNullOrEmpty()) {
        item { SectionHeader("Unlock more recipes", icon = Icons.Outlined.Bolt, tint = Olive) }
        items(s!!.unlocks, key = { "unl-${it.ingredient}" }) { sug ->
          UnlockRow(sug = sug, onAdd = { vm.addUnlock(sug) })
        }
      }

      // Regular list grouped by aisle — Library-style accordion spines, walk-the-store order.
      val items = s?.items.orEmpty()
      val byAisle = items.groupBy { (it.aisle ?: "other").lowercase() }
      val orderedAisles = AISLE_WALK_ORDER.filter { byAisle.containsKey(it.key) }
      if (items.isNotEmpty()) item { SectionHeader("Your list") }
      orderedAisles.forEach { aisleMeta ->
        val list = byAisle[aisleMeta.key] ?: emptyList()
        val isOpen = aisleMeta.key in expandedAisles.value
        item(key = "spine-${aisleMeta.key}") {
          Surface(
            onClick = {
              expandedAisles.value = if (isOpen) expandedAisles.value - aisleMeta.key
              else expandedAisles.value + aisleMeta.key
            },
            shape = RoundedCornerShape(10.dp),
            color = aisleMeta.color,
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 4.dp),
          ) {
            Row(
              Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              AisleImageOrGlyph(
                aisleKey = aisleMeta.key,
                size = 28.dp,
              )
              Spacer(Modifier.width(10.dp))
              Text(
                aisleMeta.label,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
              )
              Text(
                "${list.size}",
                style = MaterialTheme.typography.labelMedium,
                color = Ink.copy(alpha = 0.8f),
                fontWeight = FontWeight.SemiBold,
              )
              Spacer(Modifier.width(8.dp))
              Icon(
                if (isOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (isOpen) "Collapse" else "Expand",
                tint = Ink,
              )
            }
          }
        }
        if (isOpen) items(list, key = { it.id }) { item ->
          ShoppingRow(
            item,
            onToggle = { vm.toggleChecked(item) },
            onDelete = { vm.delete(item) },
            onMove = { moveTarget = item },
          )
        }
      }

      if (items.any { it.checked }) {
        item {
          Button(
            onClick = vm::clearChecked,
            modifier = Modifier.fillMaxWidth().padding(24.dp).height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink.copy(alpha = 0.08f), contentColor = Ink),
            shape = RoundedCornerShape(4.dp),
          ) { Text("Clear done") }
        }
      }

      if (!loading && items.isEmpty() && (s?.expiring.isNullOrEmpty()) && (s?.unlocks.isNullOrEmpty())) {
        item {
          Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Empty list", style = MaterialTheme.typography.titleMedium)
              Spacer(Modifier.height(4.dp))
              Text(
                "Save a recipe from Tonight and we'll auto-add missing ingredients.",
                style = MaterialTheme.typography.bodyMedium, color = InkMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
              )
            }
          }
        }
      }
    }

    // Banner pinned at bottom — only renders for non-Pro. Hidden if Pro.
    app.pantrie.billing.BannerAd()
   } // close Column

    // Aisle re-file sheet. Rendered inside the Scaffold so it gets proper insets.
    moveTarget?.let { item ->
      MoveToAisleSheet(
        item = item,
        onPick = { aisle ->
          vm.moveToAisle(item, aisle)
          moveTarget = null
        },
        onDismiss = { moveTarget = null },
      )
    }
  }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, tint: Color = Ink) {
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(6.dp))
    }
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink)
  }
}

@Composable
private fun ExpiringRow(item: ExpiringItem, onRestock: () -> Unit, onDismiss: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = Terracotta.copy(alpha = 0.06f),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
  ) {
    Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(item.name, style = MaterialTheme.typography.titleMedium, color = Ink)
        val label = when {
          item.daysLeft < 0 -> "Expired"
          item.daysLeft == 0 -> "Use today"
          item.daysLeft == 1 -> "1 day left"
          else -> "${item.daysLeft} days left"
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = Terracotta, fontWeight = FontWeight.Medium)
      }
      Button(
        onClick = onRestock,
        colors = ButtonDefaults.buttonColors(containerColor = Terracotta),
        shape = RoundedCornerShape(4.dp),
      ) {
        Text("Add to shop", color = Paper, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
      }
      IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
        Icon(
          Icons.Outlined.Close,
          contentDescription = "Dismiss",
          tint = InkMuted,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

@Composable
private fun UnlockRow(sug: UnlockSuggestion, onAdd: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = Olive.copy(alpha = 0.06f),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
  ) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(
          "Add ${sug.ingredient}",
          style = MaterialTheme.typography.titleMedium,
          color = Ink,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          "Unlocks ${sug.count} new recipes",
          style = MaterialTheme.typography.bodyMedium,
          color = Olive,
          fontWeight = FontWeight.Bold,
        )
        val recipeHint = sug.recipes.take(2).joinToString(", ") { it.title }
        if (recipeHint.isNotBlank()) {
          Text(
            "incl. $recipeHint",
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            maxLines = 1,
          )
        }
      }
      Button(
        onClick = onAdd,
        colors = ButtonDefaults.buttonColors(containerColor = Olive),
        shape = RoundedCornerShape(4.dp),
      ) { Text("Add", color = Paper, style = MaterialTheme.typography.labelMedium) }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShoppingRow(
  item: ShoppingItem,
  onToggle: () -> Unit,
  onDelete: () -> Unit,
  onMove: () -> Unit,
) {
  Row(
    // combinedClickable keeps single-tap → toggle semantics while adding long-press →
    // "move to aisle" without the two gestures fighting each other.
    modifier = Modifier
      .fillMaxWidth()
      .combinedClickable(onClick = onToggle, onLongClick = onMove)
      .padding(horizontal = 24.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      if (item.checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
      contentDescription = null,
      tint = if (item.checked) Olive else InkFaint,
    )
    Spacer(Modifier.width(10.dp))
    IngredientImageOrEmoji(name = item.name, size = 28.dp)
    Spacer(Modifier.width(8.dp))
    Column(Modifier.weight(1f)) {
      Text(
        item.name,
        style = MaterialTheme.typography.titleMedium,
        color = if (item.checked) InkMuted else Ink,
        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
      )
      val sub = listOfNotNull(
        item.quantity?.let { q -> "${if (q == q.toInt().toDouble()) q.toInt().toString() else q} ${item.unit ?: ""}".trim() },
        item.source.takeIf { it != "manual" }?.replace("-", " "),
      ).joinToString(" · ")
      if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = InkMuted)
    }
    // Discoverable affordance — users who don't stumble onto long-press still see a move icon.
    IconButton(onClick = onMove) {
      Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = "Move to aisle", tint = InkFaint)
    }
    IconButton(onClick = onDelete) {
      Icon(Icons.Outlined.Delete, contentDescription = "Remove", tint = InkFaint)
    }
  }
  HorizontalDivider(color = InkFaint.copy(alpha = 0.3f))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveToAisleSheet(
  item: ShoppingItem,
  onPick: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val current = (item.aisle ?: "other").lowercase()
  ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Paper) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
      Text(
        "Move \"${item.name}\" to…",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = Ink,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        "Override this item's aisle. Only affects this row — future adds use the default.",
        style = MaterialTheme.typography.bodySmall,
        color = InkMuted,
      )
      Spacer(Modifier.height(12.dp))
      AISLES.forEach { aisle ->
        val selected = aisle == current
        Row(
          Modifier
            .fillMaxWidth()
            .clickable { onPick(aisle) }
            .padding(vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            if (selected) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) Olive else InkFaint,
          )
          Spacer(Modifier.width(12.dp))
          Text(
            aisle.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
          )
        }
      }
    }
  }
}

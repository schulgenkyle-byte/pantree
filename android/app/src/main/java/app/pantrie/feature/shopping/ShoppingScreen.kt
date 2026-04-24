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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.*
import app.pantrie.ui.IngredientEmoji
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
    viewModelScope.launch {
      runCatching {
        api.addShopping(ShoppingAddRequest(
          name = item.name, quantity = item.quantity, unit = item.unit,
          aisle = item.category ?: "other", source = "restock",
        ))
      }
      refresh()
    }
  }

  fun restockAll(items: List<ExpiringItem>) {
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
    }
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
  "beverage", "other",
)

@Composable
fun ShoppingScreen(vm: ShoppingViewModel = hiltViewModel()) {
  val smart by vm.smart.collectAsState()
  val loading by vm.loading.collectAsState()
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

  Scaffold(containerColor = Cream) { padding ->
    LazyColumn(
      Modifier.padding(padding).fillMaxSize(),
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

      if (loading && s == null) {
        item {
          Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Ink, strokeWidth = 2.dp)
          }
        }
      }

      // Expiring section — red chrome, "Add all to shop" convenience + per-item Add buttons
      if (!s?.expiring.isNullOrEmpty()) {
        item {
          Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Outlined.AccessTime, null, tint = Terracotta, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
              "Use this week · ${s!!.expiring.size}",
              style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Ink,
              modifier = Modifier.weight(1f),
            )
            if (s.expiring.size > 1) {
              TextButton(onClick = { vm.restockAll(s.expiring) }) {
                Text("Add all", color = Terracotta, fontWeight = FontWeight.Medium)
              }
            }
          }
        }
        items(s!!.expiring, key = { "exp-${it.id}" }) { item ->
          ExpiringRow(item = item, onRestock = { vm.restock(item) })
        }
      }

      // Unlock more recipes
      if (!s?.unlocks.isNullOrEmpty()) {
        item { SectionHeader("Unlock more recipes", icon = Icons.Outlined.Bolt, tint = Olive) }
        items(s!!.unlocks, key = { "unl-${it.ingredient}" }) { sug ->
          UnlockRow(sug = sug, onAdd = { vm.addUnlock(sug) })
        }
      }

      // Regular list grouped by aisle
      val items = s?.items.orEmpty()
      val byAisle = items.groupBy { it.aisle ?: "other" }.toSortedMap()
      if (items.isNotEmpty()) item { SectionHeader("Your list") }
      byAisle.forEach { (aisle, list) ->
        item {
          Text(
            aisle.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelMedium,
            color = InkSoft,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
          )
        }
        items(list, key = { it.id }) { item ->
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
private fun ExpiringRow(item: ExpiringItem, onRestock: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = Terracotta.copy(alpha = 0.06f),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
  ) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Text(sug.ingredient, style = MaterialTheme.typography.titleMedium, color = Ink)
        val recipeHint = sug.recipes.take(2).joinToString(", ") { it.title }
        Text(
          "+${sug.count} recipes${if (recipeHint.isNotBlank()) " · $recipeHint" else ""}",
          style = MaterialTheme.typography.bodySmall, color = Olive, fontWeight = FontWeight.Medium,
          maxLines = 1,
        )
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
    val emoji = IngredientEmoji.forName(item.name)
    if (emoji != null) {
      Text(emoji, style = MaterialTheme.typography.titleLarge)
      Spacer(Modifier.width(8.dp))
    }
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
  ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Cream) {
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

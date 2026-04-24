package app.pantrie.feature.pantry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.data.entities.PantryDao
import app.pantrie.data.entities.PantryItemEntity
import app.pantrie.feature.savings.SavingsCard
import app.pantrie.network.PantrieApi
import app.pantrie.ui.IngredientEmoji
import app.pantrie.network.dto.HomeStats
import app.pantrie.network.dto.PantryAddRequest
import app.pantrie.network.dto.PantryBulkRequest
import app.pantrie.network.dto.WasteLogRequest
import app.pantrie.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PantryViewModel @Inject constructor(
  private val dao: PantryDao,
  private val api: PantrieApi,
  private val refreshBus: app.pantrie.feature.app.RefreshBus,
) : ViewModel() {
  val items = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  private val _home = MutableStateFlow<HomeStats?>(null)
  val home = _home.asStateFlow()

  init {
    refreshHome()
    oneShotSyncToBackend()
  }

  fun refreshHome() {
    viewModelScope.launch {
      runCatching { api.homeStats() }.onSuccess { _home.value = it }
    }
  }

  /**
   * Migration helper — push all local Room pantry items to the backend once.
   * Needed because earlier versions only wrote pantry locally; the deck endpoint
   * reads from backend and was seeing 0 items → 0% match on every recipe.
   * Runs on every app open but the backend `/pantry/bulk` is idempotent-ish (creates new rows
   * each call, but dedupe is handled by the backend deck matching by name).
   */
  data class SyncStatus(
    val local: Int = 0,
    val server: Int = 0,
    val pushed: Int = 0,
    val error: String? = null,
    val lastSyncEpochMs: Long? = null,
  )
  private val _syncStatus = MutableStateFlow(SyncStatus())
  val syncStatus = _syncStatus.asStateFlow()

  private val _syncMessage = MutableStateFlow<String?>(null)
  val syncMessage = _syncMessage.asStateFlow()

  private fun oneShotSyncToBackend() {
    viewModelScope.launch { syncToBackend() }
  }

  fun syncToBackendManual() {
    viewModelScope.launch { syncToBackend(showMessage = true) }
  }

  fun clearSyncMessage() { _syncMessage.value = null }

  private suspend fun syncToBackend(showMessage: Boolean = false): Int {
    val local = runCatching { dao.getAll() }.getOrDefault(emptyList())
    android.util.Log.i("PantrieSync", "local items: ${local.size}")

    // GET /pantry — if this fails we STILL know the local count, show error to user
    val backendResult = runCatching { api.pantry().items }
    val backend = backendResult.getOrDefault(emptyList())
    if (backendResult.isFailure) {
      val msg = backendResult.exceptionOrNull()?.message ?: "unknown"
      android.util.Log.e("PantrieSync", "GET /pantry failed: $msg", backendResult.exceptionOrNull())
      _syncStatus.value = SyncStatus(local = local.size, server = -1, error = "GET failed: $msg", lastSyncEpochMs = System.currentTimeMillis())
      if (showMessage) _syncMessage.value = "Sync GET failed: ${msg.take(60)}"
      return 0
    }
    android.util.Log.i("PantrieSync", "backend has: ${backend.size}")

    if (local.isEmpty()) {
      _syncStatus.value = SyncStatus(local = 0, server = backend.size, lastSyncEpochMs = System.currentTimeMillis())
      if (showMessage) _syncMessage.value = "No local items to sync"
      return 0
    }

    val backendNames = backend.map { it.name.lowercase().trim() }.toSet()
    val missing = local.filter { it.name.lowercase().trim() !in backendNames }
    android.util.Log.i("PantrieSync", "missing from backend: ${missing.size}")

    if (missing.isEmpty()) {
      _syncStatus.value = SyncStatus(local = local.size, server = backend.size, lastSyncEpochMs = System.currentTimeMillis())
      if (showMessage) _syncMessage.value = "Already in sync (${backend.size} on server)"
      refreshHome()
      refreshBus.bumpPantry()
      return 0
    }

    // Chunk in batches of 50 to stay well under the backend 500-item cap and avoid huge payloads
    var pushed = 0
    val chunks = missing.chunked(50)
    android.util.Log.i("PantrieSync", "uploading ${missing.size} items in ${chunks.size} chunks of up to 50")
    for ((i, chunk) in chunks.withIndex()) {
      val result = runCatching {
        api.addPantryItemsBulk(
          PantryBulkRequest(
            items = chunk.map {
              PantryAddRequest(
                name = it.name,
                category = it.category,
                quantity = it.quantity,
                unit = it.unit,
                expiresAt = it.expiresAt,
              )
            },
          ),
        )
      }
      if (result.isFailure) {
        val msg = result.exceptionOrNull()?.message ?: "unknown"
        android.util.Log.e("PantrieSync", "chunk ${i + 1}/${chunks.size} FAILED: $msg", result.exceptionOrNull())
        _syncStatus.value = SyncStatus(
          local = local.size, server = backend.size + pushed,
          error = "Chunk ${i + 1}/${chunks.size}: ${msg.take(80)}",
          lastSyncEpochMs = System.currentTimeMillis(),
        )
        if (showMessage) _syncMessage.value = "Push failed at chunk ${i + 1}: ${msg.take(50)}"
        return pushed
      }
      pushed += result.getOrNull()?.added?.size ?: 0
    }
    android.util.Log.i("PantrieSync", "pushed $pushed items to backend")
    _syncStatus.value = SyncStatus(local = local.size, server = backend.size + pushed, pushed = pushed, lastSyncEpochMs = System.currentTimeMillis())
    if (showMessage) _syncMessage.value = "Pushed $pushed items (server now has ${backend.size + pushed})"
    refreshHome()
    refreshBus.bumpPantry()
    return pushed
  }

  fun delete(id: String) { viewModelScope.launch { dao.delete(id) } }

  private val _clearing = MutableStateFlow(false)
  val clearing = _clearing.asStateFlow()

  fun clearPantry() {
    viewModelScope.launch {
      _clearing.value = true
      val local = runCatching { dao.getAll() }.getOrDefault(emptyList())
      // Delete server-side first so we don't leave orphan server rows if the app is killed mid-clear.
      for (it in local) runCatching { api.deletePantryItem(it.id) }
      // Also grab anything on the server that wasn't in local (e.g., added from another device).
      runCatching { api.pantry().items }.getOrDefault(emptyList())
        .filter { s -> local.none { it.id == s.id } }
        .forEach { s -> runCatching { api.deletePantryItem(s.id) } }
      runCatching { dao.clear() }
      refreshHome()
      refreshBus.bumpPantry()
      _syncMessage.value = "Pantry cleared"
      _clearing.value = false
    }
  }

  fun logAndRemove(item: PantryItemEntity, action: String) {
    viewModelScope.launch {
      runCatching {
        api.logWaste(
          WasteLogRequest(
            itemId = item.id,
            name = item.name,
            category = item.category ?: "other",
            quantity = item.quantity,
            unit = item.unit,
            action = action,
          )
        )
      }
      dao.delete(item.id)
      refreshHome()
    }
  }
}

private data class ExpiryInfo(val daysLeft: Int?, val label: String?, val tint: Color)

private fun expiryFor(expiresAt: String?): ExpiryInfo {
  if (expiresAt.isNullOrBlank()) return ExpiryInfo(null, null, InkFaint)
  val millis = runCatching {
    if (expiresAt.matches(Regex("^\\d{10,}$"))) expiresAt.toLong()
    else SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(expiresAt)?.time
  }.getOrNull() ?: return ExpiryInfo(null, null, InkFaint)
  val delta = millis - System.currentTimeMillis()
  val days = (delta / 86_400_000L).toInt()
  return when {
    days < 0 -> ExpiryInfo(days, "Expired", Terracotta)
    days <= 2 -> ExpiryInfo(days, if (days == 0) "Today" else "${days}d left", Terracotta)
    days <= 5 -> ExpiryInfo(days, "${days}d left", Color(0xFFD4A017))
    else -> ExpiryInfo(days, "${days}d", Olive)
  }
}

private fun sortByExpiry(list: List<PantryItemEntity>): List<PantryItemEntity> {
  return list.sortedWith(compareBy({ expiryFor(it.expiresAt).daysLeft ?: Int.MAX_VALUE }, { it.createdAt }))
}

@Composable
fun PantryScreen(
  onScan: () -> Unit,
  onBarcode: () -> Unit = {},
  onReceipt: () -> Unit = {},
  vm: PantryViewModel = hiltViewModel(),
) {
  val items by vm.items.collectAsState()
  val home by vm.home.collectAsState()
  val syncMsg by vm.syncMessage.collectAsState()
  val sortedItems = remember(items) { sortByExpiry(items) }

  syncMsg?.let { msg ->
    LaunchedEffect(msg) { kotlinx.coroutines.delay(3000); vm.clearSyncMessage() }
  }

  Scaffold(containerColor = Cream) { padding ->
    LazyColumn(
      modifier = Modifier.padding(padding).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
          Text("Your pantry",
            style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Normal)
          Text("What you have on hand",
            style = MaterialTheme.typography.bodyLarge, color = InkSoft)
        }
      }

      // Scan nudge banner
      home?.scanNudge?.takeIf { it.show }?.let { nudge ->
        item { NudgeCard(message = nudge.message, onAction = onScan, actionText = "Scan now") }
      }
      // Expiring nudge
      home?.expiringNudge?.takeIf { it.show }?.let { nudge ->
        item { NudgeCard(message = nudge.message, tint = Color(0xFFD4A017), onAction = null, actionText = null) }
      }

      item { SavingsCard() }

      item {
        Button(
          onClick = onScan,
          modifier = Modifier.fillMaxWidth().height(56.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Ink),
          shape = RoundedCornerShape(4.dp),
        ) {
          Icon(Icons.Outlined.CameraAlt, null, tint = Paper, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(10.dp))
          Text("Snap your shelves", color = Paper, fontWeight = FontWeight.SemiBold)
        }
      }
      item {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onBarcode, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(4.dp)) {
            Icon(Icons.Outlined.QrCodeScanner, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Barcode", fontWeight = FontWeight.Medium)
          }
          OutlinedButton(onClick = onReceipt, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(4.dp)) {
            Icon(Icons.Outlined.Receipt, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Receipt", fontWeight = FontWeight.Medium)
          }
        }
      }

      // Clear pantry (confirm first) — only show if there's anything to clear
      if (sortedItems.isNotEmpty()) {
        item {
          var confirmClear by remember { mutableStateOf(false) }
          OutlinedButton(
            onClick = { confirmClear = true },
            modifier = Modifier.fillMaxWidth().height(44.dp).padding(top = 4.dp),
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Terracotta),
          ) {
            Icon(Icons.Outlined.DeleteSweep, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Clear pantry", style = MaterialTheme.typography.labelMedium)
          }
          if (confirmClear) {
            AlertDialog(
              onDismissRequest = { confirmClear = false },
              title = { Text("Clear all pantry items?") },
              text = { Text("Removes all ${sortedItems.size} item${if (sortedItems.size == 1) "" else "s"} on this device and the server. This can't be undone.") },
              confirmButton = {
                TextButton(onClick = { confirmClear = false; vm.clearPantry() }) {
                  Text("Clear", color = Terracotta, fontWeight = FontWeight.SemiBold)
                }
              },
              dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
            )
          }
        }
      }

      if (sortedItems.isEmpty()) {
        item {
          Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(12.dp)) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(32.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text("No ingredients yet", style = MaterialTheme.typography.titleMedium, color = Ink)
              Spacer(Modifier.height(4.dp))
              Text("Snap a photo, scan a barcode, or photograph a receipt.",
                style = MaterialTheme.typography.bodyMedium, color = InkMuted)
            }
          }
        }
      } else {
        // Group by store-aisle category. Sort sections by typical store walk order; within each
        // section, preserve the expiry-first sort the user already expects.
        val AISLE_ORDER = listOf(
          "produce", "bakery", "deli", "protein", "dairy",
          "frozen", "grain", "pantry", "spice", "condiment",
          "beverage", "other",
        )
        val grouped = sortedItems.groupBy { (it.category ?: "other").lowercase() }
        val ordered = AISLE_ORDER.filter { grouped.containsKey(it) } + grouped.keys.filter { it !in AISLE_ORDER }
        for (aisle in ordered) {
          val rows = grouped[aisle] ?: continue
          item {
            Text(
              aisle.replaceFirstChar { it.uppercase() } + " · ${rows.size}",
              style = MaterialTheme.typography.labelMedium,
              color = InkSoft,
              fontWeight = FontWeight.Medium,
              modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
            )
          }
          items(rows, key = { it.id }) { item ->
            PantryRow(
              item = item,
              onCooked = { vm.logAndRemove(item, "cooked") },
              onWasted = { vm.logAndRemove(item, "wasted") },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun NudgeCard(message: String, tint: Color = Ink, onAction: (() -> Unit)?, actionText: String?) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = tint.copy(alpha = 0.08f),
    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
  ) {
    Row(
      Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(message, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.weight(1f))
      if (onAction != null && actionText != null) {
        TextButton(onClick = onAction) { Text(actionText, color = tint, fontWeight = FontWeight.Medium) }
      }
    }
  }
}

@Composable
private fun PantryRow(
  item: PantryItemEntity,
  onCooked: () -> Unit,
  onWasted: () -> Unit,
) {
  val exp = expiryFor(item.expiresAt)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(
        if (exp.daysLeft != null && exp.daysLeft <= 2) exp.tint.copy(alpha = 0.06f) else Color.Transparent,
        RoundedCornerShape(4.dp),
      )
      .padding(start = if (exp.daysLeft != null && exp.daysLeft <= 5) 8.dp else 0.dp)
      .padding(vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Left-border accent for expiring items
    if (exp.daysLeft != null && exp.daysLeft <= 5) {
      Box(
        Modifier.width(3.dp).height(36.dp).background(exp.tint, RoundedCornerShape(2.dp)),
      )
      Spacer(Modifier.width(10.dp))
    }
    val emoji = IngredientEmoji.forName(item.name)
    if (emoji != null) {
      Text(emoji, style = MaterialTheme.typography.titleLarge)
      Spacer(Modifier.width(10.dp))
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(item.name,
        style = MaterialTheme.typography.titleMedium, color = Ink, fontWeight = FontWeight.Medium)
      val sub = listOfNotNull(
        item.category?.replaceFirstChar { it.uppercase() },
        item.quantity?.let { "${if (it == it.toInt().toDouble()) it.toInt().toString() else it} ${item.unit ?: ""}".trim() },
        exp.label,
      ).joinToString(" · ")
      if (sub.isNotBlank()) {
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = if (exp.daysLeft != null && exp.daysLeft <= 2) exp.tint else InkMuted)
      }
    }
    IconButton(onClick = onCooked) {
      Icon(Icons.Outlined.LocalFireDepartment, contentDescription = "Mark cooked", tint = Olive)
    }
    IconButton(onClick = onWasted) {
      Icon(Icons.Outlined.Delete, contentDescription = "Mark wasted", tint = Terracotta)
    }
  }
  HorizontalDivider(color = InkFaint.copy(alpha = 0.3f))
}

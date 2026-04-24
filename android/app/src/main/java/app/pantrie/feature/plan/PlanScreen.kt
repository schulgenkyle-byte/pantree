package app.pantrie.feature.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.CreatePlanRequest
import app.pantrie.network.dto.PlanProposal
import app.pantrie.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

private val DAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

sealed interface PlanUiState {
  data object Empty : PlanUiState
  data object Loading : PlanUiState
  data class Proposed(val days: List<DayProposal>) : PlanUiState
  data object Saved : PlanUiState
  data class Error(val message: String) : PlanUiState
}

data class DayProposal(
  val dayLabel: String,
  val date: String,
  val dinner: PlanProposal?,
  val accepted: Boolean = true,
)

@HiltViewModel
class PlanViewModel @Inject constructor(
  private val api: PantrieApi,
) : ViewModel() {
  private val _state = MutableStateFlow<PlanUiState>(PlanUiState.Empty)
  val state = _state.asStateFlow()

  fun proposeWeek() {
    viewModelScope.launch {
      _state.value = PlanUiState.Loading
      runCatching { api.proposePlan(slots = 7) }
        .onSuccess { resp ->
          val today = Calendar.getInstance()
          val fmt = SimpleDateFormat("MMM d", Locale.US)
          val days = (0 until 7).map { i ->
            val cal = today.clone() as Calendar
            cal.add(Calendar.DAY_OF_MONTH, i)
            val label = DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]
            DayProposal(
              dayLabel = if (i == 0) "Today" else label,
              date = fmt.format(cal.time),
              dinner = resp.proposals.getOrNull(i),
            )
          }
          _state.value = PlanUiState.Proposed(days)
        }
        .onFailure { _state.value = PlanUiState.Error(it.message ?: "Failed to propose") }
    }
  }

  fun toggleAccept(idx: Int) {
    val cur = _state.value as? PlanUiState.Proposed ?: return
    val newList = cur.days.mapIndexed { i, d -> if (i == idx) d.copy(accepted = !d.accepted) else d }
    _state.value = PlanUiState.Proposed(newList)
  }

  fun lockIn() {
    val cur = _state.value as? PlanUiState.Proposed ?: return
    val ids = cur.days.filter { it.accepted }.mapNotNull { it.dinner?.id }
    if (ids.isEmpty()) return
    viewModelScope.launch {
      _state.value = PlanUiState.Loading
      runCatching { api.createPlan(CreatePlanRequest(recipeIds = ids, name = "This week")) }
        .onSuccess { _state.value = PlanUiState.Saved }
        .onFailure { _state.value = PlanUiState.Error(it.message ?: "Save failed") }
    }
  }

  fun reset() { _state.value = PlanUiState.Empty }

  // ---------- Rotate alternatives ----------
  private val _altSheet = MutableStateFlow<AltSheetState?>(null)
  val altSheet = _altSheet.asStateFlow()

  fun requestAlternatives(dayIndex: Int) {
    val cur = _state.value as? PlanUiState.Proposed ?: return
    val day = cur.days.getOrNull(dayIndex) ?: return
    val current = day.dinner ?: return
    _altSheet.value = AltSheetState(dayIndex = dayIndex, loading = true)
    viewModelScope.launch {
      val excluded = cur.days.mapNotNull { it.dinner?.id }
      runCatching {
        api.planAlternatives(app.pantrie.network.dto.PlanAlternativesRequest(
          recipeId = current.id, excludeIds = excluded,
        ))
      }.onSuccess { resp ->
        _altSheet.value = AltSheetState(
          dayIndex = dayIndex, loading = false, alternatives = resp.alternatives,
        )
      }.onFailure {
        _altSheet.value = AltSheetState(dayIndex = dayIndex, loading = false, error = it.message?.take(80) ?: "Failed")
      }
    }
  }

  fun pickAlternative(alt: app.pantrie.network.dto.PlanAlternative) {
    val sheet = _altSheet.value ?: return
    val cur = _state.value as? PlanUiState.Proposed ?: return
    val newList = cur.days.mapIndexed { i, d ->
      if (i != sheet.dayIndex) d
      else d.copy(dinner = PlanProposal(
        id = alt.id, title = alt.title, cuisine = alt.cuisine,
        prepMinutes = alt.prepMinutes, cookMinutes = alt.cookMinutes,
        servings = alt.servings,
        pantryMatchPercent = alt.pantryMatchPercent,
        usesExpiring = emptyList(),
        reason = "Swapped · ${alt.pantryMatchPercent}% match",
      ))
    }
    _state.value = PlanUiState.Proposed(newList)
    _altSheet.value = null
  }

  fun dismissAlternatives() { _altSheet.value = null }
}

data class AltSheetState(
  val dayIndex: Int,
  val loading: Boolean = false,
  val alternatives: List<app.pantrie.network.dto.PlanAlternative> = emptyList(),
  val error: String? = null,
)

@Composable
fun PlanScreen(
  onOpenMealPrep: () -> Unit = {},
  vm: PlanViewModel = hiltViewModel(),
) {
  val state by vm.state.collectAsState()
  val altSheet by vm.altSheet.collectAsState()

  // Alternatives bottom sheet
  altSheet?.let { sheet ->
    AlternativesSheet(
      loading = sheet.loading,
      error = sheet.error,
      alternatives = sheet.alternatives,
      onPick = { vm.pickAlternative(it) },
      onDismiss = { vm.dismissAlternatives() },
    )
  }

  Scaffold(containerColor = Cream) { padding ->
    LazyColumn(
      Modifier.padding(padding).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Text("Plan", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Normal)
        Text("Your week, optimized for what's in your pantry.",
          style = MaterialTheme.typography.bodyLarge, color = InkSoft)
      }

      item {
        OutlinedButton(
          onClick = onOpenMealPrep,
          modifier = Modifier.fillMaxWidth().height(48.dp),
          shape = RoundedCornerShape(4.dp),
        ) {
          Icon(Icons.Outlined.Restaurant, null, tint = Ink, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(8.dp))
          Text("Meal Prep Mode", color = Ink, fontWeight = FontWeight.Medium)
        }
      }

      when (val s = state) {
        PlanUiState.Empty -> item {
          Column(Modifier.padding(top = 12.dp)) {
            Button(
              onClick = vm::proposeWeek,
              modifier = Modifier.fillMaxWidth().height(56.dp),
              colors = ButtonDefaults.buttonColors(containerColor = Terracotta),
              shape = RoundedCornerShape(4.dp),
            ) {
              Icon(Icons.Outlined.Bolt, null, tint = Paper, modifier = Modifier.size(20.dp))
              Spacer(Modifier.width(8.dp))
              Text("Plan it for me", color = Paper, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
              "Picks 7 dinners that use what you have, prioritize what's expiring, and vary the cuisines.",
              style = MaterialTheme.typography.bodySmall, color = InkMuted,
            )
          }
        }

        PlanUiState.Loading -> item {
          Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Ink, strokeWidth = 2.dp)
          }
        }

        is PlanUiState.Proposed -> {
          item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(
                onClick = vm::reset,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(4.dp),
              ) { Text("Reset") }
              Button(
                onClick = vm::lockIn,
                modifier = Modifier.weight(2f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
                shape = RoundedCornerShape(4.dp),
                enabled = s.days.any { it.accepted },
              ) {
                Text(
                  "Lock in ${s.days.count { it.accepted }} meals",
                  color = Paper, fontWeight = FontWeight.Medium,
                )
              }
            }
          }
          items(count = s.days.size, key = { s.days[it].dayLabel + s.days[it].date }) { idx ->
            DayCard(
              day = s.days[idx],
              onToggle = { vm.toggleAccept(idx) },
              onSwap = { vm.requestAlternatives(idx) },
            )
          }
        }

        PlanUiState.Saved -> item {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Olive.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(Modifier.padding(24.dp)) {
              Text("Week locked in ✓", style = MaterialTheme.typography.titleLarge, color = Olive, fontWeight = FontWeight.SemiBold)
              Spacer(Modifier.height(4.dp))
              Text(
                "Missing ingredients were auto-added to your shopping list. Head to Shop to review.",
                style = MaterialTheme.typography.bodyMedium, color = Ink,
              )
              Spacer(Modifier.height(12.dp))
              Button(
                onClick = vm::reset,
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
                shape = RoundedCornerShape(4.dp),
              ) { Text("Plan another week", color = Paper) }
            }
          }
        }

        is PlanUiState.Error -> item {
          Column {
            Text("Something went wrong: ${s.message}", color = Terracotta, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(
              onClick = vm::reset,
              colors = ButtonDefaults.buttonColors(containerColor = Ink),
              shape = RoundedCornerShape(4.dp),
            ) { Text("Try again", color = Paper) }
          }
        }
      }
    }
  }
}

@Composable
private fun DayCard(day: DayProposal, onToggle: () -> Unit, onSwap: () -> Unit = {}) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = if (day.accepted) Paper else Paper.copy(alpha = 0.5f),
    modifier = Modifier.fillMaxWidth().clickable { if (day.dinner != null) onToggle() },
  ) {
    Column(Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          day.dayLabel,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = if (day.dayLabel == "Today") Terracotta else Ink,
        )
        Spacer(Modifier.width(6.dp))
        Text(day.date, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        Spacer(Modifier.weight(1f))
        if (day.dinner != null) {
          IconButton(onClick = onSwap, modifier = Modifier.size(32.dp)) {
            Icon(
              androidx.compose.material.icons.Icons.Outlined.Refresh,
              contentDescription = "Show alternatives",
              tint = InkMuted,
              modifier = Modifier.size(18.dp),
            )
          }
          Spacer(Modifier.width(4.dp))
          Text(
            if (day.accepted) "ON" else "OFF",
            style = MaterialTheme.typography.labelSmall,
            color = if (day.accepted) Olive else InkFaint,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
      Spacer(Modifier.height(8.dp))
      if (day.dinner == null) {
        Text("No suggestion — try scanning more items.",
          style = MaterialTheme.typography.bodyMedium, color = InkMuted)
      } else {
        Text(
          day.dinner.title,
          style = MaterialTheme.typography.titleLarge,
          color = if (day.accepted) Ink else InkMuted,
        )
        val totalMin = (day.dinner.prepMinutes ?: 0) + (day.dinner.cookMinutes ?: 0)
        val meta = listOfNotNull(
          day.dinner.cuisine,
          if (totalMin > 0) "$totalMin min" else null,
          "${day.dinner.pantryMatchPercent}% match",
        ).joinToString(" · ")
        Text(meta, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        if (day.dinner.reason.isNotBlank()) {
          Spacer(Modifier.height(6.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (day.dinner.usesExpiring.isNotEmpty()) {
              Icon(Icons.Outlined.LocalFireDepartment, null, tint = Terracotta, modifier = Modifier.size(14.dp))
              Spacer(Modifier.width(4.dp))
            }
            Text(
              day.dinner.reason,
              style = MaterialTheme.typography.labelMedium,
              color = if (day.dinner.usesExpiring.isNotEmpty()) Terracotta else Olive,
              fontWeight = FontWeight.Medium,
            )
          }
        }
      }
    }
  }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AlternativesSheet(
  loading: Boolean,
  error: String?,
  alternatives: List<app.pantrie.network.dto.PlanAlternative>,
  onPick: (app.pantrie.network.dto.PlanAlternative) -> Unit,
  onDismiss: () -> Unit,
) {
  androidx.compose.material3.ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = Cream,
  ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
      Text("Swap this meal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Ink)
      Text(
        "Similar cuisine, similar time. Doesn't use a swipe.",
        style = MaterialTheme.typography.bodyMedium, color = InkMuted,
      )
      Spacer(Modifier.height(16.dp))
      when {
        loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
          CircularProgressIndicator(color = Ink, strokeWidth = 2.dp)
        }
        error != null -> Text(error, color = Terracotta, style = MaterialTheme.typography.bodyMedium)
        alternatives.isEmpty() -> Text(
          "No good alternatives right now — try scanning more pantry items.",
          color = InkMuted, style = MaterialTheme.typography.bodyMedium,
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          alternatives.forEach { alt ->
            Surface(
              onClick = { onPick(alt) },
              shape = RoundedCornerShape(12.dp),
              color = Paper,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                  Text(alt.title, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 2)
                  val totalMin = (alt.prepMinutes ?: 0) + (alt.cookMinutes ?: 0)
                  val sub = listOfNotNull(
                    alt.cuisine?.takeIf { it.isNotBlank() },
                    if (totalMin > 0) "${totalMin} min" else null,
                    "${alt.pantryMatchPercent}% match",
                  ).joinToString(" · ")
                  Text(sub, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                }
              }
            }
          }
        }
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}

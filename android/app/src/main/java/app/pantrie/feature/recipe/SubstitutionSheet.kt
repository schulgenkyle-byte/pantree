package app.pantrie.feature.recipe

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.Substitute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SubUiState {
  data object Idle : SubUiState
  data object Loading : SubUiState
  data class Loaded(val ingredient: String, val subs: List<Substitute>) : SubUiState
  data class Error(val message: String) : SubUiState
}

@HiltViewModel
class SubstitutionViewModel @Inject constructor(
  private val api: PantrieApi,
) : ViewModel() {
  private val _state = MutableStateFlow<SubUiState>(SubUiState.Idle)
  val state = _state.asStateFlow()

  fun load(ingredient: String) {
    viewModelScope.launch {
      _state.value = SubUiState.Loading
      runCatching { api.substitutions(ingredient) }
        .onSuccess { _state.value = SubUiState.Loaded(it.ingredient, it.subs) }
        .onFailure { _state.value = SubUiState.Error(it.message ?: "Failed") }
    }
  }

  fun dismiss() { _state.value = SubUiState.Idle }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubstitutionSheet(
  ingredient: String?,
  onDismiss: () -> Unit,
  vm: SubstitutionViewModel = hiltViewModel(),
) {
  if (ingredient == null) return
  LaunchedEffect(ingredient) { vm.load(ingredient) }

  ModalBottomSheet(onDismissRequest = { vm.dismiss(); onDismiss() }) {
    val state by vm.state.collectAsState()
    Column(Modifier.padding(24.dp)) {
      Text("Substitutes for $ingredient", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(12.dp))
      when (val s = state) {
        SubUiState.Idle, SubUiState.Loading -> CircularProgressIndicator()
        is SubUiState.Loaded -> {
          if (s.subs.isEmpty()) Text("No known substitutes.") else {
            s.subs.forEach { sub ->
              Column(Modifier.padding(vertical = 8.dp)) {
                Text(sub.to, fontWeight = FontWeight.Medium)
                Text(sub.ratio, style = MaterialTheme.typography.bodySmall)
                if (sub.notes.isNotBlank()) Text(sub.notes, style = MaterialTheme.typography.bodySmall)
              }
              HorizontalDivider()
            }
          }
        }
        is SubUiState.Error -> Text(s.message)
      }
    }
  }
}

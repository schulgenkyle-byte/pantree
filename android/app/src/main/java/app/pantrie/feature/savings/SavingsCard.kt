package app.pantrie.feature.savings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.WasteSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavingsViewModel @Inject constructor(
  private val api: PantrieApi,
) : ViewModel() {
  private val _summary = MutableStateFlow<WasteSummary?>(null)
  val summary = _summary.asStateFlow()

  init { refresh("week") }

  fun refresh(range: String) {
    viewModelScope.launch {
      runCatching { api.wasteSummary(range) }.onSuccess { _summary.value = it }
    }
  }
}

@Composable
fun SavingsCard(vm: SavingsViewModel = hiltViewModel()) {
  val s by vm.summary.collectAsState()
  val summary = s ?: return
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = Color(0xFFF2F5EC),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
  ) {
    Column(Modifier.padding(16.dp)) {
      Text("Saved this ${summary.range}", style = MaterialTheme.typography.labelMedium, color = Color(0xFF5A6A3A))
      Spacer(Modifier.height(2.dp))
      Text("$${"%.2f".format(summary.savedUsd)}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(6.dp))
      Row {
        Text("${summary.cookedCount} cooked", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(12.dp))
        if (summary.wastedCount > 0) {
          Text("${summary.wastedCount} wasted · -$${"%.2f".format(summary.wastedUsd)}",
            style = MaterialTheme.typography.bodySmall, color = Color(0xFFB04A3C))
        }
      }
    }
  }
}

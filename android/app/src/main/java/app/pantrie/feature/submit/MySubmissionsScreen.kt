package app.pantrie.feature.submit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.MySubmission
import app.pantrie.ui.theme.*
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MySubmissionsViewModel @Inject constructor(
  private val api: PantrieApi,
) : ViewModel() {
  private val _subs = MutableStateFlow<List<MySubmission>>(emptyList())
  val subs = _subs.asStateFlow()
  private val _loading = MutableStateFlow(true)
  val loading = _loading.asStateFlow()

  init { refresh() }

  fun refresh() {
    viewModelScope.launch {
      _loading.value = true
      runCatching { api.mySubmissions() }.onSuccess { _subs.value = it.submissions }
      _loading.value = false
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySubmissionsScreen(
  onBack: () -> Unit,
  onNewSubmission: () -> Unit,
  vm: MySubmissionsViewModel = hiltViewModel(),
) {
  val subs by vm.subs.collectAsState()
  val loading by vm.loading.collectAsState()

  Scaffold(
    containerColor = Cream,
    topBar = {
      TopAppBar(
        title = { Text("My submissions", fontWeight = FontWeight.Normal) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream),
      )
    },
  ) { padding ->
    LazyColumn(
      Modifier.padding(padding).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        Button(
          onClick = onNewSubmission,
          colors = ButtonDefaults.buttonColors(containerColor = Terracotta),
          shape = RoundedCornerShape(4.dp),
          modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("+ Submit a new recipe", color = Paper, fontWeight = FontWeight.SemiBold) }
      }
      if (loading && subs.isEmpty()) {
        item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Ink, strokeWidth = 2.dp) } }
      } else if (subs.isEmpty()) {
        item {
          Text(
            "Submitted recipes appear here with review status.",
            style = MaterialTheme.typography.bodyMedium, color = InkMuted,
          )
        }
      } else {
        items(subs, key = { it.id }) { s -> SubRow(s) }
      }
    }
  }
}

@Composable
private fun SubRow(s: MySubmission) {
  val (label, tint) = when (s.status) {
    "pending" -> "In review" to InkMuted
    "approved" -> "Approved — live in catalog" to Olive
    "rejected" -> ("Rejected" + (s.rejectReason?.let { ": $it" } ?: "")) to Terracotta
    "duplicate" -> "Flagged as duplicate" to Color(0xFFD4A017)
    else -> s.status to InkMuted
  }
  Card(
    colors = CardDefaults.cardColors(containerColor = Paper),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(Modifier.padding(12.dp)) {
      if (!s.imageUrl.isNullOrBlank()) {
        AsyncImage(
          model = s.imageUrl, contentDescription = s.title,
          contentScale = ContentScale.Crop,
          modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
      }
      Column(Modifier.weight(1f)) {
        Text(s.title, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 2)
        s.cuisine?.let { if (it.isNotBlank()) Text(it, style = MaterialTheme.typography.labelSmall, color = InkMuted) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = tint, fontWeight = FontWeight.Medium)
      }
    }
  }
}

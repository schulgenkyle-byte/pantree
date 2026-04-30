@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantrie.feature.importlinks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Screen 1: paste up to 10 TikTok / YouTube links → submit job.
 * On success, navigates to ImportReviewScreen with the job ID.
 *
 * SCAFFOLD ONLY — visual polish, Brimm theming, and Pro-gate UX live in
 * the consumer-app PR (see HANDOFF.md). This file gives the next agent a
 * compiles-clean starting point.
 */
@Composable
fun ImportLinksScreen(
  onJobReady: (jobId: String) -> Unit,
  onCancel: () -> Unit,
  initialUrl: String? = null,
  vm: ImportLinksViewModel = hiltViewModel(),
) {
  val state by vm.state.collectAsState()
  var input by remember { mutableStateOf(initialUrl?.takeIf { it.isNotBlank() } ?: "") }

  // Auto-navigate when polling kicks off; the review screen will continue polling.
  LaunchedEffect(state) {
    val s = state
    if (s is ImportState.Reviewing) onJobReady(s.job.id)
    if (s is ImportState.Polling) onJobReady(s.jobId)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Import recipes from links") },
        navigationIcon = {
          TextButton(onClick = onCancel) { Text("Cancel") }
        },
      )
    },
  ) { pad ->
    Column(
      modifier = Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        "Paste up to 10 TikTok or YouTube cooking links — one per line. " +
          "We'll pull out the ingredients and steps and let you review before saving.",
      )

      OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        label = { Text("Links (one per line)") },
        placeholder = { Text("https://www.tiktok.com/...\nhttps://youtu.be/...") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
        minLines = 6,
      )

      val urls = remember(input) { input.split('\n').map { it.trim() }.filter { it.isNotEmpty() } }
      Text("${urls.size} link(s) detected", style = MaterialTheme.typography.labelMedium)

      val s = state
      when (s) {
        is ImportState.Submitting -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        is ImportState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
        else -> {}
      }

      Button(
        onClick = { vm.startImport(urls) },
        enabled = urls.isNotEmpty() && state !is ImportState.Submitting,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (state is ImportState.Submitting) "Starting…" else "Parse ${urls.size} link(s)")
      }
    }
  }
}

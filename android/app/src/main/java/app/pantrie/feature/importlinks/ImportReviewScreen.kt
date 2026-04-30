@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantrie.feature.importlinks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Screen 2: review parsed results, edit, submit-or-reject each.
 *
 * SCAFFOLD ONLY — the next agent should:
 *   1. Build a per-link expandable card with editable title / ingredients / steps.
 *   2. Show confidence + warnings + signals_used for transparency.
 *   3. Wire submitOne / rejectOne for each card.
 *   4. Add a "Submit all parsed" bulk action.
 *   5. Add a "Re-parse failed" option (re-runs only the failed seqs).
 */
@Composable
fun ImportReviewScreen(
  jobId: String,
  onClose: () -> Unit,
  vm: ImportLinksViewModel = hiltViewModel(),
) {
  // Each Compose destination gets its own ViewModel via hiltViewModel(), so
  // navigating paste→review hands us a fresh VM with state=Idle. attachToJob
  // sets state=Polling immediately and starts the poll loop on this jobId.
  // No-op if state is already past Idle (e.g. activity re-creation).
  val state by vm.state.collectAsState()
  LaunchedEffect(jobId) { vm.attachToJob(jobId) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Review imports") },
        navigationIcon = { TextButton(onClick = onClose) { Text("Done") } },
      )
    },
  ) { pad ->
    when (val s = state) {
      is ImportState.Polling -> Box(
        modifier = Modifier.padding(pad).fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        PouringIconLoader(
          label = "Pouring out your recipe…",
          sublabel = "${s.doneCount + s.failCount} of ${s.totalCount} done · ~${5 * (s.totalCount - s.doneCount - s.failCount)}s left",
        )
      }
      is ImportState.Reviewing -> LazyColumn(
        modifier = Modifier.padding(pad),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(s.links) { link ->
          LinkCard(
            link = link,
            onSubmit = { edits -> vm.submitOne(link.id, edits) },
            onReject = { vm.rejectOne(link.id) },
          )
        }
      }
      is ImportState.Error -> Text(s.message, modifier = Modifier.padding(pad).padding(24.dp))
      // Idle / Submitting fall through to the same brand loader so the user
      // never sees the default top-left CircularProgressIndicator.
      else -> Box(
        modifier = Modifier.padding(pad).fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        PouringIconLoader(
          label = "Pouring out your recipe…",
          sublabel = "Connecting…",
        )
      }
    }
  }
}

@Composable
private fun LinkCard(
  link: LinkJob,
  onSubmit: (SubmitImportedRequest) -> Unit,
  onReject: () -> Unit,
) {
  // Two card layouts — success vs failure. The failure path hits when the page
  // didn't expose enough text for Claude to extract a recipe (most common cause:
  // a pure-video TikTok with no caption / no pinned recipe comment). Showing
  // "0 ingredients · 0 steps" reads as a bug; an explicit error reads as honest.
  val failed = link.status == "failed" || link.status == "rejected" ||
    link.envelope?.ok == false || link.envelope?.recipe == null

  ElevatedCard(modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        link.envelope?.recipe?.title ?: link.url,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 2,
      )
      if (failed) {
        Text(
          "Couldn't read this link.",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.error,
        )
        val errorText = link.envelope?.error
          ?: link.envelope?.reason
          ?: "The page didn't have enough text — try a TikTok where the recipe is in the caption, or a YouTube cooking tutorial with captions on."
        Text(errorText, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onReject) { Text("Dismiss") }
      } else {
        val ings = link.envelope?.recipe?.ingredients.orEmpty()
        val steps = link.envelope?.recipe?.steps.orEmpty()
        Text(
          "${ings.size} ingredients · ${steps.size} steps · confidence ${link.envelope?.confidence?.let { "%.0f%%".format(it * 100) } ?: "—"}",
          style = MaterialTheme.typography.labelMedium,
        )
        if (link.envelope?.warnings?.isNotEmpty() == true) {
          Text(
            "Warning: " + link.envelope.warnings.joinToString("; "),
            style = MaterialTheme.typography.bodySmall,
          )
        }
        when (link.status) {
          // Already saved on the server. Disable the Save button entirely;
          // user can hit Done in the top bar to land on My Submissions where
          // the saved recipe lives.
          "submitted" -> Text(
            "✓ Saved · find it in My Submissions",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
          // Optimistic in-flight state set by the ViewModel the instant Save
          // is tapped — prevents double-tap while the network request is
          // pending.
          "submitting" -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Saving…", style = MaterialTheme.typography.labelMedium)
          }
          else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onReject) { Text("Skip") }
            Button(onClick = { onSubmit(SubmitImportedRequest()) }) {
              Text("Save to my recipes")
            }
          }
        }
      }
    }
  }
}

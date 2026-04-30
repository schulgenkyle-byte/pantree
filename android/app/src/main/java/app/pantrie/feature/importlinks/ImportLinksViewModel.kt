package app.pantrie.feature.importlinks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.network.PantrieApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State machine for the link-import flow:
 *   IDLE → SUBMITTING → POLLING → REVIEWING → DONE
 *
 * Polls every 3s while a job is running. Stops polling when status=done|failed
 * or we've polled 60 times (3 min cap).
 */
@HiltViewModel
class ImportLinksViewModel @Inject constructor(
  private val api: PantrieApi,
) : ViewModel() {

  private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
  val state: StateFlow<ImportState> = _state.asStateFlow()

  fun startImport(rawUrls: List<String>) {
    val cleaned = rawUrls.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(10)
    if (cleaned.isEmpty()) {
      _state.value = ImportState.Error("Paste at least one TikTok or YouTube link.")
      return
    }
    viewModelScope.launch {
      _state.value = ImportState.Submitting
      try {
        val resp = api.createImportJob(CreateImportJobRequest(urls = cleaned))
        _state.value = ImportState.Polling(jobId = resp.jobId, totalCount = resp.totalCount)
        pollUntilDone(resp.jobId)
      } catch (e: Exception) {
        _state.value = ImportState.Error(humanizeError(e))
      }
    }
  }

  private suspend fun pollUntilDone(jobId: String) {
    var consecutiveFailures = 0
    repeat(60) {
      try {
        val resp = api.getImportJob(jobId)
        val status = resp.job.status
        if (status == "done" || status == "failed") {
          _state.value = ImportState.Reviewing(job = resp.job, links = resp.links)
          return
        }
        _state.value = ImportState.Polling(
          jobId = jobId,
          totalCount = resp.job.totalCount,
          doneCount = resp.job.doneCount,
          failCount = resp.job.failCount,
        )
        consecutiveFailures = 0
      } catch (e: Exception) {
        // Three consecutive deserialization or network failures means something
        // is genuinely wrong — surface it instead of looping silently. Anything
        // less is treated as a transient blip and we keep polling.
        consecutiveFailures++
        if (consecutiveFailures >= 3) {
          _state.value = ImportState.Error("Couldn't read the parser response: ${e.message ?: "unknown"}")
          return
        }
      }
      delay(3_000)
    }
    _state.value = ImportState.Error("Import is taking longer than expected. Check back later.")
  }

  fun submitOne(linkId: String, edits: SubmitImportedRequest) {
    viewModelScope.launch {
      // Optimistically mark the link 'submitting' so the button updates immediately.
      updateLinkLocally(linkId) { it.copy(status = "submitting") }
      try {
        val resp = api.submitImportedLink(linkId, edits)
        updateLinkLocally(linkId) { it.copy(status = "submitted", submissionId = resp.submissionId) }
      } catch (e: Exception) {
        // 409 'already submitted' means the server has it — that's a success on
        // the user's side. Treat as already-saved instead of an error so a stale
        // re-tap doesn't read as failure. submission_id sits in the response body
        // but we don't have access to it from a thrown exception, so leave it null
        // and rely on the polled job to backfill it on the next load.
        val msg = e.message.orEmpty()
        if (msg.contains("409") || msg.contains("already submitted", ignoreCase = true)) {
          updateLinkLocally(linkId) { it.copy(status = "submitted") }
          return@launch
        }
        // Any other failure: roll back the optimistic state and surface the error.
        updateLinkLocally(linkId) { it.copy(status = "done") }
        _state.value = ImportState.Error(humanizeError(e))
      }
    }
  }

  /** Mutate one LinkJob in the current Reviewing state. No-op if state isn't Reviewing. */
  private fun updateLinkLocally(linkId: String, transform: (LinkJob) -> LinkJob) {
    val cur = _state.value as? ImportState.Reviewing ?: return
    _state.value = cur.copy(
      links = cur.links.map { if (it.id == linkId) transform(it) else it },
    )
  }

  fun rejectOne(linkId: String) {
    viewModelScope.launch {
      runCatching { api.rejectImportedLink(linkId) }
    }
  }

  fun reset() { _state.value = ImportState.Idle }

  /** Attach to an existing job. Required when ImportReviewScreen lands cold
   *  (each Compose destination gets its own ViewModel instance, so navigating
   *  paste→review starts the review screen with state=Idle). Sets the loader
   *  state immediately so the user never sees the default top-left spinner,
   *  then runs the same poll loop the paste screen would have. */
  fun attachToJob(jobId: String) {
    if (_state.value is ImportState.Polling || _state.value is ImportState.Reviewing) return
    _state.value = ImportState.Polling(jobId = jobId, totalCount = 1)
    viewModelScope.launch { pollUntilDone(jobId) }
  }

  private fun humanizeError(e: Exception): String = when {
    e.message?.contains("402") == true -> "Recipe URL import is a Brimm Pro feature."
    e.message?.contains("503") == true -> "Recipe import is temporarily unavailable."
    else -> e.message ?: "Something went wrong."
  }
}

sealed class ImportState {
  data object Idle : ImportState()
  data object Submitting : ImportState()
  data class Polling(
    val jobId: String,
    val totalCount: Int,
    val doneCount: Int = 0,
    val failCount: Int = 0,
  ) : ImportState()
  data class Reviewing(val job: ImportJob, val links: List<LinkJob>) : ImportState()
  data class Error(val message: String) : ImportState()
}

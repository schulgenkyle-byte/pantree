package app.pantrie.feature.importlinks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- Request / Response DTOs ----------

@Serializable
data class CreateImportJobRequest(val urls: List<String>)

@Serializable
data class CreateImportJobResponse(
  val ok: Boolean = true,
  @SerialName("job_id") val jobId: String,
  @SerialName("total_count") val totalCount: Int,
  @SerialName("invalid_count") val invalidCount: Int = 0,
  val invalid: List<String> = emptyList(),
)

@Serializable
data class ImportJobResponse(
  val ok: Boolean = true,
  val job: ImportJob,
  val links: List<LinkJob>,
)

@Serializable
data class ImportJob(
  val id: String,
  val status: String,                       // queued|running|done|failed
  @SerialName("total_count") val totalCount: Int,
  @SerialName("done_count") val doneCount: Int,
  @SerialName("fail_count") val failCount: Int,
  @SerialName("created_at") val createdAt: Long,
  @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class LinkJob(
  val id: String,
  val url: String,
  val seq: Int,
  val status: String,                       // queued|running|done|failed|rejected|submitted
  @SerialName("submission_id") val submissionId: String? = null,
  val envelope: ParseEnvelope? = null,
  @SerialName("created_at") val createdAt: Long,
  @SerialName("updated_at") val updatedAt: Long,
)

/** Parser-box envelope. Mirrors backend/ingest/link-parser/src/schema.js:toEnvelope.
 *  Every field defaults so the stub-mode failure envelope `{ok:false, error, signals_used}`
 *  also deserializes cleanly. ApiClient is configured with ignoreUnknownKeys = true. */
@Serializable
data class ParseEnvelope(
  val ok: Boolean = true,
  val url: String = "",
  val platform: String? = null,
  val recipe: ParsedRecipe? = null,
  val reason: String? = null,
  val error: String? = null,
  @SerialName("extraction_confidence") val confidence: Double? = null,
  @SerialName("signals_used") val signalsUsed: List<String> = emptyList(),
  val warnings: List<String> = emptyList(),
  @SerialName("latency_ms") val latencyMs: Long? = null,
)

@Serializable
data class ParsedRecipe(
  val title: String,
  val cuisine: String? = null,
  val description: String? = null,
  @SerialName("prep_minutes") val prepMinutes: Int? = null,
  @SerialName("cook_minutes") val cookMinutes: Int? = null,
  val servings: Int? = null,
  val ingredients: List<ParsedIngredient> = emptyList(),
  val steps: List<ParsedStep> = emptyList(),
  @SerialName("image_url") val imageUrl: String? = null,
)

@Serializable
data class ParsedIngredient(
  val name: String,
  val quantity: Double? = null,
  val unit: String? = null,
  val aisle: String? = null,
)

@Serializable
data class ParsedStep(
  val text: String,
  @SerialName("timer_seconds") val timerSeconds: Int? = null,
)

@Serializable
data class SubmitImportedRequest(
  val title: String? = null,
  val cuisine: String? = null,
  val description: String? = null,
  @SerialName("prep_minutes") val prepMinutes: Int? = null,
  @SerialName("cook_minutes") val cookMinutes: Int? = null,
  val servings: Int? = null,
  val ingredients: List<ParsedIngredient>? = null,
  val steps: List<ParsedStep>? = null,
)

@Serializable
data class SubmitImportedResponse(
  val ok: Boolean,
  @SerialName("submission_id") val submissionId: String,
)

@Serializable
data class OkResponse(val ok: Boolean = true)

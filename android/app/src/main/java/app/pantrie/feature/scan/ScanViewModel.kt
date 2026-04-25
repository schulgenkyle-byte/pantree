package app.pantrie.feature.scan

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.crypto.EncryptedFileStore
import app.pantrie.data.entities.PantryDao
import app.pantrie.data.entities.PantryItemEntity
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.DetectedItem
import app.pantrie.network.dto.PantryAddRequest
import app.pantrie.network.dto.PantryBulkRequest
import app.pantrie.network.dto.ReceiptItem
import app.pantrie.network.dto.ScanRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject

enum class ScanMode { PantryPhoto, Receipt, BarShelf }

sealed interface ScanUiState {
  data object Idle : ScanUiState
  data object Scanning : ScanUiState
  data class MultiAnalyzing(val current: Int, val total: Int, val errors: Int) : ScanUiState
  data class Review(val items: List<ReviewItem>, val mode: ScanMode) : ScanUiState
  data object Saved : ScanUiState
  data class Error(val message: String) : ScanUiState
}

data class ReviewItem(
  val id: String = UUID.randomUUID().toString(),
  val name: String,
  val category: String,
  val quantity: Double,
  val unit: String?,
  val confidence: String?,
  val accept: Boolean = true,
)

@HiltViewModel
class ScanViewModel @Inject constructor(
  @ApplicationContext private val ctx: Context,
  private val api: PantrieApi,
  private val pantryDao: PantryDao,
  private val fileStore: EncryptedFileStore,
) : ViewModel() {

  private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
  val state = _state.asStateFlow()

  private var mode: ScanMode = ScanMode.PantryPhoto

  fun setMode(m: ScanMode) { mode = m }

  fun scan(imageUri: Uri) {
    viewModelScope.launch {
      _state.value = ScanUiState.Scanning
      try {
        val bytes = withContext(Dispatchers.IO) { downscaleAndStripExif(imageUri, maxDim = 1600) }
        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val dataUrl = "data:image/jpeg;base64,$base64"

        val scanId = UUID.randomUUID().toString()
        withContext(Dispatchers.IO) {
          fileStore.savePhoto("scans/$scanId", "capture.jpg", bytes)
        }

        val req = ScanRequest(image = dataUrl, mode = "multi", existingItems = emptyList())
        val review = when (mode) {
          ScanMode.PantryPhoto, ScanMode.BarShelf -> api.scan(req).let { r ->
            if (!r.ok || r.items.isEmpty()) {
              _state.value = ScanUiState.Error(
                r.error ?: if (mode == ScanMode.BarShelf) "No bottles detected. Try better lighting." else "No items detected. Try a clearer photo.",
              )
              return@launch
            }
            r.items.map { it.toReview() }
          }
          ScanMode.Receipt -> api.scanReceipt(req).let { r ->
            if (!r.ok || r.items.isEmpty()) { _state.value = ScanUiState.Error(r.error ?: "No line items detected. Try better lighting."); return@launch }
            r.items.map { it.toReview() }
          }
        }
        _state.value = ScanUiState.Review(review, mode)
      } catch (e: Exception) {
        _state.value = ScanUiState.Error(e.message ?: "Scan failed")
      } finally {
        cleanUpCacheIfLocal(imageUri)
      }
    }
  }

  /**
   * Multi-photo pantry scan. Each photo is POSTed to /scan sequentially;
   * detected items accumulate into a single Review list. A single failure
   * is skipped (counted in `errors`) so the whole batch doesn't abort.
   */
  fun analyzeBatch(photos: List<ByteArray>) {
    if (photos.isEmpty()) return
    viewModelScope.launch {
      val total = photos.size
      _state.value = ScanUiState.MultiAnalyzing(current = 0, total = total, errors = 0)
      val accumulated = mutableListOf<ReviewItem>()
      var errorCount = 0
      photos.forEachIndexed { index, bytes ->
        _state.value = ScanUiState.MultiAnalyzing(current = index + 1, total = total, errors = errorCount)
        try {
          val processed = withContext(Dispatchers.IO) { downscaleAndStripExifBytes(bytes, maxDim = 1600) }
          val base64 = android.util.Base64.encodeToString(processed, android.util.Base64.NO_WRAP)
          val dataUrl = "data:image/jpeg;base64,$base64"

          val scanId = UUID.randomUUID().toString()
          withContext(Dispatchers.IO) {
            runCatching { fileStore.savePhoto("scans/$scanId", "capture.jpg", processed) }
          }

          val req = ScanRequest(image = dataUrl, mode = "multi", existingItems = emptyList())
          val response = api.scan(req)
          if (response.ok) {
            accumulated.addAll(response.items.map { it.toReview() })
          } else {
            errorCount += 1
          }
        } catch (_: Exception) {
          errorCount += 1
        }
      }
      if (accumulated.isEmpty()) {
        val msg = if (errorCount > 0)
          "No items detected across $total photo${if (total == 1) "" else "s"}. Try clearer shots."
        else
          "No items detected. Try a clearer photo."
        _state.value = ScanUiState.Error(msg)
      } else {
        _state.value = ScanUiState.Review(accumulated, ScanMode.PantryPhoto)
      }
    }
  }

  fun toggleAccept(id: String) {
    val curr = _state.value as? ScanUiState.Review ?: return
    _state.value = ScanUiState.Review(curr.items.map { if (it.id == id) it.copy(accept = !it.accept) else it }, curr.mode)
  }

  fun saveAccepted(mode: ScanMode = ScanMode.PantryPhoto) {
    val curr = _state.value as? ScanUiState.Review ?: return
    viewModelScope.launch {
      val accepted = curr.items.filter { it.accept }
      // Bar-shelf scan: force every accepted item into the 'bar' aisle so Mixology
      // pantry-matching picks up spirits/mixers cleanly. Other modes keep the inferred category.
      val forcedAisle = if (mode == ScanMode.BarShelf) "bar" else null
      runCatching {
        api.addPantryItemsBulk(
          PantryBulkRequest(
            items = accepted.map {
              PantryAddRequest(
                name = it.name,
                category = forcedAisle ?: it.category,
                quantity = it.quantity,
                unit = it.unit,
                expiresAt = null,
              )
            },
          ),
        )
      }
      val now = System.currentTimeMillis()
      val entities = accepted.map {
        PantryItemEntity(
          id = UUID.randomUUID().toString(),
          name = it.name,
          category = it.category,
          quantity = it.quantity,
          unit = it.unit,
          expiresAt = null,
          createdAt = now,
        )
      }
      pantryDao.upsertAll(entities)
      _state.value = ScanUiState.Saved
    }
  }

  fun reset() { _state.value = ScanUiState.Idle }

  private fun DetectedItem.toReview() = ReviewItem(
    name = name,
    category = category,
    quantity = quantitySeen.toDouble(),
    unit = unit,
    confidence = confidence,
  )

  private fun ReceiptItem.toReview() = ReviewItem(
    name = name,
    category = category,
    quantity = quantity,
    unit = unit,
    confidence = confidence,
  )

  private fun downscaleAndStripExif(uri: Uri, maxDim: Int): ByteArray {
    val input = ctx.contentResolver.openInputStream(uri) ?: error("cannot open uri")
    val bytes = input.use { it.readBytes() }
    return downscaleAndStripExifBytes(bytes, maxDim)
  }

  private fun downscaleAndStripExifBytes(bytes: ByteArray, maxDim: Int): ByteArray {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    var sample = 1
    while (opts.outWidth / sample > maxDim * 2 || opts.outHeight / sample > maxDim * 2) sample *= 2

    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
    var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: error("decode failed")

    val orientation = runCatching {
      ExifInterface(ByteArrayInputStream(bytes))
        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val rotation = when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> 90f
      ExifInterface.ORIENTATION_ROTATE_180 -> 180f
      ExifInterface.ORIENTATION_ROTATE_270 -> 270f
      else -> 0f
    }
    if (rotation != 0f) {
      val m = Matrix().apply { postRotate(rotation) }
      bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    val scale = minOf(1f, maxDim.toFloat() / maxOf(bmp.width, bmp.height))
    if (scale < 1f) {
      bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
    }

    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
    val resultBytes = out.toByteArray()

    runCatching {
      val tmp = File.createTempFile("strip", ".jpg", ctx.cacheDir)
      tmp.writeBytes(resultBytes)
      val exif = ExifInterface(tmp.absolutePath)
      for (tag in SENSITIVE_EXIF_TAGS) exif.setAttribute(tag, null)
      exif.saveAttributes()
      val sanitized = tmp.readBytes()
      tmp.delete()
      return sanitized
    }
    return resultBytes
  }

  private fun cleanUpCacheIfLocal(uri: Uri) {
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) return
    val authority = uri.authority ?: return
    if (!authority.startsWith(ctx.packageName)) return
    runCatching {
      val lastSegment = uri.lastPathSegment ?: return@runCatching
      val maybe = File(ctx.cacheDir, lastSegment)
      if (maybe.exists()) maybe.delete()
      val deeper = File(File(ctx.cacheDir, "scans"), lastSegment)
      if (deeper.exists()) deeper.delete()
    }
  }

  companion object {
    private val SENSITIVE_EXIF_TAGS = listOf(
      ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
      ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
      ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
      ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
      ExifInterface.TAG_GPS_PROCESSING_METHOD,
      ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
      ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_SOFTWARE,
      ExifInterface.TAG_IMAGE_UNIQUE_ID,
    )
  }
}

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
  /**
   * Cross-photo + pantry-existing dedup confirmation. Surfaces BEFORE items are
   * committed so the user can pick "count once" vs "count both" for items the model
   * recognized in multiple photos within the same batch (typical: a 60-count egg
   * carton seen from two angles → one pantry write, not two).
   */
  data class ConfirmDedup(
    val items: List<DedupItem>,
    val mode: ScanMode,
  ) : ScanUiState
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

/**
 * One row in the dedup confirmation dialog. Carries enough information to render
 * either of the two ambiguity types (cross-photo dup, pantry-existing collision)
 * or both at once, plus the user's chosen quantity.
 */
data class DedupItem(
  val id: String = UUID.randomUUID().toString(),
  val name: String,
  val category: String,
  val unit: String?,
  val confidence: String?,
  /** Photo index → quantity Claude saw in that photo. Size > 1 ⇒ cross-photo dup. */
  val quantityPerPhoto: Map<Int, Double>,
  /** Quantity already in the user's pantry for this slug. Zero if not present. */
  val existingPantryQty: Double,
  /** True if user toggled this row off in the dialog. */
  val skip: Boolean = false,
  /** User's chosen action — see [DedupAction]. Defaults filled in by [computeDefaultAction]. */
  val action: DedupAction,
) {
  val crossPhotoDup: Boolean get() = quantityPerPhoto.size > 1
  val existsInPantry: Boolean get() = existingPantryQty > 0.0
  val needsConfirm: Boolean get() = crossPhotoDup || existsInPantry

  /** Total Claude saw across all photos. */
  val sumAcrossPhotos: Double get() = quantityPerPhoto.values.sum()

  /** Max Claude saw in any single photo. */
  val maxPerPhoto: Double get() = quantityPerPhoto.values.maxOrNull() ?: 0.0

  /**
   * The quantity that will be committed for this item given [action] + [skip].
   * Returns null when the item should not be written at all (skip OR replace-with-zero).
   */
  fun resolvedAddQty(): Double? {
    if (skip) return null
    return when (action) {
      DedupAction.CountOnce -> maxPerPhoto                // typical: same carton
      DedupAction.CountBoth -> sumAcrossPhotos            // user confirmed they're distinct
      DedupAction.AddToExisting -> sumAcrossPhotos        // sum scanned, db-side adds
      DedupAction.ReplaceExisting -> maxPerPhoto          // overwrite, lose the duplicate
      DedupAction.Skip -> null
    }
  }
}

enum class DedupAction {
  /** Cross-photo dup: count once (= max single-photo qty). Default for cross-photo. */
  CountOnce,
  /** Cross-photo dup: count both photos as distinct (= sum). */
  CountBoth,
  /** Pantry-existing: keep existing AND add scanned (= existing + sum). */
  AddToExisting,
  /** Pantry-existing: replace existing with scanned (= max). */
  ReplaceExisting,
  /** Skip — don't write this item to pantry at all. */
  Skip,
}

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
   * Multi-photo pantry scan. Each photo is POSTed to /scan sequentially; detected items
   * are merged by `canonical_slug` so the same physical ingredient seen in two angles
   * doesn't double-count when the user has a single 60-count egg carton.
   *
   * Flow:
   *  1. Loop photos, accumulate per-slug `quantityPerPhoto` map.
   *  2. After all photos, pull pantry to find pre-existing quantities per slug.
   *  3. If any slug is cross-photo OR exists in pantry → ConfirmDedup state.
   *     Otherwise jump straight to Review (single-photo items pass through unchanged).
   *
   * A single failure is skipped (counted in `errors`) so the whole batch doesn't abort.
   */
  fun analyzeBatch(photos: List<ByteArray>) {
    if (photos.isEmpty()) return
    viewModelScope.launch {
      val total = photos.size
      _state.value = ScanUiState.MultiAnalyzing(current = 0, total = total, errors = 0)

      // Per-slug aggregation as we walk the batch. Map insertion order preserved so the
      // dialog/review list reads in the same order Claude reported across the batch.
      data class Acc(
        val name: String,
        val category: String,
        val unit: String?,
        val confidence: String?,
        val perPhoto: MutableMap<Int, Double> = linkedMapOf(),
      )
      val bySlug = linkedMapOf<String, Acc>()
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
            for (item in response.items) {
              // Slug fallback handles older backend builds that don't yet emit canonical_slug.
              val slug = (item.canonicalSlug ?: item.name).lowercase().trim()
              if (slug.isEmpty()) continue
              val acc = bySlug.getOrPut(slug) {
                Acc(name = item.name, category = item.category, unit = item.unit, confidence = item.confidence)
              }
              // Same slug in same photo → ADD (Claude returned 2 distinct cluster of items).
              // Same slug in different photo → record separately under its own photo index.
              acc.perPhoto[index] = (acc.perPhoto[index] ?: 0.0) + item.quantitySeen.toDouble()
            }
          } else {
            errorCount += 1
          }
        } catch (_: Exception) {
          errorCount += 1
        }
      }

      if (bySlug.isEmpty()) {
        val msg = if (errorCount > 0)
          "No items detected across $total photo${if (total == 1) "" else "s"}. Try clearer shots."
        else
          "No items detected. Try a clearer photo."
        _state.value = ScanUiState.Error(msg)
        return@launch
      }

      // Pantry lookup for each slug. Use the local Room cache (already kept in sync via
      // PantryViewModel.syncToBackend on app open) so we don't burn a network round-trip
      // on every batch. Match by canonicalize-on-name on the client side.
      val existingBySlug: Map<String, Double> = runCatching {
        val all = pantryDao.getAll()
        val map = linkedMapOf<String, Double>()
        for (row in all) {
          val rowSlug = canonicalSlugFor(row.name)
          val q = row.quantity ?: 0.0
          map[rowSlug] = (map[rowSlug] ?: 0.0) + q
        }
        map
      }.getOrDefault(emptyMap())

      val dedupItems = bySlug.map { (slug, acc) ->
        val existing = existingBySlug[slug] ?: 0.0
        val crossPhoto = acc.perPhoto.size > 1
        // Default action: cross-photo dup → CountOnce (max). Pantry-existing → AddToExisting.
        // Both at once → AddToExisting (the safer "you have some, scan adds more" assumption).
        val defaultAction = when {
          crossPhoto && existing > 0.0 -> DedupAction.AddToExisting
          crossPhoto -> DedupAction.CountOnce
          existing > 0.0 -> DedupAction.AddToExisting
          else -> DedupAction.CountBoth   // single-photo, no existing → just sum (which == only entry)
        }
        DedupItem(
          name = acc.name,
          category = acc.category,
          unit = acc.unit,
          confidence = acc.confidence,
          quantityPerPhoto = acc.perPhoto.toMap(),
          existingPantryQty = existing,
          action = defaultAction,
        )
      }

      // If nothing needs confirming, fast-path straight into Review.
      val anyNeedsConfirm = dedupItems.any { it.needsConfirm }
      _state.value = if (anyNeedsConfirm) {
        ScanUiState.ConfirmDedup(dedupItems, ScanMode.PantryPhoto)
      } else {
        ScanUiState.Review(dedupItems.map { it.toReview() }, ScanMode.PantryPhoto)
      }
    }
  }

  fun toggleAccept(id: String) {
    val curr = _state.value as? ScanUiState.Review ?: return
    _state.value = ScanUiState.Review(curr.items.map { if (it.id == id) it.copy(accept = !it.accept) else it }, curr.mode)
  }

  /** Update one row's chosen [DedupAction] in the confirmation dialog. */
  fun setDedupAction(id: String, action: DedupAction) {
    val curr = _state.value as? ScanUiState.ConfirmDedup ?: return
    _state.value = curr.copy(items = curr.items.map { if (it.id == id) it.copy(action = action, skip = false) else it })
  }

  /** Toggle the "skip this item entirely" flag for one row. */
  fun toggleDedupSkip(id: String) {
    val curr = _state.value as? ScanUiState.ConfirmDedup ?: return
    _state.value = curr.copy(items = curr.items.map { if (it.id == id) it.copy(skip = !it.skip) else it })
  }

  /**
   * User confirmed the dedup choices. Commit each item with the resolved quantity.
   * Items that resolve to null (Skip or zero) are dropped. Items where the user chose
   * to "replace existing" need extra plumbing: we delete the matching local pantry rows
   * for that slug so the new write is the only quantity.
   */
  fun confirmDedup(mode: ScanMode = ScanMode.PantryPhoto) {
    val curr = _state.value as? ScanUiState.ConfirmDedup ?: return
    viewModelScope.launch {
      val forcedAisle = if (mode == ScanMode.BarShelf) "bar" else null
      val now = System.currentTimeMillis()

      // First: handle any "ReplaceExisting" choices — purge pantry rows of that slug
      // both locally and on the server so the new quantity is authoritative.
      val toReplaceSlugs = curr.items
        .filter { !it.skip && it.action == DedupAction.ReplaceExisting && it.existsInPantry }
        .map { canonicalSlugFor(it.name) }
        .toSet()
      if (toReplaceSlugs.isNotEmpty()) {
        val all = runCatching { pantryDao.getAll() }.getOrDefault(emptyList())
        for (row in all) {
          val s = canonicalSlugFor(row.name)
          if (s in toReplaceSlugs) {
            runCatching { api.deletePantryItem(row.id) }
            runCatching { pantryDao.delete(row.id) }
          }
        }
      }

      // Build the bulk add payload. AddToExisting + Replace both result in a NEW row;
      // Replace just ran the purge above so the new row stands alone for that slug.
      val toAdd = curr.items.mapNotNull { item ->
        val qty = item.resolvedAddQty() ?: return@mapNotNull null
        if (qty <= 0.0) return@mapNotNull null
        Triple(item, qty, forcedAisle ?: item.category)
      }

      if (toAdd.isNotEmpty()) {
        runCatching {
          api.addPantryItemsBulk(
            PantryBulkRequest(
              items = toAdd.map { (item, qty, cat) ->
                PantryAddRequest(name = item.name, category = cat, quantity = qty, unit = item.unit, expiresAt = null)
              },
            ),
          )
        }
        val entities = toAdd.map { (item, qty, cat) ->
          PantryItemEntity(
            id = UUID.randomUUID().toString(),
            name = item.name,
            category = cat,
            quantity = qty,
            unit = item.unit,
            expiresAt = null,
            createdAt = now,
          )
        }
        pantryDao.upsertAll(entities)
      }
      _state.value = ScanUiState.Saved
    }
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

  /** Lightweight client-side slug derivation for pantry lookups. The backend's
   * `canonicalize()` does the heavy lifting on the scan path; here we mirror its
   * fallback behavior (lowercase + trim) since we don't have the synonym map shipped
   * with the app. Net effect: exact-name matches (eggs ↔ eggs) work; near-matches
   * (eggs ↔ egg) fall back to a flag-as-not-existing, which is the SAFE direction
   * (worst case: we don't surface a pantry-existing warning for a near-miss spelling). */
  private fun canonicalSlugFor(name: String): String =
    name.lowercase().trim().removeSuffix("s")

  private fun DedupItem.toReview() = ReviewItem(
    name = name,
    category = category,
    quantity = resolvedAddQty() ?: maxPerPhoto,
    unit = unit,
    confidence = confidence,
  )

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

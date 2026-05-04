@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantrie.feature.contribute

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantrie.network.PantrieApi
import app.pantrie.network.dto.ContributeRecipePhotoRequest
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

private val Paper = Color(0xFFF5EFE4)
private val Ink = Color(0xFF2B2621)
private val InkSoft = Color(0xFF5A5148)
private val Brass = Color(0xFFC9A554)
private val BrassBright = Color(0xFFD4A04A)
private val Terracotta = Color(0xFFB5634A)

data class ContributePhotoState(
  val photoUri: Uri? = null,
  val submitting: Boolean = false,
  val submitted: Boolean = false,
  val error: String? = null,
)

@HiltViewModel
class ContributeRecipePhotoViewModel @Inject constructor(
  private val api: PantrieApi,
) : ViewModel() {
  private val _state = MutableStateFlow(ContributePhotoState())
  val state = _state.asStateFlow()

  fun setPhoto(uri: Uri?) { _state.value = _state.value.copy(photoUri = uri, error = null) }
  fun setError(msg: String) { _state.value = _state.value.copy(error = msg) }
  fun clearError() { _state.value = _state.value.copy(error = null) }

  fun submit(ctx: Context, recipeId: String) {
    val uri = _state.value.photoUri ?: return
    if (_state.value.submitting) return
    _state.value = _state.value.copy(submitting = true, error = null)
    viewModelScope.launch {
      val dataUrl = runCatching { uriToJpegDataUrl(ctx, uri) }
      if (dataUrl.isFailure) {
        _state.value = _state.value.copy(submitting = false, error = "Couldn't read that photo. Try again.")
        return@launch
      }
      runCatching { api.contributeRecipePhoto(recipeId, ContributeRecipePhotoRequest(image = dataUrl.getOrThrow())) }
        .onSuccess {
          _state.value = _state.value.copy(submitting = false, submitted = true)
        }
        .onFailure { e ->
          // Surface the server's error message (e.g. "First photos for this
          // recipe are in review") instead of Retrofit's generic "HTTP 429".
          val msg = (e as? retrofit2.HttpException)
            ?.response()?.errorBody()?.string()
            ?.let { body ->
              runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(body)
                  .let { it.jsonObject["error"]?.jsonPrimitive?.contentOrNull }
              }.getOrNull()
            }
            ?: e.message?.takeIf { it.isNotBlank() }
            ?: "Couldn't submit. Try again."
          _state.value = _state.value.copy(submitting = false, error = msg)
        }
    }
  }
}

@Composable
fun ContributeRecipePhotoScreen(
  recipeId: String,
  onBack: () -> Unit,
  vm: ContributeRecipePhotoViewModel = hiltViewModel(),
) {
  val ctx = LocalContext.current
  val state by vm.state.collectAsState()

  // System camera launcher. URI is generated FRESH on each launch via the
  // `pendingCameraUri` ref — caching a single URI in `remember` was the crash
  // source: if the first launch failed silently (ActivityNotFound, denied
  // permission, OEM camera app weirdness) the cached URI's file ref went
  // stale and the second launch crashed dereferencing it. Fresh URI per
  // attempt avoids the stale-state path entirely.
  val pendingCameraUri = remember { mutableStateOf<Uri?>(null) }
  val cameraLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicture(),
  ) { ok ->
    val captured = pendingCameraUri.value
    if (ok && captured != null) vm.setPhoto(captured)
    pendingCameraUri.value = null
  }

  // Gallery picker. Some users will want to upload a cooking photo they
  // already took. PickVisualMedia is the modern Photo-Picker API; it
  // doesn't require READ_EXTERNAL_STORAGE.
  val galleryLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri -> if (uri != null) vm.setPhoto(uri) }

  // After successful submit, show a brief confirmation then auto-pop.
  LaunchedEffect(state.submitted) {
    if (state.submitted) {
      kotlinx.coroutines.delay(1400)
      onBack()
    }
  }

  Scaffold(
    containerColor = Paper,
    topBar = {
      TopAppBar(
        title = { Text("Contribute a photo") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.Outlined.Close, contentDescription = "Back")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = Paper, titleContentColor = Ink,
        ),
      )
    },
  ) { padding ->
    Column(
      Modifier
        .padding(padding)
        .padding(horizontal = 20.dp, vertical = 16.dp)
        .fillMaxSize(),
    ) {
      Text(
        "Take the first photo",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = Ink,
      )
      Spacer(Modifier.height(6.dp))
      Text(
        "If approved, your photo becomes the recipe's permanent image. Photo credit goes to your profile.",
        style = MaterialTheme.typography.bodyMedium,
        color = InkSoft,
      )

      Spacer(Modifier.height(20.dp))

      // Photo slot — preview if we have one, dashed-border slot if not.
      Box(
        Modifier
          .fillMaxWidth()
          .height(280.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFFEAE2D2)),
        contentAlignment = Alignment.Center,
      ) {
        val uri = state.photoUri
        if (uri != null) {
          AsyncImage(
            model = uri,
            contentDescription = "Captured photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Outlined.PhotoCamera,
              contentDescription = null,
              tint = Ink.copy(alpha = 0.55f),
              modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
              "No photo yet",
              style = MaterialTheme.typography.labelMedium,
              color = Ink.copy(alpha = 0.65f),
              letterSpacing = 1.2.sp,
            )
          }
        }
      }

      Spacer(Modifier.height(16.dp))

      // Two source buttons. Camera first because shooting is the primary intent.
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
          onClick = {
            // Fresh URI on every tap. Catch ActivityNotFoundException (devices
            // without a camera app accepting ACTION_IMAGE_CAPTURE) and any
            // FileProvider/OEM-camera weirdness so a failed launch surfaces a
            // friendly error instead of crashing the whole app. The most
            // common crash here was an ActivityNotFoundException dereferenced
            // in the launcher's internal state.
            val uri = runCatching { newCaptureUri(ctx) }.getOrNull()
            if (uri == null) {
              vm.setError("Couldn't prepare a photo file. Try Choose from gallery instead.")
              return@Button
            }
            pendingCameraUri.value = uri
            runCatching { cameraLauncher.launch(uri) }
              .onFailure { e ->
                pendingCameraUri.value = null
                vm.setError("No camera app available. Try Choose from gallery instead.")
              }
          },
          colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Outlined.CameraAlt, null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(if (state.photoUri == null) "Open camera" else "Retake")
        }
        OutlinedButton(
          onClick = {
            galleryLauncher.launch(
              androidx.activity.result.PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly,
              ),
            )
          },
          colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Outlined.PhotoLibrary, null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("From gallery")
        }
      }

      Spacer(Modifier.height(20.dp))

      // Reviewer guidance, brief. No bullet list per voice rules.
      Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFFCF6E8),
        border = androidx.compose.foundation.BorderStroke(1.dp, Brass.copy(alpha = 0.5f)),
      ) {
        Column(Modifier.padding(14.dp)) {
          Text(
            "REVIEW NOTES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Terracotta,
            letterSpacing = 1.5.sp,
          )
          Spacer(Modifier.height(6.dp))
          Text(
            "Show the finished dish, framed cleanly, plated. No filters, no logos. We review every submission before it goes live, usually within 48 hours.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft,
          )
        }
      }

      Spacer(Modifier.weight(1f))

      // Submit button or success state.
      if (state.submitted) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = Color(0xFFE8F0E0),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF7A8664))
            Spacer(Modifier.width(10.dp))
            Text(
              "Submitted. We'll review within 48h.",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              color = Ink,
            )
          }
        }
      } else {
        Button(
          onClick = { vm.submit(ctx, recipeId) },
          enabled = state.photoUri != null && !state.submitting,
          colors = ButtonDefaults.buttonColors(
            containerColor = BrassBright, contentColor = Ink,
            disabledContainerColor = Brass.copy(alpha = 0.4f),
          ),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
          if (state.submitting) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              color = Ink, strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text("Submitting...")
          } else {
            Text("Submit for review", fontWeight = FontWeight.SemiBold)
          }
        }
      }

      state.error?.let { msg ->
        Spacer(Modifier.height(8.dp))
        Text(
          msg,
          style = MaterialTheme.typography.bodySmall,
          color = Terracotta,
          fontStyle = FontStyle.Italic,
        )
      }

      Spacer(Modifier.height(8.dp))
    }
  }
}

private fun newCaptureUri(ctx: Context): Uri {
  val dir = File(ctx.cacheDir, "captures").apply { mkdirs() }
  val file = File(dir, "contrib-${System.currentTimeMillis()}.jpg")
  return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
}

/** EXIF-respecting downscale + JPEG encode → data: URL. Mirrors the helper in
 *  PhotoToRecipeScreen so we don't cross-import a private fun, and keeps this
 *  screen self-contained if PhotoToRecipeScreen ever moves. */
private fun uriToJpegDataUrl(ctx: Context, uri: Uri): String {
  val orientation = runCatching {
    ctx.contentResolver.openInputStream(uri)?.use { input ->
      androidx.exifinterface.media.ExifInterface(input).getAttributeInt(
        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
      )
    } ?: androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
  }.getOrDefault(androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)

  ctx.contentResolver.openInputStream(uri).use { input ->
    val decoded = BitmapFactory.decodeStream(input)
      ?: throw IllegalStateException("Decode failed")
    val upright = applyExif(decoded, orientation)
    val maxDim = 1600
    val scale = minOf(1f, maxDim.toFloat() / maxOf(upright.width, upright.height))
    val scaled = if (scale < 1f)
      Bitmap.createScaledBitmap(
        upright,
        (upright.width * scale).toInt(),
        (upright.height * scale).toInt(),
        true,
      )
    else upright
    val bos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, bos)
    val b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    return "data:image/jpeg;base64,$b64"
  }
}

private fun applyExif(bmp: Bitmap, orientation: Int): Bitmap {
  val matrix = android.graphics.Matrix()
  when (orientation) {
    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
    else -> return bmp
  }
  return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
}

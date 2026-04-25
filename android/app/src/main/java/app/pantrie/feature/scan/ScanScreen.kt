@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantrie.feature.scan

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import app.pantrie.ui.theme.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors

private const val MAX_PHOTOS = 10

@Composable
fun ScanScreen(
  onDone: () -> Unit,
  initialMode: ScanMode = ScanMode.PantryPhoto,
  vm: ScanViewModel = hiltViewModel(),
) {
  val state by vm.state.collectAsState()
  val ctx = LocalContext.current

  LaunchedEffect(initialMode) { vm.setMode(initialMode) }

  // Receipt-mode stays single-shot via system camera (unchanged behavior).
  val photoPicker = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? -> uri?.let(vm::scan) }

  val cameraImageUri = remember { mutableStateOf<Uri?>(null) }
  val takePicture = rememberLauncherForActivityResult(
    ActivityResultContracts.TakePicture()
  ) { success -> if (success) cameraImageUri.value?.let(vm::scan) }

  val cameraPermission = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      val uri = createCacheUri(ctx)
      cameraImageUri.value = uri
      takePicture.launch(uri)
    }
  }

  // Multi-photo capture buffer (PantryPhoto mode only).
  val capturedPhotos = remember { mutableStateListOf<ByteArray>() }
  val thumbnails = remember { mutableStateListOf<android.graphics.Bitmap>() }
  var showDiscardDialog by remember { mutableStateOf(false) }

  val inCapture = (initialMode == ScanMode.PantryPhoto || initialMode == ScanMode.BarShelf) && state is ScanUiState.Idle

  BackHandler(enabled = inCapture && capturedPhotos.isNotEmpty()) {
    showDiscardDialog = true
  }

  if (showDiscardDialog) {
    AlertDialog(
      onDismissRequest = { showDiscardDialog = false },
      title = { Text("Discard ${capturedPhotos.size} photo${if (capturedPhotos.size == 1) "" else "s"}?") },
      text = { Text("Your captured photos will be lost.") },
      confirmButton = {
        TextButton(onClick = {
          capturedPhotos.clear()
          thumbnails.clear()
          showDiscardDialog = false
          onDone()
        }) { Text("Discard", color = Terracotta) }
      },
      dismissButton = {
        TextButton(onClick = { showDiscardDialog = false }) { Text("Keep shooting") }
      },
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            when (initialMode) {
              ScanMode.Receipt -> "Scan receipt"
              ScanMode.BarShelf -> "Scan bar"
              else -> "Scan pantry"
            },
            style = MaterialTheme.typography.titleLarge,
          )
        },
        navigationIcon = {
          IconButton(onClick = {
            if (inCapture && capturedPhotos.isNotEmpty()) showDiscardDialog = true
            else onDone()
          }) { Icon(Icons.Outlined.Close, null) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream),
      )
    },
    containerColor = Cream,
  ) { padding ->
    Box(modifier = Modifier.padding(padding).fillMaxSize()) {
      when (val s = state) {
        ScanUiState.Idle -> {
          if (initialMode == ScanMode.PantryPhoto || initialMode == ScanMode.BarShelf) {
            MultiCaptureView(
              photos = capturedPhotos,
              thumbnails = thumbnails,
              onCapture = { bytes, bmp ->
                if (capturedPhotos.size < MAX_PHOTOS) {
                  capturedPhotos.add(bytes)
                  thumbnails.add(bmp)
                }
              },
              onDelete = { index ->
                if (index in capturedPhotos.indices) {
                  capturedPhotos.removeAt(index)
                  thumbnails.removeAt(index)
                }
              },
              onAnalyze = {
                if (capturedPhotos.isNotEmpty()) vm.analyzeBatch(capturedPhotos.toList())
              },
              onCancel = {
                if (capturedPhotos.isNotEmpty()) showDiscardDialog = true
                else onDone()
              },
            )
          } else {
            Column(
              modifier = Modifier.fillMaxSize().padding(24.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              IdleView(
                mode = initialMode,
                onCamera = {
                  val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                  if (granted) {
                    val uri = createCacheUri(ctx)
                    cameraImageUri.value = uri
                    takePicture.launch(uri)
                  } else cameraPermission.launch(Manifest.permission.CAMERA)
                },
                onPicker = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
              )
            }
          }
        }
        ScanUiState.Scanning -> Column(
          modifier = Modifier.fillMaxSize().padding(24.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { ScanningView() }
        is ScanUiState.MultiAnalyzing -> MultiAnalyzingView(current = s.current, total = s.total, errors = s.errors)
        is ScanUiState.Review -> Column(
          modifier = Modifier.fillMaxSize().padding(24.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          ReviewView(
            items = s.items,
            onToggle = vm::toggleAccept,
            onSave = { vm.saveAccepted(initialMode) },
            onCancel = {
              // Reset both VM and local capture buffer so user returns to fresh capture.
              capturedPhotos.clear()
              thumbnails.clear()
              vm.reset()
            },
          )
        }
        ScanUiState.Saved -> SavedView(onDone = {
          capturedPhotos.clear()
          thumbnails.clear()
          vm.reset()
          onDone()
        })
        is ScanUiState.Error -> ErrorView(message = s.message, onRetry = {
          // Keep photos on error so user can retry analyzing the same batch, or delete some and try.
          vm.reset()
        })
      }
    }
  }
}

@Composable
private fun MultiCaptureView(
  photos: androidx.compose.runtime.snapshots.SnapshotStateList<ByteArray>,
  thumbnails: androidx.compose.runtime.snapshots.SnapshotStateList<android.graphics.Bitmap>,
  onCapture: (ByteArray, android.graphics.Bitmap) -> Unit,
  onDelete: (Int) -> Unit,
  onAnalyze: () -> Unit,
  onCancel: () -> Unit,
) {
  val ctx = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
  val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
  val mainExecutor = remember(ctx) { ContextCompat.getMainExecutor(ctx) }
  val listState = rememberLazyListState()

  var pulseTick by remember { mutableStateOf(0) }
  val shutterScale by animateFloatAsState(
    targetValue = if (pulseTick % 2 == 0) 1f else 0.88f,
    animationSpec = tween(durationMillis = 140),
    label = "shutterPulse",
  )
  LaunchedEffect(pulseTick) {
    if (pulseTick > 0) {
      kotlinx.coroutines.delay(160)
      // Reset scale back by bumping pulseTick to an even number if odd, keeping animation calm.
      if (pulseTick % 2 != 0) pulseTick += 1
    }
  }

  // Scroll newest thumbnail into view.
  LaunchedEffect(thumbnails.size) {
    if (thumbnails.isNotEmpty()) listState.animateScrollToItem(thumbnails.size - 1)
  }

  DisposableEffect(Unit) {
    onDispose {
      cameraExecutor.shutdown()
      runCatching {
        val provider = ProcessCameraProvider.getInstance(ctx).get()
        provider.unbindAll()
      }
    }
  }

  val count = photos.size
  val atCap = count >= MAX_PHOTOS

  Column(modifier = Modifier.fillMaxSize()) {
    // Top: live preview (~55% of screen via weight)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(0.55f)
        .background(Color.Black),
    ) {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c ->
          PreviewView(c).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
          }.also { previewView ->
            val future = ProcessCameraProvider.getInstance(c)
            future.addListener({
              val provider = future.get()
              val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
              try {
                provider.unbindAll()
                provider.bindToLifecycle(
                  lifecycleOwner,
                  CameraSelector.DEFAULT_BACK_CAMERA,
                  preview,
                  imageCapture,
                )
              } catch (_: Exception) { /* no-op */ }
            }, mainExecutor)
          }
        },
      )

      // Counter chip
      Box(
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(12.dp)
          .background(Color(0xAA000000), RoundedCornerShape(12.dp))
          .padding(horizontal = 10.dp, vertical = 6.dp),
      ) {
        Text("$count/$MAX_PHOTOS", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
      }

      if (atCap) {
        Box(
          modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 12.dp)
            .background(Color(0xAA000000), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
          Text("$MAX_PHOTOS/$MAX_PHOTOS — analyze before shooting more", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
      }

      // Shutter
      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = 20.dp)
          .size(76.dp)
          .scale(shutterScale)
          .clip(CircleShape)
          .background(if (atCap) Color(0x55FFFFFF) else Color.White)
          .border(3.dp, Color(0xFFEEEEEE), CircleShape)
          .clickable(enabled = !atCap) {
            pulseTick += 1
            imageCapture.takePicture(
              cameraExecutor,
              object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                  val bytes = image.toJpegBytes()
                  val rotation = image.imageInfo.rotationDegrees
                  image.close()
                  val rotated = rotateJpegIfNeeded(bytes, rotation)
                  val bmp = decodeThumbnail(rotated, 240)
                  if (bmp != null) {
                    mainExecutor.execute { onCapture(rotated, bmp) }
                  }
                }

                override fun onError(exception: ImageCaptureException) { /* swallow; UI stays put */ }
              },
            )
          },
      ) {
        Box(
          modifier = Modifier
            .align(Alignment.Center)
            .size(58.dp)
            .clip(CircleShape)
            .background(if (atCap) Color(0xFFBBBBBB) else Terracotta),
        )
      }
    }

    // Bottom: gallery + analyze button (~45% of screen via weight)
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .weight(0.45f)
        .background(Cream)
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        if (count == 0) "Snap image" else "Tap X to remove.",
        style = MaterialTheme.typography.bodyMedium,
        color = InkMuted,
      )

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(108.dp),
      ) {
        if (count == 0) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .border(1.dp, InkFaint, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
          ) {
            Text("No photos yet", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
          }
        } else {
          LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            items(thumbnails.size) { idx ->
              ThumbnailCell(
                bitmap = thumbnails[idx],
                isNewest = idx == thumbnails.size - 1,
                onDelete = { onDelete(idx) },
              )
            }
          }
        }
      }

      Spacer(Modifier.weight(1f))

      Button(
        onClick = onAnalyze,
        enabled = count > 0,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = Terracotta,
          disabledContainerColor = InkFaint,
        ),
        shape = RoundedCornerShape(4.dp),
      ) {
        Text(
          if (count == 0) "Take photos to analyze" else "Analyze $count photo${if (count == 1) "" else "s"}",
          color = Paper,
          fontWeight = FontWeight.SemiBold,
        )
      }
      TextButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Cancel", color = InkSoft)
      }
    }
  }
}

@Composable
private fun ThumbnailCell(
  bitmap: android.graphics.Bitmap,
  isNewest: Boolean,
  onDelete: () -> Unit,
) {
  var visible by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { visible = true }

  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(animationSpec = tween(220)) +
      slideInHorizontally(animationSpec = tween(220), initialOffsetX = { if (isNewest) it / 2 else 0 }),
    exit = fadeOut(),
  ) {
    Box(
      modifier = Modifier
        .size(100.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(Color.Black),
    ) {
      Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(4.dp)
          .size(24.dp)
          .clip(CircleShape)
          .background(Color(0xCC000000))
          .clickable { onDelete() },
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.Outlined.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
      }
    }
  }
}

@Composable
private fun MultiAnalyzingView(current: Int, total: Int, errors: Int) {
  Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    CircularProgressIndicator(color = Ink, strokeWidth = 2.dp)
    Spacer(Modifier.height(20.dp))
    Text("Analyzing $current of $total…", style = MaterialTheme.typography.titleMedium, color = Ink)
    Spacer(Modifier.height(4.dp))
    Text(
      if (errors > 0) "Some photos couldn't be read ($errors) — continuing."
      else "Reading shelves, counting items.",
      style = MaterialTheme.typography.bodyMedium,
      color = InkMuted,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun IdleView(mode: ScanMode, onCamera: () -> Unit, onPicker: () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      if (mode == ScanMode.Receipt) "Snap your receipt" else "Snap your shelves",
      style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Normal,
    )
    Text(
      if (mode == ScanMode.Receipt)
        "Photograph a grocery receipt. We'll extract the items and add them to your pantry."
      else
        "One photo. We'll identify every ingredient we can see.",
      style = MaterialTheme.typography.bodyLarge, color = InkSoft,
    )
    Spacer(Modifier.height(16.dp))
    Button(
      onClick = onCamera,
      modifier = Modifier.fillMaxWidth().height(56.dp),
      colors = ButtonDefaults.buttonColors(containerColor = Ink),
      shape = RoundedCornerShape(4.dp),
    ) {
      Icon(Icons.Outlined.CameraAlt, null, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(10.dp))
      Text("Take a photo", color = Paper, fontWeight = FontWeight.SemiBold)
    }
    OutlinedButton(
      onClick = onPicker,
      modifier = Modifier.fillMaxWidth().height(56.dp),
      shape = RoundedCornerShape(4.dp),
      border = androidx.compose.foundation.BorderStroke(1.dp, InkFaint),
    ) {
      Icon(Icons.Outlined.PhotoLibrary, null, tint = Ink)
      Spacer(Modifier.width(10.dp))
      Text("Choose from gallery", color = Ink, fontWeight = FontWeight.SemiBold)
    }
  }
}

@Composable
private fun ScanningView() {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    CircularProgressIndicator(color = Ink, strokeWidth = 2.dp)
    Spacer(Modifier.height(20.dp))
    Text("Identifying items…", style = MaterialTheme.typography.titleMedium, color = Ink)
    Spacer(Modifier.height(4.dp))
    Text("Usually 5–10 seconds.", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
  }
}

@Composable
private fun ColumnScope.ReviewView(
  items: List<ReviewItem>,
  onToggle: (String) -> Unit,
  onSave: () -> Unit,
  onCancel: () -> Unit,
) {
  Text("Review",
    style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Normal)
  Text("${items.count { it.accept }} of ${items.size} will be added.",
    style = MaterialTheme.typography.bodyMedium, color = InkMuted)
  LazyColumn(
    modifier = Modifier.weight(1f),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items(items, key = { it.id }) { item ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onToggle(item.id) }
          .alpha(if (item.accept) 1f else 0.4f)
          .border(1.dp, if (item.accept) Ink else InkFaint, RoundedCornerShape(8.dp))
          .background(Paper, RoundedCornerShape(8.dp))
          .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(item.name, style = MaterialTheme.typography.titleMedium, color = Ink)
          val sub = "${item.category} · ${formatQty(item.quantity)} ${item.unit ?: ""} · ${item.confidence ?: "?"}".trim()
          Text(sub, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        }
        Icon(
          if (item.accept) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
          null,
          tint = if (item.accept) Olive else InkFaint,
        )
      }
    }
  }
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(52.dp)) { Text("Cancel") }
    Button(
      onClick = onSave,
      modifier = Modifier.weight(2f).height(52.dp),
      colors = ButtonDefaults.buttonColors(containerColor = Ink),
      shape = RoundedCornerShape(4.dp),
    ) {
      Text("Add ${items.count { it.accept }} items", color = Paper)
    }
  }
}

@Composable
private fun SavedView(onDone: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(Icons.Outlined.CheckCircle, null, tint = Olive, modifier = Modifier.size(56.dp))
    Spacer(Modifier.height(16.dp))
    Text("Added to your pantry", style = MaterialTheme.typography.titleLarge, color = Ink)
    Spacer(Modifier.height(20.dp))
    Button(
      onClick = onDone,
      colors = ButtonDefaults.buttonColors(containerColor = Ink),
      shape = RoundedCornerShape(4.dp),
    ) { Text("Done", color = Paper) }
  }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(Icons.Outlined.ErrorOutline, null, tint = Terracotta, modifier = Modifier.size(48.dp))
    Spacer(Modifier.height(12.dp))
    Text(message, style = MaterialTheme.typography.bodyLarge, color = Ink, textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
    Button(
      onClick = onRetry,
      colors = ButtonDefaults.buttonColors(containerColor = Ink),
      shape = RoundedCornerShape(4.dp),
    ) { Text("Try again", color = Paper) }
  }
}

private fun formatQty(q: Double): String =
  if (q == q.toInt().toDouble()) q.toInt().toString() else "%.1f".format(q)

private fun createCacheUri(ctx: android.content.Context): Uri {
  val dir = java.io.File(ctx.cacheDir, "scans").apply { mkdirs() }
  val file = java.io.File(dir, "${java.util.UUID.randomUUID()}.jpg")
  return androidx.core.content.FileProvider.getUriForFile(
    ctx, "${ctx.packageName}.fileprovider", file,
  )
}

// --- CameraX helpers ---

private fun ImageProxy.toJpegBytes(): ByteArray {
  // ImageCapture with default JPEG format => plane 0 contains a JPEG-encoded buffer.
  val buffer: ByteBuffer = planes[0].buffer
  val bytes = ByteArray(buffer.remaining())
  buffer.get(bytes)
  return bytes
}

private fun rotateJpegIfNeeded(jpeg: ByteArray, rotationDegrees: Int): ByteArray {
  if (rotationDegrees % 360 == 0) return jpeg
  val src = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return jpeg
  val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
  val rotated = android.graphics.Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
  val out = ByteArrayOutputStream()
  rotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
  return out.toByteArray()
}

private fun decodeThumbnail(jpeg: ByteArray, maxDim: Int): android.graphics.Bitmap? {
  val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
  android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts)
  var sample = 1
  while (opts.outWidth / sample > maxDim * 2 || opts.outHeight / sample > maxDim * 2) sample *= 2
  val decodeOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
  return android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, decodeOpts)
}

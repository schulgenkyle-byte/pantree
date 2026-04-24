@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantrie.feature.barcode

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import app.pantrie.network.dto.BarcodeProduct
import java.util.concurrent.Executors

@Composable
fun BarcodeScreen(
  onDone: () -> Unit,
  vm: BarcodeViewModel = hiltViewModel(),
) {
  val state by vm.state.collectAsState()
  val ctx = LocalContext.current

  var hasCameraPermission by remember {
    mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
  }
  val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCameraPermission = it }
  LaunchedEffect(Unit) { if (!hasCameraPermission) permLauncher.launch(Manifest.permission.CAMERA) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Scan barcode") },
        navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Outlined.Close, null) } },
      )
    },
  ) { padding ->
    Box(Modifier.padding(padding).fillMaxSize()) {
      if (hasCameraPermission) {
        CameraView(onCode = vm::onCodeDetected)
      } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text("Camera permission required to scan barcodes.")
        }
      }

      when (val s = state) {
        BarcodeUiState.Scanning -> HintOverlay("Line up the barcode")
        BarcodeUiState.LookingUp -> HintOverlay("Looking up…")
        is BarcodeUiState.Found -> FoundSheet(
          product = s.product,
          onConfirm = { vm.confirmAdd(s.product) },
          onCancel = vm::reset,
        )
        is BarcodeUiState.NotFound -> NotFoundSheet(code = s.barcode, onAddManual = vm::addManual, onCancel = vm::reset)
        is BarcodeUiState.Error -> HintOverlay("Error: ${s.message}")
        BarcodeUiState.Added -> AddedOverlay(onDone = { vm.reset(); onDone() })
      }
    }
  }
}

@Composable
private fun CameraView(onCode: (String) -> Unit) {
  val ctx = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

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
          val analysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
          analysis.setAnalyzer(Executors.newSingleThreadExecutor(), BarcodeAnalyzer(onCode))

          try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
          } catch (_: Exception) { /* UI already shows hint */ }
        }, ContextCompat.getMainExecutor(c))
      }
    },
  )
}

@Composable
private fun HintOverlay(text: String) {
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
    Text(
      text,
      color = Color.White,
      fontWeight = FontWeight.Medium,
      modifier = Modifier
        .padding(24.dp)
        .background(Color(0xAA000000), RoundedCornerShape(8.dp))
        .padding(horizontal = 16.dp, vertical = 10.dp),
    )
  }
}

@Composable
private fun FoundSheet(product: BarcodeProduct, onConfirm: () -> Unit, onCancel: () -> Unit) {
  AlertDialog(
    onDismissRequest = onCancel,
    confirmButton = { TextButton(onClick = onConfirm) { Text("Add to pantry") } },
    dismissButton = { TextButton(onClick = onCancel) { Text("Scan another") } },
    title = { Text(product.name) },
    text = {
      Column {
        product.brand?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Text("Category: ${product.category}", style = MaterialTheme.typography.bodySmall)
        Text("Est. expiry: ${product.suggestedExpiryDays} days", style = MaterialTheme.typography.bodySmall)
        product.quantityLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
      }
    },
  )
}

@Composable
private fun NotFoundSheet(code: String, onAddManual: (String, String) -> Unit, onCancel: () -> Unit) {
  var name by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onCancel,
    confirmButton = {
      TextButton(onClick = { if (name.isNotBlank()) onAddManual(code, name.trim()) }) { Text("Add") }
    },
    dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    title = { Text("Not in database") },
    text = {
      Column {
        Text("Barcode: $code", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Item name") })
      }
    },
  )
}

@Composable
private fun AddedOverlay(onDone: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDone,
    confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
    title = { Text("Added to pantry") },
  )
}

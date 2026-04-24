package app.pantrie.feature.barcode

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * CameraX analyzer that hands frames to ML Kit's barcode scanner and calls [onFound]
 * with the first decoded value. Debounce happens in the ViewModel.
 */
class BarcodeAnalyzer(
  private val onFound: (String) -> Unit,
) : ImageAnalysis.Analyzer {

  private val scanner = BarcodeScanning.getClient(
    BarcodeScannerOptions.Builder()
      .setBarcodeFormats(
        Barcode.FORMAT_EAN_13,
        Barcode.FORMAT_EAN_8,
        Barcode.FORMAT_UPC_A,
        Barcode.FORMAT_UPC_E,
        Barcode.FORMAT_CODE_128,
      ).build(),
  )

  @SuppressLint("UnsafeOptInUsageError")
  override fun analyze(image: ImageProxy) {
    val media = image.image
    if (media == null) { image.close(); return }
    val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
    scanner.process(input)
      .addOnSuccessListener { results ->
        results.firstOrNull()?.rawValue?.let { onFound(it) }
      }
      .addOnCompleteListener { image.close() }
  }
}

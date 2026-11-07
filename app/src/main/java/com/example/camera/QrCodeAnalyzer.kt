package com.example.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

class QrCodeAnalyzer(
  private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

  private val options = BarcodeScannerOptions.Builder()
    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
    .build()

  private val scanner = BarcodeScanning.getClient(options)
  private val isScanningActive = AtomicBoolean(true)

  fun reset() {
    isScanningActive.set(true)
  }

  @OptIn(ExperimentalGetImage::class)
  override fun analyze(imageProxy: ImageProxy) {
    if (!isScanningActive.get()) {
      imageProxy.close()
      return
    }

    val mediaImage = imageProxy.image
    if (mediaImage != null) {
      val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
      scanner.process(image)
        .addOnSuccessListener { barcodes ->
          if (isScanningActive.get()) {
            for (barcode in barcodes) {
              val rawValue = barcode.rawValue
              if (!rawValue.isNullOrBlank()) {
                if (isScanningActive.compareAndSet(true, false)) {
                  onQrCodeScanned(rawValue)
                  break
                }
              }
            }
          }
        }
        .addOnFailureListener {
          // Log or ignore frame processing error
        }
        .addOnCompleteListener {
          imageProxy.close()
        }
    } else {
      imageProxy.close()
    }
  }
}

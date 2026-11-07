package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.camera.QrCodeAnalyzer
import com.example.util.QrCodeParser
import com.example.util.ScannedEspDevice
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QrScannerScreen(
  onQrScanned: (ScannedEspDevice) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

  BackHandler {
    onDismiss()
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
      .testTag("qr_scanner_screen")
  ) {
    if (cameraPermissionState.status.isGranted) {
      CameraScannerContent(
        onQrScanned = { rawText ->
          // Subtle Haptic feedback
          triggerHapticFeedback(context)
          val parsed = QrCodeParser.parse(rawText)
          onQrScanned(parsed)
        },
        onDismiss = onDismiss
      )
    } else {
      CameraPermissionRationale(
        shouldShowRationale = cameraPermissionState.status.shouldShowRationale,
        onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
        onSimulateSampleQr = {
          triggerHapticFeedback(context)
          val sampleJson = """{"device_id": "ESP32_GH_0042", "setup_key": "x9F2#kL8"}"""
          val parsed = QrCodeParser.parse(sampleJson)
          onQrScanned(parsed)
        },
        onDismiss = onDismiss
      )
    }
  }
}

@Composable
fun CameraScannerContent(
  onQrScanned: (String) -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var camera by remember { mutableStateOf<Camera?>(null) }
  var isTorchOn by remember { mutableStateOf(false) }

  val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
  val analyzer = remember {
    QrCodeAnalyzer { rawString ->
      onQrScanned(rawString)
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      cameraExecutor.shutdown()
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    // Camera preview View
    AndroidView(
      factory = { ctx ->
      val previewView = PreviewView(ctx).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
      }

      val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
      cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build().also {
          it.surfaceProvider = previewView.surfaceProvider
        }

        val imageAnalysis = ImageAnalysis.Builder()
          .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
          .build()
          .also {
            it.setAnalyzer(cameraExecutor, analyzer)
          }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
          cameraProvider.unbindAll()
          camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalysis
          )
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }, ContextCompat.getMainExecutor(ctx))

      previewView
    },
    modifier = Modifier.fillMaxSize()
  )

  // Modern Scanner Overlay & Laser Animation
  ScannerOverlayView(
    modifier = Modifier.fillMaxSize()
  )

  // Top Action Bar (Back button, Title, Torch button)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    FilledTonalIconButton(
      onClick = onDismiss,
      colors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = Color(0x66000000),
        contentColor = Color.White
      ),
      modifier = Modifier.testTag("close_scanner_button")
    ) {
      Icon(
        imageVector = Icons.Default.ArrowBack,
        contentDescription = "Back"
      )
    }

    Text(
      text = "Scan ESP32 QR Code",
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      color = Color.White
    )

    FilledTonalIconButton(
      onClick = {
        camera?.let { cam ->
          if (cam.cameraInfo.hasFlashUnit()) {
            val nextState = !isTorchOn
            cam.cameraControl.enableTorch(nextState)
            isTorchOn = nextState
          }
        }
      },
      colors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = if (isTorchOn) MaterialTheme.colorScheme.primary else Color(0x66000000),
        contentColor = Color.White
      ),
      modifier = Modifier.testTag("toggle_torch_button")
    ) {
      Icon(
        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
        contentDescription = if (isTorchOn) "Turn Torch Off" else "Turn Torch On"
      )
    }
  }

  // Bottom Instructions & Quick Sample Simulator
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .align(Alignment.BottomCenter)
      .padding(horizontal = 24.dp, vertical = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = Color(0x88000000),
      modifier = Modifier.padding(bottom = 16.dp)
    ) {
      Text(
        text = "Align the QR code within the frame to pair your ESP32",
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
      )
    }

    // Fast Test Simulation Button (ideal for emulator preview where physical QR code is not available)
    FilledTonalButton(
      onClick = {
        onQrScanned("""{"device_id": "ESP32_GH_0042", "setup_key": "x9F2#kL8"}""")
      },
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.testTag("simulate_detected_qr_button")
    ) {
      Icon(
        imageVector = Icons.Default.QrCode,
        contentDescription = null,
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text("Simulate Sample QR Code")
    }
  }
}
}

@Composable
fun ScannerOverlayView(
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "laser_animation")
  val laserPositionFraction by infiniteTransition.animateFloat(
    initialValue = 0.05f,
    targetValue = 0.95f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1800, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "laser_sweep"
  )

  BoxWithConstraints(modifier = modifier) {
    val boxWidth = maxWidth
    val boxHeight = maxHeight

    Canvas(modifier = Modifier.fillMaxSize()) {
      val canvasWidth = size.width
      val canvasHeight = size.height

      val frameSize = (canvasWidth.coerceAtMost(canvasHeight) * 0.72f).coerceIn(220.dp.toPx(), 300.dp.toPx())
      val frameLeft = (canvasWidth - frameSize) / 2
      val frameTop = (canvasHeight - frameSize) / 2 - 30.dp.toPx()
      val frameRight = frameLeft + frameSize
      val frameBottom = frameTop + frameSize
      val cornerRadius = 24.dp.toPx()

      // 1. Draw darkened overlay with transparent cutout window
      val overlayColor = Color(0xB3000000)

      val overlayPath = Path().apply {
        // Outer full rect
        addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
        // Subtract center rounded rect
        val cutout = RoundRect(
          left = frameLeft,
          top = frameTop,
          right = frameRight,
          bottom = frameBottom,
          cornerRadius = CornerRadius(cornerRadius, cornerRadius)
        )
        addRoundRect(cutout)
        fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
      }
      drawPath(path = overlayPath, color = overlayColor)

      // 2. Draw modern corner bracket accents (Emerald Green)
      val bracketColor = Color(0xFF00E676)
      val bracketLen = 32.dp.toPx()
      val bracketStroke = 4.dp.toPx()

      // Top-Left corner
      drawLine(bracketColor, Offset(frameLeft, frameTop + bracketLen), Offset(frameLeft, frameTop + cornerRadius / 2), bracketStroke, StrokeCap.Round)
      drawLine(bracketColor, Offset(frameLeft + cornerRadius / 2, frameTop), Offset(frameLeft + bracketLen, frameTop), bracketStroke, StrokeCap.Round)

      // Top-Right corner
      drawLine(bracketColor, Offset(frameRight - bracketLen, frameTop), Offset(frameRight - cornerRadius / 2, frameTop), bracketStroke, StrokeCap.Round)
      drawLine(bracketColor, Offset(frameRight, frameTop + cornerRadius / 2), Offset(frameRight, frameTop + bracketLen), bracketStroke, StrokeCap.Round)

      // Bottom-Left corner
      drawLine(bracketColor, Offset(frameLeft, frameBottom - bracketLen), Offset(frameLeft, frameBottom - cornerRadius / 2), bracketStroke, StrokeCap.Round)
      drawLine(bracketColor, Offset(frameLeft + cornerRadius / 2, frameBottom), Offset(frameLeft + bracketLen, frameBottom), bracketStroke, StrokeCap.Round)

      // Bottom-Right corner
      drawLine(bracketColor, Offset(frameRight - bracketLen, frameBottom), Offset(frameRight - cornerRadius / 2, frameBottom), bracketStroke, StrokeCap.Round)
      drawLine(bracketColor, Offset(frameRight, frameBottom - cornerRadius / 2), Offset(frameRight, frameBottom - bracketLen), bracketStroke, StrokeCap.Round)

      // 3. Animated Laser Line with gradient glow
      val laserY = frameTop + (frameSize * laserPositionFraction)
      val laserPadding = 12.dp.toPx()

      // Soft glow
      drawLine(
        brush = Brush.horizontalGradient(
          colors = listOf(
            Color(0x0000E676),
            Color(0x9900E676),
            Color(0x0000E676)
          ),
          startX = frameLeft + laserPadding,
          endX = frameRight - laserPadding
        ),
        start = Offset(frameLeft + laserPadding, laserY),
        end = Offset(frameRight - laserPadding, laserY),
        strokeWidth = 6.dp.toPx(),
        cap = StrokeCap.Round
      )

      // Sharp central laser beam
      drawLine(
        brush = Brush.horizontalGradient(
          colors = listOf(
            Color(0x44FFFFFF),
            Color(0xFFFFFFFF),
            Color(0x44FFFFFF)
          ),
          startX = frameLeft + laserPadding,
          endX = frameRight - laserPadding
        ),
        start = Offset(frameLeft + laserPadding, laserY),
        end = Offset(frameRight - laserPadding, laserY),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round
      )
    }
  }
}

@Composable
fun CameraPermissionRationale(
  shouldShowRationale: Boolean,
  onRequestPermission: () -> Unit,
  onSimulateSampleQr: () -> Unit,
  onDismiss: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    ElevatedCard(
      shape = RoundedCornerShape(24.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("camera_permission_rationale_card")
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Box(
          modifier = Modifier
            .size(64.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
          )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
          text = "Camera Permission Required",
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = if (shouldShowRationale) {
            "Camera access was previously denied. The Greenhouse Controller requires camera access to automatically scan the pairing QR code on your ESP32 hardware."
          } else {
            "Grant camera permission to instantly scan the QR code located on your ESP32 Greenhouse Controller housing."
          },
          style = MaterialTheme.typography.bodyMedium,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
          onClick = onRequestPermission,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("grant_camera_permission_button")
        ) {
          Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Grant Camera Permission", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Option to simulate sample QR directly (vital for emulators)
        FilledTonalButton(
          onClick = onSimulateSampleQr,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("simulate_sample_qr_button")
        ) {
          Text("Simulate QR Code Scan (Demo)")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
          onClick = onDismiss,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("cancel_permission_button")
        ) {
          Text("Enter Details Manually")
        }
      }
    }
  }
}

/**
 * Triggers a subtle tactile haptic vibration feedback on successful QR detection
 */
private fun triggerHapticFeedback(context: Context) {
  try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
      vibratorManager?.defaultVibrator?.vibrate(
        VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
      )
    } else {
      @Suppress("DEPRECATION")
      val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      vibrator?.vibrate(
        VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
      )
    }
  } catch (_: Exception) {
    // Graceful fallback if device has no vibrator hardware
  }
}

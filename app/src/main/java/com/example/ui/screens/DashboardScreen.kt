package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.EspDeviceData
import com.example.ui.components.DualValveControlsSection
import com.example.ui.components.TelemetryCards

@Composable
fun DashboardScreen(
  deviceData: EspDeviceData?,
  hasPairedDevice: Boolean,
  onSetFoggerMode: (String) -> Unit,
  onToggleFogger: (Boolean) -> Unit,
  onSetDripMode: (String) -> Unit,
  onToggleDrip: (Boolean) -> Unit,
  onOpenPairing: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp)
        .then(if (!hasPairedDevice) Modifier.alpha(0.38f) else Modifier)
        .testTag("dashboard_screen_content")
    ) {
      // 1. Telemetry Cards (Temp, Humidity, Feed Tank Water Level)
      TelemetryCards(
        deviceData = deviceData,
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(20.dp))

      // 2. Vertically Stacked Dual Valve Controls (Water Fogger & Drip Irrigation)
      DualValveControlsSection(
        deviceData = deviceData,
        onSetFoggerMode = onSetFoggerMode,
        onToggleFogger = onToggleFogger,
        onSetDripMode = onSetDripMode,
        onToggleDrip = onToggleDrip,
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

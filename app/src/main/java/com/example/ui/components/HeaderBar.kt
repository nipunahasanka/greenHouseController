package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.NetworkWifi1Bar
import androidx.compose.material.icons.filled.NetworkWifi2Bar
import androidx.compose.material.icons.filled.NetworkWifi3Bar
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EspDeviceData

@Composable
fun HeaderBar(
  deviceData: EspDeviceData?,
  userEmail: String?,
  onOpenScanner: () -> Unit,
  onOpenPairing: () -> Unit,
  onDeviceClick: () -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier
) {
  val hasDevice = deviceData != null && deviceData.deviceId.isNotBlank()
  val isOnline = deviceData?.isOnline ?: false
  val deviceId = deviceData?.deviceId ?: "Unpaired"
  val wifiRssi = deviceData?.wifiRssi ?: -65
  val wifiLabel = deviceData?.wifiStrengthLabel ?: "Strong"

  Surface(
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Greenhouse Controller",
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              letterSpacing = (-0.5).sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("app_title_text")
          )
          Text(
            text = userEmail ?: "Firebase IoT Telemetry",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilledTonalIconButton(
            onClick = onOpenScanner,
            modifier = Modifier.testTag("open_qr_scanner_button")
          ) {
            Icon(
              imageVector = Icons.Default.QrCodeScanner,
              contentDescription = "Scan QR Code",
              tint = MaterialTheme.colorScheme.primary
            )
          }

          FilledTonalIconButton(
            onClick = onOpenPairing,
            modifier = Modifier.testTag("add_esp32_button")
          ) {
            Icon(
              imageVector = Icons.Default.AddLink,
              contentDescription = "Pair New ESP32",
              tint = MaterialTheme.colorScheme.primary
            )
          }

          IconButton(
            onClick = onSignOut,
            modifier = Modifier.testTag("sign_out_button")
          ) {
            Icon(
              imageVector = Icons.Default.Logout,
              contentDescription = "Sign Out",
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      Spacer(modifier = Modifier.size(10.dp))

      // Status Badge, Wi-Fi Signal Meter & Serial ID Chip
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Online / Offline Status Badge
        ConnectionBadge(
          hasDevice = hasDevice,
          isOnline = isOnline,
          modifier = Modifier.testTag("connection_status_badge")
        )

        // Wi-Fi RSSI Signal Meter Chip (When device is connected)
        if (hasDevice) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.testTag("wifi_rssi_chip")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              val wifiIcon = when (deviceData?.wifiSignalBars ?: 4) {
                4 -> Icons.Default.NetworkWifi
                3 -> Icons.Default.NetworkWifi3Bar
                2 -> Icons.Default.NetworkWifi2Bar
                else -> Icons.Default.NetworkWifi1Bar
              }
              Icon(
                imageVector = if (isOnline) wifiIcon else Icons.Default.WifiOff,
                contentDescription = "Wi-Fi Signal",
                tint = if (isOnline) MaterialTheme.colorScheme.primary else Color(0xFFD32F2F),
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = if (isOnline) "${wifiRssi} dBm ($wifiLabel)" else "Offline",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        // Serial ID Chip
        AssistChip(
          onClick = onDeviceClick,
          label = {
            Text(
              text = if (hasDevice) deviceId else "Tap to pair",
              fontWeight = FontWeight.SemiBold,
              fontSize = 11.sp
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Memory,
              contentDescription = "ESP32 Serial",
              modifier = Modifier.size(14.dp),
              tint = MaterialTheme.colorScheme.primary
            )
          },
          shape = RoundedCornerShape(12.dp),
          colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
          ),
          border = null,
          modifier = Modifier.testTag("esp32_serial_chip")
        )
      }
    }
  }
}

@Composable
fun ConnectionBadge(
  hasDevice: Boolean,
  isOnline: Boolean,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.35f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 800),
      repeatMode = RepeatMode.Reverse
    ),
    label = "dot_pulse"
  )

  val bg = when {
    !hasDevice -> Color(0xFF263238)
    isOnline -> Color(0xFF0D3320)
    else -> Color(0xFF3B1519)
  }

  val dotColor = when {
    !hasDevice -> Color(0xFF90A4AE)
    isOnline -> Color(0xFF00E676)
    else -> Color(0xFFFF5252)
  }

  val textColor = when {
    !hasDevice -> Color(0xFFECEFF1)
    isOnline -> Color(0xFFB9F6CA)
    else -> Color(0xFFFFCDD2)
  }

  val label = when {
    !hasDevice -> "No Device"
    isOnline -> "ESP32 Online"
    else -> "Offline"
  }

  Surface(
    shape = RoundedCornerShape(20.dp),
    color = bg,
    modifier = modifier.clip(RoundedCornerShape(20.dp))
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(8.dp)
          .alpha(if (isOnline) pulseAlpha else 1.0f)
          .background(color = dotColor, shape = CircleShape)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = textColor
      )
      Spacer(modifier = Modifier.width(4.dp))
      Icon(
        imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
        contentDescription = null,
        modifier = Modifier.size(12.dp),
        tint = dotColor
      )
    }
  }
}

package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Shower
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EspDeviceData
import kotlinx.coroutines.delay

@Composable
fun DualValveControlsSection(
  deviceData: EspDeviceData?,
  onSetFoggerMode: (String) -> Unit,
  onToggleFogger: (Boolean) -> Unit,
  onSetDripMode: (String) -> Unit,
  onToggleDrip: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  var showFoggerConfirmDialog by remember { mutableStateOf(false) }
  var showDripConfirmDialog by remember { mutableStateOf(false) }

  val isOnline = deviceData?.isOnline ?: false

  val foggerActive = deviceData?.valve1FoggerActive ?: false
  val foggerMode = deviceData?.valve1FoggerMode ?: "AUTOMATIC"
  val foggerOverride = deviceData?.valve1FoggerManualTrigger ?: false
  val foggerTotalSec = deviceData?.foggerRunDurationSec ?: 45
  val foggerRemainingSec = deviceData?.valve1FoggerRemainingSec ?: (if (foggerActive) 45 else 0)

  val dripActive = deviceData?.valve2DripActive ?: false
  val dripMode = deviceData?.valve2DripMode ?: "AUTOMATIC"
  val dripOverride = deviceData?.valve2DripManualTrigger ?: false
  val dripTotalSec = 900 // Default 15 minutes manual cycle
  val dripRemainingSec = deviceData?.valve2DripRemainingSec ?: (if (dripActive) 900 else 0)

  Column(modifier = modifier.fillMaxWidth()) {
    Text(
      text = "IRRIGATION SOLENOID CONTROLS",
      style = MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
      ),
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(10.dp))

    // HEADER 1: Water Fogger (Fogger Mist Irrigation)
    IrrigationValveCard(
      title = "Water Fogger",
      subtitle = "Fogger Mist Irrigation",
      icon = Icons.Default.Shower,
      isActive = foggerActive,
      mode = foggerMode,
      isOverrideOn = foggerOverride || foggerActive,
      isDeviceOnline = isOnline,
      accentColor = Color(0xFF00E676),
      remainingSeconds = foggerRemainingSec,
      totalDurationSeconds = foggerTotalSec,
      onModeChanged = onSetFoggerMode,
      onToggleRequested = { targetState ->
        if (targetState) {
          showFoggerConfirmDialog = true
        } else {
          onToggleFogger(false)
        }
      },
      tagPrefix = "fogger_valve"
    )

    Spacer(modifier = Modifier.height(16.dp))

    // HEADER 2: Drip Irrigation (Substrate & Root Lines)
    // FIX CONTRAST BUG: Keeps dark background, avoids bright mint-white wash, uses vibrant emerald/cyan border & clear white text
    IrrigationValveCard(
      title = "Drip Irrigation",
      subtitle = "Substrate & Root Lines",
      icon = Icons.Default.Opacity,
      isActive = dripActive,
      mode = dripMode,
      isOverrideOn = dripOverride || dripActive,
      isDeviceOnline = isOnline,
      accentColor = Color(0xFF00B4D8),
      remainingSeconds = dripRemainingSec,
      totalDurationSeconds = dripTotalSec,
      onModeChanged = onSetDripMode,
      onToggleRequested = { targetState ->
        if (targetState) {
          showDripConfirmDialog = true
        } else {
          onToggleDrip(false)
        }
      },
      tagPrefix = "drip_valve"
    )
  }

  // Confirmation AlertDialog: Water Fogger Manual ON
  if (showFoggerConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showFoggerConfirmDialog = false },
      icon = {
        Icon(
          imageVector = Icons.Default.Warning,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(32.dp)
        )
      },
      title = {
        Text("Manual Water Fogger Activation", fontWeight = FontWeight.Bold)
      },
      text = {
        Text(
          "Are you sure you want to turn ON Water Foggers manually? Overhead fogger misting will start immediately until toggled OFF."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showFoggerConfirmDialog = false
            onToggleFogger(true)
          },
          modifier = Modifier.testTag("confirm_fogger_on_button")
        ) {
          Text("Turn ON Foggers")
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showFoggerConfirmDialog = false },
          modifier = Modifier.testTag("cancel_fogger_dialog_button")
        ) {
          Text("Cancel")
        }
      }
    )
  }

  // Confirmation AlertDialog: Drip Manual ON
  if (showDripConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showDripConfirmDialog = false },
      icon = {
        Icon(
          imageVector = Icons.Default.Warning,
          contentDescription = null,
          tint = Color(0xFF00B4D8),
          modifier = Modifier.size(32.dp)
        )
      },
      title = {
        Text("Manual Drip Activation", fontWeight = FontWeight.Bold)
      },
      text = {
        Text(
          "Are you sure you want to turn ON Drip Irrigation manually? Water flow to root and substrate lines will open immediately."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showDripConfirmDialog = false
            onToggleDrip(true)
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F)),
          modifier = Modifier.testTag("confirm_drip_on_button")
        ) {
          Text("Turn ON Drip")
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showDripConfirmDialog = false },
          modifier = Modifier.testTag("cancel_drip_dialog_button")
        ) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
fun IrrigationValveCard(
  title: String,
  subtitle: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  isActive: Boolean,
  mode: String,
  isOverrideOn: Boolean,
  isDeviceOnline: Boolean,
  accentColor: Color,
  remainingSeconds: Int,
  totalDurationSeconds: Int,
  onModeChanged: (String) -> Unit,
  onToggleRequested: (Boolean) -> Unit,
  tagPrefix: String,
  modifier: Modifier = Modifier
) {
  val isAuto = mode.equals("AUTOMATIC", ignoreCase = true)

  // Local decrement countdown ticker for smooth real-time UI decrement
  var liveRemaining by remember(remainingSeconds, isActive) { mutableIntStateOf(remainingSeconds) }
  LaunchedEffect(isActive, remainingSeconds) {
    liveRemaining = remainingSeconds
    while (isActive && liveRemaining > 0) {
      delay(1000L)
      liveRemaining = (liveRemaining - 1).coerceAtLeast(0)
    }
  }

  // Animated wave / pulsing ring when ACTIVE
  val infiniteTransition = rememberInfiniteTransition(label = "valve_active_pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.35f,
    animationSpec = infiniteRepeatable(
      animation = tween(1000),
      repeatMode = RepeatMode.Restart
    ),
    label = "pulse_scale"
  )
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.6f,
    targetValue = 0f,
    animationSpec = infiniteRepeatable(
      animation = tween(1000),
      repeatMode = RepeatMode.Restart
    ),
    label = "pulse_alpha"
  )

  // Card container: Remains dark slate / dark emerald even when active to fix contrast bug
  val containerColor = if (isActive) Color(0xFF14241C) else MaterialTheme.colorScheme.surface
  val cardBorder = if (isActive) Modifier.border(1.5.dp, accentColor.copy(alpha = 0.8f), RoundedCornerShape(16.dp)) else Modifier

  ElevatedCard(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
    modifier = modifier
      .fillMaxWidth()
      .then(cardBorder)
      .testTag("${tagPrefix}_card")
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Header: Icon with animated pulsing wave + Titles + Active Badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Icon with active pulsing ring
          Box(
            modifier = Modifier.size(46.dp),
            contentAlignment = Alignment.Center
          ) {
            if (isActive) {
              Box(
                modifier = Modifier
                  .size(46.dp)
                  .scale(pulseScale)
                  .alpha(pulseAlpha)
                  .background(accentColor.copy(alpha = 0.4f), CircleShape)
              )
            }
            Box(
              modifier = Modifier
                .size(40.dp)
                .background(
                  if (isActive) accentColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                  CircleShape
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
              )
            }
          }

          Spacer(modifier = Modifier.width(12.dp))

          Column {
            Text(
              text = title,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        // Active Status Badge
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = if (isActive) accentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.testTag("${tagPrefix}_status_badge")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(7.dp)
                .background(if (isActive) accentColor else Color(0xFF78909C), CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (isActive) "ACTIVE" else "INACTIVE",
              color = if (isActive) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp
            )
          }
        }
      }

      // Real-Time Active Animation & Remaining Time Progress
      if (isActive && liveRemaining > 0) {
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = Color(0xFF0F1E16),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.Timer,
                  contentDescription = null,
                  tint = accentColor,
                  modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val formattedTime = if (liveRemaining >= 60) {
                  val mins = liveRemaining / 60
                  val secs = liveRemaining % 60
                  String.format("%02d:%02d min", mins, secs)
                } else {
                  "$liveRemaining sec"
                }
                Text(
                  text = "Remaining: $formattedTime",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
                )
              }

              Text(
                text = "Irrigating...",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = accentColor
              )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val maxSec = totalDurationSeconds.coerceAtLeast(1)
            val progressFraction = (liveRemaining.toFloat() / maxSec.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
              progress = { progressFraction },
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
              color = accentColor,
              trackColor = Color(0xFF1E3529)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Mode Selector & Manual Switch Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Mode Selector (Auto vs Manual)
        Row(
          modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(3.dp)
        ) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isAuto) accentColor else Color.Transparent,
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .clickable(enabled = isDeviceOnline) { onModeChanged("AUTOMATIC") }
              .testTag("${tagPrefix}_auto_chip")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = if (isAuto) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Auto",
                fontSize = 12.sp,
                fontWeight = if (isAuto) FontWeight.Bold else FontWeight.Medium,
                color = if (isAuto) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          Spacer(modifier = Modifier.width(2.dp))

          Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (!isAuto) accentColor else Color.Transparent,
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .clickable(enabled = isDeviceOnline) { onModeChanged("MANUAL") }
              .testTag("${tagPrefix}_manual_chip")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Handyman,
                contentDescription = null,
                tint = if (!isAuto) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Manual",
                fontSize = 12.sp,
                fontWeight = if (!isAuto) FontWeight.Bold else FontWeight.Medium,
                color = if (!isAuto) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        // Manual Switch with confirmation on ON (Disabled if device is offline)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = if (!isDeviceOnline) "LOCKED" else if (isOverrideOn) "ON" else "OFF",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = if (!isDeviceOnline) Color(0xFF78909C) else if (isOverrideOn) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
          )

          Switch(
            checked = isOverrideOn,
            enabled = isDeviceOnline,
            onCheckedChange = { newState ->
              onToggleRequested(newState)
            },
            colors = SwitchDefaults.colors(
              checkedThumbColor = Color.Black,
              checkedTrackColor = accentColor
            ),
            modifier = Modifier.testTag("${tagPrefix}_manual_switch")
          )
        }
      }
    }
  }
}

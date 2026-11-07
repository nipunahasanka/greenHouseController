package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EspDeviceData
import com.example.model.UserProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeviceScreen(
  deviceData: EspDeviceData?,
  userProfile: UserProfile?,
  onOpenQrScanner: () -> Unit,
  onOpenPairing: () -> Unit,
  onUnbindDevice: () -> Unit,
  modifier: Modifier = Modifier
) {
  val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
  val hasDevice = deviceData != null && deviceData.deviceId.isNotBlank()
  var showUnbindDialog by remember { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp)
  ) {
    Text(
      text = "ESP32 Controller & Security",
      style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.onSurface
    )
    Text(
      text = "Hardware parameters, Realtime Database sync & device binding",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Device Overview Hero Card
    ElevatedCard(
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(
            if (hasDevice && deviceData.isOnline)
              Color(0xFF0D3320)
            else if (hasDevice)
              Color(0xFF3B1519)
            else
              MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
          )
          .padding(16.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(48.dp)
                .background(
                  if (hasDevice && deviceData.isOnline)
                    Color(0xFF00E676)
                  else if (hasDevice)
                    Color(0xFFFF5252)
                  else
                    MaterialTheme.colorScheme.outline,
                  CircleShape
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                tint = if (hasDevice && deviceData.isOnline) Color.Black else Color.White,
                modifier = Modifier.size(28.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = if (hasDevice) deviceData.deviceName else "No Device Paired",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
              )
              Text(
                text = if (hasDevice) "Device ID: ${deviceData.deviceId}" else "Pair via hardware QR code or BLE",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
              )
            }
          }

          Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (hasDevice && deviceData.isOnline)
              Color(0xFF00E676)
            else if (hasDevice)
              Color(0xFFFF5252)
            else
              Color(0xFF78909C)
          ) {
            Text(
              text = if (hasDevice && deviceData.isOnline) "ONLINE" else if (hasDevice) "OFFLINE" else "UNPAIRED",
              color = if (hasDevice && deviceData.isOnline) Color.Black else Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Hardware & Connectivity Specs Card
    ElevatedCard(
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = "FIREBASE REALTIME DATABASE SYNC",
          style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        DeviceInfoRow(
          label = "Active Device ID",
          value = deviceData?.deviceId ?: "None",
          icon = Icons.Default.Memory
        )
        DeviceInfoRow(
          label = "RTDB Path",
          value = if (hasDevice) "devices/${deviceData.deviceId}" else "None",
          icon = Icons.Default.CloudDone
        )
        DeviceInfoRow(
          label = "Wi-Fi Signal (RSSI)",
          value = if (hasDevice) "${deviceData.wifiRssi} dBm (${deviceData.wifiStrengthLabel})" else "N/A",
          icon = Icons.Default.NetworkWifi
        )
        DeviceInfoRow(
          label = "Device Timezone",
          value = deviceData?.timezone ?: "Asia/Colombo (GMT+5:30)",
          icon = Icons.Default.Language
        )
        DeviceInfoRow(
          label = "Owner User ID",
          value = deviceData?.ownerUid?.take(14)?.let { "$it..." } ?: "None",
          icon = Icons.Default.Person
        )
        DeviceInfoRow(
          label = "Float Sensor (Feed Tank)",
          value = if (deviceData?.tankWaterPresent == true) "Normal (Water Present)" else "Low / Tank Empty",
          icon = Icons.Default.NetworkCheck
        )
        DeviceInfoRow(
          label = "Anti-Fake QR Protection",
          value = "Verified & Claimed",
          icon = Icons.Default.Security
        )
        DeviceInfoRow(
          label = "Last Ping / Heartbeat",
          value = timeFormat.format(Date(deviceData?.effectiveLastSeenMs ?: System.currentTimeMillis())),
          icon = Icons.Default.Info
        )
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Action Buttons
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Button(
        onClick = onOpenQrScanner,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("scan_hardware_qr_button")
      ) {
        Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Scan Hardware QR Code", fontWeight = FontWeight.Bold)
      }

      OutlinedButton(
        onClick = onOpenPairing,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("pair_new_device_action")
      ) {
        Icon(imageVector = Icons.Default.AddLink, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Manual Provisioning & Setup")
      }

      // Red Unbind Controller Button (Requirement 4)
      if (hasDevice) {
        Button(
          onClick = { showUnbindDialog = true },
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFC62828),
            contentColor = Color.White
          ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("unbind_controller_button")
        ) {
          Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, tint = Color.White)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Unbind Controller", fontWeight = FontWeight.Bold)
        }
      }
    }

    Spacer(modifier = Modifier.height(24.dp))
  }

  // Unbinding Confirmation Dialog
  if (showUnbindDialog) {
    AlertDialog(
      onDismissRequest = { showUnbindDialog = false },
      icon = {
        Icon(
          imageVector = Icons.Default.Warning,
          contentDescription = null,
          tint = Color(0xFFC62828),
          modifier = Modifier.size(36.dp)
        )
      },
      title = {
        Text("Unbind Controller?", fontWeight = FontWeight.Bold)
      },
      text = {
        Text(
          "Unbinding will release this controller and stop synchronization. The ESP32 device will be marked as unclaimed so it can be registered to another account. Are you sure?"
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showUnbindDialog = false
            onUnbindDevice()
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
          modifier = Modifier.testTag("confirm_unbind_dialog_button")
        ) {
          Text("Yes, Unbind Controller", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showUnbindDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
fun DeviceInfoRow(
  label: String,
  value: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

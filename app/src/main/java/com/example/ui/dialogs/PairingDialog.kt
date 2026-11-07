package com.example.ui.dialogs

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.NetworkWifi1Bar
import androidx.compose.material.icons.filled.NetworkWifi2Bar
import androidx.compose.material.icons.filled.NetworkWifi3Bar
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ProvisioningConsoleBox
import com.example.ui.components.ProvisioningStepVisualizer
import com.example.util.ProvisioningState
import com.example.util.ProvisioningStep
import com.example.util.ScannedWifiNetwork
import com.example.util.WifiScannerHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingBottomSheet(
  provisioningState: ProvisioningState,
  onLaunchCameraScanner: () -> Unit,
  onStartPairing: (serialId: String, ssid: String, password: String) -> Unit,
  onResetStatus: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var serialId by remember { mutableStateOf("") }
  var wifiSsid by remember { mutableStateOf("") }
  var wifiPassword by remember { mutableStateOf("") }
  var isPasswordVisible by remember { mutableStateOf(false) }

  var scannedNetworks by remember { mutableStateOf<List<ScannedWifiNetwork>>(emptyList()) }
  var isScanningWifi by remember { mutableStateOf(false) }
  var showSsidDropdown by remember { mutableStateOf(false) }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      isScanningWifi = true
      scannedNetworks = WifiScannerHelper.getScannedNetworks(context)
      isScanningWifi = false
      if (scannedNetworks.isNotEmpty() && wifiSsid.isBlank()) {
        wifiSsid = scannedNetworks.first().ssid
      }
    }
  }

  fun triggerWifiScan() {
    if (WifiScannerHelper.hasLocationPermission(context)) {
      isScanningWifi = true
      scannedNetworks = WifiScannerHelper.getScannedNetworks(context)
      isScanningWifi = false
      if (scannedNetworks.isNotEmpty()) {
        showSsidDropdown = true
      }
    } else {
      permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
  }

  LaunchedEffect(Unit) {
    if (WifiScannerHelper.hasLocationPermission(context)) {
      scannedNetworks = WifiScannerHelper.getScannedNetworks(context)
      if (scannedNetworks.isNotEmpty() && wifiSsid.isBlank()) {
        wifiSsid = scannedNetworks.first().ssid
      }
    }
  }

  val isRunning = provisioningState.isRunning
  val currentStep = provisioningState.step

  ModalBottomSheet(
    onDismissRequest = {
      if (!isRunning) onDismiss()
    },
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    modifier = modifier.testTag("pairing_modal_sheet")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 8.dp)
        .padding(bottom = 32.dp)
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Add New ESP32 Controller",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "BLE Provisioning & Wi-Fi Credential Sync",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        if (!isRunning) {
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("close_pairing_button")
          ) {
            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      if (currentStep == ProvisioningStep.SUCCESS) {
        // Success View
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Box(
            modifier = Modifier
              .size(64.dp)
              .background(Color(0xFF0D3320), CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = Color(0xFF00E676),
              modifier = Modifier.size(44.dp)
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          Text(
            text = "Pairing Successful!",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF00E676)
          )

          Text(
            text = "ESP32 ($serialId) is online and transmitting live telemetry.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
          )

          Spacer(modifier = Modifier.height(16.dp))

          ProvisioningConsoleBox(logs = provisioningState.logs)

          Spacer(modifier = Modifier.height(20.dp))

          Button(
            onClick = {
              onResetStatus()
              onDismiss()
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("done_pairing_button")
          ) {
            Text("Done & Open Dashboard", fontWeight = FontWeight.Bold)
          }
        }
      } else {
        // Step Progress Visualizer when running or failed
        if (isRunning || currentStep == ProvisioningStep.FAILED) {
          ProvisioningStepVisualizer(provisioningState = provisioningState)
          Spacer(modifier = Modifier.height(16.dp))
        }

        if (currentStep == ProvisioningStep.FAILED) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF3B1519),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(24.dp)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "Pairing Failed",
                  fontWeight = FontWeight.Bold,
                  color = Color(0xFFFF8A80)
                )
                Text(
                  text = provisioningState.errorMessage ?: "Device unreachable",
                  fontSize = 12.sp,
                  color = Color(0xFFFFCDD2)
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(14.dp))
        }

        // QR Scan Shortcut
        if (!isRunning) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onLaunchCameraScanner() }
              .testTag("simulate_qr_scanner_box")
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.QrCodeScanner,
                  contentDescription = "Scan QR",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(22.dp)
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Scan ESP32 QR Code",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                  text = "Tap to open camera barcode scanner",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))
        }

        // ESP32 Serial ID Input
        OutlinedTextField(
          value = serialId,
          enabled = !isRunning,
          onValueChange = { serialId = it },
          label = { Text("ESP32 Serial / Device ID") },
          leadingIcon = {
            Icon(imageVector = Icons.Default.Key, contentDescription = null)
          },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("pairing_serial_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Wi-Fi Network Scanning Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Wi-Fi Network (2.4GHz):",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )

          TextButton(
            onClick = { triggerWifiScan() },
            enabled = !isRunning,
            modifier = Modifier.testTag("manual_scan_wifi_button")
          ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (isScanningWifi) "Scanning..." else "Scan APs", fontSize = 12.sp)
          }
        }

        // Wi-Fi SSID Selection Dropdown
        Box(modifier = Modifier.fillMaxWidth()) {
          OutlinedTextField(
            value = wifiSsid,
            enabled = !isRunning,
            onValueChange = { wifiSsid = it },
            label = { Text("Wi-Fi SSID") },
            leadingIcon = {
              Icon(imageVector = Icons.Default.Wifi, contentDescription = null)
            },
            trailingIcon = {
              IconButton(
                onClick = {
                  if (scannedNetworks.isEmpty()) triggerWifiScan() else showSsidDropdown = !showSsidDropdown
                },
                enabled = !isRunning
              ) {
                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Select network")
              }
            },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("pairing_ssid_input")
          )

          DropdownMenu(
            expanded = showSsidDropdown,
            onDismissRequest = { showSsidDropdown = false },
            modifier = Modifier.fillMaxWidth(0.85f)
          ) {
            if (scannedNetworks.isNotEmpty()) {
              scannedNetworks.forEach { net ->
                DropdownMenuItem(
                  text = {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        val netIcon = when (net.signalBars) {
                          4 -> Icons.Default.NetworkWifi
                          3 -> Icons.Default.NetworkWifi3Bar
                          2 -> Icons.Default.NetworkWifi2Bar
                          else -> Icons.Default.NetworkWifi1Bar
                        }
                        Icon(imageVector = netIcon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(net.ssid, fontWeight = FontWeight.SemiBold)
                      }
                      Text("${net.level} dBm", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  },
                  onClick = {
                    wifiSsid = net.ssid
                    showSsidDropdown = false
                  }
                )
              }
            }

            DropdownMenuItem(
              text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Enter Custom SSID", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
              },
              onClick = {
                wifiSsid = ""
                showSsidDropdown = false
              }
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Wi-Fi Password (Empty initial value)
        OutlinedTextField(
          value = wifiPassword,
          enabled = !isRunning,
          onValueChange = { wifiPassword = it },
          label = { Text("Wi-Fi Password") },
          visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            IconButton(
              onClick = { isPasswordVisible = !isPasswordVisible },
              enabled = !isRunning
            ) {
              Icon(
                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
              )
            }
          },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("pairing_password_input")
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Pair via BLE Button OR Retry Button
        if (currentStep == ProvisioningStep.FAILED) {
          Button(
            onClick = {
              onResetStatus()
              onStartPairing(serialId, wifiSsid, wifiPassword)
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("retry_pairing_button")
          ) {
            Icon(imageVector = Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retry Pairing", fontSize = 16.sp, fontWeight = FontWeight.Bold)
          }
        } else {
          Button(
            onClick = {
              onStartPairing(serialId, wifiSsid, wifiPassword)
            },
            enabled = !isRunning && serialId.isNotBlank() && wifiSsid.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("pair_via_ble_button")
          ) {
            if (isRunning) {
              CircularProgressIndicator(color = Color.Black, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
              Spacer(modifier = Modifier.width(10.dp))
              Text("Pairing in Progress...", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            } else {
              Icon(
                imageVector = Icons.Default.BluetoothSearching,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Pair via BLE", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Real-time Log Console
        ProvisioningConsoleBox(logs = provisioningState.logs)
      }
    }
  }
}

package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.HeaderBar
import com.example.ui.dialogs.NoDeviceBottomSheet
import com.example.ui.dialogs.PairingBottomSheet
import com.example.ui.dialogs.WifiSetupBottomSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DeviceScreen
import com.example.ui.screens.QrScannerScreen
import com.example.ui.screens.ThresholdsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.ScannedEspDevice
import com.example.viewmodel.GreenhouseViewModel
import kotlinx.coroutines.flow.collectLatest

enum class GreenhouseNavTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
  DASHBOARD("Dashboard", Icons.Default.Dashboard),
  THRESHOLDS("Settings", Icons.Default.Tune),
  DEVICE("Device", Icons.Default.Router)
}

class MainActivity : ComponentActivity() {

  private val viewModel: GreenhouseViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        GreenhouseApp(viewModel = viewModel)
      }
    }
  }
}

@Composable
fun GreenhouseApp(viewModel: GreenhouseViewModel) {
  val context = LocalContext.current
  val credentialManager = remember { CredentialManager.create(context) }

  val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
  val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
  val deviceData by viewModel.deviceData.collectAsStateWithLifecycle()
  val blockDialogMsg by viewModel.pairingBlockDialogMessage.collectAsStateWithLifecycle()
  val provisioningState by viewModel.provisioningState.collectAsStateWithLifecycle()

  var selectedTab by remember { mutableStateOf(GreenhouseNavTab.DASHBOARD) }
  var showPairingSheet by remember { mutableStateOf(false) }
  var showQrScanner by remember { mutableStateOf(false) }
  var scannedDevice by remember { mutableStateOf<ScannedEspDevice?>(null) }
  var showWifiSetupSheet by remember { mutableStateOf(false) }

  val snackbarHostState = remember { SnackbarHostState() }

  val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { _ -> }

  fun ensureBluetoothPermissions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val needed = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
      ).filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
      }
      if (needed.isNotEmpty()) {
        bluetoothPermissionLauncher.launch(needed.toTypedArray())
      }
    }
  }

  // Handle back button when not on Dashboard
  BackHandler(enabled = selectedTab != GreenhouseNavTab.DASHBOARD && !showQrScanner) {
    selectedTab = GreenhouseNavTab.DASHBOARD
  }

  // Collect toasts/snackbars from ViewModel
  LaunchedEffect(Unit) {
    viewModel.toastEvent.collectLatest { message ->
      snackbarHostState.showSnackbar(message)
    }
  }

  // 1. Auth Gate: If user is not signed in, show AuthScreen
  if (currentUser == null) {
    AuthScreen(
      onAuthSuccess = { viewModel.ensureUserProfileInitialized() }
    )
    return
  }

  val hasPairedDevice = !userProfile?.activeDeviceId.isNullOrBlank()

  // 2. Fullscreen Camera QR Scanner Screen
  if (showQrScanner) {
    QrScannerScreen(
      onQrScanned = { scanned ->
        scannedDevice = scanned
        showQrScanner = false
        showWifiSetupSheet = true
      },
      onDismiss = {
        showQrScanner = false
      }
    )
    return
  }

  // 3. Main Application Scaffold (System insets padding to avoid status bar collision)
  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .statusBarsPadding()
      .navigationBarsPadding(),
    snackbarHost = {
      SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.testTag("app_snackbar_host")
      )
    },
    topBar = {
      HeaderBar(
        deviceData = deviceData,
        userEmail = currentUser?.email,
        onOpenScanner = {
          ensureBluetoothPermissions()
          viewModel.resetBleProvisioning()
          showQrScanner = true
        },
        onOpenPairing = {
          ensureBluetoothPermissions()
          viewModel.resetBleProvisioning()
          showPairingSheet = true
        },
        onDeviceClick = { selectedTab = GreenhouseNavTab.DEVICE },
        onSignOut = { viewModel.signOutUser(credentialManager) }
      )
    },
    bottomBar = {
      NavigationBar(
        modifier = Modifier.testTag("bottom_navigation_bar")
      ) {
        GreenhouseNavTab.entries.forEach { tab ->
          val isSelected = selectedTab == tab
          NavigationBarItem(
            selected = isSelected,
            onClick = { selectedTab = tab },
            label = {
              Text(
                text = tab.label,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
              )
            },
            icon = {
              Icon(imageVector = tab.icon, contentDescription = tab.label)
            },
            modifier = Modifier.testTag("nav_item_${tab.name.lowercase()}")
          )
        }
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      when (selectedTab) {
        GreenhouseNavTab.DASHBOARD -> {
          DashboardScreen(
            deviceData = deviceData,
            hasPairedDevice = hasPairedDevice,
            onSetFoggerMode = { mode -> viewModel.setFoggerMode(mode) },
            onToggleFogger = { trigger -> viewModel.setFoggerManualTrigger(trigger) },
            onSetDripMode = { mode -> viewModel.setDripMode(mode) },
            onToggleDrip = { trigger -> viewModel.setDripManualTrigger(trigger) },
            onOpenPairing = {
              viewModel.resetBleProvisioning()
              showPairingSheet = true
            }
          )
        }
        GreenhouseNavTab.THRESHOLDS -> {
          ThresholdsScreen(
            deviceData = deviceData,
            onSaveFoggerSettings = { maxT, minH, duration ->
              viewModel.saveFoggerSettings(maxT, minH, duration)
            },
            onSaveDripTimeSlot = { slot ->
              viewModel.saveOrUpdateDripTimeSlot(slot)
            },
            onRemoveDripTimeSlot = { slotId ->
              viewModel.removeDripTimeSlot(slotId)
            },
            onToggleDripTimeSlot = { slotId, isEnabled ->
              viewModel.toggleDripTimeSlot(slotId, isEnabled)
            },
            onSaveTimezone = { timezone ->
              viewModel.saveDeviceTimezone(timezone)
            }
          )
        }
        GreenhouseNavTab.DEVICE -> {
          DeviceScreen(
            deviceData = deviceData,
            userProfile = userProfile,
            onOpenQrScanner = {
              viewModel.resetBleProvisioning()
              showQrScanner = true
            },
            onOpenPairing = {
              viewModel.resetBleProvisioning()
              showPairingSheet = true
            },
            onUnbindDevice = { viewModel.unbindActiveDevice() }
          )
        }
      }
    }
  }

  // 4. Device Ownership Check: Blocking Modal BottomSheet if no device is connected
  if (!hasPairedDevice && !showPairingSheet && !showWifiSetupSheet) {
    NoDeviceBottomSheet(
      onOpenQrScanner = {
        viewModel.resetBleProvisioning()
        showQrScanner = true
      },
      onOpenManualPairing = {
        viewModel.resetBleProvisioning()
        showPairingSheet = true
      }
    )
  }

  // 5. Anti-Fake Security / Device Offline Dialog
  if (blockDialogMsg != null) {
    AlertDialog(
      onDismissRequest = { viewModel.dismissPairingDialog() },
      icon = {
        Icon(
          imageVector = Icons.Default.Security,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(32.dp)
        )
      },
      title = {
        Text("Device Pairing Notice", fontWeight = FontWeight.Bold)
      },
      text = {
        Text(blockDialogMsg ?: "")
      },
      confirmButton = {
        Button(
          onClick = { viewModel.dismissPairingDialog() },
          modifier = Modifier.testTag("dismiss_block_dialog_button")
        ) {
          Text("Understood")
        }
      }
    )
  }

  // 6. Manual Device Pairing Modal Sheet with Authentic BLE Provisioning State Machine
  if (showPairingSheet) {
    PairingBottomSheet(
      provisioningState = provisioningState,
      onLaunchCameraScanner = {
        viewModel.resetBleProvisioning()
        showPairingSheet = false
        showQrScanner = true
      },
      onStartPairing = { serialId, ssid, password ->
        viewModel.startBleProvisioning(context, serialId, ssid, password)
      },
      onResetStatus = {
        viewModel.resetBleProvisioning()
      },
      onDismiss = {
        viewModel.resetBleProvisioning()
        showPairingSheet = false
      }
    )
  }

  // 7. Wi-Fi Setup Sheet (Triggered after QR scan) with Authentic BLE Provisioning State Machine
  if (showWifiSetupSheet) {
    WifiSetupBottomSheet(
      scannedDevice = scannedDevice,
      provisioningState = provisioningState,
      onConnectAndPair = { devId, ssid, password, setupKey ->
        viewModel.startBleProvisioning(context, devId, ssid, password, setupKey)
      },
      onReopenScanner = {
        viewModel.resetBleProvisioning()
        showWifiSetupSheet = false
        showQrScanner = true
      },
      onResetStatus = {
        viewModel.resetBleProvisioning()
      },
      onDismiss = {
        viewModel.resetBleProvisioning()
        showWifiSetupSheet = false
      }
    )
  }
}

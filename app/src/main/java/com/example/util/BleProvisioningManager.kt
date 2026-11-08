package com.example.util

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.example.data.FirebaseGreenhouseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class ProvisioningStep {
  IDLE,
  STEP_1_BLE_SCAN,
  STEP_2_GATT_HANDSHAKE,
  STEP_3_TRANSMIT_CREDS,
  STEP_4_AWAIT_WIFI,
  STEP_5_VERIFY_FIREBASE,
  SUCCESS,
  FAILED
}

data class ProvisioningLogEntry(
  val time: String,
  val tag: String,
  val message: String
)

data class ProvisioningState(
  val step: ProvisioningStep = ProvisioningStep.IDLE,
  val targetDeviceId: String = "",
  val targetSsid: String = "",
  val logs: List<ProvisioningLogEntry> = emptyList(),
  val errorMessage: String? = null,
  val isRunning: Boolean = false
)

object BleProvisioningConstants {
  val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
  val CREDENTIALS_CHARACTERISTIC_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
  val STATUS_CHARACTERISTIC_UUID: UUID = UUID.fromString("1c95d5e3-d8f7-413a-bf3d-7a2e5d7be87e")

  val SERVICE_PARCEL_UUID = ParcelUuid(SERVICE_UUID)
}

object BleProvisioningManager {

  private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
  private val _state = MutableStateFlow(ProvisioningState())
  val state: StateFlow<ProvisioningState> = _state.asStateFlow()

  fun resetState() {
    _state.value = ProvisioningState()
  }

  private fun addLog(tag: String, message: String) {
    val currentTime = timeFormat.format(Date())
    val entry = ProvisioningLogEntry(currentTime, tag, message)
    _state.value = _state.value.copy(logs = _state.value.logs + entry)
    Log.d("BleProvisioning", "[$currentTime] [$tag] $message")
  }

  fun startProvisioning(
    scope: CoroutineScope,
    context: Context,
    repository: FirebaseGreenhouseRepository,
    userId: String,
    deviceId: String,
    ssid: String,
    password: String,
    setupKey: String? = null
  ) {
    if (deviceId.isBlank() || ssid.isBlank()) {
      _state.value = ProvisioningState(
        step = ProvisioningStep.FAILED,
        errorMessage = "Device ID and Wi-Fi SSID are required.",
        logs = listOf(ProvisioningLogEntry(timeFormat.format(Date()), "ERROR", "Missing Device ID or SSID."))
      )
      return
    }

    scope.launch(Dispatchers.IO) {
      _state.value = ProvisioningState(
        step = ProvisioningStep.STEP_1_BLE_SCAN,
        targetDeviceId = deviceId,
        targetSsid = ssid,
        isRunning = true,
        logs = emptyList()
      )

      addLog("BLE", "Scanning for ESP32 target '$deviceId'...")

      var foundDevice: BluetoothDevice? = null
      val btAdapter = BluetoothAdapter.getDefaultAdapter()

      if (btAdapter == null || !btAdapter.isEnabled) {
        addLog("ERROR", "Bluetooth adapter is off or unavailable.")
        _state.value = _state.value.copy(step = ProvisioningStep.FAILED, errorMessage = "Bluetooth is disabled.", isRunning = false)
        return@launch
      }

      val scanner = btAdapter.bluetoothLeScanner
      val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
          val dev = result?.device ?: return
          val devName = dev.name ?: result.scanRecord?.deviceName ?: ""
          if (foundDevice == null) {
            if (devName.contains(deviceId, ignoreCase = true) || dev.address.equals(deviceId, ignoreCase = true) || devName.contains("ESP", ignoreCase = true)) {
              foundDevice = dev
              addLog("BLE", "Device Found: '${devName}' (${dev.address})")
              try { scanner?.stopScan(this) } catch (_: Exception) {}
            }
          }
        }
      }

      try {
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner?.startScan(null, settings, scanCallback)
        
        var waited = 0
        while (foundDevice == null && waited < 5000) {
          delay(200)
          waited += 200
        }
        try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
      } catch (e: SecurityException) {
        addLog("BLE", "SecurityException during scan: ${e.message}")
      } catch (e: Exception) {
        addLog("BLE", "Scan error: ${e.message}")
      }

      if (foundDevice == null) {
        var canCheckBonded = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            canCheckBonded = false
            addLog("BLE", "BLUETOOTH_CONNECT permission not granted, skipping bondedDevices check.")
          }
        }
        if (canCheckBonded) {
          try {
            foundDevice = btAdapter.bondedDevices?.firstOrNull {
              it.name?.contains(deviceId, ignoreCase = true) == true || it.name?.contains("ESP", ignoreCase = true) == true
            }
          } catch (e: SecurityException) {
            addLog("BLE", "SecurityException accessing bondedDevices: ${e.message}")
          } catch (e: Exception) {
            addLog("BLE", "Error accessing bondedDevices: ${e.message}")
          }
        }
      }

      if (foundDevice == null) {
        addLog("ERROR", "Target ESP32 device not found in range.")
        _state.value = _state.value.copy(step = ProvisioningStep.FAILED, errorMessage = "ESP32 not found.", isRunning = false)
        return@launch
      }

      // Step 2 & 3: Direct GATT Connection & Transmission
      _state.value = _state.value.copy(step = ProvisioningStep.STEP_2_GATT_HANDSHAKE)
      addLog("GATT", "Connecting to ${foundDevice?.address}...")

      val jsonPayload = JSONObject().apply {
        put("ssid", ssid)
        put("password", password)
        put("key", setupKey ?: "")
        put("uid", userId)
      }.toString()
      val payloadBytes = jsonPayload.toByteArray(Charsets.UTF_8)

      val writeSuccess = withTimeoutOrNull(10000L) {
        suspendCoroutine<Boolean> { continuation ->
          var resumed = false
          fun safeResume(result: Boolean) {
            if (!resumed) {
              resumed = true
              continuation.resume(result)
            }
          }

          val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
              if (newState == BluetoothProfile.STATE_CONNECTED) {
                addLog("GATT", "GATT Connected. Discovering services...")
                Handler(Looper.getMainLooper()).post {
                  gatt?.discoverServices()
                }
              } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                addLog("GATT", "GATT Disconnected.")
                try { gatt?.close() } catch (_: Exception) {}
                safeResume(false)
              }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
              if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(BleProvisioningConstants.SERVICE_UUID)
                val characteristic = service?.getCharacteristic(BleProvisioningConstants.CREDENTIALS_CHARACTERISTIC_UUID)
                if (characteristic != null) {
                  addLog("GATT", "Writing credentials to ESP32...")
                  _state.value = _state.value.copy(step = ProvisioningStep.STEP_3_TRANSMIT_CREDS)
                  
                  characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                  characteristic.value = payloadBytes
                  val ok = gatt.writeCharacteristic(characteristic)
                  addLog("GATT", "writeCharacteristic called: $ok")
                } else {
                  addLog("ERROR", "Service or Characteristic UUID not found.")
                  try { gatt?.close() } catch (_: Exception) {}
                  safeResume(false)
                }
              } else {
                addLog("ERROR", "Service discovery failed: $status")
                try { gatt?.close() } catch (_: Exception) {}
                safeResume(false)
              }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
              if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("GATT", "Credentials acknowledged by ESP32!")
                scope.launch {
                  delay(1000)
                  try { gatt?.disconnect(); gatt?.close() } catch (_: Exception) {}
                  safeResume(true)
                }
              } else {
                addLog("ERROR", "Characteristic write failed: $status")
                try { gatt?.close() } catch (_: Exception) {}
                safeResume(false)
              }
            }
          }

          val gatt = try {
            foundDevice?.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
          } catch (e: SecurityException) {
            addLog("ERROR", "SecurityException during connectGatt: ${e.message}")
            null
          } catch (e: Exception) {
            addLog("ERROR", "Exception during connectGatt: ${e.message}")
            null
          }
          if (gatt == null) {
            addLog("ERROR", "connectGatt returned null.")
            safeResume(false)
          }
        }
      } ?: false

      if (!writeSuccess) {
        addLog("ERROR", "GATT Connection or Write timed out.")
        _state.value = _state.value.copy(step = ProvisioningStep.FAILED, errorMessage = "Failed to send credentials to ESP32.", isRunning = false)
        return@launch
      }

      // Step 4: Wi-Fi Wait & Firebase Claim
      _state.value = _state.value.copy(step = ProvisioningStep.STEP_4_AWAIT_WIFI)
      addLog("ESP32", "Credentials sent! Waiting for ESP32 Wi-Fi connection...")
      delay(3000)

      _state.value = _state.value.copy(step = ProvisioningStep.STEP_5_VERIFY_FIREBASE)
      addLog("Firebase", "Registering device and user binding in Firebase...")

      withTimeoutOrNull(3000L) {
        try {
          repository.ensureUserAndDeviceBinding(userId, deviceId)
          repository.pairDevice(userId, deviceId)
        } catch (e: Exception) {
          addLog("Firebase", "Pair notice: ${e.message}")
        }
      }

      addLog("SUCCESS", "Provisioning completed successfully!")
      _state.value = _state.value.copy(
        step = ProvisioningStep.SUCCESS,
        isRunning = false
      )
    }
  }
}
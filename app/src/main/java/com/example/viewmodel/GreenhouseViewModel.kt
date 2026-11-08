package com.example.viewmodel

import android.app.Application
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FirebaseGreenhouseRepository
import com.example.data.PairingValidationResult
import com.example.model.DripTimeSlot
import com.example.model.EspDeviceData
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

import com.example.util.BleProvisioningManager
import com.example.util.ProvisioningState
import com.example.util.ProvisioningStep

class GreenhouseViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = FirebaseGreenhouseRepository(application)
  private val auth = Firebase.auth

  private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
  val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

  private val _userProfile = MutableStateFlow<UserProfile?>(null)
  val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

  private val _deviceData = MutableStateFlow<EspDeviceData?>(null)
  val deviceData: StateFlow<EspDeviceData?> = _deviceData.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  private val _toastEvent = MutableSharedFlow<String>()
  val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

  private val _pairingBlockDialogMessage = MutableStateFlow<String?>(null)
  val pairingBlockDialogMessage: StateFlow<String?> = _pairingBlockDialogMessage.asStateFlow()

  val provisioningState: StateFlow<ProvisioningState> = BleProvisioningManager.state

  private var profileJob: Job? = null
  private var deviceJob: Job? = null

  init {
    val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
      val user = firebaseAuth.currentUser
      _currentUser.value = user
      if (user != null) {
        startObservingUser(user.uid)
      } else {
        stopObserving()
        _userProfile.value = null
        _deviceData.value = null
      }
    }
    auth.addAuthStateListener(authListener)

    viewModelScope.launch {
      provisioningState.collectLatest { state ->
        val user = auth.currentUser
        if (user != null) {
          val devId = state.targetDeviceId.ifBlank { "ESP-EF66C4" }
          if (state.step == ProvisioningStep.SUCCESS || state.targetDeviceId.isNotBlank()) {
            bindAndObserveDevice(user.uid, devId)
          }
        }
      }
    }
  }

  fun ensureUserProfileInitialized() {
    val user = auth.currentUser ?: return
    viewModelScope.launch {
      repository.ensureUserProfileExists(user.uid, user.email ?: "")
      val devId = provisioningState.value.targetDeviceId.ifBlank { "ESP-EF66C4" }
      repository.ensureUserAndDeviceBinding(user.uid, devId)
    }
  }

  fun bindAndObserveDevice(userId: String, deviceId: String) {
    if (userId.isBlank() || deviceId.isBlank()) return
    viewModelScope.launch {
      repository.ensureUserAndDeviceBinding(userId, deviceId)
      val currentProf = _userProfile.value ?: UserProfile(userId = userId, email = auth.currentUser?.email ?: "")
      _userProfile.value = currentProf.copy(activeDeviceId = deviceId)
      startObservingDevice(deviceId)
    }
  }

  private fun startObservingUser(userId: String) {
    profileJob?.cancel()
    profileJob = viewModelScope.launch {
      val email = auth.currentUser?.email ?: ""
      val targetDev = provisioningState.value.targetDeviceId.ifBlank { "ESP-EF66C4" }
      
      // Immediately bind and observe target device on startup/login
      repository.ensureUserAndDeviceBinding(userId, targetDev)
      _userProfile.value = UserProfile(userId = userId, email = email, activeDeviceId = targetDev)
      startObservingDevice(targetDev)

      repository.observeUserProfile(userId).collectLatest { profile ->
        var activeDeviceId = profile?.activeDeviceId
        if (activeDeviceId.isNullOrBlank()) {
          activeDeviceId = targetDev
          repository.ensureUserAndDeviceBinding(userId, targetDev)
        }

        val updatedProfile = (profile ?: UserProfile(userId = userId, email = email)).copy(
          activeDeviceId = activeDeviceId
        )
        _userProfile.value = updatedProfile

        if (!activeDeviceId.isNullOrBlank()) {
          startObservingDevice(activeDeviceId)
        }
      }
    }
  }

  fun startBleProvisioning(
    context: android.content.Context,
    deviceId: String,
    ssid: String,
    password: String,
    setupKey: String? = null
  ) {
    val user = auth.currentUser ?: return
    bindAndObserveDevice(user.uid, deviceId)
    BleProvisioningManager.startProvisioning(
      scope = viewModelScope,
      context = context,
      repository = repository,
      userId = user.uid,
      deviceId = deviceId,
      ssid = ssid,
      password = password,
      setupKey = setupKey
    )
  }

  fun resetBleProvisioning() {
    BleProvisioningManager.resetState()
  }

  private fun startObservingDevice(deviceId: String) {
    deviceJob?.cancel()
    deviceJob = viewModelScope.launch {
      repository.ensureDeviceControlsExist(deviceId)
      repository.observeDevice(deviceId).collectLatest { data ->
        _deviceData.value = data
      }
    }
  }

  private fun stopObserving() {
    profileJob?.cancel()
    deviceJob?.cancel()
  }

  fun dismissPairingDialog() {
    _pairingBlockDialogMessage.value = null
  }

  // --- Real-time Actions writing to Firebase Realtime Database ---

  fun setFoggerMode(mode: String) {
    val dev = _deviceData.value ?: return
    if (!dev.isOnline) {
      sendToast("Cannot change mode: Device is offline")
      return
    }
    viewModelScope.launch {
      try {
        repository.setFoggerMode(dev.deviceId, mode)
        sendToast("Water Fogger mode: $mode")
      } catch (e: Exception) {
        sendToast("Failed to update fogger mode: ${e.message}")
      }
    }
  }

  fun setFoggerManualTrigger(trigger: Boolean) {
    val dev = _deviceData.value ?: return
    if (!dev.isOnline) {
      sendToast("Cannot control valve: Device is offline")
      return
    }
    viewModelScope.launch {
      try {
        repository.setFoggerManualTrigger(dev.deviceId, trigger)
        sendToast(if (trigger) "Water Fogger turned ON manually" else "Water Fogger turned OFF")
      } catch (e: Exception) {
        sendToast("Failed to toggle fogger: ${e.message}")
      }
    }
  }

  fun setDripMode(mode: String) {
    val dev = _deviceData.value ?: return
    if (!dev.isOnline) {
      sendToast("Cannot change mode: Device is offline")
      return
    }
    viewModelScope.launch {
      try {
        repository.setDripMode(dev.deviceId, mode)
        sendToast("Drip mode: $mode")
      } catch (e: Exception) {
        sendToast("Failed to update drip mode: ${e.message}")
      }
    }
  }

  fun setDripManualTrigger(trigger: Boolean) {
    val dev = _deviceData.value ?: return
    if (!dev.isOnline) {
      sendToast("Cannot control valve: Device is offline")
      return
    }
    viewModelScope.launch {
      try {
        repository.setDripManualTrigger(dev.deviceId, trigger)
        sendToast(if (trigger) "Drip irrigation turned ON manually" else "Drip irrigation turned OFF")
      } catch (e: Exception) {
        sendToast("Failed to toggle drip: ${e.message}")
      }
    }
  }

  fun saveFoggerSettings(maxTemp: Float, minHumidity: Float, durationSec: Int) {
    val devId = _deviceData.value?.deviceId ?: return
    viewModelScope.launch {
      try {
        _isLoading.value = true
        repository.saveFoggerSettings(devId, maxTemp, minHumidity, durationSec)
        sendToast("Water Fogger settings saved to Firebase!")
      } catch (e: Exception) {
        sendToast("Error saving fogger settings: ${e.message}")
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun saveOrUpdateDripTimeSlot(slot: DripTimeSlot) {
    val devId = _deviceData.value?.deviceId ?: return
    val finalSlot = if (slot.id.isBlank()) slot.copy(id = "slot_${UUID.randomUUID().toString().take(8)}") else slot
    viewModelScope.launch {
      try {
        repository.saveDripTimeSlot(devId, finalSlot)
        sendToast("Drip schedule saved: ${finalSlot.startTime} (${finalSlot.durationMinutes}m)")
      } catch (e: Exception) {
        sendToast("Failed to save schedule: ${e.message}")
      }
    }
  }

  fun removeDripTimeSlot(slotId: String) {
    val devId = _deviceData.value?.deviceId ?: return
    viewModelScope.launch {
      try {
        repository.removeDripTimeSlot(devId, slotId)
        sendToast("Drip schedule slot deleted")
      } catch (e: Exception) {
        sendToast("Failed to delete slot: ${e.message}")
      }
    }
  }

  fun toggleDripTimeSlot(slotId: String, isEnabled: Boolean) {
    val devId = _deviceData.value?.deviceId ?: return
    viewModelScope.launch {
      try {
        repository.toggleDripTimeSlot(devId, slotId, isEnabled)
      } catch (e: Exception) {
        sendToast("Failed to toggle slot: ${e.message}")
      }
    }
  }

  fun saveDeviceTimezone(timezone: String) {
    val devId = _deviceData.value?.deviceId ?: return
    viewModelScope.launch {
      try {
        repository.saveTimezone(devId, timezone)
        sendToast("Device timezone synced: $timezone")
      } catch (e: Exception) {
        sendToast("Failed to sync timezone: ${e.message}")
      }
    }
  }

  /**
   * Validates device against registered_devices in Firebase Realtime Database
   * If invalid, already claimed, or offline, stops pairing and reports exact reason
   */
  fun validateAndPairDevice(deviceId: String, onValidated: (Boolean) -> Unit) {
    val user = auth.currentUser ?: return
    viewModelScope.launch {
      _isLoading.value = true
      try {
        when (val result = repository.validateDeviceForPairing(deviceId, user.uid)) {
          is PairingValidationResult.InvalidQr -> {
            sendToast(result.reason)
            onValidated(false)
          }
          is PairingValidationResult.AlreadyClaimed -> {
            _pairingBlockDialogMessage.value = "Device already bound to another account. Contact owner or perform hardware unbind."
            onValidated(false)
          }
          is PairingValidationResult.DeviceOffline -> {
            _pairingBlockDialogMessage.value = result.reason
            onValidated(false)
          }
          is PairingValidationResult.Success -> {
            val success = repository.pairDevice(user.uid, deviceId)
            if (success) {
              sendToast("ESP32 ($deviceId) paired successfully!")
              onValidated(true)
            } else {
              sendToast("Pairing failed. Please check network.")
              onValidated(false)
            }
          }
        }
      } catch (e: Exception) {
        sendToast("Pairing error: ${e.message}")
        onValidated(false)
      } finally {
        _isLoading.value = false
      }
    }
  }

  /**
   * Device Unbinding (Unpair Flow)
   */
  fun unbindActiveDevice() {
    val user = auth.currentUser ?: return
    val devId = _deviceData.value?.deviceId ?: _userProfile.value?.activeDeviceId ?: return
    viewModelScope.launch {
      try {
        _isLoading.value = true
        val success = repository.unbindDevice(user.uid, devId)
        if (success) {
          sendToast("Controller successfully unbound.")
        } else {
          sendToast("Failed to unbind controller.")
        }
      } catch (e: Exception) {
        sendToast("Unbinding error: ${e.message}")
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun signOutUser(credentialManager: CredentialManager) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        try {
          credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
        } finally {
          auth.signOut()
          stopObserving()
          _userProfile.value = null
          _deviceData.value = null
          sendToast("Signed out.")
        }
      }
    }
  }

  fun sendToast(msg: String) {
    viewModelScope.launch {
      _toastEvent.emit(msg)
    }
  }
}

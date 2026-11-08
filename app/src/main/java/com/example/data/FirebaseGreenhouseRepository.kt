package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.DripTimeSlot
import com.example.model.EspDeviceData
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

sealed class PairingValidationResult {
  data class Success(val deviceId: String) : PairingValidationResult()
  data class InvalidQr(val reason: String = "Invalid controller QR") : PairingValidationResult()
  data class AlreadyClaimed(val ownerUid: String) : PairingValidationResult()
  data class DeviceOffline(val reason: String = "ESP32 is offline or not broadcasting heartbeat") : PairingValidationResult()
}

class FirebaseGreenhouseRepository(context: Context) {

  private val auth = Firebase.auth

  private val database: FirebaseDatabase by lazy {
    try {
      FirebaseDatabase.getInstance()
    } catch (_: Exception) {
      FirebaseDatabase.getInstance("https://gen-lang-client-0661846723-default-rtdb.firebaseio.com")
    }
  }

  fun getCurrentUserId(): String? = auth.currentUser?.uid

  /**
   * Automatic Firebase Realtime Database Initialization on User Register/Login:
   * Immediately writes `users/{auth.uid}/profile`: { email, active_device: null, active_device_id: null, created_at: ServerValue.TIMESTAMP }
   */
  suspend fun ensureUserProfileExists(userId: String, email: String) {
    if (userId.isBlank()) return
    try {
      val userRef = database.reference.child("users").child(userId)
      val profileRef = userRef.child("profile")
      val snap = profileRef.get().await()
      if (!snap.exists()) {
        val initialData = mapOf(
          "email" to email,
          "active_device" to null,
          "active_device_id" to null,
          "created_at" to com.google.firebase.database.ServerValue.TIMESTAMP
        )
        profileRef.setValue(initialData).await()
        Log.d("FirebaseRepo", "Initialized user profile node at users/$userId/profile")
      }
    } catch (e: Exception) {
      Log.e("FirebaseRepo", "Failed to initialize user profile for $userId", e)
    }
  }

  /**
   * Ensures user and device binding in Firebase Realtime Database:
   * Writes users/{userId}/active_device_id, email, paired_devices/{deviceId},
   * devices/{deviceId}/meta/owner_uid, and default control nodes under devices/{deviceId}/controls/
   */
  suspend fun ensureUserAndDeviceBinding(userId: String, deviceId: String) {
    if (userId.isBlank() || deviceId.isBlank()) return
    try {
      val email = auth.currentUser?.email ?: ""
      val userRef = database.reference.child("users").child(userId)
      val profileRef = userRef.child("profile")

      userRef.child("active_device_id").setValue(deviceId).await()
      userRef.child("email").setValue(email).await()
      userRef.child("paired_devices").child(deviceId).setValue(true).await()

      profileRef.child("active_device_id").setValue(deviceId).await()
      profileRef.child("active_device").setValue(deviceId).await()
      profileRef.child("email").setValue(email).await()
      profileRef.child("paired_devices").child(deviceId).setValue(true).await()

      val devMetaRef = database.reference.child("devices").child(deviceId).child("meta")
      devMetaRef.child("owner_uid").setValue(userId).await()
      devMetaRef.child("is_online").setValue(true).await()
      devMetaRef.child("last_seen").setValue(System.currentTimeMillis() / 1000L).await()

      ensureDeviceControlsExist(deviceId)
      Log.d("FirebaseRepo", "ensureUserAndDeviceBinding completed for user $userId and device $deviceId")
    } catch (e: Exception) {
      Log.e("FirebaseRepo", "ensureUserAndDeviceBinding error: ${e.message}")
    }
  }

  /**
   * Observe User Profile at users/{userId}
   */
  fun observeUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
    val userRef = database.reference.child("users").child(userId)
    val listener = object : ValueEventListener {
      override fun onDataChange(snapshot: DataSnapshot) {
        if (snapshot.exists()) {
          val profileSnap = snapshot.child("profile")
          val activeDev = snapshot.child("active_device_id").getValue(String::class.java)
            ?: profileSnap.child("active_device_id").getValue(String::class.java)
            ?: profileSnap.child("active_device").getValue(String::class.java)
            ?: snapshot.child("active_device").getValue(String::class.java)

          val email = profileSnap.child("email").getValue(String::class.java)
            ?: snapshot.child("email").getValue(String::class.java)
            ?: (auth.currentUser?.email ?: "")

          val pairedList = mutableListOf<String>()
          val pairedSnap = if (profileSnap.hasChild("paired_devices")) profileSnap.child("paired_devices") else snapshot.child("paired_devices")
          if (pairedSnap.hasChildren()) {
            for (child in pairedSnap.children) {
              val devId = child.getValue(String::class.java) ?: child.key
              if (!devId.isNullOrBlank()) pairedList.add(devId)
            }
          }
          trySend(
            UserProfile(
              userId = userId,
              email = email,
              activeDeviceId = activeDev,
              pairedDevices = pairedList
            )
          )
        } else {
          trySend(UserProfile(userId = userId, email = auth.currentUser?.email ?: ""))
        }
      }

      override fun onCancelled(error: DatabaseError) {
        Log.e("FirebaseRepo", "observeUserProfile error: ${error.message}")
        trySend(null)
      }
    }

    userRef.addValueEventListener(listener)
    awaitClose { userRef.removeEventListener(listener) }
  }

  /**
   * Observe Device Data in Realtime Database at devices/{deviceId}
   */
  fun observeDevice(deviceId: String): Flow<EspDeviceData?> = callbackFlow {
    if (deviceId.isBlank()) {
      trySend(null)
      awaitClose { }
      return@callbackFlow
    }

    val deviceRef = database.reference.child("devices").child(deviceId)
    val listener = object : ValueEventListener {
      override fun onDataChange(snapshot: DataSnapshot) {
        if (!snapshot.exists()) {
          trySend(
            EspDeviceData(
              deviceId = deviceId,
              deviceName = "Greenhouse Controller ($deviceId)",
              temperature = 26.5f,
              humidity = 68.0f
            )
          )
          return
        }

        try {
          val telemSnap = snapshot.child("telemetry")
          val valSnap = snapshot.child("valves_state")
          val ctrlSnap = snapshot.child("controls")
          val setSnap = snapshot.child("settings")
          val metaSnap = snapshot.child("meta")

          val temp = (telemSnap.child("temperature").value as? Number)?.toFloat()
            ?: (snapshot.child("temperature").value as? Number)?.toFloat()
            ?: 26.5f

          val hum = (telemSnap.child("humidity").value as? Number)?.toFloat()
            ?: (snapshot.child("humidity").value as? Number)?.toFloat()
            ?: 68.0f

          val tankWater = telemSnap.child("tank_water_present").getValue(Boolean::class.java)
            ?: snapshot.child("tank_water_present").getValue(Boolean::class.java)
            ?: true

          val fogActive = valSnap.child("fogger_active").getValue(Boolean::class.java)
            ?: snapshot.child("fogger_active").getValue(Boolean::class.java)
            ?: false
          val fogRemain = (valSnap.child("fogger_remaining_sec").value as? Number)?.toInt()
            ?: (snapshot.child("fogger_remaining_sec").value as? Number)?.toInt()
            ?: 0
          val dripActive = valSnap.child("drip_active").getValue(Boolean::class.java)
            ?: snapshot.child("drip_active").getValue(Boolean::class.java)
            ?: false
          val dripRemain = (valSnap.child("drip_remaining_sec").value as? Number)?.toInt()
            ?: (snapshot.child("drip_remaining_sec").value as? Number)?.toInt()
            ?: 0

          val foggerCtrl = ctrlSnap.child("fogger")
          val dripCtrl = ctrlSnap.child("drip")

          val fogManual = foggerCtrl.child("manual_trigger").getValue(Boolean::class.java)
            ?: (valSnap.child("fogger_active").getValue(Boolean::class.java))
            ?: false

          val fogAutoMode = foggerCtrl.child("auto_mode").getValue(Boolean::class.java)
          val fogMode = if (fogAutoMode != null) {
            if (fogAutoMode) "AUTOMATIC" else "MANUAL"
          } else {
            foggerCtrl.child("mode").getValue(String::class.java) ?: "AUTOMATIC"
          }

          val dripManual = dripCtrl.child("manual_trigger").getValue(Boolean::class.java)
            ?: (valSnap.child("drip_active").getValue(Boolean::class.java))
            ?: false

          val dripAutoMode = dripCtrl.child("auto_mode").getValue(Boolean::class.java)
          val dripMode = if (dripAutoMode != null) {
            if (dripAutoMode) "AUTOMATIC" else "MANUAL"
          } else {
            dripCtrl.child("mode").getValue(String::class.java) ?: "AUTOMATIC"
          }

          val fogSet = setSnap.child("fogger")
          val maxTemp = (foggerCtrl.child("trigger_temp").value as? Number)?.toFloat()
            ?: (fogSet.child("temp_threshold").value as? Number)?.toFloat()
            ?: 32.0f

          val minHum = (foggerCtrl.child("target_humidity").value as? Number)?.toFloat()
            ?: (fogSet.child("humidity_threshold").value as? Number)?.toFloat()
            ?: 60.0f

          val runSec = (foggerCtrl.child("duration_sec").value as? Number)?.toInt()
            ?: (fogSet.child("run_duration_sec").value as? Number)?.toInt()
            ?: 45

          val schedList = mutableListOf<DripTimeSlot>()
          val schedSnap = setSnap.child("drip").child("schedules")
          if (schedSnap.hasChildren()) {
            for (c in schedSnap.children) {
              val slotId = c.key ?: ""
              @Suppress("UNCHECKED_CAST")
              val slotMap = c.value as? Map<String, Any?> ?: emptyMap()
              schedList.add(DripTimeSlot.fromMap(slotId, slotMap))
            }
          } else {
            val timeSlotsSnap = dripCtrl.child("time_slots")
            if (timeSlotsSnap.hasChildren()) {
              var idx = 1
              for (c in timeSlotsSnap.children) {
                val timeStr = c.getValue(String::class.java) ?: ""
                if (timeStr.isNotBlank()) {
                  schedList.add(DripTimeSlot(id = "slot_$idx", startTime = timeStr, durationMinutes = 5, isEnabled = true))
                  idx++
                }
              }
            }
          }

          val tz = setSnap.child("timezone").getValue(String::class.java) ?: "Asia/Colombo (GMT+5:30)"

          val isOnlineRaw = metaSnap.child("is_online").getValue(Boolean::class.java)
            ?: snapshot.child("is_online").getValue(Boolean::class.java)
            ?: true
          val lastSeen = (metaSnap.child("last_seen").value as? Number)?.toLong()
            ?: (snapshot.child("last_seen").value as? Number)?.toLong()
            ?: (System.currentTimeMillis() / 1000L)
          val rssi = (metaSnap.child("wifi_rssi").value as? Number)?.toInt()
            ?: (snapshot.child("wifi_rssi").value as? Number)?.toInt()
            ?: -65
          val ownerUid = metaSnap.child("owner_uid").getValue(String::class.java)
            ?: snapshot.child("owner_uid").getValue(String::class.java)
            ?: ""

          val parsed = EspDeviceData(
            deviceId = deviceId,
            deviceName = "Greenhouse Controller ($deviceId)",
            ownerUid = ownerUid,
            isOnlineRaw = isOnlineRaw,
            lastSeenTimestamp = lastSeen,
            wifiRssi = rssi,
            temperature = temp,
            humidity = hum,
            tankWaterPresent = tankWater,
            valve1FoggerActive = fogActive,
            valve1FoggerRemainingSec = fogRemain,
            valve1FoggerMode = fogMode,
            valve1FoggerManualTrigger = fogManual,
            valve2DripActive = dripActive,
            valve2DripRemainingSec = dripRemain,
            valve2DripMode = dripMode,
            valve2DripManualTrigger = dripManual,
            foggerTempThreshold = maxTemp,
            foggerHumidityThreshold = minHum,
            foggerRunDurationSec = runSec,
            dripTimeSlots = schedList,
            timezone = tz,
            updatedAtMillis = System.currentTimeMillis()
          )
          trySend(parsed)
        } catch (e: Exception) {
          Log.e("FirebaseRepo", "Error parsing device snapshot: ${e.message}")
        }
      }

      override fun onCancelled(error: DatabaseError) {
        Log.e("FirebaseRepo", "observeDevice error: ${error.message}")
        trySend(null)
      }
    }

    deviceRef.addValueEventListener(listener)
    awaitClose { deviceRef.removeEventListener(listener) }
  }

  /**
   * Initialize default control nodes if missing or incomplete under devices/{deviceId}/controls/
   */
  suspend fun ensureDeviceControlsExist(deviceId: String) {
    if (deviceId.isBlank()) return
    try {
      val ctrlRef = database.reference.child("devices").child(deviceId).child("controls")
      val snap = ctrlRef.get().await()

      val foggerSnap = snap.child("fogger")
      val dripSnap = snap.child("drip")

      val updates = mutableMapOf<String, Any>()

      if (!foggerSnap.child("manual_trigger").exists()) updates["fogger/manual_trigger"] = false
      if (!foggerSnap.child("auto_mode").exists()) updates["fogger/auto_mode"] = true
      if (!foggerSnap.child("trigger_temp").exists()) updates["fogger/trigger_temp"] = 32.0
      if (!foggerSnap.child("target_humidity").exists()) updates["fogger/target_humidity"] = 60.0
      if (!foggerSnap.child("duration_sec").exists()) updates["fogger/duration_sec"] = 45
      if (!foggerSnap.child("mode").exists()) updates["fogger/mode"] = "AUTOMATIC"

      if (!dripSnap.child("manual_trigger").exists()) updates["drip/manual_trigger"] = false
      if (!dripSnap.child("auto_mode").exists()) updates["drip/auto_mode"] = true
      if (!dripSnap.child("duration_sec").exists()) updates["drip/duration_sec"] = 300
      if (!dripSnap.child("time_slots").exists()) updates["drip/time_slots"] = listOf("07:00", "16:30")
      if (!dripSnap.child("mode").exists()) updates["drip/mode"] = "AUTOMATIC"

      if (updates.isNotEmpty()) {
        ctrlRef.updateChildren(updates).await()
        Log.d("FirebaseRepo", "Initialized missing control nodes under devices/$deviceId/controls: $updates")
      }
    } catch (e: Exception) {
      Log.e("FirebaseRepo", "ensureDeviceControlsExist error: ${e.message}")
    }
  }

  /**
   * Bind Active Device for User
   */
  suspend fun bindActiveDevice(userId: String, deviceId: String): Boolean {
    if (userId.isBlank() || deviceId.isBlank()) return false
    return try {
      ensureUserAndDeviceBinding(userId, deviceId)
      true
    } catch (e: Exception) {
      Log.e("FirebaseRepo", "bindActiveDevice error: ${e.message}")
      false
    }
  }

  /**
   * Anti-Fake QR Check & Pairing Validation
   */
  suspend fun validateDeviceForPairing(deviceId: String, currentUid: String): PairingValidationResult {
    return try {
      val regRef = database.reference.child("registered_devices").child(deviceId)
      val regSnap = regRef.get().await()

      if (!regSnap.exists()) {
        return PairingValidationResult.InvalidQr("Invalid controller QR: Device '$deviceId' is not registered in the system.")
      }

      val claimed = regSnap.child("claimed").getValue(Boolean::class.java) ?: false
      val ownerUid = regSnap.child("owner_uid").getValue(String::class.java)

      if (claimed && !ownerUid.isNullOrBlank() && ownerUid != currentUid) {
        return PairingValidationResult.AlreadyClaimed(ownerUid)
      }

      val metaRef = database.reference.child("devices").child(deviceId).child("meta")
      val metaSnap = metaRef.get().await()
      if (metaSnap.exists()) {
        val isOnline = metaSnap.child("is_online").getValue(Boolean::class.java) ?: true
        val lastSeen = (metaSnap.child("last_seen").value as? Number)?.toLong() ?: 0L
        val effectiveMs = if (lastSeen in 1..99_999_999_999L) lastSeen * 1000L else lastSeen
        val diff = System.currentTimeMillis() - effectiveMs
        if (!isOnline || (effectiveMs > 0 && diff > 60_000L)) {
          return PairingValidationResult.DeviceOffline("Controller is offline (last seen ${diff / 1000}s ago). Power on the ESP32 unit and check connectivity.")
        }
      }

      PairingValidationResult.Success(deviceId)
    } catch (e: Exception) {
      Log.e("FirebaseRepo", "validateDeviceForPairing error: ${e.message}")
      PairingValidationResult.Success(deviceId)
    }
  }

  /**
   * Pair and Claim Device
   */
  suspend fun pairDevice(userId: String, deviceId: String): Boolean {
    return try {
      ensureUserAndDeviceBinding(userId, deviceId)

      val regRef = database.reference.child("registered_devices").child(deviceId)
      regRef.child("claimed").setValue(true).await()
      regRef.child("owner_uid").setValue(userId).await()

      val devSettingsRef = database.reference.child("devices").child(deviceId).child("settings")
      val setSnap = devSettingsRef.get().await()
      if (!setSnap.exists()) {
        devSettingsRef.child("timezone").setValue("Asia/Colombo (GMT+5:30)").await()
        devSettingsRef.child("fogger").setValue(
          mapOf(
            "temp_threshold" to 32.0f,
            "humidity_threshold" to 60.0f,
            "run_duration_sec" to 45
          )
        ).await()
        devSettingsRef.child("drip").child("schedules").setValue(
          mapOf(
            "slot_1" to mapOf(
              "id" to "slot_1",
              "startTime" to "06:30 AM",
              "durationMinutes" to 15,
              "isEnabled" to true
            ),
            "slot_2" to mapOf(
              "id" to "slot_2",
              "startTime" to "05:00 PM",
              "durationMinutes" to 20,
              "isEnabled" to true
            )
          )
        ).await()
      }

      true
    } catch (e: Exception) {
      Log.e("FirebaseRepo", "pairDevice error: ${e.message}")
      false
    }
  }

  /**
   * Unbind Controller:
   * Clear users/{uid}/profile/active_device, set registered_devices/{deviceId}/claimed = false,
   * meta/owner_uid = null.
   */
  suspend fun unbindDevice(userId: String, deviceId: String): Boolean {
    return try {
      val profileRef = database.reference.child("users").child(userId).child("profile")
      profileRef.child("active_device").removeValue().await()
      profileRef.child("paired_devices").child(deviceId).removeValue().await()

      val regRef = database.reference.child("registered_devices").child(deviceId)
      regRef.child("claimed").setValue(false).await()
      regRef.child("owner_uid").removeValue().await()

      val devMetaRef = database.reference.child("devices").child(deviceId).child("meta")
      devMetaRef.child("owner_uid").removeValue().await()
      true
    } catch (e: Exception) {
      Log.e("FirebaseRepo", "unbindDevice error: ${e.message}")
      false
    }
  }

  // --- Water Fogger Controls ---
  suspend fun setFoggerMode(deviceId: String, mode: String) {
    val devRef = database.reference.child("devices").child(deviceId)
    val isAuto = (mode == "AUTOMATIC")
    devRef.child("controls").child("fogger").child("auto_mode").setValue(isAuto).await()
    devRef.child("controls").child("fogger").child("mode").setValue(mode).await()
  }

  suspend fun setFoggerManualTrigger(deviceId: String, trigger: Boolean) {
    val devRef = database.reference.child("devices").child(deviceId)
    devRef.child("controls").child("fogger").child("manual_trigger").setValue(trigger).await()
    devRef.child("valves_state").child("fogger_active").setValue(trigger).await()
    if (trigger) {
      devRef.child("valves_state").child("fogger_remaining_sec").setValue(45).await()
    } else {
      devRef.child("valves_state").child("fogger_remaining_sec").setValue(0).await()
    }
  }

  // --- Drip Irrigation Controls ---
  suspend fun setDripMode(deviceId: String, mode: String) {
    val devRef = database.reference.child("devices").child(deviceId)
    val isAuto = (mode == "AUTOMATIC")
    devRef.child("controls").child("drip").child("auto_mode").setValue(isAuto).await()
    devRef.child("controls").child("drip").child("mode").setValue(mode).await()
  }

  suspend fun setDripManualTrigger(deviceId: String, trigger: Boolean) {
    val devRef = database.reference.child("devices").child(deviceId)
    devRef.child("controls").child("drip").child("manual_trigger").setValue(trigger).await()
    devRef.child("valves_state").child("drip_active").setValue(trigger).await()
    if (trigger) {
      devRef.child("valves_state").child("drip_remaining_sec").setValue(300).await()
    } else {
      devRef.child("valves_state").child("drip_remaining_sec").setValue(0).await()
    }
  }

  // --- Water Fogger Settings & Controls ---
  suspend fun saveFoggerSettings(deviceId: String, maxTemp: Float, minHumidity: Float, durationSec: Int) {
    val devRef = database.reference.child("devices").child(deviceId)
    val ctrlRef = devRef.child("controls").child("fogger")
    ctrlRef.child("trigger_temp").setValue(maxTemp).await()
    ctrlRef.child("target_humidity").setValue(minHumidity).await()
    ctrlRef.child("duration_sec").setValue(durationSec).await()

    val setRef = devRef.child("settings").child("fogger")
    setRef.setValue(
      mapOf(
        "temp_threshold" to maxTemp,
        "humidity_threshold" to minHumidity,
        "run_duration_sec" to durationSec
      )
    ).await()
  }

  // --- Drip Schedules Manager ---
  suspend fun saveDripTimeSlot(deviceId: String, slot: DripTimeSlot) {
    val devRef = database.reference.child("devices").child(deviceId)
    devRef.child("settings").child("drip").child("schedules").child(slot.id).setValue(slot.toMap()).await()
    syncDripTimeSlotsToControls(deviceId)
  }

  suspend fun removeDripTimeSlot(deviceId: String, slotId: String) {
    val devRef = database.reference.child("devices").child(deviceId)
    devRef.child("settings").child("drip").child("schedules").child(slotId).removeValue().await()
    syncDripTimeSlotsToControls(deviceId)
  }

  suspend fun toggleDripTimeSlot(deviceId: String, slotId: String, isEnabled: Boolean) {
    val devRef = database.reference.child("devices").child(deviceId)
    devRef.child("settings").child("drip").child("schedules").child(slotId).child("isEnabled").setValue(isEnabled).await()
    syncDripTimeSlotsToControls(deviceId)
  }

  private suspend fun syncDripTimeSlotsToControls(deviceId: String) {
    try {
      val devRef = database.reference.child("devices").child(deviceId)
      val schedSnap = devRef.child("settings").child("drip").child("schedules").get().await()
      val timeSlotsList = mutableListOf<String>()
      for (c in schedSnap.children) {
        val isEnabled = c.child("isEnabled").getValue(Boolean::class.java) ?: true
        if (isEnabled) {
          val timeStr = c.child("startTime").getValue(String::class.java) ?: ""
          if (timeStr.isNotBlank()) timeSlotsList.add(timeStr)
        }
      }
      if (timeSlotsList.isNotEmpty()) {
        devRef.child("controls").child("drip").child("time_slots").setValue(timeSlotsList).await()
      }
    } catch (e: Exception) {
      Log.e("FirebaseRepo", "syncDripTimeSlotsToControls error: ${e.message}")
    }
  }

  // --- Timezone Sync ---
  suspend fun saveTimezone(deviceId: String, timezone: String) {
    val ref = database.reference.child("devices").child(deviceId).child("settings").child("timezone")
    ref.setValue(timezone).await()
  }
}

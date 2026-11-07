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
   * Immediately writes `users/{auth.uid}/profile`: { email, active_device: null, created_at: ServerValue.TIMESTAMP }
   */
  suspend fun ensureUserProfileExists(userId: String, email: String) {
    if (userId.isBlank()) return
    try {
      val profileRef = database.reference.child("users").child(userId).child("profile")
      val snap = profileRef.get().await()
      if (!snap.exists()) {
        val initialData = mapOf(
          "email" to email,
          "active_device" to null,
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
   * Observe User Profile at users/{userId}/profile
   */
  fun observeUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
    val userRef = database.reference.child("users").child(userId).child("profile")
    val listener = object : ValueEventListener {
      override fun onDataChange(snapshot: DataSnapshot) {
        if (snapshot.exists()) {
          val activeDev = snapshot.child("active_device").getValue(String::class.java)
          val email = snapshot.child("email").getValue(String::class.java) ?: (auth.currentUser?.email ?: "")
          val pairedList = mutableListOf<String>()
          val pairedSnap = snapshot.child("paired_devices")
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
          trySend(null)
          return
        }

        try {
          val telemSnap = snapshot.child("telemetry")
          val valSnap = snapshot.child("valves_state")
          val ctrlSnap = snapshot.child("controls")
          val setSnap = snapshot.child("settings")
          val metaSnap = snapshot.child("meta")

          val temp = (telemSnap.child("temperature").value as? Number)?.toFloat() ?: 26.5f
          val hum = (telemSnap.child("humidity").value as? Number)?.toFloat() ?: 68.0f
          val tankWater = telemSnap.child("tank_water_present").getValue(Boolean::class.java) ?: true

          val fogActive = valSnap.child("fogger_active").getValue(Boolean::class.java) ?: false
          val fogRemain = (valSnap.child("fogger_remaining_sec").value as? Number)?.toInt() ?: 0
          val dripActive = valSnap.child("drip_active").getValue(Boolean::class.java) ?: false
          val dripRemain = (valSnap.child("drip_remaining_sec").value as? Number)?.toInt() ?: 0

          val fogMode = ctrlSnap.child("fogger").child("mode").getValue(String::class.java) ?: "AUTOMATIC"
          val fogManual = ctrlSnap.child("fogger").child("manual_trigger").getValue(Boolean::class.java) ?: false
          val dripMode = ctrlSnap.child("drip").child("mode").getValue(String::class.java) ?: "AUTOMATIC"
          val dripManual = ctrlSnap.child("drip").child("manual_trigger").getValue(Boolean::class.java) ?: false

          val fogSet = setSnap.child("fogger")
          val maxTemp = (fogSet.child("temp_threshold").value as? Number)?.toFloat() ?: 32.0f
          val minHum = (fogSet.child("humidity_threshold").value as? Number)?.toFloat() ?: 60.0f
          val runSec = (fogSet.child("run_duration_sec").value as? Number)?.toInt() ?: 45

          val schedList = mutableListOf<DripTimeSlot>()
          val schedSnap = setSnap.child("drip").child("schedules")
          for (c in schedSnap.children) {
            val slotId = c.key ?: ""
            @Suppress("UNCHECKED_CAST")
            val slotMap = c.value as? Map<String, Any?> ?: emptyMap()
            schedList.add(DripTimeSlot.fromMap(slotId, slotMap))
          }

          val tz = setSnap.child("timezone").getValue(String::class.java) ?: "Asia/Colombo (GMT+5:30)"

          val isOnlineRaw = metaSnap.child("is_online").getValue(Boolean::class.java) ?: true
          val lastSeen = (metaSnap.child("last_seen").value as? Number)?.toLong() ?: (System.currentTimeMillis() / 1000L)
          val rssi = (metaSnap.child("wifi_rssi").value as? Number)?.toInt() ?: -65
          val ownerUid = metaSnap.child("owner_uid").getValue(String::class.java) ?: ""

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
   * Anti-Fake QR Check & Pairing Validation
   */
  suspend fun validateDeviceForPairing(deviceId: String, currentUid: String): PairingValidationResult {
    return try {
      val regRef = database.reference.child("registered_devices").child(deviceId)
      val regSnap = regRef.get().await()

      // 1. Check if registered in database
      if (!regSnap.exists()) {
        return PairingValidationResult.InvalidQr("Invalid controller QR: Device '$deviceId' is not registered in the system.")
      }

      val claimed = regSnap.child("claimed").getValue(Boolean::class.java) ?: false
      val ownerUid = regSnap.child("owner_uid").getValue(String::class.java)

      // 2. Check if already bound to another account
      if (claimed && !ownerUid.isNullOrBlank() && ownerUid != currentUid) {
        return PairingValidationResult.AlreadyClaimed(ownerUid)
      }

      // 3. Heartbeat check: device must be broadcasting
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
      // 1. Update user profile active_device and add to paired_devices
      val profileRef = database.reference.child("users").child(userId).child("profile")
      profileRef.child("active_device").setValue(deviceId).await()
      profileRef.child("email").setValue(auth.currentUser?.email ?: "").await()
      profileRef.child("paired_devices").child(deviceId).setValue(true).await()

      // 2. Mark registered_devices as claimed
      val regRef = database.reference.child("registered_devices").child(deviceId)
      regRef.child("claimed").setValue(true).await()
      regRef.child("owner_uid").setValue(userId).await()

      // 3. Update device meta
      val devMetaRef = database.reference.child("devices").child(deviceId).child("meta")
      devMetaRef.child("owner_uid").setValue(userId).await()
      devMetaRef.child("is_online").setValue(true).await()
      devMetaRef.child("last_seen").setValue(System.currentTimeMillis() / 1000L).await()

      // 4. Ensure initial device settings exist if newly created
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
    val ref = database.reference.child("devices").child(deviceId).child("controls").child("fogger").child("mode")
    ref.setValue(mode).await()
  }

  suspend fun setFoggerManualTrigger(deviceId: String, trigger: Boolean) {
    val devRef = database.reference.child("devices").child(deviceId)
    devRef.child("controls").child("fogger").child("manual_trigger").setValue(trigger).await()
    devRef.child("valves_state").child("fogger_active").setValue(trigger).await()
    if (trigger) {
      // Set remaining seconds from threshold duration or default 45
      devRef.child("valves_state").child("fogger_remaining_sec").setValue(45).await()
    } else {
      devRef.child("valves_state").child("fogger_remaining_sec").setValue(0).await()
    }
  }

  // --- Drip Irrigation Controls ---
  suspend fun setDripMode(deviceId: String, mode: String) {
    val ref = database.reference.child("devices").child(deviceId).child("controls").child("drip").child("mode")
    ref.setValue(mode).await()
  }

  suspend fun setDripManualTrigger(deviceId: String, trigger: Boolean) {
    val devRef = database.reference.child("devices").child(deviceId)
    devRef.child("controls").child("drip").child("manual_trigger").setValue(trigger).await()
    devRef.child("valves_state").child("drip_active").setValue(trigger).await()
    if (trigger) {
      // Set remaining seconds (e.g. 15 minutes = 900s)
      devRef.child("valves_state").child("drip_remaining_sec").setValue(900).await()
    } else {
      devRef.child("valves_state").child("drip_remaining_sec").setValue(0).await()
    }
  }

  // --- Water Fogger Settings ---
  suspend fun saveFoggerSettings(deviceId: String, maxTemp: Float, minHumidity: Float, durationSec: Int) {
    val ref = database.reference.child("devices").child(deviceId).child("settings").child("fogger")
    ref.setValue(
      mapOf(
        "temp_threshold" to maxTemp,
        "humidity_threshold" to minHumidity,
        "run_duration_sec" to durationSec
      )
    ).await()
  }

  // --- Drip Schedules Manager ---
  suspend fun saveDripTimeSlot(deviceId: String, slot: DripTimeSlot) {
    val ref = database.reference.child("devices").child(deviceId).child("settings").child("drip").child("schedules").child(slot.id)
    ref.setValue(slot.toMap()).await()
  }

  suspend fun removeDripTimeSlot(deviceId: String, slotId: String) {
    val ref = database.reference.child("devices").child(deviceId).child("settings").child("drip").child("schedules").child(slotId)
    ref.removeValue().await()
  }

  suspend fun toggleDripTimeSlot(deviceId: String, slotId: String, isEnabled: Boolean) {
    val ref = database.reference.child("devices").child(deviceId).child("settings").child("drip").child("schedules").child(slotId)
    ref.child("isEnabled").setValue(isEnabled).await()
  }

  // --- Timezone Sync ---
  suspend fun saveTimezone(deviceId: String, timezone: String) {
    val ref = database.reference.child("devices").child(deviceId).child("settings").child("timezone")
    ref.setValue(timezone).await()
  }
}

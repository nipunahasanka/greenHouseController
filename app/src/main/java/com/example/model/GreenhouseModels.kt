package com.example.model

data class DripTimeSlot(
  val id: String = "",
  val startTime: String = "06:30 AM",
  val durationMinutes: Int = 15,
  val isEnabled: Boolean = true
) {
  fun toMap(): Map<String, Any> {
    return mapOf(
      "id" to id,
      "startTime" to startTime,
      "durationMinutes" to durationMinutes,
      "isEnabled" to isEnabled
    )
  }

  companion object {
    fun fromMap(id: String, map: Map<String, Any?>): DripTimeSlot {
      return DripTimeSlot(
        id = id.ifBlank { (map["id"] as? String) ?: "" },
        startTime = (map["startTime"] as? String) ?: (map["start_time"] as? String) ?: "06:30 AM",
        durationMinutes = (map["durationMinutes"] as? Number)?.toInt()
          ?: (map["duration_minutes"] as? Number)?.toInt()
          ?: 15,
        isEnabled = (map["isEnabled"] as? Boolean)
          ?: (map["is_enabled"] as? Boolean)
          ?: true
      )
    }
  }
}

data class EspDeviceData(
  val deviceId: String = "",
  val deviceName: String = "Greenhouse Controller",
  val ownerUid: String = "",
  
  // Heartbeat & Connectivity Meta
  val isOnlineRaw: Boolean = true,
  val lastSeenTimestamp: Long = System.currentTimeMillis() / 1000L,
  val wifiRssi: Int = -65,
  
  // Telemetry
  val temperature: Float = 27.5f,
  val humidity: Float = 66.0f,
  val tankWaterPresent: Boolean = true,
  
  // Valve 1: Water Fogger
  val valve1FoggerActive: Boolean = false,
  val valve1FoggerRemainingSec: Int = 0,
  val valve1FoggerMode: String = "AUTOMATIC", // "AUTOMATIC" or "MANUAL"
  val valve1FoggerManualTrigger: Boolean = false,
  
  // Valve 2: Drip Irrigation
  val valve2DripActive: Boolean = false,
  val valve2DripRemainingSec: Int = 0,
  val valve2DripMode: String = "AUTOMATIC", // "AUTOMATIC" or "MANUAL"
  val valve2DripManualTrigger: Boolean = false,
  
  // Settings: Water Fogger
  val foggerTempThreshold: Float = 32.0f,
  val foggerHumidityThreshold: Float = 60.0f,
  val foggerRunDurationSec: Int = 45,
  
  // Settings: Drip Schedules & Timezone
  val dripTimeSlots: List<DripTimeSlot> = emptyList(),
  val timezone: String = "Asia/Colombo (GMT+5:30)",
  
  val updatedAtMillis: Long = System.currentTimeMillis()
) {
  // Convert lastSeen to milliseconds regardless of whether backend wrote seconds or millis
  val effectiveLastSeenMs: Long
    get() = if (lastSeenTimestamp > 0 && lastSeenTimestamp < 100_000_000_000L) {
      lastSeenTimestamp * 1000L
    } else {
      lastSeenTimestamp
    }

  // Heartbeat check: must be marked online AND have sent a ping in the last 60 seconds
  val isOnline: Boolean
    get() {
      if (!isOnlineRaw) return false
      val diffMs = System.currentTimeMillis() - effectiveLastSeenMs
      return diffMs in -5000L..60_000L
    }

  val wifiStrengthLabel: String
    get() = when {
      wifiRssi >= -65 -> "Strong"
      wifiRssi >= -75 -> "Good"
      wifiRssi >= -85 -> "Fair"
      else -> "Weak"
    }

  val wifiSignalBars: Int
    get() = when {
      wifiRssi >= -65 -> 4
      wifiRssi >= -75 -> 3
      wifiRssi >= -85 -> 2
      else -> 1
    }
}

data class RegisteredDevice(
  val deviceId: String = "",
  val claimed: Boolean = false,
  val ownerUid: String? = null,
  val model: String = "ESP32-S3-GH"
)

data class UserProfile(
  val userId: String = "",
  val email: String = "",
  val activeDeviceId: String? = null,
  val pairedDevices: List<String> = emptyList()
)

enum class PairingStatus {
  IDLE,
  SCANNING_BLE,
  DEVICE_FOUND,
  EXCHANGING_CREDS,
  CONNECTING_WIFI,
  CONNECTED_SUCCESS,
  FAILED
}

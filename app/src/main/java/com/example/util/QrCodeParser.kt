package com.example.util

import org.json.JSONObject

data class ScannedEspDevice(
  val deviceId: String,
  val setupKey: String? = null,
  val rawContent: String = ""
)

object QrCodeParser {

  /**
   * Parses QR code content which may be:
   * 1. JSON: {"device_id": "ESP32_GH_0042", "setup_key": "x9F2#kL8"}
   * 2. Key-Value pairs: device_id=ESP32_GH_0042&setup_key=x9F2#kL8 or device_id:ESP32_GH_0042;setup_key:x9F2#kL8
   * 3. Delimited: ESP32_GH_0042:x9F2#kL8
   * 4. Plain text: ESP32_GH_0042
   */
  fun parse(raw: String): ScannedEspDevice {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
      return ScannedEspDevice(deviceId = "ESP32_GH_0042", rawContent = raw)
    }

    // 1. Try parsing JSON (e.g. {"device_id": "ESP32_GH_0042", "setup_key": "x9F2#kL8"})
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
      val idPattern = """"?(?:device_id|deviceId|serial_id|serialId|id)"?\s*:\s*"([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
      val keyPattern = """"?(?:setup_key|setupKey|key|pin)"?\s*:\s*"([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)

      val idMatch = idPattern.find(trimmed)?.groupValues?.get(1)
      val keyMatch = keyPattern.find(trimmed)?.groupValues?.get(1)

      if (!idMatch.isNullOrBlank()) {
        return ScannedEspDevice(
          deviceId = idMatch.trim(),
          setupKey = keyMatch?.trim()?.ifBlank { null },
          rawContent = raw
        )
      }
    }

    // 2. Try URI query or key-value format (e.g. device_id=ESP32_GH_0042&setup_key=x9F2#kL8)
    if (trimmed.contains("=") || trimmed.contains(";")) {
      val pairs = trimmed.split("&", ";", "\n")
      var id: String? = null
      var key: String? = null

      for (pair in pairs) {
        val parts = pair.split("=", ":", limit = 2)
        if (parts.size == 2) {
          val k = parts[0].trim().lowercase()
          val v = parts[1].trim()
          if (k in listOf("device_id", "deviceid", "serial_id", "serialid", "id", "dev")) {
            id = v
          } else if (k in listOf("setup_key", "setupkey", "key", "pin", "auth")) {
            key = v
          }
        }
      }

      if (!id.isNullOrBlank()) {
        return ScannedEspDevice(
          deviceId = id,
          setupKey = key?.ifBlank { null },
          rawContent = raw
        )
      }
    }

    // 3. Try colon delimited: ESP32_GH_0042:x9F2#kL8
    if (trimmed.contains(":") && !trimmed.startsWith("http")) {
      val parts = trimmed.split(":", limit = 2)
      if (parts.size == 2 && parts[0].isNotBlank()) {
        return ScannedEspDevice(
          deviceId = parts[0].trim(),
          setupKey = parts[1].trim().ifBlank { null },
          rawContent = raw
        )
      }
    }

    // 4. Default: raw device ID
    return ScannedEspDevice(
      deviceId = trimmed,
      setupKey = null,
      rawContent = raw
    )
  }
}

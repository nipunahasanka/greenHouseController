package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat

data class ScannedWifiNetwork(
  val ssid: String,
  val level: Int, // RSSI in dBm
  val frequency: Int, // MHz
  val is24GHz: Boolean
) {
  val signalBars: Int
    get() = when {
      level >= -65 -> 4
      level >= -75 -> 3
      level >= -85 -> 2
      else -> 1
    }
}

object WifiScannerHelper {

  fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
  }

  fun getScannedNetworks(context: Context): List<ScannedWifiNetwork> {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
      ?: return emptyList()

    return try {
      @Suppress("DEPRECATION")
      val results = wifiManager.scanResults ?: emptyList()
      
      // Filter non-empty SSIDs, prioritize 2.4GHz (frequency 2400-2500 MHz)
      results
        .filter { !it.SSID.isNullOrBlank() }
        .map { result ->
          val is24 = result.frequency in 2400..2500
          ScannedWifiNetwork(
            ssid = result.SSID,
            level = result.level,
            frequency = result.frequency,
            is24GHz = is24
          )
        }
        .sortedWith(
          compareByDescending<ScannedWifiNetwork> { it.is24GHz }
            .thenByDescending { it.level }
        )
        .distinctBy { it.ssid }
    } catch (_: Exception) {
      emptyList()
    }
  }
}

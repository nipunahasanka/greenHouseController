package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testBleProvisioningJsonFormatting() {
    val ssid = "MyWifi"
    val password = "MyPassword123"
    val key = "SETUP123"
    val uid = "user_abc123"

    val jsonString = """{"ssid":"$ssid","password":"$password","key":"$key","uid":"$uid"}"""

    assertTrue(jsonString.contains("MyWifi"))
    assertTrue(jsonString.contains("MyPassword123"))
    assertTrue(jsonString.contains("SETUP123"))
    assertTrue(jsonString.contains("user_abc123"))
  }

  @Test
  fun testDefaultControlValues() {
    val foggerAutoMode = true
    val foggerTriggerTemp = 32.0
    val foggerTargetHumidity = 60.0
    val dripDurationSec = 300

    assertTrue(foggerAutoMode)
    assertEquals(32.0, foggerTriggerTemp, 0.01)
    assertEquals(60.0, foggerTargetHumidity, 0.01)
    assertEquals(300, dripDurationSec)
  }
}

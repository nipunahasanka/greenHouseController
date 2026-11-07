package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EspDeviceData

@Composable
fun TelemetryCards(
  deviceData: EspDeviceData?,
  modifier: Modifier = Modifier
) {
  val temp = deviceData?.temperature ?: 0.0f
  val humidity = deviceData?.humidity ?: 0.0f
  val maxThreshold = deviceData?.foggerTempThreshold ?: 32.0f
  val minThreshold = deviceData?.foggerHumidityThreshold ?: 60.0f
  val tankWaterPresent = deviceData?.tankWaterPresent ?: false

  Column(modifier = modifier.fillMaxWidth()) {
    // 1. Temperature & Humidity Row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      TemperatureCard(
        temp = temp,
        maxThreshold = maxThreshold,
        modifier = Modifier.weight(1f)
      )

      HumidityCard(
        humidity = humidity,
        minThreshold = minThreshold,
        modifier = Modifier.weight(1f)
      )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // 2. Feed Tank Water Level Status Card (Replaces old soil/usage cards)
    FeedTankWaterLevelCard(
      tankWaterPresent = tankWaterPresent,
      modifier = Modifier.fillMaxWidth()
    )
  }
}

@Composable
fun TemperatureCard(
  temp: Float,
  maxThreshold: Float,
  modifier: Modifier = Modifier
) {
  val isHigh = temp >= maxThreshold
  val isWarn = temp >= (maxThreshold - 2.0f)

  val cardBg = if (isHigh) {
    listOf(Color(0xFFFFF3E0), Color(0xFFFFEBEE))
  } else {
    listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
  }

  val statusColor = when {
    isHigh -> Color(0xFFD32F2F)
    isWarn -> Color(0xFFF57C00)
    else -> Color(0xFF2E7D32)
  }

  val statusText = when {
    isHigh -> "High Temp!"
    isWarn -> "Elevated"
    else -> "Optimal"
  }

  ElevatedCard(
    shape = RoundedCornerShape(16.dp),
    modifier = modifier.testTag("temperature_card")
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(Brush.verticalGradient(cardBg))
        .padding(16.dp)
    ) {
      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .background(
                color = if (isHigh) Color(0xFFFFCDD2) else Color(0xFFFFE0B2),
                shape = CircleShape
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Thermostat,
              contentDescription = "Temperature Sensor",
              tint = if (isHigh) Color(0xFFC62828) else Color(0xFFE65100),
              modifier = Modifier.size(20.dp)
            )
          }

          Surface(
            shape = RoundedCornerShape(8.dp),
            color = statusColor.copy(alpha = 0.15f)
          ) {
            Text(
              text = statusText,
              color = statusColor,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
          text = "Temperature",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(verticalAlignment = Alignment.Bottom) {
          Text(
            text = String.format("%.1f", temp),
            style = MaterialTheme.typography.headlineMedium.copy(
              fontWeight = FontWeight.Black,
              letterSpacing = (-0.5).sp
            ),
            color = if (isHigh) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("live_temp_value")
          )
          Text(
            text = " °C",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 3.dp)
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        val progress = ((temp - 10f) / 30f).coerceIn(0f, 1f)
        LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp)),
          color = statusColor,
          trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "Trigger at ≥ ${maxThreshold}°C",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
fun HumidityCard(
  humidity: Float,
  minThreshold: Float,
  modifier: Modifier = Modifier
) {
  val isLow = humidity <= minThreshold
  val isDryWarn = humidity <= (minThreshold + 5.0f)

  val cardBg = if (isLow) {
    listOf(Color(0xFFFFF8E1), Color(0xFFFFECB3))
  } else {
    listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
  }

  val statusColor = when {
    isLow -> Color(0xFFC62828)
    isDryWarn -> Color(0xFFF57C00)
    humidity > 85f -> Color(0xFF0288D1)
    else -> Color(0xFF00897B)
  }

  val statusText = when {
    isLow -> "Low Humidity"
    isDryWarn -> "Low Mist"
    humidity > 85f -> "Very Moist"
    else -> "Optimal"
  }

  ElevatedCard(
    shape = RoundedCornerShape(16.dp),
    modifier = modifier.testTag("humidity_card")
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(Brush.verticalGradient(cardBg))
        .padding(16.dp)
    ) {
      Column {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .background(
                color = if (isLow) Color(0xFFFFE0B2) else Color(0xFFE0F7FA),
                shape = CircleShape
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.WaterDrop,
              contentDescription = "Humidity Sensor",
              tint = if (isLow) Color(0xFFEF6C00) else Color(0xFF0097A7),
              modifier = Modifier.size(20.dp)
            )
          }

          Surface(
            shape = RoundedCornerShape(8.dp),
            color = statusColor.copy(alpha = 0.15f)
          ) {
            Text(
              text = statusText,
              color = statusColor,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
          text = "Relative Humidity",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(verticalAlignment = Alignment.Bottom) {
          Text(
            text = String.format("%.1f", humidity),
            style = MaterialTheme.typography.headlineMedium.copy(
              fontWeight = FontWeight.Black,
              letterSpacing = (-0.5).sp
            ),
            color = if (isLow) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("live_humidity_value")
          )
          Text(
            text = " %",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 3.dp)
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        val progress = (humidity / 100f).coerceIn(0f, 1f)
        LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp)),
          color = statusColor,
          trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "Trigger at ≤ ${minThreshold}%",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

/**
 * Feed Tank Water Level status indicator with float sensor reading
 * Shows "Water Level: Normal" (Green badge) if true, or "Water Level: Low / Empty" (Pulsing Red warning) if false.
 */
@Composable
fun FeedTankWaterLevelCard(
  tankWaterPresent: Boolean,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "warning_pulse")
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.4f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 700),
      repeatMode = RepeatMode.Reverse
    ),
    label = "red_pulse"
  )

  ElevatedCard(
    shape = RoundedCornerShape(16.dp),
    modifier = modifier.testTag("feed_tank_water_level_card")
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          if (tankWaterPresent)
            Color(0xFFE8F5E9)
          else
            Color(0xFFFFEBEE).copy(alpha = pulseAlpha)
        )
        .padding(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          modifier = Modifier.weight(1f),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(42.dp)
              .background(
                if (tankWaterPresent) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                CircleShape
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (tankWaterPresent) Icons.Default.Water else Icons.Default.Warning,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(24.dp)
            )
          }

          Spacer(modifier = Modifier.width(14.dp))

          Column {
            Text(
              text = "Feed Tank Water Level",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = if (tankWaterPresent) Color(0xFF1B5E20) else Color(0xFFB71C1C)
            )
            Text(
              text = if (tankWaterPresent)
                "Float Sensor: Water Detected (Normal)"
              else
                "Float Sensor: Low / Tank Empty!",
              style = MaterialTheme.typography.bodySmall,
              color = if (tankWaterPresent) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
          }
        }

        // Status Badge
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = if (tankWaterPresent) Color(0xFF2E7D32) else Color(0xFFD32F2F),
          modifier = Modifier.testTag("water_level_badge")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = if (tankWaterPresent) Icons.Default.CheckCircle else Icons.Default.Warning,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (tankWaterPresent) "NORMAL" else "LOW / EMPTY",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp
            )
          }
        }
      }
    }
  }
}

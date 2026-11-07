package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.ProvisioningLogEntry
import com.example.util.ProvisioningState
import com.example.util.ProvisioningStep

@Composable
fun ProvisioningStepVisualizer(
  provisioningState: ProvisioningState,
  modifier: Modifier = Modifier
) {
  val currentStep = provisioningState.step
  val steps = listOf(
    ProvisioningStep.STEP_1_BLE_SCAN to "1. BLE Scan",
    ProvisioningStep.STEP_2_GATT_HANDSHAKE to "2. GATT Link",
    ProvisioningStep.STEP_3_TRANSMIT_CREDS to "3. Transmit Wi-Fi",
    ProvisioningStep.STEP_4_AWAIT_WIFI to "4. ESP32 Connect",
    ProvisioningStep.STEP_5_VERIFY_FIREBASE to "5. Cloud Sync"
  )

  Column(modifier = modifier.fillMaxWidth()) {
    // Stepper row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      steps.forEachIndexed { index, (stepEnum, label) ->
        val isPassed = currentStep.ordinal > stepEnum.ordinal || currentStep == ProvisioningStep.SUCCESS
        val isCurrent = currentStep == stepEnum
        val isFailed = currentStep == ProvisioningStep.FAILED && provisioningState.logs.size >= index

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(28.dp)
              .background(
                color = when {
                  isPassed -> Color(0xFF00E676)
                  isFailed -> Color(0xFFFF5252)
                  isCurrent -> Color(0xFF00B4D8)
                  else -> MaterialTheme.colorScheme.surfaceVariant
                },
                shape = CircleShape
              ),
            contentAlignment = Alignment.Center
          ) {
            when {
              isPassed -> Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
              isCurrent -> CircularProgressIndicator(color = Color.Black, strokeWidth = 2.5.dp, modifier = Modifier.size(16.dp))
              isFailed -> Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
              else -> Text(text = "${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (isCurrent || isPassed) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrent || isPassed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        if (index < steps.size - 1) {
          Box(
            modifier = Modifier
              .height(2.dp)
              .weight(0.5f)
              .background(
                if (currentStep.ordinal > stepEnum.ordinal || currentStep == ProvisioningStep.SUCCESS)
                  Color(0xFF00E676)
                else
                  MaterialTheme.colorScheme.surfaceVariant
              )
          )
        }
      }
    }
  }
}

@Composable
fun ProvisioningConsoleBox(
  logs: List<ProvisioningLogEntry>,
  modifier: Modifier = Modifier
) {
  var isExpanded by remember { mutableStateOf(true) }
  val scrollState = rememberScrollState()

  // Auto scroll to bottom when logs update
  LaunchedEffect(logs.size) {
    if (logs.isNotEmpty()) {
      scrollState.animateScrollTo(scrollState.maxValue)
    }
  }

  Surface(
    shape = RoundedCornerShape(12.dp),
    color = Color(0xFF09140E),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f)),
    modifier = modifier
      .fillMaxWidth()
      .testTag("provisioning_console_box")
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      // Header row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { isExpanded = !isExpanded },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Terminal,
            contentDescription = null,
            tint = Color(0xFF00E676),
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Provisioning Console [Live Logs]",
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            ),
            color = Color(0xFF00E676)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF00E676).copy(alpha = 0.2f)
          ) {
            Text(
              text = "${logs.size} lines",
              color = Color(0xFF69F0AE),
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }

        Icon(
          imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
          contentDescription = "Toggle logs",
          tint = Color(0xFF00E676)
        )
      }

      AnimatedVisibility(visible = isExpanded) {
        Column {
          Spacer(modifier = Modifier.height(10.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 160.dp)
              .background(Color(0xFF030B07), RoundedCornerShape(8.dp))
              .padding(8.dp)
              .verticalScroll(scrollState)
          ) {
            if (logs.isEmpty()) {
              Text(
                text = "Console ready. Live BLE & GATT logs will stream here upon Connect & Pair.",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color.Gray
              )
            } else {
              Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                logs.forEach { log ->
                  val tagColor = when {
                    log.tag.contains("ERROR", ignoreCase = true) -> Color(0xFFFF5252)
                    log.tag.contains("SUCCESS", ignoreCase = true) -> Color(0xFF00E676)
                    log.tag.contains("GATT", ignoreCase = true) -> Color(0xFF80D8FF)
                    log.tag.contains("Firebase", ignoreCase = true) -> Color(0xFFFFD54F)
                    else -> Color(0xFFB9F6CA)
                  }
                  Row {
                    Text(
                      text = "[${log.time}] ",
                      fontFamily = FontFamily.Monospace,
                      fontSize = 10.5.sp,
                      color = Color.Gray
                    )
                    Text(
                      text = "[${log.tag}] ",
                      fontFamily = FontFamily.Monospace,
                      fontSize = 10.5.sp,
                      fontWeight = FontWeight.Bold,
                      color = tagColor
                    )
                    Text(
                      text = log.message,
                      fontFamily = FontFamily.Monospace,
                      fontSize = 10.5.sp,
                      color = Color.White
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

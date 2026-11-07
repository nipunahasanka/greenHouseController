package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shower
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DripTimeSlot
import com.example.model.EspDeviceData
import java.util.TimeZone

@Composable
fun ThresholdsScreen(
  deviceData: EspDeviceData?,
  onSaveFoggerSettings: (maxTemp: Float, minHumidity: Float, durationSec: Int) -> Unit,
  onSaveDripTimeSlot: (slot: DripTimeSlot) -> Unit,
  onRemoveDripTimeSlot: (slotId: String) -> Unit,
  onToggleDripTimeSlot: (slotId: String, isEnabled: Boolean) -> Unit,
  onSaveTimezone: (timezone: String) -> Unit,
  modifier: Modifier = Modifier
) {
  var maxTempInput by remember { mutableStateOf("32.0") }
  var minHumidityInput by remember { mutableStateOf("60.0") }
  var durationInput by remember { mutableStateOf("45") }

  var editingSlot by remember { mutableStateOf<DripTimeSlot?>(null) }
  var showSlotDialog by remember { mutableStateOf(false) }
  var showTimezoneDialog by remember { mutableStateOf(false) }

  // Sync with deviceData updates from Firebase
  LaunchedEffect(deviceData?.foggerTempThreshold, deviceData?.foggerHumidityThreshold, deviceData?.foggerRunDurationSec) {
    if (deviceData != null) {
      maxTempInput = deviceData.foggerTempThreshold.toString()
      minHumidityInput = deviceData.foggerHumidityThreshold.toString()
      durationInput = deviceData.foggerRunDurationSec.toString()
    }
  }

  val parsedMaxTemp = maxTempInput.toFloatOrNull() ?: 32.0f
  val parsedMinHumidity = minHumidityInput.toFloatOrNull() ?: 60.0f
  val parsedDuration = durationInput.toIntOrNull() ?: 45

  val dripSlots = deviceData?.dripTimeSlots ?: emptyList()
  val currentTimezone = deviceData?.timezone ?: "Asia/Colombo (GMT+5:30)"

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp)
      .testTag("thresholds_screen_content")
  ) {
    Text(
      text = "Irrigation Settings & Schedules",
      style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.onSurface
    )
    Text(
      text = "Configure automated trigger rules for Water Foggers and daily Drip schedules",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(16.dp))

    // ==========================================
    // 1. WATER FOGGER SETTINGS (Threshold-driven)
    // ==========================================
    ElevatedCard(
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("fogger_settings_card")
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Shower,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Water Fogger Thresholds",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
              text = "Trigger overhead mist if microclimate exceeds limits",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Max Temp Threshold
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(imageVector = Icons.Default.Thermostat, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Max Temperature Trigger:", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
              text = "${String.format("%.1f", parsedMaxTemp)} °C",
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
          }

          Slider(
            value = parsedMaxTemp,
            onValueChange = { maxTempInput = String.format("%.1f", it) },
            valueRange = 20.0f..45.0f,
            steps = 50,
            colors = SliderDefaults.colors(
              thumbColor = MaterialTheme.colorScheme.primary,
              activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.testTag("max_temp_slider")
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Min Humidity Threshold
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(imageVector = Icons.Default.WaterDrop, contentDescription = null, tint = Color(0xFF0097A7), modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Min Humidity Trigger:", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
              text = "${String.format("%.1f", parsedMinHumidity)} %",
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
          }

          Slider(
            value = parsedMinHumidity,
            onValueChange = { minHumidityInput = String.format("%.1f", it) },
            valueRange = 30.0f..90.0f,
            steps = 60,
            colors = SliderDefaults.colors(
              thumbColor = MaterialTheme.colorScheme.primary,
              activeTrackColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.testTag("min_humidity_slider")
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Run Duration
        OutlinedTextField(
          value = durationInput,
          onValueChange = { durationInput = it.filter { char -> char.isDigit() } },
          label = { Text("Misting Run Duration (Seconds)") },
          leadingIcon = {
            Icon(imageVector = Icons.Default.Timer, contentDescription = null)
          },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("fogger_duration_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
          onClick = {
            onSaveFoggerSettings(parsedMaxTemp, parsedMinHumidity, parsedDuration)
          },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("save_fogger_settings_button")
        ) {
          Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Save Water Fogger Thresholds", fontWeight = FontWeight.Bold)
        }
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // ==========================================
    // 2. DRIP SCHEDULE SLOTS MANAGER
    // ==========================================
    ElevatedCard(
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("drip_settings_card")
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        // Fixed Layout Header: Prevents overflow on compact displays
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .background(Color(0xFF00B4D8).copy(alpha = 0.2f), CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Opacity,
                contentDescription = null,
                tint = Color(0xFF00B4D8),
                modifier = Modifier.size(20.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "Drip Schedules",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
              )
              Text(
                text = "${dripSlots.size} slots active",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          // Properly padded Add Slot button
          Button(
            onClick = {
              editingSlot = null
              showSlotDialog = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F)),
            shape = RoundedCornerShape(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.testTag("add_time_slot_button")
          ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Slot", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (dripSlots.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
              .padding(20.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "No drip schedules configured yet. Tap 'Add Slot' to create an automated daily watering schedule.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            dripSlots.forEach { slot ->
              DripTimeSlotItem(
                slot = slot,
                onEdit = {
                  editingSlot = slot
                  showSlotDialog = true
                },
                onToggle = { isEnabled -> onToggleDripTimeSlot(slot.id, isEnabled) },
                onDelete = { onRemoveDripTimeSlot(slot.id) }
              )
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // ==========================================
    // 3. DEVICE TIMEZONE & CLOCK SYNC CARD
    // ==========================================
    ElevatedCard(
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("timezone_card")
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "Device Timezone & Clock",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
              )
              Text(
                text = "Ensures ESP32 cron schedules trigger on local time",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Active Timezone",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = currentTimezone,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
              )
            }

            OutlinedButton(
              onClick = { showTimezoneDialog = true },
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.testTag("change_timezone_button")
            ) {
              Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Sync / Edit", fontSize = 12.sp)
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(28.dp))
  }

  // Dialog: Add or Edit Drip Time Slot
  if (showSlotDialog) {
    SlotEditDialog(
      slotToEdit = editingSlot,
      onDismiss = { showSlotDialog = false },
      onConfirm = { savedSlot ->
        showSlotDialog = false
        onSaveDripTimeSlot(savedSlot)
      }
    )
  }

  // Dialog: Select / Sync Timezone
  if (showTimezoneDialog) {
    TimezoneSelectionDialog(
      currentTimezone = currentTimezone,
      onDismiss = { showTimezoneDialog = false },
      onConfirm = { newTz ->
        showTimezoneDialog = false
        onSaveTimezone(newTz)
      }
    )
  }
}

@Composable
fun DripTimeSlotItem(
  slot: DripTimeSlot,
  onEdit: () -> Unit,
  onToggle: (Boolean) -> Unit,
  onDelete: () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onEdit() }
      .testTag("drip_slot_${slot.id}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(34.dp)
            .background(if (slot.isEnabled) Color(0xFF00B4D8).copy(alpha = 0.2f) else Color(0xFF263238), CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.AccessTime,
            contentDescription = null,
            tint = if (slot.isEnabled) Color(0xFF00B4D8) else Color(0xFF78909C),
            modifier = Modifier.size(18.dp)
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = slot.startTime,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = if (slot.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
              imageVector = Icons.Default.Edit,
              contentDescription = "Edit slot",
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
              modifier = Modifier.size(13.dp)
            )
          }
          Text(
            text = "Duration: ${slot.durationMinutes} min",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
          checked = slot.isEnabled,
          onCheckedChange = { onToggle(it) },
          modifier = Modifier.testTag("toggle_slot_${slot.id}")
        )

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
          onClick = onDelete,
          modifier = Modifier.testTag("delete_slot_${slot.id}")
        ) {
          Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Delete Slot",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }
  }
}

@Composable
fun SlotEditDialog(
  slotToEdit: DripTimeSlot?,
  onDismiss: () -> Unit,
  onConfirm: (DripTimeSlot) -> Unit
) {
  val isEditing = slotToEdit != null
  val initialParts = (slotToEdit?.startTime ?: "06:30 AM").split(" ", ":")
  var selectedHour by remember { mutableStateOf(initialParts.getOrNull(0) ?: "06") }
  var selectedMinute by remember { mutableStateOf(initialParts.getOrNull(1) ?: "30") }
  var selectedPeriod by remember { mutableStateOf(initialParts.getOrNull(2) ?: "AM") }
  var durationInput by remember { mutableStateOf((slotToEdit?.durationMinutes ?: 15).toString()) }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(imageVector = Icons.Default.AccessTime, contentDescription = null, tint = Color(0xFF00838F))
    },
    title = {
      Text(if (isEditing) "Edit Drip Schedule" else "Add Drip Schedule", fontWeight = FontWeight.Bold)
    },
    text = {
      Column {
        Text("Start Time (12-hour AM/PM):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Hour Field
          OutlinedTextField(
            value = selectedHour,
            onValueChange = {
              val num = it.filter { c -> c.isDigit() }.take(2)
              selectedHour = num
            },
            label = { Text("Hour") },
            singleLine = true,
            modifier = Modifier.weight(1f)
          )

          Text(":", fontWeight = FontWeight.Bold, fontSize = 20.sp)

          // Minute Field
          OutlinedTextField(
            value = selectedMinute,
            onValueChange = {
              val num = it.filter { c -> c.isDigit() }.take(2)
              selectedMinute = num
            },
            label = { Text("Min") },
            singleLine = true,
            modifier = Modifier.weight(1f)
          )

          // AM / PM Selector
          Row(
            modifier = Modifier
              .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
              .padding(4.dp)
          ) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = if (selectedPeriod == "AM") Color(0xFF00838F) else Color.Transparent,
              modifier = Modifier.clickable { selectedPeriod = "AM" }
            ) {
              Text(
                "AM",
                color = if (selectedPeriod == "AM") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
              )
            }
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = if (selectedPeriod == "PM") Color(0xFF00838F) else Color.Transparent,
              modifier = Modifier.clickable { selectedPeriod = "PM" }
            ) {
              Text(
                "PM",
                color = if (selectedPeriod == "PM") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Duration in Minutes
        OutlinedTextField(
          value = durationInput,
          onValueChange = { durationInput = it.filter { c -> c.isDigit() } },
          label = { Text("Duration (Minutes)") },
          leadingIcon = {
            Icon(imageVector = Icons.Default.Timer, contentDescription = null)
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val hr = selectedHour.padStart(2, '0')
          val mn = selectedMinute.padStart(2, '0')
          val timeStr = "$hr:$mn $selectedPeriod"
          val dur = durationInput.toIntOrNull() ?: 15
          val updated = DripTimeSlot(
            id = slotToEdit?.id ?: "",
            startTime = timeStr,
            durationMinutes = dur,
            isEnabled = slotToEdit?.isEnabled ?: true
          )
          onConfirm(updated)
        },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00838F)),
        modifier = Modifier.testTag("confirm_slot_dialog_button")
      ) {
        Text("Save Slot")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

@Composable
fun TimezoneSelectionDialog(
  currentTimezone: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit
) {
  val commonTimezones = listOf(
    "Asia/Colombo (GMT+5:30)",
    "Asia/Kolkata (GMT+5:30)",
    "Asia/Bangkok (GMT+7:00)",
    "Asia/Singapore (GMT+8:00)",
    "Europe/London (GMT+0:00)",
    "Europe/Berlin (GMT+1:00)",
    "America/New_York (GMT-5:00)",
    "America/Los_Angeles (GMT-8:00)",
    "UTC (GMT+0:00)"
  )

  var selectedTz by remember { mutableStateOf(currentTimezone) }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(imageVector = Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    },
    title = {
      Text("Device Timezone & Clock Sync", fontWeight = FontWeight.Bold)
    },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
          text = "Select time zone to sync with ESP32 internal RTC clock:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Auto-detect phone timezone button
        OutlinedButton(
          onClick = {
            val phoneTz = TimeZone.getDefault().id
            selectedTz = phoneTz
          },
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Use Phone Timezone (${TimeZone.getDefault().id})", fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        commonTimezones.forEach { tz ->
          val isSelected = selectedTz == tz
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 3.dp)
              .clickable { selectedTz = tz }
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = tz,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
              )
              if (isSelected) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = { onConfirm(selectedTz) }) {
        Text("Save & Sync")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

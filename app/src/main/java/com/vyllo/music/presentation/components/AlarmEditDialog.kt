package com.vyllo.music.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vyllo.music.domain.model.DayOfWeek
import com.vyllo.music.ui.alarm.AlarmViewModel

/**
 * Dialog for adding or editing an alarm.
 */
@Composable
fun AlarmEditDialog(
    viewModel: AlarmViewModel,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (viewModel.editingAlarm == null) "Add Alarm" else "Edit Alarm",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Time selection
                AlarmTimeSelector(
                    hour = viewModel.selectedHour,
                    minute = viewModel.selectedMinute,
                    onTimeClick = { viewModel.openTimePicker() }
                )

                // Label
                OutlinedTextField(
                    value = viewModel.selectedLabel,
                    onValueChange = { viewModel.selectedLabel = it },
                    label = { Text("Label (optional)") },
                    placeholder = { Text("e.g., Wake up, Gym, Work") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Rounded.Label, contentDescription = null)
                    }
                )

                // Repeat days
                RepeatDaysSelector(
                    selectedDays = viewModel.selectedRepeatDays,
                    onDayToggle = { viewModel.toggleRepeatDay(it) }
                )

                // Sound selection
                SoundSelector(
                    soundType = viewModel.selectedSoundType,
                    songTitle = viewModel.selectedSongTitle,
                    onSoundClick = { viewModel.openSoundPicker() }
                )

                // Volume slider
                VolumeSelector(
                    volume = viewModel.volume,
                    isGradualEnabled = viewModel.isGradualVolumeEnabled,
                    onVolumeChange = { viewModel.volume = it },
                    onGradualToggle = { viewModel.isGradualVolumeEnabled = it }
                )

                // Vibration toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Vibration",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Vibrate when alarm triggers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Switch(
                        checked = viewModel.isVibrationEnabled,
                        onCheckedChange = { viewModel.isVibrationEnabled = it }
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onSave) {
                Icon(Icons.Rounded.Save, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Time selector component showing current selected time.
 */
@Composable
private fun AlarmTimeSelector(
    hour: Int,
    minute: Int,
    onTimeClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTimeClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Time",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Icon(
                Icons.Rounded.Edit,
                contentDescription = "Edit time",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Repeat days selector with day chips.
 */
@Composable
private fun RepeatDaysSelector(
    selectedDays: Set<DayOfWeek>,
    onDayToggle: (DayOfWeek) -> Unit
) {
    Column {
        Text(
            text = "Repeat",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(DayOfWeek.values()) { day ->
                val isSelected = selectedDays.contains(day)
                FilterChip(
                    selected = isSelected,
                    onClick = { onDayToggle(day) },
                    label = { Text(day.name.take(1)) },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null
                )
            }
        }
    }
}

/**
 * Sound selector showing current selected sound.
 */
@Composable
private fun SoundSelector(
    soundType: com.vyllo.music.domain.model.SoundType,
    songTitle: String?,
    onSoundClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSoundClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Sound",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = songTitle ?: "Default alarm sound",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "Select sound",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Volume slider with gradual volume toggle.
 */
@Composable
private fun VolumeSelector(
    volume: Int,
    isGradualEnabled: Boolean,
    onVolumeChange: (Int) -> Unit,
    onGradualToggle: (Boolean) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Volume: $volume%",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gradual",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Switch(
                    checked = isGradualEnabled,
                    onCheckedChange = onGradualToggle
                )
            }
        }
        Slider(
            value = volume.toFloat(),
            onValueChange = { onVolumeChange(it.toInt()) },
            valueRange = 0f..100f,
            steps = 100
        )
    }
}

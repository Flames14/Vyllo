package com.vyllo.music.ui.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vyllo.music.domain.model.AlarmModel
import com.vyllo.music.domain.model.DayOfWeek
import com.vyllo.music.domain.model.SoundType
import com.vyllo.music.presentation.components.AlarmRow
import com.vyllo.music.presentation.components.AlarmTimePickerDialog
import com.vyllo.music.presentation.components.AlarmSoundPickerDialog
import com.vyllo.music.presentation.components.AlarmEditDialog

/**
 * Main alarm screen showing list of all alarms.
 */
@Composable
fun AlarmScreen(
    viewModel: AlarmViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by remember {
        derivedStateOf {
            AlarmUiState(
                alarms = viewModel.alarms,
                isLoading = viewModel.isLoading,
                error = viewModel.showError,
                showAddEditDialog = viewModel.showAddEditDialog,
                showSoundPicker = viewModel.showSoundPicker,
                showTimePicker = viewModel.showTimePicker,
                editingAlarm = viewModel.editingAlarm,
                downloadedSongs = viewModel.downloadedSongs,
                selectedHour = viewModel.selectedHour,
                selectedMinute = viewModel.selectedMinute,
                selectedLabel = viewModel.selectedLabel,
                selectedRepeatDays = viewModel.selectedRepeatDays,
                selectedSoundType = viewModel.selectedSoundType,
                selectedSongTitle = viewModel.selectedSongTitle,
                isGradualVolumeEnabled = viewModel.isGradualVolumeEnabled,
                isVibrationEnabled = viewModel.isVibrationEnabled,
                volume = viewModel.volume,
                timeUntilNextAlarm = viewModel.timeUntilNextAlarm,
                isAlarmRinging = viewModel.isAlarmRinging
            )
        }
    }

    // Handle errors
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Show error snackbar (implement in parent)
            viewModel.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            val context = androidx.compose.ui.platform.LocalContext.current
            AlarmHeader(
                onBackClick = onBackClick,
                onAddClick = { viewModel.openAddAlarmDialog() },
                countdownText = uiState.timeUntilNextAlarm,
                isAlarmRinging = uiState.isAlarmRinging,
                onStopActiveAlarm = { viewModel.dismissActiveAlarm(context) }
            )

            // Alarm list
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.alarms.isEmpty()) {
                EmptyAlarmsView(
                    onAddClick = { viewModel.openAddAlarmDialog() }
                )
            } else {
                AlarmList(
                    alarms = uiState.alarms,
                    onToggleAlarm = { viewModel.toggleAlarm(it) },
                    onEditAlarm = { viewModel.openEditAlarmDialog(it) },
                    onDeleteAlarm = { viewModel.deleteAlarm(it) }
                )
            }
        }

        // Dialogs
        if (uiState.showAddEditDialog) {
            AlarmEditDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.closeDialog() },
                onSave = { viewModel.saveAlarm() }
            )
        }

        if (uiState.showSoundPicker) {
            AlarmSoundPickerDialog(
                downloadedSongs = uiState.downloadedSongs,
                selectedSoundType = uiState.selectedSoundType,
                selectedSongUrl = viewModel.selectedSongUrl,
                onDismiss = { viewModel.closeSoundPicker() },
                onSelectSong = { url, title -> viewModel.selectSong(url, title) },
                onSelectDefault = { viewModel.selectDefaultSound() }
            )
        }

        if (uiState.showTimePicker) {
            AlarmTimePickerDialog(
                initialHour = uiState.selectedHour,
                initialMinute = uiState.selectedMinute,
                onDismiss = { viewModel.closeTimePicker() },
                onTimeSelected = { hour, minute -> viewModel.setTime(hour, minute) }
            )
        }
    }
}

/**
 * Alarm screen header with back button and add button.
 */
@Composable
private fun AlarmHeader(
    onBackClick: () -> Unit,
    onAddClick: () -> Unit,
    countdownText: String = "",
    isAlarmRinging: Boolean = false,
    onStopActiveAlarm: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Top bar with back and add buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    androidx.compose.material.icons.Icons.Rounded.ArrowBack,
                    "Back"
                )
            }

            Text(
                text = "Alarms",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            IconButton(onClick = onAddClick) {
                Icon(
                    androidx.compose.material.icons.Icons.Rounded.Add,
                    "Add Alarm",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Emergency Stop UI
        if (isAlarmRinging) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Alarm is ringing!",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Tap to stop the sound",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                    
                    Button(
                        onClick = onStopActiveAlarm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("STOP")
                    }
                }
            }
        }

        // Countdown display
        if (countdownText.isNotBlank() && !isAlarmRinging) {
            Text(
                text = countdownText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * List of alarms.
 */
@Composable
private fun AlarmList(
    alarms: List<AlarmModel>,
    onToggleAlarm: (AlarmModel) -> Unit,
    onEditAlarm: (AlarmModel) -> Unit,
    onDeleteAlarm: (AlarmModel) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        items(alarms, key = { it.id }) { alarm ->
            AlarmRow(
                alarm = alarm,
                onToggle = { onToggleAlarm(alarm) },
                onEdit = { onEditAlarm(alarm) },
                onDelete = { onDeleteAlarm(alarm) }
            )
        }
    }
}

/**
 * Empty state when no alarms exist.
 */
@Composable
private fun EmptyAlarmsView(
    onAddClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Rounded.Alarm,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "No alarms yet",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Wake up to your favorite songs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = onAddClick) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Alarm")
            }
        }
    }
}

/**
 * UI state holder for the alarm screen.
 */
data class AlarmUiState(
    val alarms: List<AlarmModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showAddEditDialog: Boolean = false,
    val showSoundPicker: Boolean = false,
    val showTimePicker: Boolean = false,
    val editingAlarm: AlarmModel? = null,
    val downloadedSongs: List<com.vyllo.music.data.download.DownloadEntity> = emptyList(),
    val selectedHour: Int = 7,
    val selectedMinute: Int = 0,
    val selectedLabel: String = "",
    val selectedRepeatDays: Set<DayOfWeek> = emptySet(),
    val selectedSoundType: SoundType = SoundType.DEFAULT,
    val selectedSongTitle: String? = null,
    val isGradualVolumeEnabled: Boolean = true,
    val isVibrationEnabled: Boolean = true,
    val volume: Int = 80,
    val timeUntilNextAlarm: String = "",
    val isAlarmRinging: Boolean = false
)

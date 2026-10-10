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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vyllo.music.R
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
    var showExactAlarmPermissionDialog by remember { mutableStateOf(false) }
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
    val screenContext = androidx.compose.ui.platform.LocalContext.current
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Show error snackbar (implement in parent)
            viewModel.clearError()
        }
    }

    // Next enabled alarm drives the hero card.
    val nextAlarm = remember(uiState.alarms) {
        uiState.alarms
            .filter { it.isEnabled }
            .minByOrNull { it.calculateNextTriggerTime() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Slim top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        androidx.compose.material.icons.Icons.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.alarm_back)
                    )
                }

                Text(
                    text = stringResource(R.string.nav_alarms),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                IconButton(onClick = { viewModel.openAddAlarmDialog() }) {
                    Icon(
                        androidx.compose.material.icons.Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.alarm_add_alarm),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Hero: next alarm at a glance
            val context = androidx.compose.ui.platform.LocalContext.current
            AlarmHeroCard(
                nextAlarm = nextAlarm,
                countdownText = uiState.timeUntilNextAlarm,
                isAlarmRinging = uiState.isAlarmRinging,
                onAddClick = { viewModel.openAddAlarmDialog() },
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
                    onToggleAlarm = {
                        if (!it.isEnabled && !viewModel.canScheduleExactAlarms()) {
                            showExactAlarmPermissionDialog = true
                        } else {
                            viewModel.toggleAlarm(it)
                        }
                    },
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
                onSave = {
                    if (viewModel.exactAlarmPermissionRequiredForSave &&
                        !viewModel.canScheduleExactAlarms()
                    ) {
                        showExactAlarmPermissionDialog = true
                    } else {
                        viewModel.saveAlarm()
                        com.vyllo.music.core.utils.BatteryOptimizationHelper.requestExemptionIfNeeded(screenContext)
                    }
                }
            )
        }

        if (showExactAlarmPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showExactAlarmPermissionDialog = false },
                title = { Text(stringResource(R.string.exact_alarm_permission_title)) },
                text = { Text(stringResource(R.string.exact_alarm_permission_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showExactAlarmPermissionDialog = false
                        viewModel.openExactAlarmSettings()
                    }) {
                        Text(stringResource(R.string.exact_alarm_permission_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExactAlarmPermissionDialog = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }

        if (uiState.showSoundPicker) {
            AlarmSoundPickerDialog(
                downloadedSongs = uiState.downloadedSongs,
                selectedSoundType = uiState.selectedSoundType,
                selectedSongUrl = viewModel.selectedSongUrl,
                onDismiss = { viewModel.closeSoundPicker() },
                onSelectSong = { url, title -> viewModel.selectSong(url, title) },
                onSelectDefault = { viewModel.selectDefaultSound() },
                onDeleteSong = { url -> viewModel.deleteDownloadedSong(url) }
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
 * Hero card: gradient panel with the next alarm time, live countdown and a
 * ringing-state stop action. Gives the screen a single focal point instead of
 * a flat list with a bare header.
 */
@Composable
private fun AlarmHeroCard(
    nextAlarm: AlarmModel?,
    countdownText: String,
    isAlarmRinging: Boolean,
    onAddClick: () -> Unit,
    onStopActiveAlarm: () -> Unit
) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.secondaryContainer
        )
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(20.dp)
        ) {
            if (isAlarmRinging) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.alarm_ringing),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.alarm_tap_to_stop),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                    Button(
                        onClick = onStopActiveAlarm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            stringResource(R.string.alarm_stop),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            } else if (nextAlarm != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.alarm_hero_next_up).uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = remember(nextAlarm) {
                                java.text.SimpleDateFormat(
                                    "h:mm a",
                                    java.util.Locale.getDefault()
                                ).format(java.util.Date(nextAlarm.calculateNextTriggerTime()))
                            },
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1
                        )
                        if (nextAlarm.label.isNotBlank()) {
                            Text(
                                text = nextAlarm.label,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (countdownText.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = countdownText,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    FilledIconButton(
                        onClick = onAddClick,
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.alarm_hero_add),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.alarm_hero_nothing),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.alarm_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                    FilledIconButton(
                        onClick = onAddClick,
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.alarm_hero_add),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
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
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Alarm,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.alarm_no_alarms),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.alarm_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.alarm_add_alarm))
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
    val downloadedSongs: List<com.vyllo.music.domain.model.DownloadEntity> = emptyList(),
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


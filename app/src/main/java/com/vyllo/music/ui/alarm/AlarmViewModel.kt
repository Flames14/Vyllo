package com.vyllo.music.ui.alarm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.os.Handler
import android.os.Looper
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.alarm.AlarmDao
import com.vyllo.music.data.download.DownloadDao
import com.vyllo.music.data.manager.AlarmSchedulerManager
import com.vyllo.music.domain.model.AlarmModel
import com.vyllo.music.domain.model.DayOfWeek
import com.vyllo.music.domain.model.SoundType
import com.vyllo.music.domain.repository.AlarmRepository
import com.vyllo.music.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the alarm screen.
 */
@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val getAllAlarmsUseCase: GetAllAlarmsUseCase,
    private val createAlarmUseCase: CreateAlarmUseCase,
    private val updateAlarmUseCase: UpdateAlarmUseCase,
    private val deleteAlarmUseCase: DeleteAlarmUseCase,
    private val toggleAlarmUseCase: ToggleAlarmUseCase,
    private val alarmScheduler: AlarmSchedulerManager,
    private val alarmRepository: AlarmRepository,
    private val downloadDao: DownloadDao
) : ViewModel() {

    // State
    var alarms by mutableStateOf<List<AlarmModel>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var showError by mutableStateOf<String?>(null)
        private set

    var showAddEditDialog by mutableStateOf(false)
        private set

    var editingAlarm by mutableStateOf<AlarmModel?>(null)
        private set

    var showSoundPicker by mutableStateOf(false)
        private set

    var showTimePicker by mutableStateOf(false)
        private set

    var selectedHour by mutableStateOf(7)
        private set

    var selectedMinute by mutableStateOf(0)
        private set

    var selectedLabel by mutableStateOf("")
        set

    var selectedRepeatDays by mutableStateOf<Set<DayOfWeek>>(emptySet())
        set

    var selectedSoundType by mutableStateOf(SoundType.DEFAULT)
        set

    var selectedSongUrl by mutableStateOf<String?>(null)
        set

    var selectedSongTitle by mutableStateOf<String?>(null)
        set

    var isGradualVolumeEnabled by mutableStateOf(true)
        set

    var isVibrationEnabled by mutableStateOf(true)
        set

    var volume by mutableStateOf(80)
        set

    // Downloaded songs for sound picker
    var downloadedSongs by mutableStateOf<List<com.vyllo.music.data.download.DownloadEntity>>(emptyList())
        private set

    // Countdown state
    var timeUntilNextAlarm by mutableStateOf<String>("")
        private set

    var isAlarmRinging by mutableStateOf(false)
        private set

    private val countdownHandler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            updateCountdown()
            isAlarmRinging = com.vyllo.music.service.AlarmTriggerService.isRunning
            countdownHandler.postDelayed(this, 1000)
        }
    }

    init {
        loadAlarms()
        observeDownloadedSongs()
        startCountdown()
    }

    /**
     * Dismiss the currently ringing alarm.
     * Used for emergency stop from UI.
     */
    fun dismissActiveAlarm(context: android.content.Context) {
        val intent = android.content.Intent(context, com.vyllo.music.service.AlarmTriggerService::class.java).apply {
            action = com.vyllo.music.service.AlarmTriggerService.ACTION_DISMISS
        }
        context.startService(intent)
    }

    /**
     * Observe downloaded songs for real-time updates.
     */
    private fun observeDownloadedSongs() {
        viewModelScope.launch {
            try {
                downloadDao.getAllDownloads().collect { downloads ->
                    downloadedSongs = downloads
                }
            } catch (e: Exception) {
                SecureLogger.e("AlarmVM", "Failed to observe downloads", e)
            }
        }
    }

    /**
     * Load all alarms from repository.
     */
    fun loadAlarms() {
        viewModelScope.launch {
            isLoading = true
            try {
                getAllAlarmsUseCase().collect { alarmList ->
                    alarms = alarmList
                    isLoading = false
                }
            } catch (e: Exception) {
                showError = "Failed to load alarms: ${e.message}"
                SecureLogger.e("AlarmVM", "Failed to load alarms", e)
                isLoading = false
            }
        }
    }

    /**
     * Open dialog to create new alarm.
     */
    fun openAddAlarmDialog() {
        editingAlarm = null
        resetForm()
        showAddEditDialog = true
    }

    /**
     * Open dialog to edit existing alarm.
     */
    fun openEditAlarmDialog(alarm: AlarmModel) {
        editingAlarm = alarm
        selectedHour = alarm.hour
        selectedMinute = alarm.minute
        selectedLabel = alarm.label
        selectedRepeatDays = alarm.repeatDays
        selectedSoundType = alarm.soundType
        selectedSongUrl = alarm.downloadedSongUrl
        selectedSongTitle = alarm.downloadedSongTitle
        isGradualVolumeEnabled = alarm.gradualVolume
        isVibrationEnabled = alarm.vibrationEnabled
        volume = alarm.volume
        showAddEditDialog = true
    }

    /**
     * Close add/edit dialog.
     */
    fun closeDialog() {
        showAddEditDialog = false
        editingAlarm = null
    }

    /**
     * Save alarm (create or update).
     */
    fun saveAlarm() {
        viewModelScope.launch {
            try {
                val alarm = AlarmModel(
                    id = editingAlarm?.id ?: 0L,
                    hour = selectedHour,
                    minute = selectedMinute,
                    label = selectedLabel,
                    repeatDays = selectedRepeatDays,
                    soundType = selectedSoundType,
                    downloadedSongUrl = selectedSongUrl,
                    downloadedSongTitle = selectedSongTitle,
                    gradualVolume = isGradualVolumeEnabled,
                    vibrationEnabled = isVibrationEnabled,
                    volume = volume
                )

                if (editingAlarm == null) {
                    // Create new alarm
                    val newId = createAlarmUseCase(alarm)
                    val newAlarm = alarm.copy(id = newId)
                    alarmScheduler.schedule(newAlarm)
                    SecureLogger.d("AlarmVM", "Created and scheduled alarm ID=$newId for ${selectedHour}:${selectedMinute}")
                } else {
                    // Update existing alarm
                    updateAlarmUseCase(alarm)
                    alarmScheduler.schedule(alarm)
                    SecureLogger.d("AlarmVM", "Updated and scheduled alarm ID=${alarm.id} for ${selectedHour}:${selectedMinute}")
                }

                // Update notification
                closeDialog()
                loadAlarms()
            } catch (e: Exception) {
                showError = "Failed to save alarm: ${e.message}"
                SecureLogger.e("AlarmVM", "Failed to save alarm", e)
            }
        }
    }

    /**
     * Delete an alarm.
     */
    fun deleteAlarm(alarm: AlarmModel) {
        viewModelScope.launch {
            try {
                alarmScheduler.cancel(alarm)
                deleteAlarmUseCase(alarm.id)
            } catch (e: Exception) {
                showError = "Failed to delete alarm: ${e.message}"
            }
        }
    }

    /**
     * Toggle alarm enabled state.
     */
    fun toggleAlarm(alarm: AlarmModel) {
        viewModelScope.launch {
            try {
                val newEnabledState = !alarm.isEnabled
                toggleAlarmUseCase(alarm.id, newEnabledState)
                
                val updatedAlarm = alarm.copy(isEnabled = newEnabledState)
                if (newEnabledState) {
                    alarmScheduler.schedule(updatedAlarm)
                    SecureLogger.d("AlarmVM", "Enabled alarm ID=${alarm.id}")
                } else {
                    alarmScheduler.cancel(updatedAlarm)
                    SecureLogger.d("AlarmVM", "Disabled alarm ID=${alarm.id}")
                }
            } catch (e: Exception) {
                showError = "Failed to toggle alarm: ${e.message}"
                SecureLogger.e("AlarmVM", "Failed to toggle alarm", e)
            }
        }
    }

    /**
     * Open sound picker dialog.
     */
    fun openSoundPicker() {
        showSoundPicker = true
    }

    /**
     * Close sound picker dialog.
     */
    fun closeSoundPicker() {
        showSoundPicker = false
    }

    /**
     * Select a downloaded song as alarm sound.
     */
    fun selectSong(url: String, title: String) {
        selectedSoundType = SoundType.DOWNLOADED_SONG
        selectedSongUrl = url
        selectedSongTitle = title
        showSoundPicker = false
    }

    /**
     * Select default alarm sound.
     */
    fun selectDefaultSound() {
        selectedSoundType = SoundType.DEFAULT
        selectedSongUrl = null
        selectedSongTitle = null
    }

    /**
     * Open time picker dialog.
     */
    fun openTimePicker() {
        showTimePicker = true
    }

    /**
     * Close time picker dialog.
     */
    fun closeTimePicker() {
        showTimePicker = false
    }

    /**
     * Set selected time.
     */
    fun setTime(hour: Int, minute: Int) {
        selectedHour = hour
        selectedMinute = minute
        showTimePicker = false
    }

    /**
     * Toggle repeat day selection.
     */
    fun toggleRepeatDay(day: DayOfWeek) {
        selectedRepeatDays = if (selectedRepeatDays.contains(day)) {
            selectedRepeatDays - day
        } else {
            selectedRepeatDays + day
        }
    }

    /**
     * Reset form to default values.
     */
    private fun resetForm() {
        selectedHour = 7
        selectedMinute = 0
        selectedLabel = ""
        selectedRepeatDays = emptySet()
        selectedSoundType = SoundType.DEFAULT
        selectedSongUrl = null
        selectedSongTitle = null
        isGradualVolumeEnabled = true
        isVibrationEnabled = true
        volume = 80
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        showError = null
    }

    /**
     * Start countdown timer to show time until next alarm.
     */
    fun startCountdown() {
        countdownHandler.post(countdownRunnable)
    }

    /**
     * Stop countdown timer.
     */
    fun stopCountdown() {
        countdownHandler.removeCallbacks(countdownRunnable)
    }

    /**
     * Update countdown display.
     */
    private fun updateCountdown() {
        val enabledAlarms = alarms.filter { it.isEnabled }
        if (enabledAlarms.isEmpty()) {
            timeUntilNextAlarm = ""
            return
        }

        // Find the absolute earliest next trigger time
        val nextAlarmTime = enabledAlarms.map { it.calculateNextTriggerTime() }.minOrNull()
        
        if (nextAlarmTime == null) {
            timeUntilNextAlarm = ""
            return
        }

        val now = System.currentTimeMillis()
        val millisUntil = nextAlarmTime - now
        
        if (millisUntil <= 0) {
            timeUntilNextAlarm = "Ringing now!"
            return
        }

        val hours = millisUntil / 3600000
        val minutes = (millisUntil % 3600000) / 60000
        val seconds = (millisUntil % 60000) / 1000

        timeUntilNextAlarm = when {
            hours > 0 -> "Next alarm in ${hours}h ${minutes}m"
            minutes > 0 -> "Next alarm in ${minutes}m ${seconds}s"
            else -> "Next alarm in ${seconds}s"
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopCountdown()
    }
}

package com.vyllo.music

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vyllo.music.data.manager.BackupRestoreManager
import com.vyllo.music.data.manager.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val backupRestoreManager: BackupRestoreManager,
    private val permissionHandler: com.vyllo.music.domain.manager.PermissionHandler
) : ViewModel() {

    var showSettings by mutableStateOf(false)
    var isFloatingEnabled by mutableStateOf(preferenceManager.isFloatingPlayerEnabled)
    var isBackgroundPlaybackEnabled by mutableStateOf(preferenceManager.isBackgroundPlaybackEnabled)
    var isBatteryUnrestricted by mutableStateOf(false)
        private set
    var isKeepAudioPlayingEnabled by mutableStateOf(preferenceManager.isKeepAudioPlayingEnabled)
    var themeMode by mutableStateOf(preferenceManager.themeMode)
    var isHighRefreshRateEnabled by mutableStateOf(preferenceManager.isHighRefreshRateEnabled)

    var backupStatusMessage by mutableStateOf<String?>(null)

    fun toggleFloatingPlayer(enabled: Boolean) {
        isFloatingEnabled = enabled
        preferenceManager.isFloatingPlayerEnabled = enabled
    }

    fun toggleBackgroundPlayback(enabled: Boolean) {
        isBackgroundPlaybackEnabled = enabled
        preferenceManager.isBackgroundPlaybackEnabled = enabled
    }

    fun refreshBatteryAccessState() {
        isBatteryUnrestricted = permissionHandler.isBatteryOptimizationDisabled()
    }

    fun requestBatteryUnrestricted(context: android.content.Context) {
        if (isBatteryUnrestricted) {
            refreshBatteryAccessState()
            return
        }
        val activity = context.findActivity()
        if (activity != null) permissionHandler.requestDisableBatteryOptimization(activity)
    }

    private tailrec fun android.content.Context.findActivity(): android.app.Activity? {
        return when (this) {
            is android.app.Activity -> this
            is android.content.ContextWrapper -> baseContext.findActivity()
            else -> null
        }
    }

    fun toggleKeepAudioPlaying(enabled: Boolean) {
        isKeepAudioPlayingEnabled = enabled
        preferenceManager.isKeepAudioPlayingEnabled = enabled
    }

    fun updateThemeMode(newTheme: String) {
        themeMode = newTheme
        preferenceManager.themeMode = newTheme
    }

    fun toggleHighRefreshRate(enabled: Boolean) {
        isHighRefreshRateEnabled = enabled
        preferenceManager.isHighRefreshRateEnabled = enabled
    }

    fun exportBackup(onExportReady: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val json = backupRestoreManager.exportBackupJson()
                onExportReady(json)
            } catch (e: Exception) {
                backupStatusMessage = "Export failed: ${e.message}"
            }
        }
    }

    fun importBackup(jsonString: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = backupRestoreManager.restoreBackupJson(jsonString)
            if (result.isSuccess) {
                // Refresh local ViewModel states from restored preferences
                themeMode = preferenceManager.themeMode
                isFloatingEnabled = preferenceManager.isFloatingPlayerEnabled
                isBackgroundPlaybackEnabled = preferenceManager.isBackgroundPlaybackEnabled
                isKeepAudioPlayingEnabled = preferenceManager.isKeepAudioPlayingEnabled
                isHighRefreshRateEnabled = preferenceManager.isHighRefreshRateEnabled
                onComplete(true)
            } else {
                onComplete(false)
            }
        }
    }
}

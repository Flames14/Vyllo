package com.vyllo.music

import androidx.compose.runtime.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vyllo.music.data.manager.BackupRestoreManager
import com.vyllo.music.data.manager.CacheJanitor
import com.vyllo.music.data.manager.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val backupRestoreManager: BackupRestoreManager,
    private val permissionHandler: com.vyllo.music.domain.manager.PermissionHandler,
    private val cacheJanitor: CacheJanitor
) : ViewModel() {

    var showSettings by mutableStateOf(false)
    var isFloatingEnabled by mutableStateOf(preferenceManager.isFloatingPlayerEnabled)
    var isBackgroundPlaybackEnabled by mutableStateOf(preferenceManager.isBackgroundPlaybackEnabled)
    var isBatteryUnrestricted by mutableStateOf(false)
        private set
    var isKeepAudioPlayingEnabled by mutableStateOf(preferenceManager.isKeepAudioPlayingEnabled)
    var themeMode by mutableStateOf(preferenceManager.themeMode)
    var languageTag by mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags())
    var isHighRefreshRateEnabled by mutableStateOf(preferenceManager.isHighRefreshRateEnabled)
    var isBlockScreenshotsEnabled by mutableStateOf(preferenceManager.isBlockScreenshotsEnabled)
    var wifiOnlyDownloads by mutableStateOf(preferenceManager.wifiOnlyDownloads)
    var downloadsRequireBatteryOk by mutableStateOf(preferenceManager.downloadsRequireBatteryOk)
    var crossfadeSeconds by mutableStateOf(preferenceManager.crossfadeSeconds)
    var loudnessPreset by mutableStateOf(preferenceManager.loudnessPreset)

    fun updateLoudnessPreset(preset: String) {
        preferenceManager.loudnessPreset = preset
        loudnessPreset = preferenceManager.loudnessPreset
    }

    fun updateCrossfadeSeconds(seconds: Int) {
        preferenceManager.crossfadeSeconds = seconds
        // Re-read so the UI shows the clamped value, never the raw request.
        crossfadeSeconds = preferenceManager.crossfadeSeconds
    }

    var backupStatusMessage by mutableStateOf<String?>(null)

    var cacheMediaBytes by mutableStateOf(0L)
        private set
    var cacheImageBytes by mutableStateOf(0L)
        private set
    var cacheTotalBytes by mutableStateOf(0L)
        private set
    var isClearingCache by mutableStateOf(false)
        private set

    fun refreshStorageUsage() {
        viewModelScope.launch {
            val usage = cacheJanitor.usage()
            cacheMediaBytes = usage.mediaBytes
            cacheImageBytes = usage.imageBytes
            cacheTotalBytes = usage.totalBytes
        }
    }

    fun clearCache() {
        if (isClearingCache) return
        isClearingCache = true
        viewModelScope.launch {
            runCatching { cacheJanitor.clearAll() }
            val usage = cacheJanitor.usage()
            cacheMediaBytes = usage.mediaBytes
            cacheImageBytes = usage.imageBytes
            cacheTotalBytes = usage.totalBytes
            isClearingCache = false
        }
    }

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

    fun updateLanguage(tag: String) {
        languageTag = tag
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    fun toggleHighRefreshRate(enabled: Boolean) {
        isHighRefreshRateEnabled = enabled
        preferenceManager.isHighRefreshRateEnabled = enabled
    }

    fun toggleBlockScreenshots(enabled: Boolean) {
        isBlockScreenshotsEnabled = enabled
        preferenceManager.isBlockScreenshotsEnabled = enabled
    }

    fun toggleWifiOnlyDownloads(enabled: Boolean) {
        wifiOnlyDownloads = enabled
        preferenceManager.wifiOnlyDownloads = enabled
    }

    fun toggleDownloadsRequireBatteryOk(enabled: Boolean) {
        downloadsRequireBatteryOk = enabled
        preferenceManager.downloadsRequireBatteryOk = enabled
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
                isBlockScreenshotsEnabled = preferenceManager.isBlockScreenshotsEnabled
                crossfadeSeconds = preferenceManager.crossfadeSeconds
                loudnessPreset = preferenceManager.loudnessPreset
                onComplete(true)
            } else {
                onComplete(false)
            }
        }
    }
}

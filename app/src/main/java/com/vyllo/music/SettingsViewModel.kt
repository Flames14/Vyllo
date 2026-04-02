package com.vyllo.music

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.vyllo.music.data.manager.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    var showSettings by mutableStateOf(false)
    var isFloatingEnabled by mutableStateOf(preferenceManager.isFloatingPlayerEnabled)
    var isBackgroundPlaybackEnabled by mutableStateOf(preferenceManager.isBackgroundPlaybackEnabled)
    var isKeepAudioPlayingEnabled by mutableStateOf(preferenceManager.isKeepAudioPlayingEnabled)
    var themeMode by mutableStateOf(preferenceManager.themeMode)
    var isLiquidScrollEnabled by mutableStateOf(preferenceManager.isLiquidScrollEnabled)

    fun toggleFloatingPlayer(enabled: Boolean) {
        isFloatingEnabled = enabled
        preferenceManager.isFloatingPlayerEnabled = enabled
    }

    fun toggleBackgroundPlayback(enabled: Boolean) {
        isBackgroundPlaybackEnabled = enabled
        preferenceManager.isBackgroundPlaybackEnabled = enabled
    }

    fun toggleKeepAudioPlaying(enabled: Boolean) {
        isKeepAudioPlayingEnabled = enabled
        preferenceManager.isKeepAudioPlayingEnabled = enabled
    }

    fun updateThemeMode(newTheme: String) {
        themeMode = newTheme
        preferenceManager.themeMode = newTheme
    }

    fun toggleLiquidScroll(enabled: Boolean) {
        isLiquidScrollEnabled = enabled
        preferenceManager.isLiquidScrollEnabled = enabled
    }
}

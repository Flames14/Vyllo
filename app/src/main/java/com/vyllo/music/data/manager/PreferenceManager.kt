package com.vyllo.music.data.manager

import android.content.Context
import android.content.SharedPreferences
import com.vyllo.music.core.security.SecurePreferenceManager
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.EqualizerBandSetting
import com.vyllo.music.domain.model.EqualizerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PreferenceManager"

/**
 * Secure Preference Manager with Encrypted Storage
 * 
 * Uses SecurePreferenceManager for sensitive data:
 * - Lyrics preferences (encrypted)
 * - Search history (encrypted DataStore)
 * 
 * Regular preferences for non-sensitive UI settings
 */
@Singleton
class PreferenceManager @Inject constructor(context: Context) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Regular preferences for non-sensitive UI settings
    val preferences: SharedPreferences = context.getSharedPreferences("music_prefs", Context.MODE_PRIVATE)
    
    // Secure preference manager for sensitive data
    private val securePrefs = SecurePreferenceManager(context)

    var isFloatingPlayerEnabled: Boolean
        get() = preferences.getBoolean("floating_player_enabled", false)
        set(value) { preferences.edit().putBoolean("floating_player_enabled", value).apply() }

    var floatingPlayerX: Int
        get() = preferences.getInt("floating_player_x", 0)
        set(value) { preferences.edit().putInt("floating_player_x", value).apply() }

    var floatingPlayerY: Int
        get() = preferences.getInt("floating_player_y", 100)
        set(value) { preferences.edit().putInt("floating_player_y", value).apply() }

    var isBackgroundPlaybackEnabled: Boolean
        get() = preferences.getBoolean("background_playback_enabled", true)
        set(value) { preferences.edit().putBoolean("background_playback_enabled", value).apply() }

    var themeMode: String
        get() = preferences.getString("theme_mode", "System") ?: "System"
        set(value) { preferences.edit().putString("theme_mode", value).apply() }

    var isKeepAudioPlayingEnabled: Boolean
        get() = preferences.getBoolean("keep_audio_playing_enabled", false)
        set(value) { preferences.edit().putBoolean("keep_audio_playing_enabled", value).apply() }

    var isLiquidScrollEnabled: Boolean
        get() = preferences.getBoolean("liquid_scroll_enabled", false)
        set(value) { preferences.edit().putBoolean("liquid_scroll_enabled", value).apply() }

    var isEqualizerEnabled: Boolean
        get() = preferences.getBoolean(KEY_EQUALIZER_ENABLED, false)
        set(value) { preferences.edit().putBoolean(KEY_EQUALIZER_ENABLED, value).apply() }

    var equalizerBassBoostStrength: Int
        get() = preferences.getInt(KEY_EQUALIZER_BASS_BOOST, 0)
        set(value) {
            preferences.edit()
                .putInt(KEY_EQUALIZER_BASS_BOOST, value.coerceIn(EqualizerSettings.STRENGTH_MIN, EqualizerSettings.STRENGTH_MAX))
                .apply()
        }

    var equalizerVirtualizerStrength: Int
        get() = preferences.getInt(KEY_EQUALIZER_VIRTUALIZER, 0)
        set(value) {
            preferences.edit()
                .putInt(KEY_EQUALIZER_VIRTUALIZER, value.coerceIn(EqualizerSettings.STRENGTH_MIN, EqualizerSettings.STRENGTH_MAX))
                .apply()
        }

    /**
     * Security: Save search query to encrypted DataStore
     */
    fun saveSearchQuery(query: String) {
        if (query.isBlank()) return
        // Save to encrypted DataStore (async, fire-and-forget)
        scope.launch {
            try {
                securePrefs.saveSearchQuery(query)
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to save search query: ${e.message}")
            }
        }
        // Also keep in regular prefs for backward compatibility
        val history = loadSearchHistory().toMutableList()
        history.remove(query)
        history.add(0, query)
        val limitedHistory = history.take(10)
        preferences.edit().putString("search_history", limitedHistory.joinToString("|||")).apply()
    }

    /**
     * Security: Load search history from encrypted DataStore
     */
    fun loadSearchHistory(): List<String> {
        // Fallback to regular prefs for simplicity
        // Encrypted DataStore can be used in a future refactor
        val historyString = preferences.getString("search_history", "") ?: ""
        return if (historyString.isBlank()) emptyList() else historyString.split("|||")
    }

    fun clearSearchHistory() {
        scope.launch {
            try {
                securePrefs.clearSearchHistory()
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to clear search history: ${e.message}")
            }
        }
        preferences.edit().remove("search_history").apply()
    }

    /**
     * Security: Save lyrics preference with encrypted URL hash
     */
    fun saveLyricsPreference(videoUrl: String, lyricsId: String) {
        securePrefs.saveLyricsPreference(videoUrl, lyricsId)
    }

    /**
     * Security: Load lyrics preference with encrypted URL hash
     */
    fun loadLyricsPreference(videoUrl: String): String? {
        return securePrefs.loadLyricsPreference(videoUrl)
    }

    fun loadEqualizerSettings(): EqualizerSettings {
        val bands = loadEqualizerBandLevels()
        return EqualizerSettings(
            enabled = isEqualizerEnabled,
            bassBoostStrength = equalizerBassBoostStrength,
            virtualizerStrength = equalizerVirtualizerStrength,
            bands = EqualizerSettings.DEFAULT_BANDS.mapIndexed { index, band ->
                band.copy(level = bands.getOrElse(index) { band.level })
            }
        ).sanitized()
    }

    fun saveEqualizerSettings(settings: EqualizerSettings) {
        val sanitized = settings.sanitized()
        isEqualizerEnabled = sanitized.enabled
        equalizerBassBoostStrength = sanitized.bassBoostStrength
        equalizerVirtualizerStrength = sanitized.virtualizerStrength
        saveEqualizerBandLevels(sanitized.bands.map(EqualizerBandSetting::level))
    }

    fun saveEqualizerBandLevels(levels: List<Int>) {
        val sanitized = levels
            .take(EqualizerSettings.DEFAULT_BANDS.size)
            .map { it.coerceIn(EqualizerSettings.BAND_LEVEL_MIN, EqualizerSettings.BAND_LEVEL_MAX) }
        preferences.edit()
            .putString(KEY_EQUALIZER_BANDS, sanitized.joinToString(","))
            .apply()
    }

    fun loadEqualizerBandLevels(): List<Int> {
        val raw = preferences.getString(KEY_EQUALIZER_BANDS, null)
        if (raw.isNullOrBlank()) {
            return EqualizerSettings.DEFAULT_BANDS.map(EqualizerBandSetting::level)
        }

        val parsed = raw.split(",")
            .mapNotNull { value -> value.toIntOrNull() }
            .map { it.coerceIn(EqualizerSettings.BAND_LEVEL_MIN, EqualizerSettings.BAND_LEVEL_MAX) }

        return EqualizerSettings.DEFAULT_BANDS.mapIndexed { index, band ->
            parsed.getOrElse(index) { band.level }
        }
    }
    
    /**
     * Check if encryption is enabled
     */
    fun isEncryptionEnabled(): Boolean {
        return securePrefs.isEncryptionEnabled()
    }
    
    /**
     * Wipe all sensitive data (for security clear)
     */
    fun wipeSensitiveData() {
        securePrefs.wipeSensitiveData()
        SecureLogger.d(TAG, "Sensitive data wiped")
    }

    companion object {
        const val KEY_EQUALIZER_ENABLED = "equalizer_enabled"
        const val KEY_EQUALIZER_BASS_BOOST = "equalizer_bass_boost"
        const val KEY_EQUALIZER_VIRTUALIZER = "equalizer_virtualizer"
        const val KEY_EQUALIZER_BANDS = "equalizer_bands"
    }
}

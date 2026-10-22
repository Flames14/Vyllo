package com.vyllo.music.data.manager

import android.content.Context
import android.content.SharedPreferences
import com.vyllo.music.core.security.SecurePreferenceManager
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.EqualizerBandSetting
import com.vyllo.music.domain.model.EqualizerSettings
import com.vyllo.music.domain.model.SleepSchedule
import com.vyllo.music.domain.repository.PlayerPreferences
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
class PreferenceManager @Inject constructor(
    context: Context,
    private val securePrefs: SecurePreferenceManager
) : PlayerPreferences {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Regular preferences for non-sensitive UI settings (private - use typed accessors).
    private val preferences: SharedPreferences = context.getSharedPreferences("music_prefs", Context.MODE_PRIVATE)

    override var isFloatingPlayerEnabled: Boolean
        get() = preferences.getBoolean("floating_player_enabled", false)
        set(value) { preferences.edit().putBoolean("floating_player_enabled", value).apply() }

    override var floatingPlayerX: Int
        get() = preferences.getInt("floating_player_x", 0)
        set(value) { preferences.edit().putInt("floating_player_x", value).apply() }

    override var floatingPlayerY: Int
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

    var isHighRefreshRateEnabled: Boolean
        get() = preferences.getBoolean("high_refresh_rate_enabled", true)
        set(value) { preferences.edit().putBoolean("high_refresh_rate_enabled", value).apply() }

    /**
     * Blocks screenshots and screen recording of the app window (and hides the
     * app from the recents thumbnail) via FLAG_SECURE. Off by default so it
     * never surprises a user who expects to share what they see.
     */
    var isBlockScreenshotsEnabled: Boolean
        get() = preferences.getBoolean("block_screenshots_enabled", false)
        set(value) { preferences.edit().putBoolean("block_screenshots_enabled", value).apply() }

    var homeScrollPrefetchDistancePx: Int
        get() = preferences.getInt("home_scroll_prefetch_px", 1200)
        set(value) { preferences.edit().putInt("home_scroll_prefetch_px", value.coerceIn(600, 2400)).apply() }

    var homeImagePreloadAheadItems: Int
        get() = preferences.getInt("home_image_preload_ahead", 16)
        set(value) { preferences.edit().putInt("home_image_preload_ahead", value.coerceIn(8, 48)).apply() }

    var isQueueSticky: Boolean
        get() = preferences.getBoolean("queue_sticky_enabled", true)
        set(value) { preferences.edit().putBoolean("queue_sticky_enabled", value).apply() }

    var isAutoplayEnabled: Boolean
        get() = preferences.getBoolean("autoplay_enabled", true)
        set(value) { preferences.edit().putBoolean("autoplay_enabled", value).apply() }

    /**
     * When false (default), Up Next and the related sections drop live/event takes
     * and non-song uploads (covers, karaoke, instrumentals) so a studio track never
     * queues a concert recording. Enabled = keep them.
     */
    var upNextIncludeVariants: Boolean
        get() = preferences.getBoolean("upnext_include_variants", false)
        set(value) { preferences.edit().putBoolean("upnext_include_variants", value).apply() }

    /** Locally pinned playlist ids, kept in user-selected order. */
    var pinnedPlaylistIds: List<Long>
        get() = preferences.getString("pinned_playlist_ids", null)
            .orEmpty()
            .split(',')
            .mapNotNull { it.toLongOrNull() }
            .distinct()
        set(value) {
            preferences.edit()
                .putString("pinned_playlist_ids", value.distinct().joinToString(","))
                .apply()
        }

    var batteryRationaleShown: Boolean
        get() = preferences.getBoolean("battery_rationale_shown", false)
        set(value) { preferences.edit().putBoolean("battery_rationale_shown", value).apply() }

    var wifiOnlyDownloads: Boolean
        get() = preferences.getBoolean("wifi_only_downloads", false)
        set(value) { preferences.edit().putBoolean("wifi_only_downloads", value).apply() }

    var downloadsRequireBatteryOk: Boolean
        get() = preferences.getBoolean("downloads_require_battery_ok", false)
        set(value) { preferences.edit().putBoolean("downloads_require_battery_ok", value).apply() }

    override var volumeBoostMultiplier: Float
        get() = preferences.getFloat(KEY_VOLUME_BOOST, 1.0f)
        set(value) { preferences.edit().putFloat(KEY_VOLUME_BOOST, value.coerceIn(1.0f, 3.0f)).apply() }

    /** Recurring pause schedule; encoded once so the whole value is atomic. */
    var sleepSchedule: SleepSchedule
        get() = SleepSchedule.decode(preferences.getString(KEY_SLEEP_SCHEDULE, null))
        set(value) {
            preferences.edit().putString(KEY_SLEEP_SCHEDULE, SleepSchedule.encode(value)).apply()
        }

    /** Epoch day on which the schedule last paused playback (0 = never). */
    var sleepScheduleLastFiredDay: Long
        get() = preferences.getLong(KEY_SLEEP_SCHEDULE_FIRED_DAY, 0L)
        set(value) {
            preferences.edit().putLong(KEY_SLEEP_SCHEDULE_FIRED_DAY, value).apply()
        }

    /** Skip silent passages (ExoPlayer skip-silence) inside a track. */
    var skipSilence: Boolean
        get() = preferences.getBoolean(KEY_SKIP_SILENCE, false)
        set(value) { preferences.edit().putBoolean(KEY_SKIP_SILENCE, value).apply() }

    /** Pause when the active output device disconnects (headphones, BT). */
    var pauseOnDeviceDisconnect: Boolean
        get() = preferences.getBoolean(KEY_PAUSE_ON_DISCONNECT, true)
        set(value) { preferences.edit().putBoolean(KEY_PAUSE_ON_DISCONNECT, value).apply() }

    /** Resume after a disconnect once an output device comes back. */
    var resumeOnDeviceReconnect: Boolean
        get() = preferences.getBoolean(KEY_RESUME_ON_RECONNECT, false)
        set(value) { preferences.edit().putBoolean(KEY_RESUME_ON_RECONNECT, value).apply() }

    /** Pause instead of playing silently when the media stream is muted. */
    var pauseWhenMuted: Boolean
        get() = preferences.getBoolean(KEY_PAUSE_WHEN_MUTED, false)
        set(value) { preferences.edit().putBoolean(KEY_PAUSE_WHEN_MUTED, value).apply() }

    /** Let the hardware decoder render into the offload path when available. */
    var audioOffloadEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUDIO_OFFLOAD, false)
        set(value) { preferences.edit().putBoolean(KEY_AUDIO_OFFLOAD, value).apply() }

    /** Audio stream quality ceiling: Auto/High/Medium/Low. */
    var streamQuality: String
        get() = preferences.getString(KEY_STREAM_QUALITY, "Auto") ?: "Auto"
        set(value) {
            preferences.edit().putString(KEY_STREAM_QUALITY, value).apply()
        }

    /**
     * Quality ceiling for downloaded files. Separate from [streamQuality]: a
     * download is permanent, so it is worth keeping it independent from a
     * bandwidth-saving playback choice.
     */
    var downloadQuality: String
        get() = preferences.getString(KEY_DOWNLOAD_QUALITY, "High") ?: "High"
        set(value) { preferences.edit().putString(KEY_DOWNLOAD_QUALITY, value).apply() }

    /** Loudness normalization preset: Off/Aggressive/Loud/Balanced/Quiet. */    var loudnessPreset: String
        get() = preferences.getString(KEY_LOUDNESS_PRESET, "Off") ?: "Off"
        set(value) {
            preferences.edit().putString(KEY_LOUDNESS_PRESET, value).apply()
        }

    /** Crossfade window in seconds; 0 disables fades and keeps gapless intact. */
    var crossfadeSeconds: Int
        get() = preferences.getInt(KEY_CROSSFADE_SECONDS, 0)
        set(value) {
            preferences.edit()
                .putInt(KEY_CROSSFADE_SECONDS, value.coerceIn(0, 12))
                .apply()
        }

    var isEqualizerEnabled: Boolean
        get() = preferences.getBoolean(KEY_EQUALIZER_ENABLED, false)
        set(value) { preferences.edit().putBoolean(KEY_EQUALIZER_ENABLED, value).apply() }

    /**
     * Playback rate applied by [com.vyllo.music.service.MusicService]. Stored so
     * the choice survives restarts and applies to whatever starts playing next.
     */
    var playbackSpeed: Float
        get() = preferences.getFloat(KEY_PLAYBACK_SPEED, 1.0f)
        set(value) {
            preferences.edit()
                .putFloat(KEY_PLAYBACK_SPEED, value.coerceIn(SPEED_MIN, SPEED_MAX))
                .apply()
        }

    /** Pitch shift, independent of speed (1.0f = untouched). */
    var playbackPitch: Float
        get() = preferences.getFloat(KEY_PLAYBACK_PITCH, 1.0f)
        set(value) {
            preferences.edit()
                .putFloat(KEY_PLAYBACK_PITCH, value.coerceIn(PITCH_MIN, PITCH_MAX))
                .apply()
        }

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

    private val memorySearchHistory = java.util.Collections.synchronizedList(mutableListOf<String>())

    init {
        // Pre-populate in-memory search history from encrypted DataStore
        scope.launch {
            try {
                securePrefs.getSearchHistory().collect { historyList ->
                    synchronized(memorySearchHistory) {
                        memorySearchHistory.clear()
                        memorySearchHistory.addAll(historyList)
                    }
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to load encrypted search history: ${e.message}")
            }
        }
        // Cleanup any legacy plaintext search_history from regular preferences
        if (preferences.contains("search_history")) {
            val legacy = preferences.getString("search_history", null)
            if (!legacy.isNullOrBlank()) {
                legacy.split("|||").forEach { query ->
                    if (query.isNotBlank()) {
                        scope.launch { securePrefs.saveSearchQuery(query) }
                    }
                }
            }
            preferences.edit().remove("search_history").apply()
        }
    }

    /**
     * Security: Save search query to encrypted DataStore (Zero plaintext leakage)
     */
    fun saveSearchQuery(query: String) {
        if (query.isBlank()) return
        synchronized(memorySearchHistory) {
            memorySearchHistory.remove(query)
            memorySearchHistory.add(0, query)
            if (memorySearchHistory.size > 10) {
                memorySearchHistory.removeAt(memorySearchHistory.size - 1)
            }
        }
        scope.launch {
            try {
                securePrefs.saveSearchQuery(query)
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to save search query: ${e.message}")
            }
        }
    }

    /**
     * Security: Load search history from encrypted DataStore cache
     */
    fun loadSearchHistory(): List<String> {
        return synchronized(memorySearchHistory) {
            memorySearchHistory.toList()
        }
    }

    fun clearSearchHistory() {
        synchronized(memorySearchHistory) {
            memorySearchHistory.clear()
        }
        scope.launch {
            try {
                securePrefs.clearSearchHistory()
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to clear search history: ${e.message}")
            }
        }
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

    override fun saveEqualizerSettings(settings: EqualizerSettings) {
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

    // --- Typed artist-affinity accessors (replaces raw "artist_" key access) ---
    fun recordArtistListen(artist: String) {
        if (artist.isBlank()) return
        val key = "artist_$artist"
        val current = preferences.getInt(key, 0)
        preferences.edit().putInt(key, current + 1).apply()
    }

    fun getTopArtists(limit: Int = 10): List<String> {
        return preferences.all
            .filterKeys { it.startsWith("artist_") }
            .mapNotNull { (key, value) -> (value as? Int)?.let { key to it } }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first.removePrefix("artist_") }
    }

    // --- Encapsulated SharedPreferences access (no raw prefs exposure) ---
    fun registerOnPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun saveQueue(items: List<com.vyllo.music.domain.model.MusicItem>, index: Int) {
        com.vyllo.music.data.manager.QueuePersistence.save(preferences, items, index)
    }

    fun restoreQueue(): Pair<List<com.vyllo.music.domain.model.MusicItem>, Int>? {
        return com.vyllo.music.data.manager.QueuePersistence.restore(preferences)
    }

    // --- Playhead (mid-track resume after process death) ---

    /** Persists the playhead for [url]. Blank URLs and negative positions are ignored. */
    fun savePlaybackPosition(url: String?, positionMs: Long) {
        if (url.isNullOrBlank() || positionMs < 0L) return
        preferences.edit()
            .putString(KEY_PLAYBACK_POSITION_URL, url)
            .putLong(KEY_PLAYBACK_POSITION_MS, positionMs)
            .apply()
    }

    /**
     * Saved position for [url], or null when the last session was on a different
     * track or sits at the very start (nothing worth resuming).
     */
    fun savedPlaybackPosition(url: String): Long? {
        val savedUrl = preferences.getString(KEY_PLAYBACK_POSITION_URL, null)
        if (savedUrl != url) return null
        return preferences.getLong(KEY_PLAYBACK_POSITION_MS, 0L).takeIf { it > 0L }
    }

    fun clearPlaybackPosition() {
        preferences.edit()
            .remove(KEY_PLAYBACK_POSITION_URL)
            .remove(KEY_PLAYBACK_POSITION_MS)
            .apply()
    }

    companion object {
        const val KEY_EQUALIZER_ENABLED = "equalizer_enabled"
        const val KEY_EQUALIZER_BASS_BOOST = "equalizer_bass_boost"
        const val KEY_EQUALIZER_VIRTUALIZER = "equalizer_virtualizer"
        const val KEY_EQUALIZER_BANDS = "equalizer_bands"
        const val KEY_VOLUME_BOOST = "volume_boost_multiplier"
        const val KEY_PLAYBACK_SPEED = "playback_speed"
        const val KEY_PLAYBACK_PITCH = "playback_pitch"
        const val KEY_CROSSFADE_SECONDS = "crossfade_seconds"
        const val KEY_LOUDNESS_PRESET = "loudness_preset"
        const val KEY_STREAM_QUALITY = "stream_quality"
        const val KEY_DOWNLOAD_QUALITY = "download_quality"
        const val KEY_SLEEP_SCHEDULE = "sleep_schedule"
        const val KEY_SLEEP_SCHEDULE_FIRED_DAY = "sleep_schedule_fired_day"
        const val KEY_SKIP_SILENCE = "skip_silence"
        const val KEY_PAUSE_ON_DISCONNECT = "pause_on_device_disconnect"
        const val KEY_RESUME_ON_RECONNECT = "resume_on_device_reconnect"
        const val KEY_PAUSE_WHEN_MUTED = "pause_when_muted"
        const val KEY_AUDIO_OFFLOAD = "audio_offload"
        const val SPEED_MIN = 0.5f
        const val SPEED_MAX = 2.0f
        const val PITCH_MIN = 0.5f
        const val PITCH_MAX = 2.0f
        const val KEY_PLAYBACK_POSITION_URL = "playback_position_url"
        const val KEY_PLAYBACK_POSITION_MS = "playback_position_ms"
    }
}

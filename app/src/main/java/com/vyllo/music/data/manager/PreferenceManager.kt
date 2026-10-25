package com.vyllo.music.data.manager

import androidx.core.content.edit

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
import kotlinx.coroutines.flow.first
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
        set(value) { preferences.edit {putBoolean("floating_player_enabled", value)} }

    override var floatingPlayerX: Int
        get() = preferences.getInt("floating_player_x", 0)
        set(value) { preferences.edit {putInt("floating_player_x", value)} }

    override var floatingPlayerY: Int
        get() = preferences.getInt("floating_player_y", 100)
        set(value) { preferences.edit {putInt("floating_player_y", value)} }

    var isBackgroundPlaybackEnabled: Boolean
        get() = preferences.getBoolean("background_playback_enabled", true)
        set(value) { preferences.edit {putBoolean("background_playback_enabled", value)} }

    var themeMode: String
        get() = preferences.getString("theme_mode", "System") ?: "System"
        set(value) { preferences.edit {putString("theme_mode", value)} }

    var isKeepAudioPlayingEnabled: Boolean
        get() = preferences.getBoolean("keep_audio_playing_enabled", false)
        set(value) { preferences.edit {putBoolean("keep_audio_playing_enabled", value)} }

    var isHighRefreshRateEnabled: Boolean
        get() = preferences.getBoolean("high_refresh_rate_enabled", true)
        set(value) { preferences.edit {putBoolean("high_refresh_rate_enabled", value)} }

    /**
     * Blocks screenshots and screen recording of the app window (and hides the
     * app from the recents thumbnail) via FLAG_SECURE. Off by default so it
     * never surprises a user who expects to share what they see.
     */
    var isBlockScreenshotsEnabled: Boolean
        get() = preferences.getBoolean("block_screenshots_enabled", false)
        set(value) { preferences.edit {putBoolean("block_screenshots_enabled", value)} }

    /**
     * Keeps Android's navigation bar on screen when true. When false (default,
     * the app's previous behaviour) the bar is hidden in immersive mode and a
     * swipe from the bottom edge reveals it temporarily.
     */
    var isSystemNavigationBarVisible: Boolean
        get() = preferences.getBoolean("system_navigation_bar_visible", false)
        set(value) { preferences.edit {putBoolean("system_navigation_bar_visible", value)} }

    var homeScrollPrefetchDistancePx: Int
        get() = preferences.getInt("home_scroll_prefetch_px", 1200)
        set(value) { preferences.edit {putInt("home_scroll_prefetch_px", value.coerceIn(600, 2400))} }

    var homeImagePreloadAheadItems: Int
        get() = preferences.getInt("home_image_preload_ahead", 16)
        set(value) { preferences.edit {putInt("home_image_preload_ahead", value.coerceIn(8, 48))} }

    var isQueueSticky: Boolean
        get() = preferences.getBoolean("queue_sticky_enabled", true)
        set(value) { preferences.edit {putBoolean("queue_sticky_enabled", value)} }

    var isAutoplayEnabled: Boolean
        get() = preferences.getBoolean("autoplay_enabled", true)
        set(value) { preferences.edit {putBoolean("autoplay_enabled", value)} }

    /**
     * When false (default), Up Next and the related sections drop live/event takes
     * and non-song uploads (covers, karaoke, instrumentals) so a studio track never
     * queues a concert recording. Enabled = keep them.
     */
    var upNextIncludeVariants: Boolean
        get() = preferences.getBoolean("upnext_include_variants", false)
        set(value) { preferences.edit {putBoolean("upnext_include_variants", value)} }

    /** Locally pinned playlist ids, kept in user-selected order. */
    var pinnedPlaylistIds: List<Long>
        get() = preferences.getString("pinned_playlist_ids", null)
            .orEmpty()
            .split(',')
            .mapNotNull { it.toLongOrNull() }
            .distinct()
        set(value) {
            preferences.edit {
                    putString("pinned_playlist_ids", value.distinct().joinToString(","))
                }
        }

    var batteryRationaleShown: Boolean
        get() = preferences.getBoolean("battery_rationale_shown", false)
        set(value) { preferences.edit {putBoolean("battery_rationale_shown", value)} }

    var wifiOnlyDownloads: Boolean
        get() = preferences.getBoolean("wifi_only_downloads", false)
        set(value) { preferences.edit {putBoolean("wifi_only_downloads", value)} }

    var downloadsRequireBatteryOk: Boolean
        get() = preferences.getBoolean("downloads_require_battery_ok", false)
        set(value) { preferences.edit {putBoolean("downloads_require_battery_ok", value)} }

    override var volumeBoostMultiplier: Float
        get() = preferences.getFloat(KEY_VOLUME_BOOST, 1.0f)
        set(value) { preferences.edit {putFloat(KEY_VOLUME_BOOST, value.coerceIn(1.0f, 3.0f))} }

    /** Recurring pause schedule; encoded once so the whole value is atomic. */
    var sleepSchedule: SleepSchedule
        get() = SleepSchedule.decode(preferences.getString(KEY_SLEEP_SCHEDULE, null))
        set(value) {
            preferences.edit {putString(KEY_SLEEP_SCHEDULE, SleepSchedule.encode(value))}
        }

    /** Epoch day on which the schedule last paused playback (0 = never). */
    var sleepScheduleLastFiredDay: Long
        get() = preferences.getLong(KEY_SLEEP_SCHEDULE_FIRED_DAY, 0L)
        set(value) {
            preferences.edit {putLong(KEY_SLEEP_SCHEDULE_FIRED_DAY, value)}
        }

    /** Skip silent passages (ExoPlayer skip-silence) inside a track. */
    var skipSilence: Boolean
        get() = preferences.getBoolean(KEY_SKIP_SILENCE, false)
        set(value) { preferences.edit {putBoolean(KEY_SKIP_SILENCE, value)} }

    /** Pause when the active output device disconnects (headphones, BT). */
    var pauseOnDeviceDisconnect: Boolean
        get() = preferences.getBoolean(KEY_PAUSE_ON_DISCONNECT, true)
        set(value) { preferences.edit {putBoolean(KEY_PAUSE_ON_DISCONNECT, value)} }

    /** Resume after a disconnect once an output device comes back. */
    var resumeOnDeviceReconnect: Boolean
        get() = preferences.getBoolean(KEY_RESUME_ON_RECONNECT, false)
        set(value) { preferences.edit {putBoolean(KEY_RESUME_ON_RECONNECT, value)} }

    /** Pause instead of playing silently when the media stream is muted. */
    var pauseWhenMuted: Boolean
        get() = preferences.getBoolean(KEY_PAUSE_WHEN_MUTED, false)
        set(value) { preferences.edit {putBoolean(KEY_PAUSE_WHEN_MUTED, value)} }

    /** Let the hardware decoder render into the offload path when available. */
    var audioOffloadEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUDIO_OFFLOAD, false)
        set(value) { preferences.edit {putBoolean(KEY_AUDIO_OFFLOAD, value)} }

    /** Audio stream quality ceiling: Auto/High/Medium/Low. */
    var streamQuality: String
        get() = preferences.getString(KEY_STREAM_QUALITY, "Auto") ?: "Auto"
        set(value) {
            preferences.edit {putString(KEY_STREAM_QUALITY, value)}
        }

    /**
     * Quality ceiling for downloaded files. Separate from [streamQuality]: a
     * download is permanent, so it is worth keeping it independent from a
     * bandwidth-saving playback choice.
     */
    var downloadQuality: String
        get() = preferences.getString(KEY_DOWNLOAD_QUALITY, "High") ?: "High"
        set(value) { preferences.edit {putString(KEY_DOWNLOAD_QUALITY, value)} }

    /**
     * Comma-separated [com.vyllo.music.domain.model.LyricsSource] keys, most
     * preferred first. Encoded rather than ordinal so reordering the enum never
     * reshuffles an existing user's chain.
     */
    var lyricsSourceOrder: String
        get() = preferences.getString(
            KEY_LYRICS_SOURCE_ORDER,
            com.vyllo.music.domain.model.LyricsSource.encode(
                com.vyllo.music.domain.model.LyricsSource.DEFAULT_ORDER
            )
        ) ?: ""
        set(value) { preferences.edit {putString(KEY_LYRICS_SOURCE_ORDER, value)} }

    /** Lyrics typography, read once per player session. */
    fun readLyricsStyle(): com.vyllo.music.domain.model.LyricsStyle =
        com.vyllo.music.domain.model.LyricsStyle(
            scale = com.vyllo.music.domain.model.LyricsStyle.scaleFromPercent(
                preferences.getInt(KEY_LYRICS_TEXT_SCALE, 100)
            ),
            lineSpacing = com.vyllo.music.domain.model.LyricsStyle.spacingFromPercent(
                preferences.getInt(KEY_LYRICS_LINE_SPACING, 100)
            ),
            alignStart = preferences.getBoolean(KEY_LYRICS_ALIGN_START, false),
            glow = preferences.getBoolean(KEY_LYRICS_GLOW, true),
            animate = preferences.getBoolean(KEY_LYRICS_ANIMATE, true)
        )

    fun writeLyricsStyle(style: com.vyllo.music.domain.model.LyricsStyle) {
        preferences.edit {
                putInt(
                    KEY_LYRICS_TEXT_SCALE,
                    com.vyllo.music.domain.model.LyricsStyle.percentOf(style.scale)
                )
                .putInt(
                    KEY_LYRICS_LINE_SPACING,
                    com.vyllo.music.domain.model.LyricsStyle.percentOf(style.lineSpacing)
                )
                .putBoolean(KEY_LYRICS_ALIGN_START, style.alignStart)
                .putBoolean(KEY_LYRICS_GLOW, style.glow)
                .putBoolean(KEY_LYRICS_ANIMATE, style.animate)
            }
    }

    /** Loudness normalization preset: Off/Aggressive/Loud/Balanced/Quiet. */    var loudnessPreset: String
        get() = preferences.getString(KEY_LOUDNESS_PRESET, "Off") ?: "Off"
        set(value) {
            preferences.edit {putString(KEY_LOUDNESS_PRESET, value)}
        }

    /** Crossfade window in seconds; 0 disables fades and keeps gapless intact. */
    var crossfadeSeconds: Int
        get() = preferences.getInt(KEY_CROSSFADE_SECONDS, 0)
        set(value) {
            preferences.edit {
                    putInt(KEY_CROSSFADE_SECONDS, value.coerceIn(0, 12))
                }
        }

    var isEqualizerEnabled: Boolean
        get() = preferences.getBoolean(KEY_EQUALIZER_ENABLED, false)
        set(value) { preferences.edit {putBoolean(KEY_EQUALIZER_ENABLED, value)} }

    /**
     * Playback rate applied by [com.vyllo.music.service.MusicService]. Stored so
     * the choice survives restarts and applies to whatever starts playing next.
     */
    var playbackSpeed: Float
        get() = preferences.getFloat(KEY_PLAYBACK_SPEED, 1.0f)
        set(value) {
            preferences.edit {
                    putFloat(KEY_PLAYBACK_SPEED, value.coerceIn(SPEED_MIN, SPEED_MAX))
                }
        }

    /** Pitch shift, independent of speed (1.0f = untouched). */
    var playbackPitch: Float
        get() = preferences.getFloat(KEY_PLAYBACK_PITCH, 1.0f)
        set(value) {
            preferences.edit {
                    putFloat(KEY_PLAYBACK_PITCH, value.coerceIn(PITCH_MIN, PITCH_MAX))
                }
        }

    var equalizerBassBoostStrength: Int
        get() = preferences.getInt(KEY_EQUALIZER_BASS_BOOST, 0)
        set(value) {
            preferences.edit {
                    putInt(KEY_EQUALIZER_BASS_BOOST, value.coerceIn(EqualizerSettings.STRENGTH_MIN, EqualizerSettings.STRENGTH_MAX))
                }
        }

    var equalizerVirtualizerStrength: Int
        get() = preferences.getInt(KEY_EQUALIZER_VIRTUALIZER, 0)
        set(value) {
            preferences.edit {
                    putInt(KEY_EQUALIZER_VIRTUALIZER, value.coerceIn(EqualizerSettings.STRENGTH_MIN, EqualizerSettings.STRENGTH_MAX))
                }
        }

    /** Removes centred vocals through mid/side cancellation. */
    var karaokeEnabled: Boolean
        get() = preferences.getBoolean(KEY_KARAOKE, false)
        set(value) { preferences.edit {putBoolean(KEY_KARAOKE, value)} }

    /** Reverb program id, 0 meaning off. */
    var reverbPresetId: Int
        get() = preferences.getInt(KEY_REVERB_PRESET, 0).coerceIn(0, 6)
        set(value) { preferences.edit {putInt(KEY_REVERB_PRESET, value.coerceIn(0, 6))} }

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
                scope.launch {
                    legacy.split("|||").asReversed().forEach { query ->
                        if (query.isNotBlank()) securePrefs.saveSearchQuery(query)
                    }
                }
            }
            preferences.edit {remove("search_history")}
        }
    }

    /**
     * Security: Save search query to encrypted DataStore (Zero plaintext leakage)
     */
    suspend fun saveSearchQuery(query: String) {
        if (query.isBlank()) return
        // Persist before publishing the cache so the UI never advertises a query
        // that DataStore has not committed yet.
        securePrefs.saveSearchQuery(query)
        synchronized(memorySearchHistory) {
            memorySearchHistory.remove(query)
            memorySearchHistory.add(0, query)
            if (memorySearchHistory.size > 10) {
                memorySearchHistory.removeAt(memorySearchHistory.size - 1)
            }
        }
    }

    /**
     * Security: Load search history from encrypted DataStore cache
     */
    suspend fun loadSearchHistory(): List<String> {
        return try {
            val persisted = securePrefs.getSearchHistory().first()
            synchronized(memorySearchHistory) {
                memorySearchHistory.clear()
                memorySearchHistory.addAll(persisted.take(10))
                memorySearchHistory.toList()
            }
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to read search history: ${e.message}")
            synchronized(memorySearchHistory) { memorySearchHistory.toList() }
        }
    }

    suspend fun clearSearchHistory() {
        securePrefs.clearSearchHistory()
        synchronized(memorySearchHistory) {
            memorySearchHistory.clear()
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

    /** User-saved equalizer presets, stored as JSON. */
    var customEqualizerPresets: String
        get() = preferences.getString(KEY_CUSTOM_EQ_PRESETS, "") ?: ""
        set(value) { preferences.edit {putString(KEY_CUSTOM_EQ_PRESETS, value)} }

    fun saveEqualizerBandLevels(levels: List<Int>) {        val sanitized = levels
            .take(EqualizerSettings.DEFAULT_BANDS.size)
            .map { it.coerceIn(EqualizerSettings.BAND_LEVEL_MIN, EqualizerSettings.BAND_LEVEL_MAX) }
        preferences.edit {
                putString(KEY_EQUALIZER_BANDS, sanitized.joinToString(","))
            }
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
        preferences.edit {putInt(key, current + 1)}
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
        preferences.edit {
                putString(KEY_PLAYBACK_POSITION_URL, url)
                .putLong(KEY_PLAYBACK_POSITION_MS, positionMs)
            }
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
        preferences.edit {
                remove(KEY_PLAYBACK_POSITION_URL)
                .remove(KEY_PLAYBACK_POSITION_MS)
            }
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
        const val KEY_LYRICS_SOURCE_ORDER = "lyrics_source_order"
        const val KEY_LYRICS_TEXT_SCALE = "lyrics_text_scale"
        const val KEY_LYRICS_LINE_SPACING = "lyrics_line_spacing"
        const val KEY_LYRICS_ALIGN_START = "lyrics_align_start"
        const val KEY_LYRICS_GLOW = "lyrics_glow"
        const val KEY_LYRICS_ANIMATE = "lyrics_animate"
        const val KEY_CUSTOM_EQ_PRESETS = "custom_eq_presets"
        const val KEY_KARAOKE = "karaoke_enabled"
        const val KEY_REVERB_PRESET = "reverb_preset"
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

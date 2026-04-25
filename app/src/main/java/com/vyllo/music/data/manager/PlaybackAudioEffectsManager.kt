package com.vyllo.music.data.manager

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.EqualizerSettings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackAudioEffectsManager @Inject constructor(
    private val preferenceManager: PreferenceManager
) {

    private var currentAudioSessionId: Int? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    fun attachToAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0) {
            releaseEffects()
            return
        }

        if (currentAudioSessionId == audioSessionId && equalizer != null) {
            applyCurrentSettings()
            return
        }

        releaseEffects()
        currentAudioSessionId = audioSessionId

        try {
            equalizer = Equalizer(0, audioSessionId)
            bassBoost = BassBoost(0, audioSessionId)
            virtualizer = Virtualizer(0, audioSessionId)
            applyCurrentSettings()
            SecureLogger.d(TAG) { "Audio effects attached to session=$audioSessionId" }
        } catch (error: Exception) {
            SecureLogger.w(TAG, "Unable to create audio effects for session=$audioSessionId", error)
            releaseEffects()
        }
    }

    fun onPreferenceChanged(key: String?) {
        if (key == null || key !in EFFECT_PREFERENCE_KEYS) return
        applyCurrentSettings()
    }

    fun applyCurrentSettings() {
        apply(preferenceManager.loadEqualizerSettings())
    }

    fun release() {
        releaseEffects()
    }

    private fun apply(settings: EqualizerSettings) {
        val sanitized = settings.sanitized()
        equalizer?.let { effect ->
            runCatching {
                effect.enabled = sanitized.enabled
                val bandRange = effect.bandLevelRange
                val minBandLevel = bandRange.getOrNull(0)?.toInt() ?: EqualizerSettings.BAND_LEVEL_MIN
                val maxBandLevel = bandRange.getOrNull(1)?.toInt() ?: EqualizerSettings.BAND_LEVEL_MAX
                val supportedBandCount = effect.numberOfBands.toInt().coerceAtLeast(0)

                sanitized.bands.take(supportedBandCount).forEachIndexed { index, band ->
                    effect.setBandLevel(
                        index.toShort(),
                        band.level.coerceIn(minBandLevel, maxBandLevel).toShort()
                    )
                }
            }.onFailure { error ->
                SecureLogger.w(TAG, "Failed to apply equalizer settings", error)
            }
        }

        bassBoost?.let { effect ->
            runCatching {
                effect.enabled = sanitized.enabled && sanitized.bassBoostStrength > 0
                if (effect.strengthSupported) {
                    effect.setStrength(sanitized.bassBoostStrength.toShort())
                }
            }.onFailure { error ->
                SecureLogger.w(TAG, "Failed to apply bass boost", error)
            }
        }

        virtualizer?.let { effect ->
            runCatching {
                effect.enabled = sanitized.enabled && sanitized.virtualizerStrength > 0
                if (effect.strengthSupported) {
                    effect.setStrength(sanitized.virtualizerStrength.toShort())
                }
            }.onFailure { error ->
                SecureLogger.w(TAG, "Failed to apply virtualizer", error)
            }
        }
    }

    private fun releaseEffects() {
        equalizer?.releaseSafely("equalizer")
        bassBoost?.releaseSafely("bassBoost")
        virtualizer?.releaseSafely("virtualizer")
        equalizer = null
        bassBoost = null
        virtualizer = null
        currentAudioSessionId = null
    }

    private fun Any.releaseSafely(name: String) {
        runCatching {
            when (this) {
                is Equalizer -> release()
                is BassBoost -> release()
                is Virtualizer -> release()
            }
        }.onFailure { error ->
            SecureLogger.w(TAG, "Failed to release $name", error)
        }
    }

    companion object {
        private const val TAG = "PlaybackAudioEffects"

        private val EFFECT_PREFERENCE_KEYS = setOf(
            PreferenceManager.KEY_EQUALIZER_ENABLED,
            PreferenceManager.KEY_EQUALIZER_BASS_BOOST,
            PreferenceManager.KEY_EQUALIZER_VIRTUALIZER,
            PreferenceManager.KEY_EQUALIZER_BANDS
        )
    }
}

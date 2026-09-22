package com.vyllo.music.service.alarm

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import androidx.media3.exoplayer.ExoPlayer
import com.vyllo.music.core.security.SecureLogger

/**
 * Controls alarm stream volume + gradual increase.
 * Extracted from AlarmTriggerService — no behavior change.
 */
class AlarmVolumeController(
    private val handler: Handler,
    private val playerProvider: () -> ExoPlayer?,
    private val mediaPlayerProvider: () -> MediaPlayer?,
    private val targetVolumeProvider: () -> Int
) {
    private var isGradualVolumeComplete = false
    private var currentVolume = 0
    private var originalAlarmVolume: Int? = null

    companion object {
        private const val TAG = "AlarmTriggerService"
    }

    /**
     * Set the system alarm stream volume to the user's configured level.
     * Saves the original volume once so it can be restored when the alarm ends.
     */
    fun setAlarmStreamVolume(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (originalAlarmVolume == null) {
                originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            }
            val volume = targetVolumeProvider()
            val targetVolume = (maxVolume * volume / 100f).toInt().coerceIn(1, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)
            SecureLogger.d(TAG, "Set alarm stream volume to $targetVolume/$maxVolume")
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to set alarm volume", e)
        }
    }

    /**
     * Restore the user's original alarm stream volume after the alarm ends.
     */
    fun restoreAlarmStreamVolume(context: Context) {
        val original = originalAlarmVolume ?: return
        originalAlarmVolume = null
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, original, 0)
            SecureLogger.d(TAG, "Restored original alarm stream volume to $original")
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to restore alarm volume", e)
        }
    }

    /**
     * Start gradual volume increase over configured duration.
     */
    fun startGradualVolumeIncrease() {
        val volume = targetVolumeProvider()
        currentVolume = 0
        isGradualVolumeComplete = false

        val durationMs = 30000L // 30 seconds
        val intervalMs = 500L // Update every 500ms
        val steps = (durationMs / intervalMs).toInt()
        val volumeStep = volume.toFloat() / steps

        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isGradualVolumeComplete || currentVolume >= volume) {
                    isGradualVolumeComplete = true
                    setFinalVolume()
                    return
                }

                currentVolume += volumeStep.toInt()
                if (currentVolume > volume) currentVolume = volume

                setVolume(currentVolume)

                handler.postDelayed(this, intervalMs)
            }
        }, intervalMs)
    }

    /**
     * Set volume for both players.
     */
    fun setVolume(level: Int) {
        val normalizedVolume = level / 100f
        playerProvider()?.volume = normalizedVolume
        mediaPlayerProvider()?.setVolume(normalizedVolume, normalizedVolume)
    }

    /**
     * Set final volume when gradual increase is complete.
     */
    fun setFinalVolume() {
        setVolume(targetVolumeProvider())
    }

    fun markComplete() {
        isGradualVolumeComplete = true
    }
}

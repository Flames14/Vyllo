package com.vyllo.music.service.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.download.DownloadDao
import com.vyllo.music.domain.model.SoundType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * Controls alarm audio playback (downloaded song / default sound / stop).
 * Extracted from AlarmTriggerService — no behavior change.
 *
 * Owns ExoPlayer + MediaPlayer instances. Volume ramping is delegated to
 * [AlarmVolumeController]; vibration is started/stopped here to keep the
 * play/stop flow atomic as in the original service.
 */
class AlarmPlaybackController(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val scope: CoroutineScope
) {
    var player: ExoPlayer? = null
        private set
    var mediaPlayer: MediaPlayer? = null
        private set

    var volumeController: AlarmVolumeController? = null

    companion object {
        private const val TAG = "AlarmTriggerService"
    }

    /**
     * Play the alarm sound.
     */
    fun playAlarm(
        soundType: SoundType,
        songUrl: String?,
        gradualVolume: Boolean,
        vibrationEnabled: Boolean,
        vibrator: Vibrator?
    ) {
        // Stop any existing playback first
        stopPlayback(vibrator)

        when (soundType) {
            SoundType.DOWNLOADED_SONG -> playDownloadedSong(songUrl, gradualVolume)
            SoundType.DEFAULT -> playDefaultSound(gradualVolume)
        }

        if (gradualVolume) {
            volumeController?.startGradualVolumeIncrease()
        } else {
            volumeController?.setFinalVolume()
        }

        // Start vibration if enabled
        if (vibrationEnabled) {
            startVibration(vibrator)
        }
    }

    /**
     * Play a downloaded song from local storage.
     */
    private fun playDownloadedSong(songUrl: String?, gradualVolume: Boolean) {
        scope.launch {
            try {
                // Get download entity to get file path
                val download = songUrl?.let { downloadDao.getDownloadByUrl(it) }
                val filePath = download?.filePath

                SecureLogger.d(TAG, "playDownloadedSong: url=$songUrl, filePath=$filePath")

                if (filePath != null && File(filePath).exists()) {
                    player = ExoPlayer.Builder(context)
                        .setAudioAttributes(
                            androidx.media3.common.AudioAttributes.Builder()
                                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                                .setUsage(C.USAGE_ALARM)  // Use USAGE_ALARM for proper alarm behavior
                                .build(),
                            false  // Disable automatic audio focus handling
                        )
                        .setWakeMode(C.WAKE_MODE_LOCAL)  // ExoPlayer manages its own wake lock
                        .build()
                        .apply {
                            val mediaItem = MediaItem.fromUri("file://$filePath")
                            setMediaItem(mediaItem)
                            repeatMode = Player.REPEAT_MODE_ALL
                            playWhenReady = true
                            // Set initial volume based on gradual volume setting
                            this.volume = if (gradualVolume) 0.0f else 1.0f
                            prepare()

                            addListener(object : Player.Listener {
                                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                    SecureLogger.e(TAG, "ExoPlayer error playing $filePath: ${error.message}", error)
                                    // Only play default sound if ExoPlayer fails completely
                                    player?.release()
                                    player = null
                                    playDefaultSound(gradualVolume)
                                }
                            })
                        }

                    SecureLogger.d(TAG, "ExoPlayer prepared and playing downloaded song")
                } else {
                    SecureLogger.w(TAG, "File not found or null, falling back to default")
                    // Release any partial ExoPlayer before fallback
                    player?.release()
                    player = null
                    playDefaultSound(gradualVolume)
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to play downloaded song", e)
                // Release any partial ExoPlayer before fallback
                player?.release()
                player = null
                playDefaultSound(gradualVolume)
            }
        }
    }

    /**
     * Play default alarm sound from system resources.
     *
     * CRITICAL: Uses STREAM_ALARM (not STREAM_MUSIC) so the alarm plays at the
     * alarm volume level and bypasses Do Not Disturb when configured.
     */
    private fun playDefaultSound(gradualVolume: Boolean) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, Uri.parse("content://settings/system/alarm_alert"))
                isLooping = true
                prepare()
                start()
                // Set initial volume based on gradual volume setting
                val initialVol = if (gradualVolume) 0.0f else 1.0f
                this.setVolume(initialVol, initialVol)
            }
            SecureLogger.d(TAG, "Default alarm sound playing via STREAM_ALARM")
        } catch (e: Exception) {
            SecureLogger.e(TAG, "System alarm sound failed, trying fallback", e)
            // If system alarm sound fails, use a simple tone
            try {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context, Uri.parse("android.resource://${context.packageName}/raw/alarm"))
                    isLooping = true
                    prepare()
                    start()
                    val initialVol = if (gradualVolume) 0.0f else 1.0f
                    this.setVolume(initialVol, initialVol)
                }
            } catch (e2: Exception) {
                SecureLogger.e(TAG, "Failed to play fallback sound", e2)
            }
        }
    }

    /**
     * Start vibration pattern.
     */
    fun startVibration(vibrator: Vibrator?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 500, 500, 500), 0)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(longArrayOf(0, 500, 500, 500), 0)
        }
    }

    /**
     * Stop audio playback only (without releasing wake locks or stopping service).
     */
    fun stopPlayback(vibrator: Vibrator?) {
        player?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Error stopping ExoPlayer", e)
            }
        }
        player = null

        mediaPlayer?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Error stopping MediaPlayer", e)
            }
        }
        mediaPlayer = null

        // Stop vibration
        vibrator?.cancel()
    }
}

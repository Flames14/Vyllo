package com.vyllo.music.service

import com.vyllo.music.core.security.SecureLogger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vyllo.music.MainActivity
import com.vyllo.music.R
import com.vyllo.music.data.download.DownloadDao
import com.vyllo.music.data.download.DownloadDatabase
import com.vyllo.music.data.manager.AlarmSchedulerManager
import com.vyllo.music.domain.model.AlarmModel
import com.vyllo.music.domain.model.SoundType
import com.vyllo.music.domain.repository.AlarmRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import javax.inject.Inject

/**
 * Foreground service that plays alarm sound when triggered by AlarmManager.
 * 
 * This service:
 * 1. Acquires wake locks to ensure device is awake
 * 2. Plays the alarm sound (default or downloaded song)
 * 3. Shows a full-screen notification to wake the device
 * 4. Handles dismiss and snooze actions
 */
@AndroidEntryPoint
class AlarmTriggerService : Service() {

    @Inject
    lateinit var downloadDao: DownloadDao

    @Inject
    lateinit var alarmSchedulerManager: AlarmSchedulerManager

    @Inject
    lateinit var alarmRepository: AlarmRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var player: ExoPlayer? = null
    private var mediaPlayer: MediaPlayer? = null // For default sounds
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    
    private var alarmId: Long = 0
    private var alarmLabel: String = ""
    private var soundType: SoundType = SoundType.DEFAULT
    private var songUrl: String? = null
    private var volume: Int = 80
    private var gradualVolume: Boolean = true
    private var vibrationEnabled: Boolean = true
    
    private var isGradualVolumeComplete = false
    private var currentVolume = 0

    companion object {
        private const val TAG = "AlarmTriggerService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "alarm_channel"
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val EXTRA_ALARM_LABEL = "alarm_label"
        private const val EXTRA_SOUND_TYPE = "sound_type"
        private const val EXTRA_SONG_URL = "song_url"
        private const val EXTRA_VOLUME = "volume"
        private const val EXTRA_GRADUAL_VOLUME = "gradual_volume"
        private const val EXTRA_VIBRATION_ENABLED = "vibration_enabled"
        
        // Actions
        const val ACTION_DISMISS = "com.vyllo.music.action.DISMISS_ALARM"
        const val ACTION_SNOOZE = "com.vyllo.music.action.SNOOZE_ALARM"

        /**
         * Global state to track if an alarm is currently ringing.
         * Used for showing emergency stop UI.
         */
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            SecureLogger.e(TAG, "onStartCommand: null intent")
            stopSelf()
            return START_NOT_STICKY
        }

        val action = intent.action
        if (action == ACTION_DISMISS) {
            dismissAlarm()
            return START_NOT_STICKY
        } else if (action == ACTION_SNOOZE) {
            snoozeAlarm()
            return START_NOT_STICKY
        }

        // Extract alarm data from intent
        alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0)
        alarmLabel = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: ""
        soundType = SoundType.valueOf(intent.getStringExtra(EXTRA_SOUND_TYPE) ?: SoundType.DEFAULT.name)
        songUrl = intent.getStringExtra(EXTRA_SONG_URL)
        volume = intent.getIntExtra(EXTRA_VOLUME, 80)
        gradualVolume = intent.getBooleanExtra(EXTRA_GRADUAL_VOLUME, true)
        vibrationEnabled = intent.getBooleanExtra(EXTRA_VIBRATION_ENABLED, true)

        SecureLogger.d(TAG, "=== ALARM SERVICE STARTED ===")
        SecureLogger.d(TAG, "alarmId=$alarmId, label=$alarmLabel")
        SecureLogger.d(TAG, "soundType=$soundType, songUrl=$songUrl")
        SecureLogger.d(TAG, "volume=$volume, gradualVolume=$gradualVolume, vibrationEnabled=$vibrationEnabled")

        // Acquire wake locks
        acquireWakeLock()

        // Start foreground notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        // Play alarm sound
        playAlarm()

        return START_STICKY
    }

    /**
     * Create notification channel for alarms (Android 8+).
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm notifications"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 500, 500)
                setSound(Uri.parse("content://settings/system/alarm_alert"), audioAttributes)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableLights(true)
                setShowBadge(true)
                setBypassDnd(true)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create notification displayed when alarm triggers.
     */
    private fun createNotification(): Notification {
        val dismissIntent = Intent(this, AlarmTriggerService::class.java).apply {
            action = ACTION_DISMISS
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(this, AlarmTriggerService::class.java).apply {
            action = ACTION_SNOOZE
        }
        val snoozePendingIntent = PendingIntent.getService(
            this,
            2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for the full-screen alarm activity
        val fullScreenIntent = Intent(this, com.vyllo.music.ui.alarm.AlarmTriggerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra("alarm_id", alarmId)
            putExtra("alarm_label", alarmLabel)
            putExtra("alarm_time", java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date()))
            putExtra("sound_type", soundType.name)
            putExtra("song_url", songUrl)
            putExtra("volume", volume)
            putExtra("vibration", vibrationEnabled)
        }
        
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Alarm")
            .setContentText(if (alarmLabel.isNotBlank()) alarmLabel else "Tap to dismiss or snooze")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)
            .addAction(android.R.drawable.ic_menu_recent_history, "Snooze 5 min", snoozePendingIntent)
            .build()
    }

    /**
     * Play the alarm sound.
     */
    private fun playAlarm() {
        // Stop any existing playback first
        stopAlarm()
        
        when (soundType) {
            SoundType.DOWNLOADED_SONG -> playDownloadedSong()
            SoundType.DEFAULT -> playDefaultSound()
        }

        // Start vibration if enabled
        if (vibrationEnabled) {
            startVibration()
        }

        // Note: Gradual volume is disabled for alarms - we want maximum volume immediately
        // to ensure the user wakes up
    }

    /**
     * Play a downloaded song from local storage.
     */
    private fun playDownloadedSong() {
        serviceScope.launch {
            try {
                // Get download entity to get file path
                val download = songUrl?.let { downloadDao.getDownloadByUrl(it) }
                val filePath = download?.filePath

                SecureLogger.d(TAG, "playDownloadedSong: url=$songUrl, filePath=$filePath")

                if (filePath != null && File(filePath).exists()) {
                    player = ExoPlayer.Builder(this@AlarmTriggerService)
                        .setAudioAttributes(
                            androidx.media3.common.AudioAttributes.Builder()
                                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                                .setUsage(C.USAGE_ALARM)  // Use USAGE_ALARM for proper alarm behavior
                                .build(),
                            false  // Disable automatic audio focus handling
                        )
                        .build()
                        .apply {
                            val mediaItem = MediaItem.fromUri("file://$filePath")
                            setMediaItem(mediaItem)
                            repeatMode = Player.REPEAT_MODE_ALL
                            playWhenReady = true
                            // Set volume to maximum for alarm
                            this.volume = 1.0f
                            prepare()

                            addListener(object : Player.Listener {
                                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                    SecureLogger.e(TAG, "ExoPlayer error playing $filePath: ${error.message}", error)
                                    // Only play default sound if ExoPlayer fails completely
                                    player?.release()
                                    player = null
                                    playDefaultSound()
                                }
                            })
                        }
                    
                    SecureLogger.d(TAG, "ExoPlayer prepared and playing downloaded song")
                } else {
                    SecureLogger.w(TAG, "File not found or null, falling back to default")
                    // Release any partial ExoPlayer before fallback
                    player?.release()
                    player = null
                    playDefaultSound()
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to play downloaded song", e)
                // Release any partial ExoPlayer before fallback
                player?.release()
                player = null
                playDefaultSound()
            }
        }
    }

    /**
     * Play default alarm sound from system resources.
     */
    private fun playDefaultSound() {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmTriggerService, Uri.parse("content://settings/system/alarm_alert"))
                setAudioStreamType(AudioManager.STREAM_MUSIC)  // Use MUSIC stream for louder volume
                isLooping = true
                prepare()
                start()
                // Set volume to maximum for alarm
                this.setVolume(1.0f, 1.0f)
            }
        } catch (e: Exception) {
            // If system alarm sound fails, use a simple tone
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@AlarmTriggerService, Uri.parse("android.resource://${packageName}/raw/alarm"))
                    setAudioStreamType(AudioManager.STREAM_MUSIC)  // Use MUSIC stream for louder volume
                    isLooping = true
                    prepare()
                    start()
                    // Set volume to maximum
                    this.setVolume(1.0f, 1.0f)
                }
            } catch (e2: Exception) {
                SecureLogger.e(TAG, "Failed to play default sound", e2)
            }
        }
    }

    /**
     * Start gradual volume increase over configured duration.
     */
    private fun startGradualVolumeIncrease() {
        currentVolume = 0
        isGradualVolumeComplete = false
        
        val durationMs = 30000L // 30 seconds
        val intervalMs = 500L // Update every 500ms
        val steps = (durationMs / intervalMs).toInt()
        val volumeStep = volume.toFloat() / steps

        Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                if (isGradualVolumeComplete || currentVolume >= volume) {
                    isGradualVolumeComplete = true
                    setFinalVolume()
                    return
                }

                currentVolume += volumeStep.toInt()
                if (currentVolume > volume) currentVolume = volume
                
                setVolume(currentVolume)

                Handler(Looper.getMainLooper()).postDelayed(this, intervalMs)
            }
        }, intervalMs)
    }

    /**
     * Set volume for both players.
     */
    private fun setVolume(level: Int) {
        val normalizedVolume = level / 100f
        player?.volume = normalizedVolume
        mediaPlayer?.setVolume(normalizedVolume, normalizedVolume)
    }

    /**
     * Set final volume when gradual increase is complete.
     */
    private fun setFinalVolume() {
        setVolume(volume)
    }

    /**
     * Start vibration pattern.
     */
    private fun startVibration() {
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
     * Acquire wake lock to keep CPU awake.
     */
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
            PowerManager.ACQUIRE_CAUSES_WAKEUP or
            PowerManager.ON_AFTER_RELEASE,
            "Vyllo::AlarmWakeLock"
        ).apply {
            acquire(15 * 60 * 1000L) // 15 minutes max
        }
    }

    /**
     * Release wake lock.
     */
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    /**
     * Handle dismiss action.
     */
    private fun dismissAlarm() {
        stopAlarm()
        stopSelf()
    }

    /**
     * Handle snooze action — reschedules the alarm for 5 minutes from now.
     */
    private fun snoozeAlarm() {
        stopAlarm()

        serviceScope.launch {
            try {
                val alarm = alarmId.takeIf { it > 0 }?.let { id ->
                    alarmRepository.getAlarmById(id)
                }

                if (alarm != null) {
                    val snoozedAlarm = alarm.copy(
                        isEnabled = true
                    )
                    val snoozeAt = Calendar.getInstance().apply {
                        add(Calendar.MINUTE, 5)
                    }.timeInMillis
                    alarmSchedulerManager.scheduleAt(snoozedAlarm, snoozeAt)
                    SecureLogger.d(TAG, "Alarm snoozed for 5 minutes: ${java.util.Date(snoozeAt)}")
                } else {
                    SecureLogger.w(TAG, "Cannot snooze: alarm model not found (id=$alarmId)")
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to snooze alarm", e)
            } finally {
                stopSelf()
            }
        }
    }

    /**
     * Stop alarm playback and cleanup.
     */
    private fun stopAlarm() {
        player?.let {
            it.stop()
            it.release()
        }
        player = null

        mediaPlayer?.let {
            it.stop()
            it.release()
        }
        mediaPlayer = null

        // Stop vibration
        vibrator?.cancel()
        
        // Release wake locks
        releaseWakeLock()
    }

    override fun onDestroy() {
        stopAlarm()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

package com.vyllo.music.service

import com.vyllo.music.core.security.SecureLogger

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Vibrator
import com.vyllo.music.data.download.DownloadDao
import com.vyllo.music.data.manager.AlarmSchedulerManager
import com.vyllo.music.domain.model.SoundType
import com.vyllo.music.domain.repository.AlarmRepository
import com.vyllo.music.service.alarm.AlarmNotificationFactory
import com.vyllo.music.service.alarm.AlarmPlaybackController
import com.vyllo.music.service.alarm.AlarmSchedulerHelper
import com.vyllo.music.service.alarm.AlarmVolumeController
import com.vyllo.music.service.alarm.AlarmWakeLockManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

/**
 * Foreground service that plays alarm sound when triggered by AlarmManager.
 *
 * This service:
 * 1. Acquires wake locks to ensure device stays awake
 * 2. Plays the alarm sound (default or downloaded song) via STREAM_ALARM
 * 3. Shows a full-screen notification to wake the device
 * 4. Handles dismiss and snooze actions
 * 5. Auto-dismisses after 10 minutes as a safety net
 *
 * CRITICAL: This service must call startForeground() synchronously in onStartCommand()
 * and release the receiver's wake lock after doing so. See AlarmTriggerReceiver.
 *
 * Thin orchestrator — audio, volume, notification, wakelock and scheduling logic
 * lives in service/alarm/. No behavior change.
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

    private lateinit var wakeLockManager: AlarmWakeLockManager
    private lateinit var playbackController: AlarmPlaybackController
    private lateinit var volumeController: AlarmVolumeController
    private lateinit var schedulerHelper: AlarmSchedulerHelper

    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    private var alarmId: Long = 0
    private var alarmLabel: String = ""
    private var soundType: SoundType = SoundType.DEFAULT
    private var songUrl: String? = null
    private var volume: Int = 80
    private var gradualVolume: Boolean = true
    private var vibrationEnabled: Boolean = true

    // Auto-dismiss runnable — safety net to stop alarm after 10 minutes
    private val autoDismissRunnable = Runnable {
        SecureLogger.w(TAG, "Auto-dismissing alarm after 10 minutes")
        dismissAlarm()
    }

    companion object {
        private const val TAG = "AlarmTriggerService"
        private const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "vyllo_alarm_channel_silent"
        private const val EXTRA_ALARM_ID = "alarm_id"
        private const val EXTRA_ALARM_LABEL = "alarm_label"
        private const val EXTRA_SOUND_TYPE = "sound_type"
        private const val EXTRA_SONG_URL = "song_url"
        private const val EXTRA_VOLUME = "volume"
        private const val EXTRA_GRADUAL_VOLUME = "gradual_volume"
        private const val EXTRA_VIBRATION_ENABLED = "vibration_enabled"

        // Auto-dismiss after 10 minutes
        private const val AUTO_DISMISS_MS = 10L * 60 * 1000

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
        wakeLockManager = AlarmWakeLockManager(this)
        playbackController = AlarmPlaybackController(this, downloadDao, serviceScope)
        volumeController = AlarmVolumeController(
            handler = handler,
            playerProvider = { playbackController.player },
            mediaPlayerProvider = { playbackController.mediaPlayer },
            targetVolumeProvider = { volume }
        )
        playbackController.volumeController = volumeController
        schedulerHelper = AlarmSchedulerHelper(
            alarmRepository = alarmRepository,
            alarmSchedulerManager = alarmSchedulerManager,
            scope = serviceScope,
            playbackController = playbackController,
            wakeLockManager = wakeLockManager,
            handler = handler,
            autoDismissRunnable = autoDismissRunnable,
            vibratorProvider = { vibrator }
        )
        AlarmNotificationFactory.createNotificationChannel(this)
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

        // Extract alarm data from intent extras
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

        // CRITICAL: Acquire wake locks IMMEDIATELY (before any async work)
        wakeLockManager.acquire()

        // CRITICAL: Build and start the foreground notification BEFORE any async work.
        // On Android 14+, the system will kill the service if startForeground() isn't called
        // within 5 seconds of startService().
        val notification = AlarmNotificationFactory.createNotification(
            context = this,
            alarmId = alarmId,
            alarmLabel = alarmLabel,
            soundType = soundType,
            songUrl = songUrl,
            volume = volume,
            vibrationEnabled = vibrationEnabled
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // CRITICAL: Release the receiver's wake lock now that we're in foreground.
        // This completes the receiver -> service wake lock handoff.
        AlarmTriggerReceiver.releaseReceiverWakeLock()

        // Set alarm volume to configured level
        volumeController.setAlarmStreamVolume(this)

        // Now safe to do async work — the service is already in foreground
        playbackController.playAlarm(soundType, songUrl, gradualVolume, vibrationEnabled, vibrator)

        // Schedule auto-dismiss after 10 minutes as safety net
        handler.removeCallbacks(autoDismissRunnable)
        handler.postDelayed(autoDismissRunnable, AUTO_DISMISS_MS)

        // CRITICAL: Use START_REDELIVER_INTENT so if the system kills and restarts
        // this service, the original intent data is preserved and re-delivered.
        return START_REDELIVER_INTENT
    }

    /**
     * Handle dismiss action.
     */
    private fun dismissAlarm() {
        schedulerHelper.dismissAlarm(alarmId) { stopSelf() }
    }

    /**
     * Handle snooze action — reschedules the alarm for 5 minutes from now.
     */
    private fun snoozeAlarm() {
        schedulerHelper.snoozeAlarm(alarmId) { stopSelf() }
    }

    /**
     * Stop alarm playback and cleanup everything.
     */
    private fun stopAlarm() {
        schedulerHelper.stopAlarm()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoDismissRunnable)
        stopAlarm()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

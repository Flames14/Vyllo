package com.vyllo.music.ui.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vyllo.music.R
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.download.DownloadDao
import com.vyllo.music.data.download.DownloadDatabase
import com.vyllo.music.service.AlarmTriggerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.system.exitProcess

/**
 * Full-screen activity that appears when alarm triggers.
 * Shows alarm info and allows user to dismiss or snooze.
 *
 * CRITICAL: This activity also handles the case where it's launched via
 * full-screen notification but the AlarmTriggerService was killed by the system.
 * In that case, it restarts the service using the intent extras.
 */
@AndroidEntryPoint
class AlarmTriggerActivity : ComponentActivity() {

    @Inject
    lateinit var downloadDao: DownloadDao

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null
    
    private var alarmId: Long = 0
    private var alarmLabel: String = ""
    private var alarmTime: String = ""

    companion object {
        private const val TAG = "AlarmTriggerActivity"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_ALARM_LABEL = "alarm_label"
        const val EXTRA_ALARM_TIME = "alarm_time"
        const val EXTRA_SOUND_TYPE = "sound_type"
        const val EXTRA_SONG_URL = "song_url"
        const val EXTRA_VOLUME = "volume"
        const val EXTRA_VIBRATION = "vibration"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SecureLogger.d(TAG, "onCreate called")

        // Initialize vibrator
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Make activity show over lock screen
        setupWindow()

        // Get alarm data from intent
        alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0)
        alarmLabel = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: ""
        alarmTime = intent.getStringExtra(EXTRA_ALARM_TIME) ?: ""

        SecureLogger.d(TAG, "Alarm data: id=$alarmId, time=$alarmTime")

        // Show UI
        setContentView(R.layout.activity_alarm_trigger)

        // Setup views
        setupViews()

        // Acquire wake lock
        acquireWakeLock()

        // CRITICAL: If the service is not running (e.g., system killed it after clearing recents),
        // restart it so the alarm sound actually plays.
        ensureServiceRunning()
    }

    /**
     * Ensure AlarmTriggerService is running. If the app was killed from recents,
     * the service may have been destroyed. The full-screen notification intent
     * still launches this activity, but without the service there's no sound.
     */
    private fun ensureServiceRunning() {
        if (!AlarmTriggerService.isRunning) {
            SecureLogger.w(TAG, "AlarmTriggerService is NOT running — restarting it")
            try {
                val serviceIntent = Intent(this, AlarmTriggerService::class.java).apply {
                    putExtra("alarm_id", alarmId)
                    putExtra("alarm_label", alarmLabel)
                    putExtra("sound_type", intent.getStringExtra(EXTRA_SOUND_TYPE) ?: "DEFAULT")
                    putExtra("song_url", intent.getStringExtra(EXTRA_SONG_URL))
                    putExtra("volume", intent.getIntExtra(EXTRA_VOLUME, 80))
                    putExtra("gradual_volume", true)
                    putExtra("vibration_enabled", intent.getBooleanExtra(EXTRA_VIBRATION, true))
                }
                ContextCompat.startForegroundService(this, serviceIntent)
                SecureLogger.d(TAG, "AlarmTriggerService restarted from activity")
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to restart AlarmTriggerService", e)
            }
        } else {
            SecureLogger.d(TAG, "AlarmTriggerService is already running")
        }
    }

    /**
     * Setup window to show over lock screen.
     */
    private fun setupWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        // Keep the screen on while this activity is visible
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Make status bar transparent
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * Setup UI views.
     */
    private fun setupViews() {
        // Set alarm time
        findViewById<TextView>(R.id.alarmTimeText).text = alarmTime.ifBlank { 
            java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date()) 
        }
        
        // Set alarm label
        val labelTextView = findViewById<TextView>(R.id.alarmLabelText)
        labelTextView.text = alarmLabel.ifBlank { "Time to wake up!" }
        
        // Dismiss button
        findViewById<ImageButton>(R.id.dismissButton).setOnClickListener {
            dismissAlarm()
        }
        
        // Snooze button
        findViewById<ImageButton>(R.id.snoozeButton).setOnClickListener {
            snoozeAlarm()
        }
    }

    /**
     * Acquire wake lock to keep CPU alive.
     * Uses PARTIAL_WAKE_LOCK (reliable) — screen is kept on via window flags.
     */
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        cpuWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Vyllo::AlarmActivityCpuWakeLock"
        ).apply {
            acquire(15 * 60 * 1000L) // 15 minutes max
        }
    }

    /**
     * Dismiss alarm and close activity.
     */
    private fun dismissAlarm() {
        val intent = Intent(this, AlarmTriggerService::class.java).apply {
            action = AlarmTriggerService.ACTION_DISMISS
        }
        try {
            startService(intent)
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to send dismiss to service", e)
        }
        finish()
        overridePendingTransition(0, 0)
    }

    /**
     * Snooze alarm for 5 minutes.
     */
    private fun snoozeAlarm() {
        val intent = Intent(this, AlarmTriggerService::class.java).apply {
            action = AlarmTriggerService.ACTION_SNOOZE
        }
        try {
            startService(intent)
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to send snooze to service", e)
        }
        finish()
        overridePendingTransition(0, 0)
    }

    /**
     * Stop alarm vibration and release wake lock.
     */
    private fun stopAlarm() {
        // Stop vibration
        vibrator?.cancel()
        vibrator = null

        // Stop media player if playing
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Error stopping media player", e)
            }
        }
        mediaPlayer = null

        // Release wake lock
        cpuWakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        cpuWakeLock = null
    }

    override fun onBackPressed() {
        // Prevent back button from dismissing
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}

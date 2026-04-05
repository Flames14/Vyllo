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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vyllo.music.R
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.download.DownloadDao
import com.vyllo.music.data.download.DownloadDatabase
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
 */
@AndroidEntryPoint
class AlarmTriggerActivity : ComponentActivity() {

    @Inject
    lateinit var downloadDao: DownloadDao

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private var alarmId: Long = 0
    private var alarmLabel: String = ""
    private var alarmTime: String = ""

    companion object {
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

        SecureLogger.d("AlarmTriggerActivity", "onCreate called")

        // Initialize vibrator
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Make activity show over lock screen
        setupWindow()

        // Get alarm data from intent
        alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0)
        alarmLabel = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: ""
        alarmTime = intent.getStringExtra(EXTRA_ALARM_TIME) ?: ""

        SecureLogger.d("AlarmTriggerActivity", "Alarm data: id=$alarmId, time=$alarmTime")

        // Show UI
        setContentView(R.layout.activity_alarm_trigger)

        // Setup views
        setupViews()

        // Acquire wake lock
        acquireWakeLock()
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
     * Acquire wake lock to keep screen on.
     */
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
            PowerManager.ACQUIRE_CAUSES_WAKEUP or
            PowerManager.ON_AFTER_RELEASE,
            "Vyllo::AlarmActivityWakeLock"
        ).apply {
            acquire(15 * 60 * 1000L) // 15 minutes max
        }
    }

    /**
     * Dismiss alarm and close activity.
     */
    private fun dismissAlarm() {
        val intent = Intent(this, com.vyllo.music.service.AlarmTriggerService::class.java).apply {
            action = com.vyllo.music.service.AlarmTriggerService.ACTION_DISMISS
        }
        startService(intent)
        finish()
        overridePendingTransition(0, 0)
    }

    /**
     * Snooze alarm for 5 minutes.
     */
    private fun snoozeAlarm() {
        val intent = Intent(this, com.vyllo.music.service.AlarmTriggerService::class.java).apply {
            action = com.vyllo.music.service.AlarmTriggerService.ACTION_SNOOZE
        }
        startService(intent)
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
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null

        // Release wake lock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    override fun onBackPressed() {
        // Prevent back button from dismissing
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}

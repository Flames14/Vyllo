package com.vyllo.music.service

import com.vyllo.music.core.security.SecureLogger

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Receiver that handles the alarm trigger from AlarmManager.
 * It starts the AlarmTriggerService as a foreground service.
 *
 * CRITICAL: This receiver implements a multi-layer approach to ensure
 * the alarm fires in ALL scenarios:
 *
 * 1. Acquires a PARTIAL_WAKE_LOCK to keep CPU alive during handoff
 * 2. Tries startForegroundService() (works with setAlarmClock() exemption)
 * 3. If that fails, directly launches AlarmTriggerActivity as fallback
 *
 * The wake lock is stored in a static companion field and released by
 * AlarmTriggerService after it calls startForeground() successfully.
 */
class AlarmTriggerReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmTriggerReceiver"

        /**
         * Static wake lock that bridges the gap between this receiver and the service.
         * The receiver acquires it; the service releases it after startForeground().
         */
        @Volatile
        private var receiverWakeLock: PowerManager.WakeLock? = null

        /**
         * Release the wake lock that was acquired by this receiver.
         * Called by AlarmTriggerService after it has successfully promoted itself to foreground.
         */
        fun releaseReceiverWakeLock() {
            try {
                receiverWakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                        SecureLogger.d(TAG, "Receiver wake lock released by service")
                    }
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Error releasing receiver wake lock", e)
            } finally {
                receiverWakeLock = null
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra("alarm_id", -1L)

        SecureLogger.d(TAG, "=== ALARM TRIGGERED ===")
        SecureLogger.d(TAG, "Received alarm_id: $alarmId, action: ${intent.action}")

        if (alarmId == -1L) {
            SecureLogger.e(TAG, "No alarm_id provided, aborting")
            return
        }

        // CRITICAL: Acquire a CPU wake lock IMMEDIATELY.
        // Without this, the device can suspend between onReceive() returning
        // and AlarmTriggerService.onStartCommand() executing — killing the alarm.
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            receiverWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Vyllo::AlarmReceiverWakeLock"
            ).apply {
                acquire(60 * 1000L) // 60-second timeout safety net
            }
            SecureLogger.d(TAG, "Receiver wake lock acquired")
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Failed to acquire receiver wake lock", e)
        }

        // Extract all alarm data from intent extras
        val soundType = intent.getStringExtra("sound_type") ?: "DEFAULT"
        val songUrl = intent.getStringExtra("song_url")
        val songTitle = intent.getStringExtra("song_title")
        val volume = intent.getIntExtra("volume", 80)
        val gradualVolume = intent.getBooleanExtra("gradual_volume", true)
        val vibrationEnabled = intent.getBooleanExtra("vibration_enabled", true)
        val alarmLabel = intent.getStringExtra("alarm_label") ?: ""

        SecureLogger.d(TAG, "Alarm data: soundType=$soundType, url=$songUrl, label=$alarmLabel")

        // Build the service intent with all data from extras (no DB query needed)
        val serviceIntent = Intent(context, AlarmTriggerService::class.java).apply {
            // Don't forward the receiver's action — leave null so service treats as new alarm
            putExtra("alarm_id", alarmId)
            putExtra("alarm_label", alarmLabel)
            putExtra("sound_type", soundType)
            putExtra("song_url", songUrl)
            putExtra("song_title", songTitle)
            putExtra("volume", volume)
            putExtra("gradual_volume", gradualVolume)
            putExtra("vibration_enabled", vibrationEnabled)
        }

        // Try to start the foreground service.
        // setAlarmClock() provides the exemption to start foreground services from background.
        var serviceStarted = false
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
            serviceStarted = true
            SecureLogger.d(TAG, "✓ AlarmTriggerService started successfully via startForegroundService")
        } catch (e: Exception) {
            SecureLogger.e(TAG, "✗ Failed to start AlarmTriggerService: ${e.javaClass.simpleName}: ${e.message}", e)
        }

        // FALLBACK: If the service couldn't start (e.g., ForegroundServiceStartNotAllowedException
        // on Android 12+ when exemption was somehow lost), directly launch the alarm activity.
        // Activities can ALWAYS be launched — there are no background activity launch restrictions
        // when the PendingIntent comes from AlarmManager.setAlarmClock().
        if (!serviceStarted) {
            SecureLogger.w(TAG, "Service failed to start — launching AlarmTriggerActivity as fallback")
            try {
                val activityIntent = Intent(context, com.vyllo.music.ui.alarm.AlarmTriggerActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("alarm_id", alarmId)
                    putExtra("alarm_label", alarmLabel)
                    putExtra("alarm_time", java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date()))
                    putExtra("sound_type", soundType)
                    putExtra("song_url", songUrl)
                    putExtra("volume", volume)
                    putExtra("vibration", vibrationEnabled)
                }
                context.startActivity(activityIntent)
                SecureLogger.d(TAG, "✓ AlarmTriggerActivity launched as fallback")
            } catch (e2: Exception) {
                SecureLogger.e(TAG, "✗ Fallback activity launch also failed", e2)
            }
            // Release wake lock since service won't do it
            releaseReceiverWakeLock()
        }
    }
}

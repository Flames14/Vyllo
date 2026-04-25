package com.vyllo.music.data.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.AlarmModel
import com.vyllo.music.domain.model.DayOfWeek
import com.vyllo.music.service.AlarmTriggerReceiver
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager responsible for scheduling and canceling alarms using AlarmManager.
 *
 * CRITICAL: Always uses setAlarmClock() for scheduling — this is the ONLY reliable
 * method for alarms on Android. It:
 *   - Fires at the exact time (no Doze batching)
 *   - Wakes the device from deep sleep
 *   - Shows the system alarm icon in the status bar
 *   - Grants foreground service start exemption on Android 12+
 *   - Does NOT require SCHEDULE_EXACT_ALARM permission
 *   - Survives app process death
 *   - Works when screen is locked
 *   - Works when app is killed from recents
 */
@Singleton
class AlarmSchedulerManager @Inject constructor(
    private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedule an alarm with proper handling for different Android versions.
     */
    fun schedule(alarm: AlarmModel) {
        scheduleAt(alarm, calculateNextTriggerTime(alarm))
    }

    /**
     * Schedule an alarm for an exact wall-clock timestamp.
     * Used for snooze flows where the original hour/minute should not be reinterpreted.
     *
     * CRITICAL: ALWAYS uses setAlarmClock() — never falls back to setAndAllowWhileIdle().
     * setAlarmClock() does NOT require the SCHEDULE_EXACT_ALARM permission.
     * The previous code checked canScheduleExactAlarms() and fell back to an unreliable
     * method, which caused alarms to silently fail on most Android 12+ devices where
     * that permission is NOT granted by default.
     */
    fun scheduleAt(alarm: AlarmModel, triggerTime: Long) {

        SecureLogger.d("AlarmScheduler", "=== SCHEDULING ALARM ===")
        SecureLogger.d("AlarmScheduler", "ID=${alarm.id} for ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(triggerTime))}")
        SecureLogger.d("AlarmScheduler", "soundType=${alarm.soundType}")
        SecureLogger.d("AlarmScheduler", "downloadedSongUrl=${alarm.downloadedSongUrl}")
        SecureLogger.d("AlarmScheduler", "downloadedSongTitle=${alarm.downloadedSongTitle}")
        SecureLogger.d("AlarmScheduler", "triggerTime epoch=$triggerTime, now=${System.currentTimeMillis()}, delta=${triggerTime - System.currentTimeMillis()}ms")

        // Build the broadcast intent targeting our receiver
        val intent = Intent(context, AlarmTriggerReceiver::class.java).apply {
            // Set a unique action so the PendingIntent is distinct per alarm
            action = "com.vyllo.music.ALARM_TRIGGER_${alarm.id}"
            putExtra("alarm_id", alarm.id)
            putExtra("alarm_label", alarm.label)
            putExtra("sound_type", alarm.soundType.name)
            alarm.downloadedSongUrl?.let { putExtra("song_url", it) }
            alarm.downloadedSongTitle?.let { putExtra("song_title", it) }
            putExtra("volume", alarm.volume)
            putExtra("gradual_volume", alarm.gradualVolume)
            putExtra("vibration_enabled", alarm.vibrationEnabled)
        }
        
        val operationPendingIntent = createPendingIntent(alarm.id, intent)
        
        // Cancel any existing alarm with this ID to avoid duplicates
        try {
            alarmManager.cancel(operationPendingIntent)
        } catch (e: Exception) {
            SecureLogger.e("AlarmScheduler", "Error canceling existing alarm", e)
        }
        
        if (!alarm.isEnabled) {
            SecureLogger.d("AlarmScheduler", "Alarm is disabled, not scheduling")
            return
        }

        // Create the "show" intent — what happens when user taps alarm icon in status bar
        // This should open the app's main activity (not trigger the alarm)
        val showIntent = Intent(context, com.vyllo.music.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt() + 10000, // Offset to avoid collision with operation PI
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ALWAYS use setAlarmClock() — the ONLY reliable method for alarms.
        //
        // DO NOT check canScheduleExactAlarms() here!
        // setAlarmClock() does NOT require SCHEDULE_EXACT_ALARM permission.
        // Only setExact()/setExactAndAllowWhileIdle() require that permission.
        //
        // setAlarmClock() provides:
        // 1. Exact timing (no Doze batching)
        // 2. Device wake from deep sleep
        // 3. System alarm icon in status bar
        // 4. Foreground service start exemption on Android 12+
        // 5. Works in all scenarios: screen locked, app killed, background cleared
        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, operationPendingIntent)
            SecureLogger.d("AlarmScheduler", "✓ setAlarmClock() SUCCESS for ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(triggerTime))}")
        } catch (e: SecurityException) {
            SecureLogger.e("AlarmScheduler", "SecurityException in setAlarmClock (should never happen)", e)
            // Last resort fallback — setAlarmClock should never throw SecurityException
            // but if it does, try setExactAndAllowWhileIdle as degraded mode
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    operationPendingIntent
                )
                SecureLogger.w("AlarmScheduler", "Fallback to setExactAndAllowWhileIdle")
            } catch (e2: Exception) {
                SecureLogger.e("AlarmScheduler", "All scheduling methods failed", e2)
                // Last-last resort
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    operationPendingIntent
                )
                SecureLogger.w("AlarmScheduler", "Final fallback to setAndAllowWhileIdle")
            }
        } catch (e: Exception) {
            SecureLogger.e("AlarmScheduler", "Unexpected error scheduling alarm", e)
        }
    }

    /**
     * Cancel a scheduled alarm.
     */
    fun cancel(alarm: AlarmModel) {
        val intent = Intent(context, AlarmTriggerReceiver::class.java).apply {
            action = "com.vyllo.music.ALARM_TRIGGER_${alarm.id}"
            putExtra("alarm_id", alarm.id)
        }

        val pendingIntent = findPendingIntent(alarm.id, intent)
        
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
            SecureLogger.d("AlarmScheduler", "Alarm ${alarm.id} cancelled")
        }
    }

    /**
     * Calculate the next trigger time in milliseconds since epoch.
     */
    fun calculateNextTriggerTime(alarm: AlarmModel): Long {
        val now = Calendar.getInstance()
        val alarmTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If no repeat days set, it's a one-time alarm
        if (alarm.repeatDays.isEmpty()) {
            // If the time has already passed today, schedule for tomorrow
            if (alarmTime.timeInMillis <= now.timeInMillis) {
                alarmTime.add(Calendar.DAY_OF_YEAR, 1)
            }
            SecureLogger.d("AlarmScheduler", "One-time alarm scheduled for: ${alarmTime.time}")
            return alarmTime.timeInMillis
        }

        // Find the next day the alarm should trigger
        val currentDay = now.get(Calendar.DAY_OF_WEEK) - 1 // Convert to 0=Sunday
        val currentDayOfWeek = DayOfWeek.values()[currentDay]

        // Check if alarm should trigger today
        if (alarm.shouldTriggerOnDay(currentDayOfWeek)) {
            if (alarmTime.timeInMillis > now.timeInMillis) {
                // Today, hasn't passed yet
                SecureLogger.d("AlarmScheduler", "Alarm scheduled for today: ${alarmTime.time}")
                return alarmTime.timeInMillis
            }
        }

        // Find next occurrence
        for (i in 1..7) {
            val nextDay = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, i)
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val nextDayOfWeek = DayOfWeek.values()[(currentDay + i) % 7]
            if (alarm.shouldTriggerOnDay(nextDayOfWeek)) {
                SecureLogger.d("AlarmScheduler", "Alarm scheduled for day $i: ${nextDay.time}")
                return nextDay.timeInMillis
            }
        }

        // Fallback: tomorrow at the same time
        alarmTime.add(Calendar.DAY_OF_YEAR, 1)
        SecureLogger.d("AlarmScheduler", "Fallback alarm scheduled for: ${alarmTime.time}")
        return alarmTime.timeInMillis
    }

    /**
     * Check if exact alarm permission is granted.
     * Note: This is for UI display purposes only — setAlarmClock() does NOT require this.
     * Only setExact()/setExactAndAllowWhileIdle() require it.
     */
    fun hasExactAlarmPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Get intent to request exact alarm permission (Android 12+).
     * Note: This is for UI/settings purposes only — setAlarmClock() does NOT need this.
     */
    fun getExactAlarmPermissionIntent(): Intent {
        return Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            data = android.net.Uri.parse("package:${context.packageName}")
        }
    }

    private fun createPendingIntent(alarmId: Long, intent: Intent): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun findPendingIntent(alarmId: Long, intent: Intent): PendingIntent? {
        return PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

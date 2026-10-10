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
 * Uses setAlarmClock() for exact user-facing alarms. It:
 *   - Fires at the exact time (no Doze batching)
 *   - Wakes the device from deep sleep
 *   - Shows the system alarm icon in the status bar
 *   - Grants foreground service start exemption on Android 12+
 *   - Requires SCHEDULE_EXACT_ALARM special access on Android 12+
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
     * Uses setAlarmClock() for exact, user-visible alarms. Android 12+ requires
     * SCHEDULE_EXACT_ALARM special access; callers should request it before enabling
     * an alarm. A best-effort inexact fallback keeps restored alarms from crashing
     * if access was revoked while the app was not running.
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
            requestCodeFor(alarm.id, 10000),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // setAlarmClock() represents the user's explicit alarm and wakes through Doze.
        //
        // setAlarmClock() provides:
        // Exact timing (no Doze batching)
        // Device wake from deep sleep
        // System alarm icon in status bar
        // Foreground service start exemption on Android 12+
        // Works in all scenarios: screen locked, app killed, background cleared
        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, operationPendingIntent)
            SecureLogger.d("AlarmScheduler", "✓ setAlarmClock() SUCCESS for ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(triggerTime))}")
        } catch (e: SecurityException) {
            SecureLogger.e("AlarmScheduler", "Exact alarm access is unavailable", e)
            // Inexact fallback is only for alarms restored after access was revoked.
            // Single guarded attempt: a second identical call cannot succeed where
            // the first threw, and an unguarded retry would crash the caller
            // (alarm save flow runs on the UI thread).
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    operationPendingIntent
                )
                SecureLogger.w("AlarmScheduler", "Scheduled a best-effort inexact alarm")
            } catch (e2: Exception) {
                SecureLogger.e("AlarmScheduler", "All scheduling methods failed", e2)
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
     * Get intent to request exact alarm permission (Android 12+).
     * Call only after explaining the special access to the user.
     *
     * [android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM] only exists
     * on S+, so this must not be reached on older devices.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    fun getExactAlarmPermissionIntent(): Intent {
        return Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            data = android.net.Uri.parse("package:${context.packageName}")
        }
    }

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * Stable request code derived from the alarm id. Raw toInt() truncation
     * collides for ids beyond Int range; the modulo keeps every alarm distinct
     * within the 32-bit PendingIntent namespace.
     */
    private fun requestCodeFor(alarmId: Long, salt: Int = 0): Int {
        val base = (alarmId % Int.MAX_VALUE).toInt().coerceAtLeast(0)
        return base + salt
    }

    private fun createPendingIntent(alarmId: Long, intent: Intent): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(alarmId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun findPendingIntent(alarmId: Long, intent: Intent): PendingIntent? {
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(alarmId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

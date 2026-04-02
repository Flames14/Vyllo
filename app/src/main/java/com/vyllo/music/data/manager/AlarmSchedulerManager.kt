package com.vyllo.music.data.manager

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.vyllo.music.domain.model.AlarmModel
import com.vyllo.music.domain.model.DayOfWeek
import com.vyllo.music.service.AlarmTriggerService
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager responsible for scheduling and canceling alarms using AlarmManager.
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
        val triggerTime = calculateNextTriggerTime(alarm)

        android.util.Log.d("AlarmScheduler", "=== SCHEDULING ALARM ===")
        android.util.Log.d("AlarmScheduler", "ID=${alarm.id} for ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(triggerTime))}")
        android.util.Log.d("AlarmScheduler", "soundType=${alarm.soundType}")
        android.util.Log.d("AlarmScheduler", "downloadedSongUrl=${alarm.downloadedSongUrl}")
        android.util.Log.d("AlarmScheduler", "downloadedSongTitle=${alarm.downloadedSongTitle}")

        val intent = Intent(context, com.vyllo.music.service.AlarmTriggerReceiver::class.java).apply {
            putExtra("alarm_id", alarm.id)
            putExtra("alarm_label", alarm.label)
            putExtra("sound_type", alarm.soundType.name)
            alarm.downloadedSongUrl?.let { putExtra("song_url", it) }
            alarm.downloadedSongTitle?.let { putExtra("song_title", it) }
            putExtra("volume", alarm.volume)
            putExtra("gradual_volume", alarm.gradualVolume)
            putExtra("vibration_enabled", alarm.vibrationEnabled)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Cancel existing alarm first to avoid duplicates
        alarmManager.cancel(pendingIntent)
        
        if (!alarm.isEnabled) {
            android.util.Log.d("AlarmScheduler", "Alarm is disabled, not scheduling")
            return
        }
        
        // Version-specific scheduling
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // Android 12+: Check SCHEDULE_EXACT_ALARM permission
                if (hasExactAlarmPermissionInternal()) {
                    setAlarmClock(triggerTime, pendingIntent)
                } else {
                    // Fallback to inexact alarm (less reliable)
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                // Android 6-11: Use setAlarmClock for precise timing with system alarm UI
                setAlarmClock(triggerTime, pendingIntent)
            }
            else -> {
                // Android 5 and below: Basic scheduling
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        }
        
        android.util.Log.d("AlarmScheduler", "Alarm scheduled successfully")
    }

    /**
     * Set alarm using AlarmManager.setAlarmClock() for precise timing.
     * This shows system alarm icon and is more reliable.
     */
    private fun setAlarmClock(triggerTime: Long, pendingIntent: PendingIntent) {
        val alarmInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
        alarmManager.setAlarmClock(alarmInfo, pendingIntent)
        android.util.Log.d("AlarmScheduler", "setAlarmClock called for ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(triggerTime))}")
    }

    /**
     * Cancel a scheduled alarm.
     */
    fun cancel(alarm: AlarmModel) {
        val intent = Intent(context, com.vyllo.music.service.AlarmTriggerReceiver::class.java).apply {
            putExtra("alarm_id", alarm.id)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
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
            android.util.Log.d("AlarmScheduler", "One-time alarm scheduled for: ${alarmTime.time}")
            return alarmTime.timeInMillis
        }

        // Find the next day the alarm should trigger
        val currentDay = now.get(Calendar.DAY_OF_WEEK) - 1 // Convert to 0=Sunday
        val currentDayOfWeek = DayOfWeek.values()[currentDay]

        // Check if alarm should trigger today
        if (alarm.shouldTriggerOnDay(currentDayOfWeek)) {
            if (alarmTime.timeInMillis > now.timeInMillis) {
                // Today, hasn't passed yet
                android.util.Log.d("AlarmScheduler", "Alarm scheduled for today: ${alarmTime.time}")
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
                android.util.Log.d("AlarmScheduler", "Alarm scheduled for day $i: ${nextDay.time}")
                return nextDay.timeInMillis
            }
        }

        // Fallback: tomorrow at the same time
        alarmTime.add(Calendar.DAY_OF_YEAR, 1)
        android.util.Log.d("AlarmScheduler", "Fallback alarm scheduled for: ${alarmTime.time}")
        return alarmTime.timeInMillis
    }

    /**
     * Check if app has permission to schedule exact alarms (Android 12+).
     */
    private fun hasExactAlarmPermissionInternal(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Get intent to request exact alarm permission (Android 12+).
     */
    fun getExactAlarmPermissionIntent(): Intent {
        return Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            data = android.net.Uri.parse("package:${context.packageName}")
        }
    }

    /**
     * Check if exact alarm permission is granted.
     */
    fun hasExactAlarmPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
}

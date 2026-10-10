package com.vyllo.music.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vyllo.music.R
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.download.DownloadDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that re-schedules all enabled alarms after device reboot
 * and whenever the wall clock changes.
 *
 * CRITICAL: This receiver must NOT depend on Hilt/Dagger injection because
 * when the device boots, the app process may not be fully initialized.
 * We use direct database access instead of injected repositories.
 *
 * TIME_SET / TIMEZONE_CHANGED matter because alarms are RTC_WAKEUP timestamps:
 * a manual clock change or timezone hop silently shifts every alarm.
 */
class AlarmBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmBootReceiver"
        private const val CHANNEL_ID = "vyllo_alarm_system"
        private const val NOTICE_ID = 0xA1A4
    }

    /**
     * Posts a one-tap notice sending the user to the exact-alarm special-access
     * screen. Best-effort and silent on failure: on Android 13+ without
     * POST_NOTIFICATIONS there is no channel to speak through, so we skip.
     */
    private fun notifyExactAlarmAccessNeeded(context: Context, alarmCount: Int) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                SecureLogger.w(TAG, "Exact-alarm access missing but no notification permission; skipping notice")
                return
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Alarm system",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply { description = "Reminders needed to keep alarms exact" }
                )
            }
            val settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pending = PendingIntent.getActivity(
                context,
                0,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(context.getString(R.string.notif_exact_alarm_title))
                .setContentText(
                    context.resources.getQuantityString(R.plurals.notif_exact_alarm_text, alarmCount, alarmCount)
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            nm.notify(NOTICE_ID, notification)
        } catch (e: Exception) {
            SecureLogger.e(TAG, "Could not post exact-alarm notice", e)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val isBoot = action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        val isClockChange = action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        if (!isBoot && !isClockChange) {
            return
        }

        SecureLogger.d(TAG, "Re-scheduling alarms (trigger=$action)")

        // CRITICAL: Do NOT use Hilt here. Access database directly to ensure
        // alarms are re-scheduled even if the DI framework hasn't initialized.
        // Run DB operations in a coroutine scope since DAO methods are suspend functions.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = DownloadDatabase.getInstance(context)
                val alarmDao = db.alarmDao()
                val enabledAlarms = alarmDao.getEnabledAlarmsList()

                if (enabledAlarms.isEmpty()) {
                    SecureLogger.d(TAG, "No enabled alarms to re-schedule")
                    return@launch
                }

                // Use AlarmSchedulerManager directly (no DI needed)
                val scheduler = com.vyllo.music.data.manager.AlarmSchedulerManager(context)

                enabledAlarms.forEach { alarmEntity ->
                    try {
                        scheduler.schedule(alarmEntity.toAlarmModel())
                    } catch (e: Exception) {
                        SecureLogger.e(TAG, "Failed to re-schedule alarm ${alarmEntity.id}", e)
                    }
                }

                SecureLogger.d(TAG, "Successfully re-scheduled ${enabledAlarms.size} alarm(s) after reboot")

                // If the user (or the OEM) revoked exact-alarm access while we were
                // dead, every alarm above silently fell back to inexact delivery —
                // an alarm that fires minutes late is a broken alarm. Say so loudly
                // so the user can re-grant with one tap instead of missing mornings.
                if (!scheduler.canScheduleExactAlarms()) {
                    notifyExactAlarmAccessNeeded(context, enabledAlarms.size)
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to re-schedule alarms after reboot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

package com.vyllo.music.service.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import com.vyllo.music.R
import com.vyllo.music.domain.model.SoundType
import com.vyllo.music.service.AlarmTriggerService

/**
 * Builds alarm notification channel + foreground notification.
 * Extracted from AlarmTriggerService — no behavior change.
 */
object AlarmNotificationFactory {
    const val CHANNEL_ID = "vyllo_alarm_channel_silent"

    /**
     * Create notification channel for alarms (Android 8+).
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.alarm_channel_desc)
                enableVibration(false) // We handle vibration manually!
                setSound(null, null) // WE HANDLE SOUND MANUALLY!
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableLights(true)
                setShowBadge(true)
                setBypassDnd(true)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Stable per-alarm request codes. Notification actions previously shared the
     * fixed codes 0/1/2 across ALL alarms, so overlapping alarms clobbered each
     * other's dismiss/snooze intents. The alarm id is also attached as an extra
     * so the service acts on the right alarm even if fields were overwritten.
     */
    private fun requestCodeFor(alarmId: Long, slot: Int): Int {
        val base = (alarmId % 100000).toInt().coerceAtLeast(0)
        return base * 10 + slot.coerceIn(0, 9)
    }

    /**
     * Create notification displayed when alarm triggers.
     */
    fun createNotification(
        context: Context,
        alarmId: Long,
        alarmLabel: String,
        soundType: SoundType,
        songUrl: String?,
        volume: Int,
        vibrationEnabled: Boolean
    ): Notification {
        val dismissIntent = Intent(context, AlarmTriggerService::class.java).apply {
            action = AlarmTriggerService.ACTION_DISMISS
            putExtra("alarm_id", alarmId)
        }
        val dismissPendingIntent = PendingIntent.getService(
            context,
            requestCodeFor(alarmId, 1),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, AlarmTriggerService::class.java).apply {
            action = AlarmTriggerService.ACTION_SNOOZE
            putExtra("alarm_id", alarmId)
        }
        val snoozePendingIntent = PendingIntent.getService(
            context,
            requestCodeFor(alarmId, 2),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent for the full-screen alarm activity
        val fullScreenIntent = Intent(context, com.vyllo.music.ui.alarm.AlarmTriggerActivity::class.java).apply {
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
            context,
            requestCodeFor(alarmId, 0),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_alarm_title))
            .setContentText(if (alarmLabel.isNotBlank()) alarmLabel else context.getString(R.string.alarm_notif_tap_action))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.alarm_dismiss), dismissPendingIntent)
            .addAction(android.R.drawable.ic_menu_recent_history, context.getString(R.string.alarm_snooze), snoozePendingIntent)
            .build()
    }
}

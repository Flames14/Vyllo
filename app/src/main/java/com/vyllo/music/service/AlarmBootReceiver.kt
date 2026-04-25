package com.vyllo.music.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.download.DownloadDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that re-schedules all enabled alarms after device reboot.
 *
 * CRITICAL: This receiver must NOT depend on Hilt/Dagger injection because
 * when the device boots, the app process may not be fully initialized.
 * We use direct database access instead of injected repositories.
 */
class AlarmBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON" &&
            intent.action != "com.htc.intent.action.QUICKBOOT_POWERON") {
            return
        }

        SecureLogger.d(TAG, "Device boot detected — re-scheduling alarms")

        // CRITICAL: Do NOT use Hilt here. Access database directly to ensure
        // alarms are re-scheduled even if the DI framework hasn't initialized.
        // Run DB operations in a coroutine scope since DAO methods are suspend functions.
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
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to re-schedule alarms after reboot", e)
            }
        }
    }
}

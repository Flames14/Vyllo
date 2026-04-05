package com.vyllo.music.service

import com.vyllo.music.core.security.SecureLogger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vyllo.music.data.alarm.AlarmDao
import com.vyllo.music.data.download.DownloadDatabase
import com.vyllo.music.data.manager.AlarmSchedulerManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver that re-schedules all enabled alarms after device reboot.
 * 
 * CRITICAL: Without this, all alarms are lost when the device restarts.
 */
@AndroidEntryPoint
class AlarmBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alarmScheduler: AlarmSchedulerManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && 
            intent.action != "android.intent.action.QUICKBOOT_POWERON" &&
            intent.action != "com.htc.intent.action.QUICKBOOT_POWERON") {
            return
        }

        // Re-schedule all enabled alarms from database
        scope.launch {
            try {
                val db = DownloadDatabase.getInstance(context)
                val alarmDao = db.alarmDao()
                val enabledAlarms = alarmDao.getEnabledAlarmsList()
                
                enabledAlarms.forEach { alarmEntity ->
                    alarmScheduler.schedule(alarmEntity.toAlarmModel())
                }
                
                if (enabledAlarms.isNotEmpty()) {
                    SecureLogger.d(TAG, "Re-scheduled ${enabledAlarms.size} alarms after reboot")
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to re-schedule alarms after reboot", e)
            }
        }
    }

    companion object {
        private const val TAG = "AlarmBootReceiver"
    }
}

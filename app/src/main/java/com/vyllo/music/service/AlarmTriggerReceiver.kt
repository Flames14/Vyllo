package com.vyllo.music.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.vyllo.music.data.download.DownloadDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receiver that handles the alarm trigger from AlarmManager.
 * It starts the AlarmTriggerService as a foreground service.
 */
class AlarmTriggerReceiver : BroadcastReceiver() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra("alarm_id", -1L)
        
        android.util.Log.d("AlarmTriggerReceiver", "=== ALARM TRIGGERED ===")
        android.util.Log.d("AlarmTriggerReceiver", "Received alarm_id: $alarmId")
        
        if (alarmId == -1L) {
            android.util.Log.e("AlarmTriggerReceiver", "No alarm_id provided")
            return
        }
        
        // Fetch full alarm details from database
        serviceScope.launch {
            try {
                val db = DownloadDatabase.getInstance(context)
                val alarm = db.alarmDao().getAlarmById(alarmId)
                
                if (alarm == null) {
                    android.util.Log.e("AlarmTriggerReceiver", "Alarm not found for ID: $alarmId")
                    return@launch
                }
                
                android.util.Log.d("AlarmTriggerReceiver", "=== ALARM DETAILS ===")
                android.util.Log.d("AlarmTriggerReceiver", "ID: ${alarm.id}")
                android.util.Log.d("AlarmTriggerReceiver", "Label: ${alarm.label}")
                android.util.Log.d("AlarmTriggerReceiver", "SoundType: ${alarm.soundType}")
                android.util.Log.d("AlarmTriggerReceiver", "DownloadedSongUrl: ${alarm.downloadedSongUrl}")
                android.util.Log.d("AlarmTriggerReceiver", "DownloadedSongTitle: ${alarm.downloadedSongTitle}")
                android.util.Log.d("AlarmTriggerReceiver", "Volume: ${alarm.volume}")
                android.util.Log.d("AlarmTriggerReceiver", "GradualVolume: ${alarm.gradualVolume}")
                android.util.Log.d("AlarmTriggerReceiver", "VibrationEnabled: ${alarm.vibrationEnabled}")
                
                val serviceIntent = Intent(context, AlarmTriggerService::class.java).apply {
                    action = intent.action
                    putExtra("alarm_id", alarm.id)
                    putExtra("alarm_label", alarm.label)
                    putExtra("sound_type", alarm.soundType)
                    putExtra("song_url", alarm.downloadedSongUrl)
                    putExtra("song_title", alarm.downloadedSongTitle)
                    putExtra("volume", alarm.volume)
                    putExtra("gradual_volume", alarm.gradualVolume)
                    putExtra("vibration_enabled", alarm.vibrationEnabled)
                }
                
                android.util.Log.d("AlarmTriggerReceiver", 
                    "Starting AlarmTriggerService: id=${alarm.id}, soundType=${alarm.soundType}, url=${alarm.downloadedSongUrl}")
                
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                android.util.Log.e("AlarmTriggerReceiver", "Failed to fetch alarm details", e)
            }
        }
    }
}

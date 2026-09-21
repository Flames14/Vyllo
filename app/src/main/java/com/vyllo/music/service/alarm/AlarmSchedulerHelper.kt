package com.vyllo.music.service.alarm

import android.os.Handler
import android.os.Vibrator
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.manager.AlarmSchedulerManager
import com.vyllo.music.domain.repository.AlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Handles reschedule / snooze / dismiss / stop orchestration.
 * Extracted from AlarmTriggerService — no behavior change.
 */
class AlarmSchedulerHelper(
    private val alarmRepository: AlarmRepository,
    private val alarmSchedulerManager: AlarmSchedulerManager,
    private val scope: CoroutineScope,
    private val playbackController: AlarmPlaybackController,
    private val wakeLockManager: AlarmWakeLockManager,
    private val handler: Handler,
    private val autoDismissRunnable: Runnable,
    private val vibratorProvider: () -> Vibrator?
) {
    companion object {
        private const val TAG = "AlarmTriggerService"
    }

    /**
     * Handle dismiss action.
     */
    fun dismissAlarm(alarmId: Long, stopSelf: () -> Unit) {
        handler.removeCallbacks(autoDismissRunnable)
        // setAlarmClock() schedules are one-shot. Without re-registering the next
        // occurrence here, a repeating alarm dies permanently after the first
        // dismissal — which reads to the user as "the alarm stopped working".
        rescheduleNextOccurrence(alarmId)
        stopAlarm()
        stopSelf()
    }

    /**
     * Re-register the next occurrence for repeating alarms.
     *
     * One-shot alarms are intentionally left disabled after dismissal (standard
     * alarm behaviour); repeating alarms are rescheduled onto their next enabled
     * weekday. Snooze paths already reschedule explicitly and skip this.
     */
    fun rescheduleNextOccurrence(alarmId: Long) {
        if (alarmId <= 0L) return
        scope.launch {
            try {
                val alarm = alarmRepository.getAlarmById(alarmId)
                if (alarm != null && alarm.isEnabled && alarm.repeatDays.isNotEmpty()) {
                    alarmSchedulerManager.schedule(alarm)
                    SecureLogger.d(TAG, "Re-scheduled repeating alarm $alarmId after dismissal")
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to re-schedule alarm after dismissal", e)
            }
        }
    }

    /**
     * Handle snooze action — reschedules the alarm for 5 minutes from now.
     */
    fun snoozeAlarm(alarmId: Long, stopSelf: () -> Unit) {
        handler.removeCallbacks(autoDismissRunnable)
        playbackController.stopPlayback(vibratorProvider())

        scope.launch {
            try {
                val alarm = alarmId.takeIf { it > 0 }?.let { id ->
                    alarmRepository.getAlarmById(id)
                }

                if (alarm != null) {
                    val snoozedAlarm = alarm.copy(
                        isEnabled = true
                    )
                    val snoozeAt = Calendar.getInstance().apply {
                        add(Calendar.MINUTE, 5)
                    }.timeInMillis
                    alarmSchedulerManager.scheduleAt(snoozedAlarm, snoozeAt)
                    SecureLogger.d(TAG, "Alarm snoozed for 5 minutes: ${java.util.Date(snoozeAt)}")
                } else {
                    SecureLogger.w(TAG, "Cannot snooze: alarm model not found (id=$alarmId)")
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to snooze alarm", e)
            } finally {
                stopAlarm()
                stopSelf()
            }
        }
    }

    /**
     * Stop alarm playback and cleanup everything.
     */
    fun stopAlarm() {
        playbackController.stopPlayback(vibratorProvider())
        wakeLockManager.release()
    }
}

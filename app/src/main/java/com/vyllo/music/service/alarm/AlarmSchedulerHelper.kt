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
        scope.launch {
            try {
                finalizeDismissal(alarmId)
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Failed to finalize alarm dismissal", e)
            } finally {
                stopAlarm()
                stopSelf()
            }
        }
    }

    /**
     * Persist the post-dismissal state before the service stops.
     *
     * Repeating alarms are re-registered onto their next enabled weekday.
     * One-shot alarms are disabled so AlarmBootReceiver does not re-arm them
     * after a reboot (standard alarm behaviour).
     */
    private suspend fun finalizeDismissal(alarmId: Long) {
        if (alarmId <= 0L) return
        val alarm = alarmRepository.getAlarmById(alarmId) ?: return
        if (!alarm.isEnabled) return
        if (alarm.repeatDays.isEmpty()) {
            alarmRepository.toggleAlarm(alarm.id, false)
            SecureLogger.d(TAG, "Disabled one-shot alarm $alarmId after dismissal")
        } else {
            alarmSchedulerManager.schedule(alarm)
            SecureLogger.d(TAG, "Re-scheduled repeating alarm $alarmId after dismissal")
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

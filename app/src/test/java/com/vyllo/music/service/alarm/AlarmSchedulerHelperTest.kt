package com.vyllo.music.service.alarm

import android.os.Handler
import android.os.Vibrator
import com.vyllo.music.data.manager.AlarmSchedulerManager
import com.vyllo.music.domain.model.AlarmModel
import com.vyllo.music.domain.model.DayOfWeek
import com.vyllo.music.domain.repository.AlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Regression coverage for the dismissal race: persistence must finish before
 * the service stops, one-shot alarms must be disabled (so AlarmBootReceiver
 * does not re-arm them), and repeating alarms must be re-scheduled.
 *
 * Handler is mocked because unit tests have no main Looper; the ordering
 * assertions run inside the stopSelf callback, which only executes after the
 * coroutine body completed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlarmSchedulerHelperTest {

    private val alarmRepository: AlarmRepository = mock()
    private val alarmSchedulerManager: AlarmSchedulerManager = mock()
    private val playbackController: AlarmPlaybackController = mock()
    private val wakeLockManager: AlarmWakeLockManager = mock()
    private val handler: Handler = mock()
    private val vibrator: Vibrator = mock()

    private fun newHelper(scope: CoroutineScope) = AlarmSchedulerHelper(
        alarmRepository = alarmRepository,
        alarmSchedulerManager = alarmSchedulerManager,
        scope = scope,
        playbackController = playbackController,
        wakeLockManager = wakeLockManager,
        handler = handler,
        autoDismissRunnable = Runnable {},
        vibratorProvider = { vibrator }
    )

    @Test
    fun `dismissing a one-shot alarm disables it before the service stops`() = runTest {
        val oneShot = AlarmModel(id = 7L, hour = 7, minute = 30)
        whenever(alarmRepository.getAlarmById(7L)).thenReturn(oneShot)

        val events = mutableListOf<String>()
        whenever(alarmRepository.toggleAlarm(7L, false)).thenAnswer { events.add("disable") }

        newHelper(this).dismissAlarm(7L) { events.add("stop") }
        advanceUntilIdle()

        assertEquals(listOf("disable", "stop"), events)
        verify(alarmSchedulerManager, never()).schedule(any())
        verify(playbackController).stopPlayback(vibrator)
        verify(wakeLockManager).release()
        verify(handler).removeCallbacks(any())
    }

    @Test
    fun `dismissing a repeating alarm reschedules it for its next weekday`() = runTest {
        val repeating = AlarmModel(
            id = 9L,
            hour = 6,
            minute = 45,
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
        )
        whenever(alarmRepository.getAlarmById(9L)).thenReturn(repeating)

        val events = mutableListOf<String>()
        whenever(alarmSchedulerManager.schedule(repeating)).thenAnswer { events.add("reschedule") }

        newHelper(this).dismissAlarm(9L) { events.add("stop") }
        advanceUntilIdle()

        assertEquals(listOf("reschedule", "stop"), events)
        verify(alarmRepository, never()).toggleAlarm(any(), any())
    }

    @Test
    fun `dismissing a disabled alarm performs no persistence`() = runTest {
        val disabled = AlarmModel(id = 3L, hour = 8, minute = 0, isEnabled = false)
        whenever(alarmRepository.getAlarmById(3L)).thenReturn(disabled)

        var stopped = false
        newHelper(this).dismissAlarm(3L) { stopped = true }
        advanceUntilIdle()

        assertTrue(stopped)
        verify(alarmRepository, never()).toggleAlarm(any(), any())
        verify(alarmSchedulerManager, never()).schedule(any())
    }

    @Test
    fun `dismissing an alarm that no longer exists still stops cleanly`() = runTest {
        whenever(alarmRepository.getAlarmById(99L)).thenReturn(null)

        var stopped = false
        newHelper(this).dismissAlarm(99L) { stopped = true }
        advanceUntilIdle()

        assertTrue(stopped)
        verify(alarmRepository, never()).toggleAlarm(any(), any())
        verify(alarmSchedulerManager, never()).schedule(any())
    }

    @Test
    fun `repository failure still stops the service`() = runTest {
        whenever(alarmRepository.getAlarmById(5L)).thenThrow(RuntimeException("db unavailable"))

        var stopped = false
        newHelper(this).dismissAlarm(5L) { stopped = true }
        advanceUntilIdle()

        assertTrue(stopped)
        verify(playbackController).stopPlayback(vibrator)
        verify(wakeLockManager).release()
    }

    @Test
    fun `invalid alarm id skips persistence entirely`() = runTest {
        var stopped = false
        newHelper(this).dismissAlarm(0L) { stopped = true }
        advanceUntilIdle()

        assertTrue(stopped)
        verify(alarmRepository, never()).getAlarmById(any())
    }
}

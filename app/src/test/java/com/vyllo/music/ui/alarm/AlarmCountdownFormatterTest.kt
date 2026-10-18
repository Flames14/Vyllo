package com.vyllo.music.ui.alarm

import com.vyllo.music.domain.model.AlarmModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the "which unit do we show" decision for the next-alarm countdown.
 *
 * This used to be inline in AlarmViewModel against live string resources, so the
 * boundary behaviour (seconds vs minutes vs hours) was never exercised.
 */
class AlarmCountdownFormatterTest {

    private val minute = 60_000L
    private val hour = 3_600_000L

    private fun alarm(hourOfDay: Int = 7, minuteOfHour: Int = 0) =
        AlarmModel(hour = hourOfDay, minute = minuteOfHour)

    @Test
    fun `no enabled alarms is idle`() {
        assertEquals(AlarmCountdown.Idle, AlarmCountdownFormatter.next(emptyList(), now = 0L))
    }

    @Test
    fun `a trigger time in the past reads as ringing`() {
        val a = alarm()
        val trigger = a.calculateNextTriggerTime()
        assertEquals(AlarmCountdown.Ringing, AlarmCountdownFormatter.next(listOf(a), now = trigger + 1_000L))
    }

    @Test
    fun `more than an hour away reports hours and minutes`() {
        val a = alarm()
        val now = a.calculateNextTriggerTime() - (2 * hour + 5 * minute)
        assertEquals(AlarmCountdown.HoursMinutes(2, 5), AlarmCountdownFormatter.next(listOf(a), now))
    }

    @Test
    fun `minutes away reports minutes and seconds`() {
        val a = alarm()
        val now = a.calculateNextTriggerTime() - (5 * minute + 30_000L)
        assertEquals(AlarmCountdown.MinutesSeconds(5, 30), AlarmCountdownFormatter.next(listOf(a), now))
    }

    @Test
    fun `under a minute reports only seconds`() {
        val a = alarm()
        val now = a.calculateNextTriggerTime() - 30_000L
        assertEquals(AlarmCountdown.Seconds(30), AlarmCountdownFormatter.next(listOf(a), now))
    }

    @Test
    fun `the earliest of several alarms wins`() {
        val seven = alarm(hourOfDay = 7)
        val nine = alarm(hourOfDay = 9)
        val earliest = minOf(seven.calculateNextTriggerTime(), nine.calculateNextTriggerTime())
        assertEquals(
            AlarmCountdown.HoursMinutes(1, 0),
            AlarmCountdownFormatter.next(listOf(seven, nine), now = earliest - hour)
        )
    }
}

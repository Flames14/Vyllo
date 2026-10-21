package com.vyllo.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepScheduleTest {

    private val monday = 1
    private val saturday = 6
    private val workdays = SleepSchedule(enabled = true, days = setOf(1, 2, 3, 4, 5), hour = 23, minute = 30)

    @Test
    fun `not due before the scheduled minute`() {
        assertFalse(workdays.isDue(monday, 23 * 60 + 29, todayEpochDay = 10, lastFiredEpochDay = -1))
    }

    @Test
    fun `due at or after the scheduled minute on a selected day`() {
        assertTrue(workdays.isDue(monday, 23 * 60 + 30, 10, -1))
        assertTrue(workdays.isDue(monday, 23 * 60 + 45, 10, -1))
    }

    @Test
    fun `not due on a day that is not selected`() {
        assertFalse(workdays.isDue(saturday, 23 * 60 + 45, 10, -1))
    }

    @Test
    fun `fires only once per calendar day`() {
        assertFalse(workdays.isDue(monday, 23 * 60 + 45, todayEpochDay = 10, lastFiredEpochDay = 10))
    }

    @Test
    fun `disabled schedule never fires`() {
        val off = workdays.copy(enabled = false)
        assertFalse(off.isDue(monday, 23 * 60 + 45, 10, -1))
    }

    @Test
    fun `encode and decode round trip`() {
        val schedule = SleepSchedule(enabled = true, days = setOf(5, 1, 3), hour = 7, minute = 5)
        val decoded = SleepSchedule.decode(SleepSchedule.encode(schedule))
        assertEquals(schedule, decoded)
    }

    @Test
    fun `decode rejects junk and clamps ranges`() {
        assertEquals(SleepSchedule(), SleepSchedule.decode(null))
        assertEquals(SleepSchedule(), SleepSchedule.decode("garbage"))
        assertEquals(
            SleepSchedule(enabled = true, days = setOf(1, 7), hour = 23, minute = 59),
            SleepSchedule.decode("1;1,7,9,0;99;99")
        )
    }

    @Test
    fun `enabled with no valid days decodes as disabled`() {
        val decoded = SleepSchedule.decode("1;;10;00")
        assertFalse(decoded.enabled)
        assertTrue(decoded.days.isEmpty())
    }
}

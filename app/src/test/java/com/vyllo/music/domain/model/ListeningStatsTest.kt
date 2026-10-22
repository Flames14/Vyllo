package com.vyllo.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningStatsTest {

    @Test
    fun `formats minutes hours and mixed durations`() {
        assertEquals("0 min", ListeningTimeFormat.format(0))
        assertEquals("0 min", ListeningTimeFormat.format(-5_000))
        assertEquals("0 min", ListeningTimeFormat.format(59_999))
        assertEquals("1 min", ListeningTimeFormat.format(60_000))
        assertEquals("59 min", ListeningTimeFormat.format(59 * 60_000L))
        assertEquals("1 h", ListeningTimeFormat.format(60 * 60_000L))
        assertEquals("1 h 1 min", ListeningTimeFormat.format(61 * 60_000L))
        assertEquals("12 h 30 min", ListeningTimeFormat.format(750 * 60_000L))
    }

    @Test
    fun `average listen divides total time by plays`() {
        val stats = ListeningStats(totalPlays = 4, totalListenedMs = 400_000L)
        assertEquals(100_000L, stats.averageListenMs)
    }

    @Test
    fun `average listen is zero without plays`() {
        assertEquals(0L, ListeningStats(totalListenedMs = 900_000L).averageListenMs)
    }

    @Test
    fun `hasData requires both a known track and some listening`() {
        assertFalse(ListeningStats().hasData)
        assertFalse(ListeningStats(totalTracks = 3).hasData)
        assertTrue(ListeningStats(totalTracks = 3, totalPlays = 2).hasData)
        assertTrue(ListeningStats(totalTracks = 1, totalListenedMs = 1_000L).hasData)
        assertTrue(ListeningStats(totalTracks = 1, totalPlays = 1).hasData)
    }
}

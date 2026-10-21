package com.vyllo.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamQualityTest {

    private data class Stream(val bitrate: Int)

    private val streams = listOf(Stream(160_000), Stream(128_000), Stream(50_000))

    @Test
    fun `auto keeps every stream`() {
        assertEquals(streams, StreamQuality.AUTO.withinBitrate(streams) { it.bitrate })
    }

    @Test
    fun `high keeps everything at or below the ceiling`() {
        assertEquals(streams, StreamQuality.HIGH.withinBitrate(streams) { it.bitrate })
    }

    @Test
    fun `medium drops the opus tier but keeps lower fallbacks`() {
        assertEquals(
            listOf(Stream(128_000), Stream(50_000)),
            StreamQuality.MEDIUM.withinBitrate(streams) { it.bitrate }
        )
    }

    @Test
    fun `low keeps the low bitrate opus tier`() {
        assertEquals(
            listOf(Stream(50_000)),
            StreamQuality.LOW.withinBitrate(streams) { it.bitrate }
        )
    }

    @Test
    fun `nothing under the ceiling falls back to all streams`() {
        val allHigh = listOf(Stream(160_000), Stream(128_000))
        assertEquals(allHigh, StreamQuality.LOW.withinBitrate(allHigh) { it.bitrate })
    }

    @Test
    fun `unknown keys resolve to auto`() {
        assertEquals(StreamQuality.AUTO, StreamQuality.from("Ultra"))
        assertEquals(StreamQuality.AUTO, StreamQuality.from(null))
        assertEquals(StreamQuality.MEDIUM, StreamQuality.from("Medium"))
    }
}

package com.vyllo.music

import com.vyllo.music.data.repository.delegates.StreamSelector
import com.vyllo.music.data.repository.delegates.SubtitleLyricsDelegate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests the real SubtitleLyricsDelegate — not a local reimplementation.
 */
class SubtitleParserTest {

    private lateinit var delegate: SubtitleLyricsDelegate

    @Before
    fun setUp() {
        delegate = SubtitleLyricsDelegate(StreamSelector())
    }

    @Test
    fun testTimestampParsing() {
        assertEquals(13900L, delegate.parseSubtitleTimestamp("00:13.90"))
        assertEquals(13900L, delegate.parseSubtitleTimestamp("00:13.900"))
        assertEquals(13900L, delegate.parseSubtitleTimestamp("00:00:13.900"))
        assertEquals(13900L, delegate.parseSubtitleTimestamp("00:00:13,900"))
        assertEquals(3661000L, delegate.parseSubtitleTimestamp("01:01:01.000"))
        assertEquals(61000L, delegate.parseSubtitleTimestamp("01:01.000"))
    }

    @Test
    fun testWebVTTPrasing() {
        val vtt = """
            WEBVTT

            1
            00:00:01.200 --> 00:00:03.400
            <c.colorE5E5E5>Hello World</c>

            2
            00:00:04.500 --> 00:00:06.000
            <b>Second</b> Line
        """.trimIndent()

        val parsed = delegate.parseSubtitles(vtt)
        assertEquals(2, parsed.size)
        assertEquals(1200L, parsed[0].startTimeMs)
        assertEquals("Hello World", parsed[0].content)
        assertEquals(4500L, parsed[1].startTimeMs)
        assertEquals("Second Line", parsed[1].content)
    }

    @Test
    fun testSRTParsing() {
        val srt = """
            1
            00:01:10,500 --> 00:01:12,800
            This is a test

            2
            00:01:15,000 --> 00:01:18,000
            Another test line
        """.trimIndent()

        val parsed = delegate.parseSubtitles(srt)
        assertEquals(2, parsed.size)
        assertEquals(70500L, parsed[0].startTimeMs)
        assertEquals("This is a test", parsed[0].content)
        assertEquals(75000L, parsed[1].startTimeMs)
        assertEquals("Another test line", parsed[1].content)
    }

    @Test
    fun testSubtitlesParsingResetOnBlankLine() {
        val vtt = """
            WEBVTT

            cue-id-1
            00:00:01.000 --> 00:00:02.000
            Hello

            cue-id-2
            00:00:03.000 --> 00:00:04.000
            World
        """.trimIndent()

        val parsed = delegate.parseSubtitles(vtt)
        assertEquals(2, parsed.size)
        assertEquals("Hello", parsed[0].content)
        assertEquals("World", parsed[1].content)
    }

    @Test
    fun parseSubtitleTimestamp_rejectsMalformedInput() {
        assertTrue(delegate.parseSubtitleTimestamp("") == null)
        assertTrue(delegate.parseSubtitleTimestamp("not-a-time") == null)
        assertTrue(delegate.parseSubtitleTimestamp("99") == null)
    }

    @Test
    fun parseSubtitles_emptyContent_returnsEmptyList() {
        assertTrue(delegate.parseSubtitles("").isEmpty())
        assertTrue(delegate.parseSubtitles("WEBVTT\n\n").isEmpty())
    }
}

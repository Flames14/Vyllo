package com.vyllo.music.data

import com.vyllo.music.data.lyrics.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTagCleaningTest {

    @Test
    fun testParseSyncedLyricsCleansAgentAndBgTags() {
        val lrcInput = """
            [00:10.50]{agent:v1}Hello World
            [00:12.00]{bg}Yeah yeah
            [00:15.30]{agent:v2}How are you?
            [00:20.00]Clean line without tags
        """.trimIndent()

        val parsed = LrcParser.parseSyncedLyrics(lrcInput)
        
        assertEquals(4, parsed.size)
        
        assertEquals(10500L, parsed[0].startTimeMs)
        assertEquals("Hello World", parsed[0].content)
        
        assertEquals(12000L, parsed[1].startTimeMs)
        assertEquals("Yeah yeah", parsed[1].content)
        
        assertEquals(15300L, parsed[2].startTimeMs)
        assertEquals("How are you?", parsed[2].content)
        
        assertEquals(20000L, parsed[3].startTimeMs)
        assertEquals("Clean line without tags", parsed[3].content)
    }

    @Test
    fun testParseSyncedLyricsIgnoresWordSyncLines() {
        val lrcInput = """
            [00:10.00]Hello world
            <Hello:10.00:10.50|world:10.50:11.00>
            [00:12.00]Next line
        """.trimIndent()

        val parsed = LrcParser.parseSyncedLyrics(lrcInput)
        
        // The word-sync line should be ignored by line parser (doesn't match time regex)
        assertEquals(2, parsed.size)
        assertEquals("Hello world", parsed[0].content)
        assertEquals("Next line", parsed[1].content)
    }
}

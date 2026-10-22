package com.vyllo.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordTimingTest {

    private val words = listOf(
        LyricWord(0, 500, "hello"),
        LyricWord(500, 900, "world"),
        LyricWord(1_200, 1_600, "again")
    )

    @Test
    fun `before the line starts nothing is sung`() {
        assertEquals(0, WordTiming.sungWordCount(words, -100))
        assertNull(WordTiming.activeWordIndex(words, -100))
    }

    @Test
    fun `count grows as words finish`() {
        assertEquals(0, WordTiming.sungWordCount(words, 0))
        assertEquals(1, WordTiming.sungWordCount(words, 500))
        assertEquals(3, WordTiming.sungWordCount(words, 5_000))
    }

    @Test
    fun `active word is the one in progress`() {
        assertEquals(0, WordTiming.activeWordIndex(words, 100))
        assertEquals(1, WordTiming.activeWordIndex(words, 700))
        assertNull(WordTiming.activeWordIndex(words, 1_000))
    }

    @Test
    fun `after the last word there is no active word`() {
        assertNull(WordTiming.activeWordIndex(words, 2_000))
        assertEquals(3, WordTiming.sungWordCount(words, 2_000))
    }

    @Test
    fun `word timing needs at least two words`() {
        assertFalse(WordTiming.isWordTimed(emptyList()))
        assertFalse(WordTiming.isWordTimed(listOf(words.first())))
        assertTrue(WordTiming.isWordTimed(words))
    }
}

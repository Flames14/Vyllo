package com.vyllo.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSourceTest {

    @Test
    fun `default order covers every source exactly once`() {
        val order = LyricsSource.parse(null)
        assertEquals(LyricsSource.entries.size, order.size)
        assertEquals(order.size, order.distinct().size)
    }

    @Test
    fun `round trip preserves custom order`() {
        val custom = listOf(
            LyricsSource.NETEASE,
            LyricsSource.LRCLIB_EXACT,
            LyricsSource.PAXSENIX
        ) + LyricsSource.DEFAULT_ORDER.filterNot { it in setOf(LyricsSource.NETEASE, LyricsSource.LRCLIB_EXACT, LyricsSource.PAXSENIX) }

        assertEquals(custom, LyricsSource.parse(LyricsSource.encode(custom)))
    }

    @Test
    fun `unknown tokens are dropped and missing sources appended`() {
        val parsed = LyricsSource.parse("bogus,netease")
        assertEquals(LyricsSource.NETEASE, parsed.first())
        assertEquals(LyricsSource.entries.size, parsed.size)
        assertTrue(LyricsSource.PAXSENIX in parsed)
    }

    @Test
    fun `duplicate tokens collapse`() {
        val parsed = LyricsSource.parse("kugou,kugou,lyrics_ovh")
        assertEquals(LyricsSource.entries.size, parsed.size)
        assertEquals(LyricsSource.entries.size, parsed.distinct().size)
    }

    @Test
    fun `blank string falls back to defaults`() {
        assertEquals(LyricsSource.DEFAULT_ORDER, LyricsSource.parse(""))
    }
}

package com.vyllo.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioStationTest {

    private fun song(url: String, title: String = url) =
        MusicItem(id = url, title = title, url = url)

    @Test
    fun `seed is first even if related echoes it`() {
        val seed = song("a")
        val station = RadioStation.build(seed, listOf(song("a"), song("b")))
        assertEquals(listOf("a", "b"), station.map { it.url })
    }

    @Test
    fun `related order is preserved`() {
        val station = RadioStation.build(song("a"), listOf(song("c"), song("b")))
        assertEquals(listOf("a", "c", "b"), station.map { it.url })
    }

    @Test
    fun `duplicates collapse`() {
        val station = RadioStation.build(song("a"), listOf(song("b"), song("b"), song("c")))
        assertEquals(3, station.size)
        assertEquals(station.map { it.url }.distinct().size, station.size)
    }

    @Test
    fun `limit is respected`() {
        val related = (1..50).map { song("r$it") }
        assertEquals(RadioStation.DEFAULT_LIMIT, RadioStation.build(song("a"), related).size)
        assertEquals(5, RadioStation.build(song("a"), related, limit = 5).size)
    }

    @Test
    fun `identifierless entries are dropped`() {
        val station = RadioStation.build(
            seed = song("a"),
            related = listOf(MusicItem(id = "", title = "?", url = ""), song("b"))
        )
        assertEquals(listOf("a", "b"), station.map { it.url })
    }

    @Test
    fun `non-positive limit yields empty station`() {
        assertTrue(RadioStation.build(song("a"), listOf(song("b")), limit = 0).isEmpty())
    }
}

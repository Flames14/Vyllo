package com.vyllo.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryArtistsTest {

    private fun song(
        title: String,
        url: String,
        uploader: String,
        thumbnailUrl: String = ""
    ) = MusicItem(title = title, url = url, uploader = uploader, thumbnailUrl = thumbnailUrl)

    @Test
    fun `collapses Topic suffix and casing into one artist`() {
        val artists = LibraryArtists.fromSongs(
            listOf(
                song("A", "u1", "Arijit Singh - Topic"),
                song("B", "u2", "Arijit Singh"),
                song("C", "u3", "arijit  singh")
            )
        )

        assertEquals(1, artists.size)
        assertEquals("arijit singh", artists[0].key)
        assertEquals(3, artists[0].songCount)
    }

    @Test
    fun `counts distinct songs only`() {
        val artists = LibraryArtists.fromSongs(
            listOf(
                song("A", "u1", "Artist"),
                song("A", "u1", "Artist"),
                song("B", "u2", "Artist")
            )
        )

        assertEquals(1, artists.size)
        assertEquals(2, artists[0].songCount)
    }

    @Test
    fun `skips songs with a blank uploader`() {
        val artists = LibraryArtists.fromSongs(
            listOf(
                song("A", "u1", ""),
                song("B", "u2", "   "),
                song("C", "u3", "Real Artist")
            )
        )

        assertEquals(1, artists.size)
        assertEquals("Real Artist", artists[0].name)
    }

    @Test
    fun `picks the most frequent name and thumbnail`() {
        val artists = LibraryArtists.fromSongs(
            listOf(
                song("A", "u1", "deadmau5", "thumb-a"),
                song("B", "u2", "deadmau5", "thumb-a"),
                song("C", "u3", "Deadmau5", "thumb-b")
            )
        )

        assertEquals(1, artists.size)
        assertEquals("deadmau5", artists[0].name)
        assertEquals("thumb-a", artists[0].thumbnailUrl)
    }

    @Test
    fun `sorts by song count then name`() {
        val artists = LibraryArtists.fromSongs(
            listOf(
                song("A", "u1", "Zed"),
                song("B", "u2", "alpha"),
                song("C", "u3", "Busy"),
                song("D", "u4", "Busy")
            )
        )

        assertEquals(listOf("Busy", "alpha", "Zed"), artists.map { it.name })
        assertEquals(listOf(2, 1, 1), artists.map { it.songCount })
    }

    @Test
    fun `display name trims Topic suffix and collapses whitespace`() {
        assertEquals("Arijit Singh", LibraryArtists.displayName("  Arijit   Singh - Topic "))
    }

    @Test
    fun `empty input yields no artists`() {
        assertTrue(LibraryArtists.fromSongs(emptyList()).isEmpty())
    }
}

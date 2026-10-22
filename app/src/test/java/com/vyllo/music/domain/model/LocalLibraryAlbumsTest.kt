package com.vyllo.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLibraryAlbumsTest {

    @Test
    fun `albums group by normalized title and artist and ignore blank album tags`() {
        val songs = listOf(
            song("1", "Blue", "Artist", 1L),
            song("2", " blue ", " artist ", 2L),
            song("3", "Blue", "Different Artist", 3L),
            song("4", " ", "Artist", 4L)
        )

        val albums = LocalLibraryAlbums.fromSongs(songs)

        assertEquals(2, albums.size)
        assertEquals(listOf(2, 1), albums.map { it.songs.size })
        assertEquals("Blue", albums.first().title)
        assertEquals("Artist", albums.first().artist)
    }

    @Test
    fun `album artwork is the first nonblank track thumbnail`() {
        val album = LocalLibraryAlbums.fromSongs(
            listOf(
                song("1", "Album", "Artist", 2L, ""),
                song("2", "Album", "Artist", 1L, "https://example.test/cover.jpg")
            )
        ).single()

        assertEquals("https://example.test/cover.jpg", album.coverUrl)
        assertTrue(album.songs.all { it.album.equals("Album", ignoreCase = true) })
    }

    private fun song(url: String, album: String, artist: String, addedAt: Long, thumbnail: String = "") =
        LocalLibrarySong(
            url = url,
            title = "Song $url",
            artist = artist,
            album = album,
            thumbnailUrl = thumbnail,
            addedAt = addedAt
        )
}

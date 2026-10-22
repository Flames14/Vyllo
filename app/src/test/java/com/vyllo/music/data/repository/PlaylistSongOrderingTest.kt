package com.vyllo.music.data.repository

import com.vyllo.music.domain.model.PlaylistSongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistSongOrderingTest {

    @Test
    fun `move rewrites monotonically increasing order keys and preserves row data`() {
        val songs = listOf(
            song("a", addedAt = 10L),
            song("b", addedAt = 20L),
            song("c", addedAt = 30L)
        )

        val moved = PlaylistSongOrdering.move(songs, fromIndex = 2, toIndex = 0, nowMillis = 100L)

        requireNotNull(moved)
        assertEquals(listOf("c", "a", "b"), moved.map { it.url })
        assertEquals(listOf(100L, 101L, 102L), moved.map { it.addedAt })
        assertEquals("Song c", moved.first().title)
    }

    @Test
    fun `move rejects equal and out of range indices`() {
        val songs = listOf(song("a", 1L), song("b", 2L))
        assertNull(PlaylistSongOrdering.move(songs, 0, 0, nowMillis = 10L))
        assertNull(PlaylistSongOrdering.move(songs, -1, 0, nowMillis = 10L))
        assertNull(PlaylistSongOrdering.move(songs, 0, songs.size, nowMillis = 10L))
    }

    @Test
    fun `insert places a song at the requested zero based index`() {
        val songs = listOf(song("a", 10L), song("b", 20L), song("c", 30L))
        val inserted = PlaylistSongOrdering.insert(
            songs = songs,
            song = song("new", 0L),
            position = 1,
            nowMillis = 100L
        )
        assertEquals(listOf("a", "new", "b", "c"), inserted.map { it.url })
        assertEquals(listOf(100L, 101L, 102L, 103L), inserted.map { it.addedAt })
    }

    @Test
    fun `insert clamps position to playlist bounds`() {
        val songs = listOf(song("a", 10L), song("b", 20L))
        val first = PlaylistSongOrdering.insert(songs, song("new", 0L), -5, 100L)
        val last = PlaylistSongOrdering.insert(songs, song("new", 0L), 50, 100L)
        assertEquals(listOf("new", "a", "b"), first.map { it.url })
        assertEquals(listOf("a", "b", "new"), last.map { it.url })
    }

    private fun song(url: String, addedAt: Long) = PlaylistSongEntity(
        playlistId = 7L,
        url = url,
        title = "Song $url",
        uploader = "Artist",
        thumbnailUrl = "https://example.test/$url.jpg",
        addedAt = addedAt
    )
}

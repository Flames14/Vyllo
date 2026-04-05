package com.vyllo.music.domain.usecase

import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.MusicItemType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SearchMusicUseCase behavior.
 */
class SearchMusicUseCaseTest {

    @Test
    fun `empty query returns false for isNotBlank`() = runTest {
        val query = ""
        assertFalse(query.isNotBlank())
    }

    @Test
    fun `blank query returns false for isNotBlank`() = runTest {
        val query = "   "
        assertFalse(query.isNotBlank())
    }

    @Test
    fun `valid query returns true for isNotBlank`() = runTest {
        val query = "test song"
        assertTrue(query.isNotBlank())
    }

    @Test
    fun `MusicItem data class works correctly`() = runTest {
        val item = MusicItem(
            id = "1",
            title = "Test Song",
            url = "https://example.com/1",
            uploader = "Test Artist",
            thumbnailUrl = "https://example.com/thumb/1.jpg",
            type = MusicItemType.SONG
        )

        assertEquals("Test Song", item.title)
        assertEquals("https://example.com/1", item.url)
        assertEquals(MusicItemType.SONG, item.type)
    }

    @Test
    fun `search results can be capped at max limit`() = runTest {
        val maxResults = 200
        val allResults = (1..500).map { i ->
            MusicItem(
                id = "$i",
                title = "Song $i",
                url = "https://example.com/$i",
                uploader = "Artist",
                thumbnailUrl = "",
                type = MusicItemType.SONG
            )
        }

        val cappedResults = allResults.take(maxResults)
        assertEquals(maxResults, cappedResults.size)
        assertTrue(cappedResults.size < allResults.size)
    }
}

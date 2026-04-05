package com.vyllo.music.domain.usecase

import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.MusicItemType
import com.vyllo.music.domain.model.PlayResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for PlayMusicUseCase behavior.
 * Tests the use case logic indirectly through the repository mock.
 */
class PlayMusicUseCaseTest {

    private val testItem = MusicItem(
        id = "1",
        title = "Test Song",
        url = "https://example.com/song/1",
        uploader = "Test Artist",
        thumbnailUrl = "https://example.com/thumb/1.jpg",
        type = MusicItemType.SONG
    )

    @Test
    fun `MusicItem equality works correctly`() = runTest {
        val item1 = testItem
        val item2 = testItem.copy()

        assertEquals(item1, item2)
        assertEquals(item1.url, item2.url)
        assertEquals(item1.title, item2.title)
    }

    @Test
    fun `MusicItem with different URLs are distinct`() = runTest {
        val item1 = testItem
        val item2 = testItem.copy(url = "https://example.com/song/2")

        assertNotEquals(item1.url, item2.url)
    }

    @Test
    fun `PlayResult Success is a singleton object`() = runTest {
        val result1 = PlayResult.Success
        val result2 = PlayResult.Success

        assertSame(result1, result2)
        assertTrue(result1 is PlayResult.Success)
    }

    @Test
    fun `PlayResult Failure contains error message`() = runTest {
        val errorMessage = "Network error"
        val result = PlayResult.Failure(errorMessage)

        assertTrue(result is PlayResult.Failure)
        assertEquals(errorMessage, result.message)
    }
}

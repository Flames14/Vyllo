package com.vyllo.music.domain.usecase

import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.MusicItemType
import com.vyllo.music.domain.repository.IMusicRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SearchMusicUseCaseTest {

    private lateinit var repository: IMusicRepository
    private lateinit var useCase: SearchMusicUseCase

    private val sampleResults = listOf(
        MusicItem(
            id = "1",
            title = "Test Song",
            url = "https://example.com/1",
            uploader = "Test Artist",
            thumbnailUrl = "https://example.com/thumb/1.jpg",
            type = MusicItemType.SONG
        )
    )

    @Before
    fun setUp() {
        repository = mock()
        useCase = SearchMusicUseCase(repository)
    }

    @Test
    fun `blank query returns empty list without calling repository`() = runTest {
        val result = useCase("   ")
        assertTrue(result.isEmpty())
        verify(repository, never()).searchMusic(any(), any())
    }

    @Test
    fun `empty query returns empty list without calling repository`() = runTest {
        val result = useCase("")
        assertTrue(result.isEmpty())
        verify(repository, never()).searchMusic(any(), any())
    }

    @Test
    fun `valid query delegates to repository with maintainSession true by default`() = runTest {
        whenever(repository.searchMusic("test song", true)).thenReturn(sampleResults)

        val result = useCase("test song")

        assertEquals(sampleResults, result)
        verify(repository).searchMusic("test song", true)
    }

    @Test
    fun `valid query with maintainSession false passes through`() = runTest {
        whenever(repository.searchMusic("query", false)).thenReturn(sampleResults)

        val result = useCase("query", maintainSession = false)

        assertEquals(sampleResults, result)
        verify(repository).searchMusic("query", false)
    }

    @Test
    fun `repository returning empty list propagates empty result`() = runTest {
        whenever(repository.searchMusic(any(), any())).thenReturn(emptyList())

        val result = useCase("no results")

        assertTrue(result.isEmpty())
    }
}

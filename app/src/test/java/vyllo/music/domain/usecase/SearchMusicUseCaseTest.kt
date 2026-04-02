package com.vyllo.music.domain.usecase

import com.vyllo.music.data.IMusicRepository
import com.vyllo.music.data.LyricsResponse
import com.vyllo.music.data.download.DownloadEntity
import com.vyllo.music.data.download.PlaylistEntity
import com.vyllo.music.data.download.PlaylistSongEntity
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.MusicItemType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for SearchMusicUseCase.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchMusicUseCaseTest {

    private class FakeRepository : IMusicRepository {
        var searchQuery = ""
        var shouldReturnEmpty = false
        
        override suspend fun getSuggestions(query: String): List<String> = emptyList()
        
        override suspend fun searchMusic(query: String, maintainSession: Boolean): List<MusicItem> {
            searchQuery = query
            return if (shouldReturnEmpty) emptyList() 
            else if (query.isBlank()) emptyList()
            else listOf(
                MusicItem(
                    id = "1",
                    title = "Test Song",
                    url = "https://example.com/1",
                    uploader = "Test Artist",
                    thumbnailUrl = "https://example.com/thumb/1.jpg",
                    type = MusicItemType.SONG
                )
            )
        }
        
        override suspend fun loadMoreResults(): List<MusicItem> = emptyList()
        override suspend fun getTrendingMusic(): List<MusicItem> = emptyList()
        override suspend fun getStreamUrl(url: String, force: Boolean): String? = null
        override suspend fun getLocalStreamUrl(url: String): String? = null
        override suspend fun getRelatedSongs(url: String): List<MusicItem> = emptyList()
        override fun getAllPlaylists(): Flow<List<PlaylistEntity>> = flowOf(emptyList())
        override suspend fun createPlaylist(name: String) {}
        override suspend fun deletePlaylist(playlist: PlaylistEntity) {}
        override suspend fun addSongToPlaylist(playlistId: Long, item: MusicItem) {}
        override suspend fun removeSongFromPlaylist(playlistId: Long, url: String) {}
        override fun getSongsInPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>> = flowOf(emptyList())
        override fun getAllDownloads(): Flow<List<DownloadEntity>> = flowOf(emptyList())
        override fun downloadSong(item: MusicItem) {}
        override suspend fun deleteDownload(url: String) {}
        override suspend fun isDownloaded(url: String): Boolean = false
        override fun getRecentHistory(limit: Int): Flow<List<MusicItem>> = flowOf(emptyList())
        override suspend fun recordListen(item: MusicItem) {}
        override suspend fun clearHistory() {}
        override suspend fun getPatternSuggestions(): List<MusicItem> = emptyList()
        override suspend fun loadMoreRecommendations(): List<MusicItem> = emptyList()
        override fun loadSearchHistory(): List<String> = emptyList()
        override fun saveSearchQuery(query: String) {}
        override fun clearSearchHistory() {}
        override fun saveLyricsPreference(videoUrl: String, lrcId: Long) {}
        override fun getSavedLyricsId(videoUrl: String): Long? = null
        override fun isLiquidScrollEnabled(): Boolean = true
        override suspend fun getLyrics(title: String, artist: String, duration: Long, url: String): LyricsResponse? = null
    }

    @Test
    fun `invoke with empty query returns empty list`() = runTest {
        val repository = FakeRepository()
        val useCase = SearchMusicUseCase(repository)
        
        val result = useCase.invoke("")
        
        assertTrue(result.isEmpty())
    }

    @Test
    fun `invoke with blank query returns empty list`() = runTest {
        val repository = FakeRepository()
        val useCase = SearchMusicUseCase(repository)
        
        val result = useCase.invoke("   ")
        
        assertTrue(result.isEmpty())
    }

    @Test
    fun `invoke with valid query returns results`() = runTest {
        val repository = FakeRepository()
        val useCase = SearchMusicUseCase(repository)
        
        val result = useCase.invoke("test query")
        
        assertEquals(1, result.size)
        assertEquals("Test Song", result[0].title)
    }

    @Test
    fun `invoke passes query to repository`() = runTest {
        val repository = FakeRepository()
        val useCase = SearchMusicUseCase(repository)
        
        useCase.invoke("my search")
        
        assertEquals("my search", repository.searchQuery)
    }
}

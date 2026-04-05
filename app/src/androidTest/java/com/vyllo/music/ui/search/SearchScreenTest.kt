package com.vyllo.music.ui.search

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.vyllo.music.SearchViewModel
import com.vyllo.music.data.IMusicRepository
import com.vyllo.music.data.download.DownloadEntity
import com.vyllo.music.data.download.PlaylistEntity
import com.vyllo.music.data.download.PlaylistSongEntity
import com.vyllo.music.domain.model.LyricsResponse
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.usecase.SearchMusicUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test

class SearchScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun searchScreen_showsRecentHistoryWhenIdle() {
        val repository = FakeMusicRepository(
            searchHistory = mutableListOf("Coldplay", "Adele")
        )
        val viewModel = SearchViewModel(repository, SearchMusicUseCase(repository))

        composeRule.setContent {
            MaterialTheme {
                YTMSearchScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onPlay = {},
                    currentPlayingItem = null,
                    scrollState = rememberLazyListState()
                )
            }
        }

        composeRule.onNodeWithText("Recent searches").assertIsDisplayed()
        composeRule.onNodeWithText("Coldplay").assertIsDisplayed()
        composeRule.onNodeWithText("Adele").assertIsDisplayed()
    }

    @Test
    fun searchScreen_showsSuggestionsAfterTyping() {
        val repository = FakeMusicRepository(
            suggestions = mapOf("focus" to listOf("focus music", "focus piano"))
        )
        val viewModel = SearchViewModel(repository, SearchMusicUseCase(repository))

        composeRule.setContent {
            MaterialTheme {
                YTMSearchScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onPlay = {},
                    currentPlayingItem = null,
                    scrollState = rememberLazyListState()
                )
            }
        }

        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("focus")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("focus music").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("focus music").assertIsDisplayed()
        composeRule.onNodeWithText("focus piano").assertIsDisplayed()
    }
}

private class FakeMusicRepository(
    private val searchHistory: MutableList<String> = mutableListOf(),
    private val suggestions: Map<String, List<String>> = emptyMap(),
    private val searchResults: Map<String, List<MusicItem>> = emptyMap()
) : IMusicRepository {

    override suspend fun getSuggestions(query: String): List<String> = suggestions[query].orEmpty()
    override suspend fun searchMusic(query: String, maintainSession: Boolean): List<MusicItem> = searchResults[query].orEmpty()
    override suspend fun loadMoreResults(): List<MusicItem> = emptyList()
    override suspend fun getTrendingMusic(): List<MusicItem> = emptyList()
    override suspend fun getStreamUrl(url: String, force: Boolean, isVideo: Boolean): String? = null
    override suspend fun getLocalStreamUrl(url: String): String? = null
    override suspend fun getRelatedSongs(url: String): List<MusicItem> = emptyList()
    override fun getAllPlaylists(): Flow<List<PlaylistEntity>> = emptyFlow()
    override suspend fun createPlaylist(name: String) = Unit
    override suspend fun deletePlaylist(playlist: PlaylistEntity) = Unit
    override suspend fun addSongToPlaylist(playlistId: Long, item: MusicItem) = Unit
    override suspend fun removeSongFromPlaylist(playlistId: Long, url: String) = Unit
    override fun getSongsInPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>> = emptyFlow()
    override fun getAllDownloads(): Flow<List<DownloadEntity>> = emptyFlow()
    override fun downloadSong(item: MusicItem) = Unit
    override fun cancelDownload(url: String) = Unit
    override suspend fun deleteDownload(url: String) = Unit
    override suspend fun isDownloaded(url: String): Boolean = false
    override fun getRecentHistory(limit: Int): Flow<List<MusicItem>> = emptyFlow()
    override suspend fun recordListen(item: MusicItem) = Unit
    override suspend fun clearHistory() = Unit
    override suspend fun getPatternSuggestions(): List<MusicItem> = emptyList()
    override suspend fun loadMoreRecommendations(): List<MusicItem> = emptyList()
    override fun loadSearchHistory(): List<String> = searchHistory.toList()
    override fun saveSearchQuery(query: String) {
        if (query !in searchHistory) {
            searchHistory.add(0, query)
        }
    }
    override fun clearSearchHistory() {
        searchHistory.clear()
    }
    override fun saveLyricsPreference(videoUrl: String, lrcId: Long) = Unit
    override fun getSavedLyricsId(videoUrl: String): Long? = null
    override fun isLiquidScrollEnabled(): Boolean = false
    override suspend fun getLyrics(title: String, artist: String, duration: Long, url: String): LyricsResponse? = null
}

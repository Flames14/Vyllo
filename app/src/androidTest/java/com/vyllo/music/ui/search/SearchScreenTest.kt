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
import androidx.test.core.app.ApplicationProvider
import com.vyllo.music.SearchViewModel
import com.vyllo.music.domain.repository.IMusicRepository
import com.vyllo.music.domain.model.DownloadEntity
import com.vyllo.music.domain.model.PlaylistEntity
import com.vyllo.music.domain.model.PlaylistSongEntity
import com.vyllo.music.domain.model.LyricsResponse
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.usecase.SearchMusicUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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

        // Resource lookup, not a literal: the emulator carries a per-app locale,
        // so a hardcoded English label would make this test pass or fail depending
        // on the device rather than on the code.
        val recentLabel = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(com.vyllo.music.R.string.search_recent)
        composeRule.onNodeWithText(recentLabel).assertIsDisplayed()
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
    override suspend fun resolveTrack(url: String): MusicItem? = null
    override suspend fun getTrendingMusic(): List<MusicItem> = emptyList()
    override suspend fun getStreamUrl(url: String, force: Boolean, isVideo: Boolean): String? = null
    override suspend fun getLocalStreamUrl(url: String): String? = null
    override suspend fun getRelatedSongs(url: String, force: Boolean): List<MusicItem> = emptyList()
    override suspend fun getMoreRelatedSongs(url: String): List<MusicItem> = emptyList()
    override fun getAllPlaylists(): Flow<List<PlaylistEntity>> = emptyFlow()
    override suspend fun createPlaylist(name: String) = Unit
    override suspend fun deletePlaylist(playlist: PlaylistEntity) = Unit
    override suspend fun addSongToPlaylist(playlistId: Long, item: MusicItem): Boolean = true
    override suspend fun removeSongFromPlaylist(playlistId: Long, url: String) = Unit
    override suspend fun moveSongInPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int): Boolean = false
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
    override suspend fun getArtistSongs(artist: String, excludeUrls: Set<String>): List<MusicItem> = emptyList()
    override suspend fun getDiscoverSimilarSongs(title: String, artist: String, excludeUrls: Set<String>, excludeUploaders: Set<String>): List<MusicItem> = emptyList()
    override suspend fun getRelatedDiscoveryOrder(url: String, excludeUrls: Set<String>, limit: Int): List<MusicItem> = emptyList()
    override suspend fun getVideoStats(url: String): com.vyllo.music.domain.repository.VideoStats? = null
    override suspend fun getLyrics(title: String, artist: String, duration: Long, url: String): LyricsResponse? = null

    // --- Favorites / ratings ---
    // Backed by a real (in-memory) store rather than bare TODO()/constant
    // returns, so this fake keeps behaving consistently if search tests
    // ever start exercising ratings.
    private val ratings = mutableMapOf<String, Int>()
    private val ratedItems = mutableMapOf<String, MusicItem>()

    override suspend fun removeFromHistory(url: String) = Unit

    override suspend fun getRating(url: String): Int = ratings[url] ?: 0

    override fun observeLikedSongs(): Flow<List<MusicItem>> =
        flowOf(ratedItems.values.filter { ratings[it.url] == 1 })

    override fun observeDislikedSongs(): Flow<List<MusicItem>> =
        flowOf(ratedItems.values.filter { ratings[it.url] == -1 })

    override fun observeLikedCount(): Flow<Int> = flowOf(ratings.count { it.value == 1 })

    override suspend fun setRating(item: MusicItem, rating: Int): Boolean {
        if (item.url.isBlank()) return false
        if (rating == 1 || rating == -1) {
            ratings[item.url] = rating
            ratedItems[item.url] = item
        } else {
            ratings.remove(item.url)
            ratedItems.remove(item.url)
        }
        return true
    }

    override suspend fun clearAllRatings() {
        ratings.clear()
        ratedItems.clear()
    }
}

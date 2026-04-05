package com.vyllo.music

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.IMusicRepository
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.data.manager.PlaybackQueueManager
import com.vyllo.music.domain.usecase.GetHomeContentUseCase
import com.vyllo.music.domain.usecase.RecordListenUseCase
import com.vyllo.music.domain.usecase.LoadMoreRecommendationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val recentlyPlayed: List<MusicItem> = emptyList(),
    val exploreCategories: List<String> = listOf("New releases", "Charts", "Moods & genres", "Party", "Romance", "Sleep", "Workout"),
    val selectedExploreCategory: String? = null,
    val listenAgainItems: List<MusicItem> = emptyList(),
    val quickPicksItems: List<MusicItem> = emptyList(),
    val mixedForYouItems: List<MusicItem> = emptyList(),
    val newReleasesItems: List<MusicItem> = emptyList(),
    val trendingNowItems: List<MusicItem> = emptyList(),
    val chillItems: List<MusicItem> = emptyList(),
    val workoutItems: List<MusicItem> = emptyList(),
    val focusItems: List<MusicItem> = emptyList(),
    val quickPicksRows: List<List<MusicItem>> = emptyList(),
    val trendingNowRows: List<List<MusicItem>> = emptyList(),
    val selectedChip: String = "All",
    val exploreTrendingItems: List<MusicItem> = emptyList(),
    val isLoadingExplore: Boolean = false,
    val homeTitle: String = "Recommended For You",
    val isLoading: Boolean = false,
    val isLoadingMoreRecommendations: Boolean = false,
    val isLiquidScrollEnabled: Boolean = false,
    val isRefreshing: Boolean = false,
    val currentPlayingItem: MusicItem? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: IMusicRepository,
    private val getHomeContentUseCase: GetHomeContentUseCase,
    private val loadMoreRecommendationsUseCase: LoadMoreRecommendationsUseCase,
    private val recordListenUseCase: RecordListenUseCase,
    private val playbackQueueManager: PlaybackQueueManager
) : ViewModel() {

    companion object {
        private const val MAX_RECOMMENDED_ITEMS = 200
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // We keep selectedNavTab outside HomeUiState since it's a structural
    // navigation state directly manipulated by MainActivity.
    var selectedNavTab by mutableIntStateOf(0)

    init {
        _uiState.update { it.copy(isLiquidScrollEnabled = repository.isLiquidScrollEnabled()) }

        viewModelScope.launch {
            playbackQueueManager.currentPlayingItem.collectLatest { item ->
                _uiState.update { it.copy(currentPlayingItem = item) }
            }
        }

        viewModelScope.launch {
            repository.getRecentHistory().collectLatest { history ->
                _uiState.update {
                    it.copy(
                        recentlyPlayed = history,
                        listenAgainItems = history.take(10)
                    )
                }
            }
        }

        loadHomeContent()
    }

    fun loadHomeContent() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Fetch dedicated content instead of slicing a single list
            val trendingTask = async { repository.getTrendingMusic() }
            val newReleasesTask = async { repository.searchMusic("new music releases", maintainSession = false) }
            val recommendationsTask = async { getHomeContentUseCase() }

            val trending = trendingTask.await()
            val newReleases = newReleasesTask.await()
            val recommendations = recommendationsTask.await()
            
            val isHistoryEmpty = _uiState.value.recentlyPlayed.isEmpty()
            
            _uiState.update { state ->
                state.copy(
                    homeTitle = if (!isHistoryEmpty) "Recommended For You" else "Trending Now",
                    quickPicksRows = recommendations.take(8).chunked(2),
                    mixedForYouItems = recommendations.drop(8).take(6),
                    newReleasesItems = newReleases.take(8),
                    trendingNowItems = trending.take(8),
                    trendingNowRows = trending.take(8).chunked(2),
                    quickPicksItems = recommendations.drop(14),
                    isLoading = false
                )
            }
            
            loadMoodContent()
        }
    }

    private fun loadMoodContent() {
        viewModelScope.launch {
            val chillReq = async { repository.searchMusic("chill lofi music", maintainSession = false) }
            val workoutReq = async { repository.searchMusic("workout gym music", maintainSession = false) }
            val focusReq = async { repository.searchMusic("focus study music", maintainSession = false) }
            
            val chill = chillReq.await().take(8)
            val workout = workoutReq.await().take(8)
            val focus = focusReq.await().take(8)
            
            _uiState.update {
                it.copy(
                    chillItems = chill,
                    workoutItems = workout,
                    focusItems = focus
                )
            }
        }
    }

    fun onExploreCategorySelected(category: String) {
        if (_uiState.value.selectedExploreCategory == category) {
            _uiState.update { it.copy(selectedExploreCategory = null, exploreTrendingItems = emptyList()) }
            loadExploreContent()
            return
        }
        
        _uiState.update { it.copy(selectedExploreCategory = category, isLoadingExplore = true) }
        viewModelScope.launch {
            try {
                val query = when (category) {
                    "New releases" -> "new music releases official"
                    "Charts" -> "top music charts global official"
                    "Moods & genres" -> "moods and genres music mix"
                    "Party" -> "party playlist official"
                    "Romance" -> "romantic love songs playlist"
                    "Sleep" -> "sleep relaxing music"
                    "Workout" -> "workout gym motivation playlist"
                    else -> "$category playlist"
                }
                val items = repository.searchMusic(query, maintainSession = false)
                _uiState.update { it.copy(exploreTrendingItems = items) }
            } catch (e: Exception) {
                SecureLogger.e("HomeViewModel", "Explore category search failed", e)
            } finally {
                _uiState.update { it.copy(isLoadingExplore = false) }
            }
        }
    }

    fun loadExploreContent() {
        if (_uiState.value.exploreTrendingItems.isNotEmpty() && _uiState.value.selectedExploreCategory == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingExplore = true) }
            try {
                val items = repository.getTrendingMusic()
                _uiState.update { it.copy(exploreTrendingItems = items) }
            } catch (e: Exception) {
                SecureLogger.e("HomeViewModel", "Load explore content failed", e)
            } finally {
                _uiState.update { it.copy(isLoadingExplore = false) }
            }
        }
    }

    private var chipSelectionJob: Job? = null

    fun onChipSelected(chip: String) {
        // Cancel any previous chip selection request to avoid race conditions
        chipSelectionJob?.cancel()

        _uiState.update { it.copy(selectedChip = chip) }
        if (chip != "All") {
            chipSelectionJob = viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                val query = when (chip) {
                    "Relax" -> "relaxing chill music"
                    "Energize" -> "energetic upbeat music"
                    "Workout" -> "workout gym motivation music"
                    "Focus" -> "focus concentration study music"
                    "Commute" -> "driving road trip music"
                    else -> "trending music"
                }
                val results = repository.searchMusic(query)
                _uiState.update {
                    it.copy(
                        quickPicksRows = results.take(8).chunked(2),
                        mixedForYouItems = results.drop(8).take(6),
                        quickPicksItems = results.drop(14),
                        isLoading = false
                    )
                }
            }
        } else {
            loadHomeContent()
        }
    }

    fun addToRecentlyPlayed(item: MusicItem) {
        viewModelScope.launch {
            recordListenUseCase(item)
        }
    }

    fun loadMoreRecommendations() {
        if (_uiState.value.isLoadingMoreRecommendations) return
        if (_uiState.value.quickPicksItems.size >= MAX_RECOMMENDED_ITEMS) {
            SecureLogger.d("HomeViewModel", "Reached max recommended items, stopping load")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreRecommendations = true) }
            try {
                val moreItems = loadMoreRecommendationsUseCase()
                if (moreItems.isNotEmpty()) {
                    val currentSize = _uiState.value.quickPicksItems.size
                    val remainingSlots = MAX_RECOMMENDED_ITEMS - currentSize
                    val itemsToAdd = moreItems.take(remainingSlots)

                    _uiState.update { state ->
                        state.copy(quickPicksItems = state.quickPicksItems + itemsToAdd)
                    }

                    if (remainingSlots <= 0) {
                        SecureLogger.d("HomeViewModel", "Max recommended items reached")
                    }
                }
            } catch (e: Exception) {
                SecureLogger.e("HomeViewModel", "Load more recommendations failed", e)
            } finally {
                _uiState.update { it.copy(isLoadingMoreRecommendations = false) }
            }
        }
    }

    /**
     * Refresh all home content.
     */
    fun refreshAllContent() {
        if (_uiState.value.isRefreshing) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                // Fetch all content types in parallel
                val trending = async { repository.getTrendingMusic() }
                val newReleases = async { repository.searchMusic("new music releases", maintainSession = false) }
                val recommendations = async { getHomeContentUseCase() }

                val trendingResult = trending.await()
                val newReleasesResult = newReleases.await()
                val recommendationsResult = recommendations.await()

                val isHistoryEmpty = _uiState.value.recentlyPlayed.isEmpty()
                _uiState.update { state ->
                    state.copy(
                        homeTitle = if (!isHistoryEmpty) "Recommended For You" else "Trending Now",
                        quickPicksRows = recommendationsResult.take(8).chunked(2),
                        mixedForYouItems = recommendationsResult.drop(8).take(6),
                        newReleasesItems = newReleasesResult.take(8),
                        trendingNowItems = trendingResult.take(8),
                        trendingNowRows = trendingResult.take(8).chunked(2),
                        quickPicksItems = recommendationsResult.drop(14),
                        isRefreshing = false
                    )
                }
                loadMoodContent()
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false) }
                SecureLogger.e("HomeViewModel", "Refresh all content failed", e)
            }
        }
    }
}

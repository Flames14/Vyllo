package com.vyllo.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.repository.IMusicRepository
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.data.manager.HomeFeedCache
import com.vyllo.music.data.manager.HomeFeedSnapshot
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.domain.manager.PlaybackQueueManager
import com.vyllo.music.domain.model.PlaylistEntity
import com.vyllo.music.domain.usecase.GetHomeContentUseCase
import com.vyllo.music.domain.usecase.RecordListenUseCase
import com.vyllo.music.domain.usecase.LoadMoreRecommendationsUseCase
import com.vyllo.music.ui.home.HomeFeedShaper
import com.vyllo.music.ui.home.HomeQueries
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
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
    val exploreLoadFailed: Boolean = false,
    val homeTitle: String = "Recommended For You",

    /**
     * True only until the first section has content to show.
     *
     * Previously a single flag covering the whole feed, which meant the screen could
     * not distinguish "loading" from "this section came back empty".
     */
    val isLoading: Boolean = true,

    val isLoadingRecommendations: Boolean = false,
    val isLoadingTrending: Boolean = false,
    val isLoadingNewReleases: Boolean = false,
    val isLoadingMoods: Boolean = false,
    val isLoadingMoreRecommendations: Boolean = false,
    val isRefreshing: Boolean = false,

    /**
     * True when the first paint has nothing to show *and* at least one request
     * failed. Distinguishes "the feed is empty" (show the empty state) from "we
     * could not reach the network" (show an explanation and a retry).
     */
    val loadFailed: Boolean = false,

    val currentPlayingItem: MusicItem? = null,
    val pinnedPlaylistIds: List<Long> = emptyList(),
    val pinnedPlaylists: List<PlaylistEntity> = emptyList()
) {
    /** True when nothing is on screen yet, so the UI can show a full-screen loader. */
    val hasAnyContent: Boolean
        get() = quickPicksRows.isNotEmpty() || trendingNowRows.isNotEmpty() ||
            listenAgainItems.isNotEmpty() || mixedForYouItems.isNotEmpty() ||
            newReleasesItems.isNotEmpty() || trendingNowItems.isNotEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: IMusicRepository,
    private val getHomeContentUseCase: GetHomeContentUseCase,
    private val loadMoreRecommendationsUseCase: LoadMoreRecommendationsUseCase,
    private val recordListenUseCase: RecordListenUseCase,
    private val playbackQueueManager: PlaybackQueueManager,
    private val homeFeedCache: HomeFeedCache,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    companion object {
        private const val MAX_RECOMMENDED_ITEMS = 200

        /** Lets the independent section loads land before the cache is written. */
        private const val CACHE_SETTLE_MS = 1500L
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var availablePlaylists: List<PlaylistEntity> = emptyList()

    /** Tracks the below-the-fold load so it is never duplicated. */
    private var belowTheFoldJob: Job? = null

    /** Sections a network pass has actually filled, so we don't cache placeholders. */
    private val sectionsSeen = mutableSetOf<String>()

    /**
     * Sections whose [attempt] threw during the current load pass. Read once the
     * feed settles to decide between an empty state and an error state; cleared at
     * the top of every load so a successful retry clears the error.
     */
    private val failedSections = mutableSetOf<String>()

    init {
        val initialPinnedIds = preferenceManager.pinnedPlaylistIds
        _uiState.update { it.copy(pinnedPlaylistIds = initialPinnedIds) }

        viewModelScope.launch {
            repository.getAllPlaylists().collectLatest { playlists ->
                availablePlaylists = playlists
                refreshPinnedPlaylists()
            }
        }

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
                        listenAgainItems = history.take(50)
                    )
                }
            }
        }

        // Stale-while-revalidate: paint the cached feed instantly (so launch time
        // stops depending on the network), then refresh behind it. Falls back to a
        // stale snapshot when offline, and to the network when there is no cache.
        homeFeedCache.loadIfFresh()?.let(::applySnapshot) ?: run {
            homeFeedCache.loadStale()?.let { snapshot ->
                SecureLogger.d("HomeViewModel") { "serving stale home cache" }
                applySnapshot(snapshot)
            }
        }
        loadHomeContent()
    }

    fun togglePinnedPlaylist(playlistId: Long) {
        val pinnedIds = preferenceManager.pinnedPlaylistIds.toMutableList()
        if (playlistId in pinnedIds) pinnedIds.remove(playlistId) else pinnedIds.add(playlistId)
        preferenceManager.pinnedPlaylistIds = pinnedIds
        val byId = availablePlaylists.associateBy { it.id }
        _uiState.update {
            it.copy(
                pinnedPlaylistIds = pinnedIds,
                pinnedPlaylists = pinnedIds.mapNotNull(byId::get)
            )
        }
    }

    private fun refreshPinnedPlaylists() {
        val ids = preferenceManager.pinnedPlaylistIds
        val byId = availablePlaylists.associateBy { it.id }
        val validIds = ids.filter { it in byId }
        if (validIds != ids) preferenceManager.pinnedPlaylistIds = validIds
        _uiState.update {
            it.copy(
                pinnedPlaylistIds = validIds,
                pinnedPlaylists = validIds.mapNotNull(byId::get)
            )
        }
    }

    /**
     * Loads every section, emitting each one as it lands.
     *
     * The previous version awaited both the trending fetch and the (six round trip)
     * recommendation fetch before the first state update, so nothing rendered until
     * the slowest finished. Sections now update independently and each carries its
     * own loading flag, so rows appear progressively instead of all at once.
     */
    fun loadHomeContent() {
        viewModelScope.launch {
            failedSections.clear()
            _uiState.update {
                it.copy(isLoadingRecommendations = true, isLoadingTrending = true, loadFailed = false)
            }

            val trendingJob = launch {
                val trending = attempt("Trending fetch") { repository.getTrendingMusic() }
                if (trending != null && trending.isNotEmpty()) {
                    sectionsSeen += HomeFeedSnapshot.KEY_TRENDING
                    val title = currentHomeTitle()
                    _uiState.update { state ->
                        state.copy(
                            homeTitle = title,
                            trendingNowItems = HomeFeedShaper.inlineTrending(trending),
                            trendingNowRows = HomeFeedShaper.extraTrendingRows(trending),
                            isLoadingTrending = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoadingTrending = false) }
                }
            }

            val recommendationsJob = launch {
                val recommendations = attempt("Home recommendations") { getHomeContentUseCase() }
                if (recommendations != null && recommendations.isNotEmpty()) {
                    sectionsSeen += HomeFeedSnapshot.KEY_QUICK_PICKS
                    sectionsSeen += HomeFeedSnapshot.KEY_MIXED_FOR_YOU
                    val title = currentHomeTitle()
                    _uiState.update { state ->
                        state.copy(
                            homeTitle = title,
                            quickPicksRows = HomeFeedShaper.quickPicksGrid(recommendations),
                            mixedForYouItems = HomeFeedShaper.mixedForYou(recommendations),
                            quickPicksItems = HomeFeedShaper.moreQuickPicks(recommendations),
                            isLoadingRecommendations = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoadingRecommendations = false) }
                }
            }

            // isLoading clears as soon as anything is renderable, not when the last
            // section lands. Sections also drop it themselves, so this join is only a
            // backstop for the case where both above-the-fold fetches came back empty.
            launch {
                // Join the two real jobs. The old `joinAll()` had no arguments —
                // an empty vararg that joined nothing, so this settled the screen
                // before any fetch finished and made the loadFailed branch below
                // unreachable (failedSections was always still empty here).
                trendingJob.join()
                recommendationsJob.join()
                _uiState.update { state ->
                    when {
                        state.hasAnyContent -> state.copy(isLoading = false, loadFailed = false)
                        failedSections.isNotEmpty() -> state.copy(isLoading = false, loadFailed = true)
                        // Genuine empty result, not a failure: stop the spinner rather
                        // than leaving the screen blank forever.
                        else -> state.copy(isLoading = false, loadFailed = false)
                    }
                }
            }

            persistCacheWhenReady()
            loadBelowTheFoldContent()
        }
    }

    /**
     * Loads the sections the user only reaches by scrolling, concurrently rather
     * than one after another.
     *
     * They used to be strictly serial behind an 800ms delay, which existed to
     * protect the launch frames from extractor contention. With progressive
     * emission and a disk cache that trade no longer pays — these rows are below the
     * fold and the cost of leaving them blank is visible the moment the user scrolls.
     */
    fun loadBelowTheFoldContent() {
        if (belowTheFoldJob?.isActive == true) return
        belowTheFoldJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingNewReleases = true, isLoadingMoods = true) }

            launch {
                val releases = attempt("New releases search") {
                    repository.searchMusic(HomeQueries.NEW_RELEASES, maintainSession = false)
                }
                if (releases != null && releases.isNotEmpty()) {
                    sectionsSeen += HomeFeedSnapshot.KEY_NEW_RELEASES
                    _uiState.update {
                        it.copy(newReleasesItems = HomeFeedShaper.topNewReleases(releases), isLoadingNewReleases = false)
                    }
                } else {
                    _uiState.update { it.copy(isLoadingNewReleases = false) }
                }
            }

            launch { loadMoodContent() }
            persistCacheWhenReady()
        }
    }

    /** Chill / Workout / Focus rows, now concurrent instead of serial. */
    private suspend fun loadMoodContent() {
        val chill = attempt("Chill search") { repository.searchMusic(HomeQueries.CHILL, maintainSession = false) }
        if (chill != null && chill.isNotEmpty()) {
            sectionsSeen += HomeFeedSnapshot.KEY_CHILL
            _uiState.update { it.copy(chillItems = HomeFeedShaper.topMoods(chill), isLoadingMoods = false) }
        }

        val workout = attempt("Workout search") { repository.searchMusic(HomeQueries.WORKOUT, maintainSession = false) }
        if (workout != null && workout.isNotEmpty()) {
            sectionsSeen += HomeFeedSnapshot.KEY_WORKOUT
            _uiState.update { it.copy(workoutItems = HomeFeedShaper.topMoods(workout)) }
        }

        val focus = attempt("Focus search") { repository.searchMusic(HomeQueries.FOCUS, maintainSession = false) }
        if (focus != null && focus.isNotEmpty()) {
            sectionsSeen += HomeFeedSnapshot.KEY_FOCUS
            _uiState.update { it.copy(focusItems = HomeFeedShaper.topMoods(focus), isLoadingMoods = false) }
        }

        if (_uiState.value.chillItems.isEmpty() &&
            _uiState.value.workoutItems.isEmpty() &&
            _uiState.value.focusItems.isEmpty()
        ) {
            _uiState.update { it.copy(isLoadingMoods = false) }
        }
    }

    /**
     * Writes the cache once, after the sections that feed it have landed.
     *
     * Skipped while a pull-to-refresh is in flight so a partial refresh cannot
     * overwrite a good snapshot with a thinner one.
     */
    private fun persistCacheWhenReady() {
        viewModelScope.launch {
            // Give the independent section loads a turn to land.
            kotlinx.coroutines.delay(CACHE_SETTLE_MS)
            if (_uiState.value.isRefreshing) return@launch
            val snapshot = HomeFeedShaper.buildSnapshot(_uiState.value, sectionsSeen) ?: return@launch
            homeFeedCache.save(snapshot)
        }
    }

    /** Paints a cached snapshot into the UI so the feed is visible immediately. */
    private fun applySnapshot(snapshot: HomeFeedSnapshot) {
        _uiState.update { HomeFeedShaper.applySnapshot(it, snapshot) }
        // The painted sections now hold real content. Mark them so the next cache
        // save keeps them even when this launch never re-fetches them (offline).
        sectionsSeen += HomeFeedShaper.snapshotSeenKeys(snapshot)
    }

    /**
     * Runs [block], returning null and logging on failure.
     *
     * Unlike [runCatching] this lets cancellation propagate, so clearing the
     * ViewModel mid-load stops the chain instead of walking through the
     * remaining queries.
     */
    private suspend fun attempt(label: String, block: suspend () -> List<MusicItem>): List<MusicItem>? {
        return try {
            block()
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (e: Exception) {
            SecureLogger.e("HomeViewModel", "$label failed", e)
            failedSections += label
            null
        }
    }

    fun onExploreCategorySelected(category: String) {
        if (_uiState.value.selectedExploreCategory == category) {
            _uiState.update {
                it.copy(
                    selectedExploreCategory = null,
                    exploreTrendingItems = emptyList(),
                    exploreLoadFailed = false
                )
            }
            loadExploreContent()
            return
        }

        _uiState.update {
            it.copy(
                selectedExploreCategory = category,
                isLoadingExplore = true,
                exploreLoadFailed = false
            )
        }
        searchExploreCategory(category)
    }

    fun retryExploreContent() {
        val category = _uiState.value.selectedExploreCategory
        _uiState.update { it.copy(exploreLoadFailed = false) }
        if (category == null) {
            loadExploreContent()
        } else {
            _uiState.update { it.copy(isLoadingExplore = true) }
            searchExploreCategory(category)
        }
    }

    private fun searchExploreCategory(category: String) {
        viewModelScope.launch {
            try {
                val items = repository.searchMusic(HomeQueries.exploreQuery(category), maintainSession = false)
                _uiState.update { it.copy(exploreTrendingItems = items, exploreLoadFailed = false) }
            } catch (e: Exception) {
                SecureLogger.e("HomeViewModel", "Explore category search failed", e)
                _uiState.update { it.copy(exploreTrendingItems = emptyList(), exploreLoadFailed = true) }
            } finally {
                _uiState.update { it.copy(isLoadingExplore = false) }
            }
        }
    }

    fun loadExploreContent() {
        if (_uiState.value.exploreTrendingItems.isNotEmpty() && _uiState.value.selectedExploreCategory == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingExplore = true, exploreLoadFailed = false) }
            try {
                val items = repository.getTrendingMusic()
                _uiState.update { it.copy(exploreTrendingItems = items, exploreLoadFailed = false) }
            } catch (e: Exception) {
                SecureLogger.e("HomeViewModel", "Load explore content failed", e)
                _uiState.update { it.copy(exploreLoadFailed = true) }
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
                _uiState.update { it.copy(isLoadingRecommendations = true) }
                // attempt() both logs and records the failure: without it an offline
                // chip tap threw straight out of this launch, which is treated as an
                // uncaught exception on viewModelScope and took the app down.
                val results = attempt("Chip recommendations ($chip)") {
                    repository.searchMusic(HomeQueries.chipQuery(chip))
                }.orEmpty()
                _uiState.update { state ->
                    state.copy(
                        quickPicksRows = HomeFeedShaper.quickPicksGrid(results),
                        mixedForYouItems = HomeFeedShaper.mixedForYou(results),
                        isLoadingRecommendations = false
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
            SecureLogger.d("HomeViewModel") { "Reached max recommended items, stopping load" }
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
                        // distinctBy(url): LazyColumn keys on item.url; a duplicate
                        // recommendation from the API would crash with "Key was already used".
                        val merged = (state.quickPicksItems + itemsToAdd)
                            .distinctBy { it.url }
                            .take(MAX_RECOMMENDED_ITEMS)
                        state.copy(quickPicksItems = merged)
                    }

                    if (remainingSlots <= 0) {
                        SecureLogger.d("HomeViewModel") { "Max recommended items reached" }
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
     * Pull-to-refresh. Bypasses the cache and rewrites it once the fresh sections
     * have landed.
     */
    fun refreshAllContent() {
        if (_uiState.value.isRefreshing) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    isLoadingRecommendations = true,
                    isLoadingTrending = true,
                    isLoadingNewReleases = true,
                    isLoadingMoods = true
                )
            }
            sectionsSeen.clear()
            try {
                val trending = async { repository.getTrendingMusic() }
                val newReleases = async { repository.searchMusic(HomeQueries.NEW_RELEASES, maintainSession = false) }
                val recommendations = async { getHomeContentUseCase() }

                val trendingResult = trending.await()
                val newReleasesResult = newReleases.await()
                val recommendationsResult = recommendations.await()

                // Mark only sections the refresh actually refilled — an empty
                // result must not be cached as a placeholder section.
                if (trendingResult.isNotEmpty()) sectionsSeen += HomeFeedSnapshot.KEY_TRENDING
                if (recommendationsResult.isNotEmpty()) {
                    sectionsSeen += HomeFeedSnapshot.KEY_QUICK_PICKS
                    sectionsSeen += HomeFeedSnapshot.KEY_MIXED_FOR_YOU
                }
                if (newReleasesResult.isNotEmpty()) sectionsSeen += HomeFeedSnapshot.KEY_NEW_RELEASES

                val title = currentHomeTitle()
                _uiState.update { state ->
                    state.copy(
                        homeTitle = title,
                        quickPicksRows = HomeFeedShaper.quickPicksGrid(recommendationsResult),
                        mixedForYouItems = HomeFeedShaper.mixedForYou(recommendationsResult),
                        newReleasesItems = HomeFeedShaper.topNewReleases(newReleasesResult),
                        trendingNowItems = HomeFeedShaper.inlineTrending(trendingResult),
                        trendingNowRows = HomeFeedShaper.extraTrendingRows(trendingResult),
                        quickPicksItems = HomeFeedShaper.remainingQuickPicks(recommendationsResult),
                        isLoadingTrending = false,
                        isLoadingRecommendations = false,
                        isLoadingNewReleases = false
                    )
                }
                loadMoodContent()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingTrending = false,
                        isLoadingRecommendations = false,
                        isLoadingNewReleases = false,
                        isLoadingMoods = false
                    )
                }
                SecureLogger.e("HomeViewModel", "Refresh all content failed", e)
            } finally {
                _uiState.update { it.copy(isRefreshing = false, isLoading = false) }
                HomeFeedShaper.buildSnapshot(_uiState.value, sectionsSeen)?.let(homeFeedCache::save)
            }
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun currentHomeTitle(): String =
        HomeFeedShaper.homeTitle(_uiState.value.recentlyPlayed.isEmpty())
}

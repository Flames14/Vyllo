package com.vyllo.music

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.repository.IMusicRepository
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.data.manager.HomeFeedCache
import com.vyllo.music.data.manager.HomeFeedSnapshot
import com.vyllo.music.domain.manager.PlaybackQueueManager
import com.vyllo.music.domain.usecase.GetHomeContentUseCase
import com.vyllo.music.domain.usecase.RecordListenUseCase
import com.vyllo.music.domain.usecase.LoadMoreRecommendationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
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

    val currentPlayingItem: MusicItem? = null
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
    private val homeFeedCache: HomeFeedCache
) : ViewModel() {

    companion object {
        private const val MAX_RECOMMENDED_ITEMS = 200

        private const val NEW_RELEASES_QUERY = "new music releases"
        private const val CHILL_QUERY = "chill lofi music"
        private const val WORKOUT_QUERY = "workout gym music"
        private const val FOCUS_QUERY = "focus study music"

        /** Cards are laid out 4-per-row on the Home grid. */
        private const val HOME_ROW_SIZE = 4

        private const val QUICK_PICKS_CARD_COUNT = 16
        private const val MIXED_FOR_YOU_COUNT = 8
        private const val QUICK_PICKS_COUNT = 8
        private const val TRENDING_INLINE_COUNT = 8
        private const val TRENDING_ROW_COUNT = 16
        private const val NEW_RELEASES_COUNT = 8
        private const val MOOD_COUNT = 8

        /** Lets the independent section loads land before the cache is written. */
        private const val CACHE_SETTLE_MS = 1500L
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

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

    // We keep selectedNavTab outside HomeUiState since it's a structural
    // navigation state directly manipulated by MainActivity.
    var selectedNavTab by mutableIntStateOf(0)

    init {
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

            launch {
                val trending = attempt("Trending fetch") { repository.getTrendingMusic() }
                sectionsSeen += HomeFeedSnapshot.KEY_TRENDING
                if (trending != null && trending.isNotEmpty()) {
                    val title = homeTitleLabel()
                    _uiState.update { state ->
                        state.copy(
                            homeTitle = title,
                            trendingNowItems = trending.take(TRENDING_INLINE_COUNT),
                            trendingNowRows = trending.drop(TRENDING_INLINE_COUNT)
                                .take(TRENDING_ROW_COUNT)
                                .chunked(HOME_ROW_SIZE),
                            isLoadingTrending = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoadingTrending = false) }
                }
            }

            launch {
                val recommendations = attempt("Home recommendations") { getHomeContentUseCase() }
                sectionsSeen += HomeFeedSnapshot.KEY_QUICK_PICKS
                sectionsSeen += HomeFeedSnapshot.KEY_MIXED_FOR_YOU
                if (recommendations != null && recommendations.isNotEmpty()) {
                    val title = homeTitleLabel()
                    _uiState.update { state ->
                        state.copy(
                            homeTitle = title,
                            quickPicksRows = recommendations.take(QUICK_PICKS_CARD_COUNT).chunked(HOME_ROW_SIZE),
                            mixedForYouItems = recommendations.drop(QUICK_PICKS_CARD_COUNT).take(MIXED_FOR_YOU_COUNT),
                            quickPicksItems = recommendations.drop(QUICK_PICKS_CARD_COUNT + MIXED_FOR_YOU_COUNT)
                                .take(QUICK_PICKS_COUNT),
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
                joinAll()
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
                    repository.searchMusic(NEW_RELEASES_QUERY, maintainSession = false)
                }
                sectionsSeen += HomeFeedSnapshot.KEY_NEW_RELEASES
                if (releases != null && releases.isNotEmpty()) {
                    _uiState.update {
                        it.copy(newReleasesItems = releases.take(NEW_RELEASES_COUNT), isLoadingNewReleases = false)
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
        val chill = attempt("Chill search") { repository.searchMusic(CHILL_QUERY, maintainSession = false) }
        if (chill != null && chill.isNotEmpty()) {
            sectionsSeen += HomeFeedSnapshot.KEY_CHILL
            _uiState.update { it.copy(chillItems = chill.take(MOOD_COUNT), isLoadingMoods = false) }
        }

        val workout = attempt("Workout search") { repository.searchMusic(WORKOUT_QUERY, maintainSession = false) }
        if (workout != null && workout.isNotEmpty()) {
            sectionsSeen += HomeFeedSnapshot.KEY_WORKOUT
            _uiState.update { it.copy(workoutItems = workout.take(MOOD_COUNT)) }
        }

        val focus = attempt("Focus search") { repository.searchMusic(FOCUS_QUERY, maintainSession = false) }
        if (focus != null && focus.isNotEmpty()) {
            sectionsSeen += HomeFeedSnapshot.KEY_FOCUS
            _uiState.update { it.copy(focusItems = focus.take(MOOD_COUNT), isLoadingMoods = false) }
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
            val snapshot = buildSnapshot() ?: return@launch
            homeFeedCache.save(snapshot)
        }
    }

    private fun buildSnapshot(): HomeFeedSnapshot? {
        val state = _uiState.value
        val rows = HashMap<String, List<MusicItem>>()
        if (HomeFeedSnapshot.KEY_TRENDING in sectionsSeen) {
            rows[HomeFeedSnapshot.KEY_TRENDING] = state.trendingNowItems + state.trendingNowRows.flatten()
        }
        if (HomeFeedSnapshot.KEY_QUICK_PICKS in sectionsSeen) {
            rows[HomeFeedSnapshot.KEY_QUICK_PICKS] = state.quickPicksRows.flatten()
        }
        if (HomeFeedSnapshot.KEY_MIXED_FOR_YOU in sectionsSeen) {
            rows[HomeFeedSnapshot.KEY_MIXED_FOR_YOU] = state.mixedForYouItems
        }
        if (HomeFeedSnapshot.KEY_NEW_RELEASES in sectionsSeen) {
            rows[HomeFeedSnapshot.KEY_NEW_RELEASES] = state.newReleasesItems
        }
        if (HomeFeedSnapshot.KEY_CHILL in sectionsSeen) rows[HomeFeedSnapshot.KEY_CHILL] = state.chillItems
        if (HomeFeedSnapshot.KEY_WORKOUT in sectionsSeen) rows[HomeFeedSnapshot.KEY_WORKOUT] = state.workoutItems
        if (HomeFeedSnapshot.KEY_FOCUS in sectionsSeen) rows[HomeFeedSnapshot.KEY_FOCUS] = state.focusItems
        if (rows.isEmpty()) return null
        return HomeFeedSnapshot(rows = rows, cachedAtMs = System.currentTimeMillis())
    }

    /** Paints a cached snapshot into the UI so the feed is visible immediately. */
    private fun applySnapshot(snapshot: HomeFeedSnapshot) {
        val trending = snapshot.section(HomeFeedSnapshot.KEY_TRENDING)
        val quickPicks = snapshot.section(HomeFeedSnapshot.KEY_QUICK_PICKS)
        _uiState.update { state ->
            state.copy(
                homeTitle = homeTitleLabel(state.recentlyPlayed.isEmpty()),
                trendingNowItems = trending.take(TRENDING_INLINE_COUNT),
                trendingNowRows = trending.drop(TRENDING_INLINE_COUNT)
                    .take(TRENDING_ROW_COUNT)
                    .chunked(HOME_ROW_SIZE),
                quickPicksRows = quickPicks.take(QUICK_PICKS_CARD_COUNT).chunked(HOME_ROW_SIZE),
                mixedForYouItems = snapshot.section(HomeFeedSnapshot.KEY_MIXED_FOR_YOU),
                newReleasesItems = snapshot.section(HomeFeedSnapshot.KEY_NEW_RELEASES),
                chillItems = snapshot.section(HomeFeedSnapshot.KEY_CHILL),
                workoutItems = snapshot.section(HomeFeedSnapshot.KEY_WORKOUT),
                focusItems = snapshot.section(HomeFeedSnapshot.KEY_FOCUS),
                isLoading = false
            )
        }
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
                _uiState.update { it.copy(isLoadingRecommendations = true) }
                val query = when (chip) {
                    "Relax" -> "relaxing chill music"
                    "Energize" -> "energetic upbeat music"
                    "Workout" -> "workout gym motivation music"
                    "Focus" -> "focus concentration study music"
                    "Commute" -> "driving road trip music"
                    else -> "trending music"
                }
                // attempt() both logs and records the failure: without it an offline
                // chip tap threw straight out of this launch, which is treated as an
                // uncaught exception on viewModelScope and took the app down.
                val results = attempt("Chip recommendations ($chip)") {
                    repository.searchMusic(query)
                }.orEmpty()
                _uiState.update { state ->
                    state.copy(
                        quickPicksRows = results.take(QUICK_PICKS_CARD_COUNT).chunked(HOME_ROW_SIZE),
                        mixedForYouItems = results.drop(QUICK_PICKS_CARD_COUNT).take(MIXED_FOR_YOU_COUNT),
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
                val newReleases = async { repository.searchMusic(NEW_RELEASES_QUERY, maintainSession = false) }
                val recommendations = async { getHomeContentUseCase() }

                val trendingResult = trending.await()
                val newReleasesResult = newReleases.await()
                val recommendationsResult = recommendations.await()

                sectionsSeen += HomeFeedSnapshot.KEY_TRENDING
                sectionsSeen += HomeFeedSnapshot.KEY_QUICK_PICKS
                sectionsSeen += HomeFeedSnapshot.KEY_MIXED_FOR_YOU
                sectionsSeen += HomeFeedSnapshot.KEY_NEW_RELEASES

                _uiState.update { state ->
                    state.copy(
                        homeTitle = homeTitleLabel(state.recentlyPlayed.isEmpty()),
                        quickPicksRows = recommendationsResult.take(QUICK_PICKS_CARD_COUNT).chunked(HOME_ROW_SIZE),
                        mixedForYouItems = recommendationsResult.drop(QUICK_PICKS_CARD_COUNT).take(MIXED_FOR_YOU_COUNT),
                        newReleasesItems = newReleasesResult.take(NEW_RELEASES_COUNT),
                        trendingNowItems = trendingResult.take(TRENDING_INLINE_COUNT),
                        trendingNowRows = trendingResult.drop(TRENDING_INLINE_COUNT)
                            .take(TRENDING_ROW_COUNT)
                            .chunked(HOME_ROW_SIZE),
                        quickPicksItems = recommendationsResult
                            .drop(QUICK_PICKS_CARD_COUNT + MIXED_FOR_YOU_COUNT),
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
                buildSnapshot()?.let(homeFeedCache::save)
            }
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun homeTitleLabel(hasHistory: Boolean = _uiState.value.recentlyPlayed.isEmpty()): String =
        if (!hasHistory) "Recommended For You" else "Trending Now"
}
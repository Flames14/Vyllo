package com.vyllo.music.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.vyllo.music.HomeViewModel
import com.vyllo.music.R
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.core.network.rememberIsOnline
import com.vyllo.music.presentation.components.VylloErrorState
import com.vyllo.music.presentation.components.common.YTMHeader
import com.vyllo.music.presentation.scroll.HomeScrollTuner
import com.vyllo.music.presentation.scroll.rememberHomeFlingBehavior
import com.vyllo.music.presentation.scroll.rememberHomeImagePreloadCursor

/**
 * Pixel size preload requests must use so they hit the same cache entry that
 * [com.vyllo.music.presentation.components.YTMSquareCard] will ask for.
 */
private const val SQUARE_CARD_PX = 256

// =========================================================================
// YTM HOME SCREEN
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YTMHomeScreen(
    viewModel: HomeViewModel,
    onPlay: (MusicItem) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRecognizeClick: () -> Unit,
    currentPlayingItem: MusicItem?,
    loadingItemUrl: String? = null,
    scrollTuner: HomeScrollTuner? = null,
    homeScrollState: LazyListState = rememberLazyListState()
) {
    val uiState by viewModel.uiState.collectAsState()
    // Memoize filterChips to avoid list allocation during recomposition.
    // These are stable keys — HomeViewModel.onChipSelected switches on them to
    // pick the search query — so only the label below is localized.
    val filterChips = remember { listOf("All", "Relax", "Energize", "Workout", "Focus", "Commute") }
    val chipLabels = mapOf(
        "All" to stringResource(R.string.chip_all),
        "Relax" to stringResource(R.string.chip_relax),
        "Energize" to stringResource(R.string.chip_energize),
        "Workout" to stringResource(R.string.chip_workout),
        "Focus" to stringResource(R.string.chip_focus),
        "Commute" to stringResource(R.string.chip_commute)
    )
    val scrollState = homeScrollState
    val homeFlingBehavior = rememberHomeFlingBehavior()
    val imagePreloadCursor = rememberHomeImagePreloadCursor()
    val imagePreloadLimit = scrollTuner?.imagePreloadAheadItems() ?: 16
    val context = LocalContext.current
    val isOnline = rememberIsOnline()

    // Memoize recommendedItems calculation for performance
    val recommendedItems = uiState.quickPicksItems

    // Infinite scroll — distinctUntilChanged so we fire once per bottom-hit,
    // not on every layout frame during a fast fling (was spamming paging).
    LaunchedEffect(scrollState) {
        snapshotFlow {
            val layoutInfo = scrollState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 3
        }.distinctUntilChanged().collect { atBottom ->
            // Read chip from the flow inside collect — uiState from the composition
            // snapshot is stale after LaunchedEffect started (effect never restarts).
            if (atBottom && viewModel.uiState.value.selectedChip == "All") {
                viewModel.loadMoreRecommendations()
            }
        }
    }

    // Pre-cache only what the first screen actually renders.
    //
    // Every image below is fetched at exactly the size the card asks
    // VylloArtwork for, so the memory cache is hit on the first scroll instead
    // of re-decoding mid-fling. `trendingNowItems` used to be preloaded too —
    // but that state is never rendered anywhere (the grid reads
    // `trendingNowRows`), so it only spent decode budget during the launch
    // window on images nobody would ever see.
    val imageLoader = coil.Coil.imageLoader(context)
    LaunchedEffect(
        uiState.listenAgainItems,
        uiState.quickPicksItems
    ) {
        val visibleUrls = (uiState.listenAgainItems.take(6) + uiState.quickPicksItems.take(6))
            .map { it.thumbnailUrl }
            .filter { it.isNotBlank() }
            .distinct()
        if (visibleUrls.isEmpty()) return@LaunchedEffect

        // Never spend decode budget while the list is moving. Image work that lands
        // mid-motion collided with GC/JIT in the scroll trace (GrallocUpload 54ms +
        // dispatcher saturation inside a single 92ms stall), so wait for the scroll
        // to settle first.
        //
        // The wait happens BEFORE HomeImagePreloadCursor.unseen() marks anything:
        // if items change again while waiting, this coroutine is cancelled and the
        // next invocation re-derives the batch from scratch. Marking first and
        // waiting after would leak marked-but-never-enqueued URLs on cancellation.
        snapshotFlow { scrollState.isScrollInProgress }.first { inProgress -> !inProgress }

        // One small batch per content emission keeps the first frames of a fling free
        // for input instead of image decode queue churn.
        imagePreloadCursor.unseen(visibleUrls, imagePreloadLimit).forEach { url ->
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    // Must match YTMSquareCard's VylloArtwork(requestSizePx).
                    .size(SQUARE_CARD_PX, SQUARE_CARD_PX)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build()
            )
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refreshAllContent() },
        modifier = Modifier.fillMaxSize()
    ) {
        // Living Ambient Dark Aura
        HomeAmbientDarkAura()

        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            flingBehavior = homeFlingBehavior
        ) {
            // YTM Header
            item(key = "ytm_header", contentType = "header") {
                YTMHeader(
                    onSearchClick = onSearchClick,
                    onSettingsClick = onSettingsClick,
                    onRecognizeClick = onRecognizeClick
                )
            }

            // Filter Chips
            item(key = "filter_chips_row", contentType = "filter_row") {
                HomeFilterChipsRow(
                    filterChips = filterChips,
                    selectedChip = uiState.selectedChip,
                    onChipSelected = { viewModel.onChipSelected(it) },
                    chipLabel = { chipLabels[it] ?: it }
                )
            }

            // First paint failed outright: explain and offer a retry rather than
            // silently rendering an empty feed. Header + chips stay so the layout
            // does not jump when the retry succeeds.
            //
            // Two ways to get here:
            //  1. loadFailed — a fetch threw and reached the ViewModel.
            //  2. The feed settled empty while the device is offline. The data
            //     layer swallows transport errors and returns an empty list
            //     (YouTubeDataSource catches and returns emptyList), so (1) alone
            //     would never fire for the common "no network" case.
            val showHomeError = uiState.loadFailed ||
                (!uiState.hasAnyContent && !uiState.isLoading && !uiState.isRefreshing && !isOnline)
            if (showHomeError) {
                item(key = "home_error", contentType = "error_state") {
                    VylloErrorState(
                        message = stringResource(R.string.state_home_failed),
                        onRetry = { viewModel.loadHomeContent() },
                        modifier = Modifier.fillParentMaxWidth()
                    )
                }
            } else {
                HomeListSections(
                    uiState = uiState,
                    recommendedItems = recommendedItems,
                    currentPlayingItem = currentPlayingItem,
                    onPlay = onPlay,
                    loadingItemUrl = loadingItemUrl
                )
            }
        } // End LazyColumn
    } // End PullToRefreshBox
}

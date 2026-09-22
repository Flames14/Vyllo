package com.vyllo.music.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.vyllo.music.HomeViewModel
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.presentation.components.YTMHeader
import com.vyllo.music.presentation.scroll.HomeScrollTuner
import com.vyllo.music.presentation.scroll.rememberHomeFlingBehavior
import com.vyllo.music.presentation.scroll.rememberHomeImagePreloadCursor

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
    // Memoize filterChips to avoid list allocation during recomposition
    val filterChips = remember { listOf("All", "Relax", "Energize", "Workout", "Focus", "Commute") }
    val scrollState = homeScrollState
    val homeFlingBehavior = rememberHomeFlingBehavior()
    val imagePreloadCursor = rememberHomeImagePreloadCursor()
    val imagePreloadLimit = scrollTuner?.imagePreloadAheadItems() ?: 16
    val context = LocalContext.current

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

    // Pre-cache only what's visible on the first screen
    val imageLoader = coil.Coil.imageLoader(context)
    LaunchedEffect(
        uiState.listenAgainItems,
        uiState.trendingNowItems
    ) {
        val visibleUrls = (uiState.listenAgainItems.take(6) + uiState.trendingNowItems.take(6))
            .map { it.thumbnailUrl }
            .filter { it.isNotBlank() }
            .distinct()

        // One small batch per content emission keeps the first frames of a fling free
        // for input instead of image decode queue churn.
        imagePreloadCursor.unseen(visibleUrls, imagePreloadLimit).forEach { url ->
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(300, 300)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build()
            )
        }
    }

    val pullToRefreshState = rememberPullToRefreshState()

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refreshAllContent()
        }
    }
    LaunchedEffect(uiState.isRefreshing) {
        if (uiState.isRefreshing) {
            pullToRefreshState.startRefresh()
        } else {
            pullToRefreshState.endRefresh()
        }
    }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(pullToRefreshState.nestedScrollConnection)) {
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
                    onChipSelected = { viewModel.onChipSelected(it) }
                )
            }

            HomeListSections(
                uiState = uiState,
                recommendedItems = recommendedItems,
                currentPlayingItem = currentPlayingItem,
                onPlay = onPlay,
                viewModel = viewModel,
                loadingItemUrl = loadingItemUrl
            )
        } // End LazyColumn

        PullToRefreshContainer(
            state = pullToRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    } // End Box
}

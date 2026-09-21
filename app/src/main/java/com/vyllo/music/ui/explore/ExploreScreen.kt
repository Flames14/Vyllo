package com.vyllo.music.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.*
import com.vyllo.music.presentation.components.*
import com.vyllo.music.ui.components.*

// =========================================================================
// YTM EXPLORE SCREEN
// =========================================================================
@Composable
fun YTMExploreScreen(
    viewModel: HomeViewModel,
    onPlay: (MusicItem) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRecognizeClick: () -> Unit,
    currentPlayingItem: MusicItem?,
    loadingItemUrl: String? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.loadExploreContent()
    }

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // YTM Header with refresh
        item(key = "ytm_header", contentType = "header") {
            YTMHeader(
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                onRecognizeClick = onRecognizeClick,
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refreshAllContent() }
            )
        }

        // Explore Categories
        item(key = "explore_categories_row", contentType = "category_row") {
            LazyRow(
                modifier = Modifier.padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    count = uiState.exploreCategories.size,
                    key = { index -> uiState.exploreCategories[index] },
                    contentType = { "category_pill" }
                ) { index ->
                    val category = uiState.exploreCategories[index]
                    YTMFilterChip(
                        text = category,
                        isSelected = uiState.selectedExploreCategory == category,
                        onClick = { viewModel.onExploreCategorySelected(category) }
                    )
                }
            }
        }

        // Trending Section
        if (uiState.isLoadingExplore || uiState.exploreTrendingItems.isNotEmpty()) {
            item(key = "trending_header", contentType = "header") {
                val title = if (uiState.selectedExploreCategory == null) "Trending Now" else "${uiState.selectedExploreCategory}"
                YTMSectionHeader(title = title)
            }
        }

        if (uiState.isLoadingExplore && uiState.exploreTrendingItems.isEmpty()) {
            item(key = "explore_skeleton", contentType = "skeleton") {
                ListSkeleton(rows = 6)
            }
        } else if (uiState.exploreTrendingItems.isEmpty()) {
            item(key = "explore_empty", contentType = "empty") {
                VylloEmptyState(
                    icon = Icons.Rounded.Explore,
                    title = "Nothing to explore here yet",
                    message = "No music is available here right now. Try another category or search for a song.",
                    actionLabel = "Search music",
                    onAction = onSearchClick
                )
            }
        } else {
            items(
                items = uiState.exploreTrendingItems,
                key = { it.url },
                contentType = { "song_row" }
            ) { item ->
                YTMSongRow(
                    item = item,
                    isPlaying = currentPlayingItem?.title == item.title,
                    onClick = { onPlay(item) },
                    homeViewModel = viewModel,
                    isLoading = loadingItemUrl == item.url
                )
            }
        }
    }
}

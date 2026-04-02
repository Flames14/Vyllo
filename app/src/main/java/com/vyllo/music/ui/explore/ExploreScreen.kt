package com.vyllo.music.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onSettingsClick: () -> Unit,
    currentPlayingItem: MusicItem?,
    loadingItemUrl: String? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberLazyListState()
    val liquidFlingBehavior = rememberLiquidFlingBehavior(uiState.isLiquidScrollEnabled)

    LaunchedEffect(Unit) {
        viewModel.loadExploreContent()
    }

    LazyColumn(
        state = scrollState,
        flingBehavior = liquidFlingBehavior,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (currentPlayingItem != null) 140.dp else 16.dp)
    ) {
        // YTM Header with refresh
        item {
            YTMHeader(
                onSearchClick = { },
                onSettingsClick = onSettingsClick,
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refreshAllContent() }
            )
        }

        // Explore Categories
        item {
            LazyRow(
                modifier = Modifier.padding(vertical = 16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.exploreCategories.size) { index ->
                    val category = uiState.exploreCategories[index]
                    val isSelected = uiState.selectedExploreCategory == category
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.onBackground 
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { viewModel.onExploreCategorySelected(category) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.background 
                                   else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Trending Section
        item {
            val title = if (uiState.selectedExploreCategory == null) "Trending Now" else "${uiState.selectedExploreCategory}"
            YTMSectionHeader(title = title)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (uiState.isLoadingExplore) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            items(uiState.exploreTrendingItems) { item ->
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

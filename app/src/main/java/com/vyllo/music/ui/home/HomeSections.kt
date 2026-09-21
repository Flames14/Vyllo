package com.vyllo.music.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vyllo.music.HomeUiState
import com.vyllo.music.HomeViewModel
import com.vyllo.music.R
import com.vyllo.music.YTMGridSection
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.presentation.components.OptimizedHorizontalSection
import com.vyllo.music.presentation.components.VylloLoadingIndicator
import com.vyllo.music.presentation.components.HomeSkeleton
import com.vyllo.music.presentation.components.YTMFilterChip
import com.vyllo.music.presentation.components.YTMSectionHeader
import com.vyllo.music.presentation.components.YTMSongRow

@Composable
fun HomeFilterChipsRow(
    filterChips: List<String>,
    selectedChip: String,
    onChipSelected: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            count = filterChips.size,
            key = { index -> filterChips[index] },
            contentType = { "filter_chip" }
        ) { index ->
            YTMFilterChip(
                text = filterChips[index],
                isSelected = selectedChip == filterChips[index],
                onClick = { onChipSelected(filterChips[index]) }
            )
        }
    }
}

fun LazyListScope.HomeListSections(
    uiState: HomeUiState,
    recommendedItems: List<MusicItem>,
    currentPlayingItem: MusicItem?,
    onPlay: (MusicItem) -> Unit,
    viewModel: HomeViewModel,
    loadingItemUrl: String?
) {
    // Listen Again Section
    if (uiState.listenAgainItems.isNotEmpty()) {
        item(key = "listen_again_header", contentType = "header") {
            YTMSectionHeader(title = stringResource(R.string.section_listen_again))
        }
        item(key = "listen_again_row", contentType = "horizontal_section") {
            OptimizedHorizontalSection(
                items = uiState.listenAgainItems,
                currentPlayingItem = currentPlayingItem,
                onPlay = onPlay,
                isLargeCard = false,
                homeViewModel = viewModel,
                loadingItemUrl = loadingItemUrl
            )
        }
    }

    // Quick Picks Section
    if (uiState.quickPicksRows.isNotEmpty()) {
        item(key = "quick_picks_header", contentType = "header") {
            Spacer(modifier = Modifier.height(24.dp))
            YTMSectionHeader(title = stringResource(R.string.section_quick_picks))
        }
        YTMGridSection(
            rows = uiState.quickPicksRows.take(3),
            currentPlayingItem = currentPlayingItem,
            onPlay = onPlay,
            homeViewModel = viewModel,
            loadingItemUrl = loadingItemUrl,
            sectionKey = "quick_picks"
        )
    }

    // Chill Vibes Section
    if (uiState.chillItems.isNotEmpty()) {
        item(key = "chill_header", contentType = "header") {
            Spacer(modifier = Modifier.height(24.dp))
            YTMSectionHeader(title = stringResource(R.string.section_chill_vibes))
        }
        item(key = "chill_row", contentType = "horizontal_section") {
            OptimizedHorizontalSection(
                items = uiState.chillItems,
                currentPlayingItem = currentPlayingItem,
                onPlay = onPlay,
                isLargeCard = false,
                homeViewModel = viewModel,
                loadingItemUrl = loadingItemUrl
            )
        }
    }

    // Mixed For You Section (Large Cards)
    if (uiState.mixedForYouItems.isNotEmpty()) {
        item(key = "mixed_header", contentType = "header") {
            Spacer(modifier = Modifier.height(24.dp))
            YTMSectionHeader(title = stringResource(R.string.section_mixed_for_you))
        }
        item(key = "mixed_row", contentType = "horizontal_section") {
            OptimizedHorizontalSection(
                items = uiState.mixedForYouItems,
                currentPlayingItem = currentPlayingItem,
                onPlay = onPlay,
                isLargeCard = true,
                homeViewModel = viewModel,
                loadingItemUrl = loadingItemUrl
            )
        }
    }

    // Workout Music Section
    if (uiState.workoutItems.isNotEmpty()) {
        item(key = "workout_header", contentType = "header") {
            Spacer(modifier = Modifier.height(24.dp))
            YTMSectionHeader(title = stringResource(R.string.section_workout))
        }
        item(key = "workout_row", contentType = "horizontal_section") {
            OptimizedHorizontalSection(
                items = uiState.workoutItems,
                currentPlayingItem = currentPlayingItem,
                onPlay = onPlay,
                isLargeCard = false,
                homeViewModel = viewModel,
                loadingItemUrl = loadingItemUrl
            )
        }
    }

    // Focus Music Section
    if (uiState.focusItems.isNotEmpty()) {
        item(key = "focus_header", contentType = "header") {
            Spacer(modifier = Modifier.height(24.dp))
            YTMSectionHeader(title = stringResource(R.string.section_focus))
        }
        item(key = "focus_row", contentType = "horizontal_section") {
            OptimizedHorizontalSection(
                items = uiState.focusItems,
                currentPlayingItem = currentPlayingItem,
                onPlay = onPlay,
                isLargeCard = true,
                homeViewModel = viewModel,
                loadingItemUrl = loadingItemUrl
            )
        }
    }

    // Trending Now Section
    if (uiState.trendingNowRows.isNotEmpty()) {
        item(key = "trending_header", contentType = "header") {
            Spacer(modifier = Modifier.height(24.dp))
            YTMSectionHeader(title = "Trending now")
        }
        YTMGridSection(
            rows = uiState.trendingNowRows.take(3),
            currentPlayingItem = currentPlayingItem,
            onPlay = onPlay,
            homeViewModel = viewModel,
            loadingItemUrl = loadingItemUrl,
            sectionKey = "trending_now"
        )
    }

    // New Releases Section
    if (uiState.newReleasesItems.isNotEmpty()) {
        item(key = "new_releases_header", contentType = "header") {
            Spacer(modifier = Modifier.height(24.dp))
            YTMSectionHeader(title = stringResource(R.string.section_new_releases))
        }
        item(key = "new_releases_row", contentType = "horizontal_section") {
            OptimizedHorizontalSection(
                items = uiState.newReleasesItems,
                currentPlayingItem = currentPlayingItem,
                onPlay = onPlay,
                isLargeCard = false,
                homeViewModel = viewModel,
                loadingItemUrl = loadingItemUrl
            )
        }
    }

    // Recommended Section
    if (uiState.quickPicksItems.isNotEmpty()) {
        item(key = "recommended_header", contentType = "header") {
            Spacer(modifier = Modifier.height(24.dp))
            YTMSectionHeader(title = "Recommended")
        }
        items(
            items = recommendedItems,
            key = { item -> item.url },
            contentType = { "song_row" }
        ) { item ->
            YTMSongRow(
                item = item,
                isPlaying = currentPlayingItem?.url == item.url,
                onClick = { onPlay(item) },
                homeViewModel = viewModel,
                isLoading = loadingItemUrl == item.url
            )
        }
    }

    // Infinite scroll loading indicator
    if (uiState.isLoadingMoreRecommendations) {
        item(key = "loading_more", contentType = "loading") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }

    // Loading Indicator
    // On a cold start we show a skeleton that mirrors the real layout instead of a
    // lone spinner at the bottom of an otherwise empty screen.
    if (uiState.isLoading) {
        val hasNoContentYet = uiState.listenAgainItems.isEmpty() &&
            uiState.quickPicksItems.isEmpty() &&
            uiState.chillItems.isEmpty() &&
            uiState.workoutItems.isEmpty() &&
            uiState.focusItems.isEmpty() &&
            uiState.newReleasesItems.isEmpty()
        item(key = "loading_indicator", contentType = "loading") {
            if (hasNoContentYet) {
                HomeSkeleton()
            } else {
                VylloLoadingIndicator()
            }
        }
    }
}

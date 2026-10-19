package com.vyllo.music.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.vyllo.music.R
import com.vyllo.music.YTMGridSection
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.PlaylistEntity
import com.vyllo.music.presentation.components.common.OptimizedHorizontalSection
import com.vyllo.music.presentation.components.VylloLoadingIndicator
import com.vyllo.music.presentation.components.HomeSkeleton
import com.vyllo.music.presentation.components.common.YTMFilterChip
import com.vyllo.music.presentation.components.common.YTMSectionHeader
import com.vyllo.music.presentation.components.YTMSongRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import com.vyllo.music.presentation.components.VylloArtwork

@Composable
fun HomeFilterChipsRow(
    filterChips: List<String>,
    selectedChip: String,
    onChipSelected: (String) -> Unit,
    chipLabel: (String) -> String = { it }
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
                // filterChips holds stable keys; the visible label is localized.
                text = chipLabel(filterChips[index]),
                isSelected = selectedChip == filterChips[index],
                onClick = { onChipSelected(filterChips[index]) }
            )
        }
    }
}

@Composable
fun ListenAgainGrid(
    items: List<MusicItem>,
    currentPlayingItem: MusicItem?,
    onPlay: (MusicItem) -> Unit
) {
    if (items.isEmpty()) return
    val pages = items.take(50).chunked(9)
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { pages.size })
    Column(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            key = { page -> pages[page].firstOrNull()?.url ?: page.toString() }
        ) { page ->
            val pageItems = pages[page]
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cell = (maxWidth - 16.dp) / 3
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pageItems.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { item ->
                                ListenAgainCard(
                                    item = item,
                                    isCurrent = item.url == currentPlayingItem?.url,
                                    onPlay = onPlay,
                                    modifier = Modifier.size(cell)
                                )
                            }
                            repeat(3 - rowItems.size) {
                                Spacer(modifier = Modifier.size(cell))
                            }
                        }
                    }
                }
            }
        }
        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (selected) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color.White else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}

@Composable
private fun ListenAgainCard(
    item: MusicItem,
    isCurrent: Boolean,
    onPlay: (MusicItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable { onPlay(item) }
    ) {
        VylloArtwork(
            thumbnailUrl = item.thumbnailUrl,
            contentDescription = item.title,
            requestSizePx = 256,
            modifier = Modifier.fillMaxSize()
        )
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.dp, Color.White, RoundedCornerShape(12.dp))
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.15f),
                            Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Text(
                text = item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

fun LazyListScope.HomeListSections(
    uiState: HomeUiState,
    recommendedItems: List<MusicItem>,
    currentPlayingItem: MusicItem?,
    onPlay: (MusicItem) -> Unit,
    loadingItemUrl: String?,
    onPlaylistClick: (PlaylistEntity) -> Unit
) {
    if (uiState.pinnedPlaylists.isNotEmpty()) {
        item(key = "pinned_playlists_header", contentType = "header") {
            YTMSectionHeader(title = stringResource(R.string.section_pinned_playlists))
        }
        item(key = "pinned_playlists_row", contentType = "horizontal_section") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = uiState.pinnedPlaylists,
                    key = { "pinned_playlist_${it.id}" },
                    contentType = { "pinned_playlist_card" }
                ) { playlist ->
                    Surface(
                        modifier = Modifier
                            .width(190.dp)
                            .height(68.dp)
                            .clickable { onPlaylistClick(playlist) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    // Listen Again Section
    if (uiState.listenAgainItems.isNotEmpty()) {
        item(key = "listen_again_header", contentType = "header") {
            YTMSectionHeader(title = stringResource(R.string.section_listen_again))
        }
        item(key = "listen_again_grid", contentType = "grid_section") {
            ListenAgainGrid(
                items = uiState.listenAgainItems,
                currentPlayingItem = currentPlayingItem,
                onPlay = onPlay
            )
        }
    }

    // Quick Picks Section
    // The header renders while the section is still loading so the feed does not
    // appear to start midway down the page — sections now emit independently, which
    // means a section can be pending for seconds after its neighbour has painted.
    if (uiState.quickPicksRows.isNotEmpty() || uiState.isLoadingRecommendations) {
        item(key = "quick_picks_header", contentType = "header") {
            Spacer(modifier = Modifier.height(24.dp))
            YTMSectionHeader(title = stringResource(R.string.section_quick_picks))
        }
    }
    if (uiState.quickPicksRows.isNotEmpty()) {
        YTMGridSection(
            rows = uiState.quickPicksRows.take(3),
            currentPlayingItem = currentPlayingItem,
            onPlay = onPlay,
            loadingItemUrl = loadingItemUrl,
            sectionKey = "quick_picks"
        )
    }

    // Chill Vibes Section
    if (uiState.chillItems.isNotEmpty() || uiState.isLoadingMoods) {
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
                loadingItemUrl = loadingItemUrl
            )
        }
    }

    // Trending Now Section
    if (uiState.trendingNowRows.isNotEmpty() || uiState.isLoadingTrending) {
        item(key = "trending_header", contentType = "header") {
            Spacer(modifier = Modifier.height(24.dp))
            YTMSectionHeader(title = stringResource(R.string.home_trending_now))
        }
    }
    if (uiState.trendingNowRows.isNotEmpty()) {
        YTMGridSection(
            rows = uiState.trendingNowRows.take(3),
            currentPlayingItem = currentPlayingItem,
            onPlay = onPlay,
            loadingItemUrl = loadingItemUrl,
            sectionKey = "trending_now"
        )
    }

    // New Releases Section
    if (uiState.newReleasesItems.isNotEmpty() || uiState.isLoadingNewReleases) {
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
                loadingItemUrl = loadingItemUrl
            )
        }
    }

    // Recommended Section
    if (uiState.quickPicksItems.isNotEmpty()) {
        item(key = "recommended_header", contentType = "header") {
            Spacer(modifier = Modifier.height(24.dp))
            YTMSectionHeader(title = stringResource(R.string.home_recommended))
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

    // Full-screen skeleton, shown only while the feed is completely empty. Uses the
    // shared hasAnyContent predicate so this cannot drift out of sync with the
    // ViewModel's own "is anything renderable yet" decision.
    if (!uiState.hasAnyContent && !uiState.isRefreshing) {
        item(key = "loading_indicator", contentType = "loading") {
            HomeSkeleton()
        }
    }
}

package com.vyllo.music.presentation.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.vyllo.music.HomeViewModel
import com.vyllo.music.domain.model.MusicItem

@Composable
fun OptimizedHorizontalSection(
    items: List<MusicItem>,
    currentPlayingItem: MusicItem?,
    onPlay: (MusicItem) -> Unit,
    isLargeCard: Boolean,
    homeViewModel: HomeViewModel? = null,
    loadingItemUrl: String? = null
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(
            items = items,
            key = { it.url },
            contentType = { if (isLargeCard) "large_card" else "square_card" }
        ) { item ->
            if (isLargeCard) {
                com.vyllo.music.presentation.components.YTMLargeCard(
                    item = item,
                    isPlaying = currentPlayingItem?.url == item.url,
                    onClick = { onPlay(item) },
                    homeViewModel = homeViewModel,
                    isLoading = loadingItemUrl == item.url
                )
            } else {
                com.vyllo.music.presentation.components.YTMSquareCard(
                    item = item,
                    isPlaying = currentPlayingItem?.url == item.url,
                    onClick = { onPlay(item) },
                    homeViewModel = homeViewModel,
                    isLoading = loadingItemUrl == item.url
                )
            }
        }
    }
}

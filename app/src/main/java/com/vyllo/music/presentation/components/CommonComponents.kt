package com.vyllo.music.presentation.components

import androidx.compose.runtime.Composable
import com.vyllo.music.HomeViewModel
import com.vyllo.music.LibraryViewModel
import com.vyllo.music.domain.model.MusicItem

@Deprecated("Moved to presentation.components.common", ReplaceWith("com.vyllo.music.presentation.components.common.PremiumAccent()"))
@Composable
fun PremiumAccent() = com.vyllo.music.presentation.components.common.PremiumAccent()

@Deprecated("Moved to presentation.components.common", ReplaceWith("com.vyllo.music.presentation.components.common.GlassWhite()"))
@Composable
fun GlassWhite() = com.vyllo.music.presentation.components.common.GlassWhite()

@Deprecated("Moved to presentation.components.common", ReplaceWith("com.vyllo.music.presentation.components.common.GlassBorder()"))
@Composable
fun GlassBorder() = com.vyllo.music.presentation.components.common.GlassBorder()

@Deprecated("Moved to presentation.components.common", ReplaceWith("com.vyllo.music.presentation.components.common.YTMHeader(...)"))
@Composable
fun YTMHeader(
    onSearchClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onRecognizeClick: () -> Unit = {}
) {
    com.vyllo.music.presentation.components.common.YTMHeader(
        onSearchClick = onSearchClick,
        onSettingsClick = onSettingsClick,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        onRecognizeClick = onRecognizeClick
    )
}

@Deprecated("Moved to presentation.components.common", ReplaceWith("com.vyllo.music.presentation.components.common.YTMFilterChip(...)"))
@Composable
fun YTMFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    com.vyllo.music.presentation.components.common.YTMFilterChip(text, isSelected, onClick)
}

@Deprecated("Moved to presentation.components.common", ReplaceWith("com.vyllo.music.presentation.components.common.YTMSectionHeader(...)"))
@Composable
fun YTMSectionHeader(title: String, onSeeAll: (() -> Unit)? = null) {
    com.vyllo.music.presentation.components.common.YTMSectionHeader(title, onSeeAll)
}

@Deprecated("Moved to presentation.components.common", ReplaceWith("com.vyllo.music.presentation.components.common.DownloadButton(item)"))
@Composable
fun DownloadButton(item: MusicItem) {
    com.vyllo.music.presentation.components.common.DownloadButton(item)
}

@Deprecated("Moved to presentation.components.common", ReplaceWith("com.vyllo.music.presentation.components.common.PlaylistAddDialog(...)"))
@Composable
fun PlaylistAddDialog(
    viewModel: LibraryViewModel,
    onDismiss: () -> Unit
) {
    com.vyllo.music.presentation.components.common.PlaylistAddDialog(viewModel, onDismiss)
}

@Deprecated("Moved to presentation.components.common", ReplaceWith("com.vyllo.music.presentation.components.common.CreatePlaylistDialog(...)"))
@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    com.vyllo.music.presentation.components.common.CreatePlaylistDialog(onDismiss, onCreate)
}

@Deprecated("Moved to presentation.components.common", ReplaceWith("com.vyllo.music.presentation.components.common.YTMBottomNavBar(...)"))
@Composable
fun YTMBottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    com.vyllo.music.presentation.components.common.YTMBottomNavBar(selectedTab, onTabSelected)
}

@Deprecated("Moved to presentation.components.common", ReplaceWith("com.vyllo.music.presentation.components.common.PremiumGlassSearchBar(...)"))
@Composable
fun PremiumGlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit
) {
    com.vyllo.music.presentation.components.common.PremiumGlassSearchBar(query, onQueryChange, onSearch, onBack)
}

@Deprecated("Moved to presentation.components.common", ReplaceWith("com.vyllo.music.presentation.components.common.OptimizedHorizontalSection(...)"))
@Composable
fun OptimizedHorizontalSection(
    items: List<MusicItem>,
    currentPlayingItem: MusicItem?,
    onPlay: (MusicItem) -> Unit,
    isLargeCard: Boolean,
    homeViewModel: HomeViewModel? = null,
    loadingItemUrl: String? = null
) {
    com.vyllo.music.presentation.components.common.OptimizedHorizontalSection(
        items = items,
        currentPlayingItem = currentPlayingItem,
        onPlay = onPlay,
        isLargeCard = isLargeCard,
        homeViewModel = homeViewModel,
        loadingItemUrl = loadingItemUrl
    )
}

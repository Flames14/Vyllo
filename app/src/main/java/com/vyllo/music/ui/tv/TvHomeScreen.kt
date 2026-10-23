package com.vyllo.music.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vyllo.music.R
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.presentation.theme.VylloRadius
import com.vyllo.music.presentation.theme.VylloSpacing

/**
 * Leanback landing screen: large, D-pad navigable entry tiles plus a
 * "continue listening" rail. Phone screens remain reachable from here, so the
 * TV build uses the same data layer and player as the handheld build.
 */
@Composable
fun TvHomeScreen(
    recentlyPlayed: List<MusicItem>,
    hasActiveTrack: Boolean,
    onSearch: () -> Unit,
    onExplore: () -> Unit,
    onLibrary: () -> Unit,
    onNowPlaying: () -> Unit,
    onPlay: (MusicItem) -> Unit
) {
    val tiles = listOf(
        TvTileModel(
            title = stringResource(R.string.tv_home_search),
            subtitle = stringResource(R.string.tv_home_search_subtitle),
            icon = Icons.Rounded.Search,
            onClick = onSearch
        ),
        TvTileModel(
            title = stringResource(R.string.tv_home_explore),
            subtitle = stringResource(R.string.tv_home_explore_subtitle),
            icon = Icons.Rounded.Explore,
            onClick = onExplore
        ),
        TvTileModel(
            title = stringResource(R.string.tv_home_library),
            subtitle = stringResource(R.string.tv_home_library_subtitle),
            icon = Icons.Rounded.LibraryMusic,
            onClick = onLibrary
        ),
        TvTileModel(
            title = stringResource(R.string.tv_home_now_playing),
            subtitle = stringResource(R.string.tv_home_now_playing_subtitle),
            icon = Icons.Rounded.PlayCircle,
            onClick = onNowPlaying,
            enabled = hasActiveTrack
        )
    )

    val firstTileFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { firstTileFocus.requestFocus() }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(VylloSpacing.md),
        verticalArrangement = Arrangement.spacedBy(VylloSpacing.md)
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(modifier = Modifier.padding(bottom = VylloSpacing.md)) {
                Text(
                    text = "Vyllo",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.tv_home_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(tiles.size) { index ->
            TvEntryTile(
                model = tiles[index],
                modifier = if (index == 0) Modifier.focusRequester(firstTileFocus) else Modifier
            )
        }

        if (recentlyPlayed.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Column(modifier = Modifier.padding(top = VylloSpacing.lg)) {
                    Text(
                        text = stringResource(R.string.tv_home_continue),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(VylloSpacing.sm))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(VylloSpacing.md),
                        contentPadding = PaddingValues(vertical = VylloSpacing.xs)
                    ) {
                        items(recentlyPlayed, key = { it.url }) { item ->
                            TvSongCard(item = item, onClick = { onPlay(item) })
                        }
                    }
                }
            }
        }
    }
}

private data class TvTileModel(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true
)

@Composable
private fun TvEntryTile(model: TvTileModel, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.04f else 1f, label = "tvTileScale")
    val borderColor = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        onClick = model.onClick,
        enabled = model.enabled,
        shape = RoundedCornerShape(VylloRadius.lg),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (model.enabled) 0.9f else 0.4f),
        modifier = modifier
            .height(132.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(2.dp, borderColor, RoundedCornerShape(VylloRadius.lg))
            .onFocusChanged { focused = it.isFocused }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = VylloSpacing.lg, vertical = VylloSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = model.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.width(VylloSpacing.md))
            Column {
                Text(
                    text = model.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = model.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TvSongCard(item: MusicItem, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.05f else 1f, label = "tvSongScale")
    val borderColor = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(VylloRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier
            .width(210.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(2.dp, borderColor, RoundedCornerShape(VylloRadius.md))
            .onFocusChanged { focused = it.isFocused }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .clip(RoundedCornerShape(topStart = VylloRadius.md, topEnd = VylloRadius.md))
            ) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(modifier = Modifier.padding(VylloSpacing.sm)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.uploader,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

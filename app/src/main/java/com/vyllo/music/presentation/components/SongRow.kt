package com.vyllo.music.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vyllo.music.HomeViewModel
import com.vyllo.music.SearchViewModel
import com.vyllo.music.LibraryViewModel
import com.vyllo.music.LocalLibraryViewModel
import com.vyllo.music.domain.model.MusicItem

@Composable
fun YTMSongRow(
    item: MusicItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    homeViewModel: HomeViewModel? = null,
    searchViewModel: SearchViewModel? = null,
    onRemoveClick: (() -> Unit)? = null,
    isLoading: Boolean = false
) {
    val libraryViewModel = LocalLibraryViewModel.current
    var showContextMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .ytmClickable(
                enabled = !isLoading,
                onLongClick = { showContextMenu = true },
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val context = LocalContext.current
        val imageRequest = remember(item.thumbnailUrl) {
            ImageRequest.Builder(context)
                .data(item.thumbnailUrl)
                .size(120, 120)
                .crossfade(false)
                .build()
        }
        Box(modifier = Modifier.size(52.dp)) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                    .alpha(if (isLoading) 0.5f else 1f)
            )
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).align(Alignment.Center),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isPlaying) {
                    Spacer(modifier = Modifier.width(8.dp))
                    AppleEqualizerBars(
                        isPlaying = true,
                        barColor = MaterialTheme.colorScheme.primary,
                        barWidth = 2.5.dp,
                        maxBarHeight = 13.dp
                    )
                }
            }
            Text(
                text = item.uploader,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            DownloadButton(item)
            
            if (onRemoveClick != null) {
                IconButton(onClick = onRemoveClick) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Remove from Playlist",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                IconButton(onClick = { libraryViewModel.showPlaylistAddDialog(item) }) {
                    Icon(
                        Icons.Rounded.PlaylistAdd, 
                        contentDescription = "Add to Playlist",
                        tint = MaterialTheme.colorScheme.onBackground.copy(0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    if (showContextMenu) {
        val context = LocalContext.current
        SongContextMenuSheet(
            item = item,
            onDismiss = { showContextMenu = false },
            onPlayNext = { libraryViewModel.playNext(item) },
            onAddToQueue = { libraryViewModel.addToQueue(item) },
            onAddToPlaylist = { libraryViewModel.showPlaylistAddDialog(item) },
            onDownload = { libraryViewModel.downloadSong(item) },
            onShare = { com.vyllo.music.core.utils.ShareIntentManager.shareSongLink(context, item) },
            onRemoveFromPlaylist = onRemoveClick
        )
    }
}

@Composable
fun PremiumSongRow(item: MusicItem, isPlaying: Boolean, index: Int, onClick: () -> Unit, isLoading: Boolean = false, isSelected: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .ytmClickable { if (!isLoading) onClick() }
            .background(
                when {
                    isPlaying -> Color.White.copy(0.05f)
                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else -> Color.Transparent
                }
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(64.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.thumbnailUrl)
                    .size(128, 128)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .alpha(if (isLoading) 0.5f else 1f)
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp).align(Alignment.Center),
                    strokeWidth = 2.dp,
                    color = PremiumAccent()
                )
            } else if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    AppleEqualizerBars(
                        isPlaying = true,
                        barColor = PremiumAccent(),
                        barWidth = 3.dp,
                        maxBarHeight = 18.dp
                    )
                }
            } else if (isSelected) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.3f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isPlaying) PremiumAccent() else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.uploader,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(0.5f),
                maxLines = 1
            )
        }
    }
}

@Composable
fun YTMCompactRow(
    item: MusicItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel? = null,
    isLoading: Boolean = false
) {
    val libraryViewModel = LocalLibraryViewModel.current
    var showContextMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .ytmClickable(
                enabled = !isLoading,
                onLongClick = { showContextMenu = true },
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val context = LocalContext.current
        val imageRequest = remember(item.thumbnailUrl) {
            ImageRequest.Builder(context)
                .data(item.thumbnailUrl)
                .size(150, 150)
                .crossfade(false)
                .build()
        }
        Box(modifier = Modifier.size(48.dp)) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .alpha(if (isLoading) 0.5f else 1f)
            )
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).align(Alignment.Center),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isPlaying) {
                    Spacer(modifier = Modifier.width(6.dp))
                    AppleEqualizerBars(
                        isPlaying = true,
                        barColor = MaterialTheme.colorScheme.primary,
                        barWidth = 2.dp,
                        maxBarHeight = 11.dp
                    )
                }
            }
            Text(
                text = item.uploader,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        DownloadButton(item)
    }

    if (showContextMenu) {
        val context = LocalContext.current
        SongContextMenuSheet(
            item = item,
            onDismiss = { showContextMenu = false },
            onPlayNext = { libraryViewModel.playNext(item) },
            onAddToQueue = { libraryViewModel.addToQueue(item) },
            onAddToPlaylist = { libraryViewModel.showPlaylistAddDialog(item) },
            onDownload = { libraryViewModel.downloadSong(item) },
            onShare = { com.vyllo.music.core.utils.ShareIntentManager.shareSongLink(context, item) }
        )
    }
}

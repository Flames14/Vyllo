package com.vyllo.music.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.data.download.PlaylistEntity
import com.vyllo.music.*
import com.vyllo.music.presentation.components.*
import com.vyllo.music.presentation.theme.VylloSpacing

// =========================================================================
// YTM PLAYLIST SCREEN
// =========================================================================
@Composable
fun YTMPlaylistScreen(
    viewModel: LibraryViewModel,
    playlist: PlaylistEntity,
    onBack: () -> Unit,
    onPlay: (MusicItem) -> Unit,
    currentPlayingItem: MusicItem?,
    loadingItemUrl: String? = null
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = VylloSpacing.xs,
                    end = VylloSpacing.screenHorizontal,
                    top = VylloSpacing.sm,
                    bottom = VylloSpacing.sm
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = VylloSpacing.sm)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (viewModel.currentPlaylistSongs.isEmpty()) {
                item(key = "empty_playlist_message", contentType = "empty") {
                    VylloEmptyState(
                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        title = "This playlist is empty",
                        message = "Add songs with the playlist button on any track.",
                        actionLabel = "Back to library",
                        onAction = onBack
                    )
                }
            } else {
                items(
                    items = viewModel.currentPlaylistSongs,
                    key = { "playlist_song_${it.playlistId}_${it.url}" },
                    contentType = { "song_row" }
                ) { song ->
                    val musicItem = MusicItem(song.title, song.url, song.uploader, song.thumbnailUrl)
                    YTMSongRow(
                        item = musicItem,
                        isPlaying = currentPlayingItem?.url == song.url,
                        onClick = { onPlay(musicItem) },
                        onRemoveClick = {
                            viewModel.removeSongFromPlaylist(playlist.id, song.url)
                        },
                        isLoading = loadingItemUrl == song.url
                    )
                }
            }
        }
    }
}

package com.vyllo.music.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 48.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            if (viewModel.currentPlaylistSongs.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No songs in this playlist",
                            color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
                        )
                    }
                }
            } else {
                items(viewModel.currentPlaylistSongs) { song ->
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

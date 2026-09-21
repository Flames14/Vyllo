package com.vyllo.music.presentation.components.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vyllo.music.PlayerUiState
import com.vyllo.music.domain.model.MusicItem

@Composable
fun PlayerRelatedTab(
    item: MusicItem,
    playerUiState: PlayerUiState,
    onPlayRelated: (MusicItem) -> Unit,
    modifier: Modifier = Modifier
) {
    // RELATED TAB (Exploratory Discoveries & More from Artist)
    val relatedListState = rememberLazyListState()
    LazyColumn(
        state = relatedListState,
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
    ) {
        // Section 1: More by Artist
        if (playerUiState.artistSongs.isNotEmpty()) {
            item {
                Text(
                    text = "More from ${item.uploader}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }
            items(
                items = playerUiState.artistSongs,
                key = { s -> "artist_${s.url}" }
            ) { song ->
                QueueSongRowItem(
                    item = song,
                    isActive = song.url == item.url,
                    onClick = { onPlayRelated(song) }
                )
            }
        }

        // Section 2: Discover Similar Songs & Artists
        if (playerUiState.discoverSimilarSongs.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "You Might Also Like",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(
                items = playerUiState.discoverSimilarSongs,
                key = { s -> "similar_${s.url}" }
            ) { song ->
                QueueSongRowItem(
                    item = song,
                    isActive = false,
                    onClick = { onPlayRelated(song) }
                )
            }
        }

        // Loading indicator if fetching
        if (playerUiState.isLoadingRelatedTab) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White.copy(0.6f),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

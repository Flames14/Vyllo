package com.vyllo.music.presentation.components.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vyllo.music.R
import com.vyllo.music.PlayerUiState
import com.vyllo.music.PlayerViewModel
import com.vyllo.music.domain.model.MusicItem

@Composable
fun PlayerUpNextTab(
    item: MusicItem,
    relatedSongs: List<MusicItem>,
    playerUiState: PlayerUiState,
    viewModel: PlayerViewModel,
    onPlayRelated: (MusicItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    // UP NEXT TAB (Queue + Dynamic Diverse Auto-Mix)
    val queueListState = rememberLazyListState()
    val isRefreshing = playerUiState.isLoadingRelatedTab
    // Only run the spin animation while refreshing — a permanently-running
    // infiniteTransition recomposes at 60fps even during flings (jank).
    val rotation = if (isRefreshing) {
        val infiniteTransition = rememberInfiniteTransition(label = "refresh_transition")
        val r by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Restart
            ),
            label = "refresh_spin"
        )
        r
    } else 0f

    LazyColumn(
        state = queueListState,
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
    ) {
        // Current Active Song
        item {
            QueueSongRowItem(
                item = item,
                isActive = true,
                onClick = { }
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.queue_playing_from),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "• " + stringResource(R.string.queue_track_count, relatedSongs.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.forceRefreshRelatedSongs()
                    },
                    modifier = Modifier.size(32.dp),
                    enabled = !isRefreshing
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Refresh Up Next mix",
                        tint = if (isRefreshing) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.75f),
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                if (isRefreshing) {
                                    rotationZ = rotation
                                }
                            }
                    )
                }
            }
        }

        if (isRefreshing) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
        }

        // Upcoming Dynamic Auto-Mix Songs — stable keys (no index) so fast
        // flings reuse item slots instead of rebinding the whole list.
        items(
            items = relatedSongs,
            key = { s -> "upnext_${s.url}" },
            contentType = { "queue_row" }
        ) { song ->
            QueueSongRowItem(
                item = song,
                isActive = song.url == item.url,
                onClick = { onPlayRelated(song) }
            )
        }

        if (playerUiState.isLoadingMoreRelated) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
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

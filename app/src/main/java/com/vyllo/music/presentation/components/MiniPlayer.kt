package com.vyllo.music.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vyllo.music.LibraryViewModel
import com.vyllo.music.LocalLibraryViewModel
import com.vyllo.music.domain.model.MusicItem

import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

@Composable
fun PremiumMiniPlayer(
    musicItem: MusicItem, 
    isPlaying: Boolean, 
    isLoading: Boolean, 
    onTogglePlay: () -> Unit,
    controller: androidx.media3.session.MediaController? = null,
    onNext: () -> Unit = {},
    onPrev: () -> Unit = {}
) {
    val libraryViewModel = LocalLibraryViewModel.current
    var progress by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    androidx.compose.runtime.LaunchedEffect(controller, isPlaying) {
        if (!isPlaying || controller == null) {
            val dur = controller?.duration ?: 0L
            val pos = controller?.currentPosition ?: 0L
            progress = if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
            return@LaunchedEffect
        }
        while (isPlaying) {
            val dur = controller.duration
            val pos = controller.currentPosition
            progress = if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }

    var totalDrag by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (totalDrag < -60f) {
                            onNext()
                        } else if (totalDrag > 60f) {
                            onPrev()
                        }
                        totalDrag = 0f
                    },
                    onDragCancel = { totalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val context = LocalContext.current
            val miniPlayerImageRequest = remember(musicItem.thumbnailUrl) {
                ImageRequest.Builder(context)
                    .data(musicItem.thumbnailUrl)
                    .size(128, 128)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = miniPlayerImageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = musicItem.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = musicItem.uploader,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = { libraryViewModel.showPlaylistAddDialog(musicItem) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Rounded.PlaylistAdd,
                    contentDescription = "Add to Playlist",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(40.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "Next Track",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        
        // YouTube Music style continuous sleek progress bar
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
    }
}

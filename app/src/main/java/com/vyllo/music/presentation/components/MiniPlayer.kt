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

@Composable
fun PremiumMiniPlayer(
    musicItem: MusicItem, 
    isPlaying: Boolean, 
    isLoading: Boolean, 
    onTogglePlay: () -> Unit
) {
    val libraryViewModel = LocalLibraryViewModel.current
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val context = LocalContext.current
        val miniPlayerImageRequest = remember(musicItem.thumbnailUrl) {
            ImageRequest.Builder(context)
                .data(musicItem.thumbnailUrl)
                .size(120, 120)
                .build()
        }
        AsyncImage(
            model = miniPlayerImageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = musicItem.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = musicItem.uploader,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                maxLines = 1
            )
        }

        IconButton(onClick = { libraryViewModel.showPlaylistAddDialog(musicItem) }) {
            Icon(
                Icons.Rounded.PlaylistAdd,
                contentDescription = "Add to Playlist",
                tint = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(onClick = onTogglePlay) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
            } else {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

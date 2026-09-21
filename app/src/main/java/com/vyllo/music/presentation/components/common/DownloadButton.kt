package com.vyllo.music.presentation.components.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vyllo.music.LocalLibraryViewModel
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.presentation.theme.VylloSize

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadButton(item: MusicItem) {
    val viewModel = LocalLibraryViewModel.current
    val itemUrl = item.url
    val isDownloaded by remember(itemUrl) {
        derivedStateOf { viewModel.downloadedSongs.any { it.url == itemUrl } }
    }
    val progress by remember(itemUrl) {
        derivedStateOf { viewModel.downloadProgress[itemUrl] }
    }

    Box(
        modifier = Modifier
            .size(VylloSize.minTouchTarget)
            .combinedClickable(
                onClick = {
                    if (progress != null) {
                        viewModel.cancelDownload(itemUrl)
                    } else if (!isDownloaded) {
                        viewModel.downloadSong(item)
                    }
                },
                onLongClick = {
                    if (isDownloaded) {
                        viewModel.deleteDownload(itemUrl)
                    }
                },
                onLongClickLabel = "Remove download"
            ),
        contentAlignment = Alignment.Center
    ) {
        if (progress != null) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress!! / 100f },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Cancel",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Icon(
                imageVector = if (isDownloaded) Icons.Rounded.CheckCircle else Icons.Rounded.DownloadForOffline,
                contentDescription = if (isDownloaded) {
                    "Downloaded — long press to remove"
                } else {
                    "Download for offline listening"
                },
                tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

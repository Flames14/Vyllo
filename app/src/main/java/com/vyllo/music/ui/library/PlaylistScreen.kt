package com.vyllo.music.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import com.vyllo.music.R
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.PlaylistEntity
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
    loadingItemUrl: String? = null,
    isPinned: Boolean = false,
    onTogglePinned: () -> Unit = {},
    onExportM3u: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    onStartRadio: (MusicItem) -> Unit = {},
    onDownloadAll: () -> Unit = {}
) {
    var showExportMenu by remember { mutableStateOf(false) }
    var showCoverEditor by remember { mutableStateOf(false) }

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
                    contentDescription = stringResource(R.string.cd_back),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = VylloSpacing.sm).weight(1f)
            )
            Box {
                IconButton(onClick = { showExportMenu = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.header_more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showExportMenu,
                    onDismissRequest = { showExportMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.playlist_export_m3u)) },
                        onClick = {
                            showExportMenu = false
                            onExportM3u()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.playlist_export_csv)) },
                        onClick = {
                            showExportMenu = false
                            onExportCsv()
                        }
                    )
                    val radioSeed = viewModel.currentPlaylistSongs.firstOrNull()?.let { first ->
                        MusicItem(first.title, first.url, first.uploader, first.thumbnailUrl)
                    }
                    if (radioSeed != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.playlist_download_all)) },
                            onClick = {
                                showExportMenu = false
                                onDownloadAll()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.header_start_radio)) },
                            onClick = {
                                showExportMenu = false
                                onStartRadio(radioSeed)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Radio,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }
            }
            IconButton(onClick = onTogglePinned) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = stringResource(
                        if (isPinned) R.string.playlist_unpin_from_home else R.string.playlist_pin_to_home
                    ),
                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VylloSpacing.screenHorizontal, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val visibleCover = playlist.coverUrl
                ?: viewModel.currentPlaylistSongs.firstOrNull()?.thumbnailUrl?.takeIf { it.isNotBlank() }
            if (!visibleCover.isNullOrBlank()) {
                AsyncImage(
                    model = visibleCover,
                    contentDescription = stringResource(R.string.playlist_cover_preview),
                    contentScale = ContentScale.Crop,
                    alignment = androidx.compose.ui.BiasAlignment(
                        playlist.coverCropX * 2f - 1f,
                        playlist.coverCropY * 2f - 1f
                    ),
                    modifier = Modifier.size(88.dp).clip(RoundedCornerShape(14.dp))
                )
            }
            TextButton(
                onClick = { showCoverEditor = true },
                enabled = viewModel.currentPlaylistSongs.isNotEmpty()
            ) {
                Text(stringResource(R.string.playlist_cover_edit))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (viewModel.currentPlaylistSongs.isEmpty()) {
                item(key = "empty_playlist_message", contentType = "empty") {
                    VylloEmptyState(
                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        title = stringResource(R.string.library_playlist_empty),
                        message = stringResource(R.string.playlist_empty_body),
                        actionLabel = stringResource(R.string.playlist_back_action),
                        onAction = onBack
                    )
                }
            } else {
                itemsIndexed(
                    items = viewModel.currentPlaylistSongs,
                    key = { _, song -> "playlist_song_${song.playlistId}_${song.url}" },
                    contentType = { _, _ -> "song_row" }
                ) { index, song ->
                    val musicItem = MusicItem(song.title, song.url, song.uploader, song.thumbnailUrl)
                    YTMSongRow(
                        item = musicItem,
                        isPlaying = currentPlayingItem?.url == song.url,
                        onClick = { onPlay(musicItem) },
                        onRemoveClick = {
                            viewModel.removeSongFromPlaylist(playlist.id, song.url)
                        },
                        onMoveUpClick = if (index > 0) {
                            { viewModel.moveSongInPlaylist(playlist.id, index, index - 1) }
                        } else null,
                        onMoveDownClick = if (index < viewModel.currentPlaylistSongs.lastIndex) {
                            { viewModel.moveSongInPlaylist(playlist.id, index, index + 1) }
                        } else null,
                        isLoading = loadingItemUrl == song.url
                    )
                }
            }
        }
    }

    if (showCoverEditor) {
        PlaylistCoverEditorDialog(
            songs = viewModel.currentPlaylistSongs,
            initialCoverUrl = playlist.coverUrl,
            initialCropX = playlist.coverCropX,
            initialCropY = playlist.coverCropY,
            onDismiss = { showCoverEditor = false },
            onSave = { url, cropX, cropY ->
                viewModel.updatePlaylistCover(playlist.id, url, cropX, cropY)
                showCoverEditor = false
            }
        )
    }
}

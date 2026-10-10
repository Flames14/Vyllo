package com.vyllo.music.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.R
import com.vyllo.music.*
import com.vyllo.music.presentation.components.*
import com.vyllo.music.presentation.theme.VylloRadius
import com.vyllo.music.presentation.theme.VylloSize
import com.vyllo.music.presentation.theme.VylloSpacing
import com.vyllo.music.ui.alarm.AlarmScreen

// =========================================================================
// YTM LIBRARY SCREEN
// =========================================================================
@Composable
fun YTMLibraryScreen(
    viewModel: LibraryViewModel,
    onPlay: (MusicItem) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    currentPlayingItem: MusicItem?,
    onRecognizeClick: () -> Unit,
    loadingItemUrl: String? = null,
    onNavigateToAlarms: () -> Unit = {}
) {
    val scrollState = rememberLazyListState()
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            }
        )
    }

    if (viewModel.showYouTubeSyncSheet) {
        YouTubeSyncBottomSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.showYouTubeSyncSheet = false }
        )
    }

    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item(key = "ytm_header", contentType = "header") {
            YTMHeader(
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                onRecognizeClick = onRecognizeClick
            )
        }

        item(key = "alarms_card", contentType = "card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = VylloSpacing.screenHorizontal,
                        vertical = VylloSpacing.sm
                    )
                    .pressScaleClickable(onClick = onNavigateToAlarms),
                shape = RoundedCornerShape(VylloRadius.lg),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = VylloSize.minTouchTarget)
                        .padding(VylloSpacing.lg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Rounded.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(VylloSize.iconXLarge)
                        )
                        Spacer(modifier = Modifier.width(VylloSpacing.lg))
                        Column {
                            Text(
                                text = stringResource(R.string.library_alarms_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = stringResource(R.string.library_alarms_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(VylloSize.iconMedium)
                    )
                }
            }
        }

        item(key = "playlists_header", contentType = "header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.library_playlists_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        viewModel.showYouTubeSyncSheet = true
                        if (viewModel.isGoogleConnected.value) {
                            viewModel.loadRemoteYouTubePlaylists()
                        }
                    }) {
                        Icon(
                            Icons.Rounded.Sync,
                            contentDescription = stringResource(R.string.cd_sync_youtube_playlists),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Rounded.Add, stringResource(R.string.cd_new_playlist), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        if (viewModel.allPlaylists.isEmpty()) {
            item(key = "empty_playlists", contentType = "empty") {
                VylloEmptyState(
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    title = stringResource(R.string.library_no_playlists),
                    message = stringResource(R.string.library_no_playlists_body),
                    actionLabel = stringResource(R.string.library_create_playlist_action),
                    onAction = { showCreateDialog = true }
                )
            }
        } else {
            items(
                items = viewModel.allPlaylists,
                key = { "playlist_${it.id}" },
                contentType = { "playlist_item" }
            ) { playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .ytmClickable { 
                            viewModel.selectedLocalPlaylist = playlist
                            viewModel.loadPlaylistSongs(playlist.id)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = { viewModel.deletePlaylist(playlist) }) {
                        Icon(Icons.Rounded.Delete, stringResource(R.string.cd_delete), tint = MaterialTheme.colorScheme.onBackground.copy(0.3f), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        item(key = "downloads_header", contentType = "header") {
              Column(modifier = Modifier.padding(bottom = 8.dp)) {
                  YTMSectionHeader(title = stringResource(R.string.library_downloads_title))
              }
        }
        
        if (viewModel.downloadedSongs.isEmpty()) {
            item(key = "empty_downloads", contentType = "empty") {
                VylloEmptyState(
                    icon = Icons.Rounded.DownloadDone,
                    title = stringResource(R.string.library_no_downloads),
                    message = stringResource(R.string.library_downloads_body),
                    actionLabel = stringResource(R.string.library_find_music_action),
                    onAction = onSearchClick
                )
            }
        } else {
            items(
                items = viewModel.downloadedSongs,
                key = { "download_${it.url}" },
                contentType = { "song_row" }
              ) { entry ->
                  val musicItem = MusicItem(entry.title, entry.url, entry.uploader, entry.thumbnailUrl)
                  Row(
                      modifier = Modifier.fillMaxWidth(),
                      verticalAlignment = Alignment.CenterVertically
                  ) {
                      Box(modifier = Modifier.weight(1f)) {
                          YTMSongRow(
                              item = musicItem,
                              isPlaying = currentPlayingItem?.url == entry.url,
                              onClick = { onPlay(musicItem) },
                              isLoading = loadingItemUrl == entry.url
                          )
                      }
                      IconButton(
                          onClick = { viewModel.deleteDownload(entry.url) },
                          modifier = Modifier.size(40.dp)
                      ) {
                          Icon(
                              Icons.Rounded.Delete,
                              contentDescription = stringResource(R.string.download_cd_remove),
                              tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                              modifier = Modifier.size(20.dp)
                          )
                      }
                  }
              }
        }
    }
}

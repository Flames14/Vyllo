package com.vyllo.music.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vyllo.music.R
import com.vyllo.music.SmartPlaylistViewModel
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.SmartPlaylist
import com.vyllo.music.presentation.components.VylloEmptyState
import com.vyllo.music.presentation.components.YTMSongRow
import com.vyllo.music.presentation.theme.VylloRadius
import com.vyllo.music.presentation.theme.VylloSpacing

/**
 * One screen for the computed collections. There is no editing here on
 * purpose: the content is derived from likes, downloads or play counts, so
 * every row would be undone by the next data change.
 */
@Composable
fun SmartPlaylistScreen(
    viewModel: SmartPlaylistViewModel,
    onBack: () -> Unit,
    onPlay: (MusicItem) -> Unit,
    currentPlayingItem: MusicItem?,
    loadingItemUrl: String? = null
) {
    val kind = viewModel.kind
    val songs by viewModel.songs.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = VylloSpacing.xs,
                    end = VylloSpacing.screenHorizontal,
                    top = VylloSpacing.sm
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = kind?.titleRes()?.let { stringResource(it) }.orEmpty(),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (songs.isEmpty()) {
            VylloEmptyState(
                icon = Icons.Rounded.PlayArrow,
                title = kind?.emptyTitleRes()?.let { stringResource(it) }.orEmpty(),
                message = kind?.emptyBodyRes()?.let { stringResource(it) }.orEmpty()
            )
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VylloSpacing.screenHorizontal, vertical = VylloSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VylloSpacing.md)
        ) {
            Text(
                text = pluralStringResource(R.plurals.liked_song_count, songs.size, songs.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onPlay(songs.first()) },
                shape = RoundedCornerShape(VylloRadius.pill)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.width(VylloSpacing.lg)
                )
                Spacer(Modifier.width(VylloSpacing.xs))
                Text(stringResource(R.string.action_play_all))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = VylloSpacing.screenHorizontal,
                end = VylloSpacing.screenHorizontal,
                bottom = VylloSpacing.xl
            )
        ) {
            items(songs, key = { it.url }) { item ->
                YTMSongRow(
                    item = item,
                    isPlaying = currentPlayingItem?.url == item.url,
                    isLoading = loadingItemUrl == item.url,
                    onClick = { onPlay(item) }
                )
            }
        }
    }
}

private fun SmartPlaylist.titleRes(): Int = when (this) {
    SmartPlaylist.LIKED -> R.string.library_liked_title
    SmartPlaylist.OFFLINE -> R.string.library_downloads_title
    SmartPlaylist.TOP -> R.string.stats_top_tracks
}

private fun SmartPlaylist.emptyTitleRes(): Int = when (this) {
    SmartPlaylist.LIKED -> R.string.library_liked_empty
    SmartPlaylist.OFFLINE -> R.string.library_no_downloads
    SmartPlaylist.TOP -> R.string.stats_empty_title
}

private fun SmartPlaylist.emptyBodyRes(): Int = when (this) {
    SmartPlaylist.LIKED -> R.string.library_liked_empty_body
    SmartPlaylist.OFFLINE -> R.string.library_downloads_empty_body
    SmartPlaylist.TOP -> R.string.stats_empty_body
}

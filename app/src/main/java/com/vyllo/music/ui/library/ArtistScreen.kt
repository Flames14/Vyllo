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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vyllo.music.LibraryViewModel
import com.vyllo.music.R
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.normalizeArtistKey
import com.vyllo.music.presentation.components.VylloEmptyState
import com.vyllo.music.presentation.components.VylloErrorState
import com.vyllo.music.presentation.components.VylloLoadingIndicator
import com.vyllo.music.presentation.components.YTMSongRow
import com.vyllo.music.presentation.theme.VylloSpacing

/**
 * Artist page built from what the library already knows: the songs this device
 * has played, liked or downloaded, topped up with a remote artist search.
 */
@Composable
fun YTMArtistScreen(
    viewModel: LibraryViewModel,
    artistName: String,
    onBack: () -> Unit,
    onPlay: (MusicItem) -> Unit,
    currentPlayingItem: MusicItem?,
    loadingItemUrl: String? = null
) {
    LaunchedEffect(artistName) { viewModel.openArtist(artistName) }
    DisposableEffect(Unit) {
        onDispose { viewModel.clearArtist() }
    }

    // Only trust state that belongs to this route; a previous artist may still
    // be resident while the new lookup runs.
    val artist = viewModel.selectedArtist?.takeIf { it.key == normalizeArtistKey(artistName) }

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
                text = stringResource(R.string.library_artists_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = VylloSpacing.sm)
            )
        }

        if (artist == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                VylloLoadingIndicator()
            }
            return@Column
        }

        val songs = viewModel.artistSongs
        val shownCount = songs.size.takeIf { it > 0 } ?: artist.songCount

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item(key = "artist_header", contentType = "header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VylloSpacing.xxl, vertical = VylloSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = artist.thumbnailUrl,
                        contentDescription = stringResource(R.string.cd_artist),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(VylloSpacing.md))
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (shownCount > 0) {
                        Spacer(modifier = Modifier.height(VylloSpacing.xs))
                        Text(
                            text = pluralStringResource(
                                R.plurals.artist_song_count,
                                shownCount,
                                shownCount
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            when {
                viewModel.isArtistLoading && songs.isEmpty() -> item(key = "artist_loading") {
                    VylloLoadingIndicator()
                }

                viewModel.artistLoadFailed && songs.isEmpty() -> item(key = "artist_error") {
                    VylloErrorState(
                        message = stringResource(R.string.artist_loading_failed),
                        onRetry = viewModel::retrySelectedArtist
                    )
                }

                songs.isEmpty() -> item(key = "artist_empty") {
                    VylloEmptyState(
                        icon = Icons.Rounded.Person,
                        title = stringResource(R.string.artist_empty_title),
                        message = stringResource(R.string.artist_empty_body)
                    )
                }

                else -> items(
                    items = songs,
                    key = { "artist_song_${it.url}" },
                    contentType = { "song_row" }
                ) { item ->
                    YTMSongRow(
                        item = item,
                        isPlaying = currentPlayingItem?.url == item.url,
                        onClick = { onPlay(item) },
                        isLoading = loadingItemUrl == item.url
                    )
                }
            }
        }
    }
}

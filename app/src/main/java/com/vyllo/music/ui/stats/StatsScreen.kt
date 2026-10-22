package com.vyllo.music.ui.stats

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vyllo.music.R
import com.vyllo.music.StatsViewModel
import com.vyllo.music.domain.model.ListeningStats
import com.vyllo.music.domain.model.ListeningTimeFormat
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.TopArtist
import com.vyllo.music.domain.model.TopTrack
import com.vyllo.music.presentation.components.VylloEmptyState
import com.vyllo.music.presentation.components.YTMSongRow
import com.vyllo.music.presentation.theme.VylloRadius
import com.vyllo.music.presentation.theme.VylloSpacing

/**
 * Listening statistics built purely from local playback history: totals, the
 * most played tracks and the artists behind them.
 */
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onBack: () -> Unit,
    onPlay: (MusicItem) -> Unit,
    currentPlayingItem: MusicItem?,
    loadingItemUrl: String? = null
) {
    val stats by viewModel.stats.collectAsState()

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
                text = stringResource(R.string.stats_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (!stats.hasData) {
            VylloEmptyState(
                icon = Icons.Rounded.BarChart,
                title = stringResource(R.string.stats_empty_title),
                message = stringResource(R.string.stats_empty_body)
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = VylloSpacing.screenHorizontal,
                end = VylloSpacing.screenHorizontal,
                top = VylloSpacing.sm,
                bottom = VylloSpacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(VylloSpacing.xs)
        ) {
            item(key = "totals") {
                TotalsCard(stats)
            }

            if (stats.topTracks.isNotEmpty()) {
                item(key = "tracks_header") {
                    SectionHeader(
                        title = stringResource(R.string.stats_top_tracks),
                        subtitle = stringResource(R.string.stats_top_tracks_desc)
                    )
                }
                itemsIndexed(
                    items = stats.topTracks,
                    key = { _, track -> "top_${track.item.url}" }
                ) { index, track ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            YTMSongRow(
                                item = track.item,
                                isPlaying = currentPlayingItem?.url == track.item.url,
                                isLoading = loadingItemUrl == track.item.url,
                                onClick = { onPlay(track.item) }
                            )
                        }
                    }
                    TrackStatsLine(track)
                }
            }

            if (stats.topArtists.isNotEmpty()) {
                item(key = "artists_header") {
                    SectionHeader(
                        title = stringResource(R.string.stats_top_artists),
                        subtitle = stringResource(R.string.stats_top_artists_desc)
                    )
                }
                items(
                    items = stats.topArtists,
                    key = { "artist_${it.name}" }
                ) { artist ->
                    ArtistStatRow(artist)
                }
            }
        }
    }
}

@Composable
private fun TotalsCard(stats: ListeningStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(VylloRadius.lg),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(VylloSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TotalCell(
                    value = ListeningTimeFormat.format(stats.totalListenedMs),
                    label = stringResource(R.string.stats_total_time)
                )
                TotalCell(
                    value = stats.totalPlays.toString(),
                    label = pluralStringResource(
                        R.plurals.stats_play_count,
                        stats.totalPlays,
                        stats.totalPlays
                    )
                )
                TotalCell(
                    value = stats.totalTracks.toString(),
                    label = stringResource(R.string.stats_tracks_label)
                )
            }
            Spacer(Modifier.height(VylloSpacing.sm))
            Text(
                text = stringResource(
                    R.string.stats_average_listen,
                    ListeningTimeFormat.format(stats.averageListenMs)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun TotalCell(value: String, label: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(top = VylloSpacing.md, bottom = VylloSpacing.xxs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TrackStatsLine(track: TopTrack) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, bottom = VylloSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(VylloSpacing.sm)
    ) {
        Text(
            text = pluralStringResource(
                R.plurals.stats_play_count,
                track.playCount,
                track.playCount
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = ListeningTimeFormat.format(track.listenedMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ArtistStatRow(artist: TopArtist) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = VylloSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = artist.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(VylloSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name.ifBlank { stringResource(R.string.stats_unknown_artist) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = pluralStringResource(
                    R.plurals.stats_play_count,
                    artist.plays,
                    artist.plays
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = ListeningTimeFormat.format(artist.listenedMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

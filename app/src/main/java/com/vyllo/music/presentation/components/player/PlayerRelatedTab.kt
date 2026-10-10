package com.vyllo.music.presentation.components.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vyllo.music.PlayerUiState
import com.vyllo.music.R
import com.vyllo.music.domain.model.MusicItem

@Composable
fun PlayerRelatedTab(
    item: MusicItem,
    playerUiState: PlayerUiState,
    onPlayRelated: (MusicItem) -> Unit,
    modifier: Modifier = Modifier
) {
    // RELATED TAB: "More from {artist}", then the discovery-weighted ordering of the
    // same ranked pool that seeds Up Next, then wider artist-anchored discovery.
    //
    // Each section owns its own loading state and renders a skeleton immediately, so
    // the tab is never a blank screen waiting on the slowest of three searches.
    val moreFromTitle = stringResource(R.string.related_more_from, item.uploader)
    val similarToThisTrackTitle = stringResource(R.string.related_similar_track)
    val youMightAlsoLikeTitle = stringResource(R.string.related_you_might_also_like)
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
    ) {
        relatedSection(
            title = moreFromTitle,
            songs = playerUiState.artistSongs,
            isLoading = playerUiState.isLoadingArtistSongs,
            keyPrefix = "artist",
            activeUrl = item.url,
            onPlayRelated = onPlayRelated
        )

        relatedSection(
            title = similarToThisTrackTitle,
            songs = playerUiState.discoverySongs,
            isLoading = playerUiState.isLoadingPool,
            keyPrefix = "discovery",
            activeUrl = item.url,
            onPlayRelated = onPlayRelated
        )

        relatedSection(
            title = youMightAlsoLikeTitle,
            songs = playerUiState.discoverSimilarSongs,
            isLoading = playerUiState.isLoadingSimilarSongs,
            keyPrefix = "similar",
            activeUrl = item.url,
            onPlayRelated = onPlayRelated
        )
    }
}

/**
 * One Related section: header immediately, skeleton rows while its search is in
 * flight, then the real rows. Index is part of the key so a duplicate URL across
 * sections (or within one) cannot crash LazyColumn keying.
 */
private fun LazyListScope.relatedSection(
    title: String,
    songs: List<MusicItem>,
    isLoading: Boolean,
    keyPrefix: String,
    activeUrl: String,
    onPlayRelated: (MusicItem) -> Unit
) {
    if (songs.isEmpty() && !isLoading) return

    item(key = "${keyPrefix}_header") {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )
    }

    if (isLoading && songs.isEmpty()) {
        val skeletonRows = 4
        items(count = skeletonRows, key = { index -> "${keyPrefix}_skeleton_$index" }) {
            RelatedRowSkeleton()
        }
    } else {
        itemsIndexed(
            items = songs,
            key = { index, song -> "${keyPrefix}_${song.url}#$index" },
            contentType = { _, _ -> "related_row" }
        ) { _, song ->
            QueueSongRowItem(
                item = song,
                isActive = song.url == activeUrl,
                onClick = { onPlayRelated(song) }
            )
        }
    }

    item(key = "${keyPrefix}_spacer") { Spacer(Modifier.height(4.dp)) }
}

/**
 * Placeholder row with the same height as a real one, so the list does not jump
 * when real rows replace the skeleton.
 */
@Composable
private fun RelatedRowSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(14.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = Color.White.copy(alpha = 0.35f),
                strokeWidth = 1.5.dp
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.3f)
                .height(11.dp)
        )
    }
}
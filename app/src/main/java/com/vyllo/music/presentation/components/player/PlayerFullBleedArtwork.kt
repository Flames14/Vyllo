package com.vyllo.music.presentation.components.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.vyllo.music.PlayerUiState
import com.vyllo.music.domain.model.ArtworkCandidates
import com.vyllo.music.domain.model.MusicItem

/**
 * Full-bleed album art behind the whole player screen.
 *
 * The artwork fills the window edge to edge and fades into the player
 * background through a scrim, so the header and track info sit directly on the
 * image instead of on a small centered card. Uses the same fallback ladder as
 * the player card and a slow Ken Burns drift while playback runs.
 */
@Composable
fun PlayerFullBleedArtwork(
    item: MusicItem,
    playerUiState: PlayerUiState,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    // Video mode renders the video surface in PlayerArtwork instead.
    if (playerUiState.isVideoMode) return

    val resolvedUrl = playerUiState.resolvedThumbnailUrl
    val candidates = remember(item, resolvedUrl) {
        ArtworkCandidates.forItem(item, resolvedUrl)
    }
    var candidateIndex by remember(item.thumbnailUrl, resolvedUrl) { mutableIntStateOf(0) }
    val currentUrl = candidates.getOrNull(candidateIndex) ?: candidates.firstOrNull()

    val appContext = LocalContext.current.applicationContext
    val background = MaterialTheme.colorScheme.background

    val imageRequest = remember(appContext, currentUrl) {
        currentUrl?.let { url ->
            ImageRequest.Builder(appContext)
                .data(url)
                .crossfade(true)
                .size(1080)
                .build()
        }
    }

    val transition = rememberInfiniteTransition(label = "player_backdrop")
    val drift by transition.animateFloat(
        initialValue = 1.02f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "player_backdrop_zoom"
    )
    val scale = if (isPlaying) drift else 1.02f

    Box(modifier = modifier.fillMaxSize().background(background)) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                onState = { state ->
                    if (state is AsyncImagePainter.State.Error && candidateIndex < candidates.size - 1) {
                        candidateIndex++
                    }
                }
            )
        }

        // Status-bar legibility at the top, then a long fade into the player
        // background so the controls below stay readable.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = 0.55f),
                        0.12f to Color.Transparent,
                        0.38f to Color.Transparent,
                        0.56f to background.copy(alpha = 0.78f),
                        0.72f to background,
                        1.00f to background
                    )
                )
        )
    }
}

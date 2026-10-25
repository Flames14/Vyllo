package com.vyllo.music.presentation.components.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import com.vyllo.music.PlayerUiState
import com.vyllo.music.PlayerViewModel
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.presentation.components.VideoPlayerOverlayControls
import com.vyllo.music.presentation.components.VideoSurface

@Composable
fun PlayerArtwork(
    item: MusicItem,
    isPlaying: Boolean,
    isLoading: Boolean,
    expandProgress: Float,
    currentPosition: Long,
    duration: Long,
    controller: MediaController?,
    viewModel: PlayerViewModel,
    playerUiState: PlayerUiState,
    seekFeedback: String?,
    onSeekFeedback: (String?) -> Unit,
    onPositionChange: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    artworkVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // pointerInput(Unit) below starts ONE gesture coroutine that captures these
    // by value on first composition. Without updated-state holders the
    // double-tap seek kept using the original track's duration/controller
    // forever, clamping seeks against the wrong length after a track change.
    val currentDuration by rememberUpdatedState(duration)
    val currentController by rememberUpdatedState(controller)
    val currentOnPositionChange by rememberUpdatedState(onPositionChange)
    val currentOnSeekFeedback by rememberUpdatedState(onSeekFeedback)
    val artworkPlayScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.88f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "artworkPlayScale"
    )
    val activeArtworkUrl = remember(item, playerUiState.resolvedThumbnailUrl) {
        playerUiState.resolvedThumbnailUrl?.takeIf { it.isNotBlank() }
            ?: item.thumbnailUrl.takeIf { it.isNotBlank() }
            ?: com.vyllo.music.domain.model.ArtworkCandidates.forItem(item).firstOrNull().orEmpty()
    }
    val artworkContext = LocalContext.current
    val lowRamMode = remember(artworkContext) {
        com.vyllo.music.core.utils.DevicePerf.isLowRamDevice(artworkContext)
    }
    val glowBlur = if (lowRamMode) 18.dp else 36.dp
    val cardShadow = if (lowRamMode) 4.dp else 20.dp

    // 2. Large High-Resolution Album Artwork (Smoothly expands / zooms with playback, drag & double-tap seeking)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .scale((1f - (expandProgress * 0.08f)) * artworkPlayScale),
        contentAlignment = Alignment.Center
    ) {
        // Ambient Colored Glow behind Artwork (Apple Music style)
        //
        // The request is remembered and crossfade is off: this composable sits
        // above the queue drawer, so rebuilding an ImageRequest and animating a
        // crossfade while the drawer list was scrolling meant a fresh decode plus
        // a fade on top of the list's own work. The artwork URL is the only input
        // that can change it.
        if (artworkVisible && isPlaying && activeArtworkUrl.isNotBlank() && !viewModel.isVideoMode) {
            val glowContext = LocalContext.current
            val glowRequest = remember(activeArtworkUrl) {
                coil.request.ImageRequest.Builder(glowContext)
                    .data(activeArtworkUrl)
                    .size(180, 180)
                    .crossfade(false)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .build()
            }
            AsyncImage(
                model = glowRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize(0.92f)
                    .blur(glowBlur)
                    .alpha(0.65f)
            )
        }

        val cardModifier = if (artworkVisible) {
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black)
                .shadow(cardShadow, RoundedCornerShape(22.dp), spotColor = Color.Black.copy(alpha = 0.6f))
        } else {
            // Full-bleed backdrop mode: this layer only carries the seek gesture.
            Modifier.fillMaxSize()
        }
        Box(
            modifier = cardModifier
                .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (offset.x < size.width / 2) {
                            seekRelative(currentController, currentDuration, -10000L) { currentOnPositionChange(it) }
                            currentOnSeekFeedback("-10")
                        } else {
                            seekRelative(currentController, currentDuration, 10000L) { currentOnPositionChange(it) }
                            currentOnSeekFeedback("+10")
                        }
                    }
                )
            }
    ) {
        if (viewModel.isVideoMode) {
            VideoSurface(controller = controller, modifier = Modifier.fillMaxSize())
            VideoPlayerOverlayControls(
                isPlaying = isPlaying,
                isLoading = isLoading,
                currentPosition = currentPosition,
                duration = duration,
                isFullScreen = playerUiState.isFullScreenVideo,
                onTogglePlay = onTogglePlay,
                onSeek = { newPercent ->
                    val newPos = (newPercent * duration).toLong()
                    markManualSeek()
                    controller?.seekTo(newPos)
                    onPositionChange(newPos)
                },
                onForward = { seekRelative(controller, duration, 10000L) { onPositionChange(it) } },
                onRewind = { seekRelative(controller, duration, -10000L) { onPositionChange(it) } },
                onToggleFullScreen = { viewModel.setFullScreenVideo(!playerUiState.isFullScreenVideo) }
            )
        } else {
            val resolvedUrl = playerUiState.resolvedThumbnailUrl
            val candidates = remember(item, resolvedUrl) {
                com.vyllo.music.domain.model.ArtworkCandidates.forItem(item, resolvedUrl)
            }
            var candidateIndex by remember(item.thumbnailUrl, resolvedUrl) { mutableIntStateOf(0) }
            val currentThumbnail = candidates.getOrNull(candidateIndex) ?: resolvedUrl ?: item.thumbnailUrl

            // Remember request: parent recomposes 2.5x/s from seek loop, must not rebuild decoder.
            val appContext = LocalContext.current.applicationContext
            val imageRequest = remember(appContext, currentThumbnail) {
                coil.request.ImageRequest.Builder(appContext)
                    .data(currentThumbnail)
                    .crossfade(false)
                    .size(520)
                    .memoryCacheKey("artwork_$currentThumbnail")
                    .diskCacheKey("artwork_$currentThumbnail")
                    .allowHardware(true)
                    .build()
            }
            if (artworkVisible) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onState = { state ->
                        if (state is coil.compose.AsyncImagePainter.State.Error) {
                            if (candidateIndex < candidates.size - 1) {
                                candidateIndex++
                            }
                        }
                    }
                )
            }

            // Double-tap seeking indicator overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = seekFeedback != null,
                enter = fadeIn() + scaleIn(initialScale = 0.7f),
                exit = fadeOut() + scaleOut(targetScale = 1.15f),
                modifier = Modifier
                    .align(if (seekFeedback == "-10") Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(24.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.75f),
                    contentColor = Color.White,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (seekFeedback == "-10") Icons.Rounded.Replay10 else Icons.Rounded.Forward10,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (seekFeedback == "-10") "-10 sec" else "+10 sec",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
fun PlayerTrackInfo(
    item: MusicItem,
    expandProgress: Float,
    modifier: Modifier = Modifier
) {
    // 3. Track Title (with chevron >) and Artists
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha((1f - (expandProgress * 2f)).coerceIn(0f, 1f)),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = item.uploader,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
            color = Color.White.copy(alpha = 0.65f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}



package com.vyllo.music.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.vyllo.music.presentation.theme.VylloMotion
import com.vyllo.music.presentation.theme.VylloRadius
import com.vyllo.music.presentation.theme.VylloSize

/**
 * Shared artwork loader for rows and cards.
 *
 * Each row used to build its own ImageRequest size, cache policy, shape, and
 * loading overlay. On a fast 60 Hz fling through a long Home feed, those small
 * differences make image decode and placeholder work bursty instead of rhythmic.
 * Centralizing them keeps the same cached bitmap path while the list is moving.
 */
@Composable
fun VylloArtwork(
    thumbnailUrl: String,
    contentDescription: String?,
    cornerRadius: androidx.compose.ui.unit.Dp = VylloRadius.sm,
    requestSizePx: Int = 280,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp? = null,
    showLoading: Boolean = false
) {
    val context = LocalContext.current
    val imageRequest = remember(thumbnailUrl, requestSizePx) {
        ImageRequest.Builder(context)
            .data(thumbnailUrl)
            .size(requestSizePx, requestSizePx)
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    Box(
        modifier = modifier
            .then(if (size != null) Modifier.size(size) else Modifier)
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        if (showLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(VylloSize.iconLarge).align(Alignment.Center),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Eased now-playing tint so list rows do not flash during queue transitions.
 */
@Composable
fun artworkHighlightColor(isPlaying: Boolean): androidx.compose.ui.graphics.Color {
    val highlight by animateColorAsState(
        targetValue = if (isPlaying) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        animationSpec = tween(VylloMotion.medium),
        label = "row_highlight"
    )
    return highlight
}

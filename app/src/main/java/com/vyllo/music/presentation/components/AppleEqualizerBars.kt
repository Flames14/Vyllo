package com.vyllo.music.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 🎵 Apple Music Signature 3-Bar Equalizer Wave.
 * Renders 3 vertical bouncing rounded equalizer bars that animate harmonically
 * when music is playing and smoothly settle to a minimal resting line when paused.
 *
 * Drawn via Canvas with zero allocations per frame for maximum 60Hz/120Hz efficiency.
 */
@Composable
fun AppleEqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barColor: Color = Color.Unspecified,
    color: Color = MaterialTheme.colorScheme.primary,
    barWidth: Dp = 3.dp,
    barSpacing: Dp = 2.5.dp,
    maxBarHeight: Dp = 15.dp,
    minBarHeight: Dp = 3.5.dp
) {
    val resolvedColor = if (barColor != Color.Unspecified) barColor else color
    val infiniteTransition = rememberInfiniteTransition(label = "apple_eq_wave")

    val anim1 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_bar_1"
    )

    val anim2 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 390, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_bar_2"
    )

    val anim3 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 560, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_bar_3"
    )

    val bar1Fraction = if (isPlaying) anim1 else 0.25f
    val bar2Fraction = if (isPlaying) anim2 else 0.45f
    val bar3Fraction = if (isPlaying) anim3 else 0.25f

    Canvas(
        modifier = modifier.size(
            width = (barWidth * 3) + (barSpacing * 2),
            height = maxBarHeight
        )
    ) {
        val barWidthPx = barWidth.toPx()
        val spacingPx = barSpacing.toPx()
        val maxHeightPx = size.height
        val minHeightPx = minBarHeight.toPx()
        val cornerRadius = CornerRadius(barWidthPx / 2, barWidthPx / 2)

        val heights = listOf(
            minHeightPx + (maxHeightPx - minHeightPx) * bar1Fraction,
            minHeightPx + (maxHeightPx - minHeightPx) * bar2Fraction,
            minHeightPx + (maxHeightPx - minHeightPx) * bar3Fraction
        )

        for (i in 0..2) {
            val left = i * (barWidthPx + spacingPx)
            val barH = heights[i]
            val top = maxHeightPx - barH

            drawRoundRect(
                color = resolvedColor,
                topLeft = Offset(left, top),
                size = Size(barWidthPx, barH),
                cornerRadius = cornerRadius
            )
        }
    }
}

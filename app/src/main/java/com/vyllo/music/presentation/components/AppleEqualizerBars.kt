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

    // The animation states are deliberately NOT destructured with `by`.
    //
    // Reading an animation value in composition subscribes this composable's recompose
    // scope, which used to make every mounted equalizer (mini player, now-playing row)
    // recompose at panel rate (60-120x/s) and allocate a fresh Canvas lambda per frame.
    // The `.value` reads below happen inside the draw lambda instead, where they only
    // schedule a redraw — which is all a looping indicator needs.
    val bar1State = infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_bar_1"
    )

    val bar2State = infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 390, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_bar_2"
    )

    val bar3State = infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 560, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq_bar_3"
    )

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

        // Keep the three heights in locals rather than a list: `listOf` allocated a
        // backing array on every draw frame, which is the opposite of this class's
        // "zero allocations per frame" contract. The bars redraw continuously while
        // music plays (up to 120/s per visible equalizer).
        val bar1Fraction = if (isPlaying) bar1State.value else 0.25f
        val bar2Fraction = if (isPlaying) bar2State.value else 0.45f
        val bar3Fraction = if (isPlaying) bar3State.value else 0.25f
        val heightSpan = maxHeightPx - minHeightPx
        val height1 = minHeightPx + heightSpan * bar1Fraction
        val height2 = minHeightPx + heightSpan * bar2Fraction
        val height3 = minHeightPx + heightSpan * bar3Fraction

        for (i in 0..2) {
            val left = i * (barWidthPx + spacingPx)
            val barH = when (i) {
                0 -> height1
                1 -> height2
                else -> height3
            }
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

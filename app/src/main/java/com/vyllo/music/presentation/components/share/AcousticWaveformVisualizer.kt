package com.vyllo.music.presentation.components.share

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sin

/**
 * Canvas Component: Aesthetic Acoustic Soundwave Spectrum
 */
@Composable
fun AcousticWaveformVisualizer(
    modifier: Modifier = Modifier,
    barCount: Int = 32
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val maxHeight = size.height
        val totalSpacing = (barCount - 1) * 2f
        val barWidth = ((width - totalSpacing) / barCount).coerceAtLeast(1.5f)

        for (i in 0 until barCount) {
            val normalizedX = i.toFloat() / barCount.toFloat()
            val envelope = sin(normalizedX * Math.PI.toFloat()).pow(1.6f)
            val subHarmonic = 0.4f + 0.6f * sin(i * 1.4f).absoluteValue
            val barHeight = ((0.2f + 0.8f * envelope * subHarmonic) * maxHeight).coerceIn(3f, maxHeight)

            val x = i * (barWidth + 2f)
            val y = (maxHeight - barHeight) / 2f

            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f + 0.15f * envelope),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

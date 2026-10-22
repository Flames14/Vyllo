package com.vyllo.music.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vyllo.music.R
import com.vyllo.music.domain.model.EqualizerBandSetting
import com.vyllo.music.domain.model.EqualizerCurve
import kotlin.math.abs

/**
 * Response curve of the current band levels. Purely a read-out: editing stays
 * in the sliders below so dragging here can never fight them.
 */
@Composable
fun EqualizerCurveGraph(
    bands: List<EqualizerBandSetting>,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val curveDescription = stringResource(R.string.eq_curve_cd)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(vertical = 4.dp)
                .semantics { contentDescription = curveDescription }
        ) {
            val midY = size.height / 2f
            val levels = EqualizerCurve.levels(bands.map { it.level })
            val stepX = size.width / (levels.size - 1).coerceAtLeast(1)
            val amplitude = size.height * 0.42f

            // Center (0 dB) line plus 0 dB reference dashes.
            drawLine(
                color = grid,
                start = Offset(0f, midY),
                end = Offset(size.width, midY),
                strokeWidth = 1.5f
            )

            val points = levels.mapIndexed { index, level ->
                Offset(index * stepX, midY - level * amplitude)
            }
            if (points.size < 2) return@Canvas

            val line = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val previous = points[i - 1]
                    val current = points[i]
                    val controlX = (previous.x + current.x) / 2f
                    cubicTo(controlX, previous.y, controlX, current.y, current.x, current.y)
                }
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(points.last().x, midY)
                lineTo(points.first().x, midY)
                close()
            }

            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                    startY = midY - amplitude,
                    endY = midY + amplitude
                )
            )
            drawPath(path = line, color = accent, style = Stroke(width = 3f))

            // Band handles: filled dot when the band is away from 0 dB.
            bands.forEachIndexed { index, band ->
                val x = if (bands.size == 1) size.width / 2f else size.width * index / (bands.size - 1)
                val y = midY - EqualizerCurve.normalize(band.level) * amplitude
                drawCircle(color = accent, radius = if (abs(band.level) > 0) 5f else 3f, center = Offset(x, y))
            }
        }
    }
}

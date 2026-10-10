package com.vyllo.music.ui.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Ambient Background for Home Screen.
 *
 * The aura is static per screen size and theme, so it is rasterized ONCE to an
 * offscreen bitmap and blitted each frame — instead of evaluating fullscreen
 * radial-gradient shaders per pixel per frame.
 *
 * Why: the frame trace showed RenderThread stuck 209ms in `Vulkan finish frame`
 * while the main thread idled in `postAndWait` — GPU-bound, with new texture
 * uploads colliding with gradient shading. A bitmap blit is an order of
 * magnitude cheaper than 2 fullscreen gradient evaluations and visually
 * identical for soft radial blobs.
 *
 * The bitmap is half-resolution because smooth gradients upscale without visible
 * loss (~3.5MB vs ~14MB at 1280x2772). It is re-rendered only when size or theme
 * changes (both are inputs of the drawWithCache block).
 */
@Composable
fun HomeAmbientDarkAura(
    modifier: Modifier = Modifier
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val isDark = com.vyllo.music.presentation.theme.ThemeManager.isDarkColor(backgroundColor)

    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val fullW = size.width
                val fullH = size.height
                val w = (fullW / 2).toInt().coerceAtLeast(1)
                val h = (fullH / 2).toInt().coerceAtLeast(1)

                val bmp = ImageBitmap(w, h)
                CanvasDrawScope().draw(
                    density = this,
                    layoutDirection = layoutDirection,
                    canvas = Canvas(bmp),
                    size = Size(w.toFloat(), h.toFloat())
                ) {
                    if (isDark) {
                        val aura1 = Brush.radialGradient(
                            colors = listOf(Color(0xFF4A154B).copy(alpha = 0.18f), Color.Transparent),
                            center = Offset(w * 0.85f, h * 0.12f),
                            radius = w * 0.85f
                        )
                        val aura2 = Brush.radialGradient(
                            colors = listOf(Color(0xFF004D40).copy(alpha = 0.14f), Color.Transparent),
                            center = Offset(w * 0.12f, h * 0.38f),
                            radius = w * 0.75f
                        )
                        drawRect(brush = aura1)
                        drawRect(brush = aura2)
                    } else {
                        val auraLight = Brush.radialGradient(
                            colors = listOf(Color(0xFFFF0000).copy(alpha = 0.025f), Color.Transparent),
                            center = Offset(w * 0.88f, h * 0.10f),
                            radius = w * 0.8f
                        )
                        drawRect(brush = auraLight)
                    }
                }
                onDrawBehind {
                    // No opaque base fill here: the root Box in
                    // VylloNavigation already paints this exact background
                    // colour full-screen underneath.
                    drawImage(
                        image = bmp,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(w, h),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(fullW.toInt(), fullH.toInt())
                    )
                }
            }
    )
}

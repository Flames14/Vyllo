package com.vyllo.music.ui.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Ambient Background for Home Screen
 * Uses hardware-cached drawWithCache to render static gradients without per-frame allocations,
 * delivering a buttery smooth 120Hz scrolling experience.
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
                val width = size.width
                val height = size.height

                if (isDark) {
                    val aura1 = Brush.radialGradient(
                        colors = listOf(Color(0xFF4A154B).copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(width * 0.85f, height * 0.12f),
                        radius = width * 0.85f
                    )
                    val aura2 = Brush.radialGradient(
                        colors = listOf(Color(0xFF004D40).copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(width * 0.12f, height * 0.38f),
                        radius = width * 0.75f
                    )
                    onDrawBehind {
                        drawRect(backgroundColor)
                        drawRect(brush = aura1)
                        drawRect(brush = aura2)
                    }
                } else {
                    val auraLight = Brush.radialGradient(
                        colors = listOf(Color(0xFFFF0000).copy(alpha = 0.025f), Color.Transparent),
                        center = Offset(width * 0.88f, height * 0.10f),
                        radius = width * 0.8f
                    )
                    onDrawBehind {
                        drawRect(backgroundColor)
                        drawRect(brush = auraLight)
                    }
                }
            }
    )
}

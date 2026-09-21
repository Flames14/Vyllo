package com.vyllo.music.ui.components.background

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MeshGradientBackground(modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT >= 33) {
        AuroraShader(modifier)
    } else {
        FallbackMeshGradient(modifier)
    }
}

@Composable
fun FallbackMeshGradient(modifier: Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh")
    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse), label = "b1"
    )
    val offset2 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Reverse), label = "b2"
    )

    val isDark = com.vyllo.music.presentation.theme.ThemeManager.isDarkColor(MaterialTheme.colorScheme.background)
    val bg = MaterialTheme.colorScheme.background
    val blob1 = if (isDark) Color(0xFF202020) else Color(0xFFE0E0E0)
    val blob2 = if (isDark) Color(0xFF303030) else Color(0xFFD0D0D0)
    val blob3 = if (isDark) Color(0xFF151515) else Color(0xFFF0F0F0)

    Box(modifier = modifier.background(bg)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-100).dp + (100.dp * offset1), y = (-100).dp + (50.dp * offset2))
                .size(400.dp)
                .alpha(0.4f)
                .background(Brush.radialGradient(listOf(blob1, Color.Transparent)))
                .blur(40.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (100).dp - (100.dp * offset2), y = (100).dp - (50.dp * offset1))
                .size(500.dp)
                .alpha(0.3f)
                .background(Brush.radialGradient(listOf(blob2, Color.Transparent)))
                .blur(50.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (200.dp * (offset1 - 0.5f)), y = (200.dp * (offset2 - 0.5f)))
                .size(300.dp)
                .alpha(0.2f)
                .background(Brush.radialGradient(listOf(blob3, Color.Transparent)))
                .blur(45.dp)
        )
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.onBackground.copy(0.03f)))
    }
}

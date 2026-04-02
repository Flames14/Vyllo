package com.vyllo.music.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.media3.session.MediaController

import androidx.compose.animation.*
import androidx.compose.animation.core.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight

@Composable
fun VideoHintBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hint_pulse")
    val alphaValue by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Surface(
        modifier = modifier
            .padding(bottom = 12.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .alpha(alphaValue)
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
        contentColor = Color.White,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Rounded.SmartDisplay, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Click here to play video",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
    }
}

@Composable
fun VideoSurface(
    controller: MediaController?,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false // We use our own custom UI
                player = controller
                setBackgroundColor(android.graphics.Color.BLACK)
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { view ->
            view.player = controller
        },
        modifier = modifier
    )
}

@Composable
fun AutoplayRow(isChecked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("Autoplay", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text("Keep playing similar songs", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.5f))
        }
        Switch(checked = isChecked, onCheckedChange = onToggle)
    }
}

@Composable
fun ImmersiveBackground(imageUrl: String, content: @Composable BoxScope.() -> Unit) {
    val bg = MaterialTheme.colorScheme.background
    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        if (imageUrl.isNotEmpty()) {
             AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.25f).blur(40.dp)
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg.copy(alpha = 0.4f), bg.copy(alpha = 0.8f), bg))))
        content()
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

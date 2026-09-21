package com.vyllo.music.presentation.components.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.vyllo.music.presentation.components.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSeekBar(
    currentPosition: Long,
    duration: Long,
    expandProgress: Float,
    controller: MediaController?,
    onPositionChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    // 5. Apple-Style Expandable Scrubber & Time Display
    var isScrubbing by remember { mutableStateOf(false) }
    val trackHeight by animateDpAsState(
        targetValue = if (isScrubbing) 7.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scrubber_track_height"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha((1f - (expandProgress * 2.5f)).coerceIn(0f, 1f))
    ) {
        Slider(
            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
            onValueChange = { newPercent ->
                isScrubbing = true
                val newPos = (newPercent * duration).toLong()
                controller?.seekTo(newPos)
                onPositionChange(newPos)
            },
            onValueChangeFinished = {
                isScrubbing = false
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.22f)
            ),
            thumb = {
                val thumbScale by animateFloatAsState(
                    targetValue = if (isScrubbing) 1.25f else 1.0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "scrubber_thumb_scale"
                )
                Box(
                    modifier = Modifier
                        .size(if (isScrubbing) 16.dp else 12.dp)
                        .graphicsLayer {
                            scaleX = thumbScale
                            scaleY = thumbScale
                        }
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(trackHeight),
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Playback progress: ${formatTime(currentPosition)} of ${formatTime(duration)}"
                }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatTime(currentPosition),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = Color.White.copy(0.6f)
            )
            Text(
                formatTime(duration),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = Color.White.copy(0.6f)
            )
        }
    }
}

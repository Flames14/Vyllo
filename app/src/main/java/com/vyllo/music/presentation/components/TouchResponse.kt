package com.vyllo.music.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Modifier.bounceClick(
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val indication = LocalIndication.current
    val scope = rememberCoroutineScope()
    var justClicked by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.975f
            justClicked -> 1.01f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 900f),
        label = "scale"
    )

    val overlayAlpha by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.10f
            justClicked -> 0.06f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 90),
        label = "overlayAlpha"
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            if (overlayAlpha > 0f) {
                drawRect(Color.White.copy(alpha = overlayAlpha))
            }
        }
        .clickable(
            interactionSource = interactionSource,
            indication = indication,
            onClick = {
                justClicked = true
                scope.launch {
                    delay(120)
                    justClicked = false
                }
                onClick()
            }
        )
}

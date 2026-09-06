package com.vyllo.music.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * YouTube Music native bounded ripple click modifier.
 * Provides instant touch feedback with standard platform ripple,
 * zero layout scale distortion, and zero coroutine/animation overhead during scroll flings.
 */
@Composable
fun Modifier.ytmClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current

    return this.clickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onClick = onClick
    )
}

/**
 * Alias for ytmClickable to maintain compatibility with existing callers
 * while ensuring zero frame-drop scroll performance.
 */
@Composable
fun Modifier.bounceClick(
    onClick: () -> Unit
): Modifier = ytmClickable(onClick = onClick)


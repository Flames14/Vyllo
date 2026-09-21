package com.vyllo.music.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 🍎 Apple iOS-Grade Tactile Press Clickable Modifier.
 * Provides an authentic iPhone-style physical depression (0.97x) with high-stiffness
 * spring physics and subtle Taptic micro-feedback on tap.
 *
 * Performance-engineered:
 * - Scale is applied purely at the RenderNode / GPU layer via `graphicsLayer`,
 *   guaranteeing zero layout re-measurement and zero recomposition overhead during 60Hz/120Hz flings.
 * - When a vertical scroll begins, `PressInteraction.Cancel` resets the scale immediately
 *   so gestures never stick or stutter list scrolling.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Modifier.iosPressClickable(
    enabled: Boolean = true,
    pressScale: Float = 0.97f,
    enableHaptics: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressScale else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "ios_press_scale"
    )

    val scaledModifier = this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }

    return if (onLongClick != null) {
        scaledModifier.combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onLongClick = {
                if (enableHaptics) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                onLongClick()
            },
            onClick = {
                if (enableHaptics) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onClick()
            }
        )
    } else {
        scaledModifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = {
                if (enableHaptics) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onClick()
            }
        )
    }
}

/**
 * YouTube Music & Vyllo click modifier with Apple-grade tactile physics and optional long-press.
 */
@Composable
fun Modifier.ytmClickable(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = iosPressClickable(enabled = enabled, onLongClick = onLongClick, onClick = onClick)

/**
 * Alias for bounceClick maintaining backward compatibility with existing callers.
 */
@Composable
fun Modifier.bounceClick(
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = iosPressClickable(onLongClick = onLongClick, onClick = onClick)

/**
 * Lightweight scale-only press feedback (no haptics) for high-frequency
 * surfaces where the iOS Taptic would be excessive.
 */
@Composable
fun Modifier.pressScaleClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = tween(durationMillis = com.vyllo.music.presentation.theme.VylloMotion.instant),
        label = "press_scale"
    )

    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Lightweight fade-only press feedback for compact list rows.
 *
 * Rows are laid out in large lazy lists, so instead of a transform we dim the row
 * slightly while pressed. This keeps the highlight stable and avoids text shimmering
 * under sub-pixel scaling.
 */
@Composable
fun Modifier.pressFadeClickable(
    enabled: Boolean = true,
    pressedAlpha: Float = 0.65f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedAlpha else 1f,
        animationSpec = tween(durationMillis = com.vyllo.music.presentation.theme.VylloMotion.instant),
        label = "press_alpha"
    )

    return this
        .graphicsLayer { this.alpha = alpha }
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick
        )
}


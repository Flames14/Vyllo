package com.vyllo.music.service.audio

import androidx.media3.common.Player
import androidx.media3.session.CommandButton

/**
 * Pure mapping from player state to the media-notification button icons.
 *
 * Extracted from the (Android-bound) session wiring so the state transitions
 * stay unit-testable. Uses media3's built-in icon resource ids
 * ([CommandButton.ICON_*]), so no app drawables are required.
 */
object NotificationButtonState {

    /** Next repeat mode in the off -> all -> one -> off cycle. */
    fun nextRepeatMode(current: Int): Int = when (current) {
        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
        else -> Player.REPEAT_MODE_OFF
    }

    fun repeatIcon(mode: Int): Int = when (mode) {
        Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
        Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
        else -> CommandButton.ICON_REPEAT_OFF
    }

    fun shuffleIcon(enabled: Boolean): Int =
        if (enabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF

    fun likeIcon(liked: Boolean): Int =
        if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
}

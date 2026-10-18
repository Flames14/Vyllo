package com.vyllo.music.service.audio

import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationButtonStateTest {

    @Test
    fun `repeat mode cycles off then all then one then off`() {
        assertEquals(Player.REPEAT_MODE_ALL, NotificationButtonState.nextRepeatMode(Player.REPEAT_MODE_OFF))
        assertEquals(Player.REPEAT_MODE_ONE, NotificationButtonState.nextRepeatMode(Player.REPEAT_MODE_ALL))
        assertEquals(Player.REPEAT_MODE_OFF, NotificationButtonState.nextRepeatMode(Player.REPEAT_MODE_ONE))
    }

    @Test
    fun `unknown repeat mode resets to off`() {
        assertEquals(Player.REPEAT_MODE_OFF, NotificationButtonState.nextRepeatMode(Int.MAX_VALUE))
    }

    @Test
    fun `repeat icon reflects each mode`() {
        assertEquals(CommandButton.ICON_REPEAT_OFF, NotificationButtonState.repeatIcon(Player.REPEAT_MODE_OFF))
        assertEquals(CommandButton.ICON_REPEAT_ALL, NotificationButtonState.repeatIcon(Player.REPEAT_MODE_ALL))
        assertEquals(CommandButton.ICON_REPEAT_ONE, NotificationButtonState.repeatIcon(Player.REPEAT_MODE_ONE))
    }

    @Test
    fun `shuffle icon reflects enabled flag`() {
        assertEquals(CommandButton.ICON_SHUFFLE_ON, NotificationButtonState.shuffleIcon(true))
        assertEquals(CommandButton.ICON_SHUFFLE_OFF, NotificationButtonState.shuffleIcon(false))
    }

    @Test
    fun `like icon reflects liked flag`() {
        assertEquals(CommandButton.ICON_HEART_FILLED, NotificationButtonState.likeIcon(true))
        assertEquals(CommandButton.ICON_HEART_UNFILLED, NotificationButtonState.likeIcon(false))
    }
}

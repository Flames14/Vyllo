package com.vyllo.music.service.audio

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.vyllo.music.service.PlaybackQueueOrchestrator
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/**
 * Regression: notification / headset skip commands must advance the app-owned
 * queue exactly once and must not fall through to Media3's default dispatch
 * (double-advance). Also covers delegation of the custom notification commands.
 */
class MediaSessionCallbackTest {

    private val player: ExoPlayer = mock()
    private val serviceScope = TestScope()
    private val orchestrator: PlaybackQueueOrchestrator = mock()
    private val commands: PlaybackCommandsController = mock()
    private val resumption: PlaybackResumption = mock()
    private val session: MediaSession = mock()
    private val controllerInfo: MediaSession.ControllerInfo = mock()
    private val callback = MediaSessionCallback({ player }, serviceScope, orchestrator, commands, resumption)

    @Test
    fun `next skips route through queue orchestrator for both command variants`() {
        val legacy = callback.onPlayerCommandRequest(session, controllerInfo, Player.COMMAND_SEEK_TO_NEXT)
        val mediaItem = callback.onPlayerCommandRequest(session, controllerInfo, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)

        assertEquals(SessionResult.RESULT_INFO_SKIPPED, legacy)
        assertEquals(SessionResult.RESULT_INFO_SKIPPED, mediaItem)
        verify(orchestrator, times(2)).playNextTrack(serviceScope, player)
    }

    @Test
    fun `previous skips route through queue orchestrator for both command variants`() {
        val legacy = callback.onPlayerCommandRequest(session, controllerInfo, Player.COMMAND_SEEK_TO_PREVIOUS)
        val mediaItem = callback.onPlayerCommandRequest(session, controllerInfo, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)

        assertEquals(SessionResult.RESULT_INFO_SKIPPED, legacy)
        assertEquals(SessionResult.RESULT_INFO_SKIPPED, mediaItem)
        verify(orchestrator, times(2)).playPreviousTrack(serviceScope, player)
    }

    @Test
    fun `unrelated commands fall through to default handling`() {
        val result = callback.onPlayerCommandRequest(session, controllerInfo, Player.COMMAND_PLAY_PAUSE)

        assertEquals(SessionResult.RESULT_SUCCESS, result)
        verify(orchestrator, never()).playNextTrack(anyOrNull(), anyOrNull())
        verify(orchestrator, never()).playPreviousTrack(anyOrNull(), anyOrNull())
    }
}

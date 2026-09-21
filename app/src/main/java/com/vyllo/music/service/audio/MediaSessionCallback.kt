package com.vyllo.music.service.audio

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.vyllo.music.service.PlaybackQueueOrchestrator
import kotlinx.coroutines.CoroutineScope

/**
 * MediaSession.Callback extracted from MusicService — no behavior change.
 */
class MediaSessionCallback(
    private val playerProvider: () -> ExoPlayer?,
    private val serviceScope: CoroutineScope,
    private val playbackQueueOrchestrator: PlaybackQueueOrchestrator
) : MediaSession.Callback {
    override fun onPlayerCommandRequest(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        playerCommand: Int
    ): Int {
        if (playerCommand == Player.COMMAND_SEEK_TO_NEXT) {
            playbackQueueOrchestrator.playNextTrack(serviceScope, playerProvider())
            return SessionResult.RESULT_SUCCESS
        } else if (playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS) {
            playbackQueueOrchestrator.playPreviousTrack(serviceScope, playerProvider())
            return SessionResult.RESULT_SUCCESS
        }
        return super.onPlayerCommandRequest(session, controllerInfo, playerCommand)
    }
}

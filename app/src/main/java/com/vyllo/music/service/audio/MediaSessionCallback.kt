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
        // Handle next/prev ourselves (queue-aware), then return a non-success
        // code so Media3 does not ALSO dispatch the raw command — otherwise the
        // queue advances twice per skip.
        when (playerCommand) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                playbackQueueOrchestrator.playNextTrack(serviceScope, playerProvider())
                return SessionResult.RESULT_INFO_SKIPPED
            }
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                playbackQueueOrchestrator.playPreviousTrack(serviceScope, playerProvider())
                return SessionResult.RESULT_INFO_SKIPPED
            }
        }
        return super.onPlayerCommandRequest(session, controllerInfo, playerCommand)
    }
}

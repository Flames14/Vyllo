package com.vyllo.music.service.audio

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.vyllo.music.service.PlaybackQueueOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * MediaSession.Callback extracted from MusicService — no behavior change beyond
 * delegating the media-button resumption and custom notification commands to
 * their dedicated collaborators.
 */
@UnstableApi
class MediaSessionCallback(
    private val playerProvider: () -> ExoPlayer?,
    private val serviceScope: CoroutineScope,
    private val playbackQueueOrchestrator: PlaybackQueueOrchestrator,
    private val commands: PlaybackCommandsController,
    private val resumption: PlaybackResumption,
) : MediaSession.Callback {

    override fun onConnect(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        // Advertise the custom notification commands so the like/shuffle/repeat
        // buttons are visible to every controller (notification, BT, car).
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
            .buildUpon()
            .addSessionCommands(PlaybackCommandsController.COMMANDS)
            .build()
        return MediaSession.ConnectionResult.accept(
            sessionCommands,
            MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
        )
    }

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

    override fun onCustomCommand(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        customCommand: androidx.media3.session.SessionCommand,
        args: android.os.Bundle
    ): ListenableFuture<SessionResult> {
        if (commands.handle(customCommand.customAction)) {
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        return super.onCustomCommand(session, controllerInfo, customCommand, args)
    }

    override fun onPlaybackResumption(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        serviceScope.launch {
            try {
                future.set(resumption.restore())
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }
}

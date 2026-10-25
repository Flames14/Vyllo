package com.vyllo.music.service.audio

import androidx.core.net.toUri

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.vyllo.music.domain.manager.PlaybackQueueManager
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.repository.IMusicRepository
import com.vyllo.music.core.security.InputSanitizer
import com.vyllo.music.service.PlaybackQueueOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * MediaSession.Callback extracted from MusicService â€” no behavior change beyond
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
    private val catalog: AndroidAutoCatalog,
    private val repository: IMusicRepository,
    private val playbackQueueManager: PlaybackQueueManager,
    private val appPackageName: String
) : MediaLibraryService.MediaLibrarySession.Callback {

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
        LibraryResult.ofItem(catalog.root(), params)
    )

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = asyncItemListResult {
        val children = catalog.children(parentId)
        val safePage = page.coerceAtLeast(0)
        val safeSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        val start = (safePage.toLong() * safeSize).coerceAtMost(children.size.toLong()).toInt()
        val end = (start + safeSize).coerceAtMost(children.size)
        LibraryResult.ofItemList(children.subList(start, end), params)
    }

    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> = asyncItemResult {
        val item = catalog.item(mediaId)
        if (item == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        else LibraryResult.ofItem(item, null)
    }

    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> = Futures.immediateFuture(LibraryResult.ofVoid(params))

    override fun onGetSearchResult(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val safeQuery = InputSanitizer.sanitizeSearchQuery(query)
        if (safeQuery.isBlank()) {
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
        }
        return asyncItemListResult {
            val results = repository.searchMusic(safeQuery, maintainSession = false)
            val items = catalog.searchResultItems(results)
            val safePage = page.coerceAtLeast(0)
            val safeSize = pageSize.coerceIn(1, MAX_PAGE_SIZE)
            val start = (safePage.toLong() * safeSize).coerceAtMost(items.size.toLong()).toInt()
            val end = (start + safeSize).coerceAtMost(items.size)
            LibraryResult.ofItemList(items.subList(start, end), params)
        }
    }

    override fun onAddMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> = asyncResult {
        val tracks = mutableListOf<MusicItem>()
        val resolved = mediaItems.map { item ->
            val (mediaItem, track) = resolveBrowseItem(item)
            track?.let(tracks::add)
            mediaItem
        }
        if (controller.packageName != appPackageName && tracks.isNotEmpty()) {
            playbackQueueManager.replaceQueue(tracks, startIndex = 0)
        }
        resolved
    }

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
        // code so Media3 does not ALSO dispatch the raw command â€” otherwise the
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

    private suspend fun resolveBrowseItem(item: MediaItem): Pair<MediaItem, MusicItem?> {
        val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri
            ?: return item to null
        val uriString = uri.toString()
        if (!uriString.isYouTubeWatchUrl()) return item to null

        val logicalUrl = item.mediaId.takeIf { it.isNotBlank() } ?: uriString
        val track = repository.resolveTrack(logicalUrl)
            ?: MusicItem(
                id = logicalUrl,
                title = item.mediaMetadata.title?.toString().orEmpty().ifBlank { logicalUrl },
                url = logicalUrl,
                uploader = item.mediaMetadata.artist?.toString().orEmpty(),
                thumbnailUrl = item.mediaMetadata.artworkUri?.toString().orEmpty()
            )
        val streamUrl = repository.getStreamUrl(logicalUrl, force = false, isVideo = false)
            ?: throw IllegalStateException("Unable to resolve media for Android Auto playback")
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.uploader)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply {
                track.thumbnailUrl.takeIf(String::isNotBlank)?.let { setArtworkUri(it.toUri()) }
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(logicalUrl)
            .setUri(streamUrl)
            .setMediaMetadata(metadata)
            .build() to track
    }

    private fun <T> asyncResult(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        serviceScope.launch {
            try {
                future.set(block())
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                future.cancel(true)
                throw cancelled
            } catch (error: Exception) {
                future.setException(error)
            }
        }
        return future
    }

    private fun asyncItemListResult(
        block: suspend () -> LibraryResult<ImmutableList<MediaItem>>
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        asyncResult {
            try {
                block()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                LibraryResult.ofError(SessionError.ERROR_IO)
            }
        }

    private fun asyncItemResult(
        block: suspend () -> LibraryResult<MediaItem>
    ): ListenableFuture<LibraryResult<MediaItem>> =
        asyncResult {
            try {
                block()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                LibraryResult.ofError(SessionError.ERROR_IO)
            }
        }

    private fun String.isYouTubeWatchUrl(): Boolean {
        val host = runCatching { this.toUri().host?.lowercase() }.getOrNull().orEmpty()
        return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
    }

    private companion object {
        const val MAX_PAGE_SIZE = 200
    }
}

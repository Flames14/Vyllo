package com.vyllo.music.service.audio

import androidx.core.net.toUri

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.domain.manager.PlaybackQueueManager
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.repository.IMusicRepository

/**
 * Rebuilds a playable session after process death — the "play" button on a
 * headset / Bluetooth device / car unit / assistant when the app's process is
 * gone. Without this, the media button only starts an empty session.
 *
 * Only the start track is handed to the player (with its stream URL resolved).
 * The rest of the queue is left in [PlaybackQueueManager] for
 * `PlaybackQueueOrchestrator`'s lookahead to resolve and enqueue, exactly as it
 * does during a normal session. Returning the whole queue here would be wrong:
 * `playNextTrack`'s fast path seeks to the previously-enqueued next item and
 * assumes it already holds a real URL.
 */
class PlaybackResumption(
    private val preferenceManager: PreferenceManager,
    private val playbackQueueManager: PlaybackQueueManager,
    private val repository: IMusicRepository,
) {

    suspend fun restore(): MediaSession.MediaItemsWithStartPosition {
        val snapshot = playbackQueueManager.getQueueSnapshot()
        val (items, startIndex) = if (snapshot.isNotEmpty()) {
            snapshot to playbackQueueManager.currentIndex
        } else {
            preferenceManager.restoreQueue() ?: return empty()
        }

        if (items.isEmpty()) return empty()

        val start = items[startIndex.coerceIn(0, items.size - 1)]
        val startUri = try {
            repository.getStreamUrl(start.url)
        } catch (e: Exception) {
            SecureLogger.w("MusicService", "Resumption URL resolve failed: ${e.message}")
            null
        }

        // Resume where the last session left off, but only for the same track:
        // a saved position from another song must never be applied here.
        val startPositionMs = preferenceManager.savedPlaybackPosition(start.url) ?: C.TIME_UNSET

        return MediaSession.MediaItemsWithStartPosition(
            listOf(start.toMediaItem(startUri)),
            0,
            startPositionMs
        )
    }

    private fun empty(): MediaSession.MediaItemsWithStartPosition =
        MediaSession.MediaItemsWithStartPosition(emptyList(), 0, C.TIME_UNSET)

    private fun MusicItem.toMediaItem(streamUrl: String?): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(uploader)
            .apply {
                if (thumbnailUrl.isNotBlank()) {
                    runCatching { setArtworkUri(thumbnailUrl.toUri()) }
                }
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(url)
            .apply { streamUrl?.let { setUri(it) } }
            .setMediaMetadata(metadata)
            .build()
    }
}

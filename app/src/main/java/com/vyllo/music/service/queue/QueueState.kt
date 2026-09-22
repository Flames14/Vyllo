package com.vyllo.music.service.queue

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.repository.IMusicRepository
import com.vyllo.music.data.manager.PlaybackQueueManager
import com.vyllo.music.data.manager.WakeLockManager
import com.vyllo.music.domain.manager.StreamUrlCache
import com.vyllo.music.domain.model.MusicItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * Holds queue transition state + cache-first URL resolution helpers.
 * Extracted from PlaybackQueueOrchestrator — no behavior change.
 */
class QueueState(
    private val repository: IMusicRepository,
    private val playbackQueueManager: PlaybackQueueManager,
    private val wakeLockManager: WakeLockManager,
    private val streamUrlCache: StreamUrlCache
) {
    /** Monotonic generation: a stale in-flight transition aborts instead of stomping a newer one. */
    val transitionSeq = AtomicLong(0L)

    /** Throttle for background autoplay discovery so it cannot churn network while locked. */
    @Volatile
    var lastAutoplayWarmUpMs = 0L

    suspend fun resolveStreamUrl(trackUrl: String, force: Boolean = false): String? =
        withContext(Dispatchers.IO) {
            if (!force) {
                streamUrlCache.get(trackUrl, false)?.let { return@withContext it }
            }
            try {
                val resolved = repository.getStreamUrl(trackUrl, force = force)
                if (resolved != null) {
                    streamUrlCache.put(trackUrl, false, resolved)
                } else {
                    streamUrlCache.remove(trackUrl, false)
                }
                resolved
            } catch (e: Exception) {
                SecureLogger.e("MusicService", "Stream resolve failed", e)
                null
            }
        }

    fun MusicItem.toMediaItem(streamUrl: String): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(uploader)
            .setArtworkUri(android.net.Uri.parse(thumbnailUrl))
            .build()
        return MediaItem.Builder()
            .setMediaId(url)
            .setUri(streamUrl)
            .setMediaMetadata(metadata)
            .build()
    }

    fun isMediaIdEnqueued(player: ExoPlayer, mediaId: String): Boolean {
        if (mediaId.isBlank()) return true
        // Fail-safe: if the player is gone (service torn down mid-transition),
        // report "already handled" so callers back off instead of crashing.
        try {
            // Never enqueue a duplicate of the item that is already playing/buffering.
            if (player.currentMediaItem?.mediaId == mediaId) return true
            for (i in 0 until player.mediaItemCount) {
                if (player.getMediaItemAt(i).mediaId == mediaId) return true
            }
        } catch (e: Exception) {
            SecureLogger.w("MusicService", "Player gone during enqueue check: ${e.message}")
            return true
        }
        return false
    }

    suspend fun warmAutoplayDiscovery() {
        val now = System.currentTimeMillis()
        if (now - lastAutoplayWarmUpMs < 60_000L) return
        lastAutoplayWarmUpMs = now
        val queue = playbackQueueManager.getQueueSnapshot()
        val currentUrl = queue.getOrNull(playbackQueueManager.currentIndex)?.url ?: return
        wakeLockManager.withLocks {
            try {
                var related = repository.getMoreRelatedSongs(currentUrl)
                if (related.isEmpty()) {
                    related = repository.getRelatedSongs(currentUrl)
                }
                playbackQueueManager.appendDistinct(related)
            } catch (e: Exception) {
                SecureLogger.d("MusicService", "Autoplay warm-up ignored: " + e.message)
            }
        }
    }
}

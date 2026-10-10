package com.vyllo.music.domain.usecase

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.manager.PlaybackQueueManager
import com.vyllo.music.domain.manager.PlaybackManager
import com.vyllo.music.domain.manager.StreamUrlCache
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.PlayResult
import javax.inject.Inject

/**
 * Resolves a stream URL and starts playback through the PlaybackManager.
 * Encapsulates the play flow that was previously scattered across MainActivity.
 */
class PlayMusicUseCase @Inject constructor(
    private val getStreamUrlUseCase: GetStreamUrlUseCase,
    private val playbackManager: PlaybackManager,
    private val playbackQueueManager: PlaybackQueueManager,
    private val streamUrlCache: StreamUrlCache
) {

    private companion object {
        private const val TAG = "PlayMusicUseCase"
        private const val LOOKAHEAD_COUNT = 2
    }

    /**
     * Resolves the stream URL and starts playback.
     * @return PlayResult indicating success or failure with error message.
     */
    suspend fun execute(item: MusicItem, isVideo: Boolean = false, keepQueue: Boolean = false): PlayResult {
        // Ensure the MediaController is connected before mutating playback state.
        try {
            playbackManager.awaitConnection()
        } catch (e: Exception) {
            SecureLogger.w(TAG, "Controller not ready: ${e.message}")
            return PlayResult.Failure("Playback service unavailable")
        }

        // NOTE: playbackManager.stop() used to run here, before the resolve. That
        // killed the outgoing track and then left dead air for the whole multi-second
        // network resolve, which is what made every tap feel broken. ExoPlayer
        // crossfades out of the old item when the new one actually starts.

        SecureLogger.d(TAG) { "Playing: ${item.title}, isVideo=$isVideo, keepQueue=$keepQueue" }

        // Only replace queue if not explicitly instructed to keep existing queue
        if (!keepQueue) {
            playbackQueueManager.replaceQueue(listOf(item), 0)
        } else {
            val idx = playbackQueueManager.indexOf(item)
            if (idx >= 0) {
                playbackQueueManager.setCurrentIndexSafe(idx)
            } else {
                playbackQueueManager.addItem(item)
                playbackQueueManager.setCurrentIndexSafe(playbackQueueManager.size - 1)
            }
        }

        // The repository/datasource already has internal retries, so we remove the second layer of delay here
        val streamUrl = resolveStreamUrl(item, isVideo = isVideo)
            ?: return PlayResult.Failure("Unable to resolve stream URL")

        val result = playbackManager.playMusic(
            item = item,
            streamUrl = streamUrl,
            isVideo = isVideo,
            lookaheadItems = cachedLookahead(isVideo)
        )

        return if (result.isSuccess) {
            PlayResult.Success
        } else {
            val message = result.exceptionOrNull()?.message ?: "Unknown playback error"
            // The extracted URL may have been rejected server-side: drop it so
            // the next attempt re-extracts instead of serving the same dead link.
            streamUrlCache.remove(item.url, isVideo)
            SecureLogger.w(TAG, "Playback failed: $message")
            PlayResult.Failure(message)
        }
    }

    /**
     * Upcoming tracks whose URLs are *already* resolved.
     *
     * Deliberately cache-only: it must never add network latency to the tap. When a
     * track has been played recently, or the service already prefetched it, this
     * hands ExoPlayer a real playlist immediately and the handoff needs no network
     * at all. Anything unresolved is left to the service's lookahead, which resolves
     * it in the background.
     */
    private fun cachedLookahead(isVideo: Boolean): List<Pair<MusicItem, String>> {
        if (isVideo) return emptyList()
        return playbackQueueManager.getUpcomingSnapshot()
            .take(LOOKAHEAD_COUNT)
            .mapNotNull { next -> streamUrlCache.get(next.url, false)?.let { next to it } }
    }

    /**
     * Resolves the play URL, consulting the session cache first.
     *
     * Queue playback already populates [streamUrlCache], so tapping a track the
     * service has already prepared — or replaying one — skips the extractor
     * (and the cold poToken path) entirely. Only network URLs are cached: a
     * locally downloaded file is cheap to look up and must keep tracking the
     * download, not a path snapshot.
     */
    private suspend fun resolveStreamUrl(item: MusicItem, isVideo: Boolean): String? {
        streamUrlCache.get(item.url, isVideo)?.let { cached ->
            SecureLogger.d(TAG) { "Stream URL cache hit: ${item.url}" }
            return cached
        }

        val resolved = getStreamUrlUseCase(item.url, isVideo = isVideo) ?: return null
        if (resolved.startsWith("https://") || resolved.startsWith("http://")) {
            streamUrlCache.put(item.url, isVideo, resolved)
        }
        return resolved
    }
}

package com.vyllo.music.domain.manager

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.lastfm.LastFmApi
import com.vyllo.music.data.lastfm.LastFmApiException
import com.vyllo.music.data.lastfm.LastFmStore
import com.vyllo.music.domain.model.LastFmScrobbleRules
import com.vyllo.music.domain.model.LastFmTrack
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.PendingScrobble
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends "now playing" updates and scrobbles to Last.fm.
 *
 * The manager owns the playback bookkeeping so the service only reports
 * transitions and positions. Scrobbles that fail because of connectivity are
 * queued on disk and replayed on the next launch.
 */
@Singleton
class LastFmScrobbleManager @Inject constructor(
    private val api: LastFmApi,
    private val store: LastFmStore
) {
    private data class ActiveTrack(
        val track: LastFmTrack,
        val durationMs: Long,
        val startedAtSecs: Long
    )

    private val lock = Any()
    private val flushMutex = Mutex()
    private var scope: CoroutineScope? = null
    private var active: ActiveTrack? = null
    private var lastPositionMs: Long = 0L
    private var nowPlayingStartedAtSecs: Long? = null

    fun start(scope: CoroutineScope) {
        this.scope = scope
        scope.launch { flushPending() }
    }

    /** Called on every player media-item transition. */
    fun onTrackStarted(item: MusicItem?, durationMs: Long, isPlaying: Boolean = true) {
        val targetScope = scope ?: return
        val previousPosition: Long
        val previous: ActiveTrack?
        synchronized(lock) {
            previous = active
            previousPosition = lastPositionMs
            active = null
            lastPositionMs = 0L
            nowPlayingStartedAtSecs = null
        }
        submitScrobble(targetScope, previous, previousPosition)

        val track = item?.toLastFmTrack() ?: return
        if (!isReady()) return

        val state = ActiveTrack(
            track = track,
            durationMs = durationMs.takeIf { it > 0 } ?: (item.durationSecs * 1_000L),
            startedAtSecs = System.currentTimeMillis() / 1_000L
        )
        synchronized(lock) { active = state }

        if (isPlaying) notifyNowPlaying(targetScope, state)
    }

    /** Notify Last.fm on play/resume if the track was selected while paused. */
    fun onPlaybackStarted() {
        val targetScope = scope ?: return
        val state = synchronized(lock) { active } ?: return
        if (isReady()) notifyNowPlaying(targetScope, state)
    }

    private fun notifyNowPlaying(targetScope: CoroutineScope, state: ActiveTrack) {
        val apiKey = store.apiKey
        val apiSecret = store.apiSecret
        val sessionKey = store.session()?.sessionKey ?: return
        val shouldSend = synchronized(lock) {
            if (active?.startedAtSecs != state.startedAtSecs || nowPlayingStartedAtSecs == state.startedAtSecs) {
                false
            } else {
                nowPlayingStartedAtSecs = state.startedAtSecs
                true
            }
        }
        if (!shouldSend) return
        targetScope.launch {
            runCatching {
                api.updateNowPlaying(apiKey, apiSecret, sessionKey, state.track)
            }.onFailure {
                SecureLogger.w(TAG, "Last.fm now-playing failed: ${it.message}")
            }
        }
    }

    /** Periodic position sample so a manual skip still knows how far it played. */
    fun onProgress(positionMs: Long) {
        synchronized(lock) {
            if (active != null) lastPositionMs = positionMs.coerceAtLeast(0L)
        }
    }

    /** Media3 may not know duration at transition time; accept it once prepared. */
    fun onDurationAvailable(durationMs: Long) {
        if (durationMs <= 0L) return
        synchronized(lock) {
            active = active?.copy(durationMs = durationMs)
        }
    }

    /** Playback stopped or the service is going away. */
    fun onStopped() {
        val targetScope = scope ?: return
        val previous: ActiveTrack?
        val position: Long
        synchronized(lock) {
            previous = active
            position = lastPositionMs
            active = null
            lastPositionMs = 0L
            nowPlayingStartedAtSecs = null
        }
        submitScrobble(targetScope, previous, position)
    }

    private fun submitScrobble(targetScope: CoroutineScope, state: ActiveTrack?, positionMs: Long) {
        if (state == null) return
        if (!LastFmScrobbleRules.shouldScrobble(positionMs, state.durationMs)) return
        val scrobble = PendingScrobble(track = state.track, timestampSecs = state.startedAtSecs)
        // Persist before any network work. If the Android service is torn down,
        // its scope may be cancelled immediately after this call.
        store.addPending(scrobble)
        targetScope.launch { flushPending() }
    }

    private suspend fun flushPending() {
        if (!isReady()) return
        flushMutex.withLock {
            val apiKey = store.apiKey
            val apiSecret = store.apiSecret
            val sessionKey = store.session()?.sessionKey ?: return
            val pending = store.pendingScrobbles()
            if (pending.isEmpty()) return

            val remaining = mutableListOf<PendingScrobble>()
            var deferred = false
            for (scrobble in pending) {
                if (deferred) {
                    remaining.add(scrobble)
                    continue
                }
                try {
                    api.scrobble(apiKey, apiSecret, sessionKey, scrobble)
                } catch (error: LastFmApiException) {
                    if (error.isRetryable) {
                        deferred = true
                        remaining.add(scrobble)
                    }
                } catch (_: IOException) {
                    deferred = true
                    remaining.add(scrobble)
                }
            }
            store.replacePending(remaining)
        }
    }

    private fun isReady(): Boolean = store.enabled && store.isConfigured()

    private fun MusicItem.toLastFmTrack(): LastFmTrack? {
        val safeTitle = title.takeIf { it.isNotBlank() } ?: return null
        return LastFmTrack(
            title = safeTitle,
            artist = uploader.takeIf { it.isNotBlank() } ?: "Unknown Artist",
            durationSecs = durationSecs
        )
    }

    private companion object {
        const val TAG = "LastFm"
    }
}

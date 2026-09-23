package com.vyllo.music.service

import android.os.SystemClock
import androidx.media3.common.Player
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.manager.WakeLockManager
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WakeLockHelper @Inject constructor(
    private val wakeLockManager: WakeLockManager
) {
    /**
     * Suspends (without blocking any thread) until ExoPlayer reports that
     * playback has truly started, then returns true. The caller must hold the
     * [WakeLockManager] locks across this call so Doze cannot suspend the
     * process while the player is still buffering/starting with the screen off.
     *
     * Returns false when playback demonstrably will not start (user paused /
     * player idling) or when [timeoutMs] elapses. Never leaks: the wait itself
     * holds no lock, it only borrows the caller's.
     */
    suspend fun keepWakeLockUntilPlaying(player: Player, timeoutMs: Long = 45_000L): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (player.isPlaying) {
                return true
            }
            // READY + playWhenReady means ExoPlayer has buffered and is about to
            // render; its own WAKE_MODE_NETWORK lock is engaged again, so the
            // gap is over even if isPlaying flips true a moment later.
            if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
                return true
            }
            // The user (or the system) paused/stopped mid-transition: waiting any
            // longer would just burn battery holding a lock nobody needs.
            if (!player.playWhenReady && !player.isPlaying) {
                SecureLogger.d("WakeLockHelper", "playWhenReady=false while waiting; aborting wait")
                return false
            }
            try {
                delay(100L)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Scope cancelled (service destroyed) — stop waiting cleanly.
                throw e
            }
        }
        val started = player.isPlaying ||
            (player.playWhenReady && player.playbackState == Player.STATE_READY)
        if (!started) {
            SecureLogger.w(
                "WakeLockHelper",
                "Timed out waiting for playback to start; isPlaying=${player.isPlaying}, " +
                    "playbackState=${player.playbackState}, playWhenReady=${player.playWhenReady}"
            )
        }
        return started
    }
}

package com.vyllo.music.domain.manager

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import com.vyllo.music.core.security.SecureLogger

/**
 * Handles playback errors with bounded retries and exponential backoff.
 * Prevents infinite retry loops on permanent failures.
 */
class PlaybackErrorHandler(
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 2_000L,
    private val maxDelayMs: Long = 10_000L,
    private val onRetry: () -> Unit,
    private val onMaxRetriesReached: () -> Unit
) {

    private var consecutiveErrorCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private var pendingRetryRunnable: Runnable? = null

    private companion object {
        private const val TAG = "PlaybackErrorHandler"
    }

    fun handleError(error: androidx.media3.common.PlaybackException) {
        consecutiveErrorCount++
        SecureLogger.e(TAG, "Playback error #${consecutiveErrorCount}: ${error.message}", error)

        if (consecutiveErrorCount > maxRetries) {
            SecureLogger.w(TAG, "Max retries ($maxRetries) reached, stopping retry cycle")
            consecutiveErrorCount = 0
            onMaxRetriesReached()
            return
        }

        val delayMs = minOf(baseDelayMs * consecutiveErrorCount, maxDelayMs)
        SecureLogger.d(TAG, "Scheduling retry #${consecutiveErrorCount} in ${delayMs}ms")

        pendingRetryRunnable?.let { handler.removeCallbacks(it) }

        val runnable = Runnable {
            if (consecutiveErrorCount <= maxRetries) {
                onRetry()
            }
        }
        pendingRetryRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    fun resetOnSuccess() {
        if (consecutiveErrorCount > 0) {
            SecureLogger.d(TAG, "Playback recovered after $consecutiveErrorCount error(s), resetting counter")
        }
        consecutiveErrorCount = 0
        pendingRetryRunnable?.let { handler.removeCallbacks(it) }
        pendingRetryRunnable = null
    }

    fun release() {
        pendingRetryRunnable?.let { handler.removeCallbacks(it) }
        pendingRetryRunnable = null
        consecutiveErrorCount = 0
    }
}

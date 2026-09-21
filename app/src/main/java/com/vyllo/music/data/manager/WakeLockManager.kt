package com.vyllo.music.data.manager

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import com.vyllo.music.core.security.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages CPU and WiFi wake locks for background audio playback.
 *
 * Centralizes lock acquisition/release to prevent leaks and
 * eliminates duplicated lock logic across services.
 */
@Singleton
class WakeLockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val wakeLock: PowerManager.WakeLock by lazy {
        powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Vyllo::ServiceWakeLock"
        ).apply { setReferenceCounted(true) }
    }

    private val wifiLock: WifiManager.WifiLock by lazy {
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
            WifiManager.WIFI_MODE_FULL
        }
        wifiManager.createWifiLock(
            mode,
            "Vyllo::ServiceWifiLock"
        ).apply { setReferenceCounted(true) }
    }

    /**
     * Acquire both CPU and WiFi wake locks.
     *
     * NOTE: callers must pair every acquire() with release(), preferably via
     * [withLocks]. The locks are reference-counted so nested acquire/release
     * pairs (e.g. a transition that also enqueues the following track) balance
     * correctly.
     */
    fun acquire(timeoutMs: Long = TRANSITION_TIMEOUT_MS) {
        try {
            wakeLock.acquire(timeoutMs)
            wifiLock.acquire()
        } catch (e: Exception) {
            SecureLogger.e("WakeLockManager", "Failed to acquire wake locks", e)
        }
    }

    /**
     * Release both CPU and WiFi wake locks.
     */
    fun release() {
        try {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
            if (wifiLock.isHeld) {
                wifiLock.release()
            }
        } catch (e: Exception) {
            SecureLogger.e("WakeLockManager", "Failed to release wake locks", e)
        }
    }

    /**
     * Returns true if both locks are currently held.
     */
    fun isHeld(): Boolean = wakeLock.isHeld && wifiLock.isHeld

    /**
     * Runs [block] with both locks held, always releasing afterwards.
     * This is the only safe way to do gap work (track transitions, error
     * retries) while the screen is off: without a held PARTIAL_WAKE_LOCK +
     * WifiLock, Doze suspends the process mid-network-request and the next
     * track only starts when the user turns the screen back on.
     */
    suspend fun <T> withLocks(
        timeoutMs: Long = TRANSITION_TIMEOUT_MS,
        block: suspend () -> T
    ): T {
        acquire(timeoutMs)
        try {
            return block()
        } finally {
            release()
        }
    }

    companion object {
        /**
         * How long a transition may hold the locks before the failsafe timeout
         * drops them. Deliberately generous: a NewPipe resolve plus stream
         * probes can take tens of seconds on a throttled lock-screen network,
         * and an early release is exactly what stalls the queue until unlock.
         */
        const val TRANSITION_TIMEOUT_MS = 10 * 60 * 1000L
    }
}

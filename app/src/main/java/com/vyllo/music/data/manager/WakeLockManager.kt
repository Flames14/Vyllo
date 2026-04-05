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
        ).apply { setReferenceCounted(false) }
    }

    private val wifiLock: WifiManager.WifiLock by lazy {
        wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "Vyllo::ServiceWifiLock"
        ).apply { setReferenceCounted(false) }
    }

    /**
     * Acquire both CPU and WiFi wake locks.
     * Safe to call multiple times — checks held state first.
     */
    fun acquire() {
        try {
            if (!wakeLock.isHeld) {
                wakeLock.acquire()
            }
            if (!wifiLock.isHeld) {
                wifiLock.acquire()
            }
        } catch (e: Exception) {
            SecureLogger.e("WakeLockManager", "Failed to acquire wake locks", e)
        }
    }

    /**
     * Release both CPU and WiFi wake locks.
     * Safe to call multiple times — checks held state first.
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
}

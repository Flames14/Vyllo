package com.vyllo.music.service.alarm

import android.content.Context
import android.os.PowerManager
import com.vyllo.music.core.security.SecureLogger

/**
 * Manages CPU + screen wake locks for AlarmTriggerService.
 * Extracted verbatim — no behavior change.
 */
class AlarmWakeLockManager(private val context: Context) {

    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var screenWakeLock: PowerManager.WakeLock? = null

    /**
     * Acquire wake locks to keep CPU awake and turn screen on.
     *
     * CRITICAL: Uses PARTIAL_WAKE_LOCK for CPU (reliable on all modern Android).
     * FULL_WAKE_LOCK is deprecated and unreliable — DO NOT use it.
     * Screen turn-on is handled separately via the AlarmTriggerActivity window flags
     * and the full-screen notification intent.
     */
    fun acquire() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        // CPU wake lock — keeps CPU running, works reliably with screen off
        cpuWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Vyllo::AlarmCpuWakeLock"
        ).apply {
            acquire(15 * 60 * 1000L) // 15 minutes max
        }

        // Screen wake lock — turns screen on and keeps it on
        // This is separate because PARTIAL_WAKE_LOCK doesn't affect the screen.
        @Suppress("DEPRECATION")
        screenWakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
            PowerManager.ACQUIRE_CAUSES_WAKEUP or
            PowerManager.ON_AFTER_RELEASE,
            "Vyllo::AlarmScreenWakeLock"
        ).apply {
            acquire(15 * 60 * 1000L) // 15 minutes max
        }

        SecureLogger.d("AlarmTriggerService", "Wake locks acquired (CPU + Screen)")
    }

    /**
     * Release all wake locks.
     */
    fun release() {
        cpuWakeLock?.let {
            if (it.isHeld) it.release()
        }
        cpuWakeLock = null

        screenWakeLock?.let {
            if (it.isHeld) it.release()
        }
        screenWakeLock = null

        SecureLogger.d("AlarmTriggerService", "Wake locks released")
    }
}

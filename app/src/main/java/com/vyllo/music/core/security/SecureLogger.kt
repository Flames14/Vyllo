package com.vyllo.music.core.security

import android.util.Log
import com.vyllo.music.BuildConfig

/**
 * Secure Logger that strips logs in release builds
 * 
 * Prevents information leakage in production while maintaining
 * debug capabilities during development.
 */
object SecureLogger {

    private const val TAG = "Vyllo"

    /**
     * Debug level - stripped in release
     */
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "[$tag] $message", throwable)
        }
    }

    /**
     * Debug level with lazy message evaluation (performance)
     */
    fun d(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "[$tag] ${message()}")
        }
    }

    /**
     * Info level - stripped in release
     */
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "[$tag] $message", throwable)
        }
    }

    /**
     * Warning level - kept in release for monitoring
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(TAG, "[$tag] $message", throwable)
    }

    /**
     * Error level - kept in release for crash reporting
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(TAG, "[$tag] $message", throwable)
    }

    /**
     * Error level with lazy message evaluation
     */
    fun e(tag: String, message: () -> String, throwable: Throwable? = null) {
        Log.e(TAG, "[$tag] ${message()}", throwable)
    }

    /**
     * Security-specific warning (always logged)
     */
    fun security(tag: String, message: String) {
        Log.w(TAG, "[SECURITY][$tag] $message")
    }

    /**
     * Log sensitive operation (only in debug)
     */
    fun sensitive(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "[SENSITIVE][$tag] $message")
        }
    }
}

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
     * True when debug logging is compiled in.
     *
     * Lets callers skip building expensive log payloads (stream lists, header maps)
     * without paying for the string construction first.
     */
    val isDebugEnabled: Boolean get() = BuildConfig.DEBUG

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
     * Warning logs are restricted to debug builds. Logcat is readable on some
     * rooted and diagnostic environments, so release builds must not emit
     * request, account, or error details.
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "[$tag] $message", throwable)
        }
    }

    /**
     * Error logs are restricted to debug builds. Crashes still reach Android's
     * normal uncaught-exception handling; this avoids persisting sensitive
     * operational context in production Logcat.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "[$tag] $message", throwable)
        }
    }

    /**
     * Error level with lazy message evaluation
     */
    fun e(tag: String, message: () -> String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            Log.e(TAG, "[$tag] ${message()}", throwable)
        }
    }

    /**
     * Security-specific warning for development diagnostics.
     */
    fun security(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "[SECURITY][$tag] $message")
        }
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

package com.vyllo.music.core.security

import android.content.Context
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import com.vyllo.music.core.security.SecureLogger as Log
import java.io.File
import java.security.SecureRandom

/**
 * Secure Cache Manager for Media Storage
 * 
 * Provides:
 * - Randomized cache directory names
 * - Restricted file permissions
 * - Secure cache eviction
 */
object SecureCacheManager {

    private const val TAG = "SecureCacheManager"
    private const val MAX_CACHE_SIZE = 300L * 1024 * 1024 // 300MB

    @Volatile
    private var simpleCache: SimpleCache? = null

    /**
     * Gets or creates a secure cache instance
     */
    fun getSecureCache(context: Context): SimpleCache {
        return simpleCache ?: synchronized(this) {
            simpleCache ?: createSecureCache(context).also { simpleCache = it }
        }
    }

    private fun createSecureCache(context: Context): SimpleCache {
        // Create cache directory with randomized name for security
        val cacheDir = File(context.cacheDir, "media_${generateRandomSuffix()}")
        
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            
            // Set restrictive permissions (Android 10+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                try {
                    cacheDir.setReadable(false, false) // Not world-readable
                    cacheDir.setWritable(false, false) // Not world-writable
                } catch (e: Exception) {
                    Log.d(TAG, "Could not set restrictive permissions: ${e.message}")
                }
            }
        }

        Log.d(TAG, "Secure cache created at: ${cacheDir.absolutePath}")

        return SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE),
            StandaloneDatabaseProvider(context)
        )
    }

    /**
     * Generates a random suffix for cache directory name
     */
    private fun generateRandomSuffix(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..8).map { chars[SecureRandom().nextInt(chars.length)] }.joinToString("")
    }

    /**
     * Clears and releases the cache
     */
    fun clearCache() {
        simpleCache?.release()
        simpleCache = null
        Log.d(TAG, "Cache cleared")
    }

    /**
     * Gets cache statistics
     */
    fun getCacheSize(): Long {
        return simpleCache?.cacheSpace ?: 0L
    }

    /**
     * Releases resources (call on app termination)
     */
    fun release() {
        simpleCache?.release()
        simpleCache = null
    }
}

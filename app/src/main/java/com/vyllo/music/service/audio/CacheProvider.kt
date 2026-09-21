package com.vyllo.music.service.audio

import android.content.Context
import androidx.media3.datasource.cache.SimpleCache
import com.vyllo.music.core.security.SecureCacheManager

/**
 * Provides the shared ExoPlayer SimpleCache.
 * Extracted from MusicService companion — no behavior change.
 */
object CacheProvider {
    @Volatile
    private var simpleCache: SimpleCache? = null

    fun getSimpleCache(context: Context): SimpleCache {
        // Use SecureCacheManager for encrypted cache with randomized directory
        return simpleCache ?: synchronized(this) {
            simpleCache ?: SecureCacheManager.getSecureCache(context).also { simpleCache = it }
        }
    }
}

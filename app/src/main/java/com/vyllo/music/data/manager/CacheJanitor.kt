package com.vyllo.music.data.manager

import android.content.Context
import coil.Coil
import com.vyllo.music.core.security.SecureCacheManager
import com.vyllo.music.core.security.SecureLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read/clear access to the two caches the app owns:
 *
 * - media: the media3 [androidx.media3.datasource.cache.SimpleCache] under
 *   `cacheDir/media_cache` (300 MB LRU),
 * - images: Coil's disk + memory cache under `cacheDir/image_cache`.
 *
 * Sizes are measured from the filesystem so they stay correct before the
 * player has ever initialized the media cache. Clearing evicts media entries
 * through [SecureCacheManager] instead of deleting files underneath a live
 * player, which would corrupt an in-flight read.
 */
@Singleton
class CacheJanitor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class Usage(val mediaBytes: Long, val imageBytes: Long) {
        val totalBytes: Long get() = mediaBytes + imageBytes
    }

    companion object {
        private const val TAG = "CacheJanitor"
        private const val MEDIA_CACHE_DIR = "media_cache"
    }

    suspend fun usage(): Usage = withContext(Dispatchers.IO) {
        Usage(
            mediaBytes = directorySize(File(context.cacheDir, MEDIA_CACHE_DIR)),
            imageBytes = imageCacheBytes()
        )
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        evictMediaCache()
        runCatching {
            val loader = Coil.imageLoader(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }.onFailure { SecureLogger.w(TAG, "Image cache clear failed: ${it.message}") }
    }

    private fun imageCacheBytes(): Long =
        runCatching { Coil.imageLoader(context).diskCache?.size ?: 0L }.getOrDefault(0L)

    /**
     * Evicts every media cache key through the cache's own API. Per-key
     * failures are expected when a span is in use by the player; the rest of
     * the cache is still evicted, so the entry retries on the next clear.
     */
    private fun evictMediaCache() {
        if (!File(context.cacheDir, MEDIA_CACHE_DIR).exists()) return
        val cache = runCatching { SecureCacheManager.getSecureCache(context) }
            .onFailure { SecureLogger.w(TAG, "Media cache unavailable: ${it.message}") }
            .getOrNull() ?: return
        cache.keys.toList().forEach { key ->
            runCatching { cache.removeResource(key) }
        }
    }

    private fun directorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }
}

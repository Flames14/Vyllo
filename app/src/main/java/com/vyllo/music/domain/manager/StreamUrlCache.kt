package com.vyllo.music.domain.manager

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamUrlCache @Inject constructor() {
    private data class Entry(
        val resolvedUrl: String,
        val resolvedAtMs: Long = System.currentTimeMillis()
    )

    private val values = ConcurrentHashMap<String, Entry>()
    private val order = ConcurrentLinkedQueue<String>()
    private val lock = Any()
    private val maxEntries = 100

    companion object {
        /**
         * Resolved googlevideo URLs expire server-side (typically ~6h). Evict earlier
         * so a stale cached URL is never handed to the player — a stale URL surfaces
         * as a 403 mid-queue, exactly when the screen is off and recovery is hardest.
         */
        const val TTL_MS = 5 * 60 * 60 * 1000L
    }

    fun get(url: String, isVideo: Boolean): String? {
        val key = cacheKey(url, isVideo)
        val entry = values[key] ?: return null
        if (System.currentTimeMillis() - entry.resolvedAtMs > TTL_MS) {
            remove(url, isVideo)
            return null
        }
        return entry.resolvedUrl
    }

    fun put(url: String, isVideo: Boolean, resolvedUrl: String) {
        val key = cacheKey(url, isVideo)
        synchronized(lock) {
            if (!values.containsKey(key) && values.size >= maxEntries) {
                order.poll()?.let(values::remove)
            }
            values[key] = Entry(resolvedUrl)
            order.remove(key)
            order.add(key)
        }
    }

    fun remove(url: String, isVideo: Boolean) {
        val key = cacheKey(url, isVideo)
        synchronized(lock) {
            values.remove(key)
            order.remove(key)
        }
    }

    private fun cacheKey(url: String, isVideo: Boolean): String = "${url}_$isVideo"
}

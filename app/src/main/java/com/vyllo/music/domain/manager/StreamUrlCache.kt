package com.vyllo.music.domain.manager

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamUrlCache @Inject constructor() {
    private val values = ConcurrentHashMap<String, String>()
    private val order = ConcurrentLinkedQueue<String>()
    private val lock = Any()
    private val maxEntries = 100

    fun get(url: String, isVideo: Boolean): String? = values[cacheKey(url, isVideo)]

    fun put(url: String, isVideo: Boolean, resolvedUrl: String) {
        val key = cacheKey(url, isVideo)
        synchronized(lock) {
            if (!values.containsKey(key) && values.size >= maxEntries) {
                order.poll()?.let(values::remove)
            }
            values[key] = resolvedUrl
            order.remove(key)
            order.add(key)
        }
    }

    private fun cacheKey(url: String, isVideo: Boolean): String = "${url}_$isVideo"
}

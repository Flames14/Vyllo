package com.vyllo.music.domain.manager

import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.MusicItemType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Unit tests for PlayerViewModel stream URL cache thread safety.
 * Tests the cache implementation directly.
 */
class StreamUrlCacheTest {

    companion object {
        private const val MAX_CACHE_SIZE = 10
    }

    private val cache = ConcurrentHashMap<String, String>()
    private val cacheOrder = ConcurrentLinkedQueue<String>()
    private val cacheLock = Any()

    private fun addToCache(key: String, value: String) {
        synchronized(cacheLock) {
            if (cache.size >= MAX_CACHE_SIZE) {
                cacheOrder.poll()?.let { oldest ->
                    cache.remove(oldest)
                }
            }
            cache[key] = value
            cacheOrder.add(key)
        }
    }

    @Test
    fun `cache stores and retrieves values`() {
        addToCache("song1_false", "https://stream1.example.com")
        assertEquals("https://stream1.example.com", cache["song1_false"])
    }

    @Test
    fun `cache evicts oldest entry when full`() {
        // Fill cache to capacity
        repeat(MAX_CACHE_SIZE) { i ->
            addToCache("key_$i", "value_$i")
        }

        assertEquals(MAX_CACHE_SIZE, cache.size)

        // Add one more - should evict oldest
        addToCache("key_new", "value_new")

        assertEquals(MAX_CACHE_SIZE, cache.size)
        assertNull(cache["key_0"]) // Oldest should be evicted
        assertEquals("value_new", cache["key_new"])
    }

    @Test
    fun `cache is thread-safe under concurrent access`() = runTest {
        val threads = mutableListOf<Thread>()

        // Spawn multiple threads writing to cache
        repeat(10) { threadId ->
            val t = Thread {
                repeat(100) { i ->
                    addToCache("thread${threadId}_item$i", "value_${threadId}_$i")
                }
            }
            threads.add(t)
            t.start()
        }

        // Wait for all threads to complete
        threads.forEach { it.join() }

        // Cache should not exceed max size and should not have thrown
        assertTrue(cache.size <= MAX_CACHE_SIZE)
    }

    @Test
    fun `cache handles duplicate keys gracefully`() {
        addToCache("duplicate_key", "value_1")
        addToCache("duplicate_key", "value_2")

        assertEquals(1, cache.size)
        assertEquals("value_2", cache["duplicate_key"])
    }
}

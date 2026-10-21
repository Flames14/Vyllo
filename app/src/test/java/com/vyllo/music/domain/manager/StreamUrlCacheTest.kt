package com.vyllo.music.domain.manager

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.vyllo.music.data.manager.PreferenceManager

class StreamUrlCacheTest {

    private lateinit var cache: StreamUrlCache
    private lateinit var preferences: PreferenceManager

    @Before
    fun setUp() {
        preferences = mock()
        whenever(preferences.streamQuality).thenReturn("Auto")
        cache = StreamUrlCache(preferences)
    }

    @Test
    fun `changing stream quality does not reuse the previous quality's url`() {
        cache.put("https://song1", false, "https://stream-high")
        whenever(preferences.streamQuality).thenReturn("Low")

        assertNull(cache.get("https://song1", false))
    }

    @Test
    fun `put and get returns resolved url`() {
        cache.put("https://song1", false, "https://stream1")
        assertEquals("https://stream1", cache.get("https://song1", false))
    }

    @Test
    fun `get returns null for missing key`() {
        assertNull(cache.get("https://missing", false))
    }

    @Test
    fun `get distinguishes isVideo flag`() {
        cache.put("https://song1", false, "https://audio-stream")
        assertEquals("https://audio-stream", cache.get("https://song1", false))
        assertNull(cache.get("https://song1", true))
    }

    @Test
    fun `remove clears entry`() {
        cache.put("https://song1", false, "https://stream1")
        cache.remove("https://song1", false)
        assertNull(cache.get("https://song1", false))
    }

    @Test
    fun `put overwrites existing value`() {
        cache.put("https://song1", false, "https://old")
        cache.put("https://song1", false, "https://new")
        assertEquals("https://new", cache.get("https://song1", false))
    }

    @Test
    fun `evicts oldest entry when capacity exceeded`() {
        val capacity = 100
        repeat(capacity) { i ->
            cache.put("https://song_$i", false, "https://stream_$i")
        }
        cache.put("https://song_new", false, "https://stream_new")

        assertNull(cache.get("https://song_0", false))
        assertEquals("https://stream_new", cache.get("https://song_new", false))
        assertEquals("https://stream_99", cache.get("https://song_99", false))
    }

    @Test
    fun `concurrent access does not corrupt cache`() {
        val threads = 10
        val perThread = 50
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)

        repeat(threads) { t ->
            Thread {
                start.await()
                repeat(perThread) { i ->
                    cache.put("https://t${t}_$i", false, "https://v${t}_$i")
                    cache.get("https://t${t}_$i", false)
                }
                done.countDown()
            }.start()
        }

        start.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))

        // Cache must remain within capacity bounds and usable after concurrent writes
        cache.put("https://probe", false, "https://probe-val")
        assertEquals("https://probe-val", cache.get("https://probe", false))

        // Oldest entries from early threads may be evicted (capacity 100 < 500 writes);
        // a late write from the final thread should still be present or evictable cleanly.
        cache.put("https://late", false, "https://late-val")
        assertEquals("https://late-val", cache.get("https://late", false))
    }
}

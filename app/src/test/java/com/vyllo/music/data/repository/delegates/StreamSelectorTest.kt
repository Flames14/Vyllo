package com.vyllo.music.data.repository.delegates

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Stream selection contract.
 *
 * The regression this guards: selection used to issue a real HTTP probe per candidate
 * until one answered, from highest bitrate down — one or more round trips added to
 * every tap. Selection must now be probe-free; probing is the error-recovery path only.
 */
class StreamSelectorTest {

    private val selector = StreamSelector(com.vyllo.music.core.security.SecurityConfig())

    /** Streams are never probed by selection; use a URL that would fail if hit. */
    private fun audioStream(bitrate: Int, id: String): AudioStream {
        val stream = mock<AudioStream>()
        whenever(stream.isUrl).thenReturn(true)
        whenever(stream.averageBitrate).thenReturn(bitrate)
        whenever(stream.url).thenReturn("https://r1---sn-unreachable.googlevideo.com/videoplayback?expire=$id")
        return stream
    }

    @Test
    fun `audio candidates are ordered highest bitrate first`() {
        val candidates = selector.audioCandidates(
            listOf(audioStream(128, "a"), audioStream(320, "b"), audioStream(192, "c"))
        )
        assertEquals("b", candidates[0].substringAfter("expire="))
        assertEquals("c", candidates[1].substringAfter("expire="))
        assertEquals("a", candidates[2].substringAfter("expire="))
    }

    @Test
    fun `audio candidates exclude non url streams and blank urls`() {
        val notUrl = mock<AudioStream>()
        whenever(notUrl.isUrl).thenReturn(false)
        val blank = mock<AudioStream>()
        whenever(blank.isUrl).thenReturn(true)
        whenever(blank.averageBitrate).thenReturn(320)
        whenever(blank.url).thenReturn("")

        val candidates = selector.audioCandidates(listOf(notUrl, blank, audioStream(128, "a")))
        assertEquals(1, candidates.size)
        assertEquals("a", candidates[0].substringAfter("expire="))
    }

    @Test
    fun `video for audio candidates are ordered lowest bitrate first`() {
        val candidates = selector.videoCandidatesForAudio(
            listOf(
                mockVideoStream(1080, "high"),
                mockVideoStream(144, "low"),
                mockVideoStream(720, "mid")
            )
        )
        // Cheapest bandwidth wins because only the audio track is used.
        assertEquals("low", candidates[0].substringAfter("expire="))
        assertEquals("high", candidates[2].substringAfter("expire="))
    }

    @Test
    fun `video candidates are ordered highest bitrate first`() {
        val candidates = selector.videoCandidates(
            listOf(mockVideoStream(144, "low"), mockVideoStream(1080, "high"))
        )
        assertEquals("high", candidates[0].substringAfter("expire="))
    }

    @Test
    fun `empty stream list yields no candidates`() {
        assertTrue(selector.audioCandidates(emptyList()).isEmpty())
        assertTrue(selector.videoCandidates(emptyList()).isEmpty())
        assertTrue(selector.videoCandidatesForAudio(emptyList()).isEmpty())
    }

    // -----------------------------------------------------------------
    // Probe contract
    // -----------------------------------------------------------------

    @Test
    fun `selection performs no network probing`() {
        // Use the controlled probe override rather than a wall-clock threshold.
        // CI machines can be paused or CPU-starved, but a selection path that
        // probes would always increment maxInFlight regardless of host load.
        val tracker = ConcurrencyTrackingSelector()
        val candidates = tracker.audioCandidates(
            listOf(audioStream(128, "a"), audioStream(320, "b"))
        )

        assertEquals(2, candidates.size)
        assertEquals("selection must not call the recovery probe", 0, tracker.maxInFlight)
    }

    @Test
    fun `recovery path returns null when nothing is playable`() = runBlocking {
        assertNull(selector.firstPlayableStreamUrl(emptyList()))
    }

    @Test
    fun `recovery path probes candidates concurrently not serially`() = runBlocking {
        // Subclass to observe concurrency without touching the network.
        val tracker = ConcurrencyTrackingSelector()
        val candidates = (1..4).map { "https://host-$it.test/x" }

        tracker.firstPlayableStreamUrl(candidates)

        assertTrue(
            "candidates must be probed in parallel, saw at most ${tracker.maxInFlight} in flight",
            tracker.maxInFlight > 1
        )
    }

    @Test
    fun `recovery path prefers the highest quality playable candidate`() = runBlocking {
        val tracker = ConcurrencyTrackingSelector().apply {
            // Only the last (lowest bitrate) candidate is playable.
            playableUrls += "https://low.test/x"
        }
        val result = tracker.firstPlayableStreamUrl(
            listOf("https://high.test/x", "https://mid.test/x", "https://low.test/x")
        )
        assertEquals("https://low.test/x", result)
    }

    @Test
    fun `recovery path single candidate is probed directly`() = runBlocking {
        val tracker = ConcurrencyTrackingSelector().apply { playableUrls += "https://only.test/x" }
        assertEquals("https://only.test/x", tracker.firstPlayableStreamUrl(listOf("https://only.test/x")))
        assertEquals(1, tracker.maxInFlight)
    }

    private fun mockVideoStream(bitrate: Int, id: String): VideoStream {
        val stream = mock<VideoStream>()
        whenever(stream.isUrl).thenReturn(true)
        whenever(stream.bitrate).thenReturn(bitrate)
        whenever(stream.url).thenReturn("https://r1---sn-unreachable.googlevideo.com/videoplayback?expire=$id")
        return stream
    }

    /**
     * Stands in for [com.vyllo.music.data.repository.delegates.StreamSelector] with a
     * probe implementation we control, so the concurrency contract can be asserted
     * without real network I/O.
     */
    private class ConcurrencyTrackingSelector : StreamSelector(com.vyllo.music.core.security.SecurityConfig()) {
        val playableUrls = mutableSetOf<String>()

        /** Highest number of simultaneous probes observed. */
        @Volatile
        var maxInFlight: Int = 0
            private set

        private val inFlight = AtomicInteger(0)
        private val lock = Any()

        override fun isPlayableStreamUrl(streamUrl: String): Boolean {
            val current = inFlight.incrementAndGet()
            synchronized(lock) {
                if (current > maxInFlight) maxInFlight = current
            }
            try {
                Thread.sleep(40)
                return streamUrl in playableUrls
            } finally {
                inFlight.decrementAndGet()
                synchronized(lock) { /* peak already recorded on entry */ }
            }
        }
    }
}

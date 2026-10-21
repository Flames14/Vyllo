package com.vyllo.music.domain.usecase

import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.domain.manager.PlaybackQueueManager
import com.vyllo.music.domain.manager.PlaybackManager
import com.vyllo.music.domain.manager.StreamUrlCache
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.MusicItemType
import com.vyllo.music.domain.model.PlayResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PlayMusicUseCaseTest {

    private lateinit var getStreamUrlUseCase: GetStreamUrlUseCase
    private lateinit var playbackManager: PlaybackManager
    private lateinit var playbackQueueManager: PlaybackQueueManager
    private lateinit var streamUrlCache: StreamUrlCache
    private lateinit var useCase: PlayMusicUseCase

    private val testItem = MusicItem(
        id = "1",
        title = "Test Song",
        url = "https://example.com/song/1",
        uploader = "Test Artist",
        thumbnailUrl = "https://example.com/thumb/1.jpg",
        type = MusicItemType.SONG
    )

    @Before
    fun setUp() {
        getStreamUrlUseCase = mock()
        playbackManager = mock()
        playbackQueueManager = PlaybackQueueManager()
        // Fresh cache per test so one test's resolved URL never masks another's.
        streamUrlCache = StreamUrlCache(mock<PreferenceManager>().also {
            whenever(it.streamQuality).thenReturn("Auto")
        })
        useCase = PlayMusicUseCase(getStreamUrlUseCase, playbackManager, playbackQueueManager, streamUrlCache)
    }

    @Test
    fun `returns Failure when controller connection fails`() = runTest {
        whenever(playbackManager.awaitConnection())
            .thenThrow(RuntimeException("disconnected"))
        whenever(getStreamUrlUseCase(any(), any())).thenReturn("https://stream")

        val result = useCase.execute(testItem)

        assertTrue(result is PlayResult.Failure)
        assertEquals("Playback service unavailable", (result as PlayResult.Failure).message)
        verify(playbackManager, never()).stop()
        verify(playbackManager, never()).playMusic(any(), any(), any(), any())
    }

    @Test
    fun `returns Failure when stream URL cannot be resolved`() = runTest {
        whenever(playbackManager.awaitConnection()).thenReturn(mock())
        whenever(getStreamUrlUseCase(any(), any())).thenReturn(null)

        val result = useCase.execute(testItem)

        assertTrue(result is PlayResult.Failure)
        assertEquals("Unable to resolve stream URL", (result as PlayResult.Failure).message)
        // A failed resolve must leave the outgoing track alone: stopping first
        // produced dead air for the whole multi-second resolve.
        verify(playbackManager, never()).stop()
        verify(playbackManager, never()).playMusic(any(), any(), any(), any())
    }

    @Test
    fun `returns Success and replaces queue when keepQueue false`() = runTest {
        whenever(playbackManager.awaitConnection()).thenReturn(mock())
        whenever(getStreamUrlUseCase(any(), any())).thenReturn("https://stream")
        whenever(playbackManager.playMusic(any(), any(), any(), any()))
            .thenReturn(Result.success(Unit))

        playbackQueueManager.addItem(testItem.copy(url = "https://other"))

        val result = useCase.execute(testItem, keepQueue = false)

        assertTrue(result is PlayResult.Success)
        // The tap path never stops the player â€” ExoPlayer hands off on its own.
        verify(playbackManager, never()).stop()
        assertEquals(1, playbackQueueManager.size)
        assertEquals(testItem.url, playbackQueueManager.currentItem?.url)
    }

    @Test
    fun `adds item to existing queue when keepQueue true and item not present`() = runTest {
        whenever(playbackManager.awaitConnection()).thenReturn(mock())
        whenever(getStreamUrlUseCase(any(), any())).thenReturn("https://stream")
        whenever(playbackManager.playMusic(any(), any(), any(), any()))
            .thenReturn(Result.success(Unit))

        val existing = testItem.copy(url = "https://existing")
        playbackQueueManager.addItem(existing)

        val result = useCase.execute(testItem, keepQueue = true)

        assertTrue(result is PlayResult.Success)
        assertEquals(2, playbackQueueManager.size)
        assertEquals(testItem.url, playbackQueueManager.currentItem?.url)
    }

    @Test
    fun `selects existing queue index when keepQueue true and item already queued`() = runTest {
        whenever(playbackManager.awaitConnection()).thenReturn(mock())
        whenever(getStreamUrlUseCase(any(), any())).thenReturn("https://stream")
        whenever(playbackManager.playMusic(any(), any(), any(), any()))
            .thenReturn(Result.success(Unit))

        val first = testItem.copy(url = "https://first")
        val second = testItem.copy(url = "https://second")
        playbackQueueManager.replaceQueue(listOf(first, second), startIndex = 0)

        val result = useCase.execute(second, keepQueue = true)

        assertTrue(result is PlayResult.Success)
        assertEquals(2, playbackQueueManager.size)
        assertEquals(second.url, playbackQueueManager.currentItem?.url)
    }

    @Test
    fun `returns Failure with exception message when playbackManager fails`() = runTest {
        whenever(playbackManager.awaitConnection()).thenReturn(mock())
        whenever(getStreamUrlUseCase(any(), any())).thenReturn("https://stream")
        whenever(playbackManager.playMusic(any(), any(), any(), any()))
            .thenReturn(Result.failure(RuntimeException("decoder error")))

        val result = useCase.execute(testItem)

        assertTrue(result is PlayResult.Failure)
        assertEquals("decoder error", (result as PlayResult.Failure).message)
    }

    @Test
    fun `passes isVideo through to stream url resolution`() = runTest {
        whenever(playbackManager.awaitConnection()).thenReturn(mock())
        whenever(getStreamUrlUseCase(testItem.url, true)).thenReturn("https://video-stream")
        whenever(playbackManager.playMusic(any(), any(), any(), any()))
            .thenReturn(Result.success(Unit))

        val result = useCase.execute(testItem, isVideo = true)

        assertTrue(result is PlayResult.Success)
        verify(getStreamUrlUseCase).invoke(testItem.url, isVideo = true)
        verify(playbackManager, times(1))
            .playMusic(testItem, "https://video-stream", isVideo = true, lookaheadItems = emptyList())
    }

    @Test
    fun `reuses cached stream url on a repeat play instead of re-extracting`() = runTest {
        whenever(playbackManager.awaitConnection()).thenReturn(mock())
        whenever(getStreamUrlUseCase(any(), any())).thenReturn("https://stream")
        whenever(playbackManager.playMusic(any(), any(), any(), any()))
            .thenReturn(Result.success(Unit))

        val first = useCase.execute(testItem)
        val second = useCase.execute(testItem)

        assertTrue(first is PlayResult.Success)
        assertTrue(second is PlayResult.Success)
        // The whole point of the cache: one extractor pass, two plays.
        verify(getStreamUrlUseCase, times(1)).invoke(testItem.url, isVideo = false)
        verify(playbackManager, times(2))
            .playMusic(testItem, "https://stream", isVideo = false, lookaheadItems = emptyList())
    }

    @Test
    fun `passes already cached lookahead urls without adding network latency`() = runTest {
        whenever(playbackManager.awaitConnection()).thenReturn(mock())
        whenever(getStreamUrlUseCase(any(), any())).thenReturn("https://stream")
        whenever(playbackManager.playMusic(any(), any(), any(), any()))
            .thenReturn(Result.success(Unit))

        val next = testItem.copy(url = "https://next", title = "Next")
        val after = testItem.copy(url = "https://after", title = "After")
        // keepQueue = false replaces the queue with just the tapped item, so build
        // the lookahead window explicitly afterwards.
        playbackQueueManager.replaceQueue(listOf(testItem), startIndex = 0)
        playbackQueueManager.replaceUpcomingItems(listOf(next, after))

        // Only "next" has been resolved; "after" must not be fetched on the tap path.
        streamUrlCache.put(next.url, false, "https://next-stream")

        useCase.execute(testItem, keepQueue = true)

        verify(playbackManager).playMusic(
            testItem,
            "https://stream",
            isVideo = false,
            lookaheadItems = listOf(next to "https://next-stream")
        )
        // The unresolved third track was never resolved as part of the tap.
        verify(getStreamUrlUseCase, times(1)).invoke(testItem.url, isVideo = false)
    }

    @Test
    fun `passes empty lookahead in video mode`() = runTest {
        whenever(playbackManager.awaitConnection()).thenReturn(mock())
        whenever(getStreamUrlUseCase(any(), any())).thenReturn("https://v")
        whenever(playbackManager.playMusic(any(), any(), any(), any()))
            .thenReturn(Result.success(Unit))

        val next = testItem.copy(url = "https://next", title = "Next")
        playbackQueueManager.replaceQueue(listOf(testItem), startIndex = 0)
        playbackQueueManager.replaceUpcomingItems(listOf(next))
        streamUrlCache.put(next.url, false, "https://next-stream")

        useCase.execute(testItem, isVideo = true, keepQueue = true)

        // Video mode must not inherit audio lookahead URLs.
        verify(playbackManager).playMusic(
            testItem,
            "https://v",
            isVideo = true,
            lookaheadItems = emptyList()
        )
    }

    @Test
    fun `caches nothing for a local file path so a later download is still honoured`() = runTest {
        whenever(playbackManager.awaitConnection()).thenReturn(mock())
        whenever(getStreamUrlUseCase(any(), any()))
            .thenReturn("/storage/emulated/0/Music/a.mp3")
            .thenReturn(null)
        whenever(playbackManager.playMusic(any(), any(), any(), any()))
            .thenReturn(Result.success(Unit))

        val first = useCase.execute(testItem)
        val second = useCase.execute(testItem)

        assertTrue(first is PlayResult.Success)
        assertTrue(second is PlayResult.Failure)
        // A path is never cached, so the second play went back to the resolver.
        verify(getStreamUrlUseCase, times(2)).invoke(testItem.url, isVideo = false)
    }
}


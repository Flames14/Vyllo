package com.vyllo.music.service.audio

import androidx.media3.common.C
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.domain.manager.PlaybackQueueManager
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.repository.IMusicRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PlaybackResumptionTest {

    private val preferenceManager: PreferenceManager = mock()
    private val queueManager: PlaybackQueueManager = mock()
    private val repository: IMusicRepository = mock()

    private val resumption = PlaybackResumption(preferenceManager, queueManager, repository)

    private val track = MusicItem(
        title = "Song",
        url = "https://www.youtube.com/watch?v=abc123def45"
    )

    private fun stubQueue(vararg items: MusicItem, index: Int = 0) {
        whenever(queueManager.getQueueSnapshot()).thenReturn(items.toList())
        whenever(queueManager.currentIndex).thenReturn(index)
    }

    private suspend fun stubStreamUrl(url: String) {
        whenever(repository.getStreamUrl(url, force = false, isVideo = false))
            .thenReturn("https://stream.example/audio")
    }

    @Test
    fun `resumes at the saved position for the same track`() = runTest {
        stubQueue(track)
        stubStreamUrl(track.url)
        whenever(preferenceManager.savedPlaybackPosition(track.url)).thenReturn(42_000L)

        val result = resumption.restore()

        assertEquals(1, result.mediaItems.size)
        assertEquals(0, result.startIndex)
        assertEquals(42_000L, result.startPositionMs)
    }

    @Test
    fun `starts from the beginning when nothing was saved`() = runTest {
        stubQueue(track)
        stubStreamUrl(track.url)
        whenever(preferenceManager.savedPlaybackPosition(track.url)).thenReturn(null)

        val result = resumption.restore()

        assertEquals(C.TIME_UNSET, result.startPositionMs)
    }

    @Test
    fun `falls back to the persisted queue when the live one is empty`() = runTest {
        whenever(queueManager.getQueueSnapshot()).thenReturn(emptyList())
        whenever(preferenceManager.restoreQueue()).thenReturn(listOf(track) to 0)
        stubStreamUrl(track.url)
        whenever(preferenceManager.savedPlaybackPosition(track.url)).thenReturn(15_000L)

        val result = resumption.restore()

        assertEquals(1, result.mediaItems.size)
        assertEquals(15_000L, result.startPositionMs)
    }

    @Test
    fun `returns an empty result when there is nothing to resume`() = runTest {
        whenever(queueManager.getQueueSnapshot()).thenReturn(emptyList())
        whenever(preferenceManager.restoreQueue()).thenReturn(null)

        val result = resumption.restore()

        assertTrue(result.mediaItems.isEmpty())
        assertEquals(C.TIME_UNSET, result.startPositionMs)
    }
}

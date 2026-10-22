package com.vyllo.music.service.stats

import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.repository.IMusicRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import kotlinx.coroutines.CoroutineScope
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackStatsRecorderTest {

    private val repository: IMusicRepository = mock()
    private val item = MusicItem(title = "Song", url = "https://youtu.be/abc", uploader = "Artist")
    private val otherItem = MusicItem(title = "Other", url = "https://youtu.be/xyz", uploader = "Artist")

    @Test
    fun `counts a play once per media id`() = runTest {
        val recorder = PlaybackStatsRecorder(repository, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        recorder.onTrackStarted(item)
        recorder.onTrackStarted(item)
        recorder.onTrackStarted(otherItem)
        recorder.onTrackStarted(otherItem)

        verify(repository, times(2)).recordPlayStart(any())
    }

    @Test
    fun `reset allows the same track to be counted again`() = runTest {
        val recorder = PlaybackStatsRecorder(repository, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        recorder.onTrackStarted(item)
        recorder.reset()
        recorder.onTrackStarted(item)

        verify(repository, times(2)).recordPlayStart(any())
    }

    @Test
    fun `ignores blank and missing urls`() = runTest {
        val recorder = PlaybackStatsRecorder(repository, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        recorder.onTrackStarted(null)
        recorder.onTrackStarted(item.copy(url = ""))

        verify(repository, never()).recordPlayStart(any())
    }

    @Test
    fun `reports the position reached when a track ends`() = runTest {
        val recorder = PlaybackStatsRecorder(repository, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        recorder.onTrackEnded(item, 42_000L)

        val urlCaptor = argumentCaptor<String>()
        val msCaptor = argumentCaptor<Long>()
        verify(repository).recordListenedTime(urlCaptor.capture(), msCaptor.capture())
        assertEquals(item.url, urlCaptor.firstValue)
        assertEquals(42_000L, msCaptor.firstValue)
    }

    @Test
    fun `does not count scraps from skipped tracks`() = runTest {
        val recorder = PlaybackStatsRecorder(repository, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        recorder.onTrackEnded(item, 1_000L)
        recorder.onTrackEnded(item, 0L)
        recorder.onTrackEnded(null, 60_000L)

        verify(repository, never()).recordListenedTime(any(), any())
    }

    @Test
    fun `repository failures never escape the recorder`() = runTest {
        whenever(repository.recordPlayStart(any())).thenThrow(IllegalStateException("db down"))
        whenever(repository.recordListenedTime(any(), any())).thenThrow(IllegalStateException("db down"))

        val recorder = PlaybackStatsRecorder(repository, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        recorder.onTrackStarted(item)
        recorder.onTrackEnded(item, 30_000L)

        verify(repository).recordPlayStart(any())
    }
}

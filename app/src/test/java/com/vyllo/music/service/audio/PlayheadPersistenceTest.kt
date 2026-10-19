package com.vyllo.music.service.audio

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.vyllo.music.data.manager.PreferenceManager
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PlayheadPersistenceTest {

    private val preferenceManager: PreferenceManager = mock()
    private val persistence = PlayheadPersistence(preferenceManager)

    private val trackUrl = "https://www.youtube.com/watch?v=abc123def45"

    private fun playerWith(mediaId: String, positionMs: Long): Player {
        val player: Player = mock()
        whenever(player.currentMediaItem)
            .thenReturn(MediaItem.Builder().setMediaId(mediaId).build())
        whenever(player.currentPosition).thenReturn(positionMs)
        return player
    }

    @Test
    fun `persists the current media id and position`() {
        persistence.persist(playerWith(trackUrl, positionMs = 12_000L))

        verify(preferenceManager).savePlaybackPosition(trackUrl, 12_000L)
    }

    @Test
    fun `ignores a player without a current item`() {
        val player: Player = mock()

        persistence.persist(player)

        verify(preferenceManager, never()).savePlaybackPosition(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `ignores a detached player`() {
        persistence.persist(null)

        verify(preferenceManager, never()).savePlaybackPosition(org.mockito.kotlin.any(), org.mockito.kotlin.any())
    }

    @Test
    fun `clear drops the saved playhead`() {
        persistence.clear()

        verify(preferenceManager).clearPlaybackPosition()
    }
}

package com.vyllo.music.recognition.data

import com.vyllo.music.recognition.domain.model.RecognitionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionHistoryCodecTest {

    private val result = RecognitionResult(
        trackId = "track-1",
        title = "Song",
        artist = "Artist",
        album = "Album",
        coverArtUrl = "https://example.test/cover.jpg",
        coverArtHqUrl = null,
        genre = "Pop",
        releaseDate = null,
        label = null,
        lyrics = null,
        shazamUrl = "https://example.test/track",
        appleMusicUrl = null,
        spotifyUrl = null,
        isrc = "ISRC123",
        youtubeVideoId = "video1"
    )

    @Test
    fun `encode and decode preserve recognition data`() {
        val input = listOf(RecognitionHistoryEntry(result, 1_234L))
        assertEquals(input, RecognitionHistoryRepository.decode(RecognitionHistoryRepository.encode(input)))
    }

    @Test
    fun `invalid payload returns empty history`() {
        assertTrue(RecognitionHistoryRepository.decode("bad json").isEmpty())
        assertTrue(RecognitionHistoryRepository.decode(null).isEmpty())
    }

    @Test
    fun `entries missing required identity are dropped`() {
        val decoded = RecognitionHistoryRepository.decode("""[{"title":"Missing track id"}]""")
        assertTrue(decoded.isEmpty())
    }
}

package com.vyllo.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkCandidatesTest {

    @Test
    fun `resolved url comes first and is not duplicated`() {
        val item = MusicItem(
            title = "Song",
            url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        )

        val candidates = ArtworkCandidates.forItem(item, "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg")

        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", candidates.first())
        assertEquals(candidates.size, candidates.distinct().size)
    }

    @Test
    fun `blank thumbnail falls back to urls derived from the video id`() {
        val item = MusicItem(
            title = "Song",
            url = "https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RDAMVM"
        )

        val candidates = ArtworkCandidates.forItem(item)

        assertEquals(3, candidates.size)
        assertTrue(candidates.first().endsWith("/maxresdefault.jpg"))
        assertTrue(candidates[1].endsWith("/sddefault.jpg"))
        assertTrue(candidates.last().endsWith("/hqdefault.jpg"))
    }

    @Test
    fun `youtube thumbnails walk maxres to default`() {
        val item = MusicItem(
            title = "Song",
            url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg"
        )

        val candidates = ArtworkCandidates.forItem(item)

        assertTrue(candidates[0].endsWith("maxresdefault.jpg"))
        assertTrue(candidates[1].endsWith("sddefault.jpg"))
        assertTrue(candidates[2].endsWith("hqdefault.jpg"))
        assertTrue(candidates[3].endsWith("mqdefault.jpg"))
    }

    @Test
    fun `google cdn artwork is upgraded to 1080`() {
        val item = MusicItem(
            title = "Song",
            url = "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            thumbnailUrl = "https://lh3.googleusercontent.com/abc=w120-h120-l90-rj"
        )

        val candidates = ArtworkCandidates.forItem(item)

        assertEquals("https://lh3.googleusercontent.com/abc=w1080-h1080-l90-rj", candidates.first())
        assertEquals(2, candidates.size)
    }

    @Test
    fun `video id is extracted from watch and short urls`() {
        assertEquals("dQw4w9WgXcQ", ArtworkCandidates.videoIdOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", ArtworkCandidates.videoIdOf("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals("dQw4w9WgXcQ", ArtworkCandidates.videoIdOf("dQw4w9WgXcQ"))
        assertNull(ArtworkCandidates.videoIdOf("https://example.com/song.mp3"))
        assertNull(ArtworkCandidates.videoIdOf(""))
    }
}

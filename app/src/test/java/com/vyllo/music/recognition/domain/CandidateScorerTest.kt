package com.vyllo.music.recognition.domain

import com.vyllo.music.domain.model.MusicItem
import org.junit.Assert.*
import org.junit.Test

class CandidateScorerTest {

    private fun item(title: String, artist: String, url: String) = MusicItem(
        title = title,
        url = url,
        uploader = artist,
        thumbnailUrl = ""
    )

    @Test
    fun prefersOfficialUploadOverReaction() {
        val candidates = listOf(
            item("Vibe Venuma Song Reaction", "Some Reactor", "url-reaction"),
            item("Vibe Venuma (Official Video)", "Anirudh Ravichander", "url-official"),
            item("Vibe Venuma Cover by Fan", "Fan Singer", "url-cover")
        )
        assertEquals("url-official", CandidateScorer.best(candidates, "Vibe Venuma", "Anirudh Ravichander")?.url)
    }

    @Test
    fun penalizesKaraokeAndLoops() {
        val candidates = listOf(
            item("My Song Karaoke Version", "Karaoke King", "url-karaoke"),
            item("My Song", "Real Artist", "url-real")
        )
        assertEquals("url-real", CandidateScorer.best(candidates, "My Song", "Real Artist")?.url)
    }

    @Test
    fun emptyListReturnsNull() {
        assertNull(CandidateScorer.best(emptyList(), "Anything", "Anyone"))
    }

    @Test
    fun tieKeepsHighestRanked() {
        val candidates = listOf(
            item("Same Song", "Same Artist", "url-first"),
            item("Same Song", "Same Artist", "url-second")
        )
        assertEquals("url-first", CandidateScorer.best(candidates, "Same Song", "Same Artist")?.url)
    }
}

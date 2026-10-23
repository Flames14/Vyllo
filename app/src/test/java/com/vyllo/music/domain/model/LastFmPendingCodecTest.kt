package com.vyllo.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LastFmPendingCodecTest {

    @Test
    fun `scrobbles round-trip with separators in the metadata`() {
        val input = listOf(
            PendingScrobble(
                track = LastFmTrack(
                    title = "Hello | World\nAgain",
                    artist = "Aria & Friends",
                    album = "100% Pure",
                    durationSecs = 215
                ),
                timestampSecs = 1_725_000_000
            ),
            PendingScrobble(
                track = LastFmTrack(title = "No album", artist = "Someone", durationSecs = 0),
                timestampSecs = 1_725_000_100
            )
        )

        assertEquals(input, LastFmPendingCodec.decode(LastFmPendingCodec.encode(input)))
    }

    @Test
    fun `malformed lines are dropped without losing valid entries`() {
        val raw = "garbage-without-fields\n" +
            "Title%20A|Artist%20A||0|1725000000\n" +
            "Title%20B|Artist%20B|album|not-a-number|1725000000"

        val decoded = LastFmPendingCodec.decode(raw)

        assertEquals(1, decoded.size)
        assertEquals("Title A", decoded.first().track.title)
        assertEquals("Artist A", decoded.first().track.artist)
    }

    @Test
    fun `blank payload decodes to an empty queue`() {
        assertTrue(LastFmPendingCodec.decode(null).isEmpty())
        assertTrue(LastFmPendingCodec.decode("").isEmpty())
    }
}

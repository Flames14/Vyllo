package com.vyllo.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LastFmSignatureTest {

    @Test
    fun `parameters are sorted and the secret is appended`() {
        val signature = LastFmSignature.sign(
            mapOf("method" to "auth.getToken", "api_key" to "abc"),
            secret = "secret"
        )

        assertEquals("f86444211049e605f18c05a5964aabfc", signature)
    }

    @Test
    fun `format and callback are excluded from the signature`() {
        val params = mapOf(
            "api_key" to "k1",
            "artist" to "Aria",
            "method" to "track.scrobble",
            "sk" to "T1",
            "track" to "Hello"
        )
        val expected = LastFmSignature.sign(params, "secret")

        val withIgnored = params + mapOf("format" to "json", "callback" to "cb")

        assertEquals(expected, LastFmSignature.sign(withIgnored, "secret"))
        assertEquals("afdb229a4c3be28fbdb3d420e8022759", expected)
    }
}

class LastFmScrobbleRulesTest {

    @Test
    fun `half of a short track is enough`() {
        assertTrue(LastFmScrobbleRules.shouldScrobble(positionMs = 60_000, durationMs = 100_000))
        assertFalse(LastFmScrobbleRules.shouldScrobble(positionMs = 40_000, durationMs = 100_000))
    }

    @Test
    fun `long tracks scrobble after four minutes even when barely started`() {
        assertFalse(LastFmScrobbleRules.shouldScrobble(positionMs = 120_000, durationMs = 900_000))
        assertTrue(LastFmScrobbleRules.shouldScrobble(positionMs = 240_000, durationMs = 900_000))
    }

    @Test
    fun `tracks shorter than thirty seconds never scrobble`() {
        assertFalse(LastFmScrobbleRules.shouldScrobble(positionMs = 20_000, durationMs = 25_000))
        assertFalse(LastFmScrobbleRules.shouldScrobble(positionMs = 25_000, durationMs = 25_000))
    }

    @Test
    fun `unknown duration requires the four minute rule`() {
        assertFalse(LastFmScrobbleRules.shouldScrobble(positionMs = 200_000, durationMs = 0))
        assertTrue(LastFmScrobbleRules.shouldScrobble(positionMs = 240_000, durationMs = 0))
    }
}

package com.vyllo.music.data.repository.delegates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMetadataResolverTest {

    private val resolver = TrackMetadataResolver(DescriptionMetadataParser())

    // -----------------------------------------------------------------
    // Language
    // -----------------------------------------------------------------

    @Test
    fun `declared language wins and is normalized`() {
        assertEquals("hindi", resolver.resolveLanguage(declared = "Hindi", title = "Anything"))
        assertEquals("tamil", resolver.resolveLanguage(declared = "TAMIL", title = "Anything"))
        assertEquals("punjabi", resolver.resolveLanguage(declared = "  Punjabi  ", title = "Anything"))
    }

    @Test
    fun `unknown declared language falls through to content detection`() {
        assertEquals("tamil", resolver.resolveLanguage(declared = "Klingon", title = "Tamil Love Song"))
    }

    @Test
    fun `language is detected from the title`() {
        assertEquals("telugu", resolver.resolveLanguage(declared = null, title = "Telugu Romantic Hit"))
        assertEquals("korean", resolver.resolveLanguage(declared = null, title = "Korean Dance Song"))
    }

    @Test
    fun `language is detected from tags`() {
        val tags = listOf("Tamil", "Love Songs", "2024")
        assertEquals("tamil", resolver.resolveLanguage(declared = null, title = "Untitled", tags = tags))
    }

    @Test
    fun `topic channel names do not fake a language`() {
        // "Topic" channels are the artist's official channel; their name is not a
        // language signal, and "- Topic" must not be mistaken for one.
        val result = resolver.resolveLanguage(
            declared = null,
            title = "Untitled",
            tags = emptyList(),
            uploader = "Some Artist - Topic"
        )
        assertNull(result)
    }

    @Test
    fun `unknown language yields null rather than a guess`() {
        // Inventing a language produces worse ordering than admitting ignorance.
        assertNull(resolver.resolveLanguage(declared = null, title = "Instrumental Piece 4"))
        assertNull(resolver.resolveLanguage(declared = null, title = "", tags = emptyList()))
        assertNull(resolver.resolveLanguage(declared = "", title = "x", tags = emptyList()))
    }

    @Test
    fun `more specific language is not swallowed by a shorter match`() {
        // "malayalam" contains "malay" — ordering in the hint table matters.
        assertEquals("malayalam", resolver.resolveLanguage(declared = null, title = "Malayalam Song"))
    }

    @Test
    fun `normalizeLanguage handles loose spellings`() {
        assertEquals("spanish", resolver.normalizeLanguage("Spanish"))
        assertEquals("french", resolver.normalizeLanguage("francais"))
        assertEquals("chinese", resolver.normalizeLanguage("Mandarin"))
        assertNull(resolver.normalizeLanguage("klingon"))
        assertNull(resolver.normalizeLanguage(""))
    }

    // -----------------------------------------------------------------
    // Film / soundtrack detection
    // -----------------------------------------------------------------

    @Test
    fun `film markers are recognised`() {
        assertTrue(resolver.looksLikeFilm("Jawan (Original Motion Picture Soundtrack)"))
        assertTrue(resolver.looksLikeFilm("Movie OST"))
        assertTrue(resolver.looksLikeFilm("Rocky Theme"))
        assertFalse(resolver.looksLikeFilm("Moonlight Album"))
    }

    // -----------------------------------------------------------------
    // Genres
    // -----------------------------------------------------------------

    @Test
    fun `genres come from real tags not guesses`() {
        val genres = resolver.extractGenres(tags = listOf("bollywood", "romance", "pop"))
        assertTrue(genres.contains("bollywood"))
    }

    @Test
    fun `more specific genre wins over a broader one`() {
        // "hip hop" and "rap" both point at hip hop; "lo-fi" beats "pop".
        val genres = resolver.extractGenres(tags = listOf("pop", "lofi"))
        assertTrue("expected lofi first, got $genres", genres.first() == "lofi")
    }

    @Test
    fun `genre extraction is capped`() {
        val genres = resolver.extractGenres(
            tags = listOf("pop", "rock", "jazz", "rnb", "dance"),
            maxTokens = 2
        )
        assertEquals(2, genres.size)
    }

    @Test
    fun `no tags yields no genres`() {
        assertTrue(resolver.extractGenres(tags = emptyList(), title = "").isEmpty())
    }

    // -----------------------------------------------------------------
    // Query fragments
    // -----------------------------------------------------------------

    @Test
    fun `language query fragment omits english and unknown languages`() {
        assertEquals("tamil", resolver.languageQueryFragment("tamil"))
        assertNull(resolver.languageQueryFragment("english"))
        assertNull(resolver.languageQueryFragment(null))
        assertNull(resolver.languageQueryFragment(""))
    }

    // -----------------------------------------------------------------
    // Channel names
    // -----------------------------------------------------------------

    @Test
    fun `channel style uploaders are flagged`() {
        assertTrue(resolver.isLikelyChannelName("SonyMusicVines"))
        assertTrue(resolver.isLikelyChannelName("T-Series Records"))
        assertTrue(resolver.isLikelyChannelName("Rhythm Official"))
        assertFalse(resolver.isLikelyChannelName("Arijit Singh"))
    }

    // -----------------------------------------------------------------
    // Integration with the shared DescriptionMetadataParser
    // -----------------------------------------------------------------

    @Test
    fun `provided to youtube description yields song title and singers`() {
        val description = """
            Provided to YouTube by
            Kesariya · Arijit Singh, Pritam

            Kesariya
            Auto-generated by YouTube
        """.trimIndent()

        val meta = DescriptionMetadataParser().parseProvidedToYouTubeDescription(description)
        assertNotNull(meta)
        assertEquals("Kesariya", meta?.title)
        assertTrue(meta?.singers?.contains("Arijit Singh") == true)
    }

    @Test
    fun `label style description yields title singers and album`() {
        val description = """
            Song: Kesariya
            Artist: Arijit Singh
            Album: Brahmastra
        """.trimIndent()

        val meta = resolver.resolveMetadata(description)
        assertEquals("Kesariya", meta.title)
        assertEquals("Arijit Singh", meta.artist)
        assertEquals("Brahmastra", meta.movie)
    }

    @Test
    fun `boilerplate album lines are rejected`() {
        assertNull(
            DescriptionMetadataParser().parseProvidedToYouTubeDescription(
                """
                Provided to YouTube by
                Kesariya

                Auto-generated by YouTube
                """.trimIndent()
            )?.movie
        )
    }
}
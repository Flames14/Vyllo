package com.vyllo.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression: Google's dict-chrome-ex responds with nested per-chunk pairs;
 * the old parser called getString(0) and always threw.
 */
class TranslationEngineParseTest {

    @Test
    fun `nested chunk pairs are concatenated and detected language kept`() {
        val result = TranslationEngine.parseGoogleResponse(
            """[["Line one\n","pt"],["Line two","pt"]]"""
        )

        assertEquals("Line one\nLine two", result?.first)
        assertEquals("pt", result?.second)
    }

    @Test
    fun `nested pair uses first chunk language for detection`() {
        val result = TranslationEngine.parseGoogleResponse("""[["Hello","id"],["world","en"]]""")

        assertEquals("Helloworld", result?.first)
        assertEquals("id", result?.second)
    }

    @Test
    fun `flat pair shape still parses`() {
        val result = TranslationEngine.parseGoogleResponse("""["Bonjour","fr"]""")

        assertEquals("Bonjour", result?.first)
        assertEquals("fr", result?.second)
    }

    @Test
    fun `empty unusable and malformed payloads return null`() {
        assertNull(TranslationEngine.parseGoogleResponse("[]"))
        assertNull(TranslationEngine.parseGoogleResponse("""[["","pt"]]"""))
        assertNull(TranslationEngine.parseGoogleResponse("not-json"))
    }
}

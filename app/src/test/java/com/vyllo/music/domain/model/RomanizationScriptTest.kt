package com.vyllo.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomanizationScriptTest {

    @Test
    fun `detects independent scripts on mixed language lines`() {
        assertEquals("ja-t-i0-und", RomanizationScript.detect("こんにちは")?.itc)
        assertEquals("ru-t-i0-und", RomanizationScript.detect("Привет")?.itc)
    }

    @Test
    fun `does not romanize Latin text or a small non Latin fragment`() {
        assertNull(RomanizationScript.detect("Hello world"))
        assertNull(RomanizationScript.detect("Hello 好"))
    }
}

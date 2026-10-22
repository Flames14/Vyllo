package com.vyllo.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsStyleTest {

    @Test
    fun `percent conversion clamps to the supported range`() {
        assertEquals(0.8f, LyricsStyle.scaleFromPercent(10), 0.0001f)
        assertEquals(1.6f, LyricsStyle.scaleFromPercent(500), 0.0001f)
        assertEquals(1f, LyricsStyle.scaleFromPercent(100), 0.0001f)
        assertEquals(0.8f, LyricsStyle.spacingFromPercent(0), 0.0001f)
        assertEquals(1.8f, LyricsStyle.spacingFromPercent(999), 0.0001f)
    }

    @Test
    fun `percentOf rounds to the nearest percent`() {
        assertEquals(100, LyricsStyle.percentOf(1f))
        assertEquals(125, LyricsStyle.percentOf(1.25f))
        assertEquals(180, LyricsStyle.percentOf(1.8f))
    }

    @Test
    fun `defaults are neutral`() {
        val style = LyricsStyle()
        assertEquals(1f, style.scale, 0f)
        assertEquals(1f, style.lineSpacing, 0f)
        assertEquals(false, style.alignStart)
    }
}

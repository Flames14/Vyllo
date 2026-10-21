package com.vyllo.music.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRateFormatTest {

    @Test
    fun `whole speeds drop the decimal`() {
        assertEquals("1x", PlaybackRateFormat.speedLabel(1.0f))
        assertEquals("2x", PlaybackRateFormat.speedLabel(2.0f))
    }

    @Test
    fun `fractional speeds keep up to two decimals without trailing zeros`() {
        assertEquals("0.5x", PlaybackRateFormat.speedLabel(0.5f))
        assertEquals("0.75x", PlaybackRateFormat.speedLabel(0.75f))
        assertEquals("1.25x", PlaybackRateFormat.speedLabel(1.25f))
    }

    @Test
    fun `invalid speed falls back to one x`() {
        assertEquals("1x", PlaybackRateFormat.speedLabel(0f))
        assertEquals("1x", PlaybackRateFormat.speedLabel(Float.NaN))
    }

    @Test
    fun `pitch is shown as a percentage`() {
        assertEquals("100%", PlaybackRateFormat.pitchLabel(1.0f))
        assertEquals("85%", PlaybackRateFormat.pitchLabel(0.85f))
        assertEquals("150%", PlaybackRateFormat.pitchLabel(1.5f))
    }

    @Test
    fun `snapPitch rounds to five percent steps and clamps`() {
        assertEquals(1.0f, PlaybackRateFormat.snapPitch(1.02f), 0.0001f)
        assertEquals(0.85f, PlaybackRateFormat.snapPitch(0.87f), 0.0001f)
        assertEquals(0.5f, PlaybackRateFormat.snapPitch(0.1f), 0.0001f)
        assertEquals(2.0f, PlaybackRateFormat.snapPitch(3f), 0.0001f)
    }
}

package com.vyllo.music.service.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class FadeMathTest {

    @Test
    fun `no fade window leaves gain untouched`() {
        assertEquals(1f, FadeMath.gain(0L, 0L), 0.0001f)
        assertEquals(1f, FadeMath.gain(5_000L, -1L), 0.0001f)
    }

    @Test
    fun `outside the window gain is full`() {
        assertEquals(1f, FadeMath.gain(10_000L, 3_000L), 0.0001f)
        assertEquals(1f, FadeMath.gain(3_000L, 3_000L), 0.0001f)
    }

    @Test
    fun `halfway through the window gain is half`() {
        assertEquals(0.5f, FadeMath.gain(1_500L, 3_000L), 0.0001f)
    }

    @Test
    fun `end of track is silent`() {
        assertEquals(0f, FadeMath.gain(0L, 3_000L), 0.0001f)
        assertEquals(0f, FadeMath.gain(-250L, 3_000L), 0.0001f)
    }
}

package com.vyllo.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CenterCancelTest {

    @Test
    fun `centered content cancels`() {
        assertEquals(0, CenterCancel.sideSample(12_000, 12_000).toInt())
        assertEquals(0, CenterCancel.sideSample(-12_000, -12_000).toInt())
    }

    @Test
    fun `stereo side difference is centered in both output channels`() {
        assertEquals(6_000, CenterCancel.sideSample(12_000, 0).toInt())
        assertEquals(-6_000, CenterCancel.sideSample(0, 12_000).toInt())
        assertEquals(12_000, CenterCancel.sideSample(12_000, -12_000).toInt())
    }

    @Test
    fun `extreme difference is clipped to signed pcm`() {
        assertEquals(Short.MAX_VALUE.toInt(), CenterCancel.sideSample(Short.MAX_VALUE.toInt(), Short.MIN_VALUE.toInt()).toInt())
        assertEquals(-Short.MAX_VALUE.toInt(), CenterCancel.sideSample(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toInt())
    }
}

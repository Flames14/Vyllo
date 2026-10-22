package com.vyllo.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerCurveTest {

    @Test
    fun `flat bands stay on the center line`() {
        val samples = EqualizerCurve.levels(List(5) { 0 })
        assertTrue(samples.all { it == 0f })
    }

    @Test
    fun `edges keep the first and last band values`() {
        val samples = EqualizerCurve.levels(listOf(1000, 0, 0, 0, -1000), samples = 5)
        assertEquals(1f, samples.first(), 0.0001f)
        assertEquals(-1f, samples.last(), 0.0001f)
    }

    @Test
    fun `interpolation is linear between bands`() {
        val samples = EqualizerCurve.levels(listOf(0, 1000), samples = 3)
        assertEquals(0f, samples[0], 0.0001f)
        assertEquals(0.5f, samples[1], 0.0001f)
        assertEquals(1f, samples[2], 0.0001f)
    }

    @Test
    fun `out of range levels are clamped and samples are monotonic`() {
        val samples = EqualizerCurve.levels(listOf(-5000, 5000, 0))
        assertTrue(samples.first() >= -1f && samples.first() <= 1f)
        val rising = samples.take(samples.size / 2)
        assertTrue(rising.zipWithNext().all { (a, b) -> b >= a - 0.0001f })
    }

    @Test
    fun `empty levels produce a flat curve`() {
        val samples = EqualizerCurve.levels(emptyList(), samples = 4)
        assertEquals(4, samples.size)
        assertTrue(samples.all { it == 0f })
    }
}

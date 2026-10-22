package com.vyllo.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEqProfileTest {

    private val profile = AutoEqProfile(
        name = "Test",
        filters = listOf(ParametricFilter("PK", 100f, 6f, 1f))
    )

    @Test
    fun `peaking filter peaks at its center frequency`() {
        val atCenter = profile.gainAt(100f)
        assertEquals(6f, atCenter, 0.3f)
        assertTrue(profile.gainAt(1_000f) < atCenter)
    }

    @Test
    fun `preamp shifts the whole curve`() {
        val withPreamp = profile.copy(preampDb = -3f)
        assertEquals(profile.gainAt(100f) - 3f, withPreamp.gainAt(100f), 0.001f)
    }

    @Test
    fun `band levels are millibels and clamped`() {
        val levels = AutoEqProfile(
            name = "Loud",
            preampDb = 30f,
            filters = listOf(ParametricFilter("PK", 1_000f, 30f, 1f))
        ).toBandLevels()
        assertEquals(EqualizerSettings.DEFAULT_BANDS.size, levels.size)
        assertTrue(levels.all { it in EqualizerSettings.BAND_LEVEL_MIN..EqualizerSettings.BAND_LEVEL_MAX })
    }
}

class AutoEqParserTest {

    @Test
    fun `parses a parametric export`() {
        val text = """
            Preamp: -6.2 dB
            Filter 1: ON PK Fc 20 Hz Gain 5.0 dB Q 0.70
            Filter 2: ON PK Fc 250 Hz Gain -3.5 dB Q 1.20
            Filter 3: ON LSC Fc 105 Hz Gain 2.1 dB
        """.trimIndent()

        val profile = AutoEqParser.parse(text, "HD 600")
        assertNotNull(profile)
        profile!!
        assertEquals("HD 600", profile.name)
        assertEquals(-6.2f, profile.preampDb, 0.001f)
        assertEquals(3, profile.filters.size)
        assertEquals(20f, profile.filters[0].frequencyHz, 0.001f)
        assertEquals(0.7f, profile.filters[0].q, 0.001f)
    }

    @Test
    fun `parses a csv response table`() {
        val csv = """
            frequency,raw,smoothed,error,equalization
            20,1.2,1.0,4.0,4.0
            100,0.2,0.1,2.5,2.5
            1000,-0.1,0.0,0.0,0.0
            10000,-0.6,-0.5,-2.0,-2.0
        """.trimIndent()

        val profile = AutoEqParser.parse(csv)
        assertNotNull(profile)
        assertEquals(4, profile!!.curve.size)
        assertEquals(4f, profile.gainAt(20f), 0.001f)
        assertEquals(-2f, profile.gainAt(20_000f), 0.001f)
    }

    @Test
    fun `garbage input returns null`() {
        assertNull(AutoEqParser.parse(""))
        assertNull(AutoEqParser.parse("hello world"))
        assertNull(AutoEqParser.parse("frequency,gain\n-5,0"))
    }

    @Test
    fun `malformed filter lines are skipped, valid ones kept`() {
        val text = """
            Filter 1: ON PK Fc abc Hz Gain 3.0 dB Q 1.0
            Filter 2: ON PK Fc 500 Hz Gain 2.0 dB Q 1.0
        """.trimIndent()
        val profile = AutoEqParser.parse(text)
        assertNotNull(profile)
        assertEquals(1, profile!!.filters.size)
    }
}

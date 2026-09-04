package com.vyllo.music.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Equalizer presets and model sanitization.
 */
class EqualizerPresetsTest {

    @Test
    fun `preset count and names are valid`() {
        assertEquals(8, EqualizerSettings.PRESETS.size)
        val names = EqualizerSettings.PRESETS.map { it.name }
        assertTrue(names.contains("Flat"))
        assertTrue(names.contains("Bass Boost"))
        assertTrue(names.contains("Rock"))
        assertTrue(names.contains("Pop"))
        assertTrue(names.contains("Jazz"))
        assertTrue(names.contains("Electronic"))
        assertTrue(names.contains("Vocal"))
        assertTrue(names.contains("Classical"))
    }

    @Test
    fun `applyTo updates equalizer bands and strength correctly`() {
        val initial = EqualizerSettings(enabled = false)
        val rockPreset = EqualizerSettings.PRESETS.first { it.name == "Rock" }

        val applied = rockPreset.applyTo(initial)
        assertEquals(300, applied.bassBoostStrength)
        assertEquals(200, applied.virtualizerStrength)
        assertEquals(listOf(500, 300, -100, 200, 500), applied.bands.map { it.level })
    }

    @Test
    fun `matchesPreset returns true for applied preset`() {
        val initial = EqualizerSettings(enabled = true)
        val bassBoostPreset = EqualizerSettings.PRESETS.first { it.name == "Bass Boost" }

        val applied = bassBoostPreset.applyTo(initial)
        assertTrue(applied.matchesPreset(bassBoostPreset))

        val modified = applied.copy(bassBoostStrength = 100)
        assertFalse(modified.matchesPreset(bassBoostPreset))
    }

    @Test
    fun `sanitized coerces extreme band levels within limits`() {
        val outOfBounds = EqualizerSettings(
            bassBoostStrength = 5000,
            virtualizerStrength = -500,
            bands = listOf(
                EqualizerBandSetting("Bass", 9999),
                EqualizerBandSetting("Low Mid", -9999),
                EqualizerBandSetting("Mid", 0),
                EqualizerBandSetting("Upper Mid", 500),
                EqualizerBandSetting("Treble", 1500)
            )
        )

        val sanitized = outOfBounds.sanitized()
        assertEquals(EqualizerSettings.STRENGTH_MAX, sanitized.bassBoostStrength)
        assertEquals(EqualizerSettings.STRENGTH_MIN, sanitized.virtualizerStrength)
        assertEquals(EqualizerSettings.BAND_LEVEL_MAX, sanitized.bands[0].level)
        assertEquals(EqualizerSettings.BAND_LEVEL_MIN, sanitized.bands[1].level)
        assertEquals(0, sanitized.bands[2].level)
        assertEquals(500, sanitized.bands[3].level)
        assertEquals(EqualizerSettings.BAND_LEVEL_MAX, sanitized.bands[4].level)
    }
}

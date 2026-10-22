package com.vyllo.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerPresetCodecTest {

    private val preset = EqualizerPreset(
        name = "My Bass",
        bassBoostStrength = 600,
        virtualizerStrength = 200,
        bandLevels = listOf(500, 300, 0, -100, -200)
    )

    @Test
    fun `round trip preserves the preset`() {
        val decoded = EqualizerPresetCodec.decode(EqualizerPresetCodec.encode(listOf(preset)))
        assertEquals(1, decoded.size)
        assertEquals(preset, decoded.first())
    }

    @Test
    fun `malformed payload decodes to an empty list`() {
        assertTrue(EqualizerPresetCodec.decode("not json").isEmpty())
        assertTrue(EqualizerPresetCodec.decode(null).isEmpty())
        assertTrue(EqualizerPresetCodec.decode("").isEmpty())
    }

    @Test
    fun `entries without a name are skipped and duplicates collapse`() {
        val raw = """[
            {"name":"","bands":[0,0,0,0,0]},
            {"name":"Live","bass":100,"virtualizer":0,"bands":[100,0,0,0,0]},
            {"name":"live","bass":100,"virtualizer":0,"bands":[100,0,0,0,0]}
        ]"""
        val decoded = EqualizerPresetCodec.decode(raw)
        assertEquals(1, decoded.size)
        assertEquals("Live", decoded.first().name)
    }

    @Test
    fun `out of range levels are clamped on decode`() {
        val raw = """[{"name":"Extreme","bass":99999,"virtualizer":-50,"bands":[5000,-5000]}]"""
        val decoded = EqualizerPresetCodec.decode(raw).single()
        assertEquals(EqualizerSettings.STRENGTH_MAX, decoded.bassBoostStrength)
        assertEquals(EqualizerSettings.STRENGTH_MIN, decoded.virtualizerStrength)
        assertEquals(EqualizerSettings.BAND_LEVEL_MAX, decoded.bandLevels[0])
        assertEquals(EqualizerSettings.BAND_LEVEL_MIN, decoded.bandLevels[1])
        assertEquals(EqualizerSettings.DEFAULT_BANDS.size, decoded.bandLevels.size)
    }

    @Test
    fun `settings snapshot uses sanitized values`() {
        val settings = EqualizerSettings(
            bassBoostStrength = 2000,
            virtualizerStrength = -10,
            bands = EqualizerSettings.DEFAULT_BANDS.map { it.copy(level = 5000) }
        )
        val snapshot = EqualizerPresetCodec.fromSettings("  Snap  ", settings)
        assertEquals("Snap", snapshot.name)
        assertEquals(EqualizerSettings.STRENGTH_MAX, snapshot.bassBoostStrength)
        assertEquals(EqualizerSettings.STRENGTH_MIN, snapshot.virtualizerStrength)
        assertTrue(snapshot.bandLevels.all { it == EqualizerSettings.BAND_LEVEL_MAX })
    }
}

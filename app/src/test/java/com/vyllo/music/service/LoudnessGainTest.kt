package com.vyllo.music.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LoudnessGainTest {

    @Test
    fun `silence reports the floor`() {
        assertEquals(LoudnessGain.SILENCE_FLOOR_DB, LoudnessGain.rmsDb(ShortArray(64), 64), 0.001)
        assertEquals(LoudnessGain.SILENCE_FLOOR_DB, LoudnessGain.rmsDb(ShortArray(0), 0), 0.001)
    }

    @Test
    fun `full scale square wave is about zero dbfs`() {
        val samples = ShortArray(100) { if (it % 2 == 0) Short.MAX_VALUE else Short.MIN_VALUE }
        val db = LoudnessGain.rmsDb(samples, samples.size)
        assertTrue("expected ~0 dBFS, got $db", abs(db) < 0.1)
    }

    @Test
    fun `gain boosts a quiet block up to the ceiling`() {
        // -30 dBFS toward -15 needs 5.6x; the +12 dB ceiling caps it at 4x.
        assertEquals(LoudnessGain.MAX_GAIN, LoudnessGain.gainFor(-30.0, -15.0), 0.0001f)
        // -18 dBFS toward -15 is a modest 1.41x, under the ceiling.
        assertEquals(1.4125f, LoudnessGain.gainFor(-18.0, -15.0), 0.01f)
    }

    @Test
    fun `gain leaves a track at target alone`() {
        assertEquals(1f, LoudnessGain.gainFor(-15.0, -15.0), 0.0001f)
    }

    @Test
    fun `gain is clamped for very loud input`() {
        assertEquals(LoudnessGain.MIN_GAIN, LoudnessGain.gainFor(0.0, -15.0), 0.0001f)
    }

    @Test
    fun `smoothing moves a fraction of the way`() {
        assertEquals(1.5f, LoudnessGain.smooth(1f, 2f, 0.5f), 0.0001f)
        assertEquals(1f, LoudnessGain.smooth(1f, 2f, 0f), 0.0001f)
        assertEquals(2f, LoudnessGain.smooth(1f, 2f, 1f), 0.0001f)
        // Out-of-range factors are clamped, never extrapolated.
        assertEquals(1f, LoudnessGain.smooth(1f, 2f, -3f), 0.0001f)
    }

    @Test
    fun `limiter keeps quiet samples and bounds loud ones`() {
        assertEquals(0.5f, LoudnessGain.limit(0.5f), 0.0001f)
        assertTrue(abs(LoudnessGain.limit(4f)) <= 1f)
        assertTrue(LoudnessGain.limit(-4f) > -1f)
    }

    @Test
    fun `preset lookup maps names and rejects off`() {
        assertEquals(LoudnessNormalizationAudioProcessor.BALANCED, LoudnessNormalizationAudioProcessor.targetFor("Balanced")!!, 0.001)
        assertEquals(null, LoudnessNormalizationAudioProcessor.targetFor("Off"))
        assertEquals(null, LoudnessNormalizationAudioProcessor.targetFor("nonsense"))
    }
}

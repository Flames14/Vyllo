package com.vyllo.music.service

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Pure gain maths behind [LoudnessNormalizationAudioProcessor], kept separate so
 * the level/clamp/smooth rules can be unit-tested without an audio pipeline.
 */
object LoudnessGain {

    const val MIN_GAIN = 0.25f
    const val MAX_GAIN = 4.0f

    /** Below this the block is treated as silence and the gain is held. */
    const val SILENCE_FLOOR_DB = -50.0

    /** Root-mean-square level of 16-bit PCM [samples] in dBFS. */
    fun rmsDb(samples: ShortArray, count: Int): Double {
        if (count <= 0) return SILENCE_FLOOR_DB
        var sum = 0.0
        for (i in 0 until count) {
            val normalized = samples[i] / 32768.0
            sum += normalized * normalized
        }
        val rms = kotlin.math.sqrt(sum / count)
        if (rms <= 1e-9) return SILENCE_FLOOR_DB
        return 20.0 * log10(rms)
    }

    /**
     * Linear gain that brings [rmsDb] to [targetDb], clamped so a very quiet
     * track cannot be blown up into noise and a loud one cannot be crushed.
     */
    fun gainFor(rmsDb: Double, targetDb: Double): Float {
        val gain = 10.0.pow((targetDb - rmsDb) / 20.0).toFloat()
        return gain.coerceIn(MIN_GAIN, MAX_GAIN)
    }

    /** One-pole smoothing so the gain never steps audibly between blocks. */
    fun smooth(current: Float, target: Float, factor: Float): Float {
        val alpha = factor.coerceIn(0f, 1f)
        return current + (target - current) * alpha
    }

    /** Soft-knee limiter, same curve as the volume booster. */
    fun limit(sample: Float): Float {
        val knee = 0.70f
        val headroom = 1.0f - knee
        val peakLimit = 0.985f
        val magnitude = abs(sample)
        if (magnitude <= knee) return sample
        val saturated = knee + headroom * tanh(((magnitude - knee) / headroom).toDouble()).toFloat()
        return (if (sample >= 0f) saturated else -saturated) * peakLimit
    }
}

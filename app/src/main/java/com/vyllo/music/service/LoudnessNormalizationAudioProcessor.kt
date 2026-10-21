package com.vyllo.music.service

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * RMS-based loudness normalizer: it measures each block, moves the gain toward
 * the preset's target level with a one-pole smoother, and soft-limits the
 * result so boosting a quiet block cannot clip the DAC.
 *
 * This is deliberately not a K-weighted LUFS meter (that needs per-block filter
 * state and gating); RMS on 16-bit PCM is what the platform exposes cheaply per
 * buffer and matches the audible "these tracks are at the same level" goal.
 */
@UnstableApi
class LoudnessNormalizationAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    @Volatile
    var targetRmsDb: Double = BALANCED

    private var gain = 1f
    private var cachedSamples = ShortArray(0)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        gain = 1f
        return inputAudioFormat
    }

    override fun onQueueEndOfStream() {
        gain = 1f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val size = inputBuffer.remaining()

        if (!enabled) {
            val passthrough = replaceOutputBuffer(size)
            passthrough.put(inputBuffer)
            passthrough.flip()
            return
        }

        val output = replaceOutputBuffer(size)
        val numSamples = size / 2
        if (cachedSamples.size < numSamples) {
            cachedSamples = ShortArray(numSamples)
        }

        val shortBuffer = inputBuffer.asShortBuffer()
        shortBuffer.get(cachedSamples, 0, numSamples)
        inputBuffer.position(inputBuffer.position() + size)

        val measured = LoudnessGain.rmsDb(cachedSamples, numSamples)
        if (measured > LoudnessGain.SILENCE_FLOOR_DB) {
            val desired = LoudnessGain.gainFor(measured, targetRmsDb)
            // ~20 ms blocks at 44.1 kHz => 0.08 settles over roughly a second.
            gain = LoudnessGain.smooth(gain, desired, 0.08f)
        }

        for (i in 0 until numSamples) {
            val sample = cachedSamples[i] / 32768f * gain
            val limited = LoudnessGain.limit(sample)
            val pcm = (limited * 32767f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output.putShort(pcm.toShort())
        }
        output.flip()
    }

    companion object {
        const val AGGRESSIVE = -9.0
        const val LOUD = -12.0
        const val BALANCED = -15.0
        const val QUIET = -18.0

        /** Preset name -> target RMS in dBFS; unknown or "Off" disables it. */
        fun targetFor(preset: String): Double? = when (preset) {
            "Aggressive" -> AGGRESSIVE
            "Loud" -> LOUD
            "Balanced" -> BALANCED
            "Quiet" -> QUIET
            else -> null
        }
    }
}

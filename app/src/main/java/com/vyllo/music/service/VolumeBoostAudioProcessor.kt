package com.vyllo.music.service

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.C
import java.nio.ByteBuffer

@androidx.media3.common.util.UnstableApi
class VolumeBoostAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var volumeMultiplier: Float = 1.0f

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    private var cachedSamples = ShortArray(0)

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) {
            return
        }

        val size = inputBuffer.remaining()
        val outputBuffer = replaceOutputBuffer(size)

        val numSamples = size / 2
        if (cachedSamples.size < numSamples) {
            cachedSamples = ShortArray(numSamples)
        }

        val shortBuffer = inputBuffer.asShortBuffer()
        shortBuffer.get(cachedSamples, 0, numSamples)
        inputBuffer.position(inputBuffer.position() + size)

        val actualMultiplier = volumeMultiplier
        
        if (actualMultiplier <= 1.0f) {
            for (i in 0 until numSamples) {
                outputBuffer.putShort(cachedSamples[i])
            }
        } else {
            // Studio-grade soft-knee tanh compressor & true-peak limiter
            val kneeThreshold = 0.70f
            val headroom = 1.0f - kneeThreshold
            val peakLimit = 0.985f // -0.15 dBFS true-peak guard against DAC clipping
            val scale = 32767.0f

            for (i in 0 until numSamples) {
                val sample = cachedSamples[i].toFloat() / scale
                val boosted = sample * actualMultiplier
                val absBoosted = kotlin.math.abs(boosted)

                val outSample = if (absBoosted <= kneeThreshold) {
                    boosted
                } else {
                    val delta = absBoosted - kneeThreshold
                    val saturated = kneeThreshold + headroom * kotlin.math.tanh(delta / headroom.toDouble()).toFloat()
                    val sign = if (boosted >= 0f) 1.0f else -1.0f
                    sign * saturated * peakLimit
                }

                val finalShort = (outSample * scale).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                outputBuffer.putShort(finalShort.toShort())
            }
        }

        outputBuffer.flip()
    }
}

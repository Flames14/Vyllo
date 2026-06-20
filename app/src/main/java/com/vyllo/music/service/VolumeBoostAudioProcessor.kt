package com.vyllo.music.service

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.C
import java.nio.ByteBuffer

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
        
        if (actualMultiplier == 1.0f) {
            for (i in 0 until numSamples) {
                outputBuffer.putShort(cachedSamples[i])
            }
        } else {
            for (i in 0 until numSamples) {
                val original = cachedSamples[i].toInt()
                
                val f = original / 32768f
                val fBoosted = f * actualMultiplier
                
                // Fast approximation for soft clipping: x / (1 + |x|)
                val fOut = fBoosted / (1.0f + kotlin.math.abs(fBoosted))
                
                var boosted = (fOut * 32768f).toInt()
                
                if (boosted > Short.MAX_VALUE) {
                    boosted = Short.MAX_VALUE.toInt()
                } else if (boosted < Short.MIN_VALUE) {
                    boosted = Short.MIN_VALUE.toInt()
                }
                
                outputBuffer.putShort(boosted.toShort())
            }
        }

        outputBuffer.flip()
    }
}

package com.vyllo.music.recognition.data

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Utility to resample PCM audio using Media3 SonicAudioProcessor.
 */
@OptIn(UnstableApi::class)
object AudioResampler {

    /**
     * Data class to wrap decoded audio data and its properties.
     */
    data class DecodedAudio(
        val data: ByteArray,
        val channelCount: Int,
        val sampleRate: Int,
        val pcmEncoding: Int
    )

    /**
     * Resamples audio to a target sample rate using Sonic for high-fidelity conversion.
     */
    suspend fun resample(
        decodedAudio: DecodedAudio,
        targetSampleRate: Int
    ): Result<DecodedAudio> = withContext(Dispatchers.Default) {
        if (decodedAudio.sampleRate == targetSampleRate) {
            return@withContext Result.success(decodedAudio)
        }
        
        var sonicRef: AudioProcessor? = null
        try {
            val sonic: AudioProcessor = SonicAudioProcessor().apply {
                setOutputSampleRateHz(targetSampleRate)
            }
            sonicRef = sonic
            
            val inputFormat = AudioProcessor.AudioFormat(
                decodedAudio.sampleRate,
                decodedAudio.channelCount,
                decodedAudio.pcmEncoding
            )
            val outputFormat = sonic.configure(inputFormat)
            sonic.flush()

            val inputBuf = ByteBuffer.wrap(decodedAudio.data).order(ByteOrder.LITTLE_ENDIAN)
            sonic.queueInput(inputBuf)
            sonic.queueEndOfStream()

            val outputChunks = mutableListOf<ByteArray>()
            var outputChunksByteSize = 0

            while (!sonic.isEnded) {
                ensureActive()
                val outputBuffer = sonic.output
                if (!outputBuffer.hasRemaining()) continue
                val chunk = ByteArray(outputBuffer.remaining())
                outputBuffer.get(chunk)
                outputChunks.add(chunk)
                outputChunksByteSize += chunk.size
            }
            sonic.reset()

            val resampledData = if (outputChunks.size == 1) {
                outputChunks[0]
            } else {
                ByteArray(outputChunksByteSize).also {
                    var dest = 0
                    for (chunk in outputChunks) {
                        System.arraycopy(chunk, 0, it, dest, chunk.size)
                        dest += chunk.size
                    }
                }
            }
            
            Result.success(DecodedAudio(
                data = resampledData,
                channelCount = outputFormat.channelCount,
                sampleRate = outputFormat.sampleRate,
                pcmEncoding = outputFormat.encoding,
            ))
        } catch (e: Exception) {
            ensureActive()
            Result.failure(e)
        } finally {
            sonicRef?.reset()
        }
    }
}

package com.vyllo.music.service

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import com.vyllo.music.domain.model.CenterCancel
import java.nio.ByteBuffer

/** Removes centered content from stereo PCM by emitting (L-R)/2 to both channels. */
@androidx.media3.common.util.UnstableApi
class CenterCancelAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    private var channelCount = 0
    private var scratchSamples = ShortArray(0)
    private var scratchBytes = ByteArray(0)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channelCount = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val byteCount = inputBuffer.remaining()
        // Keep an independent view before asking BaseAudioProcessor for its
        // output buffer. Media3 may hand us that very same buffer as input.
        val source = inputBuffer.duplicate().order(inputBuffer.order())
        val processCenter = enabled && channelCount == 2
        val frameCount = if (processCenter) source.remaining() / 4 else 0
        val samples = if (processCenter) source.asShortBuffer() else null
        if (processCenter && scratchSamples.size < frameCount * 2) {
            scratchSamples = ShortArray(frameCount * 2)
        }
        val outputSamples = if (processCenter) scratchSamples else null
        if (samples != null && outputSamples != null) {
            repeat(frameCount) { frame ->
                val left = samples.get().toInt()
                val right = samples.get().toInt()
                val side = CenterCancel.sideSample(left, right)
                outputSamples[frame * 2] = side
                outputSamples[frame * 2 + 1] = side
            }
        }
        val output = replaceOutputBuffer(byteCount)
        if (!processCenter) {
            if (output === inputBuffer) {
                // ByteBuffer.put(buffer) rejects an object copied into itself.
                if (scratchBytes.size < byteCount) scratchBytes = ByteArray(byteCount)
                source.get(scratchBytes, 0, byteCount)
                output.put(scratchBytes, 0, byteCount)
            } else {
                output.put(inputBuffer)
            }
            output.flip()
            return
        }

        repeat(frameCount * 2) { index -> output.putShort(outputSamples!![index]) }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }
}

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

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channelCount = inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val byteCount = inputBuffer.remaining()
        val output = replaceOutputBuffer(byteCount)
        if (!enabled || channelCount != 2) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        val samples = inputBuffer.asShortBuffer()
        val frameCount = samples.remaining() / 2
        repeat(frameCount) {
            val left = samples.get().toInt()
            val right = samples.get().toInt()
            val side = CenterCancel.sideSample(left, right)
            output.putShort(side)
            output.putShort(side)
        }
        inputBuffer.position(inputBuffer.position() + byteCount)
        output.flip()
    }
}

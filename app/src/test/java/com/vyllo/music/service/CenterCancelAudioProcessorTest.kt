package com.vyllo.music.service

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CenterCancelAudioProcessorTest {

    @Test
    fun `disabled processor preserves pcm samples`() {
        val processor = configuredProcessor(enabled = false)
        val input = pcm(12_000, -8_000, 3_000, 1_000)

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        assertEquals(4, output.remaining())
        assertEquals(12_000, output.get().toInt())
        assertEquals(-8_000, output.get().toInt())
        assertEquals(3_000, output.get().toInt())
        assertEquals(1_000, output.get().toInt())
        assertFalse(input.hasRemaining())
    }

    @Test
    fun `enabled processor removes center and duplicates side signal`() {
        val processor = configuredProcessor(enabled = true)
        val input = pcm(12_000, 12_000, 12_000, 0)

        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        assertEquals(0, output.get().toInt())
        assertEquals(0, output.get().toInt())
        assertEquals(6_000, output.get().toInt())
        assertEquals(6_000, output.get().toInt())
    }

    @Test
    fun `enabled processor writes only samples for the current smaller buffer`() {
        val processor = configuredProcessor(enabled = true)

        processor.queueInput(pcm(8_000, 4_000, 6_000, 2_000, 4_000, 0, 2_000, -2_000))
        assertEquals(8, processor.output.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().remaining())

        processor.queueInput(pcm(10_000, 6_000, 2_000, -2_000))
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        assertEquals(4, output.remaining())
        assertEquals(2_000, output.get().toInt())
        assertEquals(2_000, output.get().toInt())
        assertEquals(2_000, output.get().toInt())
        assertEquals(2_000, output.get().toInt())
    }

    private fun configuredProcessor(enabled: Boolean): CenterCancelAudioProcessor =
        CenterCancelAudioProcessor().apply {
            configure(AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
            flush()
            this.enabled = enabled
        }

    private fun pcm(vararg values: Int): ByteBuffer =
        ByteBuffer.allocateDirect(values.size * Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                values.forEach { putShort(it.toShort()) }
                flip()
            }
}

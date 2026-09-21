package com.vyllo.music.service.audio

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.vyllo.music.service.VolumeBoostAudioProcessor

/**
 * Builds the DefaultRenderersFactory with the volume-boost audio processor.
 * Extracted from MusicService — no behavior change.
 */
object AudioSinkFactory {
    fun createRenderersFactory(
        context: Context,
        volumeBoostProcessor: VolumeBoostAudioProcessor
    ): DefaultRenderersFactory {
        return object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(volumeBoostProcessor))
                    .build()
            }
        }
    }
}

package com.vyllo.music.service.audio

import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.vyllo.music.service.VolumeBoostAudioProcessor

/**
 * Builds the DefaultRenderersFactory with the app's audio processing chain.
 * Extracted from MusicService — no behavior change beyond accepting the
 * processors from the caller so the service owns their configuration.
 */
@UnstableApi
object AudioSinkFactory {
    @UnstableApi
    fun createRenderersFactory(
        context: Context,
        volumeBoostProcessor: VolumeBoostAudioProcessor,
        extraProcessors: Array<AudioProcessor> = emptyArray()
    ): DefaultRenderersFactory {
        return object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf<AudioProcessor>(volumeBoostProcessor) + extraProcessors)
                    .build()
            }
        }
    }
}

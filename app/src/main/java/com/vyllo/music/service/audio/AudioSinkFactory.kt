package com.vyllo.music.service.audio

import android.content.Context
import android.os.Build
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.vyllo.music.service.VolumeBoostAudioProcessor

/**
 * Builds the DefaultRenderersFactory with the app's audio processing chain.
 * Extracted from MusicService - no behavior change beyond accepting the
 * processors from the caller so the service owns their configuration.
 *
 * [audioOffload] hands decoding to the device DSP when it can take the format
 * directly; the processors above are then bypassed for offloaded playback by
 * the platform, so the toggle is only a win for unprocessed playback.
 */
@UnstableApi
object AudioSinkFactory {
    @UnstableApi
    fun createRenderersFactory(
        context: Context,
        volumeBoostProcessor: VolumeBoostAudioProcessor,
        extraProcessors: Array<AudioProcessor> = emptyArray(),
        audioOffload: Boolean = false
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
                    .apply {
                        if (audioOffload && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // Offloaded output goes straight to the DSP, which
                            // means the processors above are skipped for those
                            // formats. Gapless is not required because a
                            // crossfade would not survive the hardware path.
                            setOffloadMode(AudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_NOT_REQUIRED)
                        }
                    }
            }
        }
    }
}

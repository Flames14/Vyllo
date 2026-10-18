package com.vyllo.music.service.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.OkHttpClient

/**
 * Builds the ExoPlayer stack (cache-backed data source, buffer tuning and the
 * player itself).
 *
 * Extracted from MusicService — no behavior change.
 */
object PlaybackPlayerFactory {

    /**
     * Wraps [okHttpClient] in a disk-cache-aware data source. `FLAG_IGNORE_CACHE_ON_ERROR`
     * means a corrupt cache entry falls back to the network instead of failing
     * playback outright.
     */
    fun createCacheDataSourceFactory(
        context: Context,
        okHttpClient: OkHttpClient
    ): CacheDataSource.Factory {
        val upstreamFactory = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(okHttpClient))
        return CacheDataSource.Factory()
            .setCache(CacheProvider.getSimpleCache(context))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Buffer tuning: a full 30s/60s window on capable devices, halved on low-RAM
     * devices to cut steady-state memory and radio bursts.
     *
     * bufferForPlaybackMs was once 1500ms, which starts playback fast but leaves
     * too little margin — any single rebuffer became an audible stall. 2500ms
     * costs ~125ms more start latency and removes that class of glitch; the
     * after-rebuffer target is larger still, because rebuffering is where the
     * audible gap actually happens.
     */
    private fun createLoadControl(lowRamPlayback: Boolean): DefaultLoadControl {
        return if (lowRamPlayback) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 30000, 2000, 4000)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(30000, 60000, 2500, 6000)
                .build()
        }
    }

    /**
     * @param keepAudioPlaying when true, audio focus is not reclaimed when the app
     *   loses it, so music keeps playing while another app holds focus.
     */
    fun buildPlayer(
        context: Context,
        cacheDataSourceFactory: CacheDataSource.Factory,
        renderersFactory: RenderersFactory,
        keepAudioPlaying: Boolean,
        lowRamPlayback: Boolean
    ): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(cacheDataSourceFactory))
            .setLoadControl(createLoadControl(lowRamPlayback))
            .setAudioAttributes(
                AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(),
                !keepAudioPlaying // Re-enable automatic audio focus if keepAudioPlaying is false
            )
            // WAKE_MODE_NETWORK keeps both the CPU and the Wi-Fi radio awake while
            // the screen is off so mid-track rebuffering never stalls (the failure
            // mode seen with WAKE_MODE_LOCAL during long screen-off sessions).
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
}

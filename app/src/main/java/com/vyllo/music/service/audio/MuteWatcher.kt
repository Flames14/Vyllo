package com.vyllo.music.service.audio

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Detects the media stream being muted while playback runs.
 *
 * Android exposes no public mute callback, so this observes the system
 * settings provider (which the volume keys write to) and re-reads the stream
 * volume. Only registered while the user has the option enabled, so apps that
 * never asked pay nothing.
 */
class MuteWatcher(
    context: Context,
    private val onMuted: () -> Unit
) {
    private val resolver = context.contentResolver
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) onMuted()
        }
    }

    fun start() {
        resolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
    }

    fun stop() {
        resolver.unregisterContentObserver(observer)
    }
}

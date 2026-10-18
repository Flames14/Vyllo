package com.vyllo.music.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import com.vyllo.music.MainActivity
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.core.utils.DevicePerf
import com.vyllo.music.data.manager.PlaybackAudioEffectsManager
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.data.manager.WakeLockManager
import com.vyllo.music.domain.manager.PlaybackErrorHandler
import com.vyllo.music.domain.manager.PlaybackQueueManager
import com.vyllo.music.domain.repository.IMusicRepository
import com.vyllo.music.service.audio.AudioSinkFactory
import com.vyllo.music.service.audio.CacheProvider
import com.vyllo.music.service.audio.MediaSessionCallback
import com.vyllo.music.service.audio.PlaybackHttpClientFactory
import com.vyllo.music.service.audio.PlaybackPlayerFactory
import com.vyllo.music.service.audio.PlayerErrorListener
import com.vyllo.music.service.audio.QueuePersistenceCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
@androidx.media3.common.util.UnstableApi
class MusicService : MediaSessionService() {

    @Inject
    lateinit var repository: IMusicRepository

    @Inject
    lateinit var preferenceManager: PreferenceManager

    @Inject
    lateinit var playbackQueueManager: PlaybackQueueManager

    @Inject
    lateinit var wakeLockManager: WakeLockManager

    @Inject
    lateinit var playbackQueueOrchestrator: PlaybackQueueOrchestrator

    @Inject
    lateinit var playbackAudioEffectsManager: PlaybackAudioEffectsManager

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    private val serviceJob = SupervisorJob()
    // Safety net: background queue work must never be able to crash the app.
    // Any stray exception from a transition/prefetch coroutine is logged here
    // instead of propagating as an uncaught exception.
    private val serviceScope = CoroutineScope(
        Dispatchers.Main + serviceJob +
            kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
                SecureLogger.e("MusicService", "Background playback task failed", throwable)
            }
    )

    private val volumeBoostProcessor = VolumeBoostAudioProcessor()

    // lazy: the constructor must not read the @Inject lateinit fields, which Hilt
    // only populates during super.onCreate().
    private val queuePersistence by lazy {
        QueuePersistenceCoordinator(playbackQueueManager, preferenceManager)
    }

    private val playbackErrorHandler = PlaybackErrorHandler(
        maxRetries = 3,
        baseDelayMs = 2_000L,
        maxDelayMs = 10_000L,
        // Retries are service-managed (see onPlayerError): the coroutine-based
        // path holds wake locks across the delay so recovery works with the
        // screen off. This Handler-based fallback is kept only for compat.
        onRetry = { player?.prepare(); player?.play() },
        onMaxRetriesReached = { SecureLogger.w("MusicService", "Playback stalled after max retries") }
    )

    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "keep_audio_playing_enabled") {
            val enabled = prefs.getBoolean(key, false)
            player?.setAudioAttributes(
                AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(),
                !enabled // false means keep playing when mic/camera is used
            )
        } else if (key == PreferenceManager.KEY_VOLUME_BOOST) {
            volumeBoostProcessor.volumeMultiplier = preferenceManager.volumeBoostMultiplier
        }
        playbackAudioEffectsManager.onPreferenceChanged(key)
    }

    companion object {
        fun getSimpleCache(context: android.content.Context): SimpleCache =
            CacheProvider.getSimpleCache(context)
    }

    override fun onCreate() {
        super.onCreate()

        val okHttpClient = PlaybackHttpClientFactory.create()
        val cacheDataSourceFactory = PlaybackPlayerFactory.createCacheDataSourceFactory(this, okHttpClient)

        val keepAudioPlaying = preferenceManager.isKeepAudioPlayingEnabled

        preferenceManager.registerOnPreferenceChangeListener(preferenceListener)
        volumeBoostProcessor.volumeMultiplier = preferenceManager.volumeBoostMultiplier

        val renderersFactory = AudioSinkFactory.createRenderersFactory(this, volumeBoostProcessor)

        player = PlaybackPlayerFactory.buildPlayer(
            context = this,
            cacheDataSourceFactory = cacheDataSourceFactory,
            renderersFactory = renderersFactory,
            keepAudioPlaying = keepAudioPlaying,
            lowRamPlayback = DevicePerf.isLowRamDevice(this)
        )

        player?.addListener(
            PlayerErrorListener(
                playerProvider = { player },
                serviceScope = serviceScope,
                playbackErrorHandler = playbackErrorHandler,
                playbackQueueOrchestrator = playbackQueueOrchestrator,
                playbackQueueManager = playbackQueueManager,
                playbackAudioEffectsManager = playbackAudioEffectsManager
            )
        )

        // Keep PlaybackQueueManager's physical order in sync with ExoPlayer shuffle
        // so sequential next/prev/lookahead follow the shuffled order.
        player?.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                playbackQueueManager.setShuffleEnabled(enabled)
            }
        })

        // Force 1.0x playback speed to prevent "super fast" playback bugs on some devices/emulators
        player?.playbackParameters = PlaybackParameters(1.0f)
        player?.audioSessionId?.let { playbackAudioEffectsManager.attachToAudioSession(it) }

        serviceScope.launch {
            playbackQueueManager.queueVersion.collectLatest {
                playbackQueueOrchestrator.prefetchUpcomingStreams(serviceScope, player)
            }
        }

        // Restore the last session's queue (process death / reboot) and persist
        // structural edits back to storage.
        queuePersistence.start(serviceScope)

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sessionCallback = MediaSessionCallback(
            playerProvider = { player },
            serviceScope = serviceScope,
            playbackQueueOrchestrator = playbackQueueOrchestrator
        )

        // Initialize MediaSession (player was assigned just above; fail soft if builder raced)
        val initializedPlayer = player
            ?: throw IllegalStateException("ExoPlayer not initialized before MediaSession build")
        mediaSession = MediaSession.Builder(this, initializedPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(sessionCallback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away must not kill background audio when the user
        // explicitly enabled background playback. Only tear down when the
        // setting is off; otherwise leave the foreground service running.
        if (!preferenceManager.isBackgroundPlaybackEnabled) {
            try {
                mediaSession?.player?.run {
                    playWhenReady = false
                    stop()
                }
                androidx.core.app.ServiceCompat.stopForeground(this, androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE)
            } catch (e: Exception) {
                SecureLogger.w("MusicService", "Error stopping service on task removed: ${e.message}")
            }
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        SecureLogger.d("MusicService", "Service onDestroy called")
        serviceJob.cancel()
        playbackErrorHandler.release()
        playbackAudioEffectsManager.release()
        preferenceManager.unregisterOnPreferenceChangeListener(preferenceListener)
        wakeLockManager.release()

        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

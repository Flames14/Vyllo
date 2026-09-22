package com.vyllo.music.service

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.*
import androidx.media3.common.Player
import com.vyllo.music.MainActivity
import com.vyllo.music.data.manager.PlaybackQueueManager
import com.vyllo.music.data.manager.PlaybackAudioEffectsManager
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.data.manager.WakeLockManager
import com.vyllo.music.domain.manager.PlaybackErrorHandler
import com.vyllo.music.domain.repository.IMusicRepository
import dagger.hilt.android.AndroidEntryPoint
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.service.audio.AudioSinkFactory
import com.vyllo.music.service.audio.CacheProvider
import com.vyllo.music.service.audio.MediaSessionCallback
import com.vyllo.music.service.audio.PlayerErrorListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
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

        // Optimize OkHttp
        val okHttpClient = OkHttpClient.Builder()
            .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
            .readTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()
                if (request.url.host.contains("googlevideo.com")) {
                    val userAgent = when {
                        url.contains("c=ANDROID", ignoreCase = true) -> {
                            "com.google.android.youtube/21.03.36 (Linux; U; Android 15; US) gzip"
                        }
                        url.contains("c=IOS", ignoreCase = true) -> {
                            "com.google.ios.youtube/21.03.2(iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X; US)"
                        }
                        url.contains("c=WEB", ignoreCase = true) -> {
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
                        }
                        else -> request.header("User-Agent")
                    }
                    if (userAgent != null) {
                        val playbackRequest = request.newBuilder()
                            .header("User-Agent", userAgent)
                            .header("Accept", "*/*")
                            .header("Accept-Encoding", "identity")
                            .build()
                        // log all outgoing headers
                        SecureLogger.d("MusicService") {
                            val hdrs = playbackRequest.headers.toMultimap().entries.joinToString { "${it.key}=${it.value}" }
                            "ExoPlayer request: method=${playbackRequest.method}, url_prefix=${url.take(120)}, headers=[$hdrs]"
                        }
                        val response = chain.proceed(playbackRequest)
                        if (!response.isSuccessful) {
                            // Log response headers for diagnosis
                            val respHdrs = response.headers.toMultimap().entries.joinToString { "${it.key}=${it.value}" }
                            SecureLogger.w(
                                "MusicService",
                                "googlevideo REJECTED: code=${response.code}, client=${clientNameFromUrl(url)}, resp_headers=[$respHdrs]"
                            )
                        } else {
                            SecureLogger.d("MusicService") { "googlevideo ACCEPTED: code=${response.code}, client=${clientNameFromUrl(url)}" }
                        }
                        return@addInterceptor response
                    }
                }
                chain.proceed(request)
            }
            .build()

        // Configure Cache
        val upstreamFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, OkHttpDataSource.Factory(okHttpClient))
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(CacheProvider.getSimpleCache(this))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // Configure LoadControl
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30000, 60000, 1500, 5000)
            .build()

        val keepAudioPlaying = preferenceManager.isKeepAudioPlayingEnabled

        preferenceManager.preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        volumeBoostProcessor.volumeMultiplier = preferenceManager.volumeBoostMultiplier

        val renderersFactory = AudioSinkFactory.createRenderersFactory(this, volumeBoostProcessor)

        // Initialize Player
        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(),
                !keepAudioPlaying // Re-enable automatic audio focus if keepAudioPlaying is false
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()

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
        player?.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
        player?.audioSessionId?.let { playbackAudioEffectsManager.attachToAudioSession(it) }

        serviceScope.launch {
            playbackQueueManager.queueVersion.collectLatest {
                playbackQueueOrchestrator.prefetchUpcomingStreams(serviceScope, player)
            }
        }

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

        // Initialize MediaSession
        mediaSession = MediaSession.Builder(this, player!!)
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
        preferenceManager.preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        wakeLockManager.release()

        mediaSession?.run {
            player?.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun clientNameFromUrl(url: String): String {
        return when {
            url.contains("c=IOS", ignoreCase = true) -> "IOS"
            url.contains("c=ANDROID", ignoreCase = true) -> "ANDROID"
            url.contains("c=WEB", ignoreCase = true) -> "WEB"
            else -> "UNKNOWN"
        }
    }
}

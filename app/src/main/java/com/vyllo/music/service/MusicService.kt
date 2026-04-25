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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.vyllo.music.MainActivity
import com.vyllo.music.data.manager.PlaybackQueueManager
import com.vyllo.music.data.manager.PlaybackAudioEffectsManager
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.data.manager.WakeLockManager
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.manager.PlaybackErrorHandler
import com.vyllo.music.data.IMusicRepository
import dagger.hilt.android.AndroidEntryPoint
import com.vyllo.music.core.security.SecureCacheManager
import com.vyllo.music.core.security.SecureLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
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
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val playbackErrorHandler = PlaybackErrorHandler(
        maxRetries = 3,
        baseDelayMs = 2_000L,
        maxDelayMs = 10_000L,
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
        }
        playbackAudioEffectsManager.onPreferenceChanged(key)
    }

    companion object {
        @Volatile
        private var simpleCache: SimpleCache? = null

        fun getSimpleCache(context: android.content.Context): SimpleCache {
            // Security: Use SecureCacheManager for encrypted cache with randomized directory
            return simpleCache ?: synchronized(this) {
                simpleCache ?: SecureCacheManager.getSecureCache(context).also { simpleCache = it }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Optimize OkHttp
        val okHttpClient = OkHttpClient.Builder()
            .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
            .readTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()

        // Configure Cache
        val upstreamFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, OkHttpDataSource.Factory(okHttpClient))
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(getSimpleCache(this))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // Configure LoadControl
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30000, 60000, 1500, 5000)
            .build()
        
        val keepAudioPlaying = preferenceManager.isKeepAudioPlayingEnabled
        preferenceManager.preferences.registerOnSharedPreferenceChangeListener(preferenceListener)

        // 1. Initialize Player
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), 
                !keepAudioPlaying // Re-enable automatic audio focus if keepAudioPlaying is false
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        // --- RETRY & AUTO-PLAY MECHANISM ---
        player?.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playbackErrorHandler.handleError(error)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        SecureLogger.d("MusicService", "Playback STATE_READY")
                        playbackErrorHandler.resetOnSuccess()
                        wakeLockManager.acquire()
                    }
                    Player.STATE_ENDED -> {
                        SecureLogger.d("MusicService", "Playback STATE_ENDED reached")
                        playbackErrorHandler.resetOnSuccess()
                        wakeLockManager.release()
                        // ExoPlayer handles REPEAT_MODE_ONE and REPEAT_MODE_ALL natively,
                        // so we only manually advance when repeat is OFF
                        val currentRepeatMode = player?.repeatMode ?: Player.REPEAT_MODE_OFF
                        if (currentRepeatMode == Player.REPEAT_MODE_OFF) {
                            playbackQueueOrchestrator.playNextTrack(serviceScope, player)
                        }
                    }
                    Player.STATE_IDLE -> {
                        SecureLogger.d("MusicService", "Playback STATE_IDLE")
                    }
                    Player.STATE_BUFFERING -> {
                        SecureLogger.d("MusicService", "Playback STATE_BUFFERING")
                        wakeLockManager.acquire()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                SecureLogger.d("MusicService", "MediaItem transition: reason=$reason, mediaId=${mediaItem?.mediaId}")
                player?.let { playbackQueueOrchestrator.enqueueNextTrackIfNeeded(serviceScope, it, mediaItem) }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId == AudioManager.ERROR) return
                playbackAudioEffectsManager.attachToAudioSession(audioSessionId)
            }
        })
        
        // Force 1.0x playback speed to prevent "super fast" playback bugs on some devices/emulators
        player?.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)
        player?.audioSessionId?.let { playbackAudioEffectsManager.attachToAudioSession(it) }

        serviceScope.launch {
            playbackQueueManager.queueVersion.collectLatest {
                val activeMediaItem = player?.currentMediaItem
                if (activeMediaItem != null) {
                    SecureLogger.d("MusicService", "Queue changed, ensuring upcoming tracks are queued")
                    player?.let { playbackQueueOrchestrator.enqueueNextTrackIfNeeded(serviceScope, it, activeMediaItem) }
                }
            }
        }

        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 3. Initialize MediaSession
        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(pendingIntent)
            .build()
            
        // Acquire wake locks immediately on service creation for reliable background playback
        wakeLockManager.acquire()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && !player.playWhenReady) {
            // Only stop if not playing — if music is active, keep the service alive
            stopSelf()
        }
        // If playing, keep the service running for background playback
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
}

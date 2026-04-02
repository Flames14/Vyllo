package com.vyllo.music.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.*
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import android.net.wifi.WifiManager
import android.os.PowerManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.vyllo.music.MainActivity
import com.vyllo.music.data.manager.PlaybackQueueManager
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.data.IMusicRepository
import dagger.hilt.android.AndroidEntryPoint
import com.vyllo.music.core.security.SecureCacheManager
import com.vyllo.music.core.security.SecureLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.system.exitProcess

@AndroidEntryPoint
class MusicService : MediaSessionService() {
    
    @Inject
    lateinit var repository: IMusicRepository
    
    @Inject
    lateinit var preferenceManager: PreferenceManager
    
    @Inject
    lateinit var playbackQueueManager: PlaybackQueueManager

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    
    // Background execution scope
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    // Locks for background transition
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    
    // Track if wake locks should be held
    private var shouldHoldWakeLocks = false

    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "keep_audio_playing_enabled") {
            val enabled = prefs.getBoolean(key, false)
            player?.setAudioAttributes(
                AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(),
                !enabled // false means keep playing when mic/camera is used
            )
        }
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

    /**
     * Acquire wake locks to prevent CPU sleep and WiFi sleep during playback.
     * This is critical for reliable background playback when screen is off.
     */
    private fun acquireWakeLocks() {
        if (shouldHoldWakeLocks) {
            // Already held
            return
        }
        try {
            wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes timeout (renewed on each STATE_READY)
            wifiLock?.acquire()
            shouldHoldWakeLocks = true
            SecureLogger.d("MusicService", "Wake locks acquired")
        } catch (e: Exception) {
            SecureLogger.e("MusicService", "Failed to acquire wake locks", e)
        }
    }

    /**
     * Release wake locks when playback is paused or stopped.
     */
    private fun releaseWakeLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
            shouldHoldWakeLocks = false
            SecureLogger.d("MusicService", "Wake locks released")
        } catch (e: Exception) {
            SecureLogger.e("MusicService", "Failed to release wake locks", e)
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
            .setBufferDurationsMs(30000, 60000, 2500, 5000)
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

        // Initialize Locks
        val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Vyllo::ServiceWakeLock").apply {
            setReferenceCounted(false)
        }

        val wifiManager = getSystemService(android.content.Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Vyllo::ServiceWifiLock").apply {
            setReferenceCounted(false)
        }

        // --- RETRY & AUTO-PLAY MECHANISM ---
        player?.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                SecureLogger.e("MusicService", "Player error: ${error.message}", error)
                
                // Auto-recover from playback errors
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                handler.postDelayed({
                    player?.prepare()
                    player?.play()
                }, 2000)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        SecureLogger.d("MusicService", "Playback STATE_READY - acquired wake locks")
                        acquireWakeLocks()
                    }
                    Player.STATE_ENDED -> {
                        SecureLogger.d("MusicService", "Playback STATE_ENDED reached")
                        playNextTrack()
                    }
                    Player.STATE_IDLE -> {
                        SecureLogger.d("MusicService", "Playback STATE_IDLE")
                    }
                    Player.STATE_BUFFERING -> {
                        SecureLogger.d("MusicService", "Playback STATE_BUFFERING")
                        // Keep wake locks during buffering
                        acquireWakeLocks()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                SecureLogger.d("MusicService", "MediaItem transition: reason=$reason, mediaId=${mediaItem?.mediaId}")
                enqueueNextTrackIfNeeded(mediaItem)
            }
        })
        
        // Force 1.0x playback speed to prevent "super fast" playback bugs on some devices/emulators
        player?.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)

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
        acquireWakeLocks()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && !player.playWhenReady) {
            stopSelf()
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun enqueueNextTrackIfNeeded(currentMediaItem: MediaItem?) {
        val player = this.player ?: return
        val currentMediaId = currentMediaItem?.mediaId ?: return

        serviceScope.launch {
            val queue = playbackQueueManager.getQueueSnapshot()
            val currentIndex = queue.indexOfFirst { it.url == currentMediaId }
            if (currentIndex == -1) {
                SecureLogger.w("MusicService", "Current media ID not found in queue: $currentMediaId")
                return@launch
            }

            playbackQueueManager.setCurrentIndexSafe(currentIndex)

            // Prefetch at least 3 tracks ahead for reliable background playback
            val tracksToPrefetch = 3
            val currentPlaylistIndex = player.currentMediaItemIndex
            val itemsAfterCurrent = player.mediaItemCount - currentPlaylistIndex - 1

            if (itemsAfterCurrent >= tracksToPrefetch) {
                SecureLogger.d("MusicService", "Already have $itemsAfterCurrent tracks queued (need $tracksToPrefetch)")
                return@launch
            }

            val tracksNeeded = tracksToPrefetch - itemsAfterCurrent
            SecureLogger.d("MusicService", "Need to prefetch $tracksNeeded more tracks (currently have $itemsAfterCurrent)")

            // Calculate next index to prefetch
            var nextIndex = currentIndex + 1

            // Prefetch tracks one by one
            for (i in 0 until tracksNeeded) {
                val prefetchIndex = nextIndex + i
                
                // If we're near the end of the queue, fetch related songs
                if (prefetchIndex >= queue.size) {
                    SecureLogger.d("MusicService", "End of queue reached at index $prefetchIndex, fetching related songs")
                    try {
                        wakeLock?.acquire(60000L)
                        wifiLock?.acquire()
                        val currentUrl = queue[maxOf(0, queue.size - 1)].url
                        val related = repository.getRelatedSongs(currentUrl)
                        if (related.isNotEmpty()) {
                            val existingUrls = queue.map { it.url }.toSet()
                            val newSongs = related.filter { it.url !in existingUrls }
                            synchronized(playbackQueueManager) {
                                val currentQueue = playbackQueueManager.currentQueue
                                if (newSongs.isNotEmpty()) {
                                    currentQueue.addAll(newSongs)
                                    SecureLogger.d("MusicService", "Added ${newSongs.size} related songs to queue")
                                } else {
                                    currentQueue.addAll(related.take(5))
                                    SecureLogger.d("MusicService", "Added ${related.size} related songs to queue")
                                }
                            }
                        } else {
                            SecureLogger.w("MusicService", "No related songs found")
                        }
                    } catch (e: Exception) {
                        SecureLogger.e("MusicService", "Prefetch discovery failed", e)
                    } finally {
                        kotlinx.coroutines.delay(2000L)
                        if (wakeLock?.isHeld == true) wakeLock?.release()
                        if (wifiLock?.isHeld == true) wifiLock?.release()
                    }
                }
            }

            // Now prefetch the stream URLs for the next tracks
            for (i in 0 until tracksNeeded) {
                val targetIndex = nextIndex + i
                if (targetIndex >= playbackQueueManager.currentQueue.size) {
                    SecureLogger.w("MusicService", "Index $targetIndex out of bounds, stopping prefetch")
                    break
                }
                
                val nextTrack = playbackQueueManager.currentQueue[targetIndex]
                
                // Check if this track is already in the player queue
                val alreadyQueued = (currentPlaylistIndex + 1 until player.mediaItemCount).any { idx ->
                    player.getMediaItemAt(idx).mediaId == nextTrack.url
                }
                
                if (alreadyQueued) {
                    SecureLogger.d("MusicService", "Track already queued: ${nextTrack.title}")
                    continue
                }
                
                SecureLogger.d("MusicService", "Prefetching track $targetIndex: ${nextTrack.title}")
                try {
                    wakeLock?.acquire(30000L)
                    wifiLock?.acquire()

                    val streamUrl = repository.getStreamUrl(nextTrack.url, isVideo = false)
                    if (streamUrl != null) {
                        val metadata = MediaMetadata.Builder()
                            .setTitle(nextTrack.title)
                            .setArtist(nextTrack.uploader)
                            .setArtworkUri(android.net.Uri.parse(nextTrack.thumbnailUrl))
                            .build()

                        val mediaItem = MediaItem.Builder()
                            .setMediaId(nextTrack.url)
                            .setUri(streamUrl)
                            .setMediaMetadata(metadata)
                            .build()

                        player.addMediaItem(mediaItem)
                        SecureLogger.d("MusicService", "Successfully prefetched: ${nextTrack.title}")
                    } else {
                        SecureLogger.w("MusicService", "Failed to get stream URL for: ${nextTrack.title}")
                    }
                } catch (e: Exception) {
                    SecureLogger.e("MusicService", "Prefetch failed for: ${nextTrack.title}", e)
                } finally {
                    kotlinx.coroutines.delay(1000L)
                    if (wakeLock?.isHeld == true) wakeLock?.release()
                    if (wifiLock?.isHeld == true) wifiLock?.release()
                }
            }
        }
    }

    private fun playNextTrack() {
        val nextIndex = playbackQueueManager.currentIndex + 1
        SecureLogger.d("MusicService", "playNextTrack called: currentIndex=${playbackQueueManager.currentIndex}, nextIndex=$nextIndex, queueSize=${playbackQueueManager.currentQueue.size}")
        
        if (nextIndex < playbackQueueManager.currentQueue.size) {
            playTrackAtIndex(nextIndex)
        } else {
            val currentUrl = playbackQueueManager.currentQueue.getOrNull(playbackQueueManager.currentIndex)?.url
            if (currentUrl != null) {
                SecureLogger.d("MusicService", "End of queue, fetching related songs for autoplay")
                serviceScope.launch {
                    try {
                        wakeLock?.acquire(60000L)
                        wifiLock?.acquire()
                        val related = repository.getRelatedSongs(currentUrl)
                        if (related.isNotEmpty()) {
                            val existingUrls = playbackQueueManager.currentQueue.map { it.url }.toSet()
                            val newSongs = related.filter { it.url !in existingUrls }
                            synchronized(playbackQueueManager) {
                                val currentQueue = playbackQueueManager.currentQueue
                                if (newSongs.isNotEmpty()) {
                                    currentQueue.addAll(newSongs)
                                    SecureLogger.d("MusicService", "Added ${newSongs.size} songs for autoplay")
                                } else {
                                    currentQueue.addAll(related.take(5))
                                    SecureLogger.d("MusicService", "Added ${related.size} songs for autoplay")
                                }
                            }
                            playTrackAtIndex(playbackQueueManager.currentIndex + 1)
                        } else {
                            SecureLogger.w("MusicService", "No related songs found for autoplay")
                        }
                    } catch (e: Exception) {
                        SecureLogger.e("MusicService", "Autoplay discovery failed", e)
                    } finally {
                        kotlinx.coroutines.delay(2000L)
                        if (wakeLock?.isHeld == true) wakeLock?.release()
                        if (wifiLock?.isHeld == true) wifiLock?.release()
                    }
                }
            }
        }
    }

    private fun playTrackAtIndex(index: Int) {
        if (index >= playbackQueueManager.currentQueue.size) {
            SecureLogger.w("MusicService", "playTrackAtIndex: index $index out of bounds (size: ${playbackQueueManager.currentQueue.size})")
            return
        }
        
        val track = playbackQueueManager.currentQueue[index]
        playbackQueueManager.setCurrentIndexSafe(index)
        SecureLogger.d("MusicService", "playTrackAtIndex: $index - ${track.title}")

        serviceScope.launch {
            try {
                wakeLock?.acquire(30000L)
                wifiLock?.acquire()
                val streamUrl = repository.getStreamUrl(track.url, force = false)
                if (streamUrl != null) {
                    val metadata = MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.uploader)
                        .setArtworkUri(android.net.Uri.parse(track.thumbnailUrl))
                        .build()
                    val mediaItem = MediaItem.Builder()
                        .setMediaId(track.url)
                        .setUri(streamUrl)
                        .setMediaMetadata(metadata)
                        .build()
                    player?.let { p ->
                        // Only set media item if we don't already have it queued
                        if (p.currentMediaItem?.mediaId != track.url) {
                            p.setMediaItem(mediaItem)
                        }
                        p.prepare()
                        p.play()
                        SecureLogger.d("MusicService", "Started playback: ${track.title}")
                    }
                } else {
                    SecureLogger.w("MusicService", "Failed to get stream URL for: ${track.title}")
                }
            } catch (e: Exception) {
                SecureLogger.e("MusicService", "playTrackAtIndex failed for: ${track.title}", e)
            } finally {
                kotlinx.coroutines.delay(2000L)
                if (wakeLock?.isHeld == true) wakeLock?.release()
                if (wifiLock?.isHeld == true) wifiLock?.release()
            }
        }
    }

    override fun onDestroy() {
        SecureLogger.d("MusicService", "Service onDestroy called")
        serviceJob.cancel()
        preferenceManager.preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)

        // Release all wake locks
        releaseWakeLocks()
        
        mediaSession?.run {
            player?.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

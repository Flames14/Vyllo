package com.example.musicpiped.service

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
import com.example.musicpiped.MainActivity
import com.example.musicpiped.data.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    
    // Background execution scope
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    // Locks for background transition
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

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
            return simpleCache ?: synchronized(this) {
                simpleCache ?: SimpleCache(
                    File(context.cacheDir, "media"),
                    LeastRecentlyUsedCacheEvictor(300 * 1024 * 1024),
                    StandaloneDatabaseProvider(context)
                ).also { simpleCache = it }
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
            .setBufferDurationsMs(30000, 60000, 2500, 5000)
            .build()
        
        val prefs = getSharedPreferences("music_prefs", android.content.Context.MODE_PRIVATE)
        val keepAudioPlaying = prefs.getBoolean("keep_audio_playing_enabled", false)
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)

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
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MusicPiped::ServiceWakeLock").apply {
            setReferenceCounted(false)
        }
        
        val wifiManager = getSystemService(android.content.Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MusicPiped::ServiceWifiLock").apply {
            setReferenceCounted(false)
        }

        // --- RETRY & AUTO-PLAY MECHANISM ---
        player?.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                handler.postDelayed({
                    player?.prepare()
                    player?.play()
                }, 2000)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val p = player ?: return
                    if (!p.hasNextMediaItem()) {
                        playNextTrack()
                    }
                }
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && !player.playWhenReady) {
            stopSelf()
        } else {
            player?.release()
            stopSelf()
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun enqueueNextTrackIfNeeded(currentMediaItem: MediaItem?) {
        val player = this.player ?: return
        val currentMediaId = currentMediaItem?.mediaId ?: return
        
        serviceScope.launch {
            val queue = MusicRepository.currentQueue
            val currentIndex = queue.indexOfFirst { it.url == currentMediaId }
            if (currentIndex == -1) return@launch
            
            MusicRepository.currentIndex = currentIndex
            
            if (player.mediaItemCount > 1 && player.currentMediaItemIndex < player.mediaItemCount - 1) {
                return@launch
            }
            
            var nextIndex = currentIndex + 1
            
            if (nextIndex >= queue.size) {
                try {
                    wakeLock?.acquire(60000L)
                    wifiLock?.acquire()
                    val currentUrl = queue.getOrNull(currentIndex)?.url ?: return@launch
                    val related = MusicRepository.getRelatedSongs(currentUrl)
                    if (related.isNotEmpty()) {
                        val existingUrls = queue.map { it.url }.toSet()
                        val newSongs = related.filter { it.url !in existingUrls }
                        if (newSongs.isNotEmpty()) {
                            MusicRepository.currentQueue.addAll(newSongs)
                        } else {
                            MusicRepository.currentQueue.addAll(related.take(5))
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MusicService", "Prefetch discovery failed", e)
                } finally {
                    kotlinx.coroutines.delay(2000L)
                    if (wakeLock?.isHeld == true) wakeLock?.release()
                    if (wifiLock?.isHeld == true) wifiLock?.release()
                }
                nextIndex = currentIndex + 1
            }
            
            if (nextIndex < MusicRepository.currentQueue.size) {
                val nextTrack = MusicRepository.currentQueue[nextIndex]
                try {
                    wakeLock?.acquire(30000L)
                    wifiLock?.acquire()
                    
                    val streamUrl = MusicRepository.getStreamUrl(nextTrack.url, force = false)
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
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    kotlinx.coroutines.delay(2000L)
                    if (wakeLock?.isHeld == true) wakeLock?.release()
                    if (wifiLock?.isHeld == true) wifiLock?.release()
                }
            }
        }
    }

    private fun playNextTrack() {
        val nextIndex = MusicRepository.currentIndex + 1
        if (nextIndex < MusicRepository.currentQueue.size) {
            playTrackAtIndex(nextIndex)
        } else {
            val currentUrl = MusicRepository.currentQueue.getOrNull(MusicRepository.currentIndex)?.url
            if (currentUrl != null) {
                serviceScope.launch {
                    try {
                        wakeLock?.acquire(60000L)
                        wifiLock?.acquire()
                        val related = MusicRepository.getRelatedSongs(currentUrl)
                        if (related.isNotEmpty()) {
                            val existingUrls = MusicRepository.currentQueue.map { it.url }.toSet()
                            val newSongs = related.filter { it.url !in existingUrls }
                            if (newSongs.isNotEmpty()) {
                                MusicRepository.currentQueue.addAll(newSongs)
                                playTrackAtIndex(MusicRepository.currentIndex + 1)
                            } else {
                                MusicRepository.currentQueue.addAll(related.take(5))
                                playTrackAtIndex(MusicRepository.currentIndex + 1)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MusicService", "Autoplay discovery failed", e)
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
        if (index >= MusicRepository.currentQueue.size) return
        val track = MusicRepository.currentQueue[index]
        MusicRepository.currentIndex = index
        
        serviceScope.launch {
            try {
                wakeLock?.acquire(30000L)
                wifiLock?.acquire()
                val streamUrl = MusicRepository.getStreamUrl(track.url, force = false)
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
                        p.setMediaItem(mediaItem)
                        p.prepare()
                        p.play()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                kotlinx.coroutines.delay(2000L)
                if (wakeLock?.isHeld == true) wakeLock?.release()
                if (wifiLock?.isHeld == true) wifiLock?.release()
            }
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        val prefs = getSharedPreferences("music_prefs", android.content.Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        
        if (wakeLock?.isHeld == true) wakeLock?.release()
        if (wifiLock?.isHeld == true) wifiLock?.release()
        mediaSession?.run {
            player?.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

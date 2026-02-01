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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.musicpiped.MainActivity
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class MusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

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
        // Optimize OkHttp
        val okHttpClient = OkHttpClient.Builder()
            .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1)) // Fix: Disable HTTP/2 to prevent potential playback speed/sync issues
            .readTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()

        // Configure Cache with DefaultDataSource to support both Network and Local Files
        val upstreamFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, OkHttpDataSource.Factory(okHttpClient))
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(getSimpleCache(this))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // Configure LoadControl (Buffer)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30000,   // minBufferMs
                60000,   // maxBufferMs
                2500,    // bufferForPlaybackMs
                5000     // bufferForPlaybackAfterRebufferMs
            )
            .build()
        
        // 1. Initialize Player with Audio Focus handling and Optimizations
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(cacheDataSourceFactory)
            )
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(), 
                true // Handle audio focus
            )
            .build()
        
        // Fix: Explicitly enforce 1.0x playback speed
        player?.playbackParameters = androidx.media3.common.PlaybackParameters(1.0f)

        // 2. Create an Intent to open UI when notification is clicked
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

    // Required method: Gives the controller (UI) access to the session
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // --- NEW: FORCE STOP LOGIC ---
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && !player.playWhenReady) {
            // If not playing, stop immediately
            stopSelf()
        } else {
            // Use this if you want to kill it EVEN IF playing music:
            player?.release()
            stopSelf()
            // Completely kill the process (Aggressive optimization)
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        }
        super.onTaskRemoved(rootIntent)
    }

    // Cleanup
    override fun onDestroy() {
        mediaSession?.run {
            player?.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
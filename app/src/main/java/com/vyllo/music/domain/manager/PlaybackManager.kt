package com.vyllo.music.domain.manager

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.data.manager.PlaybackQueueManager
import com.vyllo.music.service.MusicService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackQueueManager: PlaybackQueueManager
) {

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val listeners = mutableListOf<PlaybackListener>()

    interface PlaybackListener {
        fun onPlaybackStateChanged(isPlaying: Boolean)
        fun onMediaItemChanged(item: MusicItem?)
        fun onError(error: Exception)
    }

    fun initialize(): ListenableFuture<MediaController> {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupPlayerListener()
            } catch (e: Exception) {
                notifyError(e)
            }
        }, MoreExecutors.directExecutor())
        
        return controllerFuture!!
    }

    private fun setupPlayerListener() {
        mediaController?.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                notifyPlaybackStateChanged(playing)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val item = mediaItem?.toMusicItem()
                notifyMediaItemChanged(item)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                notifyError(error)
            }
        })
    }

    fun getController(): MediaController? = mediaController

    fun isConnected(): Boolean = mediaController != null

    suspend fun awaitConnection(): MediaController {
        val future = controllerFuture ?: throw IllegalStateException("Controller not initialized")

        return suspendCancellableCoroutine { continuation ->
            future.addListener({
                try {
                    val controller = future.get()
                    mediaController = controller
                    continuation.resume(controller)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }, MoreExecutors.directExecutor())
        }
    }

    suspend fun playMusic(item: MusicItem, streamUrl: String, isVideo: Boolean = false): Result<Unit> {
        val controller = mediaController ?: return Result.failure(Exception("MediaController not connected"))

        return try {
            playbackQueueManager.replaceQueue(listOf(item), 0)

            val metadata = MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(item.uploader)
                .setArtworkUri(Uri.parse(item.thumbnailUrl))
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(item.url)
                .setUri(streamUrl)
                .setMediaMetadata(metadata)
                .build()

            // Do not call controller.stop() here as it causes unnecessary delay
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()

            SecureLogger.d("PlaybackManager") { "playMusic: url=${item.url}, isVideo=$isVideo" }
            Result.success(Unit)
        } catch (e: Exception) {
            SecureLogger.e("PlaybackManager", "playMusic failed", e)
            Result.failure(e)
        }
    }

    suspend fun playFromLocal(localUrl: String, item: MusicItem? = null): Result<Unit> {
        val controller = mediaController ?: return Result.failure(Exception("MediaController not connected"))

        return try {
            val metadata = MediaMetadata.Builder()
                .setTitle(item?.title)
                .setArtist(item?.uploader)
                .setArtworkUri(item?.thumbnailUrl?.let { Uri.parse(it) })
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(localUrl)
                .setMediaMetadata(metadata)
                .build()

            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun pause() {
        mediaController?.pause()
    }

    fun play() {
        mediaController?.play()
    }

    fun stop() {
        mediaController?.stop()
    }

    fun isPlaying(): Boolean = mediaController?.isPlaying == true

    fun getCurrentPosition(): Long = mediaController?.currentPosition ?: 0L

    fun getDuration(): Long = mediaController?.duration ?: 0L

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

    fun skipToNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun getCurrentMediaItem(): MediaItem? = mediaController?.currentMediaItem

    fun setShuffleModeEnabled(enabled: Boolean) {
        mediaController?.shuffleModeEnabled = enabled
    }

    fun isShuffleModeEnabled(): Boolean = mediaController?.shuffleModeEnabled == true

    fun setRepeatMode(@androidx.media3.common.Player.RepeatMode mode: Int) {
        mediaController?.repeatMode = mode
    }

    fun getRepeatMode(): Int = mediaController?.repeatMode ?: androidx.media3.common.Player.REPEAT_MODE_OFF

    fun addListener(listener: PlaybackListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: PlaybackListener) {
        listeners.remove(listener)
    }

    private fun notifyPlaybackStateChanged(isPlaying: Boolean) {
        listeners.forEach { it.onPlaybackStateChanged(isPlaying) }
    }

    private fun notifyMediaItemChanged(item: MusicItem?) {
        listeners.forEach { it.onMediaItemChanged(item) }
    }

    private fun notifyError(error: Exception) {
        listeners.forEach { it.onError(error) }
    }

    fun release() {
        listeners.clear()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        controllerFuture = null
    }

    private fun MediaItem.toMusicItem(): MusicItem {
        return MusicItem(
            title = mediaMetadata.title?.toString() ?: "Unknown",
            uploader = mediaMetadata.artist?.toString() ?: "Unknown",
            thumbnailUrl = mediaMetadata.artworkUri?.toString() ?: "",
            url = mediaId
        )
    }
}

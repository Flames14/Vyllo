package com.vyllo.music.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import com.google.common.util.concurrent.ListenableFuture
import com.vyllo.music.MainActivity
import com.vyllo.music.R
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.data.LyricsEngine
import com.vyllo.music.domain.model.SyncedLyricLine
import com.vyllo.music.core.security.SecureLogger
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.compose.ui.res.stringResource

@AndroidEntryPoint
class FloatingWindowService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    @Inject
    lateinit var preferenceManager: PreferenceManager

    @Inject
    lateinit var lyricsEngine: LyricsEngine

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var composeView: ComposeView? = null
    
    companion object {
        private const val CHANNEL_ID = "floating_bubble_channel"
        private const val NOTIFICATION_ID = 888
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController by mutableStateOf<MediaController?>(null)
    private var floatingLyrics by mutableStateOf<List<SyncedLyricLine>>(emptyList())
    private var lyricsFetchJob: Job? = null

    // SavedStateRegistryOwner boilerplate
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    // ViewModelStoreOwner boilerplate
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    @androidx.media3.common.util.UnstableApi
    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Create Notification for Foreground Service
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_floating_title))
            .setContentText(getString(R.string.notif_floating_text))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Window Layout Params
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        // Initial Position - Loaded from preferenceManager
        params.gravity = Gravity.TOP or Gravity.START
        params.x = preferenceManager.floatingPlayerX
        params.y = preferenceManager.floatingPlayerY

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeViewModelStoreOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)

            setContent {
                FloatingBubbleContent(
                    mediaController = mediaController,
                    syncedLyrics = floatingLyrics,
                    onDrag = { dx, dy ->
                        try {
                            params.x += dx.toInt()
                            params.y += dy.toInt()
                            windowManager.updateViewLayout(this@apply, params)
                        } catch(e: Exception) {
                            // View might be detached
                        }
                    },
                    onDragEnd = {
                        // Persist position when drag finishes
                        preferenceManager.floatingPlayerX = params.x
                        preferenceManager.floatingPlayerY = params.y
                    },
                    onClick = {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                        stopSelf()
                    },
                    onMediaItemChanged = ::fetchFloatingLyrics,
                    onClose = { stopSelf() }
                )
            }
        }

        // Show immediately - don't wait for media connection
        try {
            composeView?.let { windowManager.addView(it, params) }
        } catch(e: Exception) {
            SecureLogger.e("FloatingWindowService", "Failed to add view", e)
        }

        // Connect to MediaSession
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                fetchFloatingLyrics(mediaController?.currentMediaItem)
            } catch (e: Exception) {
                SecureLogger.e("FloatingWindowService", "Failed to connect media controller", e)
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(this))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Player",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun fetchFloatingLyrics(mediaItem: MediaItem?) {
        lyricsFetchJob?.cancel()
        floatingLyrics = emptyList()
        val metadata = mediaItem?.mediaMetadata ?: return
        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() } ?: return
        val artist = metadata.artist?.toString().orEmpty()
        val album = metadata.albumTitle?.toString()
        lyricsFetchJob = lifecycleScope.launch {
            val durationSecs = mediaController?.duration?.takeIf { it > 0L }?.div(1_000L) ?: 0L
            val response = runCatching {
                lyricsEngine.findLyricsUniversal(title, artist, durationSecs, extractedAlbum = album)
            }.getOrNull()
            floatingLyrics = response?.syncedLines.orEmpty()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        lyricsFetchJob?.cancel()
        composeView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                SecureLogger.e("FloatingWindowService", "Failed to remove view", e)
            }
        }
        composeView = null
        store.clear()
    }
}

@Composable
fun FloatingBubbleContent(
    mediaController: MediaController?,
    syncedLyrics: List<SyncedLyricLine>,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
    onMediaItemChanged: (MediaItem?) -> Unit,
    onClose: () -> Unit
) {
    var artworkUri by remember { mutableStateOf<String?>(null) }
    var isCurrentlyPlaying by remember { mutableStateOf(false) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }

    // Listen to player state — artwork + play/pause sync with the session.
    DisposableEffect(mediaController) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                artworkUri = mediaItem?.mediaMetadata?.artworkUri?.toString()
                onMediaItemChanged(mediaItem)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isCurrentlyPlaying = isPlaying
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, playbackState: Int) {
                // Fallback: keep artwork fresh if transition callback didn't fire.
                if (artworkUri == null) {
                    artworkUri = mediaController?.currentMediaItem?.mediaMetadata?.artworkUri?.toString()
                }
            }
        }
        mediaController?.addListener(listener)
        artworkUri = mediaController?.currentMediaItem?.mediaMetadata?.artworkUri?.toString()
        isCurrentlyPlaying = mediaController?.isPlaying == true

        onDispose { mediaController?.removeListener(listener) }
    }

    LaunchedEffect(mediaController, showLyrics) {
        while (showLyrics && mediaController != null) {
            currentPosition = mediaController.currentPosition
            kotlinx.coroutines.delay(150)
        }
    }
    val activeLyric = syncedLyrics.lastOrNull { it.startTimeMs <= currentPosition }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black)
            .border(
                2.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(0.5f),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        // Artwork — draggable surface; tap opens the app.
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onClick() }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (artworkUri != null) {
                AsyncImage(
                    model = artworkUri,
                    contentDescription = stringResource(R.string.cd_music_bubble),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().clip(RoundedCornerShape(8.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = stringResource(R.string.cd_music_bubble),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Previous
        IconButton(
            onClick = { mediaController?.seekToPreviousMediaItem() },
            enabled = mediaController != null
        ) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.accessibility_skip_previous),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Play / Pause
        IconButton(
            onClick = {
                if (isCurrentlyPlaying) mediaController?.pause() else mediaController?.play()
            },
            enabled = mediaController != null
        ) {
            Icon(
                imageVector = if (isCurrentlyPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isCurrentlyPlaying) stringResource(R.string.accessibility_pause) else stringResource(R.string.accessibility_play),
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        // Next
        IconButton(
            onClick = { mediaController?.seekToNextMediaItem() },
            enabled = mediaController != null
        ) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.accessibility_skip_next),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = { showLyrics = !showLyrics },
            enabled = syncedLyrics.isNotEmpty()
        ) {
            Icon(
                Icons.Rounded.Lyrics,
                contentDescription = stringResource(R.string.floating_lyrics_toggle),
                tint = if (showLyrics) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Close bubble
        IconButton(onClick = onClose) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.cd_close),
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(18.dp)
            )
        }
      }

      if (showLyrics) {
          Text(
              text = activeLyric?.content ?: stringResource(R.string.floating_lyrics_unavailable),
              color = Color.White,
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
              maxLines = 2,
              modifier = Modifier
                  .padding(horizontal = 8.dp, vertical = 2.dp)
                  .clickable { onClick() }
          )
      }
    }
}

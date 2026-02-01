package com.example.musicpiped.service

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.musicpiped.MainActivity
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.guava.await
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.ui.Alignment
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.media3.common.MediaItem
import androidx.compose.foundation.border

class FloatingWindowService : LifecycleService(), SavedStateRegistryOwner, ViewModelStoreOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var composeView: ComposeView

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController by mutableStateOf<MediaController?>(null)

    // SavedStateRegistryOwner boilerplate
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    // ViewModelStoreOwner boilerplate
    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        
        // Connect to MediaSession
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try { 
                mediaController = controllerFuture?.get() 
            } catch (e: Exception) { 
                e.printStackTrace() 
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(this))

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // Initial Position
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 100

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingWindowService)
            setViewTreeViewModelStoreOwner(this@FloatingWindowService)
            setViewTreeSavedStateRegistryOwner(this@FloatingWindowService)
            
            setContent {
                FloatingBubbleContent(
                    mediaController = mediaController,
                    onDrag = { x, y ->
                        try {
                            params.x += x.toInt()
                            params.y += y.toInt()
                            windowManager.updateViewLayout(this, params)
                        } catch(e: Exception) {
                            // View might be detached
                        }
                    },
                    onClick = {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                        stopSelf()
                    }
                )
            }
        }

        windowManager.addView(composeView, params)
    }

    private fun addFlags(flags: Int) {
         // Placeholder in case used by Intent extension, but standard Intent has addFlags
    }

    override fun onDestroy() {
        super.onDestroy()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        try {
            windowManager.removeView(composeView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun FloatingBubbleContent(
    mediaController: MediaController?,
    onDrag: (Float, Float) -> Unit,
    onClick: () -> Unit
) {
    var artworkUri by remember { mutableStateOf<String?>(null) }
    
    // Listen to player state
    DisposableEffect(mediaController) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                artworkUri = mediaItem?.mediaMetadata?.artworkUri?.toString()
            }
            // Initial load
            override fun onPlaybackStateChanged(playbackState: Int) {
                 if (artworkUri == null) {
                     artworkUri = mediaController?.currentMediaItem?.mediaMetadata?.artworkUri?.toString()
                 }
            }
        }
        mediaController?.addListener(listener)
        // Set initial
        artworkUri = mediaController?.currentMediaItem?.mediaMetadata?.artworkUri?.toString()
        
        onDispose { mediaController?.removeListener(listener) }
    }

    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(Color.Black)
            .border(2.dp, Color(0xFFE0E0E0).copy(0.5f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (artworkUri != null) {
            AsyncImage(
                model = artworkUri,
                contentDescription = "Music Bubble",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(CircleShape)
            )
        } else {
             Icon(
                 imageVector = Icons.Rounded.MusicNote,
                 contentDescription = null,
                 tint = Color.White,
                 modifier = Modifier.size(32.dp)
             )
        }
    }
}

package com.vyllo.music

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import androidx.compose.ui.platform.LocalContext
import com.vyllo.music.VylloNavigation
import com.vyllo.music.domain.manager.PlaybackManager
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.PlayResult
import com.vyllo.music.domain.usecase.PlayMusicUseCase
import com.vyllo.music.service.MusicService
import com.vyllo.music.data.manager.PlaybackQueueManager
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.presentation.theme.ThemeManager
import com.vyllo.music.core.security.SecureLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    private val libraryViewModel: LibraryViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    @Inject
    lateinit var playbackManager: PlaybackManager

    @Inject
    lateinit var preferenceManager: PreferenceManager

    @Inject
    lateinit var playMusicUseCase: PlayMusicUseCase

    @Inject
    lateinit var playbackQueueManager: PlaybackQueueManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            SecureLogger.d("MainActivity", "Notification permission granted")
        } else {
            SecureLogger.w("MainActivity", "Notification permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupDisplayMode()
        checkPermissions()
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = 
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemBars()
        
        // Start foreground service for playback
        startService(android.content.Intent(this, MusicService::class.java))
        playbackManager.initialize()

        setContent {
            val context = LocalContext.current
            
            // Custom Coil image loader for better performance
            // Note: allowHardware(true) is used for large images (album art) for better performance.
            // bitmapConfig is not set because hardware bitmaps use their own internal format,
            // making RGB_565 redundant and ignored.
            val imageLoader = remember {
                ImageLoader.Builder(context)
                    .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.25).build() }
                    .diskCache {
                        DiskCache.Builder()
                            .directory(context.cacheDir.resolve("image_cache"))
                            .maxSizeBytes(100L * 1024 * 1024)
                            .build()
                    }
                    .crossfade(true)
                    .allowHardware(true)
                    .build()
            }

            CompositionLocalProvider(LocalImageLoader provides imageLoader) {
                val isDark = ThemeManager.isDarkTheme(settingsViewModel.themeMode, isSystemInDarkTheme())
                val colorScheme = ThemeManager.getColorScheme(settingsViewModel.themeMode, isSystemInDarkTheme())
                
                MaterialTheme(colorScheme = colorScheme) {
                    val systemUiController = WindowCompat.getInsetsController(window, window.decorView)
                    systemUiController.isAppearanceLightStatusBars = !isDark

                    VylloNavigation(
                        playbackManager = playbackManager, 
                        homeViewModel = homeViewModel,
                        searchViewModel = searchViewModel,
                        libraryViewModel = libraryViewModel,
                        playerViewModel = playerViewModel,
                        settingsViewModel = settingsViewModel,
                        onPlay = { item -> playMusic(item) },
                        onNext = { item -> playNext(item) },
                        onPrev = { item -> playPrevious(item) }
                    )
                }
            }
        }
    }

    private fun setupDisplayMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val display = windowManager.defaultDisplay
            display.supportedModes.maxByOrNull { it.refreshRate }?.let { maxMode ->
                val params = window.attributes
                params.preferredDisplayModeId = maxMode.modeId
                window.attributes = params
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun playMusic(item: MusicItem) {
        val isVideo = playerViewModel.isVideoMode
        // Set loading state immediately so the player UI shows the spinner while resolving URL
        playerViewModel.setPlaybackLoading(true, item.url)
        lifecycleScope.launch {
            val result = playMusicUseCase.execute(item, isVideo = isVideo)
            playerViewModel.setPlaybackLoading(false)
            when (result) {
                is PlayResult.Success -> playerViewModel.loadRelatedSongs(item)
                is PlayResult.Failure -> {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(
                            this@MainActivity,
                            result.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun playNext(currentItem: MusicItem?) {
        val controller = playbackManager.getController()
        if (controller != null && controller.hasNextMediaItem()) {
            playbackManager.skipToNext()
        } else {
            val queueSnapshot = playbackQueueManager.getQueueSnapshot()
            val currentIdx = playbackQueueManager.currentIndex
            if (currentIdx >= 0 && currentIdx < queueSnapshot.size - 1) {
                // If it's in the manager but not ExoPlayer, force play it
                playMusic(queueSnapshot[currentIdx + 1])
            } else {
                playerViewModel.getNextAutoplayItem()?.let { playMusic(it) }
            }
        }
    }

    private fun playPrevious(currentItem: MusicItem?) {
        val controller = playbackManager.getController()
        if (controller != null && controller.hasPreviousMediaItem()) {
            playbackManager.skipToPrevious()
        } else {
            val queueSnapshot = playbackQueueManager.getQueueSnapshot()
            val currentIdx = playbackQueueManager.currentIndex
            if (currentIdx > 0) {
                playMusic(queueSnapshot[currentIdx - 1])
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release the MediaController connection when the Activity is permanently destroyed.
        // Only do this if playback is not active — if music is playing, the MusicService
        // keeps running independently and will clean up on its own.
        if (!playbackManager.isPlaying() && isFinishing) {
            playbackManager.release()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!preferenceManager.isBackgroundPlaybackEnabled && playbackManager.isPlaying()) {
            playbackManager.pause()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (playerViewModel.isVideoMode && playerViewModel.currentPlayingItem != null) {
            enterPipMode()
        } else if (preferenceManager.isFloatingPlayerEnabled && playerViewModel.currentPlayingItem != null) {
            checkOverlayPermissionAndStartService()
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerViewModel.setPipMode(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            // Hide anything that shouldn't be seen in PiP
        } else {
            // Restore UI
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Kill floating player when returning to main app
        stopService(android.content.Intent(this, com.vyllo.music.service.FloatingWindowService::class.java))
    }

    private fun checkOverlayPermissionAndStartService() {
        val intent = android.content.Intent(this, com.vyllo.music.service.FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant Overlay permission for Floating Player", Toast.LENGTH_LONG).show()
            val overlayIntent = android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(overlayIntent)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
        }
    }
}

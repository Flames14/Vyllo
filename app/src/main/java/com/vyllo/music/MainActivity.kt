package com.vyllo.music

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.media.AudioManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
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

    @Inject
    lateinit var homeScrollTuner: com.vyllo.music.presentation.scroll.HomeScrollTuner

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
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = 
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        // Start the playback service. Plain startService (not
        // startForegroundService): Media3's MediaSessionService promotes itself
        // to a foreground service on playback start. A foreground start here
        // would ANR at launch because nothing is playing yet and therefore no
        // startForeground() happens within the FGS timeout.
        startService(android.content.Intent(this, MusicService::class.java))
        playbackManager.initialize()

        handleIncomingIntent(intent)

        setContent {
            val isDark = ThemeManager.isDarkTheme(settingsViewModel.themeMode, isSystemInDarkTheme())
            val colorScheme = ThemeManager.getColorScheme(settingsViewModel.themeMode, isSystemInDarkTheme())
            
            MaterialTheme(
                colorScheme = colorScheme,
                typography = com.vyllo.music.presentation.theme.VylloTypography,
                shapes = com.vyllo.music.presentation.theme.VylloShapes
            ) {
                val systemUiController = remember(window) { WindowCompat.getInsetsController(window, window.decorView) }
                LaunchedEffect(isDark) {
                    systemUiController.isAppearanceLightStatusBars = !isDark
                    systemUiController.isAppearanceLightNavigationBars = !isDark
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    val homeScrollState = remember(homeScrollTuner) {
                        homeScrollTuner.createHomeState()
                    }
                    VylloNavigation(
                        playbackManager = playbackManager, 
                        homeViewModel = homeViewModel,
                        searchViewModel = searchViewModel,
                        libraryViewModel = libraryViewModel,
                        playerViewModel = playerViewModel,
                        settingsViewModel = settingsViewModel,
                        homeScrollTuner = homeScrollTuner,
                        homeScrollState = homeScrollState,
                        onPlay = { item -> playMusic(item) },
                        onPlayFromQueue = { item -> playMusic(item, fromQueue = true) },
                        onNext = { item -> playNext(item) },
                        onPrev = { item -> playPrevious(item) }
                    )

                    val playerState by playerViewModel.uiState.collectAsState()
                    com.vyllo.music.presentation.components.VolumeBoosterOverlay(
                        isVisible = playerState.isVolumeBoosterUIVisible,
                        multiplier = playerState.volumeBoostMultiplier,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    // Dynamic refresh rate listener
                    LaunchedEffect(settingsViewModel.isHighRefreshRateEnabled) {
                        setupDisplayMode()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "com.vyllo.music" && data.host == "oauth2redirect") {
            libraryViewModel.handleGoogleAuthRedirect(data)
        }
    }

    private fun setupDisplayMode() {
        // Display.getSupportedModes() requires API 30 (R).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val targetDisplay = display ?: return
        val isHighRefresh = preferenceManager.isHighRefreshRateEnabled
        val modes = targetDisplay.supportedModes
        if (modes.isEmpty()) return

        val targetMode = if (isHighRefresh) {
            modes.maxByOrNull { it.refreshRate }
        } else {
            // Find 60Hz mode for battery saver, or fallback to lowest refresh rate
            modes.firstOrNull { it.refreshRate in 59.0f..61.0f }
                ?: modes.minByOrNull { it.refreshRate }
        }

        targetMode?.let { mode ->
            val params = window.attributes
            if (params.preferredDisplayModeId != mode.modeId) {
                params.preferredDisplayModeId = mode.modeId
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

    private fun playMusic(item: MusicItem, fromQueue: Boolean = false) {
        val isVideo = playerViewModel.isVideoMode
        // Set loading state immediately so the player UI shows the spinner while resolving URL
        playerViewModel.setPlaybackLoading(true, item.url)
        lifecycleScope.launch {
            val result = playMusicUseCase.execute(item, isVideo = isVideo, keepQueue = fromQueue)
            playerViewModel.setPlaybackLoading(false)
            when (result) {
                is PlayResult.Success -> {
                    homeViewModel.addToRecentlyPlayed(item)
                    if (!fromQueue || playerViewModel.relatedSongs.isEmpty()) {
                        playerViewModel.loadRelatedSongs(item)
                    }
                }
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
        val queueSnapshot = playbackQueueManager.getQueueSnapshot()
        val currentIdx = playbackQueueManager.currentIndex
        if (currentIdx >= 0 && currentIdx < queueSnapshot.size - 1) {
            playMusic(queueSnapshot[currentIdx + 1], fromQueue = true)
        } else {
            playerViewModel.getNextAutoplayItem()?.let { playMusic(it, fromQueue = true) }
        }
    }

    private fun playPrevious(currentItem: MusicItem?) {
        val queueSnapshot = playbackQueueManager.getQueueSnapshot()
        val currentIdx = playbackQueueManager.currentIndex
        if (currentIdx > 0) {
            playMusic(queueSnapshot[currentIdx - 1], fromQueue = true)
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
        // Only pause for display-driven stops when background playback is disabled.
        // Activities stop for many non-background reasons (multi-window, PiP,
        // always-on-display, dialogs); pausing unconditionally looks exactly like a
        // track that dies when the screen locks.
        if (isChangingConfigurations) return
        if (!isInPictureInPictureModeCompat()) {
            if (!preferenceManager.isBackgroundPlaybackEnabled && playbackManager.isPlaying()) {
                playbackManager.pause()
            }
        }
    }

    private fun isInPictureInPictureModeCompat(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

            if (currentVolume >= maxVolume) {
                playerViewModel.adjustVolumeBoost(0.1f)
                return true
            }
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (playerViewModel.volumeBoostMultiplier > 1.0f) {
                playerViewModel.adjustVolumeBoost(-0.1f)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

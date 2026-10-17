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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.vyllo.music.R
import com.vyllo.music.VylloNavigation
import com.vyllo.music.domain.manager.PlaybackManager
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.PlayResult
import com.vyllo.music.service.MusicService
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

    @androidx.media3.common.util.UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setupDisplayMode()
        checkPermissions()
        maybePromptBatteryExemption()
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        applySystemNavigationBarMode()
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

        // Captured so the composition below can reach back into the activity
        // without casting LocalContext (which would be null-safe noise).
        val self = this

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
                        onNext = { playNext() },
                        onPrev = { playPrevious() }
                    )

                    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()

                    // Android 12+ PiP auto-enter follows the real playback mode:
                    // on for video with something loaded, off the moment either
                    // goes away, so backgrounding never pops a stale video tile.
                    LaunchedEffect(playerState.isVideoMode, playerState.currentPlayingItem) {
                        self.syncPipAutoEnter()
                    }

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

    /**
     * Keep the app's bottom edge clear of the system navigation bar. Users can
     * still reveal Android's controls with a swipe from the screen edge; Android
     * dismisses the transient bar again automatically.
     */
    private fun applySystemNavigationBarMode() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val navigationBars = WindowInsetsCompat.Type.navigationBars()
        if (isInPictureInPictureMode) {
            controller.show(navigationBars)
        } else {
            controller.hide(navigationBars)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemNavigationBarMode()
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

    /**
     * One-time rationale dialog before the system battery-exemption prompt.
     * Explains why (uninterrupted background playback + reliable alarms) and
     * never nags again once shown.
     */
    private fun maybePromptBatteryExemption() {
        if (com.vyllo.music.core.utils.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) return
        if (preferenceManager.batteryRationaleShown) return
        preferenceManager.batteryRationaleShown = true
        lifecycleScope.launch {
            kotlinx.coroutines.delay(1500L)
            if (isFinishing || isDestroyed) return@launch
            android.app.AlertDialog.Builder(this@MainActivity)
                .setTitle("Uninterrupted playback")
                .setMessage(
                    "Vyllo plays music with the screen off and can wake you with an alarm. " +
                    "Allow Vyllo to run unrestricted in the background so playback and " +
                    "alarms keep working during long sleep?"
                )
                .setPositiveButton("Allow") { _, _ ->
                    com.vyllo.music.core.utils.BatteryOptimizationHelper.requestExemptionIfNeeded(this@MainActivity)
                }
                .setNegativeButton("Not now", null)
                .show()
        }
    }

    // Thin delegates - orchestration lives in PlayerViewModel (testable, lifecycle-safe).
    private fun playMusic(item: MusicItem, fromQueue: Boolean = false) {
        playerViewModel.playTrack(item, fromQueue = fromQueue) { result ->
            if (result is PlayResult.Failure && !isFinishing && !isDestroyed) {
                Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_SHORT).show()
            } else if (result is PlayResult.Success) {
                homeViewModel.addToRecentlyPlayed(item)
            }
        }
    }

    private fun playNext() {
        playerViewModel.playNextFromQueue { result ->
            if (result is PlayResult.Failure && !isFinishing && !isDestroyed) {
                Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playPrevious() {
        playerViewModel.playPreviousFromQueue()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release the MediaController connection when the Activity is permanently destroyed.
        // Only do this if playback is not active ΓÇö if music is playing, the MusicService
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
        // Activity#isInPictureInPictureMode is API 24 == minSdk; no guard.
        return isInPictureInPictureMode
    }

    private fun supportsPip(): Boolean =
        packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /**
     * Picture-in-picture parameters for the current state.
     *
     * API 31+ additionally wants a source-rect hint (which region of the window
     * morphs into the PiP tile) and an auto-enter flag (re-enter PiP the next
     * time the app is backgrounded). Auto-enter is deliberately state-driven
     * rather than switched on once: leaving it on would pop a video tile even
     * after the user switches back to audio-only.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(autoEnter: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            videoSurfaceRectInWindow()?.let { builder.setSourceRectHint(it) }
            builder.setAutoEnterEnabled(autoEnter)
        }
        return builder.build()
    }

    /** Called whenever playback mode changes so auto-enter tracks real state. */
    private fun syncPipAutoEnter() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val enabled = playerViewModel.isVideoMode && playerViewModel.currentPlayingItem != null
        runCatching { setPictureInPictureParams(buildPipParams(enabled)) }
            .onFailure { SecureLogger.w("MainActivity", "setPictureInPictureParams failed", it) }
    }

    /** Bounds of the Media3 video surface, in window coordinates. */
    private fun videoSurfaceRectInWindow(): android.graphics.Rect? {
        val target = findVideoSurfaceView() ?: return null
        if (target.width <= 0 || target.height <= 0) return null
        val location = IntArray(2)
        target.getLocationInWindow(location)
        return android.graphics.Rect(
            location[0],
            location[1],
            location[0] + target.width,
            location[1] + target.height
        )
    }

    private fun findVideoSurfaceView(): android.view.View? {
        fun walk(view: android.view.View?): android.view.View? = when (view) {
            null -> null
            is androidx.media3.ui.PlayerView -> view
            is android.view.ViewGroup -> {
                for (i in 0 until view.childCount) walk(view.getChildAt(i))?.let { return it }
                null
            }
            else -> null
        }
        return walk(window.decorView)
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // enterPictureInPictureMode throws IllegalStateException on devices
        // without the PiP feature; there is nothing to enter into.
        if (!supportsPip()) return
        runCatching { enterPictureInPictureMode(buildPipParams(autoEnter = true)) }
            .onFailure { SecureLogger.w("MainActivity", "enterPictureInPictureMode failed", it) }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerViewModel.setPipMode(isInPictureInPictureMode)
        applySystemNavigationBarMode()
        // Leaving PiP: re-evaluate auto-enter from the *current* state, so a
        // user who switched to audio while in PiP does not re-enter next time.
        if (!isInPictureInPictureMode) syncPipAutoEnter()
        if (isInPictureInPictureMode) {
            // Hide anything that shouldn't be seen in PiP
        } else {
            // Restore UI
        }
    }
    
    override fun onResume() {
        super.onResume()
        applySystemNavigationBarMode()
        // Kill floating player when returning to main app
        val stopFloatingIntent = android.content.Intent(this, com.vyllo.music.service.FloatingWindowService::class.java)
        stopService(stopFloatingIntent)
    }

    private fun checkOverlayPermissionAndStartService() {
        val intent = android.content.Intent(this, com.vyllo.music.service.FloatingWindowService::class.java)
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.permission_grant_overlay), Toast.LENGTH_LONG).show()
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

package com.vyllo.music

import android.os.Build
import android.os.Bundle
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
import com.vyllo.music.domain.manager.PlaybackManager
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.service.MusicService
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.ui.theme.ThemeManager
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            android.util.Log.d("MainActivity", "Notification permission granted")
        } else {
            android.util.Log.w("MainActivity", "Notification permission denied")
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
            
            // Custom Coil image loader for better performance with RGB_565
            val imageLoader = remember {
                ImageLoader.Builder(context)
                    .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.30).build() }
                    .diskCache {
                        DiskCache.Builder()
                            .directory(context.cacheDir.resolve("image_cache"))
                            .maxSizeBytes(250L * 1024 * 1024)
                            .build()
                    }
                    .crossfade(false)
                    .allowHardware(true)
                    .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                    .build()
            }

            CompositionLocalProvider(LocalImageLoader provides imageLoader) {
                val isDark = ThemeManager.isDarkTheme(settingsViewModel.themeMode, isSystemInDarkTheme())
                val colorScheme = ThemeManager.getColorScheme(settingsViewModel.themeMode, isSystemInDarkTheme())
                
                MaterialTheme(colorScheme = colorScheme) {
                    val systemUiController = WindowCompat.getInsetsController(window, window.decorView)
                    systemUiController.isAppearanceLightStatusBars = !isDark

                    VylloApp(
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
        android.util.Log.d("MainActivity", "playMusic: ${item.title}, isVideo=$isVideo")
        
        playerViewModel.resolveStreamWithCallback(item, isVideo = isVideo) { streamUrl ->
            if (streamUrl != null) {
                lifecycleScope.launch {
                    playbackManager.playMusic(item, streamUrl, isVideo = isVideo)
                    playerViewModel.loadRelatedSongs(item)
                }
            } else {
                Toast.makeText(this, "Stream error - please retry", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playNext(currentItem: MusicItem?) {
        if (currentItem == null) return
        val list = if (searchViewModel.searchResults.isNotEmpty()) searchViewModel.searchResults 
                  else homeViewModel.uiState.value.trendingNowItems
        
        val index = list.indexOfFirst { it.title == currentItem.title }
        if (index != -1 && index < list.size - 1) {
            playMusic(list[index + 1])
        } else {
            playerViewModel.getNextAutoplayItem()?.let { playMusic(it) }
        }
    }

    private fun playPrevious(currentItem: MusicItem?) {
        if (currentItem == null) return
        val list = if (searchViewModel.searchResults.isNotEmpty()) searchViewModel.searchResults 
                  else homeViewModel.uiState.value.trendingNowItems
                  
        val index = list.indexOfFirst { it.title == currentItem.title }
        if (index > 0) playMusic(list[index - 1])
    }

    override fun onDestroy() {
        super.onDestroy()
        playbackManager.release()
    }

    override fun onStop() {
        super.onStop()
        if (!preferenceManager.isBackgroundPlaybackEnabled && playbackManager.isPlaying()) {
            playbackManager.pause()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (preferenceManager.isFloatingPlayerEnabled && playerViewModel.currentPlayingItem != null) {
            checkOverlayPermissionAndStartService()
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

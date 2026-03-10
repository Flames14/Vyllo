package com.example.musicpiped

import android.content.ComponentName
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.widget.Toast
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import com.example.musicpiped.data.SyncedLyricLine
import com.example.musicpiped.data.LyricsResponse
import com.example.musicpiped.data.LyricsResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.LocalImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.musicpiped.data.MusicItem
import com.example.musicpiped.data.MusicRepository
import com.example.musicpiped.service.MusicService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.sin
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.random.Random
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var mediaController by mutableStateOf<MediaController?>(null)
    private val viewModel: MusicViewModel by viewModels()
    private var transitionWakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Optimize for High Refresh Rate
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val window = window
            val display = window.windowManager.defaultDisplay
            val supportedModes = display.supportedModes
            
            supportedModes.maxByOrNull { it.refreshRate }?.let { maxMode ->
                val params = window.attributes
                params.preferredDisplayModeId = maxMode.modeId
                window.attributes = params
            }
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemBars()
        
        // Removed transitionWakeLock from Activity, moving to Service for robustness
        
        MusicRepository.init(applicationContext)


        startService(android.content.Intent(this, MusicService::class.java))

        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        
        controllerFuture.addListener({
            try { 
                val controller = controllerFuture.get()
                mediaController = controller
                
                // Add permanent continuous listener for background playback progression
                controller.addListener(object : androidx.media3.common.Player.Listener {
                    // All background transition logic moved to MusicService.kt
                    // for robustness against Activity suspension.
                })
            } catch (e: Exception) { e.printStackTrace() }
        }, androidx.core.content.ContextCompat.getMainExecutor(this))



        setContent {
            // Configure Coil ImageLoader with aggressive caching for smooth scrolling
            val context = LocalContext.current
            val imageLoader = remember {
                ImageLoader.Builder(context)
                    .memoryCache {
                        MemoryCache.Builder(context)
                            .maxSizePercent(0.30) // Use 30% of app memory for aggressive caching
                            .build()
                    }
                    .diskCache {
                        DiskCache.Builder()
                            .directory(context.cacheDir.resolve("image_cache"))
                            .maxSizeBytes(250L * 1024 * 1024) // 250MB disk cache
                            .build()
                    }
                    .crossfade(false) // Disable crossfade globally for scroll performance
                    .allowHardware(true) // GPU-accelerated bitmaps for faster rendering
                    .bitmapConfig(android.graphics.Bitmap.Config.RGB_565) // Half memory per pixel — fine for thumbnails
                    .build()
            }

            CompositionLocalProvider(LocalImageLoader provides imageLoader) {
            // Get theme preference from ViewModel
            val savedThemeMode = viewModel.themeMode
            val systemDarkTheme = isSystemInDarkTheme()
            
            val isDark = when (savedThemeMode) {
                "Light" -> false
                "Dark", "Ocean", "Forest", "Sunset", "AMOLED", "Midnight Gold", "Cyberpunk", "Lavender", "Crimson" -> true
                else -> systemDarkTheme // "System" default
            }
            
            val colorScheme = when (savedThemeMode) {
                "Ocean" -> darkColorScheme(
                    primary = Color(0xFF00BCD4),
                    onPrimary = Color.White,
                    background = Color(0xFF001F24),
                    onBackground = Color.White,
                    surface = Color(0xFF003840),
                    onSurface = Color.White,
                    surfaceVariant = Color(0xFF005662),
                    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
                    secondaryContainer = Color(0xFF008291),
                    onSecondaryContainer = Color.White
                )
                "Forest" -> darkColorScheme(
                    primary = Color(0xFF4CAF50),
                    onPrimary = Color.White,
                    background = Color(0xFF0F1B0F),
                    onBackground = Color.White,
                    surface = Color(0xFF1E381E),
                    onSurface = Color.White,
                    surfaceVariant = Color(0xFF2E552E),
                    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
                    secondaryContainer = Color(0xFF3E723E),
                    onSecondaryContainer = Color.White
                )
                "Sunset" -> darkColorScheme(
                    primary = Color(0xFFFF5722),
                    onPrimary = Color.White,
                    background = Color(0xFF2A0F05),
                    onBackground = Color.White,
                    surface = Color(0xFF3C1509),
                    onSurface = Color.White,
                    surfaceVariant = Color(0xFF5B1F0E),
                    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
                    secondaryContainer = Color(0xFF7A2913),
                    onSecondaryContainer = Color.White
                )
                "AMOLED" -> darkColorScheme(
                    primary = Color(0xFFE91E63),
                    onPrimary = Color.White,
                    background = Color.Black,
                    onBackground = Color.White,
                    surface = Color(0xFF111111),
                    onSurface = Color.White,
                    surfaceVariant = Color(0xFF222222),
                    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
                    secondaryContainer = Color(0xFF333333),
                    onSecondaryContainer = Color.White
                )
                "Midnight Gold" -> darkColorScheme(
                    primary = Color(0xFFFFC107),
                    onPrimary = Color.Black,
                    background = Color(0xFF121212),
                    onBackground = Color.White,
                    surface = Color(0xFF1E1E1E),
                    onSurface = Color.White,
                    surfaceVariant = Color(0xFF2C2C2C),
                    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
                    secondaryContainer = Color(0xFF3A3A3A),
                    onSecondaryContainer = Color.White
                )
                "Cyberpunk" -> darkColorScheme(
                    primary = Color(0xFF00FFCC),
                    onPrimary = Color.Black,
                    background = Color(0xFF0D0221),
                    onBackground = Color.White,
                    surface = Color(0xFF1A0A38),
                    onSurface = Color.White,
                    surfaceVariant = Color(0xFF2E1B59),
                    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
                    secondaryContainer = Color(0xFF3D2173),
                    onSecondaryContainer = Color.White
                )
                "Lavender" -> darkColorScheme(
                    primary = Color(0xFFB39DDB),
                    onPrimary = Color.Black,
                    background = Color(0xFF1A1A24),
                    onBackground = Color.White,
                    surface = Color(0xFF232331),
                    onSurface = Color.White,
                    surfaceVariant = Color(0xFF323246),
                    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
                    secondaryContainer = Color(0xFF43435C),
                    onSecondaryContainer = Color.White
                )
                "Crimson" -> darkColorScheme(
                    primary = Color(0xFFFF3333),
                    onPrimary = Color.White,
                    background = Color(0xFF1A0000),
                    onBackground = Color.White,
                    surface = Color(0xFF2A0000),
                    onSurface = Color.White,
                    surfaceVariant = Color(0xFF400000),
                    onSurfaceVariant = Color.White.copy(alpha = 0.7f),
                    secondaryContainer = Color(0xFF5A0000),
                    onSecondaryContainer = Color.White
                )
                else -> if (isDark) {
                    darkColorScheme(
                        primary = Color(0xFFE91E63), // YT Music Red accent
                        onPrimary = Color.White,
                        background = Color(0xFF0F0F0F), // Midnight Black
                        onBackground = Color.White,
                        surface = Color(0xFF161616), // Dark Grey Surface
                        onSurface = Color.White,
                        surfaceVariant = Color(0xFF212121),
                        onSurfaceVariant = Color.White.copy(alpha = 0.7f),
                        secondaryContainer = Color(0xFF2B2B2B),
                        onSecondaryContainer = Color.White
                    )
                } else {
                    lightColorScheme(
                        primary = Color(0xFFE91E63),
                        onPrimary = Color.White,
                        background = Color.White,
                        onBackground = Color.Black,
                        surface = Color(0xFFF5F5F5),
                        onSurface = Color.Black,
                        surfaceVariant = Color(0xFFEEEEEE),
                        onSurfaceVariant = Color.Black.copy(alpha = 0.7f)
                    )
                }
            }
            
            MaterialTheme(colorScheme = colorScheme) {
                // Set status bar color
                val systemUiController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                systemUiController.isAppearanceLightStatusBars = !isDark

                MusicAppContent(
                    mediaController, 
                    viewModel,
                    onPlay = { item -> playMusic(item, viewModel) },
                    onNext = { item -> playNext(viewModel, item) },
                    onPrev = { item -> playPrevious(viewModel, item) }
                )
            }
            } // Close CompositionLocalProvider
        }
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun playMusic(item: MusicItem, viewModel: MusicViewModel) {
        // Optimistic update: Show player immediately
        viewModel.currentPlayingItem = item
        
        // Update Repository Queue for Background Service source of truth
        // NEW logic: Always treat single song selections as "Autoplay/Radio" starts.
        // We only populate a full queue if we are playing from a curated playlist (todo).
        // For now, clearing and adding the single item ensures that loadRelatedSongs()
        // or the Service's own discovery logic determines the NEXT tracks.
        MusicRepository.currentQueue.clear()
        MusicRepository.currentQueue.add(item)
        MusicRepository.currentIndex = 0
        
        val controller = mediaController ?: return
        controller.stop()
        
        // Fix #5, #6, #7: Use atomic resolution from ViewModel
        viewModel.resolveStream(item) { streamUrl ->
            if (streamUrl != null) {
                val metadata = MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.uploader)
                    .setArtworkUri(android.net.Uri.parse(item.thumbnailUrl))
                    .build()
                val mediaItem = MediaItem.Builder()
                    .setMediaId(item.url) // Critical for background transition sync
                    .setUri(streamUrl)
                    .setMediaMetadata(metadata)
                    .build()
                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
                
                // Fetch suggestions for this song
                viewModel.loadRelatedSongs(item)
            } else {
                Toast.makeText(applicationContext, "Stream error - Please retry", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playNext(viewModel: MusicViewModel, currentItem: MusicItem?) {
        if (currentItem == null) return
        val list = if (viewModel.searchResults.isNotEmpty()) viewModel.searchResults else viewModel.homePlaylists
        val index = list.indexOfFirst { it.title == currentItem.title }
        
        if (index != -1 && index < list.size - 1) {
            playMusic(list[index + 1], viewModel)
        } else {
            // Try smart autoplay
            val nextItem = viewModel.getNextAutoplayItem()
            if (nextItem != null) {
                playMusic(nextItem, viewModel)
            }
        }
    }

    private fun playPrevious(viewModel: MusicViewModel, currentItem: MusicItem?) {
        if (currentItem == null) return
        val list = if (viewModel.searchResults.isNotEmpty()) viewModel.searchResults else viewModel.homePlaylists
        val index = list.indexOfFirst { it.title == currentItem.title }
        if (index > 0) {
            playMusic(list[index - 1], viewModel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MediaController.releaseFuture(controllerFuture)
    }

    override fun onStop() {
        super.onStop()
        // Handle Background Playback Toggle
        if (!viewModel.isBackgroundPlaybackEnabled) {
            val controller = if (controllerFuture.isDone) controllerFuture.get() else null
            if (controller?.isPlaying == true) {
                controller.pause()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Trigger bubble exactly when user leaves for home/recent
        if (viewModel.isFloatingEnabled && viewModel.currentPlayingItem != null) {
            checkOverlayPermissionAndStartService()
        }
    }
    
    // Resume checks
    override fun onResume() {
        super.onResume()
        // Stop floating service as soon as user returns to app
        stopService(android.content.Intent(this, com.example.musicpiped.service.FloatingWindowService::class.java))
    }

    private fun checkOverlayPermissionAndStartService() {
        val intent = android.content.Intent(this, com.example.musicpiped.service.FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant Overlay permission for Floating Player", Toast.LENGTH_LONG).show()
                val overlayIntent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")
                )
                startActivity(overlayIntent)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
        } else {
             startService(intent)
        }
    }
}


// ==========================================
// GOD-LEVEL DESIGN SYSTEM & COMPONENTS
// ==========================================

@Composable 
fun PremiumAccent() = MaterialTheme.colorScheme.primary
@Composable
fun GlassWhite() = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
@Composable
fun GlassBorder() = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)

@Composable
fun MusicAppContent(
    controller: MediaController?, 
    viewModel: MusicViewModel, 
    onPlay: (MusicItem) -> Unit,
    onNext: (MusicItem?) -> Unit,
    onPrev: (MusicItem?) -> Unit
) {
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var showSearchScreen by remember { mutableStateOf(false) }
    val scrollState = rememberLazyListState()

    // Infinite Scroll Logic
    val isAtBottom by remember {
       derivedStateOf {
           val layoutInfo = scrollState.layoutInfo
           val visibleItemsInfo = layoutInfo.visibleItemsInfo
           if (layoutInfo.totalItemsCount == 0) false
           else {
               val lastVisibleItem = visibleItemsInfo.lastOrNull()
               lastVisibleItem?.let { it.index >= layoutInfo.totalItemsCount - 3 } ?: false
           }
       }
    }
    LaunchedEffect(isAtBottom) {
        if (isAtBottom && viewModel.isSearching && showSearchScreen) viewModel.loadNextPage()
    }

    // Player Listener
    val context = LocalContext.current
    DisposableEffect(controller) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId ?: return
                // Try to find the full item from global queue or recently played
                val fullItem = MusicRepository.currentQueue.find { it.url == mediaId }
                
                if (fullItem != null) {
                    viewModel.currentPlayingItem = fullItem
                    // Also update repository index if needed (e.g. if transition happened in bg)
                    val idx = MusicRepository.currentQueue.indexOf(fullItem)
                    if (idx != -1) MusicRepository.currentIndex = idx
                } else if (mediaItem.mediaMetadata.title != null) {
                    // Fallback to basic reconstruction if not in queue
                    val meta = mediaItem.mediaMetadata
                    viewModel.currentPlayingItem = MusicItem(
                        title = meta.title.toString(),
                        uploader = meta.artist.toString(),
                        thumbnailUrl = meta.artworkUri.toString(),
                        url = mediaId
                    )
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("MainActivity", "Player Error: ${error.message}", error)
                Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }

    // Back Handler
    BackHandler(enabled = isPlayerExpanded || showSearchScreen) {
        when {
            isPlayerExpanded -> isPlayerExpanded = false
            showSearchScreen -> {
                showSearchScreen = false
                viewModel.onQueryChanged("")
            }
        }
    }

    // Background
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0,0,0,0),
            bottomBar = {
                // YTM Bottom Navigation
                if (!isPlayerExpanded) {
                    YTMBottomNavBar(
                        selectedTab = viewModel.selectedNavTab,
                        onTabSelected = { 
                            viewModel.selectedNavTab = it
                            showSearchScreen = false
                        },
                        hasActivePlayer = viewModel.currentPlayingItem != null
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (showSearchScreen) {
                    // Search Screen
                    YTMSearchScreen(
                        viewModel = viewModel,
                        onBack = { 
                            showSearchScreen = false
                            viewModel.onQueryChanged("")
                        },
                        onPlay = { item ->
                            viewModel.addToRecentlyPlayed(item)
                            onPlay(item)
                        },
                        currentPlayingItem = viewModel.currentPlayingItem,
                        scrollState = scrollState
                    )
                } else {
                    when (viewModel.selectedNavTab) {
                        0 -> {
                            // Home Screen
                            YTMHomeScreen(
                                viewModel = viewModel,
                                onPlay = { item ->
                                    viewModel.addToRecentlyPlayed(item)
                                    onPlay(item)
                                },
                                onSearchClick = { showSearchScreen = true },
                                onSettingsClick = { viewModel.showSettings = true },
                                currentPlayingItem = viewModel.currentPlayingItem
                            )
                        }
                        1 -> {
                            // Explore Screen
                            YTMExploreScreen(
                                viewModel = viewModel,
                                onPlay = { item ->
                                    viewModel.addToRecentlyPlayed(item)
                                    onPlay(item)
                                },
                                onSettingsClick = { viewModel.showSettings = true },
                                currentPlayingItem = viewModel.currentPlayingItem
                            )
                        }
                        2 -> {
                            // Downloads Screen
                            YTMDownloadsScreen(
                                viewModel = viewModel,
                                onPlay = { item ->
                                    viewModel.addToRecentlyPlayed(item)
                                    onPlay(item)
                                },
                                onSettingsClick = { viewModel.showSettings = true },
                                currentPlayingItem = viewModel.currentPlayingItem
                            )
                        }
                    }
                }

                // Settings Dialog
                if (viewModel.showSettings) {
                    SettingsDialog(
                        onDismiss = { viewModel.showSettings = false },
                        isFloatingEnabled = viewModel.isFloatingEnabled,
                        onFloatingEnabledChange = { viewModel.toggleFloatingPlayer(it) },
                        isBackgroundEnabled = viewModel.isBackgroundPlaybackEnabled,
                        onBackgroundEnabledChange = { viewModel.toggleBackgroundPlayback(it) },
                        themeMode = viewModel.themeMode,
                        onThemeModeChange = { viewModel.updateThemeMode(it) },
                        isLiquidScrollEnabled = viewModel.isLiquidScrollEnabled,
                        onLiquidScrollChange = { viewModel.toggleLiquidScroll(it) }
                    )
                }

                // Player Layer
                if (viewModel.currentPlayingItem != null) {
                    if(isPlayerExpanded) {
                         Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.8f)).clickable(enabled=false){})
                    }

                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        PremiumPlayerContainer(
                            musicItem = viewModel.currentPlayingItem,
                            isPlaying = isPlaying,
                            isLoading = viewModel.isLoadingPlayer,
                            controller = controller,
                            relatedSongs = viewModel.relatedSongs,
                            isAutoplayEnabled = viewModel.autoplayEnabled,
                            isExpanded = isPlayerExpanded,
                            onTogglePlay = { if (isPlaying) controller?.pause() else controller?.play() },
                            onNext = { onNext(viewModel.currentPlayingItem) },
                            onPrev = { onPrev(viewModel.currentPlayingItem) },
                            onExpand = { isPlayerExpanded = true },
                            onCollapse = { isPlayerExpanded = false },
                            onAutoplayToggle = { viewModel.autoplayEnabled = it },
                            onPlayRelated = { item -> viewModel.addToRecentlyPlayed(item); onPlay(item) },
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// YTM HOME SCREEN
// =========================================================================
@Composable
fun YTMHomeScreen(
    viewModel: MusicViewModel,
    onPlay: (MusicItem) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    currentPlayingItem: MusicItem?
) {
    // Memoize filterChips to avoid list allocation during recomposition
    val filterChips = remember { listOf("All", "Relax", "Energize", "Workout", "Focus", "Commute") }
    val scrollState = rememberLazyListState()
    val context = LocalContext.current
    
    // --- LIQUID SMOOTH SCROLL ---
    val liquidFlingBehavior = rememberLiquidFlingBehavior(viewModel.isLiquidScrollEnabled)
    
    // Memoize recommendedItems calculation for performance
    val recommendedItems = remember(viewModel.searchResults) { viewModel.searchResults.drop(8) }
    
    // Infinite scroll — only check after scroll settles (not every frame)
    LaunchedEffect(scrollState) {
        snapshotFlow {
            val layoutInfo = scrollState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 3
        }.collect { atBottom ->
            if (atBottom && viewModel.isSearching) viewModel.loadNextPage()
        }
    }
    
    // Aggressive image pre-cache — prefetch ALL sections at correct display sizes
    val imageLoader = coil.Coil.imageLoader(context)
    LaunchedEffect(viewModel.searchResults, viewModel.listenAgainItems, viewModel.chillItems, viewModel.mixedForYouItems) {
        // Prefetch thumbnails at actual display sizes to avoid cache misses during scroll
        val smallItems = viewModel.listenAgainItems + viewModel.chillItems + viewModel.workoutItems + viewModel.focusItems + viewModel.newReleasesItems
        val largeItems = viewModel.mixedForYouItems
        val gridItems = viewModel.searchResults.take(8) // Quick picks
        val listItems = viewModel.searchResults.drop(8).take(15) // Recommended rows
        
        // Small square cards (150dp = ~300px)
        smallItems.forEach { item ->
            imageLoader.enqueue(ImageRequest.Builder(context).data(item.thumbnailUrl).size(300, 300).build())
        }
        // Large cards (280dp wide = ~560px)
        largeItems.forEach { item ->
            imageLoader.enqueue(ImageRequest.Builder(context).data(item.thumbnailUrl).size(560, 320).build())
        }
        // Grid compact rows (48dp = ~150px)
        gridItems.forEach { item ->
            imageLoader.enqueue(ImageRequest.Builder(context).data(item.thumbnailUrl).size(150, 150).build())
        }
        // Recommended song rows (52dp = ~120px)
        listItems.forEach { item ->
            imageLoader.enqueue(ImageRequest.Builder(context).data(item.thumbnailUrl).size(120, 120).build())
        }
    }

    // GPU pre-warm hack removed — it was counter-productive, adding startup
    // overhead without reliably warming shaders. The real scroll perf wins
    // come from disabling crossfade, reducing render layers, and prefetching.
    
    LazyColumn(
        state = scrollState,
        flingBehavior = liquidFlingBehavior,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (currentPlayingItem != null) 140.dp else 16.dp)
    ) {
        // YTM Header
        item {
            YTMHeader(
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick
            )
        }
        
        // Filter Chips
        item {
            LazyRow(
                modifier = Modifier.padding(vertical = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    count = filterChips.size,
                    key = { index -> filterChips[index] }
                ) { index ->
                    YTMFilterChip(
                        text = filterChips[index],
                        isSelected = viewModel.selectedChip == filterChips[index],
                        onClick = { viewModel.onChipSelected(filterChips[index]) }
                    )
                }
            }
        }

        
        
        // Listen Again Section - Ultra optimized
        if (viewModel.listenAgainItems.isNotEmpty()) {
            item(key = "listen_again_header") {
                YTMSectionHeader(title = "Listen again")
            }
            item(key = "listen_again_row") {
                OptimizedHorizontalSection(
                    items = viewModel.listenAgainItems,
                    currentPlayingItem = currentPlayingItem,
                    onPlay = onPlay,
                    isLargeCard = false
                )
            }
        }
        
        // Quick Picks Section
        if (viewModel.quickPicksRows.isNotEmpty()) {
            item(key = "quick_picks_header") {
                Spacer(modifier = Modifier.height(24.dp))
                YTMSectionHeader(title = "Quick picks")
            }
            YTMGridSection(
                rows = viewModel.quickPicksRows.take(3), // Show 3 rows (6 items)
                currentPlayingItem = currentPlayingItem,
                onPlay = onPlay,
                viewModel = viewModel
            )
        }
        
        // Chill Vibes Section - Ultra optimized
        if (viewModel.chillItems.isNotEmpty()) {
            item(key = "chill_header") {
                Spacer(modifier = Modifier.height(24.dp))
                YTMSectionHeader(title = "Chill vibes")
            }
            item(key = "chill_row") {
                OptimizedHorizontalSection(
                    items = viewModel.chillItems,
                    currentPlayingItem = currentPlayingItem,
                    onPlay = onPlay,
                    isLargeCard = false
                )
            }
        }
        
        // Mixed For You Section (Large Cards) - Ultra optimized
        if (viewModel.mixedForYouItems.isNotEmpty()) {
            item(key = "mixed_header") {
                Spacer(modifier = Modifier.height(24.dp))
                YTMSectionHeader(title = "Mixed for you")
            }
            item(key = "mixed_row") {
                OptimizedHorizontalSection(
                    items = viewModel.mixedForYouItems,
                    currentPlayingItem = currentPlayingItem,
                    onPlay = onPlay,
                    isLargeCard = true
                )
            }
        }
        
        // Workout Music Section - Ultra optimized
        if (viewModel.workoutItems.isNotEmpty()) {
            item(key = "workout_header") {
                Spacer(modifier = Modifier.height(24.dp))
                YTMSectionHeader(title = "Workout")
            }
            item(key = "workout_row") {
                OptimizedHorizontalSection(
                    items = viewModel.workoutItems,
                    currentPlayingItem = currentPlayingItem,
                    onPlay = onPlay,
                    isLargeCard = false
                )
            }
        }
        
        // Focus Music Section - Ultra optimized
        if (viewModel.focusItems.isNotEmpty()) {
            item(key = "focus_header") {
                Spacer(modifier = Modifier.height(24.dp))
                YTMSectionHeader(title = "Focus")
            }
            item(key = "focus_row") {
                OptimizedHorizontalSection(
                    items = viewModel.focusItems,
                    currentPlayingItem = currentPlayingItem,
                    onPlay = onPlay,
                    isLargeCard = true
                )
            }
        }
        
        // Trending Now Section
        if (viewModel.trendingNowRows.isNotEmpty()) {
            item(key = "trending_header") {
                Spacer(modifier = Modifier.height(24.dp))
                YTMSectionHeader(title = "Trending now")
            }
            YTMGridSection(
                rows = viewModel.trendingNowRows.take(3), // Show 3 rows (6 items)
                currentPlayingItem = currentPlayingItem,
                onPlay = onPlay,
                viewModel = viewModel
            )
        }
        
        // New Releases Section - Ultra optimized
        if (viewModel.newReleasesItems.isNotEmpty()) {
            item(key = "new_releases_header") {
                Spacer(modifier = Modifier.height(24.dp))
                YTMSectionHeader(title = "New releases")
            }
            item(key = "new_releases_row") {
                OptimizedHorizontalSection(
                    items = viewModel.newReleasesItems,
                    currentPlayingItem = currentPlayingItem,
                    onPlay = onPlay,
                    isLargeCard = false
                )
            }
        }
        
        // Recommended Section (Vertical List) - Always show some
        // Recommended Section (Vertical List) - Always show if we have content
        if (viewModel.searchResults.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                YTMSectionHeader(title = "Recommended")
            }
            // Use drop(8) to skip only the 'Quick Picks', ensuring we show content even if list is short
            itemsIndexed(
                items = recommendedItems,
                key = { index, item -> "${item.url}_$index" },
                contentType = { _, _ -> "song_row" }
            ) { _, item ->
                YTMSongRow(
                    item = item,
                    isPlaying = currentPlayingItem?.title == item.title,
                    onClick = { onPlay(item) },
                    viewModel = viewModel
                )
            }
        }
        
        // Loading Indicator
        if (viewModel.isLoading || viewModel.isLoadingMore) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                }
            }
        }
    }
}

// =========================================================================
// YTM EXPLORE SCREEN
// =========================================================================
@Composable
fun YTMExploreScreen(
    viewModel: MusicViewModel,
    onPlay: (MusicItem) -> Unit,
    onSettingsClick: () -> Unit,
    currentPlayingItem: MusicItem?
) {
    val scrollState = rememberLazyListState()
    val liquidFlingBehavior = rememberLiquidFlingBehavior(viewModel.isLiquidScrollEnabled)

    LaunchedEffect(Unit) {
        viewModel.loadExploreContent()
    }

    LazyColumn(
        state = scrollState,
        flingBehavior = liquidFlingBehavior,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (currentPlayingItem != null) 140.dp else 16.dp)
    ) {
        // App Header
        item {
            MagazineHeader()
        }
        
        // Explore Categories
        item {
            LazyRow(
                modifier = Modifier.padding(vertical = 16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(viewModel.exploreCategories.size) { index ->
                    val category = viewModel.exploreCategories[index]
                    val isSelected = viewModel.selectedExploreCategory == category
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.onBackground 
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { viewModel.onExploreCategorySelected(category) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.background 
                                   else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Trending Section
        item {
            val title = if (viewModel.selectedExploreCategory == null) "Trending Now" else "${viewModel.selectedExploreCategory}"
            YTMSectionHeader(title = title)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (viewModel.isLoadingExplore) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            items(viewModel.exploreTrendingItems) { item ->
                YTMSongRow(
                    item = item,
                    isPlaying = currentPlayingItem?.title == item.title,
                    onClick = { onPlay(item) },
                    viewModel = viewModel
                )
            }
        }
    }
}

// Removed YTMTrendingScreen

// =========================================================================
// YTM DOWNLOADS SCREEN
// =========================================================================
@Composable
fun YTMDownloadsScreen(
    viewModel: MusicViewModel,
    onPlay: (MusicItem) -> Unit,
    onSettingsClick: () -> Unit,
    currentPlayingItem: MusicItem?
) {
    val scrollState = rememberLazyListState()
    
    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        item {
            YTMHeader(
                onSearchClick = {}, // Search not needed here
                onSettingsClick = onSettingsClick
            )
        }
        
        item {
            YTMSectionHeader(title = "Downloads")
        }
        
        if (viewModel.downloadedSongs.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.DownloadDone, 
                            contentDescription = null, 
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onBackground.copy(0.2f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Your offline music will appear here", 
                            style = MaterialTheme.typography.bodyLarge, 
                            color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
                        )
                    }
                }
            }
        } else {
            itemsIndexed(
                items = viewModel.downloadedSongs,
                key = { _, entry -> entry.url }
            ) { _, entry ->
                val musicItem = MusicItem(entry.title, entry.url, entry.uploader, entry.thumbnailUrl)
                YTMSongRow(
                    item = musicItem,
                    isPlaying = currentPlayingItem?.url == entry.url,
                    onClick = { onPlay(musicItem) },
                    viewModel = viewModel
                )
            }
        }
    }
}

// =========================================================================
// YTM HEADER
// =========================================================================
@Composable
fun YTMHeader(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 56.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.PlayCircleFilled,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Music",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Rounded.Search, "Search", tint = MaterialTheme.colorScheme.onBackground)
            }
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Rounded.Settings, "Settings", tint = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

// =========================================================================
// YTM FILTER CHIP
// =========================================================================
@Composable
fun YTMFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    
    Box(
        modifier = Modifier
            .height(36.dp)
            .graphicsLayer { 
                clip = true 
                shape = RoundedCornerShape(12.dp)
            }
            .background(bgColor)
            .then(
                if (!isSelected) Modifier.border(1.dp, MaterialTheme.colorScheme.onBackground.copy(0.1f), RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}

// =========================================================================
// YTM SECTION HEADER
// =========================================================================
@Composable
fun YTMSectionHeader(title: String, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        if (onSeeAll != null) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "See all",
                tint = MaterialTheme.colorScheme.onBackground.copy(0.6f)
            )
        }
    }
}

// =========================================================================
// OPTIMIZED HORIZONTAL CARD SECTION - LazyRow for true virtualization
// =========================================================================
@Composable
fun OptimizedHorizontalSection(
    items: List<MusicItem>,
    currentPlayingItem: MusicItem?,
    onPlay: (MusicItem) -> Unit,
    isLargeCard: Boolean = false
) {
    // Fixed height to prevent layout measurement during scroll
    val height = if (isLargeCard) 160.dp else 200.dp
    
    // Pre-compute the limited items list outside of composition
    val displayItems = remember(items) { items.take(6) }
    
    // Check playing state only once for all items
    val playingTitle = currentPlayingItem?.title
    
    // LazyRow: only compose visible cards (typically 2-3), not all 6
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            count = displayItems.size,
            key = { index -> "${displayItems[index].url}_$index" },
            contentType = { if (isLargeCard) "large_card" else "square_card" }
        ) { index ->
            val item = displayItems[index]
            val isPlaying = playingTitle == item.title
            val onClick = remember(item.url) { { onPlay(item) } }
            
            if (isLargeCard) {
                StaticLargeCard(
                    item = item,
                    isPlaying = isPlaying,
                    onClick = onClick
                )
            } else {
                StaticSquareCard(
                    item = item,
                    isPlaying = isPlaying,
                    onClick = onClick
                )
            }
        }
    }
}

// =========================================================================
// STATIC SQUARE CARD - Minimal recomposition version
// =========================================================================
@Composable
private fun StaticSquareCard(
    item: MusicItem,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(item.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(item.thumbnailUrl)
            .size(300, 300)
            .build()
    }
    
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
            )
            
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = item.uploader,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =========================================================================
// STATIC LARGE CARD - Minimal recomposition version
// =========================================================================
@Composable
private fun StaticLargeCard(
    item: MusicItem,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(item.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(item.thumbnailUrl)
            .size(500, 300)
            .build()
    }
    
    Box(
        modifier = Modifier
            .width(280.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
        )
        
        // Gradient overlay — memoized to avoid allocation per composition
        val gradientBrush = remember {
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(0.8f)),
                startY = 100f
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
        )
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.uploader,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
        
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// =========================================================================
// YTM SQUARE CARD (Listen Again style) - Ultra-optimized for scroll
// =========================================================================
@Composable
fun YTMSquareCard(
    item: MusicItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    animDelay: Int = 0,
    viewModel: MusicViewModel? = null
) {
    // Use fixed height to avoid layout measurement during scroll
    Column(
        modifier = Modifier
            .width(140.dp)
            .height(200.dp) // Fixed height for predictable layout
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null // Remove ripple for faster response
            ) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(0.1f))
        ) {
            val context = LocalContext.current
            val imageRequest = remember(item.thumbnailUrl) {
                ImageRequest.Builder(context)
                    .data(item.thumbnailUrl)
                    .size(280, 280) // Reduced size for faster decode
                    .crossfade(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Playing indicator only (download button removed for scroll perf)
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = item.uploader,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// =========================================================================
// YTM LARGE CARD (Mixed for you style) - Optimized for scroll
// =========================================================================
@Composable
fun YTMLargeCard(
    item: MusicItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    animDelay: Int = 0,
    viewModel: MusicViewModel? = null
) {
    Box(
        modifier = Modifier
            .width(280.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(0.1f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        val context = LocalContext.current
        val imageRequest = remember(item.thumbnailUrl) {
            ImageRequest.Builder(context)
                .data(item.thumbnailUrl)
                .size(560, 320) // Optimized size
                .crossfade(false)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            placeholder = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            error = androidx.compose.ui.graphics.painter.ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
        )
        
        // Gradient overlay — memoized to avoid allocation per composition
        val gradientBrush = remember {
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(0.8f)),
                startY = 60f
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
        )
        
        // Title overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.uploader,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(0.7f),
                maxLines = 1
            )
        }
        
        // Playing indicator
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.GraphicEq, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// =========================================================================
// YTM QUICK PICKS GRID (2 columns)
// =========================================================================
// =========================================================================
// YTM GRID SECTION (Optimized for 120Hz)
// =========================================================================
fun LazyListScope.YTMGridSection(
    rows: List<List<MusicItem>>,
    currentPlayingItem: MusicItem?,
    onPlay: (MusicItem) -> Unit,
    viewModel: MusicViewModel? = null
) {
    itemsIndexed(
        items = rows,
        key = { _: Int, chunk: List<MusicItem> -> 
            if (chunk.isNotEmpty()) "row_${chunk[0].url}" else "empty_row"
        },
        contentType = { _: Int, _: List<MusicItem> -> "grid_row" }
    ) { _, rowItems: List<MusicItem> ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rowItems.forEach { item ->
                YTMCompactRow(
                    item = item,
                    isPlaying = currentPlayingItem?.title == item.title,
                    onClick = { onPlay(item) },
                    modifier = Modifier.weight(1f),
                    viewModel = viewModel
                )
            }
            // Fill empty space if odd number
            if (rowItems.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        // Vertical spacing between rows
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// =========================================================================
// YTM COMPACT ROW (Quick picks item)
// =========================================================================
@Composable
fun YTMCompactRow(
    item: MusicItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MusicViewModel? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val context = LocalContext.current
        val imageRequest = remember(item.thumbnailUrl) {
            ImageRequest.Builder(context)
                .data(item.thumbnailUrl)
                .size(150, 150)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.uploader,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (viewModel != null) {
            DownloadButton(item, viewModel)
        }
    }
}

// =========================================================================
// YTM SONG ROW (For vertical list)
// =========================================================================
@Composable
fun YTMSongRow(
    item: MusicItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    viewModel: MusicViewModel? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val context = LocalContext.current
        val imageRequest = remember(item.thumbnailUrl) {
            ImageRequest.Builder(context)
                .data(item.thumbnailUrl)
                .size(120, 120)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .graphicsLayer { 
                    clip = true 
                    shape = RoundedCornerShape(6.dp)
                }
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.uploader,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1
            )
        }
        
        if (viewModel != null) {
            DownloadButton(item, viewModel)
        }
    }
}

@Composable
fun DownloadButton(item: MusicItem, viewModel: MusicViewModel) {
    // Optimized: Use derivedStateOf to prevent recomposition on every downloads list change
    val itemUrl = item.url
    val isDownloaded by remember(itemUrl) {
        derivedStateOf { viewModel.downloadedSongs.any { it.url == itemUrl } }
    }
    val progress by remember(itemUrl) {
        derivedStateOf { viewModel.downloadProgress[itemUrl] }
    }
    
    IconButton(
        onClick = {
            if (isDownloaded) {
                viewModel.deleteDownload(itemUrl)
            } else if (progress == null) {
                viewModel.downloadSong(item)
            }
        },
        modifier = Modifier.size(32.dp)
    ) {
        if (progress != null) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress!! / 100f },
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "${progress}%", 
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Icon(
                imageVector = if (isDownloaded) Icons.Rounded.CheckCircle else Icons.Rounded.DownloadForOffline,
                contentDescription = "Download",
                tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// =========================================================================
// YTM SEARCH SCREEN
// =========================================================================
@Composable
fun YTMSearchScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onPlay: (MusicItem) -> Unit,
    currentPlayingItem: MusicItem?,
    scrollState: LazyListState
) {
    val context = LocalContext.current

    // Speech recognition launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull() ?: ""
            if (spokenText.isNotBlank()) {
                viewModel.searchQuery = spokenText
                viewModel.performSearch(spokenText)
            }
        }
    }

    // Permission launcher for RECORD_AUDIO
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted — launch speech recognizer
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a song name or artist…")
            }
            viewModel.isListening = true
            speechLauncher.launch(intent)
        } else {
            Toast.makeText(context, "Microphone permission is needed for voice search", Toast.LENGTH_SHORT).show()
        }
    }

    // Mic button pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val micScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 48.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.onBackground.copy(0.08f))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = viewModel.searchQuery,
                        onValueChange = viewModel::onQueryChanged,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.performSearch(viewModel.searchQuery) }),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (viewModel.searchQuery.isEmpty()) {
                                Text("Search songs, albums, artists", color = MaterialTheme.colorScheme.onBackground.copy(0.4f))
                            }
                            innerTextField()
                        }
                    )

                    if (viewModel.searchQuery.isNotEmpty()) {
                        // Clear button when text is present
                        IconButton(
                            onClick = { viewModel.onQueryChanged("") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close, "Clear",
                                tint = MaterialTheme.colorScheme.onBackground.copy(0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        // Microphone button when field is empty
                        IconButton(
                            onClick = {
                                // Check if speech recognition is available
                                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                                    Toast.makeText(context, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                // Check/request RECORD_AUDIO permission
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a song name or artist…")
                                    }
                                    viewModel.isListening = true
                                    speechLauncher.launch(intent)
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Mic, "Voice Search",
                                tint = if (viewModel.isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(0.6f),
                                modifier = Modifier.size(22.dp).then(
                                    if (viewModel.isListening) Modifier.graphicsLayer(scaleX = micScale, scaleY = micScale) else Modifier
                                )
                            )
                        }
                    }
                }
            }
        }
        
        // Content
        if (!viewModel.isSearching && viewModel.suggestions.isNotEmpty()) {
            // Suggestions
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(
                    items = viewModel.suggestions,
                    key = { it }
                ) { suggestion ->
                    PremiumSuggestionRow(suggestion) { viewModel.performSearch(suggestion) }
                }
            }
        } else {
            // Search Results
            LazyColumn(
                state = scrollState,
                contentPadding = PaddingValues(bottom = if (currentPlayingItem != null) 140.dp else 16.dp)
            ) {
                itemsIndexed(
                    items = viewModel.searchResults,
                    key = { _, item -> item.url },
                    contentType = { _, _ -> "song_row" }
                ) { _, item ->
                    YTMSongRow(
                        item = item,
                        isPlaying = currentPlayingItem?.title == item.title,
                        onClick = { onPlay(item) },
                        viewModel = viewModel
                    )
                }
                
                if (viewModel.isLoadingMore || viewModel.isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// YTM BOTTOM NAVIGATION BAR
// =========================================================================
@Composable
fun YTMBottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    hasActivePlayer: Boolean
) {
    val tabs = listOf(
        Pair("Home", Icons.Rounded.Home),
        Pair("Explore", Icons.Rounded.Explore),
        Pair("Library", Icons.Rounded.LibraryMusic)
    )
    
    NavigationBar(
        modifier = Modifier.padding(bottom = if (hasActivePlayer) 72.dp else 0.dp),
        containerColor = MaterialTheme.colorScheme.background.copy(0.95f),
        tonalElevation = 0.dp
    ) {
        tabs.forEachIndexed { index, (label, icon) ->
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onBackground.copy(0.5f),
                    unselectedTextColor = MaterialTheme.colorScheme.onBackground.copy(0.5f),
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(0.1f)
                )
            )
        }
    }
}

// -------------------------------------------------------------------------
// COMPONENT: MESH GRADIENT BACKGROUND
// -------------------------------------------------------------------------
@Composable
fun MeshGradientBackground(modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT >= 33) {
        AuroraShader(modifier)
    } else {
        FallbackMeshGradient(modifier)
    }
}

// AGSL Shader for Android 13+ (API 33)
// AGSL Shader for Android 13+ (API 33)
@RequiresApi(33)
@Composable
fun AuroraShader(modifier: Modifier) {
    val time = remember { mutableFloatStateOf(0f) }
    val isDark = isSystemInDarkTheme()
    
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { 
                time.floatValue = (it / 1_000_000_000f) // seconds
            }
        }
    }
    
    val shader = remember(isDark) {
        if (Build.VERSION.SDK_INT >= 33) {
            // MONOCHROME SHADER
            val darkColor1 = if (isDark) "vec3(0.05, 0.05, 0.05)" else "vec3(0.95, 0.95, 0.95)"
            val darkColor2 = if (isDark) "vec3(0.15, 0.15, 0.15)" else "vec3(0.85, 0.85, 0.85)"
            val highlight  = if (isDark) "vec3(0.3, 0.3, 0.3)" else "vec3(0.7, 0.7, 0.7)"
            
            RuntimeShader("""
                uniform float2 resolution;
                uniform float time;
                
                vec4 main(vec2 fragCoord) {
                    vec2 uv = fragCoord / resolution.xy;
                    float t = time * 0.5;
                    
                    float r = sin(uv.x * 3.0 + t) * 0.5 + 0.5;
                    float g = sin(uv.y * 3.0 + t * 1.5) * 0.5 + 0.5;
                    float b = sin((uv.x + uv.y) * 3.0 + t * 0.5) * 0.5 + 0.5;
                    
                    vec3 color = mix($darkColor1, $darkColor2, r * g);
                    color = mix(color, $highlight, b * 0.5);
                    
                    return vec4(color, 1.0);
                }
            """.trimIndent())
        } else null
    }

    if (shader != null) {
        Canvas(modifier = modifier) {
            shader.setFloatUniform("resolution", size.width, size.height)
            shader.setFloatUniform("time", time.floatValue)
            drawRect(brush = ShaderBrush(shader))
        }
    } else {
        FallbackMeshGradient(modifier)
    }
}

@Composable
fun FallbackMeshGradient(modifier: Modifier) {
    // A simplified mesh gradient effect using blurred blobs (Monochrome)
    val infiniteTransition = rememberInfiniteTransition(label = "mesh")
    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse), label = "b1"
    )
    val offset2 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Reverse), label = "b2"
    )
    
    val isDark = isSystemInDarkTheme()
    val bg = MaterialTheme.colorScheme.background
    val blob1 = if(isDark) Color(0xFF202020) else Color(0xFFE0E0E0)
    val blob2 = if(isDark) Color(0xFF303030) else Color(0xFFD0D0D0)
    val blob3 = if(isDark) Color(0xFF151515) else Color(0xFFF0F0F0)

    Box(modifier = modifier.background(bg)) {
        // Blob 1
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-100).dp + (100.dp * offset1), y = (-100).dp + (50.dp * offset2))
                .size(400.dp)
                .alpha(0.4f)
                .background(Brush.radialGradient(listOf(blob1, Color.Transparent)))
                .blur(40.dp)
        )
        // Blob 2
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (100).dp - (100.dp * offset2), y = (100).dp - (50.dp * offset1))
                .size(500.dp)
                .alpha(0.3f)
                .background(Brush.radialGradient(listOf(blob2, Color.Transparent)))
                .blur(50.dp)
        )
        // Blob 3
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (200.dp * (offset1 - 0.5f)), y = (200.dp * (offset2 - 0.5f)))
                .size(300.dp)
                .alpha(0.2f)
                .background(Brush.radialGradient(listOf(blob3, Color.Transparent)))
                .blur(45.dp)
        )
        
        // Noise overlay (optional)
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.onBackground.copy(0.03f)))
    }
}

// -------------------------------------------------------------------------
// COMPONENT: MAGAZINE HEADER
// -------------------------------------------------------------------------
@Composable
fun MagazineHeader() {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..11 -> "Good\nMorning."
        in 12..17 -> "Good\nAfternoon."
        else -> "Good\nEvening."
    }
    
    Column {
        Text(
            text = greeting,
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
                lineHeight = 44.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Ready for some tunes?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(0.6f),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// -------------------------------------------------------------------------
// COMPONENT: GLASS SEARCH BAR
// -------------------------------------------------------------------------
@Composable
fun PremiumGlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit
) {
    val isSearching = query.isNotEmpty()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(GlassWhite())
            .border(1.dp, GlassBorder(), RoundedCornerShape(30.dp))
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSearching) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                }
            } else {
                Icon(Icons.Filled.Search, "Search", tint = MaterialTheme.colorScheme.onBackground.copy(0.6f))
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text("Search songs, artists...", color = MaterialTheme.colorScheme.onBackground.copy(0.4f), style = MaterialTheme.typography.titleMedium)
                    }
                    innerTextField()
                }
            )
            
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, "Clear", tint = MaterialTheme.colorScheme.onBackground.copy(0.6f))
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// COMPONENT: HERO RECOMMENDATION CARD
// -------------------------------------------------------------------------
@Composable
fun HeroRecommendationCard(item: MusicItem, isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .clip(RoundedCornerShape(32.dp))
            .clickable { onClick() }
    ) {
        // Background Image
        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(0.9f))
                    )
                )
        )
        
        // Content
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "BEST MATCH",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.uploader,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(0.8f)
            )
        }

        // Play Button Overlay
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .shadow(16.dp, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if(isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// -------------------------------------------------------------------------
// COMPONENT: PREMIUM SONG ROW
// -------------------------------------------------------------------------
@Composable
fun PremiumSongRow(item: MusicItem, isPlaying: Boolean, index: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .background(if (isPlaying) Color.White.copy(0.05f) else Color.Transparent)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier.size(64.dp)
        ) {
            // Actual Image
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.thumbnailUrl)
                    .crossfade(false) // Optimized for list scrolling
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
            )
            
            if (isPlaying) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.5f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = PremiumAccent(), modifier = Modifier.size(24.dp))
                }
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isPlaying) PremiumAccent() else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.uploader,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(0.5f),
                maxLines = 1
            )
        }
        
        // Optional: Duration or specialized action button could go here
    }
}

@Composable
fun PremiumSuggestionRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(GlassWhite()), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onBackground.copy(0.5f), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(text, color = MaterialTheme.colorScheme.onBackground.copy(0.9f), style = MaterialTheme.typography.bodyLarge)
    }
}


// -------------------------------------------------------------------------
// COMPONENT: PREMIUM PLAYER CONTAINER (WITH PHYSICS & VISUALIZER)
// -------------------------------------------------------------------------
enum class PlayerState { Collapsed, Expanded }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PremiumPlayerContainer(
    musicItem: MusicItem?, 
    isPlaying: Boolean,
    isLoading: Boolean,
    controller: MediaController?, 
    relatedSongs: List<MusicItem>,
    isAutoplayEnabled: Boolean,
    onTogglePlay: () -> Unit, 
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onExpand: () -> Unit, 
    onCollapse: () -> Unit, 
    isExpanded: Boolean,
    onAutoplayToggle: (Boolean) -> Unit,
    onPlayRelated: (MusicItem) -> Unit,
    viewModel: MusicViewModel
) {
    if (musicItem == null) return

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val screenHeightPx = with(density) { screenHeight.toPx() }
    
    // Direct height — no spring animation to avoid per-frame wake-ups
    val containerHeight = if (isExpanded) screenHeight else 80.dp
    
    val targetCorner = if(isExpanded) 0.dp else 24.dp
    
    // The Container
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isExpanded) Modifier.fillMaxSize() 
                else Modifier.height(containerHeight).padding(horizontal = 8.dp, vertical = 8.dp)
            )
            .shadow(
                elevation = if(isExpanded) 0.dp else 16.dp, 
                shape = RoundedCornerShape(topStart = targetCorner, topEnd = targetCorner, bottomStart = targetCorner, bottomEnd = targetCorner),
                spotColor = Color.Black
            )
            .clip(RoundedCornerShape(topStart = targetCorner, topEnd = targetCorner, bottomStart = targetCorner, bottomEnd = targetCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant) // Use surfaceVariant for mini player
            .clickable(enabled = !isExpanded) { 
                onExpand() 
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
    ) {
        if (isExpanded) {
             // For full screen, we want it to cover everything including status bars if possible, 
             // but here we just ensure it takes full space and handles swipe down.
             
             // Wrap in a vertical drag detector to allow swipe down
             val offsetY = remember { Animatable(0f) }
             val scope = rememberCoroutineScope()
             
             Box(
                 modifier = Modifier
                     .fillMaxSize()
                     .offset { androidx.compose.ui.unit.IntOffset(0, offsetY.value.toInt()) }
                     .draggable(
                         orientation = Orientation.Vertical,
                         state = rememberDraggableState { delta ->
                             scope.launch {
                                 // Only allow dragging down (positive) or resisting up
                                 val newOffset = offsetY.value + delta
                                 if (newOffset >= 0) offsetY.snapTo(newOffset)
                             }
                         },
                         onDragStopped = { velocity ->
                             if (offsetY.value > 300f || velocity > 1000f) {
                                 onCollapse()
                                 haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                             } else {
                                 offsetY.animateTo(0f)
                             }
                         }
                     )
             ) {
                ImmersiveBackground(imageUrl = musicItem.thumbnailUrl) {
                   // Particle System Overlay
                   ParticleSystem()
                   
                   ParticleSystem()
                   
                   PremiumFullScreenPlayer(
                       item = musicItem, 
                       isPlaying = isPlaying,
                       isLoading = isLoading,
                       controller = controller, 
                       relatedSongs = relatedSongs,
                       isAutoplayEnabled = isAutoplayEnabled,
                       onTogglePlay = onTogglePlay, 
                       onNext = onNext, 
                       onPrev = onPrev, 
                       onCollapse = onCollapse,
                       onAutoplayToggle = onAutoplayToggle,
                       onPlayRelated = onPlayRelated,
                       viewModel = viewModel
                   )
                }
             }
        } else {
             PremiumMiniPlayer(musicItem, isPlaying, isLoading, onTogglePlay)
        }
    }
}

@Composable
fun ParticleSystem() {
    val particles = remember { List(15) { Particle() } }
    
    // Animation loop
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f, 
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing)), label="time"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { p ->
            val x = (p.initialX + p.velocityX * time * size.width) % size.width
            val y = (p.initialY + p.velocityY * time * size.height) % size.height
            val alpha = (sin(time * 6.28f + p.initialX) + 1) / 2 * 0.6f
            
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = p.radius,
                center = Offset(x, y)
            )
        }
    }
}

data class Particle(
    val initialX: Float = Random.nextFloat(),
    val initialY: Float = Random.nextFloat(),
    val velocityX: Float = Random.nextFloat() * 0.2f - 0.1f,
    val velocityY: Float = Random.nextFloat() * 0.2f - 0.1f,
    val radius: Float = Random.nextFloat() * 3f + 1f
)

@Composable
fun AudioVisualizer(isPlaying: Boolean, modifier: Modifier = Modifier) {
    if (!isPlaying) return

    val infiniteTransition = rememberInfiniteTransition(label = "viz")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f, 
        targetValue = 1f, 
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Canvas(modifier = modifier.fillMaxWidth().height(60.dp)) {
        val barCount = 20
        val barWidth = size.width / barCount
        val maxBarHeight = size.height
        
        for (i in 0 until barCount) {
            // Pseudo-random height based on time and index to simulate audio data
            val noise = sin((time * 10) + i) 
            val heightInfo = (noise + 1) / 2 // Normalize to 0..1
            val barHeight = heightInfo * maxBarHeight * 0.8f + (maxBarHeight * 0.1f)
            
            drawRoundRect(
                color = Color.White.copy(alpha = 0.8f),
                topLeft = Offset(i * barWidth + 5f, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth - 10f, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
            )
        }
    }
}

@Composable
fun PremiumMiniPlayer(musicItem: MusicItem, isPlaying: Boolean, isLoading: Boolean, onTogglePlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val context = LocalContext.current
        val miniPlayerImageRequest = remember(musicItem.thumbnailUrl) {
            ImageRequest.Builder(context)
                .data(musicItem.thumbnailUrl)
                .size(120, 120)
                .build()
        }
        AsyncImage(
            model = miniPlayerImageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = musicItem.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = musicItem.uploader,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                maxLines = 1
            )
        }
        
        IconButton(onClick = onTogglePlay) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
            } else {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PremiumFullScreenPlayer(
    item: MusicItem, 
    isPlaying: Boolean, 
    isLoading: Boolean,
    controller: MediaController?, 
    relatedSongs: List<MusicItem>,
    isAutoplayEnabled: Boolean,
    onTogglePlay: () -> Unit, 
    onNext: () -> Unit, 
    onPrev: () -> Unit, 
    onCollapse: () -> Unit,
    onAutoplayToggle: (Boolean) -> Unit,
    onPlayRelated: (MusicItem) -> Unit,
    viewModel: MusicViewModel
) {
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Player, 1 = Lyrics
    
    var shuffleModeEnabled by remember { mutableStateOf(controller?.shuffleModeEnabled ?: false) }
    var repeatMode by remember { mutableIntStateOf(controller?.repeatMode ?: androidx.media3.common.Player.REPEAT_MODE_OFF) }

    LaunchedEffect(controller, isPlaying) {
        while (isPlaying) {
            currentPosition = controller?.currentPosition ?: 0L
            duration = controller?.duration ?: 0L
            if (duration < 0) duration = 0L
            
            // Sync lyrics in real-time
            viewModel.updateLyricsPosition(currentPosition)
            
            delay(500) // Faster update for smoother slider
        }
    }

    // Fetch lyrics whenever the track changes or duration becomes available
    LaunchedEffect(item.url, duration) {
        if (item.url.isBlank()) return@LaunchedEffect
        
        // If it's a new track, reset the tab
        // We use a derived key or state to only reset tab on URL change
        // but for simplicity, we'll just check if duration was just found
        val durationSecs = if (duration > 0) duration / 1000L else 0L
        
        // Trigger fetch. ViewModel's internal guard will prevent 
        // redundant fetches if duration hasn't changed from 0.
        viewModel.fetchLyrics(item, durationSecs)
    }
    
    // Independent tab reset on URL change
    LaunchedEffect(item.url) {
        selectedTab = 0
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // High-end background
        ImmersiveBackground(imageUrl = item.thumbnailUrl) {
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillParentMaxHeight()
                            .padding(top = 20.dp, bottom = 32.dp, start = 32.dp, end = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header handle
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onBackground.copy(0.3f))
                                .clickable { onCollapse() }
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onCollapse) {
                                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onBackground)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "PLAYING FROM", 
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                    color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
                                )
                                Text(
                                    "Search Results", 
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            // Empty IconButton to keep the title centered
                            IconButton(onClick = {}, enabled = false) {
                                Icon(Icons.Rounded.MoreVert, null, tint = Color.Transparent)
                            }
                        }

                        Spacer(modifier = Modifier.height(40.dp))
                        
                        // Large Artwork Card
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .padding(horizontal = 8.dp)
                        ) {
                             AsyncImage(
                                model = item.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))

                        // --- Player / Lyrics Tab Row ---
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            listOf("Player", "Lyrics").forEachIndexed { idx, label ->
                                val active = selectedTab == idx
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (active) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f) else Color.Transparent)
                                        .clickable { selectedTab = idx }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Normal),
                                        color = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // --- Tab Content ---
                        if (selectedTab == 0) {
                            // Info Section
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                     text = item.title, 
                                     style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), 
                                     color = MaterialTheme.colorScheme.onBackground, 
                                     maxLines = 1, 
                                     modifier = Modifier.basicMarquee()
                                )
                                Text(
                                     text = item.uploader, 
                                     style = MaterialTheme.typography.titleMedium, 
                                     color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                            IconButton(onClick = { /* Favorite */ }) {
                                Icon(Icons.Rounded.FavoriteBorder, null, tint = MaterialTheme.colorScheme.onBackground.copy(0.7f), modifier = Modifier.size(28.dp))
                            }
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        // Progress
                        Column {
                            Slider(
                                value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                                onValueChange = { newPercent -> 
                                    val newPos = (newPercent * duration).toLong()
                                    controller?.seekTo(newPos)
                                    currentPosition = newPos 
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.onBackground, 
                                    activeTrackColor = MaterialTheme.colorScheme.onBackground, 
                                    inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(0.2f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                 Text(formatTime(currentPosition), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
                                 Text(formatTime(duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Controls
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                             IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
                                 Icon(Icons.Rounded.SkipPrevious, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(36.dp))
                             }
                             
                             IconButton(
                                 onClick = { controller?.seekTo((currentPosition - 10000).coerceAtLeast(0)) }, 
                                 modifier = Modifier.size(48.dp)
                             ) {
                                 Icon(Icons.Rounded.Replay10, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(32.dp))
                             }
                             
                             Surface(
                                 modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .clickable { onTogglePlay() },
                                 color = MaterialTheme.colorScheme.onBackground,
                                 contentColor = MaterialTheme.colorScheme.background
                             ) {
                                 Box(contentAlignment = Alignment.Center) {
                                     if (isLoading) {
                                         CircularProgressIndicator(modifier = Modifier.size(40.dp), color = MaterialTheme.colorScheme.background, strokeWidth = 3.dp)
                                     } else {
                                         Icon(
                                             if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 
                                             null, 
                                             modifier = Modifier.size(48.dp)
                                         )
                                     }
                                 }
                             }
                             
                             IconButton(
                                 onClick = { controller?.seekTo((currentPosition + 10000).coerceAtMost(duration)) }, 
                                 modifier = Modifier.size(48.dp)
                             ) {
                                 Icon(Icons.Rounded.Forward10, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(32.dp))
                             }
                             
                             IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
                                 Icon(Icons.Rounded.SkipNext, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(36.dp))
                             }
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Bottom Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(onClick = {
                                shuffleModeEnabled = !shuffleModeEnabled
                                controller?.shuffleModeEnabled = shuffleModeEnabled
                            }) {
                                Icon(Icons.Rounded.Shuffle, null, tint = if (shuffleModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(0.5f))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp).clickable { 
                                coroutineScope.launch { listState.animateScrollToItem(3) } // Scroll to related songs
                            }) {
                                Icon(Icons.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.onBackground.copy(0.5f))
                                Spacer(Modifier.width(8.dp))
                                Text("UP NEXT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
                            }
                            IconButton(onClick = {
                                repeatMode = when (repeatMode) {
                                    androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
                                    androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
                                    else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                                }
                                controller?.repeatMode = repeatMode
                            }) {
                                val repeatIcon = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
                                Icon(repeatIcon, null, tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(0.5f))
                            }
                        }
                        } else {
                            // --- LYRICS TAB (Synced) ---
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                // Lyrics content area
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.White.copy(alpha = 0.05f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        viewModel.lyricsLoading -> {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                CircularProgressIndicator(modifier = Modifier.size(36.dp), color = Color.White.copy(0.7f), strokeWidth = 2.dp)
                                                Text("Fetching lyrics…", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.5f))
                                            }
                                        }
                                        viewModel.showLyricsSelector -> {
                                            // ALTERNATIVE LYRICS SELECTOR WITH SEARCH
                                            val alternatives = viewModel.lyricsResponse?.results ?: emptyList()
                                            val currentResult = viewModel.lyricsResponse?.result

                                            Column(modifier = Modifier.fillMaxSize()) {
                                                // Header
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        "Choose Lyrics",
                                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = Color.White
                                                    )
                                                    IconButton(onClick = {
                                                        viewModel.showLyricsSelector = false
                                                        viewModel.clearLyricsSearch()
                                                    }) {
                                                        Icon(Icons.Rounded.Close, null, tint = Color.White.copy(0.7f))
                                                    }
                                                }

                                                // Search bar
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(Color.White.copy(alpha = 0.10f))
                                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.Search, null,
                                                        tint = Color.White.copy(0.5f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    androidx.compose.material3.TextField(
                                                        value = viewModel.lyricsSearchQuery,
                                                        onValueChange = { viewModel.lyricsSearchQuery = it },
                                                        placeholder = {
                                                            Text(
                                                                "Search lyrics by song name...",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = Color.White.copy(0.3f)
                                                            )
                                                        },
                                                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                                        singleLine = true,
                                                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                                                            focusedContainerColor = Color.Transparent,
                                                            unfocusedContainerColor = Color.Transparent,
                                                            focusedIndicatorColor = Color.Transparent,
                                                            unfocusedIndicatorColor = Color.Transparent,
                                                            cursorColor = Color.White
                                                        ),
                                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                                                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                                            onSearch = { viewModel.searchForLyrics(viewModel.lyricsSearchQuery) }
                                                        ),
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (viewModel.lyricsSearchQuery.isNotBlank()) {
                                                        IconButton(
                                                            onClick = { viewModel.clearLyricsSearch() },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(Icons.Rounded.Close, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                    IconButton(
                                                        onClick = { viewModel.searchForLyrics(viewModel.lyricsSearchQuery) },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        if (viewModel.lyricsSearching) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(18.dp),
                                                                color = Color.White.copy(0.7f),
                                                                strokeWidth = 2.dp
                                                            )
                                                        } else {
                                                            Icon(Icons.Rounded.ArrowForward, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(20.dp))
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))

                                                // Scrollable results area
                                                val selectorScrollState = rememberScrollState()
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .verticalScroll(selectorScrollState)
                                                        .padding(horizontal = 12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    // Auto-matched section
                                                    if (alternatives.isNotEmpty()) {
                                                        Text(
                                                            "Auto-matched",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = Color.White.copy(0.4f),
                                                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                                                        )
                                                        alternatives.forEach { alt ->
                                                            LyricsCandidateCard(
                                                                result = alt,
                                                                isSelected = alt.id == currentResult?.id,
                                                                onClick = { viewModel.selectAlternativeLyrics(alt) }
                                                            )
                                                        }
                                                    }

                                                    // Search results section
                                                    if (viewModel.lyricsSearchResults.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(1.dp)
                                                                .background(Color.White.copy(alpha = 0.1f))
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            "Search results",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = Color.White.copy(0.4f),
                                                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                                                        )
                                                        viewModel.lyricsSearchResults.forEach { alt ->
                                                            LyricsCandidateCard(
                                                                result = alt,
                                                                isSelected = alt.id == currentResult?.id,
                                                                onClick = { viewModel.selectAlternativeLyrics(alt) }
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(12.dp))
                                                }
                                            }
                                        }
                                        viewModel.lyricsResponse == null -> {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Icon(Icons.Rounded.SearchOff, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(40.dp))
                                                Text(
                                                    "Lyrics not found",
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White.copy(0.7f)
                                                )
                                                Text(
                                                    "Try searching manually",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(0.4f)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                androidx.compose.material3.FilledTonalButton(
                                                    onClick = { viewModel.showLyricsSelector = true },
                                                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = Color.White.copy(alpha = 0.12f),
                                                        contentColor = Color.White
                                                    ),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Search for lyrics")
                                                }
                                            }
                                        }
                                        viewModel.syncedLyricsLines.isEmpty() -> {
                                            // Plain lyrics fallback (No sync data)
                                            val lyricsScrollState = rememberScrollState()
                                            Box(modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(lyricsScrollState)) {
                                                Column {
                                                    Text(
                                                        text = viewModel.lyricsResponse?.plainLyrics ?: "No plain lyrics",
                                                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 32.sp, fontWeight = FontWeight.Medium),
                                                        color = Color.White.copy(0.9f)
                                                    )
                                                    // Show translated plain lyrics below original
                                                    if (viewModel.isTranslationEnabled && viewModel.translatedPlainLyrics != null) {
                                                        Spacer(modifier = Modifier.height(16.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .height(1.dp)
                                                                .background(Color.White.copy(alpha = 0.15f))
                                                        )
                                                        Spacer(modifier = Modifier.height(12.dp))
                                                        Text(
                                                            text = "Translation",
                                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = Color.White.copy(0.4f)
                                                        )
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(
                                                            text = viewModel.translatedPlainLyrics!!,
                                                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 32.sp, fontWeight = FontWeight.Medium),
                                                            color = Color.White.copy(0.65f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        else -> {
                                            // FULL SYNCED LYRICS PLAYER
                                            val lyricsListState = rememberLazyListState()
                                            
                                            // Auto-scroll logic
                                            LaunchedEffect(viewModel.currentLyricIndex) {
                                                if (viewModel.currentLyricIndex >= 0) {
                                                    lyricsListState.animateScrollToItem(
                                                        index = viewModel.currentLyricIndex,
                                                        scrollOffset = -200 // Center roughly in view
                                                    )
                                                }
                                            }
                                            
                                            LazyColumn(
                                                state = lyricsListState,
                                                modifier = Modifier.fillMaxSize(),
                                                contentPadding = PaddingValues(top = 100.dp, bottom = 200.dp)
                                            ) {
                                                itemsIndexed(viewModel.syncedLyricsLines) { index, line ->
                                                    val isActive = viewModel.currentLyricIndex == index
                                                    val alpha by animateFloatAsState(if (isActive) 1f else 0.3f, label = "lyricAlpha")
                                                    val scale by animateFloatAsState(if (isActive) 1.05f else 1f, label = "lyricScale")
                                                    
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .graphicsLayer(alpha = alpha, scaleX = scale, scaleY = scale)
                                                            .clickable { 
                                                                controller?.seekTo(line.startTimeMs)
                                                                currentPosition = line.startTimeMs
                                                            }
                                                            .padding(vertical = 12.dp, horizontal = 20.dp)
                                                    ) {
                                                        Text(
                                                            text = line.content,
                                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                                                lineHeight = 38.sp
                                                            ),
                                                            color = Color.White,
                                                            textAlign = TextAlign.Start
                                                        )
                                                        // Show translated text below the original lyric line
                                                        if (viewModel.isTranslationEnabled) {
                                                            val translatedLine = viewModel.translatedLyricsLines.getOrNull(index)
                                                            if (translatedLine != null) {
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Text(
                                                                    text = translatedLine,
                                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                                        lineHeight = 22.sp,
                                                                        fontWeight = FontWeight.Normal
                                                                    ),
                                                                    color = Color.White.copy(0.5f),
                                                                    textAlign = TextAlign.Start
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Action buttons row
                                val hasAlternatives = (viewModel.lyricsResponse?.results?.size ?: 0) > 1
                                val hasLyrics = !viewModel.lyricsLoading && viewModel.lyricsResponse != null
                                val showActionRow = hasLyrics && (hasAlternatives || true) // Always show when lyrics exist
                                if (showActionRow) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Translate button — always available when lyrics exist
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (viewModel.isTranslationEnabled) MaterialTheme.colorScheme.primary.copy(0.2f)
                                                    else Color.Transparent
                                                )
                                                .clickable {
                                                    viewModel.toggleTranslation(!viewModel.isTranslationEnabled)
                                                }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (viewModel.isTranslating) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    color = Color.White.copy(0.7f),
                                                    strokeWidth = 1.5.dp
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Rounded.Translate, null,
                                                    tint = if (viewModel.isTranslationEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(0.5f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (viewModel.isTranslationEnabled) "Translated" else "Translate",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (viewModel.isTranslationEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(0.5f)
                                            )
                                        }

                                        // "Wrong lyrics?" button
                                        if (hasAlternatives) {
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable {
                                                        viewModel.showLyricsSelector = !viewModel.showLyricsSelector
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    if (viewModel.showLyricsSelector) Icons.Rounded.Close else Icons.Rounded.SwapHoriz,
                                                    null,
                                                    tint = Color.White.copy(0.5f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (viewModel.showLyricsSelector) "Back to lyrics" else "Wrong lyrics?",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = Color.White.copy(0.5f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // AUTOPLAY ROW
                item {
                    AutoplayRow(isChecked = isAutoplayEnabled, onToggle = onAutoplayToggle)
                }
                
                // RELATED SONGS HEADER
                if (relatedSongs.isNotEmpty()) {
                    item {
                        Text(
                            "Related Songs",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp)
                        )
                    }
                    
                    items(
                        items = relatedSongs,
                        key = { it.url }
                    ) { song ->
                        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                            PremiumSongRow(
                                item = song,
                                isPlaying = false,
                                index = 0,
                                onClick = { onPlayRelated(song) }
                            )
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(64.dp)) }
            }
        }
    }
}

@Composable
fun AutoplayRow(isChecked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("Autoplay", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text("Keep playing similar songs", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.5f))
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.White.copy(0.2f)
            )
        )
    }
}

// Keeping the helper formatTime
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun ImmersiveBackground(imageUrl: String, content: @Composable BoxScope.() -> Unit) {
    val bg = MaterialTheme.colorScheme.background
    
    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        if (imageUrl.isNotEmpty()) {
             AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.25f)
                    .blur(40.dp)
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            bg.copy(alpha = 0.4f),
                            bg.copy(alpha = 0.8f),
                            bg
                        )
                    )
                )
        )
        
        content()
    }
}

// ==========================================
// COMPONENT: LIQUID SMOOTH SCROLL BEHAVIOR
// ==========================================
/**
 * iOS-like scroll: long, smooth deceleration with low friction.
 * When [enabled] is false, returns the stock Android fling.
 */
@Composable
fun rememberLiquidFlingBehavior(enabled: Boolean): FlingBehavior {
    if (!enabled) return ScrollableDefaults.flingBehavior()

    val flingSpec = remember {
        androidx.compose.animation.core.exponentialDecay<Float>(
            frictionMultiplier = 0.35f,   // Lower = longer glide (iOS feel)
            absVelocityThreshold = 0.1f    // Let it decelerate to near-zero
        )
    }
    return remember(flingSpec) {
        object : FlingBehavior {
            override suspend fun androidx.compose.foundation.gestures.ScrollScope.performFling(
                initialVelocity: Float
            ): Float {
                if (kotlin.math.abs(initialVelocity) < 50f) return initialVelocity
                var lastValue = 0f
                val state = androidx.compose.animation.core.AnimationState(
                    initialValue = 0f,
                    initialVelocity = initialVelocity
                )
                state.animateDecay(flingSpec) {
                    val delta = value - lastValue
                    lastValue = value
                    val consumed = scrollBy(delta)
                    // Stop if we hit a boundary (consumed much less than expected)
                    if (delta != 0f && kotlin.math.abs(consumed / delta) < 0.5f) {
                        this.cancelAnimation()
                    }
                }
                return state.velocity
            }
        }
    }
}

// ==========================================
// COMPONENT: SETTINGS DIALOG
// ==========================================
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    isFloatingEnabled: Boolean,
    onFloatingEnabledChange: (Boolean) -> Unit,
    isBackgroundEnabled: Boolean,
    onBackgroundEnabledChange: (Boolean) -> Unit,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    isLiquidScrollEnabled: Boolean = false,
    onLiquidScrollChange: (Boolean) -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Settings", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // App Theme Selector
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "App Theme",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val themes = listOf("System", "Light", "Dark", "Ocean", "Forest", "Sunset", "AMOLED", "Midnight Gold", "Cyberpunk", "Lavender", "Crimson")
                        themes.forEach { theme ->
                            val isSelected = theme == themeMode
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable { onThemeModeChange(theme) }
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = theme,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                )
                            }
                        }
                    }
                }

                // Background Playback Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Background Playback",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Continue playing when app is closed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                        )
                    }
                    Switch(
                        checked = isBackgroundEnabled,
                        onCheckedChange = onBackgroundEnabledChange
                    )
                }

                // Floating Player Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Floating Player",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Show bubble when leaving app",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                        )
                    }
                    Switch(
                        checked = isFloatingEnabled,
                        onCheckedChange = onFloatingEnabledChange
                    )
                }

                // Liquid Smooth Scroll Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Liquid Smooth Scroll",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "iPhone-like buttery smooth scrolling",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                        )
                    }
                    Switch(
                        checked = isLiquidScrollEnabled,
                        onCheckedChange = onLiquidScrollChange
                    )
                }

                // Donation / Support
                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { uriHandler.openUri("https://ko-fi.com/betadeveloper") }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Support Development",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Buy me a coffee ☕",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                        )
                    }
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = "Donate",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        titleContentColor = MaterialTheme.colorScheme.onSurface
    )
}

// ==========================================
// COMPONENT: LYRICS CANDIDATE CARD
// ==========================================
@Composable
fun LyricsCandidateCard(
    result: LyricsResult,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val hasSynced = result.syncedLyrics != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) Color.White.copy(alpha = 0.18f)
                else Color.White.copy(alpha = 0.06f)
            )
            .clickable { if (!isSelected) onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Selection indicator
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) Color.White
                    else Color.White.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Rounded.Check,
                    null,
                    tint = Color.Black,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Track info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.trackName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = result.artistName + if (!result.albumName.isNullOrBlank()) " \u2022 ${result.albumName}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Synced/Plain badge
        Text(
            text = if (hasSynced) "Synced" else "Plain",
            style = MaterialTheme.typography.labelSmall,
            color = if (hasSynced) Color(0xFF4CAF50) else Color.White.copy(0.4f),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
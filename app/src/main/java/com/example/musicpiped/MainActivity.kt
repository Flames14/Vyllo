package com.example.musicpiped

import android.content.ComponentName
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
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

class MainActivity : ComponentActivity() {
    
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var mediaController by mutableStateOf<MediaController?>(null)
    private val viewModel: MusicViewModel by viewModels()

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
        
        MusicRepository.init(applicationContext)


        startService(android.content.Intent(this, MusicService::class.java))

        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        
        controllerFuture.addListener({
            try { mediaController = controllerFuture.get() } catch (e: Exception) { e.printStackTrace() }
        }, androidx.core.content.ContextCompat.getMainExecutor(this))

        setContent {
            // Configure Coil ImageLoader with aggressive caching for smooth scrolling
            val context = LocalContext.current
            val imageLoader = remember {
                ImageLoader.Builder(context)
                    .memoryCache {
                        MemoryCache.Builder(context)
                            .maxSizePercent(0.25) // Use 25% of app memory
                            .build()
                    }
                    .diskCache {
                        DiskCache.Builder()
                            .directory(context.cacheDir.resolve("image_cache"))
                            .maxSizeBytes(250L * 1024 * 1024) // 250MB disk cache
                            .build()
                    }
                    .crossfade(false) // Disable crossfade globally for scroll performance
                    .build()
            }

            CompositionLocalProvider(LocalImageLoader provides imageLoader) {
            // Get theme preference - use key to force recomposition when changed
            var themeKey by remember { mutableStateOf(0) }
            val savedThemeMode = MusicRepository.themeMode
            val systemDarkTheme = isSystemInDarkTheme()
            
            val darkTheme = when (savedThemeMode) {
                "Light" -> false
                "Dark" -> true
                else -> systemDarkTheme // "System" default
            }
            
            val colorScheme = if (darkTheme) {
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
            
            MaterialTheme(colorScheme = colorScheme) {
                // Set status bar color
                val systemUiController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                systemUiController.isAppearanceLightStatusBars = !darkTheme

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
                val mediaItem = MediaItem.Builder().setUri(streamUrl).setMediaMetadata(metadata).build()
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
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    onNext(viewModel.currentPlayingItem)
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaMetadata?.let { meta ->
                    val newItemTitle = meta.title.toString()
                    if (viewModel.currentPlayingItem?.title != newItemTitle) {
                         viewModel.currentPlayingItem = MusicItem(
                            title = newItemTitle,
                            uploader = meta.artist.toString(),
                            thumbnailUrl = meta.artworkUri.toString(),
                            url = "" 
                        )
                    }
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
                        onBackgroundEnabledChange = { viewModel.toggleBackgroundPlayback(it) }
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
                            onPlayRelated = { item -> viewModel.addToRecentlyPlayed(item); onPlay(item) }
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
    
    // --- CRAZY SMOOTH FLING IMPLEMENTATION ---
    // Spline-based decay gives that premium "iOS-like" fluid friction
    // This is the professional way to get that high-end Android scroll feel.
    val liquidFlingBehavior = ScrollableDefaults.flingBehavior()
    
    // Memoize recommendedItems calculation for performance
    val recommendedItems = remember(viewModel.searchResults) { viewModel.searchResults.drop(8) }
    
    // Infinite scroll trigger - moved outside items for performance
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = scrollState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 3
        }
    }
    
    LaunchedEffect(viewModel.searchResults) {
        if (viewModel.searchResults.isNotEmpty()) {
            // Warm up the image cache for the first 10 items
            viewModel.searchResults.take(10).forEach { item ->
                val request = ImageRequest.Builder(context)
                    .data(item.thumbnailUrl)
                    .size(150, 150)
                    .build()
                coil.Coil.imageLoader(context).enqueue(request)
            }
        }
    }

    // --- ADVANCED GPU PRE-WARM HACK (V2) ---
    // We render a small "Ghost List" of items off-screen.
    // This forces Compose to measure, layout, and compile shaders for 
    // multiple instances, making the actual scroll "cache-hot".
    Box(modifier = Modifier.size(1.dp).alpha(0.001f).graphicsLayer { translationX = -2000f }) {
        Column {
            // Warm up multiple Song Rows
            if (viewModel.searchResults.isNotEmpty()) {
                repeat(5) { index ->
                    val item = viewModel.searchResults.getOrNull(index) ?: viewModel.searchResults[0]
                    YTMSongRow(item = item, isPlaying = false, onClick = {})
                }
            }
            // Warm up multiple Grid Rows
            if (viewModel.quickPicksRows.isNotEmpty()) {
                repeat(2) { index ->
                    val row = viewModel.quickPicksRows.getOrNull(index) ?: viewModel.quickPicksRows[0]
                    Row {
                        YTMCompactRow(item = row[0], isPlaying = false, onClick = {})
                        if (row.size > 1) {
                            YTMCompactRow(item = row[1], isPlaying = false, onClick = {})
                        }
                    }
                }
            }
        }
    }
    
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
// OPTIMIZED HORIZONTAL CARD SECTION - Ultra-stable for 120Hz scrolling
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
    val displayItems = remember(items) { items.take(4) }
    
    // Check playing state only once for all items
    val playingTitle = currentPlayingItem?.title
    
    // Create a single remembered scroll state
    val scrollState = rememberScrollState()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { clip = true } // Hardware layer for the entire row
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        displayItems.forEach { item ->
            // Use url as stable key
            key(item.url) {
                // Pre-compute isPlaying to avoid lambda capture issues
                val isPlaying = playingTitle == item.title
                
                // Memoize the onClick to prevent recomposition
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
            .crossfade(true)
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
                modifier = Modifier.fillMaxSize()
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
            .crossfade(true)
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
            modifier = Modifier.fillMaxSize()
        )
        
        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(0.8f)),
                        startY = 100f
                    )
                )
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
            .graphicsLayer { clip = true }
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
            .graphicsLayer { clip = true }
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
            modifier = Modifier.fillMaxSize()
        )
        
        // Gradient overlay (light weight, no recomposition)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(0.8f)),
                        startY = 60f
                    )
                )
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
                .padding(horizontal = 16.dp)
                .graphicsLayer { clip = true }, // GPU optimization
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
            .graphicsLayer { 
                clip = true 
                shape = RoundedCornerShape(8.dp)
            }
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
                .crossfade(true)
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
                .crossfade(true)
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
                androidx.compose.foundation.text.BasicTextField(
                    value = viewModel.searchQuery,
                    onValueChange = viewModel::onQueryChanged,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.performSearch(viewModel.searchQuery) }),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (viewModel.searchQuery.isEmpty()) {
                            Text("Search songs, albums, artists", color = MaterialTheme.colorScheme.onBackground.copy(0.4f))
                        }
                        innerTextField()
                    }
                )
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
        Pair("Downloads", Icons.Rounded.Download)
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
    onPlayRelated: (MusicItem) -> Unit
) {
    if (musicItem == null) return

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val screenHeightPx = with(density) { screenHeight.toPx() }
    
    // Physics State - Manual Swipe Detection
    // Physics State - Manual Swipe Detection
    val animHeight by animateDpAsState(
        targetValue = if (isExpanded) screenHeight else 80.dp,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
        label = "Expansion"
    )
    
    val targetCorner = if(isExpanded) 0.dp else 24.dp
    
    // The Container
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isExpanded) Modifier.fillMaxSize() 
                else Modifier.height(animHeight).padding(horizontal = 8.dp, vertical = 8.dp)
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
                       onPlayRelated = onPlayRelated
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
        AsyncImage(
            model = musicItem.thumbnailUrl,
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
    onPlayRelated: (MusicItem) -> Unit
) {
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(controller, isPlaying) {
        while (isPlaying) {
            currentPosition = controller?.currentPosition ?: 0L
            duration = controller?.duration ?: 0L
            if (duration < 0) duration = 0L
            delay(500) // Faster update for smoother slider
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // High-end background
        ImmersiveBackground(imageUrl = item.thumbnailUrl) {
            LazyColumn(
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
                                .background(Color.White.copy(0.3f))
                                .clickable { onCollapse() }
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onCollapse) {
                                Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Color.White)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "PLAYING FROM", 
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                    color = Color.White.copy(0.5f)
                                )
                                Text(
                                    "Search Results", 
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                            IconButton(onClick = { /* More options */ }) {
                                Icon(Icons.Rounded.MoreVert, null, tint = Color.White)
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
                        
                        Spacer(modifier = Modifier.height(48.dp))
                        
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
                                     color = Color.White, 
                                     maxLines = 1, 
                                     modifier = Modifier.basicMarquee()
                                )
                                Text(
                                     text = item.uploader, 
                                     style = MaterialTheme.typography.titleMedium, 
                                     color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                            IconButton(onClick = { /* Favorite */ }) {
                                Icon(Icons.Rounded.FavoriteBorder, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(28.dp))
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
                                    thumbColor = Color.White, 
                                    activeTrackColor = Color.White, 
                                    inactiveTrackColor = Color.White.copy(0.2f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                 Text(formatTime(currentPosition), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.5f))
                                 Text(formatTime(duration), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.5f))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Controls
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                             IconButton(onClick = onPrev, modifier = Modifier.size(56.dp)) {
                                 Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp))
                             }
                             
                             Surface(
                                 modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .clickable { onTogglePlay() },
                                 color = Color.White,
                                 contentColor = Color.Black
                             ) {
                                 Box(contentAlignment = Alignment.Center) {
                                     if (isLoading) {
                                         CircularProgressIndicator(modifier = Modifier.size(40.dp), color = Color.Black, strokeWidth = 3.dp)
                                     } else {
                                         Icon(
                                             if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 
                                             null, 
                                             modifier = Modifier.size(48.dp)
                                         )
                                     }
                                 }
                             }
                             
                             IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                                 Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp))
                             }
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Bottom Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(Icons.Rounded.Shuffle, null, tint = Color.White.copy(0.5f))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { /* Show queue */ }) {
                                Icon(Icons.Rounded.QueueMusic, null, tint = Color.White.copy(0.5f))
                                Spacer(Modifier.width(8.dp))
                                Text("UP NEXT", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(0.5f))
                            }
                            Icon(Icons.Rounded.Repeat, null, tint = Color.White.copy(0.5f))
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
                            Color.Black.copy(alpha = 0.4f),
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
// COMPONENT: SETTINGS DIALOG
// ==========================================
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    isFloatingEnabled: Boolean,
    onFloatingEnabledChange: (Boolean) -> Unit,
    isBackgroundEnabled: Boolean,
    onBackgroundEnabledChange: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Settings", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
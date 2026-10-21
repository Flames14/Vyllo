package com.vyllo.music

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.media3.common.MediaItem
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vyllo.music.core.security.SecurityMonitor
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.ui.components.dialogs.SettingsDialog
import com.vyllo.music.presentation.components.common.PlaylistAddDialog
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.PlaylistEntity
import com.vyllo.music.domain.model.PlaylistFileCodec
import com.vyllo.music.domain.manager.PlaybackManager
import com.vyllo.music.presentation.components.*
import com.vyllo.music.presentation.components.common.YTMBottomNavBar
import com.vyllo.music.ui.home.*
import com.vyllo.music.ui.explore.*
import com.vyllo.music.ui.library.*
import com.vyllo.music.ui.search.*
import com.vyllo.music.ui.alarm.AlarmScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.vyllo.music.recognition.ui.RecognitionScreen
import com.vyllo.music.recognition.presentation.RecognitionViewModel
import com.vyllo.music.update.AppUpdateViewModel
import com.vyllo.music.update.AppUpdateDialog

private const val TAG = "VylloApp"

/**
 * CompositionLocal to provide LibraryViewModel throughout the app without prop drilling.
 */
val LocalLibraryViewModel = staticCompositionLocalOf<LibraryViewModel> {
    error("No LibraryViewModel provided")
}

/**
 * String routes for the app's [NavHost].
 *
 * The bottom-bar tabs (Home / Search / Explore / Library) are top-level routes; the
 * detail screens (Alarms, Playlist) are pushed on top and popped with system back.
 */
object VylloRoutes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val EXPLORE = "explore"
    const val LIBRARY = "library"
    const val RECOGNITION = "recognition"
    const val ALARMS = "alarms"

    const val PLAYLIST_ARG = "playlistId"
    const val PLAYLIST = "playlist/{$PLAYLIST_ARG}"
    fun playlist(playlistId: Long) = "playlist/$playlistId"

    const val ARTIST_ARG = "artistName"
    const val ARTIST = "artist/{$ARTIST_ARG}"
    fun artist(name: String) = "artist/${Uri.encode(name)}"
}

/** Bottom-bar index (0=Home, 1=Search, 2=Explore, 3=Library) for a route. */
private fun bottomTabForRoute(route: String?): Int = when (route) {
    VylloRoutes.SEARCH -> 1
    VylloRoutes.EXPLORE -> 2
    VylloRoutes.LIBRARY, VylloRoutes.PLAYLIST, VylloRoutes.ARTIST, VylloRoutes.ALARMS -> 3
    else -> 0
}

/**
 * Switches the top-level tab without growing the back stack, preserving each tab's
 * scroll/state (mirrors the old manual `selectedNavTab` behaviour).
 */
private fun NavHostController.navigateToTab(tab: Int) {
    val route = when (tab) {
        0 -> VylloRoutes.HOME
        1 -> VylloRoutes.SEARCH
        2 -> VylloRoutes.EXPLORE
        else -> VylloRoutes.LIBRARY
    }
    navigate(route) {
        popUpTo(VylloRoutes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Main UI entry point for the app.
 * Extracted from MainActivity because that file was getting way too big.
 */
@Composable
fun VylloNavigation(
    playbackManager: PlaybackManager, 
    homeViewModel: HomeViewModel,
    searchViewModel: SearchViewModel,
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel,
    homeScrollTuner: com.vyllo.music.presentation.scroll.HomeScrollTuner? = null,
    homeScrollState: androidx.compose.foundation.lazy.LazyListState? = null,
    onPlay: (MusicItem) -> Unit,
    onPlayFromQueue: (MusicItem) -> Unit = onPlay,
    onNext: (MusicItem?) -> Unit,
    onPrev: (MusicItem?) -> Unit,
    pendingRoute: String? = null,
    onPendingRouteConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    var isPlayerExpanded by rememberSaveable { mutableStateOf(false) }
    // Transient playback flag - must NOT survive process restore (controller dies).
    var isPlaying by remember { mutableStateOf(false) }

    val searchScrollState = rememberLazyListState()
    val recognitionViewModel: RecognitionViewModel = hiltViewModel()
    val updateViewModel: AppUpdateViewModel = hiltViewModel()

    // Slice player state to avoid whole-app recomposition on unrelated fields
    // (likeCount, lyricsLoading, equalizer...). Each slice only recomposes its readers.
    val currentPlayingItem by remember(playerViewModel) {
        playerViewModel.uiState.map { it.currentPlayingItem }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val isLoadingPlayer by remember(playerViewModel) {
        playerViewModel.uiState.map { it.isLoadingPlayer }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val loadingItemUrl by remember(playerViewModel) {
        playerViewModel.uiState.map { it.loadingItemUrl }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val relatedSongs by remember(playerViewModel) {
        playerViewModel.uiState.map { it.relatedSongs }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val pinnedPlaylistIds by remember(homeViewModel) {
        homeViewModel.uiState.map { it.pinnedPlaylistIds }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    // Full state kept only for fullscreen overlay that genuinely needs many fields.
    // Screens above read the slices, not this.

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isSearchVisible = currentRoute == VylloRoutes.SEARCH

    // External entry points (launcher shortcuts, quick-settings tile, deep links)
    // hand us a target route; navigate once and clear it so a recomposition or a
    // config change never re-navigates.
    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        when (route) {
            VylloRoutes.HOME, VylloRoutes.SEARCH, VylloRoutes.EXPLORE, VylloRoutes.LIBRARY ->
                navController.navigateToTab(bottomTabForRoute(route))
            else -> navController.navigate(route) { launchSingleTop = true }
        }
        onPendingRouteConsumed()
    }

    val appContext = LocalContext.current
    
    // Monitor device security risk on app start (informational only — does not gate features)
    LaunchedEffect(Unit) {
        val riskLevel = SecurityMonitor.getRiskLevel(appContext)
        when (riskLevel) {
            com.vyllo.music.core.security.SecurityRiskLevel.HIGH -> {
                SecureLogger.security(TAG, "High risk signals present (root/emulator/debug) — informational only")
            }
            com.vyllo.music.core.security.SecurityRiskLevel.MEDIUM -> {
                SecureLogger.w(TAG, "Medium risk signals present — informational only")
            }
            com.vyllo.music.core.security.SecurityRiskLevel.LOW -> {
                SecureLogger.d(TAG, "Low risk factors detected")
            }
            com.vyllo.music.core.security.SecurityRiskLevel.NONE -> {
                SecureLogger.d(TAG, "Device security check passed")
            }
        }
    }

    // Infinite scroll logic for search results
    val isAtBottom by remember {
       derivedStateOf {
           val layoutInfo = searchScrollState.layoutInfo
           val visibleItemsInfo = layoutInfo.visibleItemsInfo
           if (layoutInfo.totalItemsCount == 0) false
           else {
               val lastVisibleItem = visibleItemsInfo.lastOrNull()
               lastVisibleItem?.let { it.index >= layoutInfo.totalItemsCount - 3 } ?: false
           }
       }
    }

    LaunchedEffect(isAtBottom) {
        if (isAtBottom && searchViewModel.isSearching && isSearchVisible) {
            searchViewModel.loadNextPage()
        }
    }

    val context = LocalContext.current
    val isControllerConnected by playbackManager.isConnected.collectAsStateWithLifecycle(initialValue = playbackManager.isConnected())
    val controller = remember(isControllerConnected) { playbackManager.getController() }
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Sync playing state with the controller immediately and on future updates.
    // Re-runs when controller connects (cold start), not just once with null.
    DisposableEffect(controller, isControllerConnected) {
        isPlaying = controller?.isPlaying == true

        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isPlaying = controller?.isPlaying == true
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Playback error. Tap Retry to reconnect.",
                        actionLabel = "Retry",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        controller?.prepare()
                        controller?.play()
                    }
                }
            }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }

    LaunchedEffect(controller, isControllerConnected, currentPlayingItem?.url) {
        isPlaying = controller?.isPlaying == true
    }

    // The expanded-player overlay owns back while it is open; let the NavHost handle
    // back (popping subscreens) only when it is collapsed.
    LaunchedEffect(isPlayerExpanded) {
        navController.enableOnBackPressed(!isPlayerExpanded)
    }
    BackHandler(enabled = isPlayerExpanded) { isPlayerExpanded = false }

    CompositionLocalProvider(LocalLibraryViewModel provides libraryViewModel) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0,0,0,0),
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState)
                },
                bottomBar = {
                    if (!isPlayerExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Transparent)
                        ) {
                            if (currentPlayingItem != null) {
                                PremiumPlayerContainer(
                                    musicItem = currentPlayingItem,
                                    isPlaying = isPlaying,
                                    isLoading = isLoadingPlayer,
                                    controller = controller,
                                    relatedSongs = relatedSongs,
                                    onTogglePlay = { if (isPlaying) controller?.pause() else controller?.play() },
                                    onNext = { onNext(currentPlayingItem) },
                                    onPrev = { onPrev(currentPlayingItem) },
                                    onExpand = { isPlayerExpanded = true },
                                    onCollapse = { isPlayerExpanded = false },
                                    isExpanded = false,
                                    onPlayRelated = { item -> homeViewModel.addToRecentlyPlayed(item); onPlayFromQueue(item) },
                                    viewModel = playerViewModel
                                )
                            }
                            com.vyllo.music.presentation.components.VylloOfflineBanner()
                            YTMBottomNavBar(
                                selectedTab = bottomTabForRoute(currentRoute),
                                onTabSelected = { tab ->
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    navController.navigateToTab(tab)
                                }
                            )
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    var pendingPlaylistExport by remember {
                        mutableStateOf<Pair<PlaylistEntity, PlaylistFileCodec.Format>?>(null)
                    }
                    val playlistExportLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("*/*")
                    ) { uri ->
                        val pending = pendingPlaylistExport
                        pendingPlaylistExport = null
                        if (uri != null && pending != null) {
                            libraryViewModel.exportPlaylistToFile(
                                playlistId = pending.first.id,
                                playlistName = pending.first.name,
                                uri = uri,
                                format = pending.second
                            ) { ok ->
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        if (ok) R.string.playlist_export_success
                                        else R.string.playlist_export_failed
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                    val playlistImportLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        if (uri != null) {
                            libraryViewModel.importPlaylistFromFile(uri) { ok ->
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        if (ok) R.string.playlist_import_success
                                        else R.string.playlist_import_failed
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = VylloRoutes.HOME,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(VylloRoutes.HOME) {
                            YTMHomeScreen(
                                viewModel = homeViewModel,
                                onPlay = { item ->
                                    homeViewModel.addToRecentlyPlayed(item)
                                    onPlay(item)
                                },
                                onSearchClick = { navController.navigateToTab(1) },
                                onSettingsClick = { settingsViewModel.showSettings = true },
                                onRecognizeClick = { navController.navigate(VylloRoutes.RECOGNITION) },
                                onPlaylistClick = { playlist ->
                                    libraryViewModel.loadPlaylistSongs(playlist.id)
                                    navController.navigate(VylloRoutes.playlist(playlist.id))
                                },
                                currentPlayingItem = currentPlayingItem,
                                loadingItemUrl = loadingItemUrl,
                                scrollTuner = homeScrollTuner,
                                homeScrollState = homeScrollState
                                    ?: androidx.compose.foundation.lazy.rememberLazyListState()
                            )
                        }

                        composable(VylloRoutes.EXPLORE) {
                            YTMExploreScreen(
                                viewModel = homeViewModel,
                                onPlay = { item ->
                                    homeViewModel.addToRecentlyPlayed(item)
                                    onPlay(item)
                                },
                                onSearchClick = { navController.navigateToTab(1) },
                                onSettingsClick = { settingsViewModel.showSettings = true },
                                onRecognizeClick = { navController.navigate(VylloRoutes.RECOGNITION) },
                                currentPlayingItem = currentPlayingItem,
                                loadingItemUrl = loadingItemUrl
                            )
                        }

                        composable(VylloRoutes.LIBRARY) {
                            YTMLibraryScreen(
                                viewModel = libraryViewModel,
                                onPlay = { item ->
                                    homeViewModel.addToRecentlyPlayed(item)
                                    onPlay(item)
                                },
                                onSearchClick = { navController.navigateToTab(1) },
                                onSettingsClick = { settingsViewModel.showSettings = true },
                                onRecognizeClick = { navController.navigate(VylloRoutes.RECOGNITION) },
                                currentPlayingItem = currentPlayingItem,
                                loadingItemUrl = loadingItemUrl,
                                onNavigateToAlarms = { navController.navigate(VylloRoutes.ALARMS) },
                                onPlaylistClick = { playlist ->
                                    libraryViewModel.loadPlaylistSongs(playlist.id)
                                    navController.navigate(VylloRoutes.playlist(playlist.id))
                                },
                                onArtistClick = { artist ->
                                    navController.navigate(VylloRoutes.artist(artist.name))
                                },
                                onImportPlaylistClick = {
                                    playlistImportLauncher.launch(
                                        arrayOf(
                                            "audio/x-mpegurl",
                                            "audio/mpegurl",
                                            "application/vnd.ms-playlist",
                                            "text/csv",
                                            "text/plain",
                                            "text/*"
                                        )
                                    )
                                }
                            )
                        }

                        composable(VylloRoutes.SEARCH) {
                            // Reset the query whenever the search screen leaves the graph
                            // so re-opening it starts clean.
                            DisposableEffect(searchViewModel) {
                                onDispose { searchViewModel.onQueryChanged("") }
                            }
                            YTMSearchScreen(
                                viewModel = searchViewModel,
                                onBack = { navController.popBackStack() },
                                onPlay = { item ->
                                    homeViewModel.addToRecentlyPlayed(item)
                                    onPlay(item)
                                },
                                currentPlayingItem = currentPlayingItem,
                                scrollState = searchScrollState
                            )
                        }

                        composable(VylloRoutes.RECOGNITION) {
                            RecognitionScreen(
                                viewModel = recognitionViewModel,
                                onBack = { navController.popBackStack() },
                                onTrackFound = { item ->
                                    navController.popBackStack()
                                    homeViewModel.addToRecentlyPlayed(item)
                                    onPlay(item)
                                }
                            )
                        }

                        composable(VylloRoutes.ALARMS) {
                            AlarmScreen(onBackClick = { navController.popBackStack() })
                        }

                        composable(
                            route = VylloRoutes.PLAYLIST,
                            arguments = listOf(navArgument(VylloRoutes.PLAYLIST_ARG) { type = NavType.LongType })
                        ) { entry ->
                            val playlistId = entry.arguments?.getLong(VylloRoutes.PLAYLIST_ARG) ?: 0L
                            val playlist = libraryViewModel.allPlaylists.firstOrNull { it.id == playlistId }
                            if (playlist == null) {
                                // Deleted or unknown id: nothing to show, back out.
                                LaunchedEffect(playlistId) { navController.popBackStack() }
                            } else {
                                YTMPlaylistScreen(
                                    viewModel = libraryViewModel,
                                    playlist = playlist,
                                    onBack = { navController.popBackStack() },
                                    isPinned = playlist.id in pinnedPlaylistIds,
                                    onTogglePinned = { homeViewModel.togglePinnedPlaylist(playlist.id) },
                                    onPlay = { item ->
                                        homeViewModel.addToRecentlyPlayed(item)
                                        onPlay(item)
                                    },
                                    currentPlayingItem = currentPlayingItem,
                                    loadingItemUrl = loadingItemUrl,
                                    onExportM3u = {
                                        pendingPlaylistExport = playlist to PlaylistFileCodec.Format.M3U
                                        playlistExportLauncher.launch("${playlist.name}.m3u")
                                    },
                                    onExportCsv = {
                                        pendingPlaylistExport = playlist to PlaylistFileCodec.Format.CSV
                                        playlistExportLauncher.launch("${playlist.name}.csv")
                                    }
                                )
                            }
                        }

                        composable(
                            route = VylloRoutes.ARTIST,
                            arguments = listOf(navArgument(VylloRoutes.ARTIST_ARG) { type = NavType.StringType })
                        ) { entry ->
                            val artistName = entry.arguments?.getString(VylloRoutes.ARTIST_ARG).orEmpty()
                            YTMArtistScreen(
                                viewModel = libraryViewModel,
                                artistName = artistName,
                                onBack = { navController.popBackStack() },
                                onPlay = { item ->
                                    homeViewModel.addToRecentlyPlayed(item)
                                    onPlay(item)
                                },
                                currentPlayingItem = currentPlayingItem,
                                loadingItemUrl = loadingItemUrl
                            )
                        }
                    }

                    val exportLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.CreateDocument("application/json")
                    ) { uri ->
                        if (uri != null) {
                            settingsViewModel.exportBackup { json ->
                                try {
                                    context.contentResolver.openOutputStream(uri)?.use { out ->
                                        out.write(json.toByteArray(Charsets.UTF_8))
                                    }
                                    Toast.makeText(context, context.getString(R.string.backup_export_success), Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.backup_export_error), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }

                    val importLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        if (uri != null) {
                            try {
                                val json = context.contentResolver.openInputStream(uri)?.use { input ->
                                    input.bufferedReader().readText()
                                }
                                if (!json.isNullOrBlank()) {
                                    settingsViewModel.importBackup(json) { success ->
                                        val msg = if (success) R.string.backup_import_success else R.string.backup_import_error
                                        Toast.makeText(context, context.getString(msg), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.backup_import_error), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    // Global Dialogs
                    if (settingsViewModel.showSettings) {
                       LaunchedEffect(Unit) { settingsViewModel.refreshBatteryAccessState() }
                       SettingsDialog(
                           onDismiss = { settingsViewModel.showSettings = false },
                           isFloatingEnabled = settingsViewModel.isFloatingEnabled,
                           onFloatingEnabledChange = { settingsViewModel.toggleFloatingPlayer(it) },
                           isBackgroundEnabled = settingsViewModel.isBackgroundPlaybackEnabled,
                           onBackgroundEnabledChange = { settingsViewModel.toggleBackgroundPlayback(it) },
                           isBatteryUnrestricted = settingsViewModel.isBatteryUnrestricted,
                           onBatteryAccessClick = { settingsViewModel.requestBatteryUnrestricted(context) },
                           isKeepAudioPlayingEnabled = settingsViewModel.isKeepAudioPlayingEnabled,
                           onKeepAudioPlayingChange = { settingsViewModel.toggleKeepAudioPlaying(it) },
                           themeMode = settingsViewModel.themeMode,
                           onThemeModeChange = { settingsViewModel.updateThemeMode(it) },
                           languageTag = settingsViewModel.languageTag,
                           onLanguageChange = { settingsViewModel.updateLanguage(it) },
                           isHighRefreshRateEnabled = settingsViewModel.isHighRefreshRateEnabled,
                           onHighRefreshRateChange = { settingsViewModel.toggleHighRefreshRate(it) },
                           isBlockScreenshotsEnabled = settingsViewModel.isBlockScreenshotsEnabled,
                           onBlockScreenshotsChange = { settingsViewModel.toggleBlockScreenshots(it) },
                           wifiOnlyDownloads = settingsViewModel.wifiOnlyDownloads,
                           onWifiOnlyDownloadsChange = { settingsViewModel.toggleWifiOnlyDownloads(it) },
downloadsRequireBatteryOk = settingsViewModel.downloadsRequireBatteryOk,
                            onDownloadsRequireBatteryOkChange = { settingsViewModel.toggleDownloadsRequireBatteryOk(it) },
                            autoplayEnabled = playerViewModel.autoplayEnabled,
                            onAutoplayChange = { playerViewModel.autoplayEnabled = it },
                            isQueueSticky = playerViewModel.isQueueSticky,
                            onQueueStickyChange = { playerViewModel.isQueueSticky = it },
                            upNextIncludeVariants = playerViewModel.upNextIncludeVariants,
                            onUpNextIncludeVariantsChange = { playerViewModel.upNextIncludeVariants = it },
                           crossfadeSeconds = settingsViewModel.crossfadeSeconds,
                           onCrossfadeSecondsChange = { settingsViewModel.updateCrossfadeSeconds(it) },
                           loudnessPreset = settingsViewModel.loudnessPreset,
                           onLoudnessPresetChange = { settingsViewModel.updateLoudnessPreset(it) },
                           storageMediaBytes = settingsViewModel.cacheMediaBytes,
                           storageImageBytes = settingsViewModel.cacheImageBytes,
                           storageTotalBytes = settingsViewModel.cacheTotalBytes,
                           isClearingCache = settingsViewModel.isClearingCache,
                           onRefreshStorage = { settingsViewModel.refreshStorageUsage() },
                           onClearCacheClick = { settingsViewModel.clearCache() },
                           onExportBackupClick = { exportLauncher.launch("vyllo_backup_${System.currentTimeMillis()}.json") },
                           onImportBackupClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                           onCheckUpdateClick = { updateViewModel.checkForUpdates() }
                       )
                    }

                    // App Update Dialog
                    AppUpdateDialog(
                        viewModel = updateViewModel,
                        onDismiss = { /* Dialog handles internal hide, so no external toggle needed usually unless we want */ }
                    )

                    if (libraryViewModel.showPlaylistAddDialog) {
                       PlaylistAddDialog(
                           viewModel = libraryViewModel,
                           onDismiss = { libraryViewModel.showPlaylistAddDialog = false }
                       )
                    }
                }
            }
        }

        // FullScreen Player Overlay (renders over everything when expanded)
        if (isPlayerExpanded) {
            currentPlayingItem?.let { expandedPlayingItem ->
                PremiumFullScreenPlayer(
                    item = expandedPlayingItem,
                    isPlaying = isPlaying,
                    isLoading = isLoadingPlayer,
                    controller = controller,
                    relatedSongs = relatedSongs,
                    onTogglePlay = { if (isPlaying) controller?.pause() else controller?.play() },
                    onNext = { onNext(currentPlayingItem) },
                    onPrev = { onPrev(currentPlayingItem) },
                    onCollapse = { isPlayerExpanded = false },
                    onPlayRelated = { item -> homeViewModel.addToRecentlyPlayed(item); onPlayFromQueue(item) },
                    viewModel = playerViewModel
                )
            }
        }
    }
}

/**
 * Grid layout for home/explore screens.
 */
fun LazyListScope.YTMGridSection(
    rows: List<List<MusicItem>>,
    currentPlayingItem: MusicItem?,
    onPlay: (MusicItem) -> Unit,
    loadingItemUrl: String? = null,
    sectionKey: String = "grid"
) {
    item {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = rows,
                key = { index, colItems -> "${sectionKey}_col_${index}_${colItems.firstOrNull()?.url ?: "empty"}" },
                contentType = { _, _ -> "grid_column" }
            ) { _, columnItems ->
                Column(
                    modifier = Modifier.width(300.dp), // Standard width for YT Music compact row columns
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    columnItems.forEach { item ->
                        YTMCompactRow(
                            item = item,
                            isPlaying = currentPlayingItem?.url == item.url,
                            onClick = { onPlay(item) },
                            modifier = Modifier.fillMaxWidth(),
                            isLoading = loadingItemUrl == item.url
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

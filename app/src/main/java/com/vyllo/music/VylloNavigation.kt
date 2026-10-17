package com.vyllo.music

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.vyllo.music.core.security.SecurityMonitor
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.ui.components.dialogs.SettingsDialog
import com.vyllo.music.presentation.components.common.PlaylistAddDialog
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.manager.PlaybackManager
import com.vyllo.music.presentation.components.*
import com.vyllo.music.presentation.components.common.YTMBottomNavBar
import com.vyllo.music.ui.home.*
import com.vyllo.music.ui.explore.*
import com.vyllo.music.ui.library.*
import com.vyllo.music.ui.search.*
import com.vyllo.music.ui.alarm.AlarmScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.vyllo.music.ui.alarm.AlarmViewModel
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
    onPrev: (MusicItem?) -> Unit
) {
    var isPlayerExpanded by rememberSaveable { mutableStateOf(false) }
    // Transient playback flag - must NOT survive process restore (controller dies).
    var isPlaying by remember { mutableStateOf(false) }
    var showSearchScreen by rememberSaveable { mutableStateOf(false) }
    var showAlarmScreen by rememberSaveable { mutableStateOf(false) }
    var showRecognitionScreen by rememberSaveable { mutableStateOf(false) }
    
    val scrollState = rememberLazyListState()
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
    // Full state kept only for fullscreen overlay that genuinely needs many fields.
    // Screens above read the slices, not this.

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
        if (isAtBottom && searchViewModel.isSearching && showSearchScreen) {
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

    // Handle back button behavior
    BackHandler(enabled = isPlayerExpanded || showSearchScreen || showAlarmScreen || showRecognitionScreen) {
        when {
            isPlayerExpanded -> isPlayerExpanded = false
            showAlarmScreen -> showAlarmScreen = false
            showRecognitionScreen -> showRecognitionScreen = false
            showSearchScreen -> {
                showSearchScreen = false
                searchViewModel.onQueryChanged("")
            }
        }
    }

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
                                selectedTab = if (showSearchScreen) 1 else if (homeViewModel.selectedNavTab == 0) 0 else homeViewModel.selectedNavTab + 1,
                                onTabSelected = { 
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    if (it == 1) {
                                        showSearchScreen = true
                                    } else {
                                        showSearchScreen = false
                                        homeViewModel.selectedNavTab = if (it == 0) 0 else it - 1
                                    }
                                    showRecognitionScreen = false
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
                    // Navigation Logic
                    if (showSearchScreen) {
                        YTMSearchScreen(
                            viewModel = searchViewModel,
                            onBack = { 
                                showSearchScreen = false
                                searchViewModel.onQueryChanged("")
                            },
                            onPlay = { item ->
                                homeViewModel.addToRecentlyPlayed(item)
                                onPlay(item)
                            },
                            currentPlayingItem = currentPlayingItem,
                            scrollState = scrollState
                        )
                    } else if (showRecognitionScreen) {
                         RecognitionScreen(
                            viewModel = recognitionViewModel,
                            onBack = { showRecognitionScreen = false },
                            onTrackFound = { item ->
                                showRecognitionScreen = false
                                homeViewModel.addToRecentlyPlayed(item)
                                onPlay(item)
                            }
                        )
                    } else {
                        when (homeViewModel.selectedNavTab) {
                            0 -> YTMHomeScreen(
                                viewModel = homeViewModel,
                                onPlay = { item ->
                                    homeViewModel.addToRecentlyPlayed(item)
                                    onPlay(item)
                                },
                                onSearchClick = { showSearchScreen = true },
                                onSettingsClick = { settingsViewModel.showSettings = true },
                                onRecognizeClick = { showRecognitionScreen = true },
                                currentPlayingItem = currentPlayingItem,
                                loadingItemUrl = loadingItemUrl,
                                scrollTuner = homeScrollTuner,
                                homeScrollState = homeScrollState
                                    ?: androidx.compose.foundation.lazy.rememberLazyListState()
                            )
                            1 -> YTMExploreScreen(
                                viewModel = homeViewModel,
                                onPlay = { item ->
                                    homeViewModel.addToRecentlyPlayed(item)
                                    onPlay(item)
                                },
                                onSearchClick = { showSearchScreen = true },
                                onSettingsClick = { settingsViewModel.showSettings = true },
                                onRecognizeClick = { showRecognitionScreen = true },
                                currentPlayingItem = currentPlayingItem,
                                loadingItemUrl = loadingItemUrl
                            )
                            2 -> {
                                // Library/Playlist/Alarm navigation
                                if (showAlarmScreen) {
                                    AlarmScreen(
                                        onBackClick = { showAlarmScreen = false }
                                    )
                                } else {
                                    val selectedPlaylist = libraryViewModel.selectedLocalPlaylist
                                    if (selectedPlaylist != null) {
                                        YTMPlaylistScreen(
                                            viewModel = libraryViewModel,
                                            playlist = selectedPlaylist,
                                            onBack = { libraryViewModel.selectedLocalPlaylist = null },
                                            onPlay = { item ->
                                                homeViewModel.addToRecentlyPlayed(item)
                                                onPlay(item)
                                            },
                                            currentPlayingItem = currentPlayingItem,
                                            loadingItemUrl = loadingItemUrl
                                        )
                                    } else {
                                        YTMLibraryScreen(
                                            viewModel = libraryViewModel,
                                            onPlay = { item ->
                                                homeViewModel.addToRecentlyPlayed(item)
                                                onPlay(item)
                                            },
                                            onSearchClick = { showSearchScreen = true },
                                            onSettingsClick = { settingsViewModel.showSettings = true },
                                            onRecognizeClick = { showRecognitionScreen = true },
                                            currentPlayingItem = currentPlayingItem,
                                            loadingItemUrl = loadingItemUrl,
                                            onNavigateToAlarms = { showAlarmScreen = true }
                                        )
                                    }
                                }
                            }
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
                           isHighRefreshRateEnabled = settingsViewModel.isHighRefreshRateEnabled,
                           onHighRefreshRateChange = { settingsViewModel.toggleHighRefreshRate(it) },
                           wifiOnlyDownloads = settingsViewModel.wifiOnlyDownloads,
                           onWifiOnlyDownloadsChange = { settingsViewModel.toggleWifiOnlyDownloads(it) },
downloadsRequireBatteryOk = settingsViewModel.downloadsRequireBatteryOk,
                            onDownloadsRequireBatteryOkChange = { settingsViewModel.toggleDownloadsRequireBatteryOk(it) },
                            autoplayEnabled = playerViewModel.autoplayEnabled,
                            onAutoplayChange = { playerViewModel.autoplayEnabled = it },
                            isQueueSticky = playerViewModel.isQueueSticky,
                            onQueueStickyChange = { playerViewModel.isQueueSticky = it },
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


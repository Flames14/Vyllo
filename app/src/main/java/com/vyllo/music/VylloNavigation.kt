package com.vyllo.music

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.vyllo.music.core.security.SecurityMonitor
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.ui.components.SettingsDialog
import com.vyllo.music.presentation.components.PlaylistAddDialog
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.manager.PlaybackManager
import com.vyllo.music.presentation.components.*
import com.vyllo.music.ui.home.*
import com.vyllo.music.ui.explore.*
import com.vyllo.music.ui.library.*
import com.vyllo.music.ui.search.*
import com.vyllo.music.ui.alarm.AlarmScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.vyllo.music.ui.alarm.AlarmViewModel

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
    onPlay: (MusicItem) -> Unit,
    onNext: (MusicItem?) -> Unit,
    onPrev: (MusicItem?) -> Unit
) {
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var showSearchScreen by remember { mutableStateOf(false) }
    var showAlarmScreen by remember { mutableStateOf(false) }
    val scrollState = rememberLazyListState()

    // Collect player UI state so Compose recomposes when player state changes
    val playerUiState by playerViewModel.uiState.collectAsState()
    
    val appContext = LocalContext.current
    
    // Security: Monitor device security risk on app start
    LaunchedEffect(Unit) {
        val riskLevel = SecurityMonitor.getRiskLevel(appContext)
        when (riskLevel) {
            com.vyllo.music.core.security.SecurityRiskLevel.HIGH -> {
                SecureLogger.security(TAG, "High risk device detected - some features may be limited")
                // Optionally disable sensitive features like downloads
            }
            com.vyllo.music.core.security.SecurityRiskLevel.MEDIUM -> {
                SecureLogger.w(TAG, "Medium risk device detected")
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
    val controller = playbackManager.getController()

    // Sync playing state with Media3 controller
    DisposableEffect(controller) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Just toast the error for now, maybe add a proper UI alert later?
                Toast.makeText(context, "Playback error: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }

    // Handle back button behavior
    BackHandler(enabled = isPlayerExpanded || showSearchScreen || showAlarmScreen) {
        when {
            isPlayerExpanded -> isPlayerExpanded = false
            showAlarmScreen -> showAlarmScreen = false
            showSearchScreen -> {
                showSearchScreen = false
                searchViewModel.onQueryChanged("")
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CompositionLocalProvider(LocalLibraryViewModel provides libraryViewModel) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0,0,0,0),
                bottomBar = {
                    if (!isPlayerExpanded) {
                        YTMBottomNavBar(
                            selectedTab = homeViewModel.selectedNavTab,
                            onTabSelected = { 
                                homeViewModel.selectedNavTab = it
                                showSearchScreen = false
                            },
                            hasActivePlayer = playerUiState.currentPlayingItem != null
                        )
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
                            currentPlayingItem = playerUiState.currentPlayingItem,
                            scrollState = scrollState
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
                                currentPlayingItem = playerUiState.currentPlayingItem,
                                loadingItemUrl = playerUiState.loadingItemUrl
                            )
                            1 -> YTMExploreScreen(
                                viewModel = homeViewModel,
                                onPlay = { item ->
                                    homeViewModel.addToRecentlyPlayed(item)
                                    onPlay(item)
                                },
                                onSearchClick = { showSearchScreen = true },
                                onSettingsClick = { settingsViewModel.showSettings = true },
                                currentPlayingItem = playerUiState.currentPlayingItem,
                                loadingItemUrl = playerUiState.loadingItemUrl
                            )
                            2 -> {
                                // Library/Playlist/Alarm navigation
                                if (showAlarmScreen) {
                                    AlarmScreen(
                                        onBackClick = { showAlarmScreen = false }
                                    )
                                } else if (libraryViewModel.selectedLocalPlaylist != null) {
                                    YTMPlaylistScreen(
                                        viewModel = libraryViewModel,
                                        playlist = libraryViewModel.selectedLocalPlaylist!!,
                                        onBack = { libraryViewModel.selectedLocalPlaylist = null },
                                        onPlay = { item ->
                                            homeViewModel.addToRecentlyPlayed(item)
                                            onPlay(item)
                                        },
                                        currentPlayingItem = playerUiState.currentPlayingItem,
                                        loadingItemUrl = playerUiState.loadingItemUrl
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
                                        currentPlayingItem = playerUiState.currentPlayingItem,
                                        loadingItemUrl = playerUiState.loadingItemUrl,
                                        onNavigateToAlarms = { showAlarmScreen = true }
                                    )
                                }
                            }
                        }
                    }

                    // Global Dialogs
                    if (settingsViewModel.showSettings) {
                       SettingsDialog(
                           onDismiss = { settingsViewModel.showSettings = false },
                           isFloatingEnabled = settingsViewModel.isFloatingEnabled,
                           onFloatingEnabledChange = { settingsViewModel.toggleFloatingPlayer(it) },
                           isBackgroundEnabled = settingsViewModel.isBackgroundPlaybackEnabled,
                           onBackgroundEnabledChange = { settingsViewModel.toggleBackgroundPlayback(it) },
                           isKeepAudioPlayingEnabled = settingsViewModel.isKeepAudioPlayingEnabled,
                           onKeepAudioPlayingChange = { settingsViewModel.toggleKeepAudioPlaying(it) },
                           themeMode = settingsViewModel.themeMode,
                           onThemeModeChange = { settingsViewModel.updateThemeMode(it) },
                           isLiquidScrollEnabled = settingsViewModel.isLiquidScrollEnabled,
                           onLiquidScrollChange = { settingsViewModel.toggleLiquidScroll(it) }
                       )
                    }

                    if (libraryViewModel.showPlaylistAddDialog) {
                       PlaylistAddDialog(
                           viewModel = libraryViewModel,
                           onDismiss = { libraryViewModel.showPlaylistAddDialog = false }
                       )
                    }

                    // Player Component
                    if (playerUiState.currentPlayingItem != null) {
                        if(isPlayerExpanded) {
                             // Dim background when player is full screen
                             Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.8f)).clickable(enabled=false){})
                        }

                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                            PremiumPlayerContainer(
                                musicItem = playerUiState.currentPlayingItem,
                                isPlaying = isPlaying,
                                isLoading = playerUiState.isLoadingPlayer,
                                controller = controller,
                                relatedSongs = playerUiState.relatedSongs,
                                isAutoplayEnabled = playerUiState.autoplayEnabled,
                                onTogglePlay = { if (isPlaying) controller?.pause() else controller?.play() },
                                onNext = { onNext(playerUiState.currentPlayingItem) },
                                onPrev = { onPrev(playerUiState.currentPlayingItem) },
                                onExpand = { isPlayerExpanded = true },
                                onCollapse = { isPlayerExpanded = false },
                                isExpanded = isPlayerExpanded,
                                onAutoplayToggle = { playerViewModel.autoplayEnabled = it },
                                onPlayRelated = { item -> homeViewModel.addToRecentlyPlayed(item); onPlay(item) },
                                viewModel = playerViewModel
                            )
                        }
                    }
                }
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
    homeViewModel: HomeViewModel? = null,
    loadingItemUrl: String? = null,
    sectionKey: String = "grid"
) {
    itemsIndexed(
        items = rows,
        key = { index: Int, _: List<MusicItem> ->
            // Use index-based key with section prefix to avoid duplicates across sections
            "${sectionKey}_row_$index"
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
                    homeViewModel = homeViewModel,
                    isLoading = loadingItemUrl == item.url
                )
            }
            // Fill empty space if row is not full
            if (rowItems.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

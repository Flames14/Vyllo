package com.vyllo.music.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import com.vyllo.music.PlayerViewModel
import com.vyllo.music.data.LyricsEngine
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.presentation.components.player.PlayerActionPills
import com.vyllo.music.presentation.components.player.PlayerArtwork
import com.vyllo.music.presentation.components.player.PlayerAudioPills
import com.vyllo.music.presentation.components.player.PlayerEqualizerSheet
import com.vyllo.music.presentation.components.player.PlayerFullscreenVideoOverlay
import com.vyllo.music.presentation.components.player.PlayerHeader
import com.vyllo.music.presentation.components.player.PlayerPlaybackControls
import com.vyllo.music.presentation.components.player.PlayerQueueSheet
import com.vyllo.music.presentation.components.player.PlayerSeekBar
import com.vyllo.music.presentation.components.player.PlayerSleepTimerDialog
import com.vyllo.music.presentation.components.player.PlayerStoryCommentsSheets
import com.vyllo.music.presentation.components.player.PlayerTrackInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    viewModel: PlayerViewModel
) {
    val context = LocalContext.current
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showEqualizerSheet by rememberSaveable { mutableStateOf(false) }
    var showStoryShareSheet by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var shuffleModeEnabled by remember { mutableStateOf(controller?.shuffleModeEnabled ?: false) }
    var repeatMode by remember { mutableIntStateOf(controller?.repeatMode ?: androidx.media3.common.Player.REPEAT_MODE_OFF) }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // Queue drawer geometry fix (ported from miniplayer/queue offset fix):
    // the travel distance must be measured from the sheet height (78% of
    // screen), not the full screen height, or the collapsed sheet overshoots.
    val queueSheetHeightDp = (configuration.screenHeightDp * 0.78f).dp
    val queueSheetHeightPx = with(density) { queueSheetHeightDp.toPx() }
    val peekHeightPx = with(density) { 64.dp.toPx() }
    val sheetMaxOffset = (queueSheetHeightPx - peekHeightPx).coerceAtLeast(0f)
    val sheetOffsetY = remember { Animatable(sheetMaxOffset) }
    val expandProgress = remember(sheetOffsetY.value, sheetMaxOffset) {
        if (sheetMaxOffset > 0f) (1f - (sheetOffsetY.value / sheetMaxOffset)).coerceIn(0f, 1f) else 0f
    }
    val isSheetExpanded = expandProgress > 0.5f
    var selectedDrawerTab by rememberSaveable { mutableIntStateOf(0) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            kotlinx.coroutines.delay(650)
            seekFeedback = null
        }
    }
    LaunchedEffect(sheetMaxOffset) {
        if (!isSheetExpanded) sheetOffsetY.snapTo(sheetMaxOffset)
    }
    DisposableEffect(controller) {
        controller?.let { mediaController ->
            shuffleModeEnabled = mediaController.shuffleModeEnabled
            repeatMode = mediaController.repeatMode
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onShuffleModeEnabledChanged(enabled: Boolean) { shuffleModeEnabled = enabled }
                override fun onRepeatModeChanged(mode: Int) { repeatMode = mode }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == androidx.media3.common.Player.STATE_READY) {
                        duration = mediaController.duration.coerceAtLeast(0L)
                    }
                }
            }
            mediaController.addListener(listener)
            onDispose { mediaController.removeListener(listener) }
        } ?: onDispose { }
    }
    val playerUiState by viewModel.uiState.collectAsState()
    LaunchedEffect(controller, isPlaying) {
        if (!isPlaying || controller == null) {
            if (controller != null) {
                currentPosition = controller.currentPosition.coerceAtLeast(0L)
                duration = controller.duration.coerceAtLeast(0L)
            }
            return@LaunchedEffect
        }
        while (isActive && isPlaying) {
            currentPosition = controller.currentPosition.coerceAtLeast(0L)
            duration = controller.duration.coerceAtLeast(0L)
            val lineIdx = LyricsEngine.getCurrentLyricLine(
                playerUiState.syncedLyricsLines, currentPosition + playerUiState.lyricsOffsetMs
            )
            if (lineIdx != viewModel.currentLyricIndex) viewModel.currentLyricIndex = lineIdx
            delay(400)
        }
    }
    LaunchedEffect(item.url) {
        if (item.url.isBlank()) return@LaunchedEffect
        var attempts = 0
        var dur = 0L
        while (attempts < 20 && dur <= 0) {
            delay(250)
            dur = duration
            attempts++
        }
        val durationSecs = if (dur > 0) dur / 1000L else 0L
        viewModel.fetchLyrics(item, durationSecs)
    }
    val activity = context as? android.app.Activity
    DisposableEffect(playerUiState.isFullScreenVideo) {
        if (playerUiState.isFullScreenVideo) {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            activity?.window?.let { window ->
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        } else {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (playerUiState.isFullScreenVideo) {
                activity?.window?.let { window ->
                    val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }
    DisposableEffect(Unit) {
        val window = activity?.window
        val insetsController = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        val prevLightStatus = insetsController?.isAppearanceLightStatusBars ?: false
        val prevLightNav = insetsController?.isAppearanceLightNavigationBars ?: false
        insetsController?.isAppearanceLightStatusBars = false
        insetsController?.isAppearanceLightNavigationBars = false
        onDispose {
            insetsController?.isAppearanceLightStatusBars = prevLightStatus
            insetsController?.isAppearanceLightNavigationBars = prevLightNav
        }
    }
    BackHandler {
        if (playerUiState.isFullScreenVideo) viewModel.setFullScreenVideo(false)
        else if (isSheetExpanded) {
            coroutineScope.launch {
                sheetOffsetY.animateTo(sheetMaxOffset, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            }
        } else onCollapse()
    }
    val activeArtworkUrl = playerUiState.resolvedThumbnailUrl ?: item.thumbnailUrl
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    coroutineScope.launch {
                        val newTarget = (sheetOffsetY.value + delta).coerceIn(0f, sheetMaxOffset)
                        sheetOffsetY.snapTo(newTarget)
                    }
                },
                onDragStopped = { velocity ->
                    coroutineScope.launch {
                        if (!isSheetExpanded && velocity > 800f) {
                            onCollapse()
                        } else if (velocity < -400f || sheetOffsetY.value < sheetMaxOffset * 0.5f) {
                            sheetOffsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                        } else {
                            sheetOffsetY.animateTo(sheetMaxOffset, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                        }
                    }
                }
            )
    ) {
        // Apple Music living fluid mesh backdrop (ported from Apple-style pass)
        AppleFluidBackdrop(
            artworkUrl = activeArtworkUrl,
            isPlaying = isPlaying
        )
        PlayerEqualizerSheet(show = showEqualizerSheet, playerUiState = playerUiState, viewModel = viewModel, onDismiss = { showEqualizerSheet = false })
        PlayerSleepTimerDialog(show = showSleepTimerDialog, playerUiState = playerUiState, viewModel = viewModel, controller = controller, onDismiss = { showSleepTimerDialog = false })
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(top = 8.dp, bottom = 72.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            PlayerHeader(item = item, currentPosition = currentPosition, controller = controller, viewModel = viewModel, playerUiState = playerUiState, onCollapse = onCollapse, onShowEqualizer = { showEqualizerSheet = true }, onShowSleepTimer = { showSleepTimerDialog = true }, onShowStoryShare = { showStoryShareSheet = true })
            PlayerArtwork(item = item, isPlaying = isPlaying, isLoading = isLoading, expandProgress = expandProgress, currentPosition = currentPosition, duration = duration, controller = controller, viewModel = viewModel, playerUiState = playerUiState, seekFeedback = seekFeedback, onSeekFeedback = { seekFeedback = it }, onPositionChange = { currentPosition = it }, onTogglePlay = onTogglePlay, modifier = Modifier.weight(1f).fillMaxWidth())
            Spacer(modifier = Modifier.height(14.dp))
            PlayerTrackInfo(item = item, expandProgress = expandProgress)
            Spacer(modifier = Modifier.height(10.dp))
            PlayerActionPills(item = item, playerUiState = playerUiState, viewModel = viewModel, expandProgress = expandProgress, onLyricsClick = { selectedDrawerTab = 1; coroutineScope.launch { sheetOffsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) } }, onShowComments = { showCommentsSheet = true }, onShowStoryShare = { showStoryShareSheet = true })
            Spacer(modifier = Modifier.height(6.dp))
            PlayerSeekBar(currentPosition = currentPosition, duration = duration, expandProgress = expandProgress, controller = controller, onPositionChange = { currentPosition = it })
            Spacer(modifier = Modifier.height(6.dp))
            PlayerPlaybackControls(isPlaying = isPlaying, isLoading = isLoading, currentPosition = currentPosition, duration = duration, controller = controller, shuffleModeEnabled = shuffleModeEnabled, repeatMode = repeatMode, expandProgress = expandProgress, onTogglePlay = onTogglePlay, onNext = onNext, onPrev = onPrev, onShuffleChange = { shuffleModeEnabled = it }, onRepeatChange = { repeatMode = it }, onPositionChange = { currentPosition = it }, onSeekFeedback = { seekFeedback = it })
            Spacer(modifier = Modifier.height(6.dp))
            PlayerAudioPills(expandProgress = expandProgress)
        }
        PlayerQueueSheet(item = item, relatedSongs = relatedSongs, playerUiState = playerUiState, viewModel = viewModel, controller = controller, sheetOffsetY = sheetOffsetY, sheetMaxOffset = sheetMaxOffset, expandProgress = expandProgress, isSheetExpanded = isSheetExpanded, selectedDrawerTab = selectedDrawerTab, onTabSelected = { selectedDrawerTab = it }, onPlayRelated = onPlayRelated, coroutineScope = coroutineScope, modifier = Modifier.align(Alignment.BottomCenter))
    }
    PlayerStoryCommentsSheets(showStory = showStoryShareSheet, showComments = showCommentsSheet, item = item, onDismissStory = { showStoryShareSheet = false }, onDismissComments = { showCommentsSheet = false })
    PlayerFullscreenVideoOverlay(item = item, isPlaying = isPlaying, isLoading = isLoading, currentPosition = currentPosition, duration = duration, controller = controller, viewModel = viewModel, playerUiState = playerUiState, onTogglePlay = onTogglePlay, onPositionChange = { currentPosition = it })
}

// Backward-compat re-exports: original top-level composables moved to player/ package.
@Composable
fun QueueSongRowItem(item: MusicItem, isActive: Boolean, onClick: () -> Unit) {
    com.vyllo.music.presentation.components.player.QueueSongRowItem(item = item, isActive = isActive, onClick = onClick)
}

@Composable
fun YtmPillButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    com.vyllo.music.presentation.components.player.YtmPillButton(icon = icon, label = label, onClick = onClick)
}

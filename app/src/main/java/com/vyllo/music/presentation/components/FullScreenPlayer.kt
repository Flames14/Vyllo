package com.vyllo.music.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.vyllo.music.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import com.vyllo.music.core.utils.ShareIntentManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import com.vyllo.music.LocalLibraryViewModel
import com.vyllo.music.PlayerViewModel
import com.vyllo.music.data.LyricsEngine
import com.vyllo.music.domain.model.MusicItem
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
    val libraryViewModel = LocalLibraryViewModel.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
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
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    // Peek bar height & sheet travel distance
    val peekHeightPx = with(density) { 60.dp.toPx() }
    val sheetMaxOffset = (screenHeightPx - peekHeightPx).coerceAtLeast(0f)
    val sheetOffsetY = remember { Animatable(sheetMaxOffset) }

    // 0.0f = collapsed (huge album art), 1.0f = expanded (queue list visible)
    val expandProgress = remember(sheetOffsetY.value, sheetMaxOffset) {
        if (sheetMaxOffset > 0f) {
            (1f - (sheetOffsetY.value / sheetMaxOffset)).coerceIn(0f, 1f)
        } else 0f
    }
    val isSheetExpanded = expandProgress > 0.5f
    var selectedDrawerTab by rememberSaveable { mutableIntStateOf(0) } // 0: Up Next, 1: Lyrics, 2: Related

    // Re-synchronize when screen dimensions change
    LaunchedEffect(sheetMaxOffset) {
        if (!isSheetExpanded) {
            sheetOffsetY.snapTo(sheetMaxOffset)
        }
    }

    // Media Controller sync
    DisposableEffect(controller) {
        controller?.let { mediaController ->
            shuffleModeEnabled = mediaController.shuffleModeEnabled
            repeatMode = mediaController.repeatMode

            val listener = object : androidx.media3.common.Player.Listener {
                override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                    shuffleModeEnabled = enabled
                }

                override fun onRepeatModeChanged(mode: Int) {
                    repeatMode = mode
                }

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

    // Smooth position polling (Battery Saver: only loops when playing)
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
            if (lineIdx != viewModel.currentLyricIndex) {
                viewModel.currentLyricIndex = lineIdx
            }
            delay(400)
        }
    }

    // Lyrics fetch
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

    // Ensure status bar and navigation bar icons remain bright/readable on the dark player backdrop
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

    // Back handler
    BackHandler {
        if (playerUiState.isFullScreenVideo) {
            viewModel.setFullScreenVideo(false)
        } else if (isSheetExpanded) {
            coroutineScope.launch {
                sheetOffsetY.animateTo(sheetMaxOffset, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            }
        } else {
            onCollapse()
        }
    }

    val activeArtworkUrl = playerUiState.resolvedThumbnailUrl ?: item.thumbnailUrl

    // Master YouTube Music Container with unified vertical swipe gesture
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
                        if (velocity < -600f || sheetOffsetY.value < sheetMaxOffset * 0.65f) {
                            sheetOffsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                        } else {
                            sheetOffsetY.animateTo(sheetMaxOffset, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                        }
                    }
                }
            )
    ) {
        // Ambient Blurred Backdrop
        if (activeArtworkUrl.isNotBlank()) {
            AsyncImage(
                model = activeArtworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(50.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF0F0F0F).copy(alpha = 0.5f),
                                Color(0xFF0F0F0F).copy(alpha = 0.88f),
                                Color(0xFF0F0F0F)
                            )
                        )
                    )
            )
        }

        if (showEqualizerSheet) {
            EqualizerBottomSheet(
                settings = playerUiState.equalizerSettings,
                volumeBoostMultiplier = playerUiState.volumeBoostMultiplier,
                onDismiss = { showEqualizerSheet = false },
                onEnabledChange = viewModel::setEqualizerEnabled,
                onBassBoostChange = viewModel::updateBassBoost,
                onVirtualizerChange = viewModel::updateVirtualizer,
                onBandLevelChange = viewModel::updateEqualizerBand,
                onVolumeBoostChange = viewModel::updateVolumeBoost,
                onPresetSelected = viewModel::applyEqualizerPreset,
                onReset = viewModel::resetEqualizer
            )
        }

        if (showSleepTimerDialog) {
            AlertDialog(
                onDismissRequest = { showSleepTimerDialog = false },
                containerColor = Color(0xFF1E1E22),
                title = {
                    Text(
                        stringResource(R.string.sleep_timer_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 45, 60).forEach { mins ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.setSleepTimer(
                                            minutes = mins,
                                            onTimerFinished = { controller?.pause() },
                                            onFadeVolume = { fadeRatio -> controller?.volume = fadeRatio }
                                        )
                                        showSleepTimerDialog = false
                                    },
                                color = Color.White.copy(alpha = 0.08f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        stringResource(R.string.sleep_timer_minutes, mins),
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Icon(
                                        Icons.Rounded.Timer,
                                        contentDescription = null,
                                        tint = Color.White.copy(0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        if (playerUiState.isSleepTimerActive) {
                            TextButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.cancelSleepTimer(onResetVolume = { controller?.volume = 1.0f })
                                    showSleepTimerDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    stringResource(R.string.sleep_timer_turn_off),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSleepTimerDialog = false }) {
                        Text(stringResource(R.string.common_cancel), color = Color.White.copy(0.7f))
                    }
                }
            )
        }

        // ==========================================
        // MAIN PLAYER LAYER (Expands smoothly on Swipe Down)
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 48.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Bar: Down Chevron | Song/Video Pill [ 🎧 | ▶ ] | 3-Dots Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Audio / Video Switcher Pill (YouTube Music style)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Song Mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (!viewModel.isVideoMode) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable {
                                    if (viewModel.isVideoMode) {
                                        viewModel.toggleVideoMode(currentPosition) { newUrl ->
                                            if (newUrl != null) {
                                                val mediaItem = androidx.media3.common.MediaItem.Builder()
                                                    .setUri(newUrl)
                                                    .setMediaId(item.url)
                                                    .build()
                                                controller?.setMediaItem(mediaItem, currentPosition)
                                                controller?.prepare()
                                                controller?.play()
                                            }
                                        }
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Headphones,
                                    contentDescription = "Song Mode",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Song",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }

                        // Video Mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (viewModel.isVideoMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable {
                                    if (!viewModel.isVideoMode) {
                                        viewModel.toggleVideoMode(currentPosition) { newUrl ->
                                            if (newUrl != null) {
                                                val mediaItem = androidx.media3.common.MediaItem.Builder()
                                                    .setUri(newUrl)
                                                    .setMediaId(item.url)
                                                    .build()
                                                controller?.setMediaItem(mediaItem, currentPosition)
                                                controller?.prepare()
                                                controller?.play()
                                            }
                                        }
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.SmartDisplay,
                                    contentDescription = "Video Mode",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Video",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // Options Menu
                Box {
                    var showMoreMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, "More Options", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        modifier = Modifier.background(Color(0xFF1E1E22))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Equalizer", color = Color.White) },
                            onClick = {
                                showMoreMenu = false
                                showEqualizerSheet = true
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Tune, "Equalizer", tint = Color.White)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                val remaining = playerUiState.sleepTimerRemainingSeconds
                                val label = if (playerUiState.isSleepTimerActive && remaining != null) {
                                    val m = remaining / 60
                                    val s = remaining % 60
                                    "Sleep Timer (%02d:%02d)".format(m, s)
                                } else {
                                    "Sleep Timer"
                                }
                                Text(
                                    label,
                                    color = if (playerUiState.isSleepTimerActive) MaterialTheme.colorScheme.primary else Color.White
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                showSleepTimerDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Snooze,
                                    "Sleep Timer",
                                    tint = if (playerUiState.isSleepTimerActive) MaterialTheme.colorScheme.primary else Color.White
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Story Card", color = Color.White) },
                            onClick = {
                                showMoreMenu = false
                                showStoryShareSheet = true
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.CameraAlt, "Share Story", tint = Color.White)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Song Link", color = Color.White) },
                            onClick = {
                                showMoreMenu = false
                                ShareIntentManager.shareSongLink(context, item)
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Share, "Share Link", tint = Color.White)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy Song Link", color = Color.White) },
                            onClick = {
                                showMoreMenu = false
                                ShareIntentManager.copySongLink(context, item)
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.ContentCopy, "Copy Link", tint = Color.White)
                            }
                        )
                    }
                }
            }

            val artworkPlayScale by animateFloatAsState(
                targetValue = if (isPlaying) 1.0f else 0.94f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "artworkPlayScale"
            )

            var seekFeedback by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(seekFeedback) {
                if (seekFeedback != null) {
                    kotlinx.coroutines.delay(650)
                    seekFeedback = null
                }
            }

            // 2. Large High-Resolution Album Artwork (Smoothly expands / zooms with playback, drag & double-tap seeking)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .scale((1f - (expandProgress * 0.08f)) * artworkPlayScale)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black)
                    .shadow(16.dp, RoundedCornerShape(18.dp), spotColor = Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (offset.x < size.width / 2) {
                                    val newPos = (currentPosition - 10000L).coerceAtLeast(0L)
                                    controller?.seekTo(newPos)
                                    currentPosition = newPos
                                    seekFeedback = "-10"
                                } else {
                                    val newPos = (currentPosition + 10000L).coerceAtMost(duration)
                                    controller?.seekTo(newPos)
                                    currentPosition = newPos
                                    seekFeedback = "+10"
                                }
                            }
                        )
                    }
            ) {
                if (viewModel.isVideoMode) {
                    VideoSurface(controller = controller, modifier = Modifier.fillMaxSize())
                    VideoPlayerOverlayControls(
                        isPlaying = isPlaying,
                        isLoading = isLoading,
                        currentPosition = currentPosition,
                        duration = duration,
                        isFullScreen = playerUiState.isFullScreenVideo,
                        onTogglePlay = onTogglePlay,
                        onSeek = { newPercent ->
                            val newPos = (newPercent * duration).toLong()
                            controller?.seekTo(newPos)
                            currentPosition = newPos
                        },
                        onForward = { controller?.seekTo((currentPosition + 10000).coerceAtMost(duration)) },
                        onRewind = { controller?.seekTo((currentPosition - 10000).coerceAtLeast(0)) },
                        onToggleFullScreen = { viewModel.setFullScreenVideo(!playerUiState.isFullScreenVideo) }
                    )
                } else {
                    val resolvedUrl = playerUiState.resolvedThumbnailUrl
                    val candidates = remember(item.thumbnailUrl, resolvedUrl) {
                        if (!resolvedUrl.isNullOrBlank()) {
                            (listOf(resolvedUrl) + item.getThumbnailCandidates()).distinct()
                        } else {
                            item.getThumbnailCandidates()
                        }
                    }
                    var candidateIndex by remember(item.thumbnailUrl, resolvedUrl) { mutableIntStateOf(0) }
                    val currentThumbnail = candidates.getOrNull(candidateIndex) ?: item.thumbnailUrl

                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(currentThumbnail)
                            .crossfade(true)
                            .allowHardware(false)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onState = { state ->
                            if (state is coil.compose.AsyncImagePainter.State.Error) {
                                if (candidateIndex < candidates.size - 1) {
                                    candidateIndex++
                                }
                            }
                        }
                    )

                    // Double-tap seeking indicator overlay
                    androidx.compose.animation.AnimatedVisibility(
                        visible = seekFeedback != null,
                        enter = fadeIn() + scaleIn(initialScale = 0.7f),
                        exit = fadeOut() + scaleOut(targetScale = 1.15f),
                        modifier = Modifier
                            .align(if (seekFeedback == "-10") Alignment.CenterStart else Alignment.CenterEnd)
                            .padding(24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.75f),
                            contentColor = Color.White,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (seekFeedback == "-10") Icons.Rounded.Replay10 else Icons.Rounded.Forward10,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = Color.White
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (seekFeedback == "-10") "-10 sec" else "+10 sec",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Track Title (with chevron >) and Artists
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha((1f - (expandProgress * 2f)).coerceIn(0f, 1f)),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.uploader,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Action Pills Row (Like/Dislike, Comments, Save, Share, Download)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha((1f - (expandProgress * 2f)).coerceIn(0f, 1f))
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like / Dislike Combined Pill (Real YouTube Likes with Interactive Feedback)
                var isLiked by remember(item.url) { mutableStateOf(false) }
                var isDisliked by remember(item.url) { mutableStateOf(false) }

                val displayedLikes = remember(playerUiState.likeCountFormatted, isLiked) {
                    when {
                        isLiked -> {
                            val baseCount = playerUiState.likeCount
                            if (baseCount > 0) {
                                viewModel.formatMetricCount(baseCount + 1)
                            } else "1"
                        }
                        !playerUiState.likeCountFormatted.isNullOrBlank() -> playerUiState.likeCountFormatted!!
                        else -> "Like"
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.12f),
                    contentColor = Color.White
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ThumbUp,
                            contentDescription = "Like",
                            tint = if (isLiked) Color(0xFF3EA6FF) else Color.White,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isLiked = !isLiked
                                    if (isLiked) isDisliked = false
                                }
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = displayedLikes,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isLiked) Color(0xFF3EA6FF) else Color.White
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color.White.copy(0.2f)))
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.Rounded.ThumbDown,
                            contentDescription = "Dislike",
                            tint = if (isDisliked) Color(0xFF3EA6FF) else Color.White,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isDisliked = !isDisliked
                                    if (isDisliked) isLiked = false
                                }
                        )
                    }
                }

                // Lyrics Pill
                YtmPillButton(icon = Icons.Rounded.Lyrics, label = "Lyrics") {
                    selectedDrawerTab = 1
                    coroutineScope.launch {
                        sheetOffsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                    }
                }

                // Comments Pill (Real YouTube Comment Count)
                val displayedComments = playerUiState.commentCountFormatted ?: "Comments"
                YtmPillButton(icon = Icons.AutoMirrored.Rounded.Comment, label = displayedComments) {
                    showCommentsSheet = true
                }

                // Save to Playlist Pill
                YtmPillButton(icon = Icons.AutoMirrored.Rounded.PlaylistAdd, label = "Save") {
                    libraryViewModel.showPlaylistAddDialog(item)
                }

                // Share Pill
                YtmPillButton(icon = Icons.Rounded.Share, label = "Share") {
                    showStoryShareSheet = true
                }

                // Download Pill
                YtmPillButton(icon = Icons.Rounded.Download, label = "Download") {
                    libraryViewModel.downloadSong(item)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 5. Scrubber & Time Display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha((1f - (expandProgress * 2.5f)).coerceIn(0f, 1f))
            ) {
                Slider(
                    value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                    onValueChange = { newPercent ->
                        val newPos = (newPercent * duration).toLong()
                        controller?.seekTo(newPos)
                        currentPosition = newPos
                    },
                    onValueChangeFinished = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Playback progress: ${formatTime(currentPosition)} of ${formatTime(duration)}"
                        }
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatTime(currentPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(0.6f)
                    )
                    Text(
                        formatTime(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 6. Playback Controls Row (Shuffle | Prev | Replay10 | Play/Pause Circle | Forward10 | Next | Repeat)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha((1f - (expandProgress * 2.5f)).coerceIn(0f, 1f)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        shuffleModeEnabled = !shuffleModeEnabled
                        controller?.shuffleModeEnabled = shuffleModeEnabled
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = stringResource(R.string.accessibility_shuffle),
                        tint = if (shuffleModeEnabled) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPrev()
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.accessibility_skip_previous),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Go back 10 seconds
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val newPos = (currentPosition - 10000L).coerceAtLeast(0L)
                        controller?.seekTo(newPos)
                        currentPosition = newPos
                        seekFeedback = "-10"
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        Icons.Rounded.Replay10,
                        contentDescription = "Rewind 10 seconds",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Main Big Play/Pause Circle
                Surface(
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTogglePlay()
                        }
                        .semantics {
                            contentDescription = if (isPlaying) "Pause" else "Play"
                        },
                    color = Color.White,
                    contentColor = Color.Black
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color.Black,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(if (isPlaying) R.string.accessibility_pause else R.string.accessibility_play),
                                modifier = Modifier.size(40.dp),
                                tint = Color.Black
                            )
                        }
                    }
                }

                // Skip 10 seconds forward
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val newPos = (currentPosition + 10000L).coerceAtMost(duration)
                        controller?.seekTo(newPos)
                        currentPosition = newPos
                        seekFeedback = "+10"
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        Icons.Rounded.Forward10,
                        contentDescription = "Forward 10 seconds",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNext()
                    },
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.accessibility_skip_next),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = {
                        repeatMode = when (repeatMode) {
                            androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
                            androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
                            else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                        }
                        controller?.repeatMode = repeatMode
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    val repeatIcon = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
                    Icon(
                        repeatIcon,
                        contentDescription = stringResource(R.string.accessibility_repeat),
                        tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // ==========================================
        // 7. SWIPEABLE "UP NEXT" QUEUE DRAWER
        // ==========================================
        val queueSheetHeightDp = (configuration.screenHeightDp * 0.78f).dp

        val sheetDraggableState = rememberDraggableState { delta ->
            coroutineScope.launch {
                sheetOffsetY.snapTo((sheetOffsetY.value + delta).coerceIn(0f, sheetMaxOffset))
            }
        }

        val sheetDragModifier = Modifier.draggable(
            state = sheetDraggableState,
            orientation = Orientation.Vertical,
            onDragStopped = { velocity ->
                coroutineScope.launch {
                    val target = when {
                        velocity > 400f -> sheetMaxOffset // Effortless fast swipe down -> close!
                        velocity < -400f -> 0f           // Effortless fast swipe up -> open!
                        sheetOffsetY.value > sheetMaxOffset * 0.25f -> sheetMaxOffset // 25% down -> close smoothly!
                        else -> 0f
                    }
                    sheetOffsetY.animateTo(
                        target,
                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                    )
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(queueSheetHeightDp)
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, sheetOffsetY.value.toInt()) }
                .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFF1E1E22))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Header Bar with full draggable touch tracking
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(sheetDragModifier)
                        .padding(vertical = 8.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Drag Pill Handle
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.45f))
                            .clickable {
                                coroutineScope.launch {
                                    val target = if (isSheetExpanded) sheetMaxOffset else 0f
                                    sheetOffsetY.animateTo(target, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                                }
                            }
                    )

                    Spacer(Modifier.height(8.dp))

                    if (!isSheetExpanded) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        sheetOffsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                                    }
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.KeyboardArrowUp,
                                        contentDescription = "Swipe up",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Swipe up for Up Next",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${item.title} • Auto-Mix",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Save Mix Button
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.1f),
                                contentColor = Color.White,
                                modifier = Modifier.clickable { libraryViewModel.showPlaylistAddDialog(item) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.PlaylistAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Save", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    } else {
                        // YouTube Music Drawer Tabs: UP NEXT | LYRICS | RELATED + Quick Collapse Chevron Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("UP NEXT", "LYRICS", "RELATED").forEachIndexed { idx, tabTitle ->
                                    val isTabSelected = selectedDrawerTab == idx
                                    Column(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { selectedDrawerTab = idx }
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = tabTitle,
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                                letterSpacing = 1.sp
                                            ),
                                            color = if (isTabSelected) Color.White else Color.White.copy(alpha = 0.5f)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        if (isTabSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .width(32.dp)
                                                    .height(2.dp)
                                                    .clip(RoundedCornerShape(1.dp))
                                                    .background(Color.White)
                                            )
                                        } else {
                                            Spacer(Modifier.height(2.dp))
                                        }
                                    }
                                }
                            }

                            // Dedicated Close / Collapse Chevron Button
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        sheetOffsetY.animateTo(sheetMaxOffset, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Collapse Queue",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                // Drawer Content Switcher
                when (selectedDrawerTab) {
                    0 -> {
                        // UP NEXT TAB (Queue + Dynamic Diverse Auto-Mix)
                        val queueListState = rememberLazyListState()
                        val isRefreshing = playerUiState.isLoadingRelatedTab
                        val infiniteTransition = rememberInfiniteTransition(label = "refresh_transition")
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(900, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "refresh_spin"
                        )

                        LazyColumn(
                            state = queueListState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
                        ) {
                            // Current Active Song
                            item {
                                QueueSongRowItem(
                                    item = item,
                                    isActive = true,
                                    onClick = { }
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Playing from Queue",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "• ${relatedSongs.size} tracks",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.4f)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.forceRefreshRelatedSongs()
                                        },
                                        modifier = Modifier.size(32.dp),
                                        enabled = !isRefreshing
                                    ) {
                                        Icon(
                                            Icons.Rounded.Refresh,
                                            contentDescription = "Refresh Up Next mix",
                                            tint = if (isRefreshing) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.75f),
                                            modifier = Modifier
                                                .size(20.dp)
                                                .graphicsLayer {
                                                    if (isRefreshing) {
                                                        rotationZ = rotation
                                                    }
                                                }
                                        )
                                    }
                                }
                            }

                            if (isRefreshing) {
                                item {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 4.dp)
                                            .height(2.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = Color.Transparent
                                    )
                                }
                            }

                            // Upcoming Dynamic Auto-Mix Songs
                            itemsIndexed(
                                items = relatedSongs,
                                key = { idx, s -> "upnext_${s.url}_$idx" }
                            ) { _, song ->
                                QueueSongRowItem(
                                    item = song,
                                    isActive = song.url == item.url,
                                    onClick = { onPlayRelated(song) }
                                )
                            }

                            if (playerUiState.isLoadingMoreRelated) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = Color.White.copy(0.6f),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Lyrics & Live Translator View
                        LyricsViewContent(
                            playerUiState = playerUiState,
                            viewModel = viewModel,
                            controller = controller,
                            onSeek = { controller?.seekTo(it) },
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
                        )
                    }
                    2 -> {
                        // RELATED TAB (Exploratory Discoveries & More from Artist)
                        val relatedListState = rememberLazyListState()
                        LazyColumn(
                            state = relatedListState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
                        ) {
                            // Section 1: More by Artist
                            if (playerUiState.artistSongs.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "More from ${item.uploader}",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                                    )
                                }
                                items(
                                    items = playerUiState.artistSongs,
                                    key = { s -> "artist_${s.url}" }
                                ) { song ->
                                    QueueSongRowItem(
                                        item = song,
                                        isActive = song.url == item.url,
                                        onClick = { onPlayRelated(song) }
                                    )
                                }
                            }

                            // Section 2: Discover Similar Songs & Artists
                            if (playerUiState.discoverSimilarSongs.isNotEmpty()) {
                                item {
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = "You Might Also Like",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                items(
                                    items = playerUiState.discoverSimilarSongs,
                                    key = { s -> "similar_${s.url}" }
                                ) { song ->
                                    QueueSongRowItem(
                                        item = song,
                                        isActive = false,
                                        onClick = { onPlayRelated(song) }
                                    )
                                }
                            }

                            // Loading indicator if fetching
                            if (playerUiState.isLoadingRelatedTab) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = Color.White.copy(0.6f),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStoryShareSheet) {
        StoryShareBottomSheet(item = item, onDismiss = { showStoryShareSheet = false })
    }

    if (showCommentsSheet) {
        CommentsBottomSheet(item = item, onDismiss = { showCommentsSheet = false })
    }

    if (playerUiState.isFullScreenVideo) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(999f)
                .background(Color.Black)
        ) {
            VideoSurface(
                controller = controller,
                modifier = Modifier.fillMaxSize()
            )

            VideoPlayerOverlayControls(
                isPlaying = isPlaying,
                isLoading = isLoading,
                currentPosition = currentPosition,
                duration = duration,
                isFullScreen = true,
                onTogglePlay = onTogglePlay,
                onSeek = { newPercent ->
                    val newPos = (newPercent * duration).toLong()
                    controller?.seekTo(newPos)
                    currentPosition = newPos
                },
                onForward = { controller?.seekTo((currentPosition + 10000).coerceAtMost(duration)) },
                onRewind = { controller?.seekTo((currentPosition - 10000).coerceAtLeast(0)) },
                onToggleFullScreen = { viewModel.setFullScreenVideo(false) }
            )

            // Top Header Bar in Fullscreen Video
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.setFullScreenVideo(false) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Exit Fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.uploader,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun QueueSongRowItem(
    item: MusicItem,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with GraphicEq if active
        Box(modifier = Modifier.size(48.dp)) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(item.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
            )
            if (isActive) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium),
                color = if (isActive) Color.White else Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.uploader,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        // Drag handle icon (=)
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = "Reorder",
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun YtmPillButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.12f),
        contentColor = Color.White,
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
            if (label.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

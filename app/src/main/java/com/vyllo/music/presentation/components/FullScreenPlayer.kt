package com.vyllo.music.presentation.components

import android.content.Intent
import android.widget.Toast

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.vyllo.music.PlayerViewModel
import com.vyllo.music.LocalLibraryViewModel
import com.vyllo.music.R
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.LyricsEngine
import com.vyllo.music.domain.model.MusicItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer

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
    viewModel: PlayerViewModel
) {
    val libraryViewModel = LocalLibraryViewModel.current
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var recentlySelectedSongUrl by remember { mutableStateOf<String?>(null) }
    var showEqualizerSheet by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    var shuffleModeEnabled by remember { mutableStateOf(controller?.shuffleModeEnabled ?: false) }
    var repeatMode by remember { mutableIntStateOf(controller?.repeatMode ?: androidx.media3.common.Player.REPEAT_MODE_OFF) }

    // Observe shuffle/repeat mode changes from MediaController
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
            }

            mediaController.addListener(listener)

            onDispose {
                mediaController.removeListener(listener)
            }
        } ?: onDispose { }
    }

    // Collect player UI state so Compose recomposes when lyrics/translation state changes
    val playerUiState by viewModel.uiState.collectAsState()

    // Event-driven position updates — only polls while actively playing
    var lastLineIndex by remember { mutableIntStateOf(-1) }
    var pollingActive by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Register Player.Listener via DisposableEffect for proper lifecycle management
    DisposableEffect(controller) {
        controller ?: return@DisposableEffect onDispose {}

        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                pollingActive = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_READY) {
                    pollingActive = controller.isPlaying
                    duration = controller.duration.coerceAtLeast(0L)
                } else {
                    pollingActive = false
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo,
                reason: Int
            ) {
                currentPosition = newPosition.positionMs
                duration = controller.duration.coerceAtLeast(0L)
                if (controller.isPlaying) {
                    val newLineIndex = LyricsEngine.getCurrentLyricLine(
                        playerUiState.syncedLyricsLines, currentPosition + playerUiState.lyricsOffsetMs
                    )
                    if (newLineIndex != lastLineIndex) {
                        viewModel.currentLyricIndex = newLineIndex
                        lastLineIndex = newLineIndex
                    }
                }
            }
        }

        controller.addListener(listener)
        pollingActive = controller.isPlaying

        onDispose {
            controller.removeListener(listener)
            pollingActive = false
        }
    }

    // Lightweight polling only for smooth lyrics sync while playing
    LaunchedEffect(controller, playerUiState.syncedLyricsLines.hashCode()) {
        controller ?: return@LaunchedEffect
        while (isActive) {
            if (pollingActive && controller.playbackState == androidx.media3.common.Player.STATE_READY) {
                currentPosition = controller.currentPosition
                duration = controller.duration.coerceAtLeast(0L)
                val newLineIndex = LyricsEngine.getCurrentLyricLine(
                    playerUiState.syncedLyricsLines, currentPosition + playerUiState.lyricsOffsetMs
                )
                if (newLineIndex != lastLineIndex) {
                    viewModel.currentLyricIndex = newLineIndex
                    lastLineIndex = newLineIndex
                }
            }
            delay(500)
        }
    }

    LaunchedEffect(item.url) {
        if (item.url.isBlank()) return@LaunchedEffect
        // Wait for duration to become available (polling loop updates it every 250ms)
        var attempts = 0
        var dur = 0L
        while (attempts < 20 && dur <= 0) {
            delay(250)
            dur = duration
            attempts++
        }
        val durationSecs = if (dur > 0) dur / 1000L else 0L
        SecureLogger.d("FullScreenPlayer") { "Fetching lyrics: url=${item.url}, duration=${durationSecs}s, attempts=$attempts" }
        viewModel.fetchLyrics(item, durationSecs)
    }

    LaunchedEffect(item.url) {
        selectedTab = 0
        currentPosition = 0L
        duration = 0L
        recentlySelectedSongUrl = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showEqualizerSheet) {
            EqualizerBottomSheet(
                settings = playerUiState.equalizerSettings,
                onDismiss = { showEqualizerSheet = false },
                onEnabledChange = viewModel::setEqualizerEnabled,
                onBassBoostChange = viewModel::updateBassBoost,
                onVirtualizerChange = viewModel::updateVirtualizer,
                onBandLevelChange = viewModel::updateEqualizerBand,
                onReset = viewModel::resetEqualizer
            )
        }

        ImmersiveBackground(imageUrl = item.thumbnailUrl) {
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()

            // Handle orientation and system UI when in full screen
            val activity = LocalContext.current as? Activity
            DisposableEffect(playerUiState.isFullScreenVideo) {
                if (playerUiState.isFullScreenVideo) {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                } else {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                onDispose {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }

            // Back handler to exit full screen
            BackHandler(enabled = playerUiState.isFullScreenVideo) {
                viewModel.setFullScreenVideo(false)
            }

            if (playerUiState.isInPipMode) {
                // PiP Mode: Only show video surface
                VideoSurface(
                    controller = controller,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
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
                                    stringResource(R.string.player_playing_from),
                                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                                    color = MaterialTheme.colorScheme.onBackground.copy(0.5f)
                                )
                                Text(
                                    stringResource(R.string.player_search_results),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            IconButton(onClick = {}, enabled = false) {
                                Icon(Icons.Rounded.MoreVert, null, tint = Color.Transparent)
                            }
                        }

                        Spacer(modifier = Modifier.height(40.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            listOf(R.string.player_tab_player, R.string.player_tab_lyrics).forEachIndexed { idx, labelRes ->
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
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Normal),
                                        color = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        if (selectedTab == 0) {
                            Box(
                                modifier = if (playerUiState.isFullScreenVideo) {
                                    Modifier
                                        .fillMaxSize()
                                        .zIndex(10f)
                                        .background(Color.Black)
                                } else {
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f) // Restore original square aspect ratio
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color.Black)
                                }
                            ) {
                                // Background Video Layer
                                if (viewModel.isVideoMode) {
                                    VideoSurface(
                                        controller = controller,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    
                                    // Professional Overlay Controls
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
                                }

                                // Album Art Layer (only if not in video mode)
                                if (!viewModel.isVideoMode) {
                                    AsyncImage(
                                        model = item.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // Direct Video/Audio Toggle Icon (Only show if not fullscreen)
                                if (!playerUiState.isFullScreenVideo) {
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(16.dp)
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                viewModel.toggleVideoMode(currentPosition) { newUrl ->
                                                    if (newUrl != null) {
                                                        val mediaItem = androidx.media3.common.MediaItem.Builder()
                                                            .setUri(newUrl)
                                                            .setMediaId(item.url)
                                                            .setMediaMetadata(
                                                                androidx.media3.common.MediaMetadata.Builder()
                                                                    .setTitle(item.title)
                                                                    .setArtist(item.uploader)
                                                                    .setArtworkUri(android.net.Uri.parse(item.thumbnailUrl))
                                                                    .build()
                                                            )
                                                            .build()
                                                        controller?.setMediaItem(mediaItem, currentPosition)
                                                        controller?.prepare()
                                                        controller?.play()
                                                    }
                                                }
                                            },
                                        color = if (viewModel.isVideoMode) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f),
                                        contentColor = Color.White,
                                        tonalElevation = 8.dp
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (viewModel.isVideoMode) Icons.Rounded.MusicNote else Icons.Rounded.SmartDisplay,
                                                contentDescription = "Toggle Video Mode",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                // Animated Hint Bar (Bottom Center)
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = !viewModel.isVideoMode && !playerUiState.isFullScreenVideo,
                                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { it / 2 }),
                                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { it / 2 }),
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                ) {
                                    VideoHintBar(
                                        onClick = {
                                            viewModel.toggleVideoMode(currentPosition) { newUrl ->
                                                if (newUrl != null) {
                                                    val mediaItem = androidx.media3.common.MediaItem.Builder()
                                                        .setUri(newUrl)
                                                        .setMediaId(item.url)
                                                        .setMediaMetadata(
                                                            androidx.media3.common.MediaMetadata.Builder()
                                                                .setTitle(item.title)
                                                                .setArtist(item.uploader)
                                                                .setArtworkUri(android.net.Uri.parse(item.thumbnailUrl))
                                                                .build()
                                                        )
                                                        .build()
                                                    controller?.setMediaItem(mediaItem, currentPosition)
                                                    controller?.prepare()
                                                    controller?.play()
                                                }
                                            }
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    DownloadButton(item)
                                    IconButton(onClick = { showEqualizerSheet = true }) {
                                        Icon(
                                            Icons.Rounded.GraphicEq,
                                            contentDescription = stringResource(R.string.equalizer_title),
                                            tint = if (playerUiState.equalizerSettings.enabled) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onBackground.copy(0.7f)
                                            },
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    IconButton(onClick = {
                                        val shareUrl = item.getUniversalShareUrl()
                                        if (shareUrl == null) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.share_music_error),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(
                                                    Intent.EXTRA_SUBJECT,
                                                    context.getString(R.string.share_music_subject, item.title)
                                                )
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    context.getString(R.string.share_music_message, item.title, shareUrl)
                                                )
                                            }
                                            ContextCompat.startActivity(
                                                context,
                                                Intent.createChooser(
                                                    shareIntent,
                                                    context.getString(R.string.share_music_chooser)
                                                ),
                                                null
                                            )
                                        }
                                    }) {
                                        Icon(
                                            Icons.Rounded.Share,
                                            contentDescription = stringResource(R.string.share_music),
                                            tint = MaterialTheme.colorScheme.onBackground.copy(0.7f),
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    IconButton(onClick = { libraryViewModel.showPlaylistAddDialog(item) }) {
                                        Icon(Icons.Rounded.PlaylistAdd, "Add to Playlist", tint = MaterialTheme.colorScheme.onBackground.copy(0.7f), modifier = Modifier.size(28.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))
                            
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
                                    coroutineScope.launch { listState.animateScrollToItem(3) }
                                }) {
                                    Icon(Icons.Rounded.QueueMusic, null, tint = MaterialTheme.colorScheme.onBackground.copy(0.5f))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.player_up_next), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
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
                            // The LyricsTabContent has its own height now
                            LyricsTabContent(playerUiState, viewModel, controller, { currentPosition = it })
                        }
                    }
                }
                
                if (relatedSongs.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.player_related_songs),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp)
                        )
                    }

                    items(
                        items = relatedSongs,
                        key = { it.url }
                    ) { song ->
                        val isSelected = recentlySelectedSongUrl == song.url
                        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                            PremiumSongRow(
                                item = song,
                                isPlaying = false,
                                index = 0,
                                onClick = {
                                    recentlySelectedSongUrl = song.url
                                    onPlayRelated(song)
                                },
                                isSelected = isSelected
                            )
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(64.dp)) }
            }
        }
    }
}
}

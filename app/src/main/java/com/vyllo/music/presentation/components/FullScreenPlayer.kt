package com.vyllo.music.presentation.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import com.vyllo.music.PlayerViewModel
import com.vyllo.music.LibraryViewModel
import com.vyllo.music.LocalLibraryViewModel
import com.vyllo.music.domain.model.MusicItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    var shuffleModeEnabled by remember { mutableStateOf(controller?.shuffleModeEnabled ?: false) }
    var repeatMode by remember { mutableIntStateOf(controller?.repeatMode ?: androidx.media3.common.Player.REPEAT_MODE_OFF) }

    LaunchedEffect(controller, isPlaying) {
        while (isPlaying) {
            if (controller?.playbackState == androidx.media3.common.Player.STATE_READY) {
                currentPosition = controller.currentPosition
                duration = controller.duration
                if (duration < 0) duration = 0L
                viewModel.updateLyricsPosition(currentPosition)
            }
            delay(100)
        }
    }

    LaunchedEffect(item.url, duration) {
        if (item.url.isBlank()) return@LaunchedEffect
        val durationSecs = if (duration > 0) duration / 1000L else 0L
        viewModel.fetchLyrics(item, durationSecs)
    }

    LaunchedEffect(item.url) {
        selectedTab = 0
        currentPosition = 0L
        duration = 0L
    }

    Box(modifier = Modifier.fillMaxSize()) {
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

                        if (selectedTab == 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color.Black)
                            ) {
                                // Background Video Layer
                                if (viewModel.isVideoMode) {
                                    VideoSurface(
                                        controller = controller,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    android.util.Log.d("FullScreenPlayer", "VideoSurface displayed for: ${item.title}")
                                } else {
                                    android.util.Log.d("FullScreenPlayer", "Album art displayed for: ${item.title}")
                                }

                                // Album Art Layer
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = !viewModel.isVideoMode,
                                    enter = androidx.compose.animation.fadeIn(),
                                    exit = androidx.compose.animation.fadeOut()
                                ) {
                                    AsyncImage(
                                        model = item.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // Direct Video/Audio Toggle Icon
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(16.dp)
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            viewModel.toggleVideoMode(currentPosition) { newUrl ->
                                                if (newUrl != null) {
                                                    controller?.setMediaItem(androidx.media3.common.MediaItem.fromUri(newUrl))
                                                    controller?.prepare()
                                                    controller?.seekTo(currentPosition)
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

                                // Animated Hint Bar (Bottom Center)
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = !viewModel.isVideoMode,
                                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { it / 2 }),
                                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { it / 2 }),
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                ) {
                                    VideoHintBar(
                                        onClick = {
                                            viewModel.toggleVideoMode(currentPosition) { newUrl ->
                                                if (newUrl != null) {
                                                    controller?.setMediaItem(androidx.media3.common.MediaItem.fromUri(newUrl))
                                                    controller?.prepare()
                                                    controller?.seekTo(currentPosition)
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
                            // The LyricsTabContent has its own height now
                            LyricsTabContent(viewModel, controller, { currentPosition = it })
                        }
                    }
                }
                
                item {
                    AutoplayRow(isChecked = isAutoplayEnabled, onToggle = onAutoplayToggle)
                }
                
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

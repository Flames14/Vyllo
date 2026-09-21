package com.vyllo.music.presentation.components.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.vyllo.music.PlayerUiState
import com.vyllo.music.PlayerViewModel
import com.vyllo.music.core.utils.ShareIntentManager
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.presentation.components.iosPressClickable

@Composable
fun PlayerHeader(
    item: MusicItem,
    currentPosition: Long,
    controller: MediaController?,
    viewModel: PlayerViewModel,
    playerUiState: PlayerUiState,
    onCollapse: () -> Unit,
    onShowEqualizer: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onShowStoryShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 1. Top Bar: Down Chevron | Song/Video Pill [ 🎧 | ▶ ] | 3-Dots Menu
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
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

        // Audio / Video Switcher Pill (iOS Glass Segmented Control)
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.5f),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.18f))
        ) {
            Row(
                modifier = Modifier.padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Song Mode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (!viewModel.isVideoMode) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                        .iosPressClickable {
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
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
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
                        .iosPressClickable {
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
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
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
                        onShowEqualizer()
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
                        onShowSleepTimer()
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
                        onShowStoryShare()
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
}

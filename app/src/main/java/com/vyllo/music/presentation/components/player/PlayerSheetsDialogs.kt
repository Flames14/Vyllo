package com.vyllo.music.presentation.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.session.MediaController
import com.vyllo.music.PlayerUiState
import com.vyllo.music.PlayerViewModel
import com.vyllo.music.R
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.presentation.components.CommentsBottomSheet
import com.vyllo.music.presentation.components.EqualizerBottomSheet
import com.vyllo.music.presentation.components.StoryShareBottomSheet
import com.vyllo.music.presentation.components.iosPressClickable
import com.vyllo.music.presentation.components.VideoPlayerOverlayControls
import com.vyllo.music.presentation.components.VideoSurface

@Composable
fun PlayerEqualizerSheet(
    show: Boolean,
    playerUiState: PlayerUiState,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    if (show) {
        EqualizerBottomSheet(
            settings = playerUiState.equalizerSettings,
            volumeBoostMultiplier = playerUiState.volumeBoostMultiplier,
            onDismiss = onDismiss,
            onEnabledChange = viewModel::setEqualizerEnabled,
            onBassBoostChange = viewModel::updateBassBoost,
            onVirtualizerChange = viewModel::updateVirtualizer,
            onBandLevelChange = viewModel::updateEqualizerBand,
            onVolumeBoostChange = viewModel::updateVolumeBoost,
            onPresetSelected = viewModel::applyEqualizerPreset,
            onReset = viewModel::resetEqualizer
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSleepTimerDialog(
    show: Boolean,
    playerUiState: PlayerUiState,
    viewModel: PlayerViewModel,
    controller: MediaController?,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    if (show) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = Color(0xFF1C1C1E),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 8.dp)
                        .size(width = 36.dp, height = 5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.28f))
                )
            },
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    stringResource(R.string.sleep_timer_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                ) {
                    listOf(15, 30, 45, 60).forEachIndexed { idx, mins ->
                        if (idx > 0) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .iosPressClickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.setSleepTimer(
                                        minutes = mins,
                                        onTimerFinished = { controller?.pause() },
                                        onFadeVolume = { fadeRatio -> controller?.volume = fadeRatio }
                                    )
                                    onDismiss()
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(R.string.sleep_timer_minutes, mins),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                            Icon(
                                Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                if (playerUiState.isSleepTimerActive) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .iosPressClickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.cancelSleepTimer(onResetVolume = { controller?.volume = 1.0f })
                                onDismiss()
                            },
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.sleep_timer_turn_off),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerStoryCommentsSheets(
    showStory: Boolean,
    showComments: Boolean,
    item: MusicItem,
    onDismissStory: () -> Unit,
    onDismissComments: () -> Unit
) {
    if (showStory) {
        StoryShareBottomSheet(item = item, onDismiss = onDismissStory)
    }

    if (showComments) {
        CommentsBottomSheet(item = item, onDismiss = onDismissComments)
    }
}

@Composable
fun PlayerFullscreenVideoOverlay(
    item: MusicItem,
    isPlaying: Boolean,
    isLoading: Boolean,
    currentPosition: Long,
    duration: Long,
    controller: MediaController?,
    viewModel: PlayerViewModel,
    playerUiState: PlayerUiState,
    onTogglePlay: () -> Unit,
    onPositionChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (playerUiState.isFullScreenVideo) {
        Box(
            modifier = modifier
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
                    onPositionChange(newPos)
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

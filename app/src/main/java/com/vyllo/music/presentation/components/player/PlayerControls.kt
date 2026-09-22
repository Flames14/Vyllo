package com.vyllo.music.presentation.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.vyllo.music.LocalLibraryViewModel
import com.vyllo.music.PlayerUiState
import com.vyllo.music.PlayerViewModel
import com.vyllo.music.R
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.presentation.components.iosPressClickable

@Composable
fun PlayerActionPills(
    item: MusicItem,
    playerUiState: PlayerUiState,
    viewModel: PlayerViewModel,
    expandProgress: Float,
    onLyricsClick: () -> Unit,
    onShowComments: () -> Unit,
    onShowStoryShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val libraryViewModel = LocalLibraryViewModel.current
    val haptic = LocalHapticFeedback.current
    // 4. Action Pills Row (Like/Dislike, Comments, Save, Share, Download)
    Row(
        modifier = modifier
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
            onLyricsClick()
        }

        // Comments Pill (Real YouTube Comment Count)
        val displayedComments = playerUiState.commentCountFormatted ?: "Comments"
        YtmPillButton(icon = Icons.AutoMirrored.Rounded.Comment, label = displayedComments) {
            onShowComments()
        }

        // Save to Playlist Pill
        YtmPillButton(icon = Icons.AutoMirrored.Rounded.PlaylistAdd, label = "Save") {
            libraryViewModel.showPlaylistAddDialog(item)
        }

        // Share Pill
        YtmPillButton(icon = Icons.Rounded.Share, label = "Share") {
            onShowStoryShare()
        }

        // Download Pill
        YtmPillButton(icon = Icons.Rounded.Download, label = "Download") {
            libraryViewModel.downloadSong(item)
        }
    }
}

@Composable
fun PlayerPlaybackControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    currentPosition: Long,
    duration: Long,
    controller: MediaController?,
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    expandProgress: Float,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatChange: (Int) -> Unit,
    onPositionChange: (Long) -> Unit,
    onSeekFeedback: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    // 6. Playback Controls Row (Shuffle | Prev | Replay10 | Play/Pause Circle | Forward10 | Next | Repeat)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha((1f - (expandProgress * 2.5f)).coerceIn(0f, 1f)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val newShuffle = !shuffleModeEnabled
                onShuffleChange(newShuffle)
                controller?.shuffleModeEnabled = newShuffle
                // Keep the service-side queue order in sync (MediaController listener
                // in MusicService reorders PlaybackQueueManager on this change).
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
                onPositionChange(newPos)
                onSeekFeedback("-10")
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

        // Main Big Play/Pause Circle with Apple Damped Spring
        Surface(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .iosPressClickable(pressScale = 0.90f) {
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
                onPositionChange(newPos)
                onSeekFeedback("+10")
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
                val newRepeat = when (repeatMode) {
                    androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
                    androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
                    else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                }
                onRepeatChange(newRepeat)
                controller?.repeatMode = newRepeat
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

/**
 * Apple-Style Audio Output Route & Lossless Audio Quality Pills.
 * Decorative status pills (no-op taps) mirroring the Apple Music player.
 */
@Composable
fun PlayerAudioPills(
    expandProgress: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .alpha((1f - (expandProgress * 2.5f)).coerceIn(0f, 1f)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Route Pill (AirPlay style)
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.08f),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
            modifier = Modifier.iosPressClickable { }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Speaker,
                    contentDescription = "Audio Output",
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "Phone Speaker",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Apple Lossless Audio Quality Pill
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.08f),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
            modifier = Modifier.iosPressClickable { }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.HighQuality,
                    contentDescription = "Audio Quality",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Lossless • 256k",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

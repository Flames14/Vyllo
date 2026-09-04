package com.vyllo.music.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.vyllo.music.PlayerViewModel
import com.vyllo.music.domain.model.MusicItem

@Composable
fun PremiumPlayerContainer(
    musicItem: MusicItem?, 
    isPlaying: Boolean,
    isLoading: Boolean,
    controller: MediaController?, 
    relatedSongs: List<MusicItem>,
    isAutoplayEnabled: Boolean,
    onTogglePlay: () -> Unit, 
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onExpand: () -> Unit, 
    onCollapse: () -> Unit, 
    isExpanded: Boolean,
    onAutoplayToggle: (Boolean) -> Unit,
    onPlayRelated: (MusicItem) -> Unit,
    viewModel: PlayerViewModel
) {
    if (musicItem == null) return

    val haptic = LocalHapticFeedback.current
    val playerUiState by viewModel.uiState.collectAsState()
    val isActuallyExpanded = isExpanded || playerUiState.isInPipMode

    if (isActuallyExpanded) {
        PremiumFullScreenPlayer(
            item = musicItem, 
            isPlaying = isPlaying,
            isLoading = isLoading,
            controller = controller, 
            relatedSongs = relatedSongs,
            isAutoplayEnabled = isAutoplayEnabled,
            onTogglePlay = onTogglePlay, 
            onNext = onNext, 
            onPrev = onPrev, 
            onCollapse = onCollapse,
            onAutoplayToggle = onAutoplayToggle,
            onPlayRelated = onPlayRelated,
            viewModel = viewModel
        )
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clickable { 
                    onExpand() 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 6.dp
        ) {
            PremiumMiniPlayer(
                musicItem = musicItem,
                isPlaying = isPlaying,
                isLoading = isLoading,
                onTogglePlay = onTogglePlay,
                controller = controller,
                onNext = onNext,
                onPrev = onPrev
            )
        }
    }
}

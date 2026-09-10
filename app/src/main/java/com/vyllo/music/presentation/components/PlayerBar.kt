package com.vyllo.music.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
        val isDark = com.vyllo.music.presentation.theme.ThemeManager.isDarkColor(MaterialTheme.colorScheme.background)
        val cardColor = if (isDark) {
            Color(0xFF202024).copy(alpha = 0.94f)
        } else {
            Color(0xFFF7F7F9).copy(alpha = 0.96f)
        }
        val borderColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .height(64.dp)
                .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = Color.Black.copy(alpha = 0.35f))
                .clip(RoundedCornerShape(14.dp))
                .iosPressClickable(pressScale = 0.985f) { 
                    onExpand() 
                },
            shape = RoundedCornerShape(14.dp),
            color = cardColor,
            border = BorderStroke(0.5.dp, borderColor),
            tonalElevation = 0.dp
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

package com.vyllo.music.presentation.components.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.vyllo.music.PlayerUiState
import com.vyllo.music.PlayerViewModel
import com.vyllo.music.presentation.components.LyricsViewContent

@Composable
fun PlayerLyricsTab(
    playerUiState: PlayerUiState,
    viewModel: PlayerViewModel,
    controller: MediaController?,
    modifier: Modifier = Modifier
) {
    // Lyrics & Live Translator View
    LyricsViewContent(
        playerUiState = playerUiState,
        viewModel = viewModel,
        controller = controller,
        onSeek = { controller?.seekTo(it) },
        modifier = modifier.fillMaxSize().padding(horizontal = 8.dp)
    )
}

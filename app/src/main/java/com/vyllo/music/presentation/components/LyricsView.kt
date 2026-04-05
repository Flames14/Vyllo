package com.vyllo.music.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import com.vyllo.music.PlayerViewModel
import com.vyllo.music.PlayerUiState
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.LyricsEngine
import com.vyllo.music.domain.model.LyricsResult
import com.vyllo.music.domain.model.SyncedLyricLine
import kotlinx.coroutines.launch

// ============================================================
// THEME-ADAPTIVE COLORS FOR LYRICS AREA
// ============================================================
private object LyricsColors {
    val surface = Color(0xFF1A1A2E)
    val surfaceVariant = Color(0xFF16213E)
    val cardBg = Color(0xFF0F3460)
    val textPrimary = Color(0xFFEAEAEA)
    val textSecondary = Color(0xFFB0B0B0)
    val textInactive = Color(0xFF707070)
    val border = Color(0xFF2A2A4A)
    val overlay = Color(0xFFFFFFFF).copy(alpha = 0.06f)
    val overlaySelected = Color(0xFFFFFFFF).copy(alpha = 0.12f)
    val accent = Color(0xFF00D4AA)
}

// ============================================================
// MAIN LYRICS TAB CONTENT
// ============================================================
@Composable
fun androidx.compose.foundation.lazy.LazyItemScope.LyricsTabContent(
    playerUiState: PlayerUiState,
    viewModel: PlayerViewModel,
    controller: MediaController?,
    onSeek: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().height(520.dp)
    ) {
        // --- SYNC CONTROLS ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(LyricsColors.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Sync, null, tint = LyricsColors.textInactive, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Lyrics Sync", style = MaterialTheme.typography.labelMedium, color = LyricsColors.textInactive)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.adjustLyricsOffset(-500L) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.RemoveCircleOutline, null, tint = LyricsColors.textSecondary, modifier = Modifier.size(18.dp))
                }
                Text(
                    "${if (viewModel.lyricsOffsetMs >= 0) "+" else ""}${viewModel.lyricsOffsetMs / 1000.0}s",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (viewModel.lyricsOffsetMs != 0L) LyricsColors.accent else LyricsColors.textSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(onClick = { viewModel.adjustLyricsOffset(500L) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.AddCircleOutline, null, tint = LyricsColors.textSecondary, modifier = Modifier.size(18.dp))
                }
                if (viewModel.lyricsOffsetMs != 0L) {
                    TextButton(onClick = { viewModel.lyricsOffsetMs = 0L }, modifier = Modifier.height(28.dp)) {
                        Text("Reset", style = MaterialTheme.typography.labelSmall, color = LyricsColors.textSecondary)
                    }
                }
            }
        }

        // --- MAIN CONTENT AREA ---
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(LyricsColors.surface)
                .border(1.dp, LyricsColors.border, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Determine what to show
            val hasLyricsResponse = playerUiState.lyricsResponse != null
            val hasSyncedLines = playerUiState.syncedLyricsLines.isNotEmpty()
            val isSearching = playerUiState.showLyricsSelector

            when {
                playerUiState.lyricsLoading -> {
                    // Loading state
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = LyricsColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(36.dp))
                        Text("Fetching lyrics…", style = MaterialTheme.typography.bodyMedium, color = LyricsColors.textSecondary)
                    }
                }

                isSearching -> {
                    // Manual search mode
                    LyricsManualSearchScreen(playerUiState, viewModel)
                }

                hasSyncedLines -> {
                    // Has synced lyrics
                    SyncedLyricsDisplay(playerUiState, viewModel, controller, onSeek)
                }

                hasLyricsResponse && playerUiState.lyricsResponse?.plainLyrics != null -> {
                    // Has plain text lyrics
                    PlainLyricsDisplay(playerUiState, viewModel)
                }

                hasLyricsResponse && !playerUiState.lyricsResponse!!.success -> {
                    // Failed to find lyrics
                    LyricsFailedState(playerUiState, viewModel)
                }

                !hasLyricsResponse && !playerUiState.lyricsLoading -> {
                    // No lyrics response yet (shouldn't happen, but fallback)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.MusicNote, null, tint = LyricsColors.textInactive, modifier = Modifier.size(48.dp))
                        Text("Tap a song to show lyrics", style = MaterialTheme.typography.bodyMedium, color = LyricsColors.textSecondary)
                        FilledTonalButton(onClick = {
                            SecureLogger.d("LyricsTabContent", "User tapped search button from empty state")
                            viewModel.showLyricsSelector = true
                        }) {
                            Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Search for lyrics")
                        }
                    }
                }
            }
        }

        // --- ACTION ROW ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.toggleTranslation(!playerUiState.isTranslationEnabled) }) {
                Icon(Icons.Rounded.Translate, null,
                    tint = if (playerUiState.isTranslationEnabled) LyricsColors.accent else LyricsColors.textInactive)
            }
            TextButton(onClick = {
                SecureLogger.d("LyricsTabContent", "User tapped 'Search / Change lyrics' button")
                viewModel.showLyricsSelector = true
            }) {
                Text("Search / Change lyrics", color = LyricsColors.textSecondary)
            }
            if (playerUiState.isTranslating) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = LyricsColors.accent
                )
            }
        }

    }
}

// ============================================================
// MANUAL SEARCH SCREEN
// ============================================================
@Composable
private fun LyricsManualSearchScreen(playerUiState: PlayerUiState, viewModel: PlayerViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Search Lyrics", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = LyricsColors.textPrimary)
            IconButton(onClick = { viewModel.showLyricsSelector = false }) {
                Icon(Icons.Rounded.Close, null, tint = LyricsColors.textSecondary)
            }
        }

        // Search field
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(LyricsColors.overlay)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, null, tint = LyricsColors.textInactive, modifier = Modifier.size(18.dp))
            TextField(
                value = playerUiState.lyricsSearchQuery,
                onValueChange = {
                    SecureLogger.d("LyricsSearch", "User typing: '$it'")
                    viewModel.lyricsSearchQuery = it
                },
                placeholder = { Text("Artist - Song", color = LyricsColors.textInactive) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = LyricsColors.textPrimary),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = LyricsColors.accent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    SecureLogger.d("LyricsSearch", "User pressed search for: '${viewModel.lyricsSearchQuery}'")
                    viewModel.searchForLyrics(viewModel.lyricsSearchQuery)
                }),
                modifier = Modifier.weight(1f)
            )
            if (playerUiState.lyricsSearching) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = LyricsColors.accent, strokeWidth = 2.dp)
            } else {
                IconButton(onClick = {
                    SecureLogger.d("LyricsSearch", "User clicked search button for: '${viewModel.lyricsSearchQuery}'")
                    viewModel.searchForLyrics(viewModel.lyricsSearchQuery)
                }) {
                    Icon(Icons.Rounded.ArrowForward, null, tint = LyricsColors.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Results
        if (playerUiState.lyricsSearchResults.isNotEmpty()) {
            Text(
                "${playerUiState.lyricsSearchResults.size} results",
                style = MaterialTheme.typography.labelSmall,
                color = LyricsColors.textInactive,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                playerUiState.lyricsSearchResults.forEach { result ->
                    LyricsResultCard(result, isSelected = false) {
                        SecureLogger.d("LyricsSearch", "User selected: ${result.trackName}")
                        viewModel.selectAlternativeLyrics(result)
                    }
                }
            }
        } else if (!playerUiState.lyricsSearching && playerUiState.lyricsSearchQuery.isNotBlank()) {
            Text(
                "No results found",
                style = MaterialTheme.typography.bodyMedium,
                color = LyricsColors.textSecondary,
                modifier = Modifier.padding(vertical = 16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ============================================================
// LYRICS RESULT CARD
// ============================================================
@Composable
private fun LyricsResultCard(result: LyricsResult, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) LyricsColors.overlaySelected else LyricsColors.overlay)
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(18.dp).clip(CircleShape)
                .background(if (isSelected) LyricsColors.accent else LyricsColors.textInactive),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) Icon(Icons.Rounded.Check, null, tint = LyricsColors.surface, modifier = Modifier.size(12.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(result.trackName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium), color = LyricsColors.textPrimary, maxLines = 1)
            Text(result.artistName, style = MaterialTheme.typography.bodySmall, color = LyricsColors.textSecondary, maxLines = 1)
        }
        if (result.syncedLyrics != null) {
            Text("Synced", style = MaterialTheme.typography.labelSmall, color = LyricsColors.accent)
        } else {
            Text("Plain", style = MaterialTheme.typography.labelSmall, color = LyricsColors.textInactive)
        }
    }
}

// ============================================================
// SYNCED LYRICS DISPLAY
// ============================================================
@Composable
private fun SyncedLyricsDisplay(
    playerUiState: PlayerUiState,
    viewModel: PlayerViewModel,
    controller: MediaController?,
    onSeek: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        playerUiState.syncedLyricsLines.forEachIndexed { index, line ->
            val isActive = playerUiState.currentLyricIndex == index
            val alpha by animateFloatAsState(if (isActive) 1f else 0.35f)
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clickable { controller?.seekTo(line.startTimeMs); onSeek(line.startTimeMs) }
                    .padding(vertical = 6.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    line.content,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                        lineHeight = 26.sp
                    ),
                    color = LyricsColors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().alpha(alpha)
                )
                if (playerUiState.isTranslationEnabled && !playerUiState.isTranslating && index < playerUiState.translatedLyricsLines.size) {
                    val translated = playerUiState.translatedLyricsLines[index]
                    if (!translated.isNullOrBlank()) {
                        Text(
                            translated,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                            color = LyricsColors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().alpha(alpha)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// PLAIN LYRICS DISPLAY
// ============================================================
@Composable
private fun PlainLyricsDisplay(playerUiState: PlayerUiState, viewModel: PlayerViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        val text = if (playerUiState.isTranslationEnabled && !playerUiState.translatedPlainLyrics.isNullOrBlank()) {
            playerUiState.translatedPlainLyrics
        } else {
            playerUiState.lyricsResponse?.plainLyrics
        } ?: ""
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
            color = LyricsColors.textPrimary
        )
    }
}

// ============================================================
// LYRICS FAILED STATE
// ============================================================
@Composable
private fun LyricsFailedState(playerUiState: PlayerUiState, viewModel: PlayerViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Rounded.SearchOff, null, tint = LyricsColors.textInactive, modifier = Modifier.size(40.dp))
        Text("Lyrics not found", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = LyricsColors.textSecondary)
        Text("Try searching manually", style = MaterialTheme.typography.bodySmall, color = LyricsColors.textInactive)
        FilledTonalButton(onClick = {
            SecureLogger.d("LyricsTabContent", "User tapped 'Search for lyrics' from failed state")
            viewModel.showLyricsSelector = true
        }) {
            Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Search for lyrics")
        }
        // Show error message if available
        val error = playerUiState.lyricsResponse?.error
        if (error != null && error.isNotBlank()) {
            Text(error, style = MaterialTheme.typography.labelSmall, color = LyricsColors.textInactive, modifier = Modifier.padding(horizontal = 16.dp), textAlign = TextAlign.Center)
        }
    }
}

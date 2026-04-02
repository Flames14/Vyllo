package com.vyllo.music.presentation.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import com.vyllo.music.PlayerViewModel
import com.vyllo.music.data.LyricsResult

@Composable
fun androidx.compose.foundation.lazy.LazyItemScope.LyricsTabContent(
    viewModel: PlayerViewModel, 
    controller: MediaController?,
    onSeek: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(520.dp) // Significantly increased size for a dedicated experience
    ) {
        // --- SYNC CONTROLS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Sync, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Lyrics Sync",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(0.4f)
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.adjustLyricsOffset(-500L) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Rounded.RemoveCircleOutline, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(20.dp))
                }
                
                Text(
                    text = "${if (viewModel.lyricsOffsetMs >= 0) "+" else ""}${viewModel.lyricsOffsetMs / 1000.0}s",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (viewModel.lyricsOffsetMs != 0L) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                
                IconButton(
                    onClick = { viewModel.adjustLyricsOffset(500L) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Rounded.AddCircleOutline, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(20.dp))
                }

                if (viewModel.lyricsOffsetMs != 0L) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { viewModel.lyricsOffsetMs = 0L },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Reset", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                viewModel.lyricsLoading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp), color = Color.White.copy(0.7f), strokeWidth = 2.dp)
                        Text("Fetching lyrics…", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.5f))
                    }
                }
                viewModel.showLyricsSelector -> {
                    LyricsSelectorContent(viewModel)
                }
                viewModel.syncedLyricsLines.isEmpty() && (viewModel.lyricsResponse == null || !viewModel.lyricsResponse!!.success) && !viewModel.lyricsLoading -> {
                    LyricsNotFoundContent(viewModel)
                }
                viewModel.syncedLyricsLines.isEmpty() && !viewModel.lyricsLoading -> {
                    PlainLyricsContent(viewModel)
                }
                else -> {
                    SyncedLyricsContent(viewModel, controller, onSeek)
                }
            }
        }

        LyricsActionRow(viewModel)
    }
}

@Composable
fun LyricsSelectorContent(viewModel: PlayerViewModel) {
    val alternatives = viewModel.lyricsResponse?.results ?: emptyList()
    val currentResult = viewModel.lyricsResponse?.result

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Choose Lyrics",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            IconButton(onClick = {
                viewModel.showLyricsSelector = false
                viewModel.clearLyricsSearchResults()
            }) {
                Icon(Icons.Rounded.Close, null, tint = Color.White.copy(0.7f))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            TextField(
                value = viewModel.lyricsSearchQuery,
                onValueChange = { viewModel.lyricsSearchQuery = it },
                placeholder = { Text("Search lyrics...", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.3f)) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color.White
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.searchForLyrics(viewModel.lyricsSearchQuery) }),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.searchForLyrics(viewModel.lyricsSearchQuery) }) {
                if (viewModel.lyricsSearching) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White.copy(0.7f), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.ArrowForward, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(20.dp))
            }
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            if (alternatives.isNotEmpty()) {
                Text("Auto-matched", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.4f), modifier = Modifier.padding(vertical = 8.dp))
                alternatives.forEach { alt ->
                    LyricsCandidateCard(alt, alt.id == currentResult?.id) { viewModel.selectAlternativeLyrics(alt) }
                }
            }
            if (viewModel.lyricsSearchResults.isNotEmpty()) {
                Text("Search results", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.4f), modifier = Modifier.padding(vertical = 8.dp))
                viewModel.lyricsSearchResults.forEach { alt ->
                    LyricsCandidateCard(alt, alt.id == currentResult?.id) { viewModel.selectAlternativeLyrics(alt) }
                }
            }
        }
    }
}

@Composable
fun LyricsCandidateCard(result: LyricsResult, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (isSelected) Color.White.copy(0.18f) else Color.White.copy(0.06f)).clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(if (isSelected) Color.White else Color.White.copy(0.15f)), contentAlignment = Alignment.Center) {
            if (isSelected) Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(result.trackName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal), color = Color.White, maxLines = 1)
            Text(result.artistName, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f), maxLines = 1)
        }
        Text(if (result.syncedLyrics != null) "Synced" else "Plain", style = MaterialTheme.typography.labelSmall, color = if (result.syncedLyrics != null) Color(0xFF4CAF50) else Color.White.copy(0.4f))
    }
}

@Composable
fun LyricsNotFoundContent(viewModel: PlayerViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.SearchOff, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(40.dp))
        Text("Lyrics not found", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color.White.copy(0.7f))
        FilledTonalButton(onClick = { viewModel.showLyricsSelector = true }) {
            Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Search for lyrics")
        }
    }
}

@Composable
fun PlainLyricsContent(viewModel: PlayerViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(viewModel.lyricsResponse?.plainLyrics ?: "", style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp), color = Color.White.copy(0.9f))
        if (viewModel.isTranslationEnabled && viewModel.translatedPlainLyrics != null) {
            Spacer(Modifier.height(12.dp))
            Text("Translation", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White.copy(0.4f))
            Text(viewModel.translatedPlainLyrics!!, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp), color = Color.White.copy(0.65f))
        }
    }
}

@Composable
fun SyncedLyricsContent(viewModel: PlayerViewModel, controller: MediaController?, onSeek: (Long) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(viewModel.currentLyricIndex) {
        if (viewModel.currentLyricIndex >= 0) listState.animateScrollToItem(viewModel.currentLyricIndex, scrollOffset = -50)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 40.dp, bottom = 100.dp)) {
            itemsIndexed(viewModel.syncedLyricsLines) { index, line ->
                val isActive = viewModel.currentLyricIndex == index
                val alpha by animateFloatAsState(if (isActive) 1f else 0.3f)
                Text(
                    line.content,
                    modifier = Modifier.fillMaxWidth().alpha(alpha).clickable { controller?.seekTo(line.startTimeMs); onSeek(line.startTimeMs) }.padding(vertical = 8.dp, horizontal = 16.dp),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium, lineHeight = 28.sp),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun LyricsActionRow(viewModel: PlayerViewModel) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.toggleTranslation(!viewModel.isTranslationEnabled) }) {
            Icon(Icons.Rounded.Translate, null, tint = if (viewModel.isTranslationEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(0.5f))
        }
        TextButton(onClick = { viewModel.showLyricsSelector = true }) {
            Text("Wrong lyrics?", color = Color.White.copy(0.5f))
        }
    }
}

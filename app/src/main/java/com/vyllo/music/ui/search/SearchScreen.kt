package com.vyllo.music.ui.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.*
import com.vyllo.music.presentation.components.*
import com.vyllo.music.ui.components.*

// =========================================================================
// YTM SEARCH SCREEN
// =========================================================================
@Composable
fun YTMSearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onPlay: (MusicItem) -> Unit,
    currentPlayingItem: MusicItem?,
    scrollState: LazyListState,
    loadingItemUrl: String? = null
) {
    val context = LocalContext.current

    // Speech recognition launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull() ?: ""
            if (spokenText.isNotBlank()) {
                viewModel.searchQuery = spokenText
                viewModel.performSearch(spokenText)
            }
        }
    }

    // Permission launcher for RECORD_AUDIO
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a song name or artist…")
            }
            viewModel.isListening = true
            speechLauncher.launch(intent)
        } else {
            Toast.makeText(context, "Microphone permission is needed for voice search", Toast.LENGTH_SHORT).show()
        }
    }

    // Mic button pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val micScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar with Filter Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 48.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.onBackground.copy(0.08f))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = viewModel.searchQuery,
                        onValueChange = viewModel::onQueryChanged,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.performSearch(viewModel.searchQuery) }),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (viewModel.searchQuery.isEmpty()) {
                                Text("Search songs, albums, artists", color = MaterialTheme.colorScheme.onBackground.copy(0.4f))
                            }
                            innerTextField()
                        }
                    )

                    if (viewModel.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.onQueryChanged("") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close, "Clear",
                                tint = MaterialTheme.colorScheme.onBackground.copy(0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                                    Toast.makeText(context, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show()
                                    return@IconButton
                                }
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a song name or artist…")
                                    }
                                    viewModel.isListening = true
                                    speechLauncher.launch(intent)
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Mic, "Voice Search",
                                tint = if (viewModel.isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(0.6f),
                                modifier = Modifier.size(22.dp).then(
                                    if (viewModel.isListening) Modifier.graphicsLayer(scaleX = micScale, scaleY = micScale) else Modifier
                                )
                            )
                        }
                    }
                }
            }
        }
        
        // Content
        if (!viewModel.isSearching) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (viewModel.searchQuery.isEmpty() && viewModel.searchHistory.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Recent searches",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            TextButton(onClick = { viewModel.clearSearchHistory() }) {
                                Text("Clear", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    items(viewModel.searchHistory) { historyItem ->
                        HistorySuggestionRow(historyItem) { viewModel.performSearch(historyItem) }
                    }
                } else if (viewModel.suggestions.isNotEmpty()) {
                    items(
                        items = viewModel.suggestions,
                        key = { it }
                    ) { suggestion ->
                        PremiumSuggestionRow(suggestion) { viewModel.performSearch(suggestion) }
                    }
                }
            }
        } else {
            // Search Results
            LazyColumn(
                state = scrollState,
                contentPadding = PaddingValues(bottom = if (currentPlayingItem != null) 140.dp else 16.dp)
            ) {
                itemsIndexed(
                    items = viewModel.searchResults,
                    key = { index, item -> "search_result_${index}_${item.url}" },
                    contentType = { _, _ -> "song_row" }
                ) { _, item ->
                    YTMSongRow(
                        item = item,
                        isPlaying = currentPlayingItem?.title == item.title,
                        onClick = { onPlay(item) },
                        searchViewModel = viewModel,
                        isLoading = loadingItemUrl == item.url
                    )
                }
                
                if (viewModel.isLoadingMore || viewModel.isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

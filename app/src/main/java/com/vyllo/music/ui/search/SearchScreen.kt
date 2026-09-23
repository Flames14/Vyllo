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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
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
import com.vyllo.music.presentation.theme.VylloRadius
import com.vyllo.music.presentation.theme.VylloSize
import com.vyllo.music.presentation.theme.VylloSpacing
import com.vyllo.music.ui.components.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import com.vyllo.music.R

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
                .statusBarsPadding()
                .padding(
                    start = VylloSpacing.xs,
                    end = VylloSpacing.screenHorizontal,
                    top = VylloSpacing.sm,
                    bottom = VylloSpacing.sm
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(VylloSize.toolbar)
                    .clip(RoundedCornerShape(VylloRadius.pill))
                    .background(MaterialTheme.colorScheme.onBackground.copy(0.08f))
                    .padding(horizontal = VylloSpacing.lg),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }

                    androidx.compose.foundation.text.BasicTextField(
                        value = viewModel.searchQuery,
                        onValueChange = viewModel::onQueryChanged,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.performSearch(viewModel.searchQuery) }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged { state -> 
                                if (state.isFocused) viewModel.onSearchFieldFocused() 
                            },
                        decorationBox = { innerTextField ->
                            if (viewModel.searchQuery.isEmpty()) {
                                Text(
                                    stringResource(R.string.search_placeholder_long),
                                    color = MaterialTheme.colorScheme.onBackground.copy(0.4f)
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (viewModel.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.onQueryChanged("") },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close, "Clear search",
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
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Mic, "Search by voice",
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
        
        // Content: Live Suggestions & Search History or Results
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
                    items(
                        items = viewModel.searchHistory,
                        key = { "history_$it" },
                        contentType = { "history_row" }
                    ) { historyItem ->
                        HistorySuggestionRow(
                            text = historyItem, 
                            onClick = { viewModel.performSearch(historyItem) },
                            onInsert = { viewModel.insertSuggestion(historyItem) }
                        )
                    }
                } else if (viewModel.suggestions.isNotEmpty() || viewModel.searchQuery.isNotEmpty()) {
                    // YouTube Music style: direct search prompt for current input if not blank
                    if (viewModel.searchQuery.isNotBlank()) {
                        item(key = "search_exact_query") {
                            PremiumSuggestionRow(
                                text = viewModel.searchQuery,
                                onClick = { viewModel.performSearch(viewModel.searchQuery) }
                            )
                        }
                    }
                    items(
                        items = viewModel.suggestions.filter { !it.equals(viewModel.searchQuery, ignoreCase = true) },
                        key = { "suggestion_$it" },
                        contentType = { "suggestion_row" }
                    ) { suggestion ->
                        PremiumSuggestionRow(
                            text = suggestion, 
                            onClick = { viewModel.performSearch(suggestion) },
                            onInsert = { viewModel.insertSuggestion(suggestion) }
                        )
                    }
                } else {
                    // Idle state: nothing typed, no history, no suggestions.
                    // Previously this rendered a blank white screen behind the keyboard.
                    item(key = "search_idle_empty") {
                        VylloEmptyState(
                            icon = Icons.Rounded.Search,
                            title = "Search Vyllo",
                            message = "Find songs, albums and artists. You can also tap the mic to search by voice — your recent searches will appear here.",
                            modifier = Modifier.padding(top = VylloSpacing.xxl)
                        )
                    }
                }
            }
        } else {
            // Search Results
            LazyColumn(
                state = scrollState,
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (viewModel.searchResults.isEmpty() &&
                    (viewModel.isLoading || viewModel.isLoadingMore)
                ) {
                    // Skeleton instead of a bare spinner so the layout never jumps.
                    item(key = "search_skeleton", contentType = "skeleton") {
                        ListSkeleton(rows = 7)
                    }
                } else if (viewModel.searchResults.isEmpty() && !viewModel.isLoading) {
                    item(key = "search_no_results", contentType = "empty") {
                        VylloEmptyState(
                            icon = Icons.Rounded.SearchOff,
                            title = "No results found",
                            message = "We couldn't find anything for \u201C${viewModel.searchQuery}\u201D. Check the spelling or try a different search.",
                            actionLabel = "Clear search",
                            onAction = { viewModel.onQueryChanged("") },
                            modifier = Modifier.padding(top = VylloSpacing.xxl)
                        )
                    }
                }

                items(
                    items = viewModel.searchResults,
                    key = { item -> "search_result_${item.url}" },
                    contentType = { "song_row" }
                ) { item ->
                    YTMSongRow(
                        item = item,
                        isPlaying = currentPlayingItem?.url == item.url,
                        onClick = { onPlay(item) },
                        searchViewModel = viewModel,
                        isLoading = loadingItemUrl == item.url
                    )
                }
                
                if ((viewModel.isLoadingMore || viewModel.isLoading) && viewModel.searchResults.isNotEmpty()) {
                    item(key = "search_loading", contentType = "loader") {
                        VylloLoadingIndicator()
                    }
                }
            }
        }
    }
}

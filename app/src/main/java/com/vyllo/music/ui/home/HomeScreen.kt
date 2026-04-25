package com.vyllo.music.ui.home

import android.content.ComponentName
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.widget.Toast
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.vyllo.music.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import com.vyllo.music.domain.model.SyncedLyricLine
import com.vyllo.music.domain.model.LyricsResponse
import com.vyllo.music.domain.model.LyricsResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.LocalImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.vyllo.music.service.MusicService
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.data.download.PlaylistEntity
import com.vyllo.music.data.download.PlaylistSongEntity
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.platform.LocalConfiguration
import kotlin.math.sin
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.random.Random
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.core.content.ContextCompat

import com.vyllo.music.*
import com.vyllo.music.presentation.components.*
import com.vyllo.music.ui.components.*

// =========================================================================
// YTM HOME SCREEN
// =========================================================================
@Composable
fun YTMHomeScreen(
    viewModel: HomeViewModel,
    onPlay: (MusicItem) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRecognizeClick: () -> Unit,
    currentPlayingItem: MusicItem?,
    loadingItemUrl: String? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    // Memoize filterChips to avoid list allocation during recomposition
    val filterChips = remember { listOf("All", "Relax", "Energize", "Workout", "Focus", "Commute") }
    val scrollState = rememberLazyListState()
    val context = LocalContext.current
    
    // --- LIQUID SMOOTH SCROLL ---
    val liquidFlingBehavior = rememberLiquidFlingBehavior(uiState.isLiquidScrollEnabled)
    
    // Memoize recommendedItems calculation for performance
    val recommendedItems = uiState.quickPicksItems
    
    // Infinite scroll
    LaunchedEffect(scrollState) {
        snapshotFlow {
            val layoutInfo = scrollState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 3
        }.collect { atBottom ->
            if (atBottom && uiState.selectedChip == "All") {
                viewModel.loadMoreRecommendations()
            }
        }
    }
    
    // Pre-cache only what's visible on the first screen
    val imageLoader = coil.Coil.imageLoader(context)
    LaunchedEffect(
        uiState.listenAgainItems,
        uiState.trendingNowItems
    ) {
        val visibleUrls = (uiState.listenAgainItems.take(6) + uiState.trendingNowItems.take(6))
            .map { it.thumbnailUrl }
            .filter { it.isNotBlank() }
            .distinct()

        visibleUrls.forEach { url ->
            imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(300, 300)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build()
            )
        }
    }

    LazyColumn(
        state = scrollState,
        flingBehavior = liquidFlingBehavior,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (currentPlayingItem != null) 140.dp else 16.dp)
    ) {
        // YTM Header
        item {
            YTMHeader(
                onSearchClick = onSearchClick,
                onSettingsClick = onSettingsClick,
                onRecognizeClick = onRecognizeClick,
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refreshAllContent() }
            )
        }
        
        // Filter Chips
        item {
            LazyRow(
                modifier = Modifier.padding(vertical = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    count = filterChips.size,
                    key = { index -> filterChips[index] }
                ) { index ->
                    YTMFilterChip(
                        text = filterChips[index],
                        isSelected = uiState.selectedChip == filterChips[index],
                        onClick = { viewModel.onChipSelected(filterChips[index]) }
                    )
                }
            }
        }
        
        // Listen Again Section
        if (uiState.listenAgainItems.isNotEmpty()) {
            item(key = "listen_again_header") {
                YTMSectionHeader(title = stringResource(R.string.section_listen_again))
            }
            item(key = "listen_again_row") {
                OptimizedHorizontalSection(
                    items = uiState.listenAgainItems,
                    currentPlayingItem = currentPlayingItem,
                    onPlay = onPlay,
                    isLargeCard = false,
                    homeViewModel = viewModel,
                    loadingItemUrl = loadingItemUrl
                )
            }
        }
        
        // Quick Picks Section
        if (uiState.quickPicksRows.isNotEmpty()) {
            item(key = "quick_picks_header") {
                Spacer(modifier = Modifier.height(24.dp))
                YTMSectionHeader(title = stringResource(R.string.section_quick_picks))
            }
            YTMGridSection(
                rows = uiState.quickPicksRows.take(3),
                currentPlayingItem = currentPlayingItem,
                onPlay = onPlay,
                homeViewModel = viewModel,
                loadingItemUrl = loadingItemUrl,
                sectionKey = "quick_picks"
            )
        }
        
        // Chill Vibes Section
        if (uiState.chillItems.isNotEmpty()) {
            item(key = "chill_header") {
                Spacer(modifier = Modifier.height(24.dp))
                YTMSectionHeader(title = stringResource(R.string.section_chill_vibes))
            }
            item(key = "chill_row") {
                OptimizedHorizontalSection(
                    items = uiState.chillItems,
                    currentPlayingItem = currentPlayingItem,
                    onPlay = onPlay,
                    isLargeCard = false,
                    homeViewModel = viewModel,
                    loadingItemUrl = loadingItemUrl
                )
            }
        }
        
        // Mixed For You Section (Large Cards)
        if (uiState.mixedForYouItems.isNotEmpty()) {
            item(key = "mixed_header") {
                Spacer(modifier = Modifier.height(24.dp))
                YTMSectionHeader(title = stringResource(R.string.section_mixed_for_you))
            }
            item(key = "mixed_row") {
                OptimizedHorizontalSection(
                    items = uiState.mixedForYouItems,
                    currentPlayingItem = currentPlayingItem,
                    onPlay = onPlay,
                    isLargeCard = true,
                    homeViewModel = viewModel,
                    loadingItemUrl = loadingItemUrl
                )
            }
        }
        
        // Workout Music Section
        if (uiState.workoutItems.isNotEmpty()) {
            item(key = "workout_header") {
                Spacer(modifier = Modifier.height(24.dp))
                YTMSectionHeader(title = stringResource(R.string.section_workout))
            }
            item(key = "workout_row") {
                OptimizedHorizontalSection(
                    items = uiState.workoutItems,
                    currentPlayingItem = currentPlayingItem,
                    onPlay = onPlay,
                    isLargeCard = false,
                    homeViewModel = viewModel,
                    loadingItemUrl = loadingItemUrl
                )
            }
        }
        
        // Focus Music Section
        if (uiState.focusItems.isNotEmpty()) {
            item(key = "focus_header") {
                Spacer(modifier = Modifier.height(24.dp))
                YTMSectionHeader(title = stringResource(R.string.section_focus))
            }
            item(key = "focus_row") {
                OptimizedHorizontalSection(
                    items = uiState.focusItems,
                    currentPlayingItem = currentPlayingItem,
                    onPlay = onPlay,
                    isLargeCard = true,
                    homeViewModel = viewModel,
                    loadingItemUrl = loadingItemUrl
                )
            }
        }
        
        // Trending Now Section
        if (uiState.trendingNowRows.isNotEmpty()) {
            item(key = "trending_header") {
                Spacer(modifier = Modifier.height(24.dp))
                YTMSectionHeader(title = "Trending now")
            }
            YTMGridSection(
                rows = uiState.trendingNowRows.take(3),
                currentPlayingItem = currentPlayingItem,
                onPlay = onPlay,
                homeViewModel = viewModel,
                loadingItemUrl = loadingItemUrl,
                sectionKey = "trending_now"
            )
        }
        
        // New Releases Section
        if (uiState.newReleasesItems.isNotEmpty()) {
            item(key = "new_releases_header") {
                Spacer(modifier = Modifier.height(24.dp))
                YTMSectionHeader(title = stringResource(R.string.section_new_releases))
            }
            item(key = "new_releases_row") {
                OptimizedHorizontalSection(
                    items = uiState.newReleasesItems,
                    currentPlayingItem = currentPlayingItem,
                    onPlay = onPlay,
                    isLargeCard = false,
                    homeViewModel = viewModel,
                    loadingItemUrl = loadingItemUrl
                )
            }
        }
        
        // Recommended Section
        if (uiState.quickPicksItems.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                YTMSectionHeader(title = "Recommended")
            }
            itemsIndexed(
                items = recommendedItems,
                key = { index, item -> "${item.url}_$index" },
                contentType = { _, _ -> "song_row" }
            ) { _, item ->
                YTMSongRow(
                    item = item,
                    isPlaying = currentPlayingItem?.title == item.title,
                    onClick = { onPlay(item) },
                    homeViewModel = viewModel,
                    isLoading = loadingItemUrl == item.url
                )
            }
        }
        
        // Infinite scroll loading indicator
        if (uiState.isLoadingMoreRecommendations) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        // Loading Indicator
        if (uiState.isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                }
            }
        }
    }
    
    // Full-screen refresh overlay for better visual feedback
    if (uiState.isRefreshing) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.TopCenter
        ) {
            Card(
                modifier = Modifier.padding(top = 80.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Refreshing...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Fetching latest content",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

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
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
@OptIn(ExperimentalMaterial3Api::class)
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

    val pullToRefreshState = rememberPullToRefreshState()
    
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.refreshAllContent()
        }
    }
    LaunchedEffect(uiState.isRefreshing) {
        if (uiState.isRefreshing) {
            pullToRefreshState.startRefresh()
        } else {
            pullToRefreshState.endRefresh()
        }
    }

    val isScrolling = scrollState.isScrollInProgress

    Box(modifier = Modifier.fillMaxSize().nestedScroll(pullToRefreshState.nestedScrollConnection)) {
        // Living Ambient Dark Aura with 60Hz/120Hz scroll throttling
        HomeAmbientDarkAura(isScrolling = isScrolling)

        LazyColumn(
            state = scrollState,
            flingBehavior = liquidFlingBehavior,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // YTM Header
            item(key = "ytm_header", contentType = "header") {
                YTMHeader(
                    onSettingsClick = onSettingsClick,
                    onRecognizeClick = onRecognizeClick
                )
            }
        
        // Filter Chips
        item(key = "filter_chips_row", contentType = "filter_row") {
            LazyRow(
                modifier = Modifier.padding(vertical = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    count = filterChips.size,
                    key = { index -> filterChips[index] },
                    contentType = { "filter_chip" }
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
    } // End LazyColumn

    PullToRefreshContainer(
        state = pullToRefreshState,
        modifier = Modifier.align(Alignment.TopCenter)
    )
} // End Box
}

/**
 * 🌌 Ambient Dynamic Aura / Subtle Living Cosmic Mesh for Home Screen
 * Dynamically adjusts for both Dark and Light themes with pristine contrast.
 */
@Composable
fun HomeAmbientDarkAura(
    isScrolling: Boolean = false,
    modifier: Modifier = Modifier
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val isDark = com.vyllo.music.presentation.theme.ThemeManager.isDarkColor(backgroundColor)

    val infiniteTransition = rememberInfiniteTransition(label = "DarkAuraTransition")
    val livePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "AuraPhase"
    )

    var frozenPhase by remember { mutableFloatStateOf(0f) }
    if (!isScrolling) {
        frozenPhase = livePhase
    }
    val phase = if (isScrolling) frozenPhase else livePhase

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        if (isDark) {
            // Dark Mode: Deep cosmic black base with rich ambient glows
            drawRect(Color(0xFF0A0A0E))

            val aura1X = width * (0.8f + 0.1f * kotlin.math.sin(phase))
            val aura1Y = height * (0.15f + 0.08f * kotlin.math.cos(phase))

            val aura2X = width * (0.15f + 0.1f * kotlin.math.cos(phase * 0.8f))
            val aura2Y = height * (0.45f + 0.1f * kotlin.math.sin(phase * 0.8f))

            val aura3X = width * (0.75f + 0.15f * kotlin.math.sin(phase * 1.2f))
            val aura3Y = height * (0.75f + 0.08f * kotlin.math.cos(phase * 1.2f))

            // 1. Violet / Cosmic Purple Glow (Top Right)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF5B1A8C).copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(aura1X, aura1Y),
                    radius = width * 0.85f
                ),
                center = Offset(aura1X, aura1Y),
                radius = width * 0.85f
            )

            // 2. Deep Emerald / Oceanic Teal Glow (Middle Left)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF00695C).copy(alpha = 0.13f), Color.Transparent),
                    center = Offset(aura2X, aura2Y),
                    radius = width * 0.75f
                ),
                center = Offset(aura2X, aura2Y),
                radius = width * 0.75f
            )

            // 3. Deep Ruby / Amber Glow (Bottom Right)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF880E4F).copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(aura3X, aura3Y),
                    radius = width * 0.8f
                ),
                center = Offset(aura3X, aura3Y),
                radius = width * 0.8f
            )
        } else {
            // Light Mode: Clean YouTube Music crisp white with ultra-subtle ambient warmth
            drawRect(backgroundColor)

            val aura1X = width * (0.85f + 0.08f * kotlin.math.sin(phase))
            val aura1Y = height * (0.12f + 0.06f * kotlin.math.cos(phase))

            val aura2X = width * (0.12f + 0.08f * kotlin.math.cos(phase * 0.8f))
            val aura2Y = height * (0.42f + 0.08f * kotlin.math.sin(phase * 0.8f))

            // Subtle warm red / coral ambient hint (Top Right)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF0000).copy(alpha = 0.035f), Color.Transparent),
                    center = Offset(aura1X, aura1Y),
                    radius = width * 0.8f
                ),
                center = Offset(aura1X, aura1Y),
                radius = width * 0.8f
            )

            // Subtle cool sky ambient hint (Middle Left)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF007AFF).copy(alpha = 0.025f), Color.Transparent),
                    center = Offset(aura2X, aura2Y),
                    radius = width * 0.7f
                ),
                center = Offset(aura2X, aura2Y),
                radius = width * 0.7f
            )
        }
    }
}

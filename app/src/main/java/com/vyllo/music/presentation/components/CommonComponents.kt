package com.vyllo.music.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import com.vyllo.music.LibraryViewModel
import com.vyllo.music.HomeViewModel
import com.vyllo.music.LocalLibraryViewModel
import com.vyllo.music.domain.model.MusicItem

@Composable 
fun PremiumAccent() = MaterialTheme.colorScheme.primary

@Composable
fun GlassWhite() = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)

@Composable
fun GlassBorder() = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)

@Composable
fun YTMHeader(
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onRecognizeClick: () -> Unit = {}
) {
    // Animation for refresh icon rotation (continuous spin when refreshing)
    val refreshRotation by animateFloatAsState(
        targetValue = if (isRefreshing) 1440f else 0f,
        animationSpec = tween(durationMillis = if (isRefreshing) 1200 else 300, easing = FastOutSlowInEasing),
        label = "refreshRotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 56.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.PlayCircleFilled,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Music",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Refresh Button with continuous rotation
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isRefreshing) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        strokeWidth = 2.dp
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh",
                        tint = if (isRefreshing) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { rotationZ = refreshRotation }
                    )
                }
            }
            // Search Button
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Rounded.Search, "Search", tint = MaterialTheme.colorScheme.onBackground)
            }
            // Music Recognition Button
            IconButton(
                onClick = onRecognizeClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Rounded.GraphicEq, "Recognize", tint = MaterialTheme.colorScheme.onBackground)
            }
            // Settings Button
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Rounded.Settings, "Settings", tint = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

@Composable
fun YTMFilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    
    Box(
        modifier = Modifier
            .height(36.dp)
            .graphicsLayer { 
                clip = true 
                shape = RoundedCornerShape(12.dp)
            }
            .background(bgColor)
            .then(
                if (!isSelected) Modifier.border(1.dp, MaterialTheme.colorScheme.onBackground.copy(0.1f), RoundedCornerShape(12.dp))
                else Modifier
            )
            .bounceClick { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}

@Composable
fun YTMSectionHeader(title: String, onSeeAll: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        if (onSeeAll != null) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "See all",
                tint = MaterialTheme.colorScheme.onBackground.copy(0.6f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadButton(item: MusicItem) {
    val viewModel = LocalLibraryViewModel.current
    val itemUrl = item.url
    val isDownloaded by remember(itemUrl) {
        derivedStateOf { viewModel.downloadedSongs.any { it.url == itemUrl } }
    }
    val progress by remember(itemUrl) {
        derivedStateOf { viewModel.downloadProgress[itemUrl] }
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .combinedClickable(
                onClick = {
                    if (progress != null) {
                        viewModel.cancelDownload(itemUrl)
                    } else if (!isDownloaded) {
                        viewModel.downloadSong(item)
                    }
                },
                onLongClick = {
                    if (isDownloaded) {
                        viewModel.deleteDownload(itemUrl)
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (progress != null) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress!! / 100f },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Cancel",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Icon(
                imageVector = if (isDownloaded) Icons.Rounded.CheckCircle else Icons.Rounded.DownloadForOffline,
                contentDescription = "Download",
                tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
@Composable
fun PlaylistAddDialog(
    viewModel: LibraryViewModel,
    onDismiss: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    
    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreateDialog = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("New Playlist", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                
                if (viewModel.allPlaylists.isEmpty()) {
                    Text(
                        "No playlists yet",
                        modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    )
                } else {
                    viewModel.allPlaylists.forEach { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    viewModel.songToAddToPlaylist?.let { 
                                        viewModel.addSongToPlaylist(playlist.id, it) 
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.PlaylistPlay, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(playlist.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var playlistName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Playlist") },
        text = {
            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text("Playlist Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (playlistName.isNotBlank()) onCreate(playlistName) },
                enabled = playlistName.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun YTMBottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    hasActivePlayer: Boolean
) {
    val tabs = listOf(
        Pair("Home", Icons.Rounded.Home),
        Pair("Explore", Icons.Rounded.Explore),
        Pair("Library", Icons.Rounded.LibraryMusic)
    )
    
    NavigationBar(
        modifier = Modifier.padding(bottom = if (hasActivePlayer) 72.dp else 0.dp),
        containerColor = MaterialTheme.colorScheme.background.copy(0.95f),
        tonalElevation = 0.dp
    ) {
        tabs.forEachIndexed { index, (label, icon) ->
            NavigationBarItem(
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onBackground.copy(0.5f),
                    unselectedTextColor = MaterialTheme.colorScheme.onBackground.copy(0.5f),
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(0.1f)
                )
            )
        }
    }
}

@Composable
fun PremiumGlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit
) {
    val isSearching = query.isNotEmpty()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(GlassWhite())
            .border(1.dp, GlassBorder(), RoundedCornerShape(30.dp))
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSearching) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                }
            } else {
                Icon(Icons.Filled.Search, "Search", tint = MaterialTheme.colorScheme.onBackground.copy(0.6f))
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text("Search songs, artists...", color = MaterialTheme.colorScheme.onBackground.copy(0.4f), style = MaterialTheme.typography.titleMedium)
                    }
                    innerTextField()
                }
            )
            
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, "Clear", tint = MaterialTheme.colorScheme.onBackground.copy(0.6f))
                }
            }
        }
    }
}

@Composable
fun OptimizedHorizontalSection(
    items: List<MusicItem>,
    currentPlayingItem: MusicItem?,
    onPlay: (MusicItem) -> Unit,
    isLargeCard: Boolean,
    homeViewModel: HomeViewModel? = null,
    loadingItemUrl: String? = null
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(
            items = items,
            key = { it.url }
        ) { item ->
            if (isLargeCard) {
                YTMLargeCard(
                    item = item,
                    isPlaying = currentPlayingItem?.url == item.url,
                    onClick = { onPlay(item) },
                    homeViewModel = homeViewModel,
                    isLoading = loadingItemUrl == item.url
                )
            } else {
                YTMSquareCard(
                    item = item,
                    isPlaying = currentPlayingItem?.url == item.url,
                    onClick = { onPlay(item) },
                    homeViewModel = homeViewModel,
                    isLoading = loadingItemUrl == item.url
                )
            }
        }
    }
}

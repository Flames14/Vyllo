package com.vyllo.music.presentation.components.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import com.vyllo.music.LocalLibraryViewModel
import com.vyllo.music.PlayerUiState
import com.vyllo.music.PlayerViewModel
import com.vyllo.music.domain.model.MusicItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun PlayerQueueSheet(
    item: MusicItem,
    relatedSongs: List<MusicItem>,
    playerUiState: PlayerUiState,
    viewModel: PlayerViewModel,
    controller: MediaController?,
    sheetOffsetY: Animatable<Float, AnimationVector1D>,
    sheetMaxOffset: Float,
    expandProgress: Float,
    isSheetExpanded: Boolean,
    selectedDrawerTab: Int,
    onTabSelected: (Int) -> Unit,
    onPlayRelated: (MusicItem) -> Unit,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val libraryViewModel = LocalLibraryViewModel.current
    // ==========================================
    // 7. SWIPEABLE "UP NEXT" QUEUE DRAWER
    // ==========================================
    val queueSheetHeightDp = (configuration.screenHeightDp * 0.78f).dp

    val sheetDraggableState = rememberDraggableState { delta ->
        coroutineScope.launch {
            sheetOffsetY.snapTo((sheetOffsetY.value + delta).coerceIn(0f, sheetMaxOffset))
        }
    }

    val sheetDragModifier = Modifier.draggable(
        state = sheetDraggableState,
        orientation = Orientation.Vertical,
        onDragStopped = { velocity ->
            coroutineScope.launch {
                val target = when {
                    velocity > 400f -> sheetMaxOffset // Effortless fast swipe down -> close!
                    velocity < -400f -> 0f           // Effortless fast swipe up -> open!
                    sheetOffsetY.value > sheetMaxOffset * 0.35f -> sheetMaxOffset // 35% down -> close smoothly!
                    else -> 0f
                }
                sheetOffsetY.animateTo(
                    target,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                )
            }
        }
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(queueSheetHeightDp)
            .offset { IntOffset(0, sheetOffsetY.value.toInt()) }
            .shadow(28.dp, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), spotColor = Color.Black.copy(alpha = 0.5f))
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(Color(0xFF1E1E22).copy(alpha = 0.96f))
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Header Bar with full draggable touch tracking
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(sheetDragModifier)
                    .padding(vertical = 8.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag Pill Handle
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.45f))
                        .clickable {
                            coroutineScope.launch {
                                val target = if (isSheetExpanded) sheetMaxOffset else 0f
                                sheetOffsetY.animateTo(target, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                            }
                        }
                )

                Spacer(Modifier.height(8.dp))

                if (!isSheetExpanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    sheetOffsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                                }
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = stringResource(com.vyllo.music.R.string.accessibility_swipe_up),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = stringResource(com.vyllo.music.R.string.queue_swipe_up_hint),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = stringResource(com.vyllo.music.R.string.queue_auto_mix, item.title),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Save Mix Button
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.1f),
                            contentColor = Color.White,
                            modifier = Modifier.clickable { libraryViewModel.showPlaylistAddDialog(item) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.PlaylistAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Save", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                } else {
                    // YouTube Music Drawer Tabs: UP NEXT | LYRICS | RELATED + Quick Collapse Chevron Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("UP NEXT", "LYRICS", "RELATED").forEachIndexed { idx, tabTitle ->
                                val isTabSelected = selectedDrawerTab == idx
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onTabSelected(idx) }
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = tabTitle,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                                            letterSpacing = 1.sp
                                        ),
                                        color = if (isTabSelected) Color.White else Color.White.copy(alpha = 0.5f)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    if (isTabSelected) {
                                        Box(
                                            modifier = Modifier
                                                .width(32.dp)
                                                .height(2.dp)
                                                .clip(RoundedCornerShape(1.dp))
                                                .background(Color.White)
                                        )
                                    } else {
                                        Spacer(Modifier.height(2.dp))
                                    }
                                }
                            }
                        }

                        // Dedicated Close / Collapse Chevron Button
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    sheetOffsetY.animateTo(sheetMaxOffset, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Collapse Queue",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

            // Drawer Content Switcher
            when (selectedDrawerTab) {
                0 -> {
                    PlayerUpNextTab(
                        item = item,
                        relatedSongs = relatedSongs,
                        playerUiState = playerUiState,
                        viewModel = viewModel,
                        onPlayRelated = onPlayRelated
                    )
                }
                1 -> {
                    PlayerLyricsTab(
                        playerUiState = playerUiState,
                        viewModel = viewModel,
                        controller = controller,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                2 -> {
                    PlayerRelatedTab(
                        item = item,
                        playerUiState = playerUiState,
                        onPlayRelated = onPlayRelated,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun QueueSongRowItem(
    item: MusicItem,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with GraphicEq if active — remembered request so scroll
        // frames never rebuild decoders (was allocating per recomposition).
        Box(modifier = Modifier.size(48.dp)) {
            val context = LocalContext.current
            val thumbRequest = remember(item.thumbnailUrl) {
                coil.request.ImageRequest.Builder(context)
                    .data(item.thumbnailUrl)
                    .size(160, 160)
                    .crossfade(false)
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                    .build()
            }
            AsyncImage(
                model = thumbRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
            )
            if (isActive) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium),
                color = if (isActive) Color.White else Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.uploader,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        // Drag handle icon (=)
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = "Reorder",
            tint = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp)
        )
    }
}

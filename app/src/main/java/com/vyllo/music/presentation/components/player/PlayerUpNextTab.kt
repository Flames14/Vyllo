package com.vyllo.music.presentation.components.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.vyllo.music.PlayerUiState
import com.vyllo.music.PlayerViewModel
import com.vyllo.music.R
import com.vyllo.music.domain.model.MusicItem
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun PlayerUpNextTab(
    item: MusicItem,
    playerUiState: PlayerUiState,
    viewModel: PlayerViewModel,
    onPlayRelated: (MusicItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val queueItems = playerUiState.queueItems
    val queueListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var menuForUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }
    val isRefreshing = playerUiState.isLoadingRelatedTab
    val undoLabel = stringResource(R.string.action_undo)
    val removedMessage = stringResource(R.string.queue_item_removed)
    val clearedMessage = stringResource(R.string.queue_cleared)

    // Only run the spin animation while refreshing; a permanently-running
    // transition recomposes at 60fps during scrolling.
    val rotation = if (isRefreshing) {
        val infiniteTransition = rememberInfiniteTransition(label = "refresh_transition")
        val r by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "refresh_spin"
        )
        r
    } else {
        0f
    }

    // ------------------------------------------------------------------
    // Long-press drag to reorder Up Next (hand-rolled; no dependency).
    //
    // A drag freezes the queue into [draggingList] so a mid-gesture autoplay
    // refill cannot shift rows under the finger. Rows swap live as the dragged
    // row's centre crosses a neighbour, but the order is committed to the queue
    // manager exactly ONCE, on drop — so queueVersion, persistence and the
    // ExoPlayer lookahead are touched a single time per reorder, not per frame.
    // ------------------------------------------------------------------
    val density = LocalDensity.current
    val autoScrollEdgePx = with(density) { 72.dp.toPx() }
    val autoScrollStepPx = with(density) { 14.dp.toPx() }

    var draggingStartIndex by remember { mutableStateOf<Int?>(null) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var draggingOffsetY by remember { mutableFloatStateOf(0f) }
    var draggingList by remember { mutableStateOf<List<MusicItem>?>(null) }
    var autoScrollDir by remember { mutableIntStateOf(0) }

    // Latest queue for the gesture callback, which outlives its composition.
    val latestQueue by rememberUpdatedState(queueItems)

    fun resetDrag() {
        draggingStartIndex = null
        draggingIndex = null
        draggingOffsetY = 0f
        autoScrollDir = 0
    }

    // Recomputes the edge auto-scroll direction and performs at most one swap.
    fun evaluateReorder() {
        val from = draggingIndex ?: return
        val list = draggingList ?: return
        val dragged = list.getOrNull(from) ?: return
        val draggedKey = "upnext_${dragged.url}"
        val layout = queueListState.layoutInfo
        val draggedInfo = layout.visibleItemsInfo.firstOrNull { it.key == draggedKey } ?: return
        val size = draggedInfo.size
        if (size <= 0) return

        val center = draggedInfo.offset + draggingOffsetY + size / 2f
        autoScrollDir = when {
            center < layout.viewportStartOffset + autoScrollEdgePx -> -1
            center > layout.viewportEndOffset - autoScrollEdgePx -> 1
            else -> 0
        }

        val target = layout.visibleItemsInfo.firstOrNull { vi ->
            val key = vi.key as? String
            key != null && key.startsWith("upnext_") && key != draggedKey &&
                center >= vi.offset && center < vi.offset + vi.size
        } ?: return
        val to = list.indexOfFirst { "upnext_${it.url}" == target.key }
        if (to < 0 || to == from) return

        draggingList = list.toMutableList().also { it.add(to, it.removeAt(from)) }
        draggingOffsetY -= (to - from) * size
        draggingIndex = to
    }

    // Keeps scrolling while the finger hovers near an edge, so long queues are
    // reachable in one gesture; each step re-checks for a swap under the finger.
    LaunchedEffect(autoScrollDir, draggingIndex) {
        if (draggingIndex == null || autoScrollDir == 0) return@LaunchedEffect
        while (isActive) {
            val consumed = queueListState.scrollBy(autoScrollDir * autoScrollStepPx)
            if (consumed == 0f) break
            evaluateReorder()
            withFrameNanos { }
        }
    }

    // Drop the frozen snapshot only once the committed order has propagated back,
    // so there is no flicker between the local order and the queue flow.
    LaunchedEffect(queueItems) {
        if (draggingIndex == null) draggingList = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = queueListState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
        ) {
            item {
                QueueSongRowItem(item = item, isActive = true, onClick = { })
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.queue_playing_from),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "- " + pluralStringResource(
                                R.plurals.queue_track_count,
                                queueItems.size,
                                queueItems.size
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }

                    Row {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.forceRefreshRelatedSongs()
                            },
                            modifier = Modifier.size(32.dp),
                            enabled = !isRefreshing
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.cd_refresh_up_next),
                                tint = if (isRefreshing) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(20.dp).graphicsLayer {
                                    if (isRefreshing) rotationZ = rotation
                                }
                            )
                        }
                        IconButton(
                            onClick = { showClearConfirmation = true },
                            modifier = Modifier.size(32.dp),
                            enabled = queueItems.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Rounded.Clear,
                                contentDescription = stringResource(R.string.cd_clear_queue),
                                tint = Color.White.copy(alpha = if (queueItems.isEmpty()) 0.3f else 0.75f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            if (isRefreshing) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                }
            }

            // This list mirrors PlaybackQueueManager, not relatedSongs. The latter
            // is a recommendation pool and cannot safely represent user edits.
            itemsIndexed(
                items = draggingList ?: queueItems,
                key = { _, song -> "upnext_${song.url}" },
                contentType = { _, _ -> "queue_row" }
            ) { index, song ->
                val isDragging = draggingIndex == index
                val currentIndex by rememberUpdatedState(index)
                val rowModifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        if (isDragging) {
                            translationY = draggingOffsetY
                            shadowElevation = 12f
                        }
                    }
                    .pointerInput(song.url) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggingList = latestQueue
                                draggingStartIndex = currentIndex
                                draggingIndex = currentIndex
                                draggingOffsetY = 0f
                                autoScrollDir = 0
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (draggingIndex == null) return@detectDragGesturesAfterLongPress
                                draggingOffsetY += dragAmount.y
                                evaluateReorder()
                            },
                            onDragEnd = {
                                val from = draggingStartIndex
                                val to = draggingIndex
                                if (from != null && to != null && from != to) {
                                    viewModel.moveInQueue(from, to)
                                } else {
                                    // No-op drag: nothing to wait for, unfreeze now.
                                    draggingList = null
                                }
                                resetDrag()
                            },
                            onDragCancel = {
                                draggingList = null
                                resetDrag()
                            }
                        )
                    }
                QueueSongRowItem(
                    item = song,
                    isActive = false,
                    onClick = { onPlayRelated(song) },
                    modifier = rowModifier,
                    trailingContent = {
                        Box {
                            IconButton(onClick = { menuForUrl = song.url }) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.cd_queue_options),
                                    tint = Color.White.copy(alpha = 0.75f)
                                )
                            }
                            DropdownMenu(
                                expanded = menuForUrl == song.url,
                                onDismissRequest = { menuForUrl = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.queue_move_up)) },
                                    onClick = {
                                        viewModel.moveInQueueBy(index, -1)
                                        menuForUrl = null
                                    },
                                    enabled = index > 0
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.queue_move_down)) },
                                    onClick = {
                                        viewModel.moveInQueueBy(index, 1)
                                        menuForUrl = null
                                    },
                                    enabled = index < queueItems.lastIndex
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.queue_remove)) },
                                    onClick = {
                                        menuForUrl = null
                                        val removed = viewModel.removeFromQueue(song.url)
                                        if (removed != null) {
                                            coroutineScope.launch {
                                                if (snackbarHostState.showSnackbar(
                                                        message = removedMessage,
                                                        actionLabel = undoLabel
                                                    ) == SnackbarResult.ActionPerformed
                                                ) {
                                                    viewModel.undoQueueRemoval(removed, index)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                )
            }

            if (playerUiState.isLoadingMoreRelated) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White.copy(alpha = 0.6f),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(stringResource(R.string.queue_clear_title)) },
            text = { Text(stringResource(R.string.queue_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirmation = false
                    val removed = viewModel.clearUpcoming()
                    if (removed.isNotEmpty()) {
                        coroutineScope.launch {
                            if (snackbarHostState.showSnackbar(
                                    message = clearedMessage,
                                    actionLabel = undoLabel
                                ) == SnackbarResult.ActionPerformed
                            ) {
                                viewModel.undoQueueClear(removed)
                            }
                        }
                    }
                }) {
                    Text(stringResource(R.string.common_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

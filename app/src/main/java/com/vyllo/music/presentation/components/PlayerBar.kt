package com.vyllo.music.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import com.vyllo.music.PlayerViewModel
import com.vyllo.music.domain.model.MusicItem
import kotlinx.coroutines.launch

@Composable
fun PremiumPlayerContainer(
    musicItem: MusicItem?, 
    isPlaying: Boolean,
    isLoading: Boolean,
    controller: MediaController?, 
    relatedSongs: List<MusicItem>,
    isAutoplayEnabled: Boolean,
    onTogglePlay: () -> Unit, 
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onExpand: () -> Unit, 
    onCollapse: () -> Unit, 
    isExpanded: Boolean,
    onAutoplayToggle: (Boolean) -> Unit,
    onPlayRelated: (MusicItem) -> Unit,
    viewModel: PlayerViewModel
) {
    if (musicItem == null) return

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    
    val containerHeight = if (isExpanded) screenHeight else 80.dp
    val targetCorner = if(isExpanded) 0.dp else 24.dp
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isExpanded) Modifier.fillMaxSize() 
                else Modifier.height(containerHeight).padding(horizontal = 8.dp, vertical = 8.dp)
            )
            .shadow(
                elevation = if(isExpanded) 0.dp else 16.dp, 
                shape = RoundedCornerShape(targetCorner),
                spotColor = Color.Black
            )
            .clip(RoundedCornerShape(targetCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = !isExpanded) { 
                onExpand() 
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
    ) {
        if (isExpanded) {
             val offsetY = remember { Animatable(0f) }
             val scope = rememberCoroutineScope()
             
             Box(
                 modifier = Modifier
                     .fillMaxSize()
                     .offset { androidx.compose.ui.unit.IntOffset(0, offsetY.value.toInt()) }
                     .draggable(
                         orientation = Orientation.Vertical,
                         state = rememberDraggableState { delta ->
                             scope.launch {
                                 val newOffset = offsetY.value + delta
                                 if (newOffset >= 0) offsetY.snapTo(newOffset)
                             }
                         },
                         onDragStopped = { velocity ->
                             if (offsetY.value > 300f || velocity > 1000f) {
                                 onCollapse()
                                 haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                             } else {
                                 offsetY.animateTo(0f)
                             }
                         }
                     )
             ) {
                ImmersiveBackground(imageUrl = musicItem.thumbnailUrl) {
                   PremiumFullScreenPlayer(
                       item = musicItem, 
                       isPlaying = isPlaying,
                       isLoading = isLoading,
                       controller = controller, 
                       relatedSongs = relatedSongs,
                       isAutoplayEnabled = isAutoplayEnabled,
                       onTogglePlay = onTogglePlay, 
                       onNext = onNext, 
                       onPrev = onPrev, 
                       onCollapse = onCollapse,
                       onAutoplayToggle = onAutoplayToggle,
                       onPlayRelated = onPlayRelated,
                       viewModel = viewModel
                   )
                }
             }
        } else {
             PremiumMiniPlayer(musicItem, isPlaying, isLoading, onTogglePlay)
        }
    }
}

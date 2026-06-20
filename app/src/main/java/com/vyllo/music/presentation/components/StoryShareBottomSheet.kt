package com.vyllo.music.presentation.components

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vyllo.music.core.utils.ShareIntentManager
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.presentation.utils.captureToPicture
import com.vyllo.music.presentation.utils.createBitmapFromPicture
import com.vyllo.music.presentation.utils.rememberPicture
import com.vyllo.music.presentation.utils.saveBitmapToFile
import kotlinx.coroutines.launch

enum class StoryVibe(val title: String, val colors: List<Color>, val message: String) {
    LOFI("Lo-fi", listOf(Color(0xFF2C3E50), Color(0xFFE74C3C)), "Late night overthinking soundtrack"),
    PHONK("Phonk", listOf(Color(0xFF000000), Color(0xFF8E44AD), Color(0xFFC0392B)), "Main character mode activated"),
    ROMANTIC("Romantic", listOf(Color(0xFF9B59B6), Color(0xFFE056FD)), "In my feelings right now"),
    SAD("Sad", listOf(Color(0xFF0A192F), Color(0xFF172A45)), "Rainy window energy"),
    EDM("EDM", listOf(Color(0xFF11998E), Color(0xFF38EF7D)), "Vibe check passed"),
    CYBERPUNK("Cyberpunk", listOf(Color(0xFFF8049C), Color(0xFF0DEAFB)), "Night City cruising"),
    RETROWAVE("Retrowave", listOf(Color(0xFF2A0845), Color(0xFF6441A5)), "80s nostalgia hit"),
    SUNSET("Sunset", listOf(Color(0xFFFF512F), Color(0xFFDD2476)), "Golden hour vibes"),
    OCEAN("Ocean", listOf(Color(0xFF2193b0), Color(0xFF6dd5ed)), "Deep dive thoughts"),
    MIDNIGHT("Midnight", listOf(Color(0xFF232526), Color(0xFF414345)), "2:00 AM thoughts"),
    NEON("Neon", listOf(Color(0xFF00C6FF), Color(0xFF0072FF)), "Electric energy"),
    FOREST("Forest", listOf(Color(0xFF134E5E), Color(0xFF71B280)), "Lost in the woods"),
    LAVA("Lava", listOf(Color(0xFFFF9966), Color(0xFFFF5E62)), "Absolute fire"),
    GALAXY("Galaxy", listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243E)), "Floating in space"),
    CHILL("Chill", listOf(Color(0xFF4CA1AF), Color(0xFFC4E0E5)), "Just vibing")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryShareBottomSheet(
    item: MusicItem,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedVibe by remember { mutableStateOf(StoryVibe.LOFI) }
    val picture = rememberPicture()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isGenerating by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Share Story",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            // The Story Frame Preview (This is what gets captured)
            Box(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(24.dp))
                    .captureToPicture(picture) // Capture this exact box
            ) {
                // Background Image with heavy blur
                var thumbnailError by remember { mutableStateOf(false) }
                val imageUrl = if (thumbnailError) item.thumbnailUrl else item.getHighResThumbnailUrl()
                
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(imageUrl)
                        .allowHardware(false) // Fixes Software rendering crash!
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(40.dp),
                    onState = { state ->
                        if (state is coil.compose.AsyncImagePainter.State.Error) {
                            thumbnailError = true
                        }
                    }
                )

                // Dark/Tint Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .background(Brush.verticalGradient(
                            colors = listOf(
                                selectedVibe.colors.first().copy(alpha = 0.4f),
                                selectedVibe.colors.last().copy(alpha = 0.7f)
                            )
                        ))
                )

                // Main Content
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Space / Vibe Badge
                    Box(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = selectedVibe.message.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = Color.White
                        )
                    }

                    // Glassmorphism Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Album Art
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(16.dp))
                            ) {
                                AsyncImage(
                                    model = coil.request.ImageRequest.Builder(context)
                                        .data(imageUrl)
                                        .allowHardware(false)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.uploader,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // Progress bar
                            Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))) {
                                Box(modifier = Modifier.fillMaxWidth(0.3f).height(4.dp).clip(RoundedCornerShape(50)).background(Color.White))
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Fake Playback Controls
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipPrevious,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(28.dp)
                                )
                                Icon(
                                    imageVector = Icons.Rounded.PlayCircleFilled,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    // Bottom Watermark
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "VYLLO MUSIC",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Vibe Selector
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(StoryVibe.values()) { vibe ->
                    val isSelected = selectedVibe == vibe
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { selectedVibe = vibe }
                    ) {
                        Text(
                            text = vibe.title,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        if (isGenerating) return@Button
                        isGenerating = true
                        coroutineScope.launch {
                            try {
                                val bitmap = createBitmapFromPicture(picture)
                                if (bitmap != null) {
                                    val file = saveBitmapToFile(context, bitmap)
                                    if (file != null) {
                                        ShareIntentManager.shareToInstagramStory(context, file)
                                    } else {
                                        Toast.makeText(context, "Failed to generate image", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } finally {
                                isGenerating = false
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C)) // Instagram Pink
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("IG Story", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = {
                        if (isGenerating) return@Button
                        isGenerating = true
                        coroutineScope.launch {
                            try {
                                val bitmap = createBitmapFromPicture(picture)
                                if (bitmap != null) {
                                    val file = saveBitmapToFile(context, bitmap)
                                    if (file != null) {
                                        ShareIntentManager.shareToWhatsApp(context, file)
                                    } else {
                                        Toast.makeText(context, "Failed to generate image", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } finally {
                                isGenerating = false
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)) // WhatsApp Green
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("WhatsApp", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            val bitmap = createBitmapFromPicture(picture)
                            if (bitmap != null) {
                                val file = saveBitmapToFile(context, bitmap)
                                if (file != null) {
                                    ShareIntentManager.shareToAny(context, file, "Listening to ${item.title} on Vyllo")
                                }
                            }
                        }
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

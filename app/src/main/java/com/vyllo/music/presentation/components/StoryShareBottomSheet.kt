package com.vyllo.music.presentation.components

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vyllo.music.core.utils.ShareIntentManager
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.presentation.utils.captureToPicture
import com.vyllo.music.presentation.utils.createBitmapFromPicture
import com.vyllo.music.presentation.utils.rememberPicture
import com.vyllo.music.presentation.utils.saveBitmapToFile
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sin

enum class ShareCardTemplate(val title: String, val subtitle: String) {
    OBSIDIAN("Studio Obsidian", "Hi-Fi Card"),
    GLASS_CANVAS("Glass Canvas", "Editorial Soundwave")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryShareBottomSheet(
    item: MusicItem,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTemplate by remember { mutableStateOf(ShareCardTemplate.OBSIDIAN) }
    val picture = rememberPicture()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isGenerating by remember { mutableStateOf(false) }

    // Use guaranteed valid thumbnail URL
    val imageUrl = remember(item) {
        if (item.thumbnailUrl.isNotBlank()) item.thumbnailUrl else item.getHighResThumbnailUrl()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0D0D11),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Share Story",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "Clean music card for Instagram & WhatsApp",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // Template Selector Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ShareCardTemplate.values().forEach { template ->
                    val isSelected = selectedTemplate == template
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) Color.White else Color(0xFF1C1C22),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .clickable { selectedTemplate = template }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (template == ShareCardTemplate.OBSIDIAN) Icons.Rounded.Headphones else Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = if (isSelected) Color.Black else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = template.title,
                                color = if (isSelected) Color.Black else Color.White,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // The Story Frame Preview (Captured exact 9:16 Canvas)
            Box(
                modifier = Modifier
                    .width(260.dp)
                    .aspectRatio(9f / 16f)
                    .shadow(28.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .captureToPicture(picture)
            ) {
                when (selectedTemplate) {
                    ShareCardTemplate.OBSIDIAN -> {
                        ObsidianStudioStoryCard(
                            item = item,
                            imageUrl = imageUrl,
                            context = context
                        )
                    }
                    ShareCardTemplate.GLASS_CANVAS -> {
                        GlassCanvasStoryCard(
                            item = item,
                            imageUrl = imageUrl,
                            context = context
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons Row 1: Social Story Sharing (IG + WhatsApp + Any)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // IG Story Button
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
                                    }
                                }
                            } finally {
                                isGenerating = false
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("IG Story", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // WhatsApp Status / Chat Button
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
                                        ShareIntentManager.shareToWhatsApp(context, file, item)
                                    }
                                }
                            } finally {
                                isGenerating = false
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("WhatsApp", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Share Image Card to Any App
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            val bitmap = createBitmapFromPicture(picture)
                            if (bitmap != null) {
                                val file = saveBitmapToFile(context, bitmap)
                                if (file != null) {
                                    val shareUrl = item.getUniversalShareUrl()
                                    ShareIntentManager.shareToAny(
                                        context,
                                        file,
                                        "🎵 Listening to \"${item.title}\" by ${item.uploader} on Vyllo Music\n🔗 $shareUrl"
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF22222A), CircleShape)
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share Image", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row 2: Direct Link Sharing & Clipboard Copy
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Copy Link Button
                OutlinedButton(
                    onClick = {
                        ShareIntentManager.copySongLink(context, item)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy Link", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                // Share Direct Song Link Button
                Button(
                    onClick = {
                        ShareIntentManager.shareSongLink(context, item)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text("Share Link", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * 🏛️ TEMPLATE 1: "Vyllo Obsidian Studio" (Hi-Fi Clean Minimal Luxury)
 */
@Composable
private fun ObsidianStudioStoryCard(
    item: MusicItem,
    imageUrl: String,
    context: Context
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09090D))
    ) {
        // Ambient Blurred Glow
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .crossfade(false)
                    .allowHardware(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(70.dp)
                    .alpha(0.32f)
            )
        }

        // Geometric Ambient Halo Arcs
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = Color.White.copy(alpha = 0.04f),
                center = center,
                radius = size.width * 0.65f,
                style = Stroke(width = 1f)
            )
        }

        // Content Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Elegantly Written Brand
            Text(
                text = "VYLLO",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp,
                    fontSize = 15.sp
                ),
                color = Color.White,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Central Frosted Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(24.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF14141A).copy(alpha = 0.94f))
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.4f),
                                Color.White.copy(alpha = 0.08f),
                                Color.Black.copy(alpha = 0.6f)
                            )
                        ),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Album Artwork Frame with fallback
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .shadow(12.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1A1A22))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(48.dp)
                        )
                        if (imageUrl.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(imageUrl)
                                    .crossfade(false)
                                    .allowHardware(false)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Track Title
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Artist Row + Verified Badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.uploader,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = "Verified",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Acoustic Waveform Visualizer
                    AcousticWaveformVisualizer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                        barCount = 32
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Scrubber Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.52f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1:24", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color.White.copy(alpha = 0.6f))
                        Text("3:45", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }

            // Bottom Minimal Footer
            Text(
                text = "LISTEN ON VYLLO",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.5.sp,
                    fontSize = 10.sp
                ),
                color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

/**
 * 💎 TEMPLATE 2: "Vyllo Glass Canvas" (Editorial Soundwave Clean Edition)
 */
@Composable
private fun GlassCanvasStoryCard(
    item: MusicItem,
    imageUrl: String,
    context: Context
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Deep Ambient Blurred Backdrop
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .crossfade(false)
                    .allowHardware(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(65.dp)
                    .alpha(0.55f)
            )
        }

        // Dark Vignette
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.88f)
                        )
                    )
                )
        )

        // Main Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: Elegantly Written Brand
            Text(
                text = "VYLLO",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 6.sp,
                    fontSize = 15.sp
                ),
                color = Color.White,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Floating Frosted Glass Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(24.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Top-Left Compact Artwork
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .shadow(8.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1A1A22))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(28.dp)
                        )
                        if (imageUrl.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(imageUrl)
                                    .crossfade(false)
                                    .allowHardware(false)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Title
                    Text(
                        text = item.title.uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Artist
                    Text(
                        text = "BY ${item.uploader.uppercase()}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        ),
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Waveform
                    AcousticWaveformVisualizer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp),
                        barCount = 36
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress bar & Play Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("1:24", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color.White.copy(0.7f))
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.45f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("3:10", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color.White.copy(0.7f))
                    }
                }
            }

            // Bottom Signature
            Text(
                text = "VYLLO MUSIC",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontSize = 10.sp
                ),
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

/**
 * 🎵 Canvas Component: Aesthetic Acoustic Soundwave Spectrum
 */
@Composable
private fun AcousticWaveformVisualizer(
    modifier: Modifier = Modifier,
    barCount: Int = 32
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val maxHeight = size.height
        val totalSpacing = (barCount - 1) * 2f
        val barWidth = ((width - totalSpacing) / barCount).coerceAtLeast(1.5f)

        for (i in 0 until barCount) {
            val normalizedX = i.toFloat() / barCount.toFloat()
            val envelope = sin(normalizedX * Math.PI.toFloat()).pow(1.6f)
            val subHarmonic = 0.4f + 0.6f * sin(i * 1.4f).absoluteValue
            val barHeight = ((0.2f + 0.8f * envelope * subHarmonic) * maxHeight).coerceIn(3f, maxHeight)

            val x = i * (barWidth + 2f)
            val y = (maxHeight - barHeight) / 2f

            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f + 0.15f * envelope),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}



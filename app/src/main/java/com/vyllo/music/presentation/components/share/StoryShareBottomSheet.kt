package com.vyllo.music.presentation.components.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vyllo.music.core.utils.ShareIntentManager
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.presentation.utils.captureToPicture
import com.vyllo.music.presentation.utils.createBitmapFromPicture
import com.vyllo.music.presentation.utils.rememberPicture
import com.vyllo.music.presentation.utils.saveBitmapToFile
import kotlinx.coroutines.launch

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

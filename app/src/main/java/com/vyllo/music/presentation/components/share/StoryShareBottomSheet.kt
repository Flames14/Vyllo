package com.vyllo.music.presentation.components.share

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vyllo.music.R
import com.vyllo.music.core.utils.ShareIntentManager
import com.vyllo.music.domain.model.ArtworkCandidates
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.presentation.utils.StoryCardRenderer
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryShareBottomSheet(
    item: MusicItem,
    resolvedThumbnailUrl: String? = null,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // The card is rasterized once, then used for both the preview and the
    // share. The old implementation captured the Compose tree on tap, which is
    // why the exported image was frequently empty whenever Coil had not
    // finished decoding the artwork yet.
    var card by remember(item) { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember(item) { mutableStateOf(true) }

    LaunchedEffect(item, resolvedThumbnailUrl) {
        isGenerating = true
        try {
            val candidates = ArtworkCandidates.forItem(item, resolvedThumbnailUrl)
            val artwork = StoryCardRenderer.loadArtwork(context, candidates)
            card = StoryCardRenderer.renderCard(context, item, artwork)
        } finally {
            isGenerating = false
        }
    }

    fun shareCard(action: (File) -> Unit) {
        val current = card ?: return
        if (isGenerating) return
        isGenerating = true
        coroutineScope.launch {
            try {
                val file = StoryCardRenderer.saveCard(context, current)
                if (file == null) {
                    Toast.makeText(context, context.getString(R.string.share_failed), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                action(file)
            } finally {
                isGenerating = false
                onDismiss()
            }
        }
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
                        text = stringResource(R.string.share_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = stringResource(R.string.share_story_tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = Color.White
                    )
                }
            }

            // Exact 9:16 card preview. This is the same bitmap that gets shared.
            Box(
                modifier = Modifier
                    .width(260.dp)
                    .aspectRatio(9f / 16f)
                    .shadow(28.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
            ) {
                val current = card
                if (current != null) {
                    Image(
                        bitmap = current.asImageBitmap(),
                        contentDescription = stringResource(R.string.share_image_cd),
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF14141A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.share_preparing),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
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
                        shareCard { file -> ShareIntentManager.shareToInstagramStory(context, file) }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = card != null && !isGenerating,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE1306C)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            stringResource(R.string.share_ig),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // WhatsApp Status / Chat Button
                Button(
                    onClick = {
                        shareCard { file -> ShareIntentManager.shareToWhatsApp(context, file, item) }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = card != null && !isGenerating,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            stringResource(R.string.share_whatsapp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // Share Image Card to Any App
                IconButton(
                    onClick = {
                        shareCard { file ->
                            val shareUrl = item.getUniversalShareUrl()
                            ShareIntentManager.shareToAny(
                                context,
                                file,
                                "🎵 Listening to \"${item.title}\" by ${item.uploader} on Vyllo Music\n🔗 $shareUrl"
                            )
                        }
                    },
                    enabled = card != null && !isGenerating,
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF22222A), CircleShape)
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.share_image_cd), tint = Color.White)
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
                    Text(
                        stringResource(R.string.share_copy_link),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
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
                    Text(
                        stringResource(R.string.share_link),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

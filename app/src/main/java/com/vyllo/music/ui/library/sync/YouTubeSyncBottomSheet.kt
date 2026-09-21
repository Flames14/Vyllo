package com.vyllo.music.ui.library.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vyllo.music.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeSyncBottomSheet(
    viewModel: LibraryViewModel,
    onDismiss: () -> Unit
) {
    val isConnected by viewModel.isGoogleConnected.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val hasClientId by viewModel.hasGoogleClientId.collectAsState()
    val syncProgress = viewModel.syncProgressState

    var linkInput by remember { mutableStateOf("") }
    var isOAuthSectionExpanded by remember { mutableStateOf(isConnected) }
    var isClientIdConfigExpanded by remember { mutableStateOf(false) }
    var customClientIdInput by remember { mutableStateOf(viewModel.getGoogleClientId() ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF0000).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = Color(0xFFFF0000),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "YouTube Playlist Sync",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Import YouTube & YouTube Music Playlists",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SyncStatusBanner(
                syncProgress = syncProgress,
                onClear = { viewModel.clearSyncProgress() }
            )

            InstantImportCard(
                linkInput = linkInput,
                onLinkChange = { linkInput = it },
                onImportClick = {
                    if (linkInput.isNotBlank()) {
                        viewModel.importPlaylistByUrl(linkInput.trim())
                        linkInput = ""
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            GoogleOAuthSection(
                viewModel = viewModel,
                isConnected = isConnected,
                userEmail = userEmail,
                hasClientId = hasClientId,
                isExpanded = isOAuthSectionExpanded,
                onToggleExpand = { isOAuthSectionExpanded = !isOAuthSectionExpanded },
                isClientIdExpanded = isClientIdConfigExpanded,
                onClientIdExpandedChange = { isClientIdConfigExpanded = it },
                customClientIdInput = customClientIdInput,
                onCustomClientIdChange = { customClientIdInput = it }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

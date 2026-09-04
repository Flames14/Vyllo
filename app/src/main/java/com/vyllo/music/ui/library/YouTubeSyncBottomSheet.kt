package com.vyllo.music.ui.library

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vyllo.music.LibraryViewModel
import com.vyllo.music.data.network.YouTubeRemotePlaylist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeSyncBottomSheet(
    viewModel: LibraryViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
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

            // Real-Time Sync Status Banner
            if (syncProgress != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (syncProgress.isSuccess) Color(0xFF1B5E20).copy(alpha = 0.3f)
                        else if (syncProgress.errorMessage != null) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (syncProgress.isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else if (syncProgress.isSuccess) {
                                    Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(22.dp))
                                } else {
                                    Icon(Icons.Rounded.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (syncProgress.isSyncing) "Syncing: ${syncProgress.playlistTitle}..."
                                    else if (syncProgress.isSuccess) "Imported '${syncProgress.playlistTitle}' (${syncProgress.totalTracks} tracks)!"
                                    else "Sync error: ${syncProgress.errorMessage}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                            IconButton(onClick = { viewModel.clearSyncProgress() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // =================================================================
            // SECTION 1: INSTANT URL / ID IMPORT (RECOMMENDED - 100% RELIABLE)
            // =================================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bolt, null, tint = Color(0xFFFFB300), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Instant Playlist Import",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "No Login Needed",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Paste any YouTube or YouTube Music playlist link to import all tracks into your library immediately with artwork.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = linkInput,
                        onValueChange = { linkInput = it },
                        placeholder = { Text("https://music.youtube.com/playlist?list=PL...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            if (linkInput.isNotBlank()) {
                                IconButton(onClick = { linkInput = "" }) {
                                    Icon(Icons.Rounded.Close, null)
                                }
                            } else {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                    if (!clipText.isNullOrBlank()) {
                                        linkInput = clipText.trim()
                                    }
                                }) {
                                    Icon(Icons.Rounded.ContentPaste, "Paste Link")
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (linkInput.isNotBlank()) {
                                viewModel.importPlaylistByUrl(linkInput.trim())
                                linkInput = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = linkInput.isNotBlank()
                    ) {
                        Icon(Icons.Rounded.Download, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import Playlist Now", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =================================================================
            // SECTION 2: GOOGLE OAUTH 2.0 PKCE (FOR PRIVATE PLAYLISTS)
            // =================================================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isOAuthSectionExpanded = !isOAuthSectionExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Google Account Sync (OAuth 2.0)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            if (isOAuthSectionExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            null
                        )
                    }

                    AnimatedVisibility(visible = isOAuthSectionExpanded) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            if (!isConnected) {
                                Text(
                                    text = "Google OAuth connects directly to your Google Account to synchronize all your private and unlisted YouTube playlists.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                if (hasClientId) {
                                    Button(
                                        onClick = {
                                            val authUri = viewModel.getGoogleAuthUri()
                                            if (authUri != null) {
                                                val browserIntent = Intent(Intent.ACTION_VIEW, authUri)
                                                context.startActivity(browserIntent)
                                            } else {
                                                Toast.makeText(context, "Please configure your Google Client ID first", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(46.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Rounded.AccountCircle, null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Sign in with Google", fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    TextButton(
                                        onClick = { isClientIdConfigExpanded = !isClientIdConfigExpanded },
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    ) {
                                        Icon(Icons.Rounded.Settings, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Edit Google Client ID", style = MaterialTheme.typography.labelMedium)
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "Google Cloud OAuth Client ID Required",
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "To sign in via Google OAuth, enter your OAuth 2.0 Client ID from Google Cloud Console (Redirect URI: com.vyllo.music://oauth2redirect). Or use the instant import above without any setup!",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(0.8f)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = { isClientIdConfigExpanded = !isClientIdConfigExpanded },
                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Rounded.Key, null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Configure Client ID")
                                    }
                                }

                                AnimatedVisibility(visible = isClientIdConfigExpanded) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                                        OutlinedTextField(
                                            value = customClientIdInput,
                                            onValueChange = { customClientIdInput = it },
                                            label = { Text("Google Cloud Client ID") },
                                            placeholder = { Text("xxxxxx.apps.googleusercontent.com") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(onClick = {
                                                customClientIdInput = ""
                                                viewModel.setGoogleClientId(null)
                                                isClientIdConfigExpanded = false
                                            }) {
                                                Text("Reset")
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = {
                                                    viewModel.setGoogleClientId(customClientIdInput.trim())
                                                    isClientIdConfigExpanded = false
                                                    Toast.makeText(context, "Client ID saved!", Toast.LENGTH_SHORT).show()
                                                },
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text("Save")
                                            }
                                        }
                                    }
                                }
                            } else {
                                // CONNECTED STATE
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Connected to Google",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Text(
                                            text = userEmail ?: "YouTube API Read-Only Active",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                                        )
                                    }

                                    Row {
                                        IconButton(onClick = { viewModel.loadRemoteYouTubePlaylists() }) {
                                            Icon(Icons.Rounded.Refresh, "Refresh Playlists")
                                        }
                                        IconButton(onClick = { viewModel.disconnectGoogle() }) {
                                            Icon(Icons.AutoMirrored.Rounded.Logout, "Disconnect", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Remote Playlists
                                Text(
                                    text = "Your YouTube Playlists (${viewModel.remoteYouTubePlaylists.size})",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )

                                if (viewModel.isLoadingRemotePlaylists) {
                                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    }
                                } else if (viewModel.remoteYouTubePlaylists.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                                        Text("Tap refresh to load playlists", color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        viewModel.remoteYouTubePlaylists.forEach { playlist ->
                                            RemotePlaylistItemRow(
                                                playlist = playlist,
                                                onSyncClick = { viewModel.syncYouTubePlaylist(playlist) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RemotePlaylistItemRow(
    playlist: YouTubeRemotePlaylist,
    onSyncClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.DarkGray),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${playlist.trackCount} tracks • ${playlist.privacyStatus.replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                }
            }

            Button(
                onClick = onSyncClick,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Sync", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

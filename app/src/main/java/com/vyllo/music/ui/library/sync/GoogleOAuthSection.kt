package com.vyllo.music.ui.library.sync

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vyllo.music.LibraryViewModel

@Composable
fun GoogleOAuthSection(
    viewModel: LibraryViewModel,
    isConnected: Boolean,
    userEmail: String?,
    hasClientId: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    isClientIdExpanded: Boolean,
    onClientIdExpandedChange: (Boolean) -> Unit,
    customClientIdInput: String,
    onCustomClientIdChange: (String) -> Unit
) {
    val context = LocalContext.current
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
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, contentDescription = "Google Account Sync", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Google Account Sync (OAuth 2.0)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse Google Sync" else "Expand Google Sync"
                )
            }

            AnimatedVisibility(visible = isExpanded) {
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
                                onClick = { onClientIdExpandedChange(!isClientIdExpanded) },
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
                                onClick = { onClientIdExpandedChange(!isClientIdExpanded) },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Key, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Configure Client ID")
                            }
                        }

                        AnimatedVisibility(visible = isClientIdExpanded) {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                                OutlinedTextField(
                                    value = customClientIdInput,
                                    onValueChange = onCustomClientIdChange,
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
                                        onCustomClientIdChange("")
                                        viewModel.setGoogleClientId(null)
                                        onClientIdExpandedChange(false)
                                    }) {
                                        Text("Reset")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            viewModel.setGoogleClientId(customClientIdInput.trim())
                                            onClientIdExpandedChange(false)
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
}

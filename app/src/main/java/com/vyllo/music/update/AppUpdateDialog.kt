package com.vyllo.music.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vyllo.music.BuildConfig

@Composable
fun AppUpdateDialog(
    viewModel: AppUpdateViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.updateState.collectAsState()

    if (state == AppUpdateState.Idle) return

    AlertDialog(
        onDismissRequest = {
            if (state !is AppUpdateState.Downloading && state !is AppUpdateState.Checking) {
                viewModel.resetState()
                onDismiss()
            }
        },
        title = {
            Text(
                "App Update",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 400.dp)) {
                when (val currentState = state) {
                    is AppUpdateState.Checking -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Checking for updates...")
                        }
                    }
                    is AppUpdateState.UpdateAvailable -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            val currentVer = BuildConfig.VERSION_NAME
                            val newVer = currentState.release.tagName
                            Text(
                                "🚀 A new version is available!",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Current Version: $currentVer")
                            Text("New Version: $newVer")
                            Text("Release Date: ${currentState.release.publishedAt.substringBefore("T")}")
                            Spacer(Modifier.height(16.dp))
                            Text("What's new:", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    currentState.release.body,
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    is AppUpdateState.UpToDate -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "✅ You are on the latest version!",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    is AppUpdateState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "❌ Failed to check for updates.",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(currentState.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is AppUpdateState.Downloading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Downloading update in background...",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "You will be prompted to install once finished.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    AppUpdateState.Idle -> {}
                }
            }
        },
        confirmButton = {
            when (val currentState = state) {
                is AppUpdateState.UpdateAvailable -> {
                    Button(onClick = { viewModel.startDownload(currentState.apkUrl) }) {
                        Text("Update Now")
                    }
                }
                is AppUpdateState.UpToDate, is AppUpdateState.Error, is AppUpdateState.Downloading -> {
                    Button(onClick = {
                        viewModel.resetState()
                        onDismiss()
                    }) {
                        Text("OK")
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            if (state is AppUpdateState.UpdateAvailable) {
                TextButton(onClick = {
                    viewModel.resetState()
                    onDismiss()
                }) {
                    Text("Later")
                }
            }
        }
    )
}

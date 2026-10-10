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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vyllo.music.BuildConfig
import com.vyllo.music.R

@Composable
fun AppUpdateDialog(
    viewModel: AppUpdateViewModel,
    onDismiss: () -> Unit
) {
    val state by viewModel.updateState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.runtime.LaunchedEffect(state) {
        if (state is AppUpdateState.DownloadComplete) {
            val file = (state as AppUpdateState.DownloadComplete).file
            if (!ApkInstallValidator.ensureUnknownSourcesAllowed(context)) {
                com.vyllo.music.core.security.SecureLogger.w(
                    "AppUpdate", "Unknown-sources not allowed; sent user to settings"
                )
                viewModel.resetState()
                onDismiss()
                return@LaunchedEffect
            }
            val intent = ApkInstallValidator.buildInstallIntent(context, file)
            if (intent == null) {
                viewModel.resetState()
                onDismiss()
                return@LaunchedEffect
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                com.vyllo.music.core.security.SecureLogger.e("AppUpdate", "Failed to launch installer", e)
            }
            viewModel.resetState()
            onDismiss()
        }
    }

    if (state == AppUpdateState.Idle || state is AppUpdateState.DownloadComplete) return

    AlertDialog(
        onDismissRequest = {
            if (state !is AppUpdateState.Downloading && state !is AppUpdateState.Checking) {
                viewModel.resetState()
                onDismiss()
            }
        },
        title = {
            Text(
                stringResource(R.string.update_title),
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
                            Text(stringResource(R.string.update_checking))
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
                                stringResource(R.string.update_available),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.update_current_version, currentVer))
                            Text(stringResource(R.string.update_new_version, newVer))
                            Text(stringResource(R.string.update_release_date, currentState.release.publishedAt.substringBefore("T")))
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.update_whats_new), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
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
                                stringResource(R.string.update_up_to_date),
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
                                stringResource(R.string.update_failed),
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
                                stringResource(R.string.update_downloading),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                stringResource(R.string.update_download_hint),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    is AppUpdateState.DownloadComplete -> {}
                    AppUpdateState.Idle -> {}
                }
            }
        },
        confirmButton = {
            when (val currentState = state) {
                is AppUpdateState.UpdateAvailable -> {
                    Button(onClick = { viewModel.startDownload(currentState.apkUrl) }) {
                        Text(stringResource(R.string.update_now))
                    }
                }
                is AppUpdateState.UpToDate, is AppUpdateState.Error, is AppUpdateState.Downloading -> {
                    Button(onClick = {
                        viewModel.resetState()
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.common_ok))
                    }
                }
                is AppUpdateState.DownloadComplete -> {}
                else -> {}
            }
        },
        dismissButton = {
            if (state is AppUpdateState.UpdateAvailable) {
                TextButton(onClick = {
                    viewModel.resetState()
                    onDismiss()
                }) {
                    Text(stringResource(R.string.common_later))
                }
            }
        }
    )
}

package com.vyllo.music.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vyllo.music.R

@Composable
fun LastFmSettingsDialog(
    viewModel: LastFmViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val authState = viewModel.authState

    LaunchedEffect(authState) {
        val awaiting = authState as? LastFmAuthState.AwaitingAuthorization ?: return@LaunchedEffect
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(awaiting.url)))
        }.onFailure {
            uriHandler.openUri(awaiting.url)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lastfm_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.lastfm_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.lastfm_scrobbling),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.lastfm_scrobbling_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = viewModel.enabled,
                        onCheckedChange = { viewModel.updateEnabled(it) }
                    )
                }

                Spacer(Modifier.height(12.dp))

                val connected = authState is LastFmAuthState.Connected
                if (connected || viewModel.username.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.lastfm_connected_as, viewModel.username),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { viewModel.disconnect() }) {
                        Text(stringResource(R.string.lastfm_disconnect))
                    }
                } else {
                    OutlinedTextField(
                        value = viewModel.apiKey,
                        onValueChange = { viewModel.updateApiKey(it) },
                        label = { Text(stringResource(R.string.lastfm_api_key)) },
                        singleLine = true,
                        enabled = authState !is LastFmAuthState.Connecting &&
                            authState !is LastFmAuthState.AwaitingAuthorization,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = viewModel.apiSecret,
                        onValueChange = { viewModel.updateApiSecret(it) },
                        label = { Text(stringResource(R.string.lastfm_api_secret)) },
                        singleLine = true,
                        enabled = authState !is LastFmAuthState.Connecting &&
                            authState !is LastFmAuthState.AwaitingAuthorization,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.lastfm_credentials_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { uriHandler.openUri("https://www.last.fm/api/account/create") }) {
                        Text(stringResource(R.string.lastfm_create_account))
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { viewModel.connect() },
                        enabled = authState !is LastFmAuthState.Connecting &&
                            authState !is LastFmAuthState.AwaitingAuthorization
                    ) {
                        Text(stringResource(R.string.lastfm_connect))
                    }
                }

                when (authState) {
                    is LastFmAuthState.Connecting,
                    is LastFmAuthState.AwaitingAuthorization -> Text(
                        text = stringResource(R.string.lastfm_waiting_auth),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    is LastFmAuthState.Failed -> Text(
                        text = lastFmFailureMessage(authState.reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        titleContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun lastFmFailureMessage(reason: LastFmFailure): String = when (reason) {
    LastFmFailure.MISSING_CREDENTIALS -> stringResource(R.string.lastfm_error_missing_credentials)
    LastFmFailure.INVALID_CREDENTIALS -> stringResource(R.string.lastfm_error_invalid_credentials)
    LastFmFailure.NETWORK -> stringResource(R.string.lastfm_error_network)
    LastFmFailure.AUTHORIZATION_TIMEOUT -> stringResource(R.string.lastfm_error_timeout)
}

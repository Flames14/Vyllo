package com.vyllo.music.ui.library.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vyllo.music.data.repository.SyncProgress
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.vyllo.music.R

@Composable
fun SyncStatusBanner(
    syncProgress: SyncProgress?,
    onClear: () -> Unit
) {
    if (syncProgress == null) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (syncProgress.isSuccess) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
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
                        Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.cd_sync_succeeded), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    } else {
                        Icon(Icons.Rounded.Error, contentDescription = stringResource(R.string.cd_sync_failed), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (syncProgress.isSyncing) stringResource(R.string.sync_syncing, syncProgress.playlistTitle)
                        else if (syncProgress.isSuccess) pluralStringResource(R.plurals.sync_imported, syncProgress.totalTracks, syncProgress.playlistTitle, syncProgress.totalTracks)
                        else stringResource(R.string.sync_error, syncProgress.errorMessage.orEmpty()),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_dismiss), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

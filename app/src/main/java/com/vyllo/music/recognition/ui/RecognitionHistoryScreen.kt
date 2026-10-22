package com.vyllo.music.recognition.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vyllo.music.R
import com.vyllo.music.recognition.data.RecognitionHistoryEntry
import java.text.DateFormat
import java.util.Date

@Composable
fun RecognitionHistoryScreen(
    entries: List<RecognitionHistoryEntry>,
    onBack: () -> Unit,
    onPlay: (RecognitionHistoryEntry) -> Unit,
    onRemove: (RecognitionHistoryEntry) -> Unit,
    onClear: () -> Unit
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.recognition_history_clear_title)) },
            text = { Text(stringResource(R.string.recognition_history_clear_message)) },
            confirmButton = {
                TextButton(onClick = { onClear(); confirmClear = false }) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
                Text(
                    text = stringResource(R.string.recognition_history_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
            if (entries.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Text(stringResource(R.string.common_clear))
                }
            }
        }

        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.recognition_history_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 48.dp)
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries, key = { it.key }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onPlay(entry) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = entry.result.coverArtHqUrl ?: entry.result.coverArtUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp)
                            )
                            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(entry.result.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text(entry.result.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                Text(
                                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                        .format(Date(entry.recognizedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onRemove(entry) }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.recognition_history_remove)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

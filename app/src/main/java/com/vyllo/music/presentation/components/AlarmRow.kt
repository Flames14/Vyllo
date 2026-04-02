package com.vyllo.music.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vyllo.music.domain.model.AlarmModel
import com.vyllo.music.domain.model.SoundType

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Switch
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

/**
 * Individual alarm row component for the alarm list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlarmRow(
    alarm: AlarmModel,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var countdownText by remember { mutableStateOf("") }

    // Individual countdown logic
    if (alarm.isEnabled) {
        LaunchedEffect(alarm) {
            while (true) {
                val nextTime = alarm.calculateNextTriggerTime()
                val now = System.currentTimeMillis()
                val diff = nextTime - now

                if (diff > 0) {
                    val hours = diff / 3600000
                    val minutes = (diff % 3600000) / 60000
                    val seconds = (diff % 60000) / 1000
                    
                    countdownText = when {
                        hours > 0 -> "${hours}h ${minutes}m"
                        minutes > 0 -> "${minutes}m ${seconds}s"
                        else -> "${seconds}s"
                    }
                } else {
                    countdownText = "Ringing..."
                }
                delay(1000)
            }
        }
    } else {
        countdownText = ""
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Alarm") },
            text = { Text("Are you sure you want to delete this alarm?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { onEdit() },
                onLongClick = { showDeleteConfirm = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = if (alarm.isEnabled) 1f else 0.5f
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time and details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Time and Countdown Row
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = alarm.getDisplayTime(),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (alarm.isEnabled) 1f else 0.5f
                        ),
                        maxLines = 1
                    )
                    
                    if (countdownText.isNotBlank()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "in $countdownText",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary.copy(
                                alpha = if (alarm.isEnabled) 1f else 0.5f
                            ),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Label
                if (alarm.label.isNotBlank()) {
                    Text(
                        text = alarm.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (alarm.isEnabled) 0.8f else 0.4f
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Repeat days
                Text(
                    text = alarm.getRepeatDaysDisplay(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (alarm.isEnabled) 0.6f else 0.3f
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Sound info
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (alarm.isEnabled) 0.5f else 0.3f
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = alarm.downloadedSongTitle ?: "Default sound",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (alarm.isEnabled) 0.5f else 0.3f
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Toggle switch
            Switch(
                checked = alarm.isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

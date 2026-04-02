package com.vyllo.music.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Time picker dialog for setting alarm time.
 */
@Composable
fun AlarmTimePickerDialog(
    initialHour: Int = 7,
    initialMinute: Int = 0,
    onDismiss: () -> Unit,
    onTimeSelected: (Int, Int) -> Unit
) {
    var selectedHour by remember { mutableStateOf(initialHour) }
    var selectedMinute by remember { mutableStateOf(initialMinute) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Time") },
        text = {
            Column {
                // Hour picker
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Decrease hour
                    IconButton(onClick = {
                        selectedHour = if (selectedHour > 0) selectedHour - 1 else 23
                    }) {
                        Icon(Icons.Default.Remove, "Decrease hour")
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Hour display
                    Box(
                        modifier = Modifier.width(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedHour.toString().padStart(2, '0'),
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Increase hour
                    IconButton(onClick = {
                        selectedHour = if (selectedHour < 23) selectedHour + 1 else 0
                    }) {
                        Icon(Icons.Default.Add, "Increase hour")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Minute picker
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Decrease minute
                    IconButton(onClick = {
                        selectedMinute = if (selectedMinute > 0) selectedMinute - 1 else 59
                    }) {
                        Icon(Icons.Default.Remove, "Decrease minute")
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Minute display
                    Box(
                        modifier = Modifier.width(64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedMinute.toString().padStart(2, '0'),
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Increase minute
                    IconButton(onClick = {
                        selectedMinute = if (selectedMinute < 59) selectedMinute + 1 else 0
                    }) {
                        Icon(Icons.Default.Add, "Increase minute")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onTimeSelected(selectedHour, selectedMinute)
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

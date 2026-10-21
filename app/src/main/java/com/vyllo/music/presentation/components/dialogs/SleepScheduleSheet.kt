package com.vyllo.music.presentation.components.dialogs

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vyllo.music.R
import com.vyllo.music.domain.model.SleepSchedule
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * Recurring sleep schedule editor: which days, and what local time playback
 * should stop. Kept in its own bottom sheet so the settings dialog does not
 * grow another full-screen form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScheduleSheet(
    schedule: SleepSchedule,
    onSave: (SleepSchedule) -> Unit,
    onDismiss: () -> Unit
) {
    var enabled by remember(schedule) { mutableStateOf(schedule.enabled) }
    var days by remember(schedule) { mutableStateOf(schedule.days) }
    val timeState = rememberTimePickerState(
        initialHour = schedule.hour,
        initialMinute = schedule.minute,
        is24Hour = true
    )
    val locale = Locale.getDefault()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = stringResource(R.string.sleep_schedule_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.sleep_schedule_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.sleep_schedule_enabled),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                (1..7).forEach { isoDay ->
                    val selected = isoDay in days
                    val label = DayOfWeek.of(isoDay).getDisplayName(TextStyle.SHORT, locale)
                        .take(2)
                        .replaceFirstChar { it.uppercase(locale) }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                days = if (selected) days - isoDay else days + isoDay
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = timeState)
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
                TextButton(
                    onClick = {
                        onSave(
                            SleepSchedule(
                                enabled = enabled && days.isNotEmpty(),
                                days = days,
                                hour = timeState.hour,
                                minute = timeState.minute
                            )
                        )
                    }
                ) {
                    Text(
                        text = stringResource(R.string.common_save),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

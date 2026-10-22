package com.vyllo.music.presentation.components.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vyllo.music.R
import com.vyllo.music.domain.model.AutoEqImportState
import com.vyllo.music.domain.model.EqualizerSettings
import java.util.Locale

/**
 * Two-step AutoEQ wizard: paste a link/export, review the reduced five-band
 * curve, then apply. The review step exists because the device equalizer has
 * fewer bands than the source profile, so the user should see the compromise.
 */
@Composable
fun AutoEqImportDialog(
    state: AutoEqImportState,
    onImport: (source: String, name: String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    var source by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    val bandLabels = stringArrayResource(R.array.eq_band_labels)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.eq_autoeq_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.eq_autoeq_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!state.hasPreview) {
                    OutlinedTextField(
                        value = source,
                        onValueChange = { source = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.eq_autoeq_source)) },
                        minLines = 3,
                        maxLines = 8
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.eq_autoeq_name)) },
                        singleLine = true
                    )
                } else {
                    Text(
                        text = state.profileName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    state.bandLevels.forEachIndexed { index, level ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = bandLabels.getOrElse(index) {
                                    EqualizerSettings.DEFAULT_BANDS.getOrNull(index)?.label.orEmpty()
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = formatDb(level),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.eq_autoeq_approx),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.heightIn(max = 24.dp))
                }
                state.error?.let { error ->
                    Text(
                        text = stringResource(error.messageRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (state.hasPreview) onApply() else onImport(source, name)
                },
                enabled = !state.isLoading && (state.hasPreview || source.isNotBlank())
            ) {
                Text(
                    stringResource(
                        if (state.hasPreview) R.string.eq_autoeq_apply else R.string.eq_autoeq_import
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

private fun formatDb(millibels: Int): String {
    val db = millibels / 100f
    val prefix = if (db > 0) "+" else ""
    return "$prefix${String.format(Locale.US, "%.1f", db)} dB"
}

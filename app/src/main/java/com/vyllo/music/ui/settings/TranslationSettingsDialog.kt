package com.vyllo.music.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vyllo.music.R
import com.vyllo.music.domain.model.TranslationPrompt
import com.vyllo.music.domain.model.TranslationProvider

private val TARGET_LANGUAGES = listOf(
    "en", "es", "pt", "fr", "de", "it", "nl", "pl", "ru", "uk",
    "tr", "ar", "he", "hi", "bn", "id", "vi", "th", "ja", "ko", "zh"
)

@Composable
fun TranslationSettingsDialog(
    viewModel: TranslationViewModel,
    onSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            viewModel.discardChanges()
            onDismiss()
        },
        title = { Text(stringResource(R.string.translation_settings_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.translation_settings_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                SelectorRow(
                    label = stringResource(R.string.translation_provider),
                    value = stringResource(providerLabelRes(viewModel.provider)),
                    options = TranslationProvider.entries.map { it to stringResource(providerLabelRes(it)) },
                    onSelect = { viewModel.updateProvider(it) }
                )

                if (viewModel.provider.needsKey) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = viewModel.apiKey,
                        onValueChange = { viewModel.updateApiKey(it) },
                        label = { Text(stringResource(R.string.translation_api_key)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.translation_key_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (viewModel.apiKey.isNotBlank()) {
                        TextButton(onClick = { viewModel.clearKey() }) {
                            Text(stringResource(R.string.translation_clear_key))
                        }
                    }
                }

                if (viewModel.provider.supportsModel) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = viewModel.model,
                        onValueChange = { viewModel.updateModel(it) },
                        label = { Text(stringResource(R.string.translation_model)) },
                        placeholder = { Text(viewModel.provider.defaultModel) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(10.dp))
                SelectorRow(
                    label = stringResource(R.string.translation_language),
                    value = TranslationPrompt.languageName(viewModel.targetLanguage),
                    options = TARGET_LANGUAGES.map { it to TranslationPrompt.languageName(it) },
                    onSelect = { viewModel.updateTargetLanguage(it) }
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.translation_romanize),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.translation_romanize_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = viewModel.romanize,
                        onCheckedChange = { viewModel.updateRomanize(it) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.save()
                    onSaved()
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                viewModel.discardChanges()
                onDismiss()
            }) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        titleContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun <T> SelectorRow(
    label: String,
    value: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(value)
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (key, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            expanded = false
                            onSelect(key)
                        }
                    )
                }
            }
        }
    }
}

private fun providerLabelRes(provider: TranslationProvider): Int = when (provider) {
    TranslationProvider.AUTO -> R.string.translation_provider_auto
    TranslationProvider.DEEPL -> R.string.translation_provider_deepl
    TranslationProvider.OPENAI -> R.string.translation_provider_openai
    TranslationProvider.CLAUDE -> R.string.translation_provider_claude
    TranslationProvider.GEMINI -> R.string.translation_provider_gemini
    TranslationProvider.OPENROUTER -> R.string.translation_provider_openrouter
    TranslationProvider.PERPLEXITY -> R.string.translation_provider_perplexity
}

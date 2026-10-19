package com.vyllo.music.ui.components.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vyllo.music.R
import com.vyllo.music.core.utils.ByteSizeFormatter

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    isFloatingEnabled: Boolean,
    onFloatingEnabledChange: (Boolean) -> Unit,
    isBackgroundEnabled: Boolean,
    onBackgroundEnabledChange: (Boolean) -> Unit,
    isBatteryUnrestricted: Boolean,
    onBatteryAccessClick: () -> Unit,
    isKeepAudioPlayingEnabled: Boolean = false,
    onKeepAudioPlayingChange: (Boolean) -> Unit = {},
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    languageTag: String = "",
    onLanguageChange: (String) -> Unit = {},
    isHighRefreshRateEnabled: Boolean = true,
    onHighRefreshRateChange: (Boolean) -> Unit = {},
    isBlockScreenshotsEnabled: Boolean = false,
    onBlockScreenshotsChange: (Boolean) -> Unit = {},
    wifiOnlyDownloads: Boolean = false,
    onWifiOnlyDownloadsChange: (Boolean) -> Unit = {},
    downloadsRequireBatteryOk: Boolean = false,
    onDownloadsRequireBatteryOkChange: (Boolean) -> Unit = {},
    autoplayEnabled: Boolean = true,
    onAutoplayChange: (Boolean) -> Unit = {},
    isQueueSticky: Boolean = true,
    onQueueStickyChange: (Boolean) -> Unit = {},
    upNextIncludeVariants: Boolean = false,
    onUpNextIncludeVariantsChange: (Boolean) -> Unit = {},
    storageMediaBytes: Long = 0L,
    storageImageBytes: Long = 0L,
    storageTotalBytes: Long = 0L,
    isClearingCache: Boolean = false,
    onRefreshStorage: () -> Unit = {},
    onClearCacheClick: () -> Unit = {},
    onExportBackupClick: () -> Unit = {},
    onImportBackupClick: () -> Unit = {},
    onCheckUpdateClick: () -> Unit = {}
) {
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { onRefreshStorage() }

    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text(stringResource(R.string.settings_storage_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_storage_clear_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheConfirm = false
                        onClearCacheClick()
                    }
                ) {
                    Text(stringResource(R.string.settings_storage_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.settings_theme_mode),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val themes = listOf("System", "Light", "Dark", "Ocean", "Forest", "Sunset", "AMOLED", "Midnight Gold", "Cyberpunk", "Lavender", "Crimson")
                        themes.forEach { theme ->
                            val isSelected = theme == themeMode
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable { onThemeModeChange(theme) }
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = theme,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                )
                            }
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.settings_language),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val languages = listOf(
                            "" to R.string.settings_language_system,
                            "en" to R.string.settings_language_english,
                            "es" to R.string.settings_language_spanish
                        )
                        val selectedLanguage = languageTag.substringBefore('-').lowercase()
                        languages.forEach { (tag, labelRes) ->
                            val selected = tag == selectedLanguage
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable { onLanguageChange(tag) }
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }
                        }
                    }
                }

                // Background playback directly controls whether music survives the lock
                // screen and app backgrounding, so surface the battery exemption here.
                SettingsToggleRow(
                    title = stringResource(R.string.settings_background_playback),
                    subtitle = if (isBackgroundEnabled) stringResource(R.string.settings_bg_on) else stringResource(R.string.settings_bg_off),
                    checked = isBackgroundEnabled,
                    onCheckedChange = onBackgroundEnabledChange
                )

                SettingsToggleRow(
                    title = stringResource(R.string.settings_bg_battery_title),
                    subtitle = stringResource(R.string.settings_bg_battery_desc),
                    checked = isBatteryUnrestricted,
                    onCheckedChange = { onBatteryAccessClick() }
                )

                SettingsToggleRow(
                    title = stringResource(R.string.settings_keep_audio_playing),
                    subtitle = stringResource(R.string.settings_pause_on_mic),
                    checked = isKeepAudioPlayingEnabled,
                    onCheckedChange = onKeepAudioPlayingChange
                )

                SettingsToggleRow(
                    title = stringResource(R.string.settings_floating_player),
                    subtitle = stringResource(R.string.settings_show_bubble),
                    checked = isFloatingEnabled,
                    onCheckedChange = onFloatingEnabledChange
                )

                SettingsToggleRow(
                    title = stringResource(R.string.settings_high_refresh),
                    subtitle = if (isHighRefreshRateEnabled) stringResource(R.string.settings_refresh_on) else stringResource(R.string.settings_refresh_off),
                    checked = isHighRefreshRateEnabled,
                    onCheckedChange = onHighRefreshRateChange
                )

                SettingsToggleRow(
                    title = stringResource(R.string.settings_block_screenshots),
                    subtitle = stringResource(R.string.settings_block_screenshots_desc),
                    checked = isBlockScreenshotsEnabled,
                    onCheckedChange = onBlockScreenshotsChange
                )

                SettingsToggleRow(
                    title = stringResource(R.string.settings_wifi_only),
                    subtitle = stringResource(R.string.settings_wifi_only_desc),
                    checked = wifiOnlyDownloads,
                    onCheckedChange = onWifiOnlyDownloadsChange
                )

                SettingsToggleRow(
                    title = stringResource(R.string.settings_pause_low_battery),
                    subtitle = stringResource(R.string.settings_pause_low_battery_desc),
                    checked = downloadsRequireBatteryOk,
                    onCheckedChange = onDownloadsRequireBatteryOkChange
                )

                // Playback Behaviour Section -- these two prefs were previously
                // persisted and consumed by the player but had no UI at all, so
                // the setting existed in code and nowhere else.
                SettingsToggleRow(
                    title = stringResource(R.string.settings_autoplay),
                    subtitle = stringResource(R.string.settings_autoplay_desc),
                    checked = autoplayEnabled,
                    onCheckedChange = onAutoplayChange
                )

                SettingsToggleRow(
                    title = stringResource(R.string.settings_sticky_queue),
                    subtitle = stringResource(R.string.settings_sticky_queue_desc),
                    checked = isQueueSticky,
                    onCheckedChange = onQueueStickyChange
                )

                SettingsToggleRow(
                    title = stringResource(R.string.settings_upnext_variants),
                    subtitle = stringResource(R.string.settings_upnext_variants_desc),
                    checked = upNextIncludeVariants,
                    onCheckedChange = onUpNextIncludeVariantsChange
                )

                // Storage Section -- audio/image caches are the two things that
                // actually grow on disk, so expose both sizes and a safe clear.
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.settings_storage_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_storage_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsValueRow(
                        title = stringResource(R.string.settings_storage_media),
                        value = ByteSizeFormatter.format(storageMediaBytes)
                    )
                    SettingsValueRow(
                        title = stringResource(R.string.settings_storage_images),
                        value = ByteSizeFormatter.format(storageImageBytes)
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SettingsValueRow(
                        title = stringResource(R.string.settings_storage_total),
                        value = ByteSizeFormatter.format(storageTotalBytes),
                        emphasize = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isClearingCache) { showClearCacheConfirm = true }
                    ) {
                        Text(
                            text = if (isClearingCache) {
                                stringResource(R.string.settings_storage_clearing)
                            } else {
                                stringResource(R.string.settings_storage_clear)
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // Backup & Restore Section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.backup_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onExportBackupClick() }
                        ) {
                            Text(
                                stringResource(R.string.backup_export),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onImportBackupClick() }
                        ) {
                            Text(
                                stringResource(R.string.backup_import),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                SettingsActionRow(
                    title = stringResource(R.string.settings_support),
                    subtitle = stringResource(R.string.settings_buy_coffee),
                    icon = Icons.Rounded.Favorite,
                    onClick = { uriHandler.openUri("https://ko-fi.com/betadeveloper") },
                    iconContentDescription = stringResource(R.string.settings_donate_cd)
                )

                SettingsActionRow(
                    title = stringResource(R.string.settings_check_updates),
                    subtitle = stringResource(R.string.settings_check_updates_desc),
                    icon = Icons.Rounded.Update,
                    onClick = { onCheckUpdateClick() },
                    iconContentDescription = stringResource(R.string.settings_update_cd)
                )
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
private fun SettingsValueRow(
    title: String,
    value: String,
    emphasize: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal
            )
        )
    }
}

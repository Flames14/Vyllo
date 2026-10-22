package com.vyllo.music.ui.components.dialogs

import androidx.compose.foundation.clickable
import android.os.Build
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.vyllo.music.domain.model.SleepSchedule

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
    crossfadeSeconds: Int = 0,
    onCrossfadeSecondsChange: (Int) -> Unit = {},
    loudnessPreset: String = "Off",
    onLoudnessPresetChange: (String) -> Unit = {},
    streamQuality: String = "Auto",
    onStreamQualityChange: (String) -> Unit = {},
    downloadQuality: String = "High",
    onDownloadQualityChange: (String) -> Unit = {},
    sleepSchedule: SleepSchedule = SleepSchedule(),
    onSleepScheduleChange: (SleepSchedule) -> Unit = {},
    skipSilence: Boolean = false,
    onSkipSilenceChange: (Boolean) -> Unit = {},
    pauseOnDeviceDisconnect: Boolean = true,
    onPauseOnDeviceDisconnectChange: (Boolean) -> Unit = {},
    resumeOnDeviceReconnect: Boolean = false,
    onResumeOnDeviceReconnectChange: (Boolean) -> Unit = {},
    pauseWhenMuted: Boolean = false,
    onPauseWhenMutedChange: (Boolean) -> Unit = {},
    audioOffloadEnabled: Boolean = false,
    onAudioOffloadChange: (Boolean) -> Unit = {},
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
    var showCrossfadePicker by remember { mutableStateOf(false) }
    var showLoudnessPicker by remember { mutableStateOf(false) }
    var showQualityPicker by remember { mutableStateOf(false) }
    var showDownloadQualityPicker by remember { mutableStateOf(false) }
    var showSleepScheduleSheet by remember { mutableStateOf(false) }

    if (showSleepScheduleSheet) {
        com.vyllo.music.presentation.components.dialogs.SleepScheduleSheet(
            schedule = sleepSchedule,
            onSave = {
                onSleepScheduleChange(it)
                showSleepScheduleSheet = false
            },
            onDismiss = { showSleepScheduleSheet = false }
        )
    }

    if (showQualityPicker) {
        val qualities = listOf("Auto", "High", "Medium", "Low")
        AlertDialog(
            onDismissRequest = { showQualityPicker = false },
            title = { Text(stringResource(R.string.quality_dialog_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_stream_quality_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    qualities.forEach { quality ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onStreamQualityChange(quality)
                                    showQualityPicker = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = qualityLabel(quality),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (streamQuality == quality) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                            if (streamQuality == quality) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityPicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    }

    if (showDownloadQualityPicker) {
        val qualities = listOf("Auto", "High", "Medium", "Low")
        AlertDialog(
            onDismissRequest = { showDownloadQualityPicker = false },
            title = { Text(stringResource(R.string.download_quality_dialog_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_download_quality_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    qualities.forEach { quality ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDownloadQualityChange(quality)
                                    showDownloadQualityPicker = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = qualityLabel(quality),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (downloadQuality == quality) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                            if (downloadQuality == quality) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloadQualityPicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    }

    if (showLoudnessPicker) {
        val presets = listOf("Off", "Quiet", "Balanced", "Loud", "Aggressive")
        AlertDialog(
            onDismissRequest = { showLoudnessPicker = false },
            title = { Text(stringResource(R.string.loudness_dialog_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_loudness_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    presets.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLoudnessPresetChange(preset)
                                    showLoudnessPicker = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = loudnessPresetLabel(preset),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (loudnessPreset == preset) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                            if (loudnessPreset == preset) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLoudnessPicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    }

    if (showCrossfadePicker) {
        val options = listOf(0, 2, 3, 5, 8)
        AlertDialog(
            onDismissRequest = { showCrossfadePicker = false },
            title = { Text(stringResource(R.string.crossfade_dialog_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.settings_crossfade_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    options.forEach { seconds ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCrossfadeSecondsChange(seconds)
                                    showCrossfadePicker = false
                                }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (seconds == 0) {
                                    stringResource(R.string.crossfade_off)
                                } else {
                                    stringResource(R.string.crossfade_seconds, seconds)
                                },
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (crossfadeSeconds == seconds) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                            if (crossfadeSeconds == seconds) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCrossfadePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    }
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
                        val themes = buildList {
                            add("System")
                            add("Light")
                            add("Dark")
                            // Material You needs Android 12+; hide rather than
                            // offer a chip that silently does nothing.
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add("Dynamic")
                            addAll(
                                listOf(
                                    "Ocean", "Forest", "Sunset", "AMOLED",
                                    "Midnight Gold", "Cyberpunk", "Lavender", "Crimson"
                                )
                            )
                        }
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

                // Scheduled sleep -- pause playback automatically at a fixed
                // local time on the chosen weekdays.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSleepScheduleSheet = true }
                        .padding(vertical = 4.dp)
                ) {
                    SettingsValueRow(
                        title = stringResource(R.string.settings_sleep_schedule),
                        value = sleepScheduleSummary(sleepSchedule),
                        emphasize = true
                    )
                }

                // Skip silent passages, and how playback reacts to device and
                // output-state changes.
                SettingsToggleRow(
                    title = stringResource(R.string.settings_skip_silence),
                    subtitle = stringResource(R.string.settings_skip_silence_desc),
                    checked = skipSilence,
                    onCheckedChange = onSkipSilenceChange
                )

                SettingsToggleRow(
                    title = stringResource(R.string.settings_pause_on_disconnect),
                    subtitle = stringResource(R.string.settings_pause_on_disconnect_desc),
                    checked = pauseOnDeviceDisconnect,
                    onCheckedChange = onPauseOnDeviceDisconnectChange
                )

                SettingsToggleRow(
                    title = stringResource(R.string.settings_resume_on_reconnect),
                    subtitle = stringResource(R.string.settings_resume_on_reconnect_desc),
                    checked = resumeOnDeviceReconnect,
                    onCheckedChange = onResumeOnDeviceReconnectChange
                )

                SettingsToggleRow(
                    title = stringResource(R.string.settings_pause_when_muted),
                    subtitle = stringResource(R.string.settings_pause_when_muted_desc),
                    checked = pauseWhenMuted,
                    onCheckedChange = onPauseWhenMutedChange
                )

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_audio_offload),
                        subtitle = stringResource(R.string.settings_audio_offload_desc),
                        checked = audioOffloadEnabled,
                        onCheckedChange = onAudioOffloadChange
                    )
                }

                // Stream quality -- caps the audio bitrate we pick from the
                // extractor's stream list for every newly resolved track.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showQualityPicker = true }
                        .padding(vertical = 4.dp)
                ) {
                    SettingsValueRow(
                        title = stringResource(R.string.settings_stream_quality),
                        value = qualityLabel(streamQuality),
                        emphasize = true
                    )
                    Text(
                        text = stringResource(R.string.settings_stream_quality_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Download quality -- the ceiling used when fetching the file
                // itself, kept separate from the streaming preference because a
                // download outlives the connection it was made on.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDownloadQualityPicker = true }
                        .padding(vertical = 4.dp)
                ) {
                    SettingsValueRow(
                        title = stringResource(R.string.settings_download_quality),
                        value = qualityLabel(downloadQuality),
                        emphasize = true
                    )
                    Text(
                        text = stringResource(R.string.settings_download_quality_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Loudness normalization -- real per-block RMS gain in the audio
                // sink chain, not the volume booster's static multiplier.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLoudnessPicker = true }
                        .padding(vertical = 4.dp)
                ) {
                    SettingsValueRow(
                        title = stringResource(R.string.settings_loudness),
                        value = loudnessPresetLabel(loudnessPreset),
                        emphasize = true
                    )
                    Text(
                        text = stringResource(R.string.settings_loudness_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Crossfade -- 0s is a real option (and the default): it leaves
                // ExoPlayer's codec-level gapless transitions untouched.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCrossfadePicker = true }
                        .padding(vertical = 4.dp)
                ) {
                    SettingsValueRow(
                        title = stringResource(R.string.settings_crossfade),
                        value = if (crossfadeSeconds <= 0) {
                            stringResource(R.string.crossfade_off)
                        } else {
                            stringResource(R.string.crossfade_seconds, crossfadeSeconds)
                        },
                        emphasize = true
                    )
                    Text(
                        text = stringResource(R.string.settings_crossfade_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

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
private fun sleepScheduleSummary(schedule: SleepSchedule): String {
    if (!schedule.enabled || schedule.days.isEmpty()) {
        return stringResource(R.string.loudness_off)
    }
    val locale = java.util.Locale.getDefault()
    val days = schedule.days.sorted().mapNotNull { day ->
        runCatching {
            java.time.DayOfWeek.of(day)
                .getDisplayName(java.time.format.TextStyle.SHORT, locale)
        }.getOrNull()
    }
    val time = String.format(locale, "%02d:%02d", schedule.hour, schedule.minute)
    return (days + time).joinToString(" ")
}

@Composable
private fun qualityLabel(quality: String): String = when (quality) {
    "High" -> stringResource(R.string.quality_high)
    "Medium" -> stringResource(R.string.quality_medium)
    "Low" -> stringResource(R.string.quality_low)
    else -> stringResource(R.string.quality_auto)
}

@Composable
private fun loudnessPresetLabel(preset: String): String = when (preset) {
    "Aggressive" -> stringResource(R.string.loudness_aggressive)
    "Loud" -> stringResource(R.string.loudness_loud)
    "Balanced" -> stringResource(R.string.loudness_balanced)
    "Quiet" -> stringResource(R.string.loudness_quiet)
    else -> stringResource(R.string.loudness_off)
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

package com.vyllo.music.ui.components

import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vyllo.music.domain.model.MusicItem

@Deprecated("Moved to ui.components.background", ReplaceWith("com.vyllo.music.ui.components.background.MeshGradientBackground(modifier)", "com.vyllo.music.ui.components.background.MeshGradientBackground"))
@Composable
fun MeshGradientBackground(modifier: Modifier = Modifier) {
    com.vyllo.music.ui.components.background.MeshGradientBackground(modifier)
}

@Deprecated("Moved to ui.components.background", ReplaceWith("com.vyllo.music.ui.components.background.AuroraShader(modifier)"))
@RequiresApi(33)
@Composable
fun AuroraShader(modifier: Modifier) {
    com.vyllo.music.ui.components.background.AuroraShader(modifier)
}

@Deprecated("Moved to ui.components.background", ReplaceWith("com.vyllo.music.ui.components.background.FallbackMeshGradient(modifier)"))
@Composable
fun FallbackMeshGradient(modifier: Modifier) {
    com.vyllo.music.ui.components.background.FallbackMeshGradient(modifier)
}

@Deprecated("Moved to ui.components.cards", ReplaceWith("com.vyllo.music.ui.components.cards.MagazineHeader()"))
@Composable
fun MagazineHeader() {
    com.vyllo.music.ui.components.cards.MagazineHeader()
}

@Deprecated("Moved to ui.components.cards", ReplaceWith("com.vyllo.music.ui.components.cards.HeroRecommendationCard(item, isPlaying, onClick)"))
@Composable
fun HeroRecommendationCard(item: MusicItem, isPlaying: Boolean, onClick: () -> Unit) {
    com.vyllo.music.ui.components.cards.HeroRecommendationCard(item, isPlaying, onClick)
}

@Deprecated("Moved to ui.components.rows", ReplaceWith("com.vyllo.music.ui.components.rows.PremiumSuggestionRow(text, onClick, onInsert)"))
@Composable
fun PremiumSuggestionRow(
    text: String,
    onClick: () -> Unit,
    onInsert: (() -> Unit)? = null
) {
    com.vyllo.music.ui.components.rows.PremiumSuggestionRow(text, onClick, onInsert)
}

@Deprecated("Moved to ui.components.rows", ReplaceWith("com.vyllo.music.ui.components.rows.HistorySuggestionRow(text, onClick, onInsert)"))
@Composable
fun HistorySuggestionRow(
    text: String,
    onClick: () -> Unit,
    onInsert: (() -> Unit)? = null
) {
    com.vyllo.music.ui.components.rows.HistorySuggestionRow(text, onClick, onInsert)
}

@Deprecated("Moved to ui.components.dialogs", ReplaceWith("com.vyllo.music.ui.components.dialogs.SettingsDialog(...)"))
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
    isHighRefreshRateEnabled: Boolean = true,
    onHighRefreshRateChange: (Boolean) -> Unit = {},
    onExportBackupClick: () -> Unit = {},
    onImportBackupClick: () -> Unit = {},
    onCheckUpdateClick: () -> Unit = {}
) {
    com.vyllo.music.ui.components.dialogs.SettingsDialog(
        onDismiss = onDismiss,
        isFloatingEnabled = isFloatingEnabled,
        onFloatingEnabledChange = onFloatingEnabledChange,
        isBackgroundEnabled = isBackgroundEnabled,
        onBackgroundEnabledChange = onBackgroundEnabledChange,
        isBatteryUnrestricted = isBatteryUnrestricted,
        onBatteryAccessClick = onBatteryAccessClick,
        isKeepAudioPlayingEnabled = isKeepAudioPlayingEnabled,
        onKeepAudioPlayingChange = onKeepAudioPlayingChange,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        isHighRefreshRateEnabled = isHighRefreshRateEnabled,
        onHighRefreshRateChange = onHighRefreshRateChange,
        onExportBackupClick = onExportBackupClick,
        onImportBackupClick = onImportBackupClick,
        onCheckUpdateClick = onCheckUpdateClick
    )
}

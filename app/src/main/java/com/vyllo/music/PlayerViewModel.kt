package com.vyllo.music

import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.LyricsResponse
import com.vyllo.music.domain.model.SyncedLyricLine
import com.vyllo.music.domain.model.LyricsResult
import com.vyllo.music.domain.model.LyricsStatus
import com.vyllo.music.domain.model.EqualizerSettings
import com.vyllo.music.data.*
import com.vyllo.music.data.manager.PlaybackQueueManager
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.domain.manager.StreamUrlCache
import com.vyllo.music.domain.usecase.GetStreamUrlUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import javax.inject.Inject

data class PlayerUiState(
    val currentPlayingItem: MusicItem? = null,
    val relatedSongs: List<MusicItem> = emptyList(),
    val autoplayEnabled: Boolean = true,
    val isLoadingPlayer: Boolean = false,
    val loadingItemUrl: String? = null,
    val isVideoMode: Boolean = false,
    val lyricsResponse: LyricsResponse? = null,
    val syncedLyricsLines: List<SyncedLyricLine> = emptyList(),
    val currentLyricIndex: Int = -1,
    val lyricsLoading: Boolean = false,
    val lyricsOffsetMs: Long = 0L,
    val showLyricsSelector: Boolean = false,
    val lyricsSearchQuery: String = "",
    val lyricsSearchResults: List<LyricsResult> = emptyList(),
    val lyricsSearching: Boolean = false,
    val translatedLyricsLines: List<String?> = emptyList(),
    val translatedPlainLyrics: String? = null,
    val isTranslationEnabled: Boolean = false,
    val isTranslating: Boolean = false,
    val detectedLyricsLangCode: String? = null,
    val equalizerSettings: EqualizerSettings = EqualizerSettings(),
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: IMusicRepository,
    private val getStreamUrlUseCase: GetStreamUrlUseCase,
    private val playbackQueueManager: PlaybackQueueManager,
    private val preferenceManager: PreferenceManager,
    private val streamUrlCache: StreamUrlCache,
    private val lyricsCoordinator: PlayerLyricsCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    // Convenience properties for backward compatibility with Compose UI
    val currentPlayingItem: MusicItem?
        get() = _uiState.value.currentPlayingItem
    val relatedSongs: List<MusicItem>
        get() = _uiState.value.relatedSongs
    var autoplayEnabled: Boolean
        get() = _uiState.value.autoplayEnabled
        set(value) { _uiState.update { it.copy(autoplayEnabled = value) } }
    val isLoadingPlayer: Boolean
        get() = _uiState.value.isLoadingPlayer
    val loadingItemUrl: String?
        get() = _uiState.value.loadingItemUrl
    var isVideoMode: Boolean
        get() = _uiState.value.isVideoMode
        set(value) { _uiState.update { it.copy(isVideoMode = value) } }
    val lyricsResponse: LyricsResponse?
        get() = _uiState.value.lyricsResponse
    val syncedLyricsLines: List<SyncedLyricLine>
        get() = _uiState.value.syncedLyricsLines
    var currentLyricIndex: Int
        get() = _uiState.value.currentLyricIndex
        set(value) { _uiState.update { it.copy(currentLyricIndex = value) } }
    val lyricsLoading: Boolean
        get() = _uiState.value.lyricsLoading
    var lyricsOffsetMs: Long
        get() = _uiState.value.lyricsOffsetMs
        set(value) { _uiState.update { it.copy(lyricsOffsetMs = value) } }
    var showLyricsSelector: Boolean
        get() = _uiState.value.showLyricsSelector
        set(value) { _uiState.update { it.copy(showLyricsSelector = value) } }
    var lyricsSearchQuery: String
        get() = _uiState.value.lyricsSearchQuery
        set(value) { _uiState.update { it.copy(lyricsSearchQuery = value) } }
    val lyricsSearchResults: List<LyricsResult>
        get() = _uiState.value.lyricsSearchResults
    val lyricsSearching: Boolean
        get() = _uiState.value.lyricsSearching
    val translatedLyricsLines: List<String?>
        get() = _uiState.value.translatedLyricsLines
    val translatedPlainLyrics: String?
        get() = _uiState.value.translatedPlainLyrics
    var isTranslationEnabled: Boolean
        get() = _uiState.value.isTranslationEnabled
        set(value) { _uiState.update { it.copy(isTranslationEnabled = value) } }
    val isTranslating: Boolean
        get() = _uiState.value.isTranslating
    val detectedLyricsLangCode: String?
        get() = _uiState.value.detectedLyricsLangCode
    val equalizerSettings: EqualizerSettings
        get() = _uiState.value.equalizerSettings

    init {
        _uiState.update { it.copy(equalizerSettings = preferenceManager.loadEqualizerSettings()) }
        viewModelScope.launch {
            playbackQueueManager.currentPlayingItem.collectLatest { item ->
                _uiState.update { it.copy(currentPlayingItem = item) }
            }
        }
    }

    fun setPlaybackLoading(isLoading: Boolean, itemUrl: String? = null) {
        _uiState.update { it.copy(isLoadingPlayer = isLoading, loadingItemUrl = itemUrl) }
    }

    fun loadRelatedSongs(item: MusicItem) {
        viewModelScope.launch {
            val related = repository.getRelatedSongs(item.url)
            if (related.isNotEmpty()) {
                _uiState.update { it.copy(relatedSongs = related) }
                syncQueueWithRelated(related)
            }
        }
    }

    private fun syncQueueWithRelated(related: List<MusicItem>) {
        val currentIdx = playbackQueueManager.currentIndex
        if (currentIdx >= 0) {
            playbackQueueManager.replaceUpcomingItems(related)
        }
    }

    suspend fun resolveStream(item: MusicItem, isVideo: Boolean = false): String? {
        streamUrlCache.get(item.url, isVideo)?.let {
            SecureLogger.d("PlayerViewModel") { "Stream URL cache hit: ${item.url}_$isVideo" }
            return it
        }

        _uiState.update { it.copy(loadingItemUrl = item.url, isLoadingPlayer = true) }
        SecureLogger.d("PlayerViewModel") { "Resolving stream: url=${item.url}, isVideo=$isVideo" }
        val url = getStreamUrlUseCase(item.url, isVideo = isVideo)
        _uiState.update { it.copy(loadingItemUrl = null, isLoadingPlayer = false) }

        if (url != null) {
            streamUrlCache.put(item.url, isVideo, url)
            SecureLogger.d("PlayerViewModel") { "Stream URL resolved" }
        } else {
            SecureLogger.w("PlayerViewModel", "Failed to resolve stream URL")
        }
        return url
    }

    fun toggleVideoMode(currentPosition: Long, onSwitch: (String?) -> Unit) {
        val item = currentPlayingItem ?: return
        val targetVideoMode = !isVideoMode
        SecureLogger.d("PlayerViewModel") { "toggleVideoMode: current=$isVideoMode, target=$targetVideoMode, position=$currentPosition" }

        isVideoMode = targetVideoMode

        viewModelScope.launch {
            val newUrl = resolveStream(item, isVideo = isVideoMode)
            if (isActive) {
                SecureLogger.d("PlayerViewModel") { "toggleVideoMode callback: newUrl resolved" }
                onSwitch(newUrl)
            }
        }
    }

    fun fetchLyrics(item: MusicItem, durationSecs: Long) {
        lyricsCoordinator.fetchLyrics(
            scope = viewModelScope,
            currentState = { _uiState.value },
            updateState = { _uiState.value = it },
            item = item,
            durationSecs = durationSecs,
            onTranslateCurrentLyrics = ::translateCurrentLyrics
        )
    }

    fun updateLyricsPosition(positionMs: Long) {
        val index = LyricsEngine.getCurrentLyricLine(syncedLyricsLines, positionMs + lyricsOffsetMs)
        if (index != currentLyricIndex) {
            _uiState.update { it.copy(currentLyricIndex = index) }
        }
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        _uiState.update { it.copy(lyricsOffsetMs = it.lyricsOffsetMs + deltaMs) }
    }

    fun selectAlternativeLyrics(result: LyricsResult) {
        lyricsCoordinator.selectAlternativeLyrics(
            currentState = { _uiState.value },
            updateState = { _uiState.value = it },
            result = result
        )
    }

    fun searchForLyrics(query: String) {
        lyricsCoordinator.searchForLyrics(
            scope = viewModelScope,
            currentState = { _uiState.value },
            updateState = { _uiState.value = it },
            query = query
        )
    }

    fun clearLyricsSearchResults() {
        lyricsCoordinator.clearLyricsSearchResults(
            currentState = { _uiState.value },
            updateState = { _uiState.value = it }
        )
    }

    fun translateCurrentLyrics() {
        lyricsCoordinator.translateCurrentLyrics(
            scope = viewModelScope,
            currentState = { _uiState.value },
            updateState = { _uiState.value = it }
        )
    }

    fun toggleTranslation(enabled: Boolean) {
        isTranslationEnabled = enabled
        if (enabled && translatedLyricsLines.isEmpty() && translatedPlainLyrics == null) {
            translateCurrentLyrics()
        }
    }

    fun getNextAutoplayItem(): MusicItem? {
        if (!autoplayEnabled) return null
        return relatedSongs.firstOrNull()
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        updateEqualizerSettings(equalizerSettings.copy(enabled = enabled))
    }

    fun updateBassBoost(strength: Int) {
        updateEqualizerSettings(
            equalizerSettings.copy(
                bassBoostStrength = strength.coerceIn(EqualizerSettings.STRENGTH_MIN, EqualizerSettings.STRENGTH_MAX)
            )
        )
    }

    fun updateVirtualizer(strength: Int) {
        updateEqualizerSettings(
            equalizerSettings.copy(
                virtualizerStrength = strength.coerceIn(EqualizerSettings.STRENGTH_MIN, EqualizerSettings.STRENGTH_MAX)
            )
        )
    }

    fun updateEqualizerBand(index: Int, level: Int) {
        if (index !in equalizerSettings.bands.indices) return

        val updatedBands = equalizerSettings.bands.mapIndexed { bandIndex, band ->
            if (bandIndex == index) {
                band.copy(level = level.coerceIn(EqualizerSettings.BAND_LEVEL_MIN, EqualizerSettings.BAND_LEVEL_MAX))
            } else {
                band
            }
        }
        updateEqualizerSettings(equalizerSettings.copy(bands = updatedBands))
    }

    fun resetEqualizer() {
        updateEqualizerSettings(EqualizerSettings())
    }

    private fun updateEqualizerSettings(settings: EqualizerSettings) {
        val sanitized = settings.sanitized()
        _uiState.update { it.copy(equalizerSettings = sanitized) }
        preferenceManager.saveEqualizerSettings(sanitized)
    }
}

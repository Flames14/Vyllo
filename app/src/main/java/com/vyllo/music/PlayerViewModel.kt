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
import com.vyllo.music.domain.model.EqualizerPreset
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

@androidx.compose.runtime.Immutable
data class PlayerUiState(
    val currentPlayingItem: MusicItem? = null,
    val resolvedThumbnailUrl: String? = null,
    val relatedSongs: List<MusicItem> = emptyList(),
    val artistSongs: List<MusicItem> = emptyList(),
    val discoverSimilarSongs: List<MusicItem> = emptyList(),
    val isLoadingRelatedTab: Boolean = false,
    val isLoadingMoreRelated: Boolean = false,
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
    val volumeBoostMultiplier: Float = 1.0f,
    val isVolumeBoosterUIVisible: Boolean = false,
    val isInPipMode: Boolean = false,
    val isFullScreenVideo: Boolean = false,
    val likeCount: Long = -1L,
    val commentCount: Long = -1L,
    val likeCountFormatted: String? = null,
    val commentCountFormatted: String? = null,
    val isQueueSticky: Boolean = true,
    val sleepTimerRemainingSeconds: Long? = null,
    val isSleepTimerActive: Boolean = false
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: IMusicRepository,
    private val getStreamUrlUseCase: GetStreamUrlUseCase,
    private val playbackQueueManager: PlaybackQueueManager,
    private val preferenceManager: PreferenceManager,
    private val streamUrlCache: StreamUrlCache,
    private val lyricsCoordinator: PlayerLyricsCoordinator,
    private val thumbnailResolver: com.vyllo.music.data.network.YouTubeThumbnailResolver
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    private var volumeBoosterJob: kotlinx.coroutines.Job? = null

    /** Anchor track that the current related-songs pool was built for. */
    private var relatedAnchorUrl: String? = null

    // Convenience properties for backward compatibility with Compose UI
    val currentPlayingItem: MusicItem?
        get() = _uiState.value.currentPlayingItem
    val relatedSongs: List<MusicItem>
        get() = _uiState.value.relatedSongs
    var isQueueSticky: Boolean
        get() = _uiState.value.isQueueSticky
        set(value) {
            preferenceManager.isQueueSticky = value
            _uiState.update { it.copy(isQueueSticky = value) }
        }
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
    val volumeBoostMultiplier: Float
        get() = _uiState.value.volumeBoostMultiplier
    val isVolumeBoosterUIVisible: Boolean
        get() = _uiState.value.isVolumeBoosterUIVisible
    val isInPipMode: Boolean
        get() = _uiState.value.isInPipMode
    val isFullScreenVideo: Boolean
        get() = _uiState.value.isFullScreenVideo
    val likeCountFormatted: String?
        get() = _uiState.value.likeCountFormatted
    val commentCountFormatted: String?
        get() = _uiState.value.commentCountFormatted

    init {
        _uiState.update { it.copy(
            equalizerSettings = preferenceManager.loadEqualizerSettings(),
            volumeBoostMultiplier = preferenceManager.volumeBoostMultiplier,
            isQueueSticky = preferenceManager.isQueueSticky
        ) }
        viewModelScope.launch {
            playbackQueueManager.currentPlayingItem.collectLatest { item ->
                _uiState.update { it.copy(
                    currentPlayingItem = item,
                    resolvedThumbnailUrl = null,
                    likeCount = -1L,
                    commentCount = -1L,
                    likeCountFormatted = null,
                    commentCountFormatted = null
                ) }
                if (item != null) {
                    resolveThumbnail(item)
                    loadVideoStats(item)
                    val upcoming = playbackQueueManager.getUpcomingSnapshot()
                    if (upcoming.isEmpty() || _uiState.value.relatedSongs.isEmpty()) {
                        loadRelatedSongs(item, force = true)
                    } else {
                        _uiState.update { it.copy(relatedSongs = upcoming) }
                        if (upcoming.size < 5) {
                            loadMoreRelatedSongs()
                        }
                    }
                }
            }
        }
    }

    fun formatMetricCount(count: Long): String {
        return when {
            count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0).replace(".0M", "M")
            count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000.0).replace(".0K", "K")
            count > 0 -> count.toString()
            else -> ""
        }
    }

    private fun loadVideoStats(item: MusicItem) {
        viewModelScope.launch {
            try {
                val stats = repository.getVideoStats(item.url)
                if (isActive && _uiState.value.currentPlayingItem?.url == item.url && stats != null) {
                    val formattedLikes = if (stats.likeCount > 0) formatMetricCount(stats.likeCount) else null
                    val formattedComments = if (stats.commentCount >= 0) formatMetricCount(stats.commentCount) else null
                    _uiState.update { it.copy(
                        likeCount = stats.likeCount,
                        commentCount = stats.commentCount,
                        likeCountFormatted = formattedLikes,
                        commentCountFormatted = formattedComments
                    ) }
                }
            } catch (e: Exception) {
                SecureLogger.w("PlayerViewModel", "Failed to load video stats", e)
            }
        }
    }

    private fun resolveThumbnail(item: MusicItem) {
        viewModelScope.launch {
            try {
                val highRes = thumbnailResolver.resolveHighResThumbnail(
                    urlOrId = item.url,
                    fallbackThumbnail = item.thumbnailUrl,
                    title = item.title,
                    artist = item.uploader
                )
                if (isActive && _uiState.value.currentPlayingItem?.url == item.url) {
                    _uiState.update { it.copy(resolvedThumbnailUrl = highRes) }
                }
            } catch (e: Exception) {
                SecureLogger.w("PlayerViewModel", "Error resolving thumbnail", e)
            }
        }
    }

    fun setPlaybackLoading(isLoading: Boolean, itemUrl: String? = null) {
        _uiState.update { it.copy(isLoadingPlayer = isLoading, loadingItemUrl = itemUrl) }
    }

    fun loadRelatedSongs(item: MusicItem, force: Boolean = false) {
        relatedAnchorUrl = item.url
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRelatedTab = true) }
            val relatedJob = launch {
                val related = repository.getRelatedSongs(item.url, force = force)
                val filtered = related.filter { it.url != item.url }
                playbackQueueManager.replaceUpcomingItems(filtered)
                val upcoming = playbackQueueManager.getUpcomingSnapshot()
                _uiState.update { it.copy(relatedSongs = upcoming, isLoadingMoreRelated = false) }
            }
            val artistJob = launch {
                val artistTracks = repository.getArtistSongs(item.uploader)
                _uiState.update { it.copy(artistSongs = artistTracks) }
            }
            val similarJob = launch {
                val discoveries = repository.getDiscoverSimilarSongs(item.title, item.uploader)
                _uiState.update { it.copy(discoverSimilarSongs = discoveries) }
            }
            relatedJob.join()
            artistJob.join()
            similarJob.join()
            _uiState.update { it.copy(isLoadingRelatedTab = false) }
        }
    }

    /**
     * Fetches the next batch of suggestions for the current track so the
     * Up Next list keeps growing as the user scrolls.
     */
    fun loadMoreRelatedSongs() {
        val anchor = relatedAnchorUrl ?: return
        if (_uiState.value.isLoadingMoreRelated) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreRelated = true) }
            val more = repository.getMoreRelatedSongs(anchor)
            playbackQueueManager.appendDistinct(more)
            val upcoming = playbackQueueManager.getUpcomingSnapshot()
            _uiState.update { state ->
                state.copy(relatedSongs = upcoming, isLoadingMoreRelated = false)
            }
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
            updateState = { transform -> _uiState.update(transform) },
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
            updateState = { transform -> _uiState.update(transform) },
            result = result
        )
    }

    fun searchForLyrics(query: String) {
        lyricsCoordinator.searchForLyrics(
            scope = viewModelScope,
            updateState = { transform -> _uiState.update(transform) },
            query = query
        )
    }

    fun clearLyricsSearchResults() {
        lyricsCoordinator.clearLyricsSearchResults(
            updateState = { transform -> _uiState.update(transform) }
        )
    }

    fun translateCurrentLyrics() {
        lyricsCoordinator.translateCurrentLyrics(
            scope = viewModelScope,
            currentState = { _uiState.value },
            updateState = { transform -> _uiState.update(transform) }
        )
    }

    fun toggleTranslation(enabled: Boolean) {
        isTranslationEnabled = enabled
        if (enabled) {
            translateCurrentLyrics()
        }
    }

    fun toggleStickyQueue() {
        val next = !_uiState.value.isQueueSticky
        isQueueSticky = next
    }

    fun forceRefreshRelatedSongs() {
        val item = currentPlayingItem ?: return
        relatedAnchorUrl = null
        loadRelatedSongs(item, force = true)
    }

    fun getNextAutoplayItem(): MusicItem? {
        if (!autoplayEnabled) return null
        return playbackQueueManager.nextItem
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

    fun applyEqualizerPreset(preset: EqualizerPreset) {
        val updated = preset.applyTo(equalizerSettings).copy(enabled = true)
        updateEqualizerSettings(updated)
    }

    fun resetEqualizer() {
        updateEqualizerSettings(EqualizerSettings())
    }

    private fun updateEqualizerSettings(settings: EqualizerSettings) {
        val sanitized = settings.sanitized()
        _uiState.update { it.copy(equalizerSettings = sanitized) }
        preferenceManager.saveEqualizerSettings(sanitized)
    }

    fun updateVolumeBoost(multiplier: Float) {
        val clamped = multiplier.coerceIn(1.0f, 3.0f)
        preferenceManager.volumeBoostMultiplier = clamped
        _uiState.update { it.copy(volumeBoostMultiplier = clamped) }
    }

    fun adjustVolumeBoost(delta: Float) {
        val current = _uiState.value.volumeBoostMultiplier
        updateVolumeBoost(current + delta)
        showVolumeBoosterUI()
    }

    fun showVolumeBoosterUI() {
        _uiState.update { it.copy(isVolumeBoosterUIVisible = true) }
        volumeBoosterJob?.cancel()
        volumeBoosterJob = viewModelScope.launch {
            kotlinx.coroutines.delay(3000)
            _uiState.update { it.copy(isVolumeBoosterUIVisible = false) }
        }
    }

    fun setPipMode(enabled: Boolean) {
        _uiState.update { it.copy(isInPipMode = enabled) }
    }

    fun setFullScreenVideo(enabled: Boolean) {
        _uiState.update { it.copy(isFullScreenVideo = enabled) }
    }

    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    fun setSleepTimer(minutes: Int, onTimerFinished: () -> Unit, onFadeVolume: ((Float) -> Unit)? = null) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _uiState.update { it.copy(sleepTimerRemainingSeconds = null, isSleepTimerActive = false) }
            onFadeVolume?.invoke(1.0f)
            return
        }

        val totalSeconds = (minutes * 60).toLong()
        _uiState.update { it.copy(sleepTimerRemainingSeconds = totalSeconds, isSleepTimerActive = true) }
        sleepTimerJob = viewModelScope.launch {
            var remaining = totalSeconds
            val fadeWindowSeconds = 30L.coerceAtMost(totalSeconds)
            while (remaining > 0 && isActive) {
                kotlinx.coroutines.delay(1000)
                remaining--
                _uiState.update { it.copy(sleepTimerRemainingSeconds = remaining) }
                if (remaining <= fadeWindowSeconds && onFadeVolume != null) {
                    val fadeRatio = (remaining.toFloat() / fadeWindowSeconds).coerceIn(0.0f, 1.0f)
                    onFadeVolume(fadeRatio)
                }
            }
            _uiState.update { it.copy(sleepTimerRemainingSeconds = null, isSleepTimerActive = false) }
            onTimerFinished()
            onFadeVolume?.invoke(1.0f)
        }
    }

    fun cancelSleepTimer(onResetVolume: (() -> Unit)? = null) {
        sleepTimerJob?.cancel()
        _uiState.update { it.copy(sleepTimerRemainingSeconds = null, isSleepTimerActive = false) }
        onResetVolume?.invoke()
    }
}

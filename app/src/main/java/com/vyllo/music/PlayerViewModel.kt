package com.vyllo.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.LyricsResponse
import com.vyllo.music.domain.model.SyncedLyricLine
import com.vyllo.music.domain.model.LyricsResult
import com.vyllo.music.domain.model.EqualizerSettings
import com.vyllo.music.domain.model.EqualizerPreset
import com.vyllo.music.domain.repository.IMusicRepository
import com.vyllo.music.data.*
import com.vyllo.music.data.manager.PlaybackQueueManager
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.domain.manager.StreamUrlCache
import com.vyllo.music.domain.manager.player.EqualizerController
import com.vyllo.music.domain.manager.player.LyricsController
import com.vyllo.music.domain.manager.player.RelatedController
import com.vyllo.music.domain.manager.player.SleepTimerController
import com.vyllo.music.domain.manager.player.StreamResolver
import com.vyllo.music.domain.usecase.GetStreamUrlUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    // Controllers are initialized in init after viewModelScope is available.
    private lateinit var equalizerController: EqualizerController
    private lateinit var sleepTimerController: SleepTimerController
    private lateinit var lyricsController: LyricsController
    private lateinit var relatedController: RelatedController
    private lateinit var streamResolver: StreamResolver

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
        equalizerController = EqualizerController(
            preferenceManager = preferenceManager,
            getState = { _uiState.value },
            updateState = { transform -> _uiState.update(transform) },
            scopeProvider = { viewModelScope }
        )
        sleepTimerController = SleepTimerController(
            updateState = { transform -> _uiState.update(transform) },
            scopeProvider = { viewModelScope }
        )
        lyricsController = LyricsController(
            lyricsCoordinator = lyricsCoordinator,
            getState = { _uiState.value },
            updateState = { transform -> _uiState.update(transform) },
            scopeProvider = { viewModelScope }
        )
        relatedController = RelatedController(
            repository = repository,
            playbackQueueManager = playbackQueueManager,
            getState = { _uiState.value },
            updateState = { transform -> _uiState.update(transform) },
            scopeProvider = { viewModelScope }
        )
        streamResolver = StreamResolver(
            repository = repository,
            getStreamUrlUseCase = getStreamUrlUseCase,
            streamUrlCache = streamUrlCache,
            thumbnailResolver = thumbnailResolver,
            getState = { _uiState.value },
            updateState = { transform -> _uiState.update(transform) },
            scopeProvider = { viewModelScope }
        )

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
                    streamResolver.resolveThumbnail(item)
                    streamResolver.loadVideoStats(item)
                    val upcoming = playbackQueueManager.getUpcomingSnapshot()
                    if (upcoming.isEmpty() || _uiState.value.relatedSongs.isEmpty()) {
                        relatedController.loadRelatedSongs(item, force = true)
                    } else {
                        _uiState.update { it.copy(relatedSongs = upcoming) }
                        if (upcoming.size < 5) {
                            relatedController.loadMoreRelatedSongs()
                        }
                    }
                }
            }
        }
    }

    fun formatMetricCount(count: Long): String = streamResolver.formatMetricCount(count)

    fun setPlaybackLoading(isLoading: Boolean, itemUrl: String? = null) {
        _uiState.update { it.copy(isLoadingPlayer = isLoading, loadingItemUrl = itemUrl) }
    }

    fun loadRelatedSongs(item: MusicItem, force: Boolean = false) =
        relatedController.loadRelatedSongs(item, force)

    fun loadMoreRelatedSongs() = relatedController.loadMoreRelatedSongs()

    suspend fun resolveStream(item: MusicItem, isVideo: Boolean = false): String? =
        streamResolver.resolveStream(item, isVideo)

    fun toggleVideoMode(currentPosition: Long, onSwitch: (String?) -> Unit) {
        streamResolver.toggleVideoMode(
            currentItem = currentPlayingItem,
            isVideoMode = isVideoMode,
            currentPosition = currentPosition,
            setVideoMode = { isVideoMode = it },
            onSwitch = onSwitch
        )
    }

    fun fetchLyrics(item: MusicItem, durationSecs: Long) =
        lyricsController.fetchLyrics(item, durationSecs)

    fun updateLyricsPosition(positionMs: Long) =
        lyricsController.updatePosition(positionMs)

    fun adjustLyricsOffset(deltaMs: Long) =
        lyricsController.adjustOffset(deltaMs)

    fun selectAlternativeLyrics(result: LyricsResult) =
        lyricsController.selectAlternative(result)

    fun searchForLyrics(query: String) =
        lyricsController.search(query)

    fun clearLyricsSearchResults() =
        lyricsController.clearSearchResults()

    fun translateCurrentLyrics() =
        lyricsController.translateCurrentLyrics()

    fun toggleTranslation(enabled: Boolean) =
        lyricsController.toggleTranslation(enabled)

    fun toggleStickyQueue() {
        val next = !_uiState.value.isQueueSticky
        isQueueSticky = next
    }

    fun forceRefreshRelatedSongs() =
        relatedController.forceRefreshRelatedSongs(currentPlayingItem)

    fun getNextAutoplayItem(): MusicItem? =
        relatedController.getNextAutoplayItem(autoplayEnabled)

    fun setEqualizerEnabled(enabled: Boolean) =
        equalizerController.setEqualizerEnabled(enabled)

    fun updateBassBoost(strength: Int) =
        equalizerController.updateBassBoost(strength)

    fun updateVirtualizer(strength: Int) =
        equalizerController.updateVirtualizer(strength)

    fun updateEqualizerBand(index: Int, level: Int) =
        equalizerController.updateEqualizerBand(index, level)

    fun applyEqualizerPreset(preset: EqualizerPreset) =
        equalizerController.applyEqualizerPreset(preset)

    fun resetEqualizer() =
        equalizerController.resetEqualizer()

    fun updateVolumeBoost(multiplier: Float) =
        equalizerController.updateVolumeBoost(multiplier)

    fun adjustVolumeBoost(delta: Float) =
        equalizerController.adjustVolumeBoost(delta)

    fun showVolumeBoosterUI() =
        equalizerController.showVolumeBoosterUI()

    fun setPipMode(enabled: Boolean) {
        _uiState.update { it.copy(isInPipMode = enabled) }
    }

    fun setFullScreenVideo(enabled: Boolean) {
        _uiState.update { it.copy(isFullScreenVideo = enabled) }
    }

    fun setSleepTimer(minutes: Int, onTimerFinished: () -> Unit, onFadeVolume: ((Float) -> Unit)? = null) =
        sleepTimerController.setSleepTimer(minutes, onTimerFinished, onFadeVolume)

    fun cancelSleepTimer(onResetVolume: (() -> Unit)? = null) =
        sleepTimerController.cancelSleepTimer(onResetVolume)
}

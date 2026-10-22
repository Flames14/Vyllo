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
import com.vyllo.music.domain.model.EqualizerPresetCodec
import com.vyllo.music.domain.repository.IMusicRepository
import com.vyllo.music.data.*
import com.vyllo.music.domain.manager.PlaybackQueueManager
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.domain.manager.StreamUrlCache
import com.vyllo.music.domain.manager.player.EqualizerController
import com.vyllo.music.domain.manager.player.LyricsController
import com.vyllo.music.domain.manager.player.PlayerLyricsCoordinator
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
    /** Related tab: top of the same ranked pool Up Next uses, weighted to new artists. */
    val discoverySongs: List<MusicItem> = emptyList(),
    val isLoadingRelatedTab: Boolean = false,
    val isLoadingMoreRelated: Boolean = false,
    /** Per-section flags so each Related section can show its own skeleton. */
    val isLoadingPool: Boolean = false,
    val isLoadingArtistSongs: Boolean = false,
    val isLoadingSimilarSongs: Boolean = false,
    val autoplayEnabled: Boolean = true,
    /** Whether Up Next keeps live/event takes and non-song uploads (default off). */
    val upNextIncludeVariants: Boolean = false,
    val isLoadingPlayer: Boolean = false,
    val loadingItemUrl: String? = null,
    val isVideoMode: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
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
    val customEqualizerPresets: List<EqualizerPreset> = emptyList(),
    val volumeBoostMultiplier: Float = 1.0f,
    val isVolumeBoosterUIVisible: Boolean = false,
    val isInPipMode: Boolean = false,
    val isFullScreenVideo: Boolean = false,
    val likeCount: Long = -1L,
    val commentCount: Long = -1L,
    val likeCountFormatted: String? = null,
    val commentCountFormatted: String? = null,
    /** Persisted user rating for the current track: 1 liked, -1 disliked, 0 unrated. */
    val favoriteRating: Int = 0,
    /**
     * The authoritative upcoming queue, mirroring [PlaybackQueueManager].
     *
     * Deliberately NOT [relatedSongs]: that is the shared recommendation pool
     * which autoplay refills, so it cannot represent a user-edited queue. Up
     * Next renders this so remove/reorder actually change playback order.
     */
    val queueItems: List<MusicItem> = emptyList(),
    /** Played prefix of the live queue, newest first; the current item is excluded. */
    val playedQueueItems: List<MusicItem> = emptyList(),
    val isQueueSticky: Boolean = true,
    val sleepTimerRemainingSeconds: Long? = null,
    val isSleepTimerActive: Boolean = false
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: IMusicRepository,
    private val getStreamUrlUseCase: GetStreamUrlUseCase,
    private val playMusicUseCase: com.vyllo.music.domain.usecase.PlayMusicUseCase,
    private val playbackQueueManager: PlaybackQueueManager,
    private val preferenceManager: PreferenceManager,
    private val streamUrlCache: StreamUrlCache,
    private val lyricsCoordinator: PlayerLyricsCoordinator,
    private val thumbnailResolver: com.vyllo.music.data.network.YouTubeThumbnailResolver,
    private val autoEqRepository: com.vyllo.music.data.AutoEqRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            autoplayEnabled = preferenceManager.isAutoplayEnabled,
            playbackSpeed = preferenceManager.playbackSpeed,
            playbackPitch = preferenceManager.playbackPitch
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    // Lyric highlight index lives outside PlayerUiState so the 500ms tick
    // only recomposes the lyrics list, not the whole navigation/player UI.
    private val _lyricIndex = MutableStateFlow(-1)
    val lyricIndex: StateFlow<Int> = _lyricIndex.asStateFlow()

    // Kept outside PlayerUiState: only the lyrics list reads it, and dragging a
    // size slider must not recompose the player chrome.
    private val _lyricsStyle = MutableStateFlow(preferenceManager.readLyricsStyle())
    val lyricsStyle: StateFlow<com.vyllo.music.domain.model.LyricsStyle> = _lyricsStyle.asStateFlow()

    fun updateLyricsStyle(style: com.vyllo.music.domain.model.LyricsStyle) {
        preferenceManager.writeLyricsStyle(style)
        _lyricsStyle.value = style
    }

    // Controllers are initialized in init after viewModelScope is available.
    private val equalizerController: EqualizerController
    private val sleepTimerController: SleepTimerController
    private val lyricsController: LyricsController
    private val relatedController: RelatedController
    private val streamResolver: StreamResolver

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
        set(value) {
            preferenceManager.isAutoplayEnabled = value
            _uiState.update { it.copy(autoplayEnabled = value) }
        }
    var upNextIncludeVariants: Boolean
        get() = _uiState.value.upNextIncludeVariants
        set(value) {
            preferenceManager.upNextIncludeVariants = value
            _uiState.update { it.copy(upNextIncludeVariants = value) }
        }
    val isLoadingPlayer: Boolean
        get() = _uiState.value.isLoadingPlayer
    val loadingItemUrl: String?
        get() = _uiState.value.loadingItemUrl
    var isVideoMode: Boolean
        get() = _uiState.value.isVideoMode
        set(value) { _uiState.update { it.copy(isVideoMode = value) } }

    /**
     * Speed/pitch live in Preferences because MediaService owns the player and
     * listens for these keys — the UI never has to hold a controller to apply
     * a change, and it keeps working with the screen off.
     */
    var playbackSpeed: Float
        get() = _uiState.value.playbackSpeed
        set(value) {
            preferenceManager.playbackSpeed = value
            _uiState.update { it.copy(playbackSpeed = preferenceManager.playbackSpeed) }
        }

    var playbackPitch: Float
        get() = _uiState.value.playbackPitch
        set(value) {
            preferenceManager.playbackPitch = value
            _uiState.update { it.copy(playbackPitch = preferenceManager.playbackPitch) }
        }

    fun resetPlaybackRate() {
        playbackSpeed = 1.0f
        playbackPitch = 1.0f
    }
    val lyricsResponse: LyricsResponse?
        get() = _uiState.value.lyricsResponse
    val syncedLyricsLines: List<SyncedLyricLine>
        get() = _uiState.value.syncedLyricsLines
    var currentLyricIndex: Int
        get() = _lyricIndex.value
        set(value) { _lyricIndex.value = value }
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
    val favoriteRating: Int
        get() = _uiState.value.favoriteRating
    val queueItems: List<MusicItem>
        get() = _uiState.value.queueItems

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
            scopeProvider = { viewModelScope },
            getLyricIndex = { _lyricIndex.value },
            setLyricIndex = { _lyricIndex.value = it }
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
            customEqualizerPresets = EqualizerPresetCodec.decode(preferenceManager.customEqualizerPresets),
            volumeBoostMultiplier = preferenceManager.volumeBoostMultiplier,
            isQueueSticky = preferenceManager.isQueueSticky,
            upNextIncludeVariants = preferenceManager.upNextIncludeVariants
        ) }
        // Keep the Up Next list bound to the real queue. The manager bumps
        // queueVersion on every structural edit, so this also covers edits made
        // from the service (autoplay refill, restore, shuffle toggle).
        viewModelScope.launch {
            playbackQueueManager.queueVersion.collectLatest {
                _uiState.update {
                    it.copy(
                        queueItems = playbackQueueManager.getUpcomingSnapshot(),
                        playedQueueItems = playbackQueueManager.getPlayedSnapshot()
                    )
                }
            }
        }
        viewModelScope.launch {
            playbackQueueManager.currentPlayingItem.collectLatest { item ->
                _uiState.update { it.copy(
                    currentPlayingItem = item,
                    playedQueueItems = playbackQueueManager.getPlayedSnapshot(),
                    resolvedThumbnailUrl = null,
                    likeCount = -1L,
                    commentCount = -1L,
                    likeCountFormatted = null,
                    commentCountFormatted = null,
                    favoriteRating = 0
                ) }
                if (item != null) {
                    streamResolver.resolveThumbnail(item)
                    streamResolver.loadVideoStats(item)
                    // Hydrate the persisted rating so the like/dislike pill
                    // reflects real state on track change instead of a stub.
                    val rating = runCatching { repository.getRating(item.url) }.getOrDefault(0)
                    _uiState.update { it.copy(favoriteRating = rating) }
                    val upcoming = playbackQueueManager.getUpcomingSnapshot()
                    val previousRelated = _uiState.value.relatedSongs
                    val isFromCurrentPool = previousRelated.any { it.url == item.url }
                    if (!isFromCurrentPool) {
                        // New playback context (search / recent / external). Do NOT
                        // force: the pool for this anchor may still be cached, and
                        // force=true threw away a good ranking plus fired nine
                        // searches on every track change.
                        relatedController.loadRelatedSongs(item, force = false)
                    } else {
                        // Same pool: natural advance (played the head) trims it
                        // off; a manual skip-ahead keeps the skipped songs above
                        // so they don't vanish from Up Next.
                        val playedHead = previousRelated.firstOrNull()?.url == item.url
                        if (playedHead) {
                            if (upcoming.isNotEmpty()) {
                                _uiState.update { it.copy(relatedSongs = upcoming) }
                            }
                        } else {
                            val merged = (previousRelated + upcoming)
                                .distinctBy { it.url }
                                .filter { it.url != item.url }
                            _uiState.update { it.copy(relatedSongs = merged) }
                        }
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

    fun fetchLyrics(item: MusicItem, durationSecs: Long) {
        _lyricIndex.value = -1
        lyricsController.fetchLyrics(item, durationSecs)
    }

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

    /**
     * Rate the current track. Passing the already-active rating clears it, which
     * is what a second tap on the same thumb-up must do.
     *
     * Optimistic: the UI updates immediately, then reverts if persistence fails.
     */
    fun toggleFavoriteRating(rating: Int) {
        val item = _uiState.value.currentPlayingItem ?: return
        val previous = _uiState.value.favoriteRating
        val next = if (previous == rating) 0 else rating
        _uiState.update { it.copy(favoriteRating = next) }
        viewModelScope.launch {
            val persisted = runCatching { repository.setRating(item, next) }.getOrDefault(false)
            if (!persisted) {
                _uiState.update { it.copy(favoriteRating = previous) }
            }
        }
    }

    // --- Up Next queue editing -------------------------------------------------
    // Indices are upcoming-relative (0 = the next track). Every mutation bumps
    // queueVersion, which re-syncs queueItems, re-prunes the ExoPlayer lookahead
    // and persists the queue, so there is nothing extra to refresh by hand.

    /** Removes one upcoming track. Returns it so the caller can offer undo. */
    fun removeFromQueue(url: String): MusicItem? {
        val index = playbackQueueManager.upcomingIndexOf(url)
        if (index < 0) return null
        return playbackQueueManager.removeUpcomingAt(index)
    }

    /** Restores a removed track at its original upcoming position. */
    fun undoQueueRemoval(item: MusicItem, index: Int): Boolean =
        playbackQueueManager.insertUpcomingAt(index, listOf(item))

    /** Restores a cleared Up Next section at the head of the queue. */
    fun undoQueueClear(items: List<MusicItem>): Boolean =
        playbackQueueManager.insertUpcomingAt(0, items)

    /** Reorders upcoming tracks. [from]/[to] are upcoming-relative. */
    fun moveInQueue(from: Int, to: Int): Boolean = playbackQueueManager.moveUpcoming(from, to)

    /** Moves an upcoming track by an offset, e.g. -1 for "move up". */
    fun moveInQueueBy(index: Int, delta: Int): Boolean =
        playbackQueueManager.moveUpcomingBy(index, delta)

    /** Empties Up Next while leaving the current track playing. Returns what was removed. */
    fun clearUpcoming(): List<MusicItem> = playbackQueueManager.clearUpcoming()

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

    /**
     * Saves the current curve as a named preset. An existing preset with the
     * same name is replaced, which doubles as "update existing".
     */
    fun saveEqualizerPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val snapshot = EqualizerPresetCodec.fromSettings(trimmed, equalizerSettings)
        val updated = _uiState.value.customEqualizerPresets
            .filterNot { it.name.equals(trimmed, ignoreCase = true) } + snapshot
        preferenceManager.customEqualizerPresets = EqualizerPresetCodec.encode(updated)
        _uiState.update { it.copy(customEqualizerPresets = updated) }
    }

    fun deleteEqualizerPreset(preset: EqualizerPreset) {
        val updated = _uiState.value.customEqualizerPresets
            .filterNot { it.name.equals(preset.name, ignoreCase = true) }
        preferenceManager.customEqualizerPresets = EqualizerPresetCodec.encode(updated)
        _uiState.update { it.copy(customEqualizerPresets = updated) }
    }

    // ---- AutoEQ import wizard ----

    private val _autoEqImport = MutableStateFlow(com.vyllo.music.domain.model.AutoEqImportState())
    val autoEqImport: StateFlow<com.vyllo.music.domain.model.AutoEqImportState> =
        _autoEqImport.asStateFlow()

    private var pendingAutoEqProfile: com.vyllo.music.domain.model.AutoEqProfile? = null

    /**
     * Reads [source] as either a direct link to an AutoEQ export or the exported
     * text itself, then prepares a five-band preview. Nothing is applied until
     * the user confirms.
     */
    fun importAutoEq(source: String, name: String) {
        val trimmed = source.trim()
        val fallbackName = name.trim().ifBlank { "AutoEQ" }
        if (trimmed.isEmpty()) {
            _autoEqImport.value = com.vyllo.music.domain.model.AutoEqImportState(
                error = com.vyllo.music.domain.model.AutoEqImportError.INVALID_SOURCE
            )
            return
        }
        viewModelScope.launch {
            _autoEqImport.value = com.vyllo.music.domain.model.AutoEqImportState(isLoading = true)
            val text = if (trimmed.startsWith("http", ignoreCase = true)) {
                runCatching { autoEqRepository.fetch(trimmed) }.getOrElse {
                    _autoEqImport.value = com.vyllo.music.domain.model.AutoEqImportState(
                        error = com.vyllo.music.domain.model.AutoEqImportError.FETCH_FAILED
                    )
                    return@launch
                }
            } else {
                trimmed
            }

            val profile = com.vyllo.music.domain.model.AutoEqParser.parse(text, fallbackName)
            if (profile == null) {
                _autoEqImport.value = com.vyllo.music.domain.model.AutoEqImportState(
                    error = com.vyllo.music.domain.model.AutoEqImportError.UNPARSABLE
                )
                return@launch
            }
            pendingAutoEqProfile = profile
            _autoEqImport.value = com.vyllo.music.domain.model.AutoEqImportState(
                profileName = profile.name,
                bandLevels = profile.toBandLevels()
            )
        }
    }

    /** Applies the previewed profile as an enabled preset. */
    fun applyAutoEqImport() {
        val profile = pendingAutoEqProfile ?: return
        val preset = com.vyllo.music.domain.model.EqualizerPreset(
            name = profile.name,
            bassBoostStrength = 0,
            virtualizerStrength = 0,
            bandLevels = profile.toBandLevels()
        )
        equalizerController.applyEqualizerPreset(preset)
        clearAutoEqImport()
    }

    fun clearAutoEqImport() {
        pendingAutoEqProfile = null
        _autoEqImport.value = com.vyllo.music.domain.model.AutoEqImportState()
    }

    fun updateVolumeBoost(multiplier: Float) =
        equalizerController.updateVolumeBoost(multiplier)

    fun adjustVolumeBoost(delta: Float) =
        equalizerController.adjustVolumeBoost(delta)

    fun showVolumeBoosterUI() =
        equalizerController.showVolumeBoosterUI()

    fun setPipMode(enabled: Boolean) {
        _uiState.update { it.copy(isInPipMode = enabled) }
    }

    // --- Playback orchestration (moved out of MainActivity for 10/10) ---
    // Last-tap-wins generation + 250ms debounce, previously in Activity.
    private var playRequestSeq: Long = 0L
    private var lastPlayRequestAt: Long = 0L


    /**
     * Resolves + starts playback. Returns PlayResult for UI feedback (toast/snackbar).
     * Safe to call from Activity/Compose; handles loading flags + related preload.
     */
    fun playTrack(
        item: MusicItem,
        fromQueue: Boolean = false,
        onResult: ((com.vyllo.music.domain.model.PlayResult) -> Unit)? = null
    ) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastPlayRequestAt < 250L) return
        lastPlayRequestAt = now
        val mySeq = ++playRequestSeq
        val isVideo = _uiState.value.isVideoMode
        setPlaybackLoading(true, item.url)
        viewModelScope.launch {
            try {
                val result = playMusicUseCase.execute(item, isVideo = isVideo, keepQueue = fromQueue)
                if (mySeq != playRequestSeq) return@launch
                if (result is com.vyllo.music.domain.model.PlayResult.Success) {
                    if (!fromQueue || _uiState.value.relatedSongs.isEmpty()) {
                        loadRelatedSongs(item)
                    }
                }
                onResult?.invoke(result)
            } finally {
                if (mySeq == playRequestSeq) {
                    setPlaybackLoading(false)
                }
            }
        }
    }

    /**
     * Starts an endless station seeded by [item].
     *
     * Ordinary playback queues a single track and lets the service's lookahead
     * decide what comes next, which leaves the Up Next list looking empty right
     * after the tap. A radio start instead resolves the related pool up front and
     * seeds the queue with it, so the station is visible and shuffling inside it
     * behaves like a normal queue. Autoplay still refills once the seed list runs
     * out.
     */
    fun startRadio(
        item: MusicItem,
        onResult: ((com.vyllo.music.domain.model.PlayResult) -> Unit)? = null
    ) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastPlayRequestAt < 250L) return
        lastPlayRequestAt = now
        val mySeq = ++playRequestSeq
        setPlaybackLoading(true, item.url)
        viewModelScope.launch {
            try {
                val related = runCatching { repository.getRelatedSongs(item.url, force = true) }
                    .getOrDefault(emptyList())
                if (mySeq != playRequestSeq) return@launch
                val station = com.vyllo.music.domain.model.RadioStation.build(item, related)
                playbackQueueManager.replaceQueue(station, 0)
                val result = playMusicUseCase.execute(item, isVideo = _uiState.value.isVideoMode, keepQueue = true)
                if (mySeq != playRequestSeq) return@launch
                if (result is com.vyllo.music.domain.model.PlayResult.Success) {
                    loadRelatedSongs(item)
                }
                onResult?.invoke(result)
            } finally {
                if (mySeq == playRequestSeq) {
                    setPlaybackLoading(false)
                }
            }
        }
    }

    fun playNextFromQueue(onResult: ((com.vyllo.music.domain.model.PlayResult) -> Unit)? = null) {        val queueSnapshot = playbackQueueManager.getQueueSnapshot()
        val currentIdx = playbackQueueManager.currentIndex
        if (currentIdx >= 0 && currentIdx < queueSnapshot.size - 1) {
            playTrack(queueSnapshot[currentIdx + 1], fromQueue = true, onResult = onResult)
        } else {
            getNextAutoplayItem()?.let { playTrack(it, fromQueue = true, onResult = onResult) }
        }
    }

    fun playPreviousFromQueue() {
        val queueSnapshot = playbackQueueManager.getQueueSnapshot()
        val currentIdx = playbackQueueManager.currentIndex
        if (currentIdx > 0) {
            playTrack(queueSnapshot[currentIdx - 1], fromQueue = true)
        }
    }

    fun setFullScreenVideo(enabled: Boolean) {
        _uiState.update { it.copy(isFullScreenVideo = enabled) }
    }

    fun setSleepTimer(minutes: Int, onTimerFinished: () -> Unit, onFadeVolume: ((Float) -> Unit)? = null) =
        sleepTimerController.setSleepTimer(minutes, onTimerFinished, onFadeVolume)

    fun cancelSleepTimer(onResetVolume: (() -> Unit)? = null) =
        sleepTimerController.cancelSleepTimer(onResetVolume)
}

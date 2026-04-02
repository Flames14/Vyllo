package com.vyllo.music

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.data.*
import com.vyllo.music.data.manager.PlaybackQueueManager
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.domain.usecase.GetLyricsUseCase
import com.vyllo.music.domain.usecase.GetStreamUrlUseCase
import com.vyllo.music.domain.usecase.TranslateLyricsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: IMusicRepository,
    private val getStreamUrlUseCase: GetStreamUrlUseCase,
    private val getLyricsUseCase: GetLyricsUseCase,
    private val translateLyricsUseCase: TranslateLyricsUseCase,
    private val playbackQueueManager: PlaybackQueueManager,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    var currentPlayingItem by mutableStateOf<MusicItem?>(null)
    var relatedSongs by mutableStateOf<List<MusicItem>>(emptyList())
    var autoplayEnabled by mutableStateOf(true)
    var isLoadingPlayer by mutableStateOf(false)
    var loadingItemUrl by mutableStateOf<String?>(null)
    var isVideoMode by mutableStateOf(false)
    private var pendingVideoMode by mutableStateOf<Boolean?>(null)

    // --- Lyrics state ---
    var lyricsResponse by mutableStateOf<LyricsResponse?>(null)
    var syncedLyricsLines by mutableStateOf<List<SyncedLyricLine>>(emptyList())
    var currentLyricIndex by mutableIntStateOf(-1)
    var lyricsLoading by mutableStateOf(false)
    var lyricsOffsetMs by mutableLongStateOf(0L)
    var showLyricsSelector by mutableStateOf(false)
    var lyricsSearchQuery by mutableStateOf("")
    var lyricsSearchResults by mutableStateOf<List<LyricsResult>>(emptyList())
    var lyricsSearching by mutableStateOf(false)
    
    // --- Translation state ---
    var translatedLyricsLines by mutableStateOf<List<String?>>(emptyList())
    var translatedPlainLyrics by mutableStateOf<String?>(null)
    var isTranslationEnabled by mutableStateOf(false)
    var isTranslating by mutableStateOf(false)
    var detectedLyricsLangCode by mutableStateOf<String?>(null)

    private var currentLyricsUrl: String? = null
    private var lyricsJob: Job? = null
    private var lyricsSearchJob: Job? = null
    private var playbackJob: Job? = null
    private var translationJob: Job? = null
    private var lastFetchedDuration: Long = 0

    init {
        viewModelScope.launch {
            playbackQueueManager.currentPlayingItem.collectLatest { item ->
                currentPlayingItem = item
            }
        }
    }

    fun loadRelatedSongs(item: MusicItem) {
        viewModelScope.launch {
            val related = repository.getRelatedSongs(item.url)
            if (related.isNotEmpty()) {
                relatedSongs = related
                syncQueueWithRelated(related)
            }
        }
    }

    private fun syncQueueWithRelated(related: List<MusicItem>) {
        val currentIdx = playbackQueueManager.currentIndex
        if (currentIdx >= 0) {
            val kept = playbackQueueManager.currentQueue.take(currentIdx + 1)
            playbackQueueManager.currentQueue.clear()
            playbackQueueManager.currentQueue.addAll(kept)
            playbackQueueManager.currentQueue.addAll(related)
        }
    }

    private val streamUrlCache = mutableMapOf<String, String>()

    suspend fun resolveStream(item: MusicItem, isVideo: Boolean = false): String? {
        val cacheKey = "${item.url}_$isVideo"
        streamUrlCache[cacheKey]?.let { 
            android.util.Log.d("PlayerViewModel", "Stream URL cache hit: $cacheKey")
            return it 
        }

        loadingItemUrl = item.url
        isLoadingPlayer = true
        android.util.Log.d("PlayerViewModel", "Resolving stream: url=${item.url}, isVideo=$isVideo")
        val url = getStreamUrlUseCase(item.url, isVideo = isVideo)
        isLoadingPlayer = false
        loadingItemUrl = null

        if (url != null) {
            streamUrlCache[cacheKey] = url
            android.util.Log.d("PlayerViewModel", "Stream URL resolved: ${url.take(50)}...")
        } else {
            android.util.Log.w("PlayerViewModel", "Failed to resolve stream URL")
        }
        return url
    }

    fun resolveStreamWithCallback(item: MusicItem, isVideo: Boolean = false, onResult: (String?) -> Unit) {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val url = resolveStream(item, isVideo)
            if (isActive) onResult(url)
        }
    }

    fun toggleVideoMode(currentPosition: Long, onSwitch: (String?) -> Unit) {
        val item = currentPlayingItem ?: return
        val targetVideoMode = !isVideoMode
        android.util.Log.d("PlayerViewModel", "toggleVideoMode: current=$isVideoMode, target=$targetVideoMode, position=$currentPosition")
        
        isVideoMode = targetVideoMode

        // Re-resolve the stream with the new mode
        resolveStreamWithCallback(item, isVideo = isVideoMode) { newUrl ->
            android.util.Log.d("PlayerViewModel", "toggleVideoMode callback: newUrl=${newUrl?.take(50)}...")
            onSwitch(newUrl)
        }
    }

    fun fetchLyrics(item: MusicItem, durationSecs: Long) {
        if (currentLyricsUrl == item.url && (lastFetchedDuration > 0 || durationSecs == 0L)) return

        currentLyricsUrl = item.url
        lastFetchedDuration = durationSecs
        resetLyricsState()
        
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch(Dispatchers.IO) {
            val result = getLyricsUseCase(item, durationSecs)
            lyricsResponse = result
            syncedLyricsLines = result?.syncedLines ?: emptyList()
            lyricsLoading = false

            val detectedLanguages = result?.languages ?: listOf("english")
            detectedLyricsLangCode = if (detectedLanguages.any { it != "english" }) {
                detectedLanguages.firstOrNull { it != "english" } ?: "english"
            } else {
                "english"
            }
        }
    }

    private fun resetLyricsState() {
        lyricsResponse = null
        syncedLyricsLines = emptyList()
        currentLyricIndex = -1
        lyricsLoading = true
        showLyricsSelector = false
        lyricsSearchQuery = ""
        lyricsSearchResults = emptyList()
        translatedLyricsLines = emptyList()
        translatedPlainLyrics = null
        isTranslationEnabled = false
        isTranslating = false
        detectedLyricsLangCode = null
        translationJob?.cancel()
        TranslationEngine.resetSession()
    }

    fun updateLyricsPosition(positionMs: Long) {
        val index = LyricsEngine.getCurrentLyricLine(syncedLyricsLines, positionMs + lyricsOffsetMs)
        if (index != currentLyricIndex) {
            currentLyricIndex = index
        }
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        lyricsOffsetMs += deltaMs
    }

    fun selectAlternativeLyrics(result: LyricsResult) {
        val parsedLines = LyricsEngine.parseSyncedLyrics(result.syncedLyrics)
        syncedLyricsLines = parsedLines
        currentLyricIndex = -1
        showLyricsSelector = false
        lyricsSearchQuery = ""
        lyricsSearchResults = emptyList()

        currentLyricsUrl?.let { url ->
            repository.saveLyricsPreference(url, result.id)
        }

        lyricsResponse = lyricsResponse?.copy(
            result = result,
            plainLyrics = result.plainLyrics,
            syncedLines = parsedLines.takeIf { it.isNotEmpty() },
            lyricsStatus = LyricsStatus(
                hasPlain = result.plainLyrics != null,
                hasSynced = result.syncedLyrics != null,
                isInstrumental = result.instrumental
            )
        )
    }

    fun searchForLyrics(query: String) {
        if (query.isBlank()) return
        lyricsSearching = true
        lyricsSearchJob?.cancel()
        lyricsSearchJob = viewModelScope.launch(Dispatchers.IO) {
            val results = LyricsEngine.searchLyrics(query)
            lyricsSearchResults = results
            lyricsSearching = false
        }
    }

    fun clearLyricsSearchResults() {
        lyricsSearchQuery = ""
        lyricsSearchResults = emptyList()
        lyricsSearching = false
        lyricsSearchJob?.cancel()
    }

    fun translateCurrentLyrics() {
        translationJob?.cancel()
        isTranslating = true
        translationJob = viewModelScope.launch {
            if (syncedLyricsLines.isNotEmpty()) {
                translatedLyricsLines = translateLyricsUseCase.translateLines(syncedLyricsLines)
            } else {
                val plain = lyricsResponse?.plainLyrics
                if (!plain.isNullOrBlank()) {
                    translatedPlainLyrics = translateLyricsUseCase.translatePlain(plain)
                }
            }
            isTranslating = false
        }
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
}

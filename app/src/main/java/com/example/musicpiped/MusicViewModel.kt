package com.example.musicpiped

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicpiped.data.MusicItem
import com.example.musicpiped.data.MusicRepository
import com.example.musicpiped.data.MusicItemType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.compose.runtime.mutableStateMapOf
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.collectLatest
import com.example.musicpiped.data.download.DownloadEntity
import com.example.musicpiped.data.LyricsResponse
import com.example.musicpiped.data.LyricsResult
import com.example.musicpiped.data.SyncedLyricLine
import com.example.musicpiped.data.LyricsEngine
import com.example.musicpiped.data.TranslationEngine

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<MusicItem>>(emptyList())
    var suggestions by mutableStateOf<List<String>>(emptyList())
    var homePlaylists by mutableStateOf<List<MusicItem>>(emptyList())
    var recentlyPlayed by mutableStateOf<List<MusicItem>>(emptyList())
    var exploreCategories by mutableStateOf<List<String>>(
        listOf("New releases", "Charts", "Moods & genres", "Party", "Romance", "Sleep", "Workout")
    )
    var selectedExploreCategory by mutableStateOf<String?>(null)
    
    // YTM-style categorized home content
    var listenAgainItems by mutableStateOf<List<MusicItem>>(emptyList())
    var quickPicksItems by mutableStateOf<List<MusicItem>>(emptyList())
    var mixedForYouItems by mutableStateOf<List<MusicItem>>(emptyList())
    var newReleasesItems by mutableStateOf<List<MusicItem>>(emptyList())
    var trendingNowItems by mutableStateOf<List<MusicItem>>(emptyList())
    var chillItems by mutableStateOf<List<MusicItem>>(emptyList())
    var workoutItems by mutableStateOf<List<MusicItem>>(emptyList())
    var focusItems by mutableStateOf<List<MusicItem>>(emptyList())
    
    // Performance: Pre-calculated Grid rows (Box Column Area)
    var quickPicksRows by mutableStateOf<List<List<MusicItem>>>(emptyList())
    var trendingNowRows by mutableStateOf<List<List<MusicItem>>>(emptyList())
    
    var selectedChip by mutableStateOf("All")
    var selectedNavTab by mutableStateOf(0) // 0=Home, 1=Explore, 2=Library
    
    var exploreTrendingItems by mutableStateOf<List<MusicItem>>(emptyList())
    var isLoadingExplore by mutableStateOf(false)

    var homeTitle by mutableStateOf("Recommended For You")
    var isLoading by mutableStateOf(false)
    var isLoadingMore by mutableStateOf(false)
    var isSearching by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var isFloatingEnabled by mutableStateOf(MusicRepository.isFloatingPlayerEnabled)
    var isBackgroundPlaybackEnabled by mutableStateOf(MusicRepository.isBackgroundPlaybackEnabled)
    var themeMode by mutableStateOf(MusicRepository.themeMode)

    
    // Fix #8: Show buffering state immediately
    var isLoadingPlayer by mutableStateOf(false)

    // Download states
    var downloadedSongs by mutableStateOf<List<DownloadEntity>>(emptyList())
    var downloadProgress = mutableStateMapOf<String, Int>()

    // Playback state lifted to ViewModel for optimistic updates
    var currentPlayingItem by mutableStateOf<MusicItem?>(null)
    var relatedSongs by mutableStateOf<List<MusicItem>>(emptyList())
    var autoplayEnabled by mutableStateOf(true)

    // --- Lyrics state ---
    var lyricsResponse by mutableStateOf<LyricsResponse?>(null)
    var syncedLyricsLines by mutableStateOf<List<SyncedLyricLine>>(emptyList())
    var currentLyricIndex by mutableStateOf(-1)
    var lyricsLoading by mutableStateOf(false)
    var showLyricsSelector by mutableStateOf(false)
    var lyricsSearchQuery by mutableStateOf("")
    var lyricsSearchResults by mutableStateOf<List<LyricsResult>>(emptyList())
    var lyricsSearching by mutableStateOf(false)
    private var currentLyricsUrl: String? = null
    private var lyricsJob: Job? = null
    private var lyricsSearchJob: Job? = null

    // --- Speech-to-text state ---
    var isListening by mutableStateOf(false)

    // --- Translation state ---
    var translatedLyricsLines by mutableStateOf<List<String?>>(emptyList())
    var translatedPlainLyrics by mutableStateOf<String?>(null)
    var isTranslationEnabled by mutableStateOf(false)
    var isTranslating by mutableStateOf(false)
    var detectedLyricsLangCode by mutableStateOf<String?>(null)
    private var translationJob: Job? = null

    // Fix #7: Lock extractor per video (Job cancellation)
    private var playbackJob: Job? = null

    fun loadRelatedSongs(item: MusicItem) {
        viewModelScope.launch {
            val related = MusicRepository.getRelatedSongs(item.url)
            if (related.isNotEmpty()) {
                relatedSongs = related
            }
        }
    }
    
    // Fix #5, #6, #7: Atomic stream resolution with retry
    fun resolveStream(item: MusicItem, onResult: (String?) -> Unit) {
        playbackJob?.cancel() // Cancel previous resolution if user clicks fast
        playbackJob = viewModelScope.launch {
            isLoadingPlayer = true
            
            // Step 0: Check local download first
            val localUrl = MusicRepository.getLocalStreamUrl(context, item.url)
            if (localUrl != null) {
                android.util.Log.d("MusicViewModel", "Playing from local file: $localUrl")
                isLoadingPlayer = false
                onResult(localUrl)
                return@launch
            }
            android.util.Log.d("MusicViewModel", "No local file found for ${item.url}, trying network...")

            // Step 1: Try standard resolution (uses cache if available)
            var url = MusicRepository.getStreamUrl(item.url, force = false)
            
            // Step 2: If failed, try FORCE resolution (Fix #6)
            if (url == null) {
                 delay(300) // Small breather
                 url = MusicRepository.getStreamUrl(item.url, force = true)
            }
            
            isLoadingPlayer = false
            if (isActive) { // Only return result if job wasn't cancelled
                onResult(url)
            }
        }
    }

    private var lastFetchedDuration: Long = 0

    fun fetchLyrics(item: MusicItem, durationSecs: Long) {
        // Only return if it's the same URL AND we already have a valid duration-based fetch
        if (currentLyricsUrl == item.url && (lastFetchedDuration > 0 || durationSecs == 0L)) return 
        
        currentLyricsUrl = item.url
        lastFetchedDuration = durationSecs
        lyricsResponse = null
        syncedLyricsLines = emptyList()
        currentLyricIndex = -1
        lyricsLoading = true
        showLyricsSelector = false
        lyricsSearchQuery = ""
        lyricsSearchResults = emptyList()
        // Reset translation state on song change
        translatedLyricsLines = emptyList()
        translatedPlainLyrics = null
        isTranslationEnabled = false
        isTranslating = false
        detectedLyricsLangCode = null
        translationJob?.cancel()
        TranslationEngine.resetSession()
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch(Dispatchers.IO) {
            val result = MusicRepository.getLyrics(
                title = item.title,
                artist = item.uploader,
                durationSecs = durationSecs,
                videoUrl = item.url
            )
            lyricsResponse = result
            syncedLyricsLines = result?.syncedLines ?: emptyList()
            lyricsLoading = false

            // Use local language detection for translation toggle visibility
            // LyricsEngine.detectLanguage() already ran during buildResponse()
            val detectedLanguages = result?.languages ?: listOf("english")
            detectedLyricsLangCode = if (detectedLanguages.any { it != "english" }) {
                detectedLanguages.firstOrNull { it != "english" } ?: "english"
            } else {
                "english"
            }
        }
    }

    fun updateLyricsPosition(positionMs: Long) {
        val index = LyricsEngine.getCurrentLyricLine(syncedLyricsLines, positionMs)
        if (index != currentLyricIndex) {
            currentLyricIndex = index
        }
    }

    /**
     * Switches to a different lyrics candidate from the available alternatives.
     * Also saves the preference so it persists across sessions.
     */
    fun selectAlternativeLyrics(result: LyricsResult) {
        val parsedLines = LyricsEngine.parseSyncedLyrics(result.syncedLyrics)
        syncedLyricsLines = parsedLines
        currentLyricIndex = -1
        showLyricsSelector = false
        lyricsSearchQuery = ""
        lyricsSearchResults = emptyList()

        // Save preference for this song
        currentLyricsUrl?.let { url ->
            MusicRepository.saveLyricsPreference(url, result.id)
        }

        // Update the lyricsResponse to reflect the new selection
        lyricsResponse = lyricsResponse?.copy(
            result = result,
            plainLyrics = result.plainLyrics,
            syncedLines = parsedLines.takeIf { it.isNotEmpty() },
            lyricsStatus = com.example.musicpiped.data.LyricsStatus(
                hasPlain = result.plainLyrics != null,
                hasSynced = result.syncedLyrics != null,
                isInstrumental = result.instrumental
            )
        )
    }

    /**
     * Manual lyrics search by custom query.
     */
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

    fun clearLyricsSearch() {
        lyricsSearchQuery = ""
        lyricsSearchResults = emptyList()
        lyricsSearching = false
        lyricsSearchJob?.cancel()
    }

    /**
     * Translates the currently loaded lyrics (synced or plain) to English.
     */
    fun translateCurrentLyrics() {
        translationJob?.cancel()
        isTranslating = true
        translationJob = viewModelScope.launch(Dispatchers.IO) {
            val sourceLang = "auto"

            if (syncedLyricsLines.isNotEmpty()) {
                // Translate synced lyrics line by line
                val translated = TranslationEngine.translateLines(syncedLyricsLines, sourceLang)
                translatedLyricsLines = translated
            } else {
                // Translate plain lyrics
                val plain = lyricsResponse?.plainLyrics
                if (!plain.isNullOrBlank()) {
                    translatedPlainLyrics = TranslationEngine.translatePlainLyrics(plain, sourceLang)
                }
            }

            isTranslating = false
        }
    }

    /**
     * Toggles translation on/off. Fetches translations on first enable.
     */
    fun toggleTranslation(enabled: Boolean) {
        isTranslationEnabled = enabled
        if (enabled && translatedLyricsLines.isEmpty() && translatedPlainLyrics == null) {
            translateCurrentLyrics()
        }
    }

    fun getNextAutoplayItem(): MusicItem? {
        if (!autoplayEnabled) return null
        
        // 1. Filter out already recent played songs to avoid loops
        val candidates = relatedSongs.filter { candidate ->
            recentlyPlayed.none { it.title == candidate.title }
        }
        
        // 2. Prioritize Songs over Playlists
        val bestMatch = candidates.firstOrNull { it.type == MusicItemType.SONG }
            ?: candidates.firstOrNull()
            
        // 3. Fallback: If all suggestions are in history, just pick the first suggestion (loop > silence)
        return bestMatch ?: relatedSongs.firstOrNull()
    }

    private var debounceJob: Job? = null

    fun addToRecentlyPlayed(item: MusicItem) {
        val currentList = recentlyPlayed.toMutableList()
        currentList.removeAll { it.title == item.title }
        currentList.add(0, item)
        recentlyPlayed = currentList.take(20)
        
        // Record for pattern analysis
        MusicRepository.recordListen(item)
        // Persist history
        MusicRepository.saveHistory(recentlyPlayed)
    }

    init {
        // Fix #1 & #2: Allow first frame to render before loading content
        viewModelScope.launch {
            delay(150) // Small breather for UI thread
            
            launch(Dispatchers.IO) { 
                MusicRepository.warmUp() 
            }
            
            loadHomeContent()
            observeDownloads()
        }
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            MusicRepository.getAllDownloads(context).collectLatest { downloads ->
                downloadedSongs = downloads
            }
        }
    }

    fun startProgressObserver(url: String) {
        viewModelScope.launch {
            WorkManager.getInstance(context)
                .getWorkInfosByTagFlow("download_$url")
                .collectLatest { workInfos ->
                    val workInfo = workInfos.firstOrNull()
                    if (workInfo != null) {
                        val progress = workInfo.progress.getInt("progress", 0)
                        if (progress > 0) {
                            downloadProgress[url] = progress
                        }
                        
                        if (workInfo.state.isFinished) {
                            downloadProgress.remove(url)
                        }
                    }
                }
        }
    }

    fun downloadSong(item: MusicItem) {
        MusicRepository.downloadSong(context, item)
        startProgressObserver(item.url)
    }

    fun deleteDownload(url: String) {
        viewModelScope.launch {
            MusicRepository.deleteDownload(context, url)
        }
    }

    fun toggleFloatingPlayer(enabled: Boolean) {
        isFloatingEnabled = enabled
        MusicRepository.isFloatingPlayerEnabled = enabled
    }

    fun toggleBackgroundPlayback(enabled: Boolean) {
        isBackgroundPlaybackEnabled = enabled
        MusicRepository.isBackgroundPlaybackEnabled = enabled
    }

    fun updateThemeMode(newTheme: String) {
        themeMode = newTheme
        MusicRepository.themeMode = newTheme
    }

    var isLiquidScrollEnabled by mutableStateOf(MusicRepository.isLiquidScrollEnabled)

    fun toggleLiquidScroll(enabled: Boolean) {
        isLiquidScrollEnabled = enabled
        MusicRepository.isLiquidScrollEnabled = enabled
    }


    fun loadHomeContent() {
        viewModelScope.launch {
            isLoading = true
            
            // Restore history if empty
            if (recentlyPlayed.isEmpty()) {
                recentlyPlayed = MusicRepository.loadHistory()
            }

            // Get personalized content based on patterns
            val recommendations = MusicRepository.getPatternSuggestions()
            searchResults = recommendations
            
            // Set title based on whether we probably got personalized results
            homeTitle = if (recentlyPlayed.isNotEmpty()) "Recommended For You" else "Trending Now"
            
            // Populate all YTM-style shelves
            val recommendationsList = recommendations
            quickPicksItems = recommendationsList.take(8)
            quickPicksRows = quickPicksItems.chunked(2)
            
            mixedForYouItems = recommendationsList.drop(8).take(6)
            newReleasesItems = recommendationsList.drop(14).take(8)
            
            trendingNowItems = recommendationsList.drop(22).take(8)
            trendingNowRows = trendingNowItems.chunked(2)
            
            // These will be populated when user selects corresponding chips
            // or we can search for them
            listenAgainItems = recentlyPlayed.take(10)
            
            // Load mood-based content
            loadMoodContent()
            
            isSearching = true 
            isLoading = false
        }
    }
    
    fun loadExploreContent() {
        if (exploreTrendingItems.isNotEmpty() && selectedExploreCategory == null) return // Already loaded
        
        viewModelScope.launch {
            isLoadingExplore = true
            try {
                exploreTrendingItems = MusicRepository.getTrendingMusic()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingExplore = false
            }
        }
    }

    fun onExploreCategorySelected(category: String) {
        if (selectedExploreCategory == category) {
            selectedExploreCategory = null
            exploreTrendingItems = emptyList() // clear it to force loading default
            loadExploreContent()
            return
        }
        
        selectedExploreCategory = category
        viewModelScope.launch {
            isLoadingExplore = true
            try {
                val query = when (category) {
                    "New releases" -> "new music releases official"
                    "Charts" -> "top music charts global official"
                    "Moods & genres" -> "moods and genres music mix"
                    "Party" -> "party playlist official"
                    "Romance" -> "romantic love songs playlist"
                    "Sleep" -> "sleep relaxing music"
                    "Workout" -> "workout gym motivation playlist"
                    else -> "$category playlist"
                }
                exploreTrendingItems = MusicRepository.searchMusic(query, maintainSession = false)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingExplore = false
            }
        }
    }

    private fun loadMoodContent() {
        viewModelScope.launch {
            // Load chill/relax music - use maintainSession=false to keep main extractor
            val chillResults = MusicRepository.searchMusic("chill lofi music", maintainSession = false)
            chillItems = chillResults.take(8)
            
            // Load workout music
            val workoutResults = MusicRepository.searchMusic("workout gym music", maintainSession = false)
            workoutItems = workoutResults.take(8)
            
            // Load focus music
            val focusResults = MusicRepository.searchMusic("focus study music", maintainSession = false)
            focusItems = focusResults.take(8)
        }
    }
    
    fun onChipSelected(chip: String) {
        selectedChip = chip
        if (chip != "All") {
            viewModelScope.launch {
                isLoading = true
                val query = when (chip) {
                    "Relax" -> "relaxing chill music"
                    "Energize" -> "energetic upbeat music"
                    "Workout" -> "workout gym motivation music"
                    "Focus" -> "focus concentration study music"
                    "Commute" -> "driving road trip music"
                    else -> "trending music"
                }
                // Maintain session here because this IS the main content now
                val results = MusicRepository.searchMusic(query) 
                searchResults = results
                quickPicksItems = results.take(8)
                quickPicksRows = quickPicksItems.chunked(2)
                mixedForYouItems = results.drop(8).take(6)
                isSearching = true
                isLoading = false
            }
        } else {
            loadHomeContent()
        }
    }

    fun onQueryChanged(newQuery: String) {
        searchQuery = newQuery
        isSearching = false // Show suggestions mode

        debounceJob?.cancel()
        if (newQuery.isBlank()) {
            suggestions = emptyList()
            return
        }

        debounceJob = viewModelScope.launch {
            delay(300) // 300ms delay
            suggestions = MusicRepository.getSuggestions(newQuery)
        }
    }

    fun performSearch(query: String) {
        if (query.isBlank()) return
        
        searchQuery = query
        isSearching = true // Show results mode
        isLoading = true
        suggestions = emptyList()
        
        viewModelScope.launch {
            searchResults = MusicRepository.searchMusic(query)
            isLoading = false
        }
    }

    fun loadNextPage() {
        // Prevent multiple calls or calling when not searching
        if (isLoadingMore || !isSearching || searchResults.isEmpty()) return
        
        isLoadingMore = true
        viewModelScope.launch {
            try {
                val newItems = MusicRepository.loadMoreResults()
                if (newItems.isNotEmpty()) {
                    // Append new items to existing list, filtering duplicates to avoid LazyColumn crashes
                    val currentUrls = searchResults.map { it.url }.toSet()
                    val distinctNewItems = newItems.filter { it.url !in currentUrls }
                    
                    if (distinctNewItems.isNotEmpty()) {
                        searchResults = searchResults + distinctNewItems
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingMore = false
            }
        }
    }
}
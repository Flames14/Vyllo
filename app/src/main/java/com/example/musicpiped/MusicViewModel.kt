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
    
    // YTM-style categorized home content
    var listenAgainItems by mutableStateOf<List<MusicItem>>(emptyList())
    var quickPicksItems by mutableStateOf<List<MusicItem>>(emptyList())
    var mixedForYouItems by mutableStateOf<List<MusicItem>>(emptyList())
    var newReleasesItems by mutableStateOf<List<MusicItem>>(emptyList())
    var trendingNowItems by mutableStateOf<List<MusicItem>>(emptyList())
    var chillItems by mutableStateOf<List<MusicItem>>(emptyList())
    var workoutItems by mutableStateOf<List<MusicItem>>(emptyList())
    var focusItems by mutableStateOf<List<MusicItem>>(emptyList())
    var selectedChip by mutableStateOf("All")
    var selectedNavTab by mutableStateOf(0) // 0=Home, 1=Library
    
    var homeTitle by mutableStateOf("Recommended For You")
    var isLoading by mutableStateOf(false)
    var isLoadingMore by mutableStateOf(false)
    var isSearching by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    
    // Fix #8: Show buffering state immediately
    var isLoadingPlayer by mutableStateOf(false)

    // Download states
    var downloadedSongs by mutableStateOf<List<DownloadEntity>>(emptyList())
    var downloadProgress = mutableStateMapOf<String, Int>()

    // Playback state lifted to ViewModel for optimistic updates
    var currentPlayingItem by mutableStateOf<MusicItem?>(null)
    var relatedSongs by mutableStateOf<List<MusicItem>>(emptyList())
    var autoplayEnabled by mutableStateOf(true)
    
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
            delay(50) // Minimal delay - just let first frame render
            
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
            quickPicksItems = recommendations.take(8)
            mixedForYouItems = recommendations.drop(8).take(6)
            newReleasesItems = recommendations.drop(14).take(8)
            trendingNowItems = recommendations.drop(22).take(8)
            
            // These will be populated when user selects corresponding chips
            // or we can search for them
            listenAgainItems = recentlyPlayed.take(10)
            
            // Load mood-based content
            loadMoodContent()
            
            isSearching = true 
            isLoading = false
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
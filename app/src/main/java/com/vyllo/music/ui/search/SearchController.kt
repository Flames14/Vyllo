package com.vyllo.music.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.data.IMusicRepository
import com.vyllo.music.domain.usecase.SearchMusicUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchController(
    private val scope: CoroutineScope,
    private val searchMusicUseCase: SearchMusicUseCase,
    private val repository: IMusicRepository
) {
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<MusicItem>>(emptyList())
    var suggestions by mutableStateOf<List<String>>(emptyList())
    var isSearching by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var isLoadingMore by mutableStateOf(false)
    var isListening by mutableStateOf(false)

    private var searchJob: Job? = null
    private var suggestionJob: Job? = null

    fun onQueryChanged(newQuery: String) {
        searchQuery = newQuery
        if (newQuery.isBlank()) {
            suggestions = emptyList()
            isSearching = false
            return
        }

        suggestionJob?.cancel()
        suggestionJob = scope.launch {
            delay(300)
            suggestions = repository.getSuggestions(newQuery)
        }
    }

    fun performSearch(query: String) {
        searchQuery = query
        suggestions = emptyList()
        isSearching = true
        isLoading = true

        searchJob?.cancel()
        searchJob = scope.launch {
            repository.saveSearchQuery(query)
            searchResults = searchMusicUseCase(query)
            isLoading = false
        }
    }

    fun loadNextPage() {
        if (isLoadingMore || !isSearching) return
        isLoadingMore = true
        scope.launch {
            val nextItems = repository.loadMoreResults()
            if (nextItems.isNotEmpty()) {
                searchResults = searchResults + nextItems
            }
            isLoadingMore = false
        }
    }
}

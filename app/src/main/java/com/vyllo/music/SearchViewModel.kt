package com.vyllo.music

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vyllo.music.core.security.InputSanitizer
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.IMusicRepository
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.usecase.SearchMusicUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SearchViewModel"

/**
 * Search ViewModel with Security Hardening
 * 
 * Features:
 * - Input sanitization to prevent injection attacks
 * - Input validation before search execution
 * - Secure logging
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: IMusicRepository,
    private val searchMusicUseCase: SearchMusicUseCase
) : ViewModel() {

    var searchQuery by mutableStateOf("")

    var searchResults by mutableStateOf<List<MusicItem>>(emptyList())
        private set

    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set

    var searchHistory by mutableStateOf<List<String>>(emptyList())
        private set

    var isSearching by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    var isListening by mutableStateOf(false)

    private var searchJob: Job? = null
    private var suggestionJob: Job? = null

    init {
        loadSearchHistory()
    }

    private fun loadSearchHistory() {
        searchHistory = repository.loadSearchHistory()
    }

    fun onQueryChanged(newQuery: String) {
        searchQuery = newQuery
        if (newQuery.isBlank()) {
            suggestions = emptyList()
            isSearching = false
            return
        }

        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            delay(300) // Debounce for suggestions
            suggestions = repository.getSuggestions(newQuery)
        }
    }

    fun performSearch(rawQuery: String) {
        // Security: Validate and sanitize input
        val validationResult = InputSanitizer.validateInput(rawQuery, InputSanitizer.InputType.SEARCH)
        
        if (!validationResult.isValid) {
            SecureLogger.w(TAG, "Invalid search input blocked: ${validationResult.errorMessage}")
            searchQuery = ""
            searchResults = emptyList()
            isSearching = false
            return
        }

        // Security: Sanitize the query
        val sanitizedQuery = InputSanitizer.sanitizeSearchQuery(rawQuery)
        
        // Additional security check for injection attempts
        if (InputSanitizer.isPotentialSqlInjection(rawQuery) || 
            InputSanitizer.isPotentialCommandInjection(rawQuery)) {
            SecureLogger.security(TAG, "Potential injection attempt blocked: $rawQuery")
            searchQuery = ""
            searchResults = emptyList()
            isSearching = false
            return
        }

        searchQuery = sanitizedQuery
        suggestions = emptyList()
        isSearching = true
        isLoading = true

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            repository.saveSearchQuery(sanitizedQuery)
            searchResults = searchMusicUseCase(sanitizedQuery)
            isLoading = false
            loadSearchHistory()
        }
    }

    fun clearSearchHistory() {
        repository.clearSearchHistory()
        loadSearchHistory()
    }

    fun loadNextPage() {
        if (isLoadingMore || !isSearching) return
        isLoadingMore = true
        viewModelScope.launch {
            try {
                val nextItems = repository.loadMoreResults()
                if (nextItems.isNotEmpty()) {
                    searchResults = searchResults + nextItems
                }
            } catch (e: Exception) {
                SecureLogger.e(TAG, "Load more failed: ${e.message}")
            } finally {
                isLoadingMore = false
            }
        }
    }
}

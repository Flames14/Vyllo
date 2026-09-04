package com.vyllo.music

import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.data.IMusicRepository
import com.vyllo.music.data.LyricsEngine
import com.vyllo.music.data.TranslationEngine
import com.vyllo.music.domain.model.LyricsResponse
import com.vyllo.music.domain.model.LyricsResult
import com.vyllo.music.domain.model.LyricsStatus
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.usecase.GetLyricsUseCase
import com.vyllo.music.domain.usecase.TranslateLyricsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

class PlayerLyricsCoordinator @Inject constructor(
    private val repository: IMusicRepository,
    private val lyricsEngine: LyricsEngine,
    private val getLyricsUseCase: GetLyricsUseCase,
    private val translateLyricsUseCase: TranslateLyricsUseCase
) {
    private var currentLyricsUrl: String? = null
    private var lastFetchedDuration: Long = 0
    private var lyricsJob: Job? = null
    private var lyricsSearchJob: Job? = null
    private var translationJob: Job? = null

    fun fetchLyrics(
        scope: CoroutineScope,
        currentState: () -> PlayerUiState,
        updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
        item: MusicItem,
        durationSecs: Long,
        onTranslateCurrentLyrics: () -> Unit
    ) {
        if (currentLyricsUrl == item.url && lastFetchedDuration == durationSecs && durationSecs > 0) return

        currentLyricsUrl = item.url
        lastFetchedDuration = durationSecs
        resetLyricsState(updateState)

        lyricsJob?.cancel()
        lyricsJob = scope.launch(Dispatchers.IO) {
            try {
                val result = getLyricsUseCase(item, durationSecs)
                updateState {
                    it.copy(
                        lyricsResponse = result,
                        syncedLyricsLines = result?.syncedLines ?: emptyList(),
                        lyricsLoading = false,
                        detectedLyricsLangCode = detectLangCode(result?.languages)
                    )
                }

                if (currentState().isTranslationEnabled) {
                    onTranslateCurrentLyrics()
                }
            } catch (e: Exception) {
                SecureLogger.e("PlayerViewModel", "Lyrics fetch error: ${e.message}", e)
                updateState {
                    it.copy(
                        lyricsResponse = LyricsResponse(
                            success = false,
                            strategy = "ERROR",
                            error = e.message ?: "Failed to load lyrics"
                        ),
                        syncedLyricsLines = emptyList(),
                        lyricsLoading = false
                    )
                }
            }
        }
    }

    fun searchForLyrics(
        scope: CoroutineScope,
        updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
        query: String
    ) {
        if (query.isBlank()) return
        updateState { it.copy(lyricsSearching = true) }
        lyricsSearchJob?.cancel()
        lyricsSearchJob = scope.launch(Dispatchers.IO) {
            val results = lyricsEngine.searchLyrics(query)
            updateState { it.copy(lyricsSearchResults = results, lyricsSearching = false) }
        }
    }

    fun clearLyricsSearchResults(
        updateState: ((PlayerUiState) -> PlayerUiState) -> Unit
    ) {
        updateState {
            it.copy(
                lyricsSearchQuery = "",
                lyricsSearchResults = emptyList(),
                lyricsSearching = false
            )
        }
        lyricsSearchJob?.cancel()
    }

    fun translateCurrentLyrics(
        scope: CoroutineScope,
        currentState: () -> PlayerUiState,
        updateState: ((PlayerUiState) -> PlayerUiState) -> Unit
    ) {
        translationJob?.cancel()
        updateState { it.copy(isTranslating = true) }
        translationJob = scope.launch {
            val state = currentState()
            if (state.syncedLyricsLines.isNotEmpty()) {
                val translated = translateLyricsUseCase.translateLines(state.syncedLyricsLines)
                updateState { it.copy(translatedLyricsLines = translated, isTranslating = false) }
            } else {
                val plain = state.lyricsResponse?.plainLyrics
                if (!plain.isNullOrBlank()) {
                    val translated = translateLyricsUseCase.translatePlain(plain)
                    updateState { it.copy(translatedPlainLyrics = translated, isTranslating = false) }
                } else {
                    updateState { it.copy(isTranslating = false) }
                }
            }
        }
    }

    fun selectAlternativeLyrics(
        updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
        result: LyricsResult
    ) {
        val parsedLines = LyricsEngine.parseSyncedLyrics(result.syncedLyrics)
        updateState { state ->
            state.copy(
                syncedLyricsLines = parsedLines,
                currentLyricIndex = -1,
                showLyricsSelector = false,
                lyricsSearchQuery = "",
                lyricsSearchResults = emptyList(),
                lyricsResponse = state.lyricsResponse?.copy(
                    result = result,
                    plainLyrics = result.plainLyrics,
                    syncedLines = parsedLines.takeIf { it.isNotEmpty() },
                    lyricsStatus = LyricsStatus(
                        hasPlain = result.plainLyrics != null,
                        hasSynced = result.syncedLyrics != null,
                        isInstrumental = result.instrumental
                    )
                )
            )
        }

        currentLyricsUrl?.let { repository.saveLyricsPreference(it, result.id) }
    }

    private fun resetLyricsState(
        updateState: ((PlayerUiState) -> PlayerUiState) -> Unit
    ) {
        updateState {
            it.copy(
                lyricsResponse = null,
                syncedLyricsLines = emptyList(),
                currentLyricIndex = -1,
                lyricsLoading = true,
                showLyricsSelector = false,
                lyricsSearchQuery = "",
                lyricsSearchResults = emptyList(),
                translatedLyricsLines = emptyList(),
                translatedPlainLyrics = null,
                isTranslating = false,
                detectedLyricsLangCode = null
            )
        }
        translationJob?.cancel()
        TranslationEngine.resetSession()
    }

    private fun detectLangCode(languages: List<String>?): String? {
        if (languages.isNullOrEmpty()) return null
        return if (languages.any { it != "english" }) {
            languages.firstOrNull { it != "english" } ?: "english"
        } else {
            "english"
        }
    }
}

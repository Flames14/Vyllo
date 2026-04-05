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
        updateState: (PlayerUiState) -> Unit,
        item: MusicItem,
        durationSecs: Long,
        onTranslateCurrentLyrics: () -> Unit
    ) {
        if (currentLyricsUrl == item.url && lastFetchedDuration == durationSecs && durationSecs > 0) return

        currentLyricsUrl = item.url
        lastFetchedDuration = durationSecs
        resetLyricsState(currentState, updateState)

        lyricsJob?.cancel()
        lyricsJob = scope.launch(Dispatchers.IO) {
            try {
                val result = getLyricsUseCase(item, durationSecs)
                updateState(
                    currentState().copy(
                        lyricsResponse = result,
                        syncedLyricsLines = result?.syncedLines ?: emptyList(),
                        lyricsLoading = false,
                        detectedLyricsLangCode = detectLangCode(result?.languages)
                    )
                )

                if (currentState().isTranslationEnabled) {
                    onTranslateCurrentLyrics()
                }
            } catch (e: Exception) {
                SecureLogger.e("PlayerViewModel", "Lyrics fetch error: ${e.message}", e)
                updateState(
                    currentState().copy(
                        lyricsResponse = LyricsResponse(
                            success = false,
                            strategy = "ERROR",
                            error = e.message ?: "Failed to load lyrics"
                        ),
                        syncedLyricsLines = emptyList(),
                        lyricsLoading = false
                    )
                )
            }
        }
    }

    fun searchForLyrics(
        scope: CoroutineScope,
        currentState: () -> PlayerUiState,
        updateState: (PlayerUiState) -> Unit,
        query: String
    ) {
        if (query.isBlank()) return
        updateState(currentState().copy(lyricsSearching = true))
        lyricsSearchJob?.cancel()
        lyricsSearchJob = scope.launch(Dispatchers.IO) {
            val results = lyricsEngine.searchLyrics(query)
            updateState(currentState().copy(lyricsSearchResults = results, lyricsSearching = false))
        }
    }

    fun clearLyricsSearchResults(
        currentState: () -> PlayerUiState,
        updateState: (PlayerUiState) -> Unit
    ) {
        updateState(
            currentState().copy(
                lyricsSearchQuery = "",
                lyricsSearchResults = emptyList(),
                lyricsSearching = false
            )
        )
        lyricsSearchJob?.cancel()
    }

    fun translateCurrentLyrics(
        scope: CoroutineScope,
        currentState: () -> PlayerUiState,
        updateState: (PlayerUiState) -> Unit
    ) {
        translationJob?.cancel()
        updateState(currentState().copy(isTranslating = true))
        translationJob = scope.launch {
            val state = currentState()
            if (state.syncedLyricsLines.isNotEmpty()) {
                val translated = translateLyricsUseCase.translateLines(state.syncedLyricsLines)
                updateState(currentState().copy(translatedLyricsLines = translated, isTranslating = false))
            } else {
                val plain = state.lyricsResponse?.plainLyrics
                if (!plain.isNullOrBlank()) {
                    val translated = translateLyricsUseCase.translatePlain(plain)
                    updateState(currentState().copy(translatedPlainLyrics = translated, isTranslating = false))
                } else {
                    updateState(currentState().copy(isTranslating = false))
                }
            }
        }
    }

    fun selectAlternativeLyrics(
        currentState: () -> PlayerUiState,
        updateState: (PlayerUiState) -> Unit,
        result: LyricsResult
    ) {
        val parsedLines = LyricsEngine.parseSyncedLyrics(result.syncedLyrics)
        val state = currentState()
        updateState(
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
        )

        currentLyricsUrl?.let { repository.saveLyricsPreference(it, result.id) }
    }

    private fun resetLyricsState(
        currentState: () -> PlayerUiState,
        updateState: (PlayerUiState) -> Unit
    ) {
        val state = currentState()
        updateState(
            state.copy(
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
        )
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

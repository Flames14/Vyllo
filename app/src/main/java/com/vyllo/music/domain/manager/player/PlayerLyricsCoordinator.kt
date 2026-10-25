package com.vyllo.music.domain.manager.player

import com.vyllo.music.PlayerUiState
import com.vyllo.music.core.security.SecureLogger
import com.vyllo.music.domain.repository.IMusicRepository
import com.vyllo.music.domain.repository.LyricsSearchService
import com.vyllo.music.domain.repository.LyricsTranslator
import com.vyllo.music.domain.model.LyricsResponse
import com.vyllo.music.domain.model.LyricsResult
import com.vyllo.music.domain.model.LyricsStatus
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.usecase.GetLyricsUseCase
import com.vyllo.music.domain.usecase.TranslateLyricsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

class PlayerLyricsCoordinator @Inject constructor(
    private val repository: IMusicRepository,
    private val searchService: LyricsSearchService,
    private val translator: LyricsTranslator,
    private val getLyricsUseCase: GetLyricsUseCase,
    private val translateLyricsUseCase: TranslateLyricsUseCase
) {
    private var currentLyricsUrl: String? = null
    private var lastFetchedDuration: Long = 0
    private var lyricsJob: Job? = null
    private var lyricsSearchJob: Job? = null
    private var translationJob: Job? = null
    private var romanizationJob: Job? = null

    fun fetchLyrics(
        scope: CoroutineScope,
        currentState: () -> PlayerUiState,
        updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
        item: MusicItem,
        durationSecs: Long,
        onTranslateCurrentLyrics: () -> Unit,
        onRomanizeCurrentLyrics: () -> Unit = {}
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
                if (currentState().isRomanizationEnabled) {
                    onRomanizeCurrentLyrics()
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
            val results = searchService.searchLyrics(query)
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
            try {
                val state = currentState()
                val targetLang = translator.preferredTargetLanguage()
                if (state.syncedLyricsLines.isNotEmpty()) {
                    val translated = translateLyricsUseCase.translateLines(state.syncedLyricsLines, targetLang)
                    updateState { it.copy(translatedLyricsLines = translated, isTranslating = false) }
                } else {
                    val plain = state.lyricsResponse?.plainLyrics
                    if (!plain.isNullOrBlank()) {
                        val translated = translateLyricsUseCase.translatePlain(plain, targetLang)
                        updateState { it.copy(translatedPlainLyrics = translated, isTranslating = false) }
                    } else {
                        updateState { it.copy(isTranslating = false) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                SecureLogger.e("PlayerViewModel", "Lyrics translation failed: ${error.message}", error)
                updateState { it.copy(isTranslating = false) }
            }
        }
    }

    fun romanizeCurrentLyrics(
        scope: CoroutineScope,
        currentState: () -> PlayerUiState,
        updateState: ((PlayerUiState) -> PlayerUiState) -> Unit
    ) {
        romanizationJob?.cancel()
        updateState { it.copy(isRomanizing = true) }
        romanizationJob = scope.launch {
            try {
                val state = currentState()
                if (state.syncedLyricsLines.isNotEmpty()) {
                    val romanized = translator.romanizeLines(state.syncedLyricsLines.map { it.content })
                    updateState { it.copy(romanizedLyricsLines = romanized.orEmpty(), isRomanizing = false) }
                } else {
                    val plain = state.lyricsResponse?.plainLyrics
                    if (!plain.isNullOrBlank()) {
                        val romanized = translator.romanizePlain(plain)
                        updateState { it.copy(romanizedPlainLyrics = romanized, isRomanizing = false) }
                    } else {
                        updateState { it.copy(isRomanizing = false) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                SecureLogger.e("PlayerViewModel", "Lyrics romanization failed: ${error.message}", error)
                updateState { it.copy(isRomanizing = false) }
            }
        }
    }

    fun toggleRomanization(
        scope: CoroutineScope,
        updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
        enabled: Boolean,
        refresh: () -> Unit
    ) {
        translator.setRomanizationEnabled(enabled)
        updateState {
            it.copy(
                isRomanizationEnabled = enabled,
                romanizedLyricsLines = if (enabled) it.romanizedLyricsLines else emptyList(),
                romanizedPlainLyrics = if (enabled) it.romanizedPlainLyrics else null
            )
        }
        romanizationJob?.cancel()
        if (enabled) refresh() else updateState { it.copy(isRomanizing = false) }
    }

    fun selectAlternativeLyrics(
        updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
        result: LyricsResult
    ) {
        val parsedLines = searchService.parseSyncedLyrics(result.syncedLyrics)
        updateState { state ->
            state.copy(
                syncedLyricsLines = parsedLines,
                currentLyricIndex = -1,
                showLyricsSelector = false,
                lyricsSearchQuery = "",
                lyricsSearchResults = emptyList(),
                romanizedLyricsLines = emptyList(),
                romanizedPlainLyrics = null,
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
                romanizedLyricsLines = emptyList(),
                romanizedPlainLyrics = null,
                isRomanizationEnabled = translator.romanizationEnabled(),
                isRomanizing = false,
                detectedLyricsLangCode = null
            )
        }
        translationJob?.cancel()
        romanizationJob?.cancel()
        translator.resetSession()
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

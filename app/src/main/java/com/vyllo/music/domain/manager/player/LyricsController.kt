package com.vyllo.music.domain.manager.player

import com.vyllo.music.PlayerUiState
import com.vyllo.music.domain.model.LyricPositioning
import com.vyllo.music.domain.model.LyricsResult
import com.vyllo.music.domain.model.MusicItem
import kotlinx.coroutines.CoroutineScope

/**
 * Owns lyrics fetch / position / offset / select / search / translate / romanize / toggle
 * logic extracted from PlayerViewModel. Delegates to PlayerLyricsCoordinator.
 */
class LyricsController(
    private val lyricsCoordinator: PlayerLyricsCoordinator,
    private val getState: () -> PlayerUiState,
    private val updateState: (((PlayerUiState) -> PlayerUiState)) -> Unit,
    private val scopeProvider: () -> CoroutineScope,
    private val getLyricIndex: () -> Int = { getState().currentLyricIndex },
    private val setLyricIndex: (Int) -> Unit = {}
) {
    fun fetchLyrics(item: MusicItem, durationSecs: Long) {
        lyricsCoordinator.fetchLyrics(
            scope = scopeProvider(),
            currentState = { getState() },
            updateState = { transform -> updateState(transform) },
            item = item,
            durationSecs = durationSecs,
            onTranslateCurrentLyrics = ::translateCurrentLyrics,
            onRomanizeCurrentLyrics = ::romanizeCurrentLyrics
        )
    }

    fun updatePosition(positionMs: Long) {
        val state = getState()
        val index = LyricPositioning.getCurrentLine(state.syncedLyricsLines, positionMs + state.lyricsOffsetMs)
        if (index != getLyricIndex()) {
            setLyricIndex(index)
        }
    }

    fun adjustOffset(deltaMs: Long) {
        updateState { it.copy(lyricsOffsetMs = it.lyricsOffsetMs + deltaMs) }
    }

    fun selectAlternative(result: LyricsResult) {
        lyricsCoordinator.selectAlternativeLyrics(
            updateState = { transform -> updateState(transform) },
            result = result
        )
    }

    fun search(query: String) {
        lyricsCoordinator.searchForLyrics(
            scope = scopeProvider(),
            updateState = { transform -> updateState(transform) },
            query = query
        )
    }

    fun clearSearchResults() {
        lyricsCoordinator.clearLyricsSearchResults(
            updateState = { transform -> updateState(transform) }
        )
    }

    fun translateCurrentLyrics() {
        lyricsCoordinator.translateCurrentLyrics(
            scope = scopeProvider(),
            currentState = { getState() },
            updateState = { transform -> updateState(transform) }
        )
    }

    fun romanizeCurrentLyrics() {
        lyricsCoordinator.romanizeCurrentLyrics(
            scope = scopeProvider(),
            currentState = { getState() },
            updateState = { transform -> updateState(transform) }
        )
    }

    fun toggleTranslation(enabled: Boolean) {
        updateState { it.copy(isTranslationEnabled = enabled) }
        if (enabled) {
            translateCurrentLyrics()
        }
    }

    fun toggleRomanization(enabled: Boolean) {
        lyricsCoordinator.toggleRomanization(
            scope = scopeProvider(),
            updateState = { transform -> updateState(transform) },
            enabled = enabled,
            refresh = ::romanizeCurrentLyrics
        )
    }

    /** Called after the user edits provider/language settings in the dialog. */
    fun onTranslationSettingsChanged() {
        val state = getState()
        updateState {
            it.copy(
                translatedLyricsLines = emptyList(),
                translatedPlainLyrics = null,
                romanizedLyricsLines = emptyList(),
                romanizedPlainLyrics = null,
                isRomanizationEnabled = state.isRomanizationEnabled
            )
        }
        if (state.isTranslationEnabled) translateCurrentLyrics()
        if (state.isRomanizationEnabled) romanizeCurrentLyrics()
    }
}

package com.vyllo.music.domain.manager.player

import com.vyllo.music.PlayerUiState
import com.vyllo.music.domain.model.LyricPositioning
import com.vyllo.music.domain.model.LyricsResult
import com.vyllo.music.domain.model.MusicItem
import kotlinx.coroutines.CoroutineScope

/**
 * Owns lyrics fetch / position / offset / select / search / translate / toggle logic
 * extracted from PlayerViewModel. Delegates to PlayerLyricsCoordinator.
 * No behavior change — verbatim logic moved here.
 */
class LyricsController(
    private val lyricsCoordinator: PlayerLyricsCoordinator,
    private val getState: () -> PlayerUiState,
    private val updateState: (((PlayerUiState) -> PlayerUiState)) -> Unit,
    private val scopeProvider: () -> CoroutineScope
) {
    fun fetchLyrics(item: MusicItem, durationSecs: Long) {
        lyricsCoordinator.fetchLyrics(
            scope = scopeProvider(),
            currentState = { getState() },
            updateState = { transform -> updateState(transform) },
            item = item,
            durationSecs = durationSecs,
            onTranslateCurrentLyrics = ::translateCurrentLyrics
        )
    }

    fun updatePosition(positionMs: Long) {
        val state = getState()
        val index = LyricPositioning.getCurrentLine(state.syncedLyricsLines, positionMs + state.lyricsOffsetMs)
        if (index != state.currentLyricIndex) {
            updateState { it.copy(currentLyricIndex = index) }
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

    fun toggleTranslation(enabled: Boolean) {
        updateState { it.copy(isTranslationEnabled = enabled) }
        if (enabled) {
            translateCurrentLyrics()
        }
    }
}

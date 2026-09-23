package com.vyllo.music.domain.repository

import com.vyllo.music.domain.model.LyricsResult
import com.vyllo.music.domain.model.SyncedLyricLine

/**
 * Lyrics search + LRC parsing. Implemented by the data-layer lyrics engine
 * so the player coordinator stays in domain.
 */
interface LyricsSearchService {
    suspend fun searchLyrics(query: String): List<LyricsResult>
    fun parseSyncedLyrics(lrc: String?): List<SyncedLyricLine>
}

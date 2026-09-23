package com.vyllo.music.data

import com.vyllo.music.domain.model.LyricsResult
import com.vyllo.music.domain.model.SyncedLyricLine
import com.vyllo.music.domain.repository.LyricsSearchService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsSearchAdapter @Inject constructor(
    private val lyricsEngine: LyricsEngine
) : LyricsSearchService {
    override suspend fun searchLyrics(query: String): List<LyricsResult> =
        lyricsEngine.searchLyrics(query)

    override fun parseSyncedLyrics(lrc: String?): List<SyncedLyricLine> =
        LyricsEngine.parseSyncedLyrics(lrc)
}

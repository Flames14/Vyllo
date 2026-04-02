package com.vyllo.music.domain.usecase

import com.vyllo.music.data.IMusicRepository
import com.vyllo.music.domain.model.MusicItem
import javax.inject.Inject

class SearchMusicUseCase @Inject constructor(
    private val repository: IMusicRepository
) {
    suspend operator fun invoke(query: String, maintainSession: Boolean = true): List<MusicItem> {
        if (query.isBlank()) return emptyList()
        return repository.searchMusic(query, maintainSession)
    }
}

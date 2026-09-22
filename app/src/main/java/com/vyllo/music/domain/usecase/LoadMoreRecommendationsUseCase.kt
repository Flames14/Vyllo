package com.vyllo.music.domain.usecase

import com.vyllo.music.domain.repository.IMusicRepository
import com.vyllo.music.domain.model.MusicItem
import javax.inject.Inject

class LoadMoreRecommendationsUseCase @Inject constructor(
    private val repository: IMusicRepository
) {
    suspend operator fun invoke(): List<MusicItem> {
        return repository.loadMoreRecommendations()
    }
}

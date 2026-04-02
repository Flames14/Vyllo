package com.vyllo.music.domain.usecase

import com.vyllo.music.data.IMusicRepository
import com.vyllo.music.domain.model.MusicItem
import javax.inject.Inject

class GetHomeContentUseCase @Inject constructor(
    private val repository: IMusicRepository
) {
    suspend operator fun invoke(): List<MusicItem> {
        return repository.getPatternSuggestions()
    }
}

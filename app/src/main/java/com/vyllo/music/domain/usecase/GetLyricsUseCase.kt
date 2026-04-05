package com.vyllo.music.domain.usecase

import com.vyllo.music.data.IMusicRepository
import com.vyllo.music.domain.model.LyricsResponse
import com.vyllo.music.domain.model.MusicItem
import javax.inject.Inject

class GetLyricsUseCase @Inject constructor(
    private val repository: IMusicRepository
) {
    suspend operator fun invoke(item: MusicItem, durationSecs: Long): LyricsResponse? {
        return repository.getLyrics(
            title = item.title,
            artist = item.uploader,
            duration = durationSecs,
            url = item.url
        )
    }
}

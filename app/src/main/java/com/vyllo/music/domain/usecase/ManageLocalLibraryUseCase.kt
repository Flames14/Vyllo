package com.vyllo.music.domain.usecase

import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.repository.ILocalLibraryRepository
import javax.inject.Inject

class ManageLocalLibraryUseCase @Inject constructor(
    private val repository: ILocalLibraryRepository
) {
    fun observeSongs() = repository.observeSongs()
    suspend fun save(item: MusicItem) = repository.save(item)
    suspend fun updateMetadata(url: String, title: String, artist: String, album: String) =
        repository.updateMetadata(url, title, artist, album)
    suspend fun remove(url: String) = repository.remove(url)
}

package com.vyllo.music.data.repository

import com.vyllo.music.data.download.LocalLibraryDao
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.LocalLibrarySong
import com.vyllo.music.domain.model.toLocalLibrarySong
import com.vyllo.music.domain.repository.ILocalLibraryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLibraryRepository @Inject constructor(
    private val dao: LocalLibraryDao
) : ILocalLibraryRepository {
    override fun observeSongs(): Flow<List<LocalLibrarySong>> = dao.observeSongs()

    override suspend fun save(item: MusicItem): Boolean = dao.insertIfAbsent(item.toLocalLibrarySong()) != -1L

    override suspend fun updateMetadata(url: String, title: String, artist: String, album: String) {
        val current = dao.getByUrl(url) ?: return
        dao.update(
            current.copy(
                title = title.trim().ifBlank { current.title },
                artist = artist.trim(),
                album = album.trim()
            )
        )
    }

    override suspend fun remove(url: String) = dao.deleteByUrl(url)
}

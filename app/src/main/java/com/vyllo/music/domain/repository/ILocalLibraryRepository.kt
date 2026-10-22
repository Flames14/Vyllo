package com.vyllo.music.domain.repository

import com.vyllo.music.domain.model.LocalLibrarySong
import com.vyllo.music.domain.model.MusicItem
import kotlinx.coroutines.flow.Flow

interface ILocalLibraryRepository {
    fun observeSongs(): Flow<List<LocalLibrarySong>>
    suspend fun save(item: MusicItem): Boolean
    suspend fun updateMetadata(url: String, title: String, artist: String, album: String)
    suspend fun remove(url: String)
}

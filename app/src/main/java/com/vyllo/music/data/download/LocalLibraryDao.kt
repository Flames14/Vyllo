package com.vyllo.music.data.download

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.vyllo.music.domain.model.LocalLibrarySong

@Dao
interface LocalLibraryDao {
    @Query("SELECT * FROM local_library_songs ORDER BY addedAt DESC")
    fun observeSongs(): Flow<List<LocalLibrarySong>>

    @Query("SELECT * FROM local_library_songs ORDER BY addedAt DESC")
    suspend fun getSongs(): List<LocalLibrarySong>

    @Query("SELECT * FROM local_library_songs WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): LocalLibrarySong?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(song: LocalLibrarySong): Long

    @Update
    suspend fun update(song: LocalLibrarySong)

    @Query("DELETE FROM local_library_songs WHERE url = :url")
    suspend fun deleteByUrl(url: String)
}

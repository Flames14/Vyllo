package com.vyllo.music.data

import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.data.download.DownloadEntity
import com.vyllo.music.data.download.PlaylistEntity
import com.vyllo.music.data.download.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow

interface IMusicRepository {
    suspend fun getSuggestions(query: String): List<String>
    suspend fun searchMusic(query: String, maintainSession: Boolean = true): List<MusicItem>
    suspend fun loadMoreResults(): List<MusicItem>
    suspend fun getTrendingMusic(): List<MusicItem>
    suspend fun getStreamUrl(url: String, force: Boolean = false, isVideo: Boolean = false): String?
    suspend fun getLocalStreamUrl(url: String): String?
    suspend fun getRelatedSongs(url: String): List<MusicItem>
    
    // Playlist Methods
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>
    suspend fun createPlaylist(name: String)
    suspend fun deletePlaylist(playlist: PlaylistEntity)
    suspend fun addSongToPlaylist(playlistId: Long, item: MusicItem)
    suspend fun removeSongFromPlaylist(playlistId: Long, url: String)
    fun getSongsInPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>>
    
    // Download Methods
    fun getAllDownloads(): Flow<List<DownloadEntity>>
    fun downloadSong(item: MusicItem)
    fun cancelDownload(url: String)
    suspend fun deleteDownload(url: String)
    suspend fun isDownloaded(url: String): Boolean

    // History Methods
    fun getRecentHistory(limit: Int = 20): Flow<List<MusicItem>>
    suspend fun recordListen(item: MusicItem)
    suspend fun clearHistory()
    suspend fun getPatternSuggestions(): List<MusicItem>
    suspend fun loadMoreRecommendations(): List<MusicItem>

    // Preference Methods
    fun loadSearchHistory(): List<String>
    fun saveSearchQuery(query: String)
    fun clearSearchHistory()
    fun saveLyricsPreference(videoUrl: String, lrcId: Long)
    fun getSavedLyricsId(videoUrl: String): Long?
    fun isLiquidScrollEnabled(): Boolean
    
    suspend fun getLyrics(title: String, artist: String, duration: Long, url: String): LyricsResponse?
}

package com.vyllo.music.domain.repository

import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.LyricsResponse
import com.vyllo.music.domain.model.DownloadEntity
import com.vyllo.music.domain.model.PlaylistEntity
import com.vyllo.music.domain.model.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow

interface IMusicRepository {
    suspend fun getSuggestions(query: String): List<String>
    suspend fun searchMusic(query: String, maintainSession: Boolean = true): List<MusicItem>
    suspend fun loadMoreResults(): List<MusicItem>

    /** Full metadata (title/artist/art/duration) for a watch URL â€” content deep links. */
    suspend fun resolveTrack(url: String): MusicItem?
    suspend fun getTrendingMusic(): List<MusicItem>
    suspend fun getStreamUrl(
        url: String,
        force: Boolean = false,
        isVideo: Boolean = false,
        quality: com.vyllo.music.domain.model.StreamQuality? = null
    ): String?
    suspend fun getLocalStreamUrl(url: String): String?
    suspend fun getRelatedSongs(url: String, force: Boolean = false): List<MusicItem>
    suspend fun getMoreRelatedSongs(url: String): List<MusicItem>
    suspend fun getArtistSongs(artist: String, excludeUrls: Set<String> = emptySet()): List<MusicItem>
    suspend fun getDiscoverSimilarSongs(
        title: String,
        artist: String,
        excludeUrls: Set<String> = emptySet(),
        excludeUploaders: Set<String> = emptySet()
    ): List<MusicItem>

    /**
     * Discovery ordering of the already-built related pool â€” backs the Related tab's
     * "similar to this track" section from the same ranked candidates that seed Up
     * Next, re-weighted toward new artists.
     */
    suspend fun getRelatedDiscoveryOrder(
        url: String,
        excludeUrls: Set<String> = emptySet(),
        limit: Int = 25
    ): List<MusicItem>

    // Playlist Methods
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>
    suspend fun createPlaylist(name: String): Long
    suspend fun deletePlaylist(playlist: PlaylistEntity)
    /** Adds once; returns false if the same URL is already in the playlist. */
    suspend fun addSongToPlaylist(playlistId: Long, item: MusicItem, position: Int? = null): Boolean
    suspend fun updatePlaylistCover(playlistId: Long, coverUrl: String?, cropX: Float, cropY: Float)
    suspend fun removeSongFromPlaylist(playlistId: Long, url: String)
    suspend fun moveSongInPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int): Boolean
    fun getSongsInPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>>

    // Download Methods
    fun getAllDownloads(): Flow<List<DownloadEntity>>
    fun getActiveDownloads(): Flow<List<DownloadEntity>>
    fun downloadSong(item: MusicItem)
    fun cancelDownload(url: String)
    suspend fun deleteDownload(url: String)
    suspend fun isDownloaded(url: String): Boolean

    // History Methods
    fun getRecentHistory(limit: Int = 50): Flow<List<MusicItem>>
    suspend fun recordListen(item: MusicItem)
    /** Counts a play for [item] (service-side, once per track start). */
    suspend fun recordPlayStart(item: MusicItem)
    /** Adds actual playback time to a track that is already in history. */
    suspend fun recordListenedTime(url: String, listenedMs: Long)
    /** Aggregate listening statistics, updated live as history changes. */
    fun observeListeningStats(): Flow<com.vyllo.music.domain.model.ListeningStats>
    suspend fun clearHistory()
    suspend fun removeFromHistory(url: String)
    suspend fun getPatternSuggestions(): List<MusicItem>
    suspend fun loadMoreRecommendations(): List<MusicItem>

    // Favorites / Ratings Methods
    /** Signed rating for [url]: 1 liked, -1 disliked, 0 unrated. */
    suspend fun getRating(url: String): Int
    fun observeLikedSongs(): Flow<List<MusicItem>>
    fun observeDislikedSongs(): Flow<List<MusicItem>>
    fun observeLikedCount(): Flow<Int>
    /**
     * Rate [item] as liked (1), disliked (-1), or clear the rating (0).
     * Callers should optimistically update the UI and surface failures.
     */
    suspend fun setRating(item: MusicItem, rating: Int): Boolean
    suspend fun clearAllRatings()

    // Preference Methods
    suspend fun loadSearchHistory(): List<String>
    suspend fun saveSearchQuery(query: String)
    suspend fun clearSearchHistory()
    fun saveLyricsPreference(videoUrl: String, lrcId: Long)
    fun getSavedLyricsId(videoUrl: String): Long?

    suspend fun getLyrics(title: String, artist: String, duration: Long, url: String): LyricsResponse?
    suspend fun getVideoStats(url: String): VideoStats?
}

data class VideoStats(
    val likeCount: Long = -1L,
    val viewCount: Long = -1L,
    val commentCount: Long = -1L
)

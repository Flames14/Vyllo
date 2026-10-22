package com.vyllo.music

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vyllo.music.domain.model.MusicItem
import com.vyllo.music.domain.model.SmartPlaylist
import com.vyllo.music.domain.repository.IMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Backs the computed library collections (liked, offline, most played). The
 * kind comes from the route argument so one screen serves all of them and the
 * lists stay live as the underlying tables change.
 */
@HiltViewModel
class SmartPlaylistViewModel @Inject constructor(
    repository: IMusicRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val kind: SmartPlaylist? =
        SmartPlaylist.from(savedStateHandle.get<String>(SMART_PLAYLIST_ARG))

    val songs: StateFlow<List<MusicItem>> = when (kind) {
        SmartPlaylist.LIKED -> repository.observeLikedSongs()
        SmartPlaylist.OFFLINE -> repository.getAllDownloads().map { downloads ->
            downloads.map { MusicItem(it.title, it.url, it.uploader, it.thumbnailUrl) }
        }
        SmartPlaylist.TOP -> repository.observeListeningStats().map { stats ->
            stats.topTracks.map { it.item }
        }
        null -> flowOf(emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    companion object {
        const val SMART_PLAYLIST_ARG = "smartKey"
    }
}

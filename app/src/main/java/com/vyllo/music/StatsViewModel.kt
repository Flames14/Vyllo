package com.vyllo.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vyllo.music.domain.model.ListeningStats
import com.vyllo.music.domain.repository.IMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Read-only view over playback history aggregates. The repository already
 * combines the totals/lists into one snapshot, so the ViewModel only has to
 * keep the cold flow warm while the screen is visible.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    repository: IMusicRepository
) : ViewModel() {

    val stats: StateFlow<ListeningStats> = repository.observeListeningStats()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ListeningStats()
        )
}

package com.henriquesebastiao.downtify.feature.stats

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henriquesebastiao.downtify.core.data.stats.ListeningStatsRepository
import com.henriquesebastiao.downtify.core.model.ListeningStats
import com.henriquesebastiao.downtify.core.model.ListeningStatsCalculator
import com.henriquesebastiao.downtify.core.model.RecentContext
import com.henriquesebastiao.downtify.core.model.StatsRange
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class StatsUiState(
    val loading: Boolean = true,
    val range: StatsRange = StatsRange.Last30Days,
    val stats: ListeningStats = ListeningStats.Empty,
    /** Albums, artists and playlists started lately: the one thing the phone really remembers. */
    val recent: List<RecentContext> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val repository: ListeningStatsRepository,
) : ViewModel() {
    private val range = savedState.getStateFlow(KEY_RANGE, StatsRange.Last30Days.name)
        .map { name -> StatsRange.entries.firstOrNull { it.name == name } ?: StatsRange.Last30Days }

    val uiState: StateFlow<StatsUiState> = combine(
        repository.plays(),
        repository.recentlyPlayed(),
        range,
    ) { plays, recent, range ->
        StatsUiState(
            loading = false,
            range = range,
            stats = ListeningStatsCalculator.calculate(plays, range, Instant.now(), zone),
            recent = recent,
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StatsUiState())

    fun selectRange(value: StatsRange) {
        savedState[KEY_RANGE] = value.name
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val KEY_RANGE = "range"

        /** The phone's own clock and zone: a play counts on the day the listener had. */
        val zone: ZoneId = ZoneId.systemDefault()
    }
}

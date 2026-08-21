package dev.nk.musicplayer.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.nk.musicplayer.data.db.NamedCount
import dev.nk.musicplayer.data.db.TrackCount
import dev.nk.musicplayer.data.stats.StatsRepository
import dev.nk.musicplayer.data.stats.StatsWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(private val repository: StatsRepository) : ViewModel() {

    private val _window = MutableStateFlow(StatsWindow.Week)
    val window = _window.asStateFlow()

    val listeningTime = _window
        .flatMapLatest { repository.listeningTime(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val topArtists = _window
        .flatMapLatest { repository.topArtists(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<NamedCount>())

    val topTracks = _window
        .flatMapLatest { repository.topTracks(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<TrackCount>())

    val mostSkipped = repository.mostSkipped()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val byHour = repository.byHour()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val aiCompletion = repository.aiCompletion()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val eventCount = repository.eventCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setWindow(value: StatsWindow) {
        _window.value = value
    }

    companion object {
        fun factory(repository: StatsRepository) =
            viewModelFactory { initializer { StatsViewModel(repository) } }
    }
}

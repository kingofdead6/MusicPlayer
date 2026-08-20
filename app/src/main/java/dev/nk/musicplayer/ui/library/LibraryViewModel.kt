package dev.nk.musicplayer.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dev.nk.musicplayer.data.db.AlbumSummary
import dev.nk.musicplayer.data.db.ArtistSummary
import dev.nk.musicplayer.data.db.Track
import dev.nk.musicplayer.data.library.LibraryRepository
import dev.nk.musicplayer.util.normalizeForSearch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val repository: LibraryRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    private val _lastScan = MutableStateFlow<String?>(null)
    val lastScan = _lastScan.asStateFlow()

    val trackCount = repository.observeTrackCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Paged so a 10k-track library never materialises in one Compose list. */
    val pagedTracks: Flow<PagingData<Track>> = _query
        .debounce(200)
        .flatMapLatest { repository.pagedTracks(it) }
        .cachedIn(viewModelScope)

    val artists = combine(repository.observeArtists(), _query.debounce(150)) { artists, q ->
        artists.filterByName(q) { it.artist }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ArtistSummary>())

    val albums = combine(repository.observeAlbums(), _query.debounce(150)) { albums, q ->
        albums.filter { q.isBlank() || normalizeForSearch("${it.album} ${it.artist}").contains(normalizeForSearch(q)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<AlbumSummary>())

    init {
        // First launch (or first launch after the permission was granted) has an empty table.
        viewModelScope.launch {
            if (repository.allTracks().isEmpty()) rescan()
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun rescan() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                val result = repository.rescan()
                _lastScan.value = buildString {
                    append("${result.total} tracks")
                    if (result.updated > 0) append(" · ${result.updated} updated")
                    if (result.missing > 0) append(" · ${result.missing} missing")
                }
            } catch (e: Exception) {
                _lastScan.value = "Scan failed: ${e.message}"
            } finally {
                _refreshing.value = false
            }
        }
    }

    suspend fun queueForSongsList(startTrackId: Long) =
        repository.queueForSongsList(_query.value, startTrackId)

    /** Whole library in random order, capped so ExoPlayer's timeline stays sane. */
    suspend fun shuffleQueue(): List<Track> = repository.randomTracks(500)

    suspend fun tracksByArtist(artist: String) = repository.tracksByArtist(artist)
    suspend fun tracksByAlbum(albumId: Long) = repository.tracksByAlbum(albumId)

    companion object {
        fun factory(repository: LibraryRepository) = viewModelFactory {
            initializer { LibraryViewModel(repository) }
        }
    }
}

private inline fun <T> List<T>.filterByName(query: String, name: (T) -> String): List<T> {
    if (query.isBlank()) return this
    val normalized = normalizeForSearch(query)
    return filter { normalizeForSearch(name(it)).contains(normalized) }
}

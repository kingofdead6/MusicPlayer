package dev.nk.musicplayer.data.library

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import dev.nk.musicplayer.data.db.AlbumSummary
import dev.nk.musicplayer.data.db.ArtistSummary
import dev.nk.musicplayer.data.db.Track
import dev.nk.musicplayer.data.db.TrackDao
import dev.nk.musicplayer.util.normalizeForSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LibraryRepository(
    private val trackDao: TrackDao,
    private val scanner: MediaStoreScanner
) {

    fun observeTrackCount(): Flow<Int> = trackDao.observeTrackCount()
    fun observeArtists(): Flow<List<ArtistSummary>> = trackDao.observeArtists()
    fun observeAlbums(): Flow<List<AlbumSummary>> = trackDao.observeAlbums()
    fun observeTracksByArtist(artist: String) = trackDao.observeTracksByArtist(artist)
    fun observeTracksByAlbum(albumId: Long) = trackDao.observeTracksByAlbum(albumId)

    suspend fun tracksByArtist(artist: String) = trackDao.tracksByArtist(artist)
    suspend fun tracksByAlbum(albumId: Long) = trackDao.tracksByAlbum(albumId)
    suspend fun allTracks(): List<Track> = trackDao.allTracksById()
    suspend fun trackById(id: Long): Track? = trackDao.trackById(id)

    /**
     * The same ordering the paged All Songs list shows, materialised so a tap can build a
     * queue. Capped: a queue of more than [MAX_QUEUE] items is not something I will ever
     * reach the end of, and ExoPlayer does not need to hold a 10k timeline.
     */
    suspend fun queueForSongsList(query: String, startTrackId: Long): Pair<List<Track>, Int> {
        val normalized = normalizeForSearch(query)
        val all = if (normalized.isBlank()) trackDao.allSongsSorted() else trackDao.searchSorted(normalized)
        val index = all.indexOfFirst { it.id == startTrackId }.coerceAtLeast(0)
        if (all.size <= MAX_QUEUE) return all to index
        val from = (index - MAX_QUEUE / 4).coerceAtLeast(0)
        val window = all.subList(from, minOf(from + MAX_QUEUE, all.size)).toList()
        return window to (index - from)
    }
    suspend fun tracksByIds(ids: List<Long>): List<Track> = trackDao.tracksByIds(ids)
    suspend fun randomTracks(limit: Int): List<Track> = trackDao.randomTracks(limit)

    /**
     * Paged song list. A 10k-track library never lands in a single Compose list; Room hands
     * out windows of [PAGE_SIZE] rows as the list scrolls.
     */
    fun pagedTracks(query: String): Flow<PagingData<Track>> {
        val normalized = normalizeForSearch(query)
        return Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)
        ) {
            if (normalized.isBlank()) trackDao.pagedAllSongs() else trackDao.pagedSearch(normalized)
        }.flow
    }

    /**
     * Incremental rescan. Rows are upserted by MediaStore `_ID`, so a rescan never duplicates.
     * Files that vanished are marked missing rather than deleted, which keeps play history
     * pointing at something real.
     *
     * @return how many rows were inserted or refreshed.
     */
    suspend fun rescan(): ScanResult = withContext(Dispatchers.IO) {
        val found = scanner.scan()
        val known = trackDao.fingerprints().associateBy { it.id }

        // Rewrite a row when the file changed, when it is new, or when it was previously
        // marked missing and has come back -- otherwise a restored file would stay hidden
        // forever, since its DATE_MODIFIED never changed.
        val changed = found.filter { track ->
            val previous = known[track.id]
            previous == null || previous.dateModified != track.dateModified || previous.isMissing
        }
        if (changed.isNotEmpty()) trackDao.upsertAll(changed)

        val foundIds = found.mapTo(HashSet()) { it.id }
        // Only rows that are newly gone; ones already flagged need no write.
        val goneIds = known.values.filter { !it.isMissing && it.id !in foundIds }.map { it.id }
        // Chunked because SQLite caps the number of bound variables at 999.
        goneIds.chunked(500).forEach { trackDao.markMissing(it) }

        Log.i(TAG, "rescan: ${found.size} in MediaStore, ${changed.size} upserted, ${goneIds.size} marked missing")
        ScanResult(total = found.size, updated = changed.size, missing = goneIds.size)
    }

    /** Called when the player fails to open a file: the row stays, but drops out of the library. */
    suspend fun markMissing(trackId: Long) = trackDao.markMissing(trackId)

    companion object {
        private const val TAG = "LibraryRepository"
        private const val PAGE_SIZE = 100
        private const val MAX_QUEUE = 1000
    }
}

data class ScanResult(val total: Int, val updated: Int, val missing: Int)

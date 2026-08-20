package dev.nk.musicplayer.data.stats

import dev.nk.musicplayer.data.db.AiPlaylistCompletion
import dev.nk.musicplayer.data.db.HourBucket
import dev.nk.musicplayer.data.db.NamedCount
import dev.nk.musicplayer.data.db.PlayEventDao
import dev.nk.musicplayer.data.db.TrackCount
import kotlinx.coroutines.flow.Flow

/** How far back a stats screen is looking. */
enum class StatsWindow(val label: String) {
    Week("Week"),
    Month("Month"),
    All("All time");

    /** Epoch millis to filter from; [All] reaches back to the beginning of time. */
    fun since(now: Long = System.currentTimeMillis()): Long = when (this) {
        Week -> now - 7L * 24 * 60 * 60 * 1000
        Month -> now - 30L * 24 * 60 * 60 * 1000
        All -> 0L
    }
}

class StatsRepository(private val dao: PlayEventDao) {

    fun listeningTime(window: StatsWindow): Flow<Long> = dao.observeListeningTime(window.since())
    fun topArtists(window: StatsWindow): Flow<List<NamedCount>> = dao.observeTopArtists(window.since(), 10)
    fun topTracks(window: StatsWindow): Flow<List<TrackCount>> = dao.observeTopTracks(window.since(), 10)

    fun mostSkipped(): Flow<List<TrackCount>> = dao.observeMostSkipped(10)
    fun byHour(): Flow<List<HourBucket>> = dao.observeByHour()
    fun aiCompletion(): Flow<List<AiPlaylistCompletion>> = dao.observeAiCompletion()
    fun eventCount(): Flow<Int> = dao.observeEventCount()
}

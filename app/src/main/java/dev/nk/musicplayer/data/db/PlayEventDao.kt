package dev.nk.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayEventDao {

    @Insert
    suspend fun insert(event: PlayEvent)

    @Query("SELECT COALESCE(SUM(listenedMs), 0) FROM play_events WHERE startedAt >= :since")
    fun observeListeningTime(since: Long): Flow<Long>

    @Query("SELECT COUNT(*) FROM play_events")
    fun observeEventCount(): Flow<Int>

    @Query(
        """
        SELECT t.artist AS label, COUNT(*) AS count
        FROM play_events e JOIN tracks t ON t.id = e.trackId
        WHERE e.startedAt >= :since AND e.skipped = 0
        GROUP BY t.artist ORDER BY count DESC, label COLLATE NOCASE LIMIT :limit
        """
    )
    fun observeTopArtists(since: Long, limit: Int): Flow<List<NamedCount>>

    @Query(
        """
        SELECT t.id AS trackId, t.title AS title, t.artist AS artist, COUNT(*) AS count
        FROM play_events e JOIN tracks t ON t.id = e.trackId
        WHERE e.startedAt >= :since AND e.skipped = 0
        GROUP BY t.id ORDER BY count DESC, title COLLATE NOCASE LIMIT :limit
        """
    )
    fun observeTopTracks(since: Long, limit: Int): Flow<List<TrackCount>>

    @Query(
        """
        SELECT t.id AS trackId, t.title AS title, t.artist AS artist, COUNT(*) AS count
        FROM play_events e JOIN tracks t ON t.id = e.trackId
        WHERE e.skipped = 1
        GROUP BY t.id ORDER BY count DESC, title COLLATE NOCASE LIMIT :limit
        """
    )
    fun observeMostSkipped(limit: Int): Flow<List<TrackCount>>

    /**
     * Listening spread over the local hour-of-day. `strftime('%H', ..., 'localtime')` is used
     * so the buckets match the clock I was actually looking at while listening.
     */
    @Query(
        """
        SELECT CAST(strftime('%H', startedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour,
               COUNT(*) AS count,
               COALESCE(SUM(listenedMs), 0) AS totalMs
        FROM play_events
        GROUP BY hour ORDER BY hour
        """
    )
    fun observeByHour(): Flow<List<HourBucket>>

    /**
     * For every AI-generated playlist, how many of its tracks I have ever completed while
     * listening *from that playlist* (source = "playlist:<id>").
     */
    @Query(
        """
        SELECT p.id AS playlistId, p.name AS name,
               (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlistId = p.id) AS trackCount,
               (SELECT COUNT(DISTINCT e.trackId) FROM play_events e
                 WHERE e.source = 'playlist:' || p.id AND e.completed = 1
                   AND e.trackId IN (SELECT pt2.trackId FROM playlist_tracks pt2 WHERE pt2.playlistId = p.id)
               ) AS completedTrackCount
        FROM playlists p
        WHERE p.isAiGenerated = 1
        ORDER BY p.createdAt DESC
        """
    )
    fun observeAiCompletion(): Flow<List<AiPlaylistCompletion>>
}

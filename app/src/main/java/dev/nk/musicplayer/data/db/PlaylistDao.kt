package dev.nk.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun playlistById(id: Long): Playlist?

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<Playlist?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(rows: List<PlaylistTrack>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Query(
        """
        SELECT t.* FROM playlist_tracks pt
        JOIN tracks t ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position
        """
    )
    fun observeTracks(playlistId: Long): Flow<List<Track>>

    @Query(
        """
        SELECT t.* FROM playlist_tracks pt
        JOIN tracks t ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position
        """
    )
    suspend fun tracks(playlistId: Long): List<Track>

    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt,
               p.isAiGenerated AS isAiGenerated, p.sourcePrompt AS sourcePrompt,
               COUNT(t.id) AS trackCount,
               COALESCE(SUM(t.durationMs), 0) AS totalDurationMs
        FROM playlists p
        LEFT JOIN playlist_tracks pt ON pt.playlistId = p.id
        LEFT JOIN tracks t ON t.id = pt.trackId
        GROUP BY p.id
        ORDER BY p.createdAt DESC
        """
    )
    fun observePlaylistSummaries(): Flow<List<PlaylistSummary>>

    /** Replaces the whole ordered membership of a playlist in one transaction. */
    @Transaction
    suspend fun replaceTracks(playlistId: Long, trackIds: List<Long>) {
        clearPlaylist(playlistId)
        insertPlaylistTracks(
            trackIds.mapIndexed { index, trackId -> PlaylistTrack(playlistId, trackId, index) }
        )
    }

    @Transaction
    suspend fun appendTracks(playlistId: Long, trackIds: List<Long>) {
        var next = maxPosition(playlistId) + 1
        val rows = trackIds.map { PlaylistTrack(playlistId, it, next++) }
        insertPlaylistTracks(rows)
    }

    @Transaction
    suspend fun createWithTracks(playlist: Playlist, trackIds: List<Long>): Long {
        val id = insertPlaylist(playlist)
        insertPlaylistTracks(trackIds.mapIndexed { i, trackId -> PlaylistTrack(id, trackId, i) })
        return id
    }
}

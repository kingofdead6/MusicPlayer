package dev.nk.musicplayer.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Upsert
    suspend fun upsertAll(tracks: List<Track>)

    @Query("SELECT id, dateModified FROM tracks")
    suspend fun idsAndModified(): List<TrackFingerprint>

    @Query("UPDATE tracks SET isMissing = 1 WHERE id IN (:ids)")
    suspend fun markMissing(ids: List<Long>)

    @Query("UPDATE tracks SET isMissing = 1 WHERE id = :id")
    suspend fun markMissing(id: Long)

    @Query("SELECT COUNT(*) FROM tracks WHERE isMissing = 0")
    fun observeTrackCount(): Flow<Int>

    @Query("SELECT * FROM tracks WHERE isMissing = 0 ORDER BY id")
    suspend fun allTracksById(): List<Track>

    @Query("SELECT * FROM tracks WHERE isMissing = 0 ORDER BY title COLLATE NOCASE")
    fun pagedAllSongs(): PagingSource<Int, Track>

    @Query(
        """
        SELECT * FROM tracks
        WHERE isMissing = 0 AND searchText LIKE '%' || :query || '%'
        ORDER BY title COLLATE NOCASE
        """
    )
    fun pagedSearch(query: String): PagingSource<Int, Track>

    @Query("SELECT * FROM tracks WHERE isMissing = 0 ORDER BY title COLLATE NOCASE")
    suspend fun allSongsSorted(): List<Track>

    @Query(
        """
        SELECT * FROM tracks
        WHERE isMissing = 0 AND searchText LIKE '%' || :query || '%'
        ORDER BY title COLLATE NOCASE
        """
    )
    suspend fun searchSorted(query: String): List<Track>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun trackById(id: Long): Track?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun tracksByIds(ids: List<Long>): List<Track>

    @Query("SELECT * FROM tracks WHERE isMissing = 0 ORDER BY RANDOM() LIMIT :limit")
    suspend fun randomTracks(limit: Int): List<Track>

    @Query(
        """
        SELECT artist AS artist, COUNT(*) AS trackCount, COUNT(DISTINCT albumId) AS albumCount
        FROM tracks WHERE isMissing = 0
        GROUP BY artist ORDER BY artist COLLATE NOCASE
        """
    )
    fun observeArtists(): Flow<List<ArtistSummary>>

    @Query(
        """
        SELECT albumId AS albumId, album AS album, MIN(artist) AS artist,
               COUNT(*) AS trackCount, MAX(year) AS year
        FROM tracks WHERE isMissing = 0
        GROUP BY albumId, album ORDER BY album COLLATE NOCASE
        """
    )
    fun observeAlbums(): Flow<List<AlbumSummary>>

    @Query(
        "SELECT * FROM tracks WHERE isMissing = 0 AND artist = :artist " +
            "ORDER BY album COLLATE NOCASE, trackNumber, title COLLATE NOCASE"
    )
    fun observeTracksByArtist(artist: String): Flow<List<Track>>

    @Query(
        "SELECT * FROM tracks WHERE isMissing = 0 AND albumId = :albumId " +
            "ORDER BY trackNumber, title COLLATE NOCASE"
    )
    fun observeTracksByAlbum(albumId: Long): Flow<List<Track>>

    @Query(
        "SELECT * FROM tracks WHERE isMissing = 0 AND artist = :artist " +
            "ORDER BY album COLLATE NOCASE, trackNumber, title COLLATE NOCASE"
    )
    suspend fun tracksByArtist(artist: String): List<Track>

    @Query(
        "SELECT * FROM tracks WHERE isMissing = 0 AND albumId = :albumId " +
            "ORDER BY trackNumber, title COLLATE NOCASE"
    )
    suspend fun tracksByAlbum(albumId: Long): List<Track>
}

data class TrackFingerprint(val id: Long, val dateModified: Long)

package dev.nk.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SongAnalysisDao {

    @Upsert
    suspend fun upsert(analysis: SongAnalysis)

    @Query("SELECT * FROM song_analysis WHERE trackId = :trackId")
    suspend fun byTrack(trackId: Long): SongAnalysis?

    @Query("SELECT * FROM song_analysis")
    suspend fun all(): List<SongAnalysis>

    @Query("SELECT COUNT(*) FROM song_analysis")
    fun observeAnalyzedCount(): Flow<Int>

    /**
     * Library rows that have never been analysed, oldest first so a long run is resumable and
     * always makes progress on something new.
     */
    @Query(
        """
        SELECT t.* FROM tracks t
        LEFT JOIN song_analysis a ON a.trackId = t.id
        WHERE t.isMissing = 0 AND a.trackId IS NULL
        ORDER BY t.id
        LIMIT :limit
        """
    )
    suspend fun tracksWithoutAnalysis(limit: Int): List<Track>
}

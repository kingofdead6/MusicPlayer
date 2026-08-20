package dev.nk.musicplayer.data.playlist

import dev.nk.musicplayer.data.db.Playlist
import dev.nk.musicplayer.data.db.PlaylistDao
import dev.nk.musicplayer.data.db.PlaylistSummary
import dev.nk.musicplayer.data.db.Track
import kotlinx.coroutines.flow.Flow

class PlaylistRepository(private val dao: PlaylistDao) {

    fun observeSummaries(): Flow<List<PlaylistSummary>> = dao.observePlaylistSummaries()
    fun observePlaylist(id: Long): Flow<Playlist?> = dao.observePlaylist(id)
    fun observeTracks(playlistId: Long): Flow<List<Track>> = dao.observeTracks(playlistId)

    suspend fun tracks(playlistId: Long): List<Track> = dao.tracks(playlistId)
    suspend fun playlist(id: Long): Playlist? = dao.playlistById(id)

    suspend fun create(name: String): Long =
        dao.insertPlaylist(
            Playlist(
                name = name.trim().ifBlank { "Untitled playlist" },
                createdAt = System.currentTimeMillis(),
                isAiGenerated = false,
                sourcePrompt = null
            )
        )

    /** Used by the AI flow once I hit Save; [prompt] is stored verbatim. */
    suspend fun createGenerated(name: String, prompt: String, trackIds: List<Long>): Long =
        dao.createWithTracks(
            Playlist(
                name = name.trim().ifBlank { "AI playlist" },
                createdAt = System.currentTimeMillis(),
                isAiGenerated = true,
                sourcePrompt = prompt
            ),
            trackIds
        )

    suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim().ifBlank { "Untitled playlist" })
    suspend fun delete(id: Long) = dao.deletePlaylist(id)
    suspend fun addTracks(playlistId: Long, trackIds: List<Long>) = dao.appendTracks(playlistId, trackIds)
    suspend fun removeTrack(playlistId: Long, trackId: Long) = dao.removeTrack(playlistId, trackId)

    /** Persists a reorder by rewriting every `position` in one transaction. */
    suspend fun reorder(playlistId: Long, orderedTrackIds: List<Long>) =
        dao.replaceTracks(playlistId, orderedTrackIds)
}

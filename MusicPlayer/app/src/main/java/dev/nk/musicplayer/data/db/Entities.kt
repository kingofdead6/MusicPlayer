package dev.nk.musicplayer.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One audio file known to MediaStore.
 *
 * [id] is the MediaStore `_ID`, so rescans upsert instead of duplicating.
 *
 * Two columns are not in the original spec but are needed by requirements that are:
 *  - [dateModified] backs the incremental rescan (only re-read rows whose file changed).
 *  - [isMissing] lets a file deleted from disk be *marked* rather than deleted, so play
 *    history that references it stays intact.
 *  - [searchText] is a lowercased, diacritic-stripped "title artist album" blob so that
 *    SQLite LIKE search behaves for French accents and passes Arabic through untouched.
 */
@Entity(
    tableName = "tracks",
    indices = [Index("artist"), Index("album"), Index("albumId"), Index("isMissing")]
)
data class Track(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val albumId: Long,
    val year: Int?,
    val trackNumber: Int?,
    val dateAdded: Long,
    val dateModified: Long = 0L,
    @ColumnInfo(defaultValue = "0") val isMissing: Boolean = false,
    val searchText: String = ""
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val isAiGenerated: Boolean,
    /** The natural-language request, kept verbatim so I can see what produced a playlist. */
    val sourcePrompt: String?
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trackId"), Index("playlistId", "position")]
)
data class PlaylistTrack(
    val playlistId: Long,
    val trackId: Long,
    val position: Int
)

@Entity(tableName = "play_events", indices = [Index("trackId"), Index("startedAt"), Index("source")])
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val startedAt: Long,
    val listenedMs: Long,
    val trackDurationMs: Long,
    /** >= 90% listened. */
    val completed: Boolean,
    /** < 30% listened. */
    val skipped: Boolean,
    /** "library" | "playlist:<id>" | "shuffle" */
    val source: String
)

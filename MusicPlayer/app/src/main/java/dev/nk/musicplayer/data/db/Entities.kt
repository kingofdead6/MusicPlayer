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

/**
 * What the app worked out about one song by listening to it.
 *
 * Produced by [dev.nk.musicplayer.data.analysis.SongAnalyzer]: a short sample of the file is
 * transcribed by a speech-to-text model, and the transcript plus the file's metadata are
 * classified by the LLM. The row is a cache — deriving it costs two network calls, so it is
 * written once and re-used by every later playlist request.
 *
 * [transcript] is a capped excerpt kept only so a re-analysis (a better prompt, a different
 * model) does not have to pay for transcription again. It is device-local and the UI never
 * renders it; only the derived fields are shown.
 */
@Entity(
    tableName = "song_analysis",
    indices = [Index("mood"), Index("category"), Index("sentiment")]
)
data class SongAnalysis(
    @PrimaryKey val trackId: Long,
    val analyzedAt: Long,
    /** "lyrics" when a transcript was obtained, "metadata" when the song had no usable vocals. */
    val source: String,
    /** Language of the vocals as the model heard them, or "unknown"/"instrumental". */
    val language: String,
    /** positive | negative | neutral | mixed */
    val sentiment: String,
    /** A single word from a small vocabulary: hopeful, melancholic, angry, … */
    val mood: String,
    /** What the song is *for*: party, workout, focus, driving, heartbreak, … */
    val category: String,
    val genre: String,
    /** 0 = bleak, 1 = joyful. */
    val valence: Float,
    /** 0 = still, 1 = frantic. */
    val energy: Float,
    /** Comma-separated subject matter, e.g. "loss, memory, home". */
    val themes: String,
    /** One line in the model's own words about what the song is doing. */
    val summary: String,
    val explicit: Boolean,
    /** Capped transcript excerpt, kept for cheap re-analysis. Never displayed. */
    val transcript: String
) {
    val themeList: List<String>
        get() = themes.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** True when the classification came from a transcript rather than the file's tags alone. */
    val fromLyrics: Boolean get() = source == SOURCE_LYRICS

    companion object {
        const val SOURCE_LYRICS = "lyrics"
        const val SOURCE_METADATA = "metadata"
    }
}

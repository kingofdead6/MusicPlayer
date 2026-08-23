package dev.nk.musicplayer.data.db

/** Row shapes returned by aggregate queries. Kept here so the DAOs stay readable. */

data class ArtistSummary(
    val artist: String,
    val trackCount: Int,
    val albumCount: Int
)

data class AlbumSummary(
    val albumId: Long,
    val album: String,
    val artist: String,
    val trackCount: Int,
    val year: Int?
)

data class NamedCount(
    val label: String,
    val count: Int
)

data class TrackCount(
    val trackId: Long,
    val title: String,
    val artist: String,
    val count: Int
)

data class HourBucket(
    val hour: Int,
    val count: Int,
    val totalMs: Long
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val isAiGenerated: Boolean,
    val sourcePrompt: String?,
    val trackCount: Int,
    val totalDurationMs: Long,
    /**
     * The playlist's cover: the first track in playlist order. Both ids are carried because
     * artwork resolution prefers the track over the album — see `TrackArtworkFetcher`.
     * Null for an empty playlist, which has nothing to draw.
     */
    val coverTrackId: Long?,
    val coverAlbumId: Long?
)

data class AiPlaylistCompletion(
    val playlistId: Long,
    val name: String,
    val trackCount: Int,
    val completedTrackCount: Int
)

/** Fraction of an AI playlist's tracks I actually listened through, in 0f..1f. */
val AiPlaylistCompletion.ratio: Float
    get() = if (trackCount == 0) 0f else completedTrackCount.toFloat() / trackCount

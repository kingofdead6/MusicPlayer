package dev.nk.musicplayer.playback

import android.net.Uri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.nk.musicplayer.data.db.Track
import dev.nk.musicplayer.ui.components.albumArtUri

const val EXTRA_ALBUM_ID = "albumId"
const val EXTRA_DURATION_MS = "durationMs"
const val EXTRA_SOURCE = "source"

/** Where a queue came from, recorded on every [dev.nk.musicplayer.data.db.PlayEvent]. */
object PlaySource {
    const val LIBRARY = "library"
    const val SHUFFLE = "shuffle"
    fun playlist(id: Long) = "playlist:$id"
}

/**
 * A MediaItem crossing the MediaController -> MediaSession boundary loses its
 * `localConfiguration` (and with it the URI), so the real URI travels in `requestMetadata`
 * and is put back by [PlaybackService]'s `onAddMediaItems`.
 */
fun Track.toMediaItem(source: String): MediaItem {
    val parsed = Uri.parse(uri)
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(parsed)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(parsed).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(albumArtUri(albumId))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setExtras(
                    bundleOf(
                        EXTRA_ALBUM_ID to albumId,
                        EXTRA_DURATION_MS to durationMs,
                        EXTRA_SOURCE to source
                    )
                )
                .build()
        )
        .build()
}

/** What the UI needs about one queue entry, readable from a MediaController. */
data class QueueEntry(
    val trackId: Long,
    val title: String,
    val artist: String,
    val albumId: Long,
    val durationMs: Long,
    val source: String
)

fun MediaItem.toQueueEntry(): QueueEntry {
    val extras = mediaMetadata.extras
    return QueueEntry(
        trackId = mediaId.toLongOrNull() ?: -1L,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        albumId = extras?.getLong(EXTRA_ALBUM_ID) ?: -1L,
        durationMs = extras?.getLong(EXTRA_DURATION_MS) ?: 0L,
        source = extras?.getString(EXTRA_SOURCE) ?: PlaySource.LIBRARY
    )
}

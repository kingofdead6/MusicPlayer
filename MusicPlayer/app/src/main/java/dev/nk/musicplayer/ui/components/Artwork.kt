package dev.nk.musicplayer.ui.components

import android.content.ContentUris
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import androidx.core.graphics.drawable.toDrawable
import dev.nk.musicplayer.data.db.Track

/**
 * Identifies the artwork for one track. Both ids are needed because the three sources below
 * key off different things: the thumbnail API and the embedded-tag reader want the *track*
 * uri, while the legacy provider wants the *album* id.
 */
data class TrackArtwork(val trackId: Long, val albumId: Long) {
    companion object {
        fun of(track: Track) = TrackArtwork(track.id, track.albumId)
    }
}

/** Artwork keyed by album alone, for rows that have no specific track in hand. */
fun albumArtwork(albumId: Long) = TrackArtwork(trackId = -1L, albumId = albumId)

private val ALBUM_ART_BASE: Uri = Uri.parse("content://media/external/audio/albumart")

/**
 * Resolves cover art for a track, trying three sources in descending reliability.
 *
 * The app previously used only `content://media/external/audio/albumart`, which is
 * deprecated as of API 29 and — more to the point — is backed by a per-album cache that
 * MediaProvider populates opportunistically. On plenty of devices it simply has no row for a
 * given album even though the file itself carries a perfectly good cover, which is why
 * artwork went missing for many tracks.
 *
 * The order is:
 *  1. [android.content.ContentResolver.loadThumbnail] on the track uri (API 29+). This is the
 *     supported replacement and reads the file directly.
 *  2. [MediaMetadataRetriever.getEmbeddedPicture] on the track uri. Works back to API 26 and
 *     catches anything the thumbnail API declines, at the cost of opening the file.
 *  3. The legacy album-art provider, which still serves art for albums whose cache row exists
 *     and is the only option when all we know is an album id.
 *
 * A track with no art at all fails all three, and [AlbumArt] falls back to its placeholder.
 */
class TrackArtworkFetcher(
    private val data: TrackArtwork,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val resolver = options.context.contentResolver

        if (data.trackId >= 0) {
            val trackUri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                data.trackId
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 512² is comfortably above the largest slot the UI draws (full-width art on
                // Now Playing) while staying cheap to decode for a scrolling list.
                runCatching { resolver.loadThumbnail(trackUri, Size(512, 512), null) }
                    .getOrNull()
                    ?.let { return it.asResult() }
            }

            runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(options.context, trackUri)
                    retriever.embeddedPicture
                }
            }.getOrNull()?.let { bytes ->
                android.graphics.BitmapFactory
                    .decodeByteArray(bytes, 0, bytes.size)
                    ?.let { return it.asResult() }
            }
        }

        if (data.albumId >= 0) {
            val legacy = ContentUris.withAppendedId(ALBUM_ART_BASE, data.albumId)
            runCatching {
                resolver.openInputStream(legacy)?.use { android.graphics.BitmapFactory.decodeStream(it) }
            }.getOrNull()?.let { return it.asResult() }
        }

        // Coil treats a null FetchResult as "this fetcher had nothing to say" rather than as
        // a failure, which can leave the request unresolved instead of reporting onError.
        // Throwing is how a fetcher signals "there is genuinely no artwork here".
        throw NoArtworkException(data)
    }

    private fun Bitmap.asResult(): FetchResult = DrawableResult(
        drawable = toDrawable(options.context.resources),
        isSampled = false,
        dataSource = DataSource.DISK
    )

    /** Signals that all three sources came up empty for this track. */
    class NoArtworkException(artwork: TrackArtwork) :
        IllegalStateException("No artwork for track=${artwork.trackId} album=${artwork.albumId}")

    class Factory : Fetcher.Factory<TrackArtwork> {
        override fun create(
            data: TrackArtwork,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = TrackArtworkFetcher(data, options)
    }
}

/**
 * `MediaMetadataRetriever` only became [AutoCloseable] in API 29, so this stands in for
 * `use` on older devices and guarantees the native handle is released either way.
 */
private inline fun <T> MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> T): T = try {
    block(this)
} finally {
    runCatching { release() }
}

/** Kept so existing call sites and the media notification keep resolving album art. */
fun albumArtUri(albumId: Long): Uri = ContentUris.withAppendedId(ALBUM_ART_BASE, albumId)

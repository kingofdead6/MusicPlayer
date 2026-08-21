package dev.nk.musicplayer.data.library

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import dev.nk.musicplayer.data.db.Track
import dev.nk.musicplayer.util.normalizeForSearch

/** Anything shorter than this is a ringtone or a voice memo, not music. */
private const val MIN_DURATION_MS = 30_000L

class MediaStoreScanner(private val context: Context) {

    /**
     * Reads every music file MediaStore will show us. Filtered to real music longer than
     * 30 s so ringtones, notification blips and voice memos stay out of the library.
     */
    fun scan(): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND ${MediaStore.Audio.Media.DURATION} > ?"
        val selectionArgs = arrayOf(MIN_DURATION_MS.toString())

        val out = ArrayList<Track>()
        context.contentResolver.query(
            collection, projection, selection, selectionArgs,
            "${MediaStore.Audio.Media._ID} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val fileName = cursor.getStringOrNull(nameCol)?.substringBeforeLast('.').orEmpty()

                // Null/blank metadata (and MediaStore's literal "<unknown>") falls back to the
                // file name so nothing shows up as an empty row.
                val title = cursor.getStringOrNull(titleCol).orFallback(fileName.ifBlank { "Track $id" })
                val artist = cursor.getStringOrNull(artistCol).orFallback("Unknown artist")
                val album = cursor.getStringOrNull(albumCol).orFallback("Unknown album")

                val rawTrackNo = cursor.getIntOrNull(trackCol)
                // MediaStore encodes disc number as 1xxx / 2xxx; keep only the track part.
                val trackNumber = rawTrackNo?.let { if (it > 1000) it % 1000 else it }?.takeIf { it > 0 }
                val year = cursor.getIntOrNull(yearCol)?.takeIf { it > 0 }

                out += Track(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = cursor.getLong(durationCol),
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    albumId = cursor.getLong(albumIdCol),
                    year = year,
                    trackNumber = trackNumber,
                    // MediaStore stores these in seconds.
                    dateAdded = cursor.getLong(addedCol) * 1000L,
                    dateModified = cursor.getLong(modifiedCol),
                    isMissing = false,
                    searchText = normalizeForSearch("$title $artist $album $fileName")
                )
            }
        }
        return out
    }
}

private fun String?.orFallback(fallback: String): String =
    if (this.isNullOrBlank() || this == "<unknown>") fallback else this

private fun android.database.Cursor.getStringOrNull(index: Int): String? =
    if (isNull(index)) null else getString(index)

private fun android.database.Cursor.getIntOrNull(index: Int): Int? =
    if (isNull(index)) null else getInt(index)

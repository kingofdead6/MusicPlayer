package dev.nk.musicplayer.data.playlist

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import dev.nk.musicplayer.data.db.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStreamWriter

/**
 * Writes a playlist as UTF-8 `.m3u8` into `Music/Playlists/`, with paths relative to that
 * folder so the file survives being copied to another device.
 */
class M3uExporter(private val context: Context) {

    private val playlistRelativeDir = "${Environment.DIRECTORY_MUSIC}/Playlists"

    suspend fun export(playlistName: String, tracks: List<Track>): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(tracks.isNotEmpty()) { "Playlist is empty" }
                val fileName = sanitizeFileName(playlistName) + ".m3u8"
                val locations = readFileLocations(tracks.map { it.id })
                val body = buildM3u(tracks, locations)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    writeViaMediaStore(fileName, body)
                } else {
                    writeLegacyFile(fileName, body)
                }
            }
        }

    // ---- content ---------------------------------------------------------------------

    private fun buildM3u(tracks: List<Track>, locations: Map<Long, String>): String = buildString {
        append("#EXTM3U\n")
        tracks.forEach { track ->
            val location = locations[track.id] ?: return@forEach
            append("#EXTINF:${track.durationMs / 1000},${track.artist} - ${track.title}\n")
            append(relativize(location))
            append('\n')
        }
    }

    /**
     * Both the playlist folder and the track live under the same volume root, so a relative
     * path is just "climb out of Music/Playlists, then walk down to the track".
     */
    private fun relativize(trackVolumePath: String): String {
        val from = playlistRelativeDir.trim('/').split('/')
        val to = trackVolumePath.trim('/').split('/')
        var shared = 0
        while (shared < from.size && shared < to.size && from[shared] == to[shared]) shared++
        val up = List(from.size - shared) { ".." }
        return (up + to.drop(shared)).joinToString("/")
    }

    /**
     * Volume-relative location ("Music/Rock/song.mp3") for each track. RELATIVE_PATH exists
     * from API 29; below that only the absolute DATA column does, which is then trimmed down
     * to the same shape.
     */
    private fun readFileLocations(ids: List<Long>): Map<Long, String> {
        val out = HashMap<Long, String>()
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.DISPLAY_NAME
            )
        } else {
            @Suppress("DEPRECATION")
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
        }

        // Chunked to stay under SQLite's bound-variable limit.
        ids.chunked(500).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media._ID} IN ($placeholders)",
                chunk.map { it.toString() }.toTypedArray(),
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val location = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val relative = cursor.getString(
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                        ).orEmpty()
                        val name = cursor.getString(
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                        ).orEmpty()
                        "${relative.trim('/')}/$name"
                    } else {
                        @Suppress("DEPRECATION")
                        val absolute = cursor.getString(
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                        ).orEmpty()
                        val root = Environment.getExternalStorageDirectory().absolutePath
                        absolute.removePrefix(root).trim('/')
                    }
                    if (location.isNotBlank()) out[id] = location
                }
            }
        }
        return out
    }

    // ---- writing ---------------------------------------------------------------------

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeViaMediaStore(fileName: String, body: String): String {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        // Replace an earlier export of the same playlist instead of piling up "name (1)".
        resolver.delete(
            collection,
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf("$playlistRelativeDir%", fileName)
        )

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/x-mpegurl")
            put(MediaStore.MediaColumns.RELATIVE_PATH, playlistRelativeDir)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: error("MediaStore refused to create $playlistRelativeDir/$fileName")

        resolver.openOutputStream(uri)?.use { stream ->
            OutputStreamWriter(stream, Charsets.UTF_8).use { it.write(body) }
        } ?: error("Could not open $fileName for writing")

        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null
        )
        return "$playlistRelativeDir/$fileName"
    }

    /**
     * API 26-28 has no RELATIVE_PATH, so the file is written directly and MediaStore is told
     * about it afterwards. Needs WRITE_EXTERNAL_STORAGE, which is only declared up to API 28.
     */
    private fun writeLegacyFile(fileName: String, body: String): String {
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStorageDirectory(), playlistRelativeDir)
        if (!dir.exists() && !dir.mkdirs()) error("Could not create $playlistRelativeDir")
        val file = File(dir, fileName)
        file.writeText(body, Charsets.UTF_8)

        android.media.MediaScannerConnection.scanFile(
            context, arrayOf(file.absolutePath), arrayOf("audio/x-mpegurl"), null
        )
        return "$playlistRelativeDir/$fileName"
    }

    private fun sanitizeFileName(name: String): String =
        name.trim()
            .replace(Regex("[/\\\\:*?\"<>|]"), "_")
            .take(80)
            .ifBlank { "playlist" }
}

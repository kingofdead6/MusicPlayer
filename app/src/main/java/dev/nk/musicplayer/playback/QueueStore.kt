package dev.nk.musicplayer.playback

import android.content.Context
import androidx.core.content.edit

/**
 * Last queue + position, so relaunching the app puts me back where I was.
 * SharedPreferences is plenty: it is a handful of longs written a few times a minute.
 */
class QueueStore(context: Context) {

    private val prefs = context.getSharedPreferences("queue", Context.MODE_PRIVATE)

    data class Saved(
        val trackIds: List<Long>,
        val index: Int,
        val positionMs: Long,
        val shuffle: Boolean,
        val repeatMode: Int,
        val source: String
    )

    fun save(saved: Saved) {
        prefs.edit {
            putString(KEY_IDS, saved.trackIds.joinToString(","))
            putInt(KEY_INDEX, saved.index)
            putLong(KEY_POSITION, saved.positionMs)
            putBoolean(KEY_SHUFFLE, saved.shuffle)
            putInt(KEY_REPEAT, saved.repeatMode)
            putString(KEY_SOURCE, saved.source)
        }
    }

    fun load(): Saved? {
        val raw = prefs.getString(KEY_IDS, null)?.takeIf { it.isNotBlank() } ?: return null
        val ids = raw.split(",").mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return null
        return Saved(
            trackIds = ids,
            index = prefs.getInt(KEY_INDEX, 0),
            positionMs = prefs.getLong(KEY_POSITION, 0L),
            shuffle = prefs.getBoolean(KEY_SHUFFLE, false),
            repeatMode = prefs.getInt(KEY_REPEAT, 0),
            source = prefs.getString(KEY_SOURCE, PlaySource.LIBRARY) ?: PlaySource.LIBRARY
        )
    }

    fun clear() = prefs.edit { clear() }

    private companion object {
        const val KEY_IDS = "ids"
        const val KEY_INDEX = "index"
        const val KEY_POSITION = "position"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_REPEAT = "repeat"
        const val KEY_SOURCE = "source"
    }
}

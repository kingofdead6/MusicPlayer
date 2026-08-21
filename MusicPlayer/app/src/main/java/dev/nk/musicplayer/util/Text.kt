package dev.nk.musicplayer.util

import java.text.Normalizer
import java.util.Locale

private val DIACRITICS = Regex("\\p{Mn}+")

/**
 * Lowercase + strip combining marks so that "Cafe" matches "Café".
 * Arabic text has no case and its letters are not combining marks, so it passes through
 * unchanged and still matches literally. Arabic *harakat* are combining marks and get
 * stripped, which makes search more forgiving, not less.
 */
fun normalizeForSearch(input: String): String =
    DIACRITICS.replace(Normalizer.normalize(input, Normalizer.Form.NFD), "")
        .lowercase(Locale.ROOT)
        .trim()

/** "3:42" / "1:02:07" */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%d:%02d", m, s)
}

/** "3 h 12 min" style, for durations shown as totals rather than clock positions. */
fun formatDurationLong(ms: Long): String {
    val totalMinutes = ms / 60_000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

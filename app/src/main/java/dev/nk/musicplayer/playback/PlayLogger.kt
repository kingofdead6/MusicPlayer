package dev.nk.musicplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.nk.musicplayer.data.db.PlayEvent
import dev.nk.musicplayer.data.db.PlayEventDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Writes one [PlayEvent] per track that finishes, gets skipped, or gets replaced.
 *
 * Listened time is taken from the player's own position at the moment the track is left,
 * which media3 hands over in `onPositionDiscontinuity`'s `oldPosition`. That is exact for an
 * end-of-track and for a skip; seeking forward inside a track and then leaving it will
 * over-count, which is a trade I'll take over polling the player twice a second.
 */
class PlayLogger(
    private val dao: PlayEventDao,
    private val scope: CoroutineScope
) : Player.Listener {

    private var trackId: Long? = null
    private var source: String = PlaySource.LIBRARY
    private var durationMs: Long = 0L
    private var startedAt: Long = 0L

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        val inTrackSeek = oldPosition.mediaItemIndex == newPosition.mediaItemIndex &&
            reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION
        // Repeat-one re-enters the same index via AUTO_TRANSITION, and that *is* a new play.
        if (inTrackSeek) return

        finish(oldPosition.positionMs)
        begin(newPosition.mediaItem)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // Covers the very first track of a fresh queue, which produces no discontinuity.
        if (trackId == null) begin(mediaItem)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        // End of the last track in the queue: nothing to transition to, so no discontinuity.
        if (playbackState == Player.STATE_ENDED) finish(durationMs)
    }

    /** Called when the service goes away, so the track in progress is not lost. */
    fun flush(positionMs: Long) = finish(positionMs)

    private fun begin(item: MediaItem?) {
        val entry = item?.toQueueEntry()
        trackId = entry?.trackId?.takeIf { it > 0 }
        source = entry?.source ?: PlaySource.LIBRARY
        durationMs = entry?.durationMs ?: 0L
        startedAt = System.currentTimeMillis()
    }

    private fun finish(positionMs: Long) {
        val id = trackId ?: return
        trackId = null

        val duration = durationMs
        val listened = if (duration > 0) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        // Queue churn (replacing the queue, clearing it) shouldn't look like listening.
        if (listened < MIN_LOGGED_MS) return

        val event = PlayEvent(
            trackId = id,
            startedAt = startedAt,
            listenedMs = listened,
            trackDurationMs = duration,
            completed = duration > 0 && listened >= duration * 0.9,
            skipped = duration > 0 && listened < duration * 0.3,
            source = source
        )
        scope.launch { dao.insert(event) }
    }

    private companion object {
        const val MIN_LOGGED_MS = 500L
    }
}

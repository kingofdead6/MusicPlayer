package dev.nk.musicplayer.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.nk.musicplayer.data.db.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val connected: Boolean = false,
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF
) {
    val current: QueueEntry? get() = queue.getOrNull(currentIndex)
    val hasQueue: Boolean get() = queue.isNotEmpty()
}

/**
 * The UI's single handle on playback. Owns a [MediaController] bound to [PlaybackService] and
 * republishes its state as a [StateFlow] so Compose can just read it.
 */
class PlayerConnection(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = runCatching { future.get() }.getOrNull()
            controller?.addListener(listener)
            publish()
            startTicker()
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        ticker?.cancel()
        ticker = null
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        _state.value = _state.value.copy(connected = false)
    }

    // ---- commands -------------------------------------------------------------------

    fun play(tracks: List<Track>, startIndex: Int, source: String) {
        val player = controller ?: return
        if (tracks.isEmpty()) return
        val items = tracks.map { it.toMediaItem(source) }
        player.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        val player = controller ?: return
        // Same behaviour as every other player: restart the track unless we are near the top.
        if (player.currentPosition > 3_000) player.seekTo(0) else player.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        publish()
    }

    fun skipToQueueIndex(index: Int) {
        val player = controller ?: return
        player.seekTo(index, 0L)
        player.play()
    }

    fun toggleShuffle() {
        val player = controller ?: return
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    /** off -> all -> one -> off */
    fun cycleRepeat() {
        val player = controller ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        controller?.moveMediaItem(from, to)
    }

    fun removeQueueItem(index: Int) {
        controller?.removeMediaItem(index)
    }

    fun addToQueue(tracks: List<Track>, source: String) {
        val player = controller ?: return
        val items: List<MediaItem> = tracks.map { it.toMediaItem(source) }
        player.addMediaItems(items)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
    }

    fun clearQueue() {
        controller?.clearMediaItems()
    }

    // ---- state ----------------------------------------------------------------------

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(500)
                val player = controller
                if (player != null && player.isPlaying) publish()
            }
        }
    }

    private fun publish() {
        val player = controller
        if (player == null) {
            _state.value = PlayerUiState()
            return
        }
        val queue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toQueueEntry() }
        _state.value = PlayerUiState(
            connected = true,
            queue = queue,
            currentIndex = player.currentMediaItemIndex.takeIf { queue.isNotEmpty() } ?: -1,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            // The player's own duration is authoritative once loaded; fall back to what
            // MediaStore told us so the seek bar has a scale before the file is opened.
            durationMs = player.duration.takeIf { it > 0 }
                ?: queue.getOrNull(player.currentMediaItemIndex)?.durationMs ?: 0L,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode
        )
    }
}

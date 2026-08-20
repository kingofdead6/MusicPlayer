package dev.nk.musicplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.nk.musicplayer.MainActivity
import dev.nk.musicplayer.MusicApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The only thing that ever owns an ExoPlayer. Everything in the UI talks to it through a
 * MediaController, which is what keeps playback alive with the app swiped away.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaSession: MediaSession? = null
    private lateinit var queueStore: QueueStore
    private lateinit var playLogger: PlayLogger

    override fun onCreate() {
        super.onCreate()
        val app = application as MusicApp
        queueStore = app.container.queueStore
        // The logger writes on the container's process-lifetime scope, so the final event
        // still lands after this service's own scope is cancelled.
        playLogger = PlayLogger(app.container.database.playEventDao(), app.container.ioScope)

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                // true = ExoPlayer requests audio focus, ducks/pauses for calls and other
                // apps, and resumes afterwards.
                true
            )
            // Pause when the headphones are yanked out.
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(PlayerEvents(player))
        player.addListener(playLogger)

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setCallback(ResolvingCallback)
            .build()

        restoreLastQueue(player)
        startPositionAutosave(player)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away should not stop music that is playing; if it is paused there
        // is nothing worth keeping a foreground service (and its notification) alive for.
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.let { session ->
            // Don't lose the track that was in progress when the service is torn down.
            playLogger.flush(session.player.currentPosition)
            persist(session.player)
            session.player.release()
            session.release()
        }
        mediaSession = null
        scope.cancel()
        super.onDestroy()
    }

    /**
     * MediaItems sent by a controller arrive without their `localConfiguration`, so the URI
     * has to be lifted back out of `requestMetadata` before the player sees them.
     */
    private object ResolvingCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                val uri = item.requestMetadata.mediaUri
                if (item.localConfiguration != null || uri == null) item
                else item.buildUpon().setUri(uri).build()
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }
    }

    private inner class PlayerEvents(private val player: Player) : Player.Listener {

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED
                )
            ) {
                persist(player)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // A file that disappeared from disk after the last scan: drop it from the queue,
            // remember that it is gone, and carry on with the next track.
            val index = player.currentMediaItemIndex
            val trackId = player.currentMediaItem?.mediaId?.toLongOrNull()
            Log.w(TAG, "playback error on track $trackId (${error.errorCodeName}), skipping", error)

            if (trackId != null && error.errorCode.isMissingFile()) {
                scope.launch {
                    (application as MusicApp).container.libraryRepository.markMissing(trackId)
                }
            }
            if (player.mediaItemCount > 1) {
                player.removeMediaItem(index)
                player.prepare()
                player.play()
            } else {
                player.clearMediaItems()
            }
        }
    }

    private fun Int.isMissingFile(): Boolean =
        this == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
            this == PlaybackException.ERROR_CODE_IO_NO_PERMISSION ||
            this == PlaybackException.ERROR_CODE_IO_UNSPECIFIED

    private fun restoreLastQueue(player: Player) {
        scope.launch {
            val saved = queueStore.load() ?: return@launch
            // A controller may have raced us here by starting playback immediately.
            if (player.mediaItemCount > 0) return@launch

            val repository = (application as MusicApp).container.libraryRepository
            val byId = repository.tracksByIds(saved.trackIds).associateBy { it.id }
            val items = saved.trackIds.mapNotNull { byId[it] }.map { it.toMediaItem(saved.source) }
            if (items.isEmpty() || player.mediaItemCount > 0) return@launch

            player.setMediaItems(items, saved.index.coerceIn(0, items.lastIndex), saved.positionMs)
            player.shuffleModeEnabled = saved.shuffle
            player.repeatMode = saved.repeatMode
            // Prepared but deliberately not started: restoring should not make noise.
            player.prepare()
        }
    }

    /** The listener catches structural changes; this catches "where in the track am I". */
    private fun startPositionAutosave(player: Player) {
        scope.launch {
            while (isActive) {
                delay(5_000)
                if (player.isPlaying) persist(player)
            }
        }
    }

    private fun persist(player: Player) {
        val count = player.mediaItemCount
        if (count == 0) {
            queueStore.clear()
            return
        }
        val ids = (0 until count).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }
        queueStore.save(
            QueueStore.Saved(
                trackIds = ids,
                index = player.currentMediaItemIndex,
                positionMs = player.currentPosition,
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                source = player.currentMediaItem?.toQueueEntry()?.source ?: PlaySource.LIBRARY
            )
        )
    }

    private companion object {
        const val TAG = "PlaybackService"
    }
}

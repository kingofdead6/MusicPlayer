package dev.nk.musicplayer

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import dev.nk.musicplayer.data.db.AppDatabase
import dev.nk.musicplayer.data.library.LibraryRepository
import dev.nk.musicplayer.data.library.MediaStoreScanner
import dev.nk.musicplayer.data.playlist.M3uExporter
import dev.nk.musicplayer.data.playlist.PlaylistRepository
import dev.nk.musicplayer.playback.PlayerConnection
import dev.nk.musicplayer.playback.QueueStore

/**
 * Manual dependency wiring. One user, one process, no DI framework: everything the app needs
 * is built lazily here and reached through [LocalContainer] or [MusicApp.container].
 */
class AppContainer(private val context: Context) {

    val database: AppDatabase by lazy { AppDatabase.build(context) }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(database.trackDao(), MediaStoreScanner(context))
    }

    val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepository(database.playlistDao())
    }

    val m3uExporter: M3uExporter by lazy { M3uExporter(context) }

    val queueStore: QueueStore by lazy { QueueStore(context) }

    /** Shared by the whole UI; connected while an Activity is started. */
    val playerConnection: PlayerConnection by lazy { PlayerConnection(context) }
}

val LocalContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}

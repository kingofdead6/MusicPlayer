package dev.nk.musicplayer

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import dev.nk.musicplayer.data.db.AppDatabase
import dev.nk.musicplayer.data.library.LibraryRepository
import dev.nk.musicplayer.data.library.MediaStoreScanner

/**
 * Manual dependency wiring. One user, one process, no DI framework: everything the app needs
 * is built lazily here and reached through [LocalContainer] or [MusicApp.container].
 */
class AppContainer(private val context: Context) {

    val database: AppDatabase by lazy { AppDatabase.build(context) }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(database.trackDao(), MediaStoreScanner(context))
    }
}

val LocalContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}

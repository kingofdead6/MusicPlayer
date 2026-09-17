package dev.nk.musicplayer

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import dev.nk.musicplayer.data.analysis.AnalysisRepository
import dev.nk.musicplayer.data.analysis.AudioSampler
import dev.nk.musicplayer.data.analysis.SongAnalyzer
import dev.nk.musicplayer.data.analysis.SpeechToTextClient
import dev.nk.musicplayer.data.analysis.SttConfig
import dev.nk.musicplayer.data.db.AppDatabase
import dev.nk.musicplayer.data.library.LibraryRepository
import dev.nk.musicplayer.data.library.MediaStoreScanner
import dev.nk.musicplayer.data.llm.AiPlaylistGenerator
import dev.nk.musicplayer.data.llm.LlmClient
import dev.nk.musicplayer.data.llm.LlmConfig
import dev.nk.musicplayer.data.playlist.M3uExporter
import dev.nk.musicplayer.data.playlist.PlaylistRepository
import dev.nk.musicplayer.data.settings.SettingsStore
import dev.nk.musicplayer.playback.PlayerConnection
import dev.nk.musicplayer.data.stats.StatsRepository
import dev.nk.musicplayer.playback.QueueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency wiring. One user, one process, no DI framework: everything the app needs
 * is built lazily here and reached through [LocalContainer] or [MusicApp.container].
 */
class AppContainer(private val context: Context) {

    /**
     * Process-lifetime scope for writes that must not be cancelled by whatever component
     * started them — notably the last play event as the playback service is torn down.
     */
    val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.build(context) }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(database.trackDao(), MediaStoreScanner(context))
    }

    val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepository(database.playlistDao())
    }

    val m3uExporter: M3uExporter by lazy { M3uExporter(context) }

    val statsRepository: StatsRepository by lazy { StatsRepository(database.playEventDao()) }

    /**
     * The only part of the app that touches the network. The API key is read from
     * [settingsStore] on each call, so a key entered in Settings works without a restart.
     */
    /** One client, shared by playlist curation and song classification. */
    private val llmClient: LlmClient by lazy {
        LlmClient { LlmConfig.forUserKey(settingsStore.hfApiKey.value) }
    }

    val aiPlaylistGenerator: AiPlaylistGenerator by lazy {
        AiPlaylistGenerator(libraryRepository, llmClient, database.songAnalysisDao())
    }

    /**
     * Listening: a sample of each file is transcribed, the transcript is classified, and the
     * verdict is cached in Room so every later playlist request is built on what the songs
     * are actually about rather than on their filenames.
     */
    val analysisRepository: AnalysisRepository by lazy {
        AnalysisRepository(
            analysisDao = database.songAnalysisDao(),
            trackDao = database.trackDao(),
            analyzer = SongAnalyzer(
                sampler = AudioSampler(context),
                stt = SpeechToTextClient { SttConfig.forUserKey(settingsStore.hfApiKey.value) },
                llm = llmClient,
                dao = database.songAnalysisDao()
            ),
            scope = ioScope
        )
    }

    val queueStore: QueueStore by lazy { QueueStore(context) }

    /** Appearance choices; read by the theme at the very top of the UI tree. */
    val settingsStore: SettingsStore by lazy { SettingsStore(context) }

    /** Shared by the whole UI; connected while an Activity is started. */
    val playerConnection: PlayerConnection by lazy { PlayerConnection(context) }
}

val LocalContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}

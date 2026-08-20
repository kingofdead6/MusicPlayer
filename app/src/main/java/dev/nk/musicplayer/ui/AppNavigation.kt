package dev.nk.musicplayer.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.nk.musicplayer.LocalContainer
import dev.nk.musicplayer.data.db.Track
import dev.nk.musicplayer.ui.components.MiniPlayer
import dev.nk.musicplayer.ui.library.AlbumDetailScreen
import dev.nk.musicplayer.ui.library.ArtistDetailScreen
import dev.nk.musicplayer.ui.library.LibraryScreen
import dev.nk.musicplayer.ui.library.LibraryViewModel
import dev.nk.musicplayer.ui.nowplaying.NowPlayingScreen
import dev.nk.musicplayer.ui.nowplaying.QueueScreen
import dev.nk.musicplayer.ui.permission.RequestNotificationPermissionOnce

object Routes {
    const val LIBRARY = "library"
    const val ARTIST = "artist/{artist}"
    const val ALBUM = "album/{albumId}"
    const val NOW_PLAYING = "nowPlaying"
    const val QUEUE = "queue"

    fun artist(name: String) = "artist/${Uri.encode(name)}"
    fun album(id: Long) = "album/$id"
}

/** Routes that own the full screen and therefore hide the mini player. */
private val FULL_SCREEN_ROUTES = setOf(Routes.NOW_PLAYING, Routes.QUEUE)

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val container = LocalContainer.current
    val navController = rememberNavController()
    val player = container.playerConnection
    val playerState by player.state.collectAsStateWithLifecycle()

    RequestNotificationPermissionOnce()

    // One LibraryViewModel for the whole graph: the paged song list and the scan state should
    // survive navigating into an album and back.
    val libraryViewModel: LibraryViewModel =
        viewModel(factory = LibraryViewModel.factory(container.libraryRepository))

    val onPlay: (List<Track>, Int, String) -> Unit = { tracks, index, source ->
        player.play(tracks, index, source)
    }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showMiniPlayer = playerState.current != null && currentRoute !in FULL_SCREEN_ROUTES

    Column(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.weight(1f)
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    viewModel = libraryViewModel,
                    onPlay = onPlay,
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
                    onOpenAlbum = { navController.navigate(Routes.album(it)) },
                    contentPadding = PaddingValues(bottom = 8.dp)
                )
            }
            composable(
                route = Routes.ARTIST,
                arguments = listOf(navArgument("artist") { type = NavType.StringType })
            ) { entry ->
                ArtistDetailScreen(
                    artist = entry.arguments?.getString("artist").orEmpty(),
                    viewModel = libraryViewModel,
                    onPlay = onPlay,
                    onBack = { navController.popBackStack() },
                    contentPadding = PaddingValues(bottom = 8.dp)
                )
            }
            composable(
                route = Routes.ALBUM,
                arguments = listOf(navArgument("albumId") { type = NavType.LongType })
            ) { entry ->
                AlbumDetailScreen(
                    albumId = entry.arguments?.getLong("albumId") ?: 0L,
                    viewModel = libraryViewModel,
                    onPlay = onPlay,
                    onBack = { navController.popBackStack() },
                    contentPadding = PaddingValues(bottom = 8.dp)
                )
            }
            composable(Routes.NOW_PLAYING) {
                NowPlayingScreen(
                    state = playerState,
                    onBack = { navController.popBackStack() },
                    onOpenQueue = { navController.navigate(Routes.QUEUE) },
                    onTogglePlayPause = player::togglePlayPause,
                    onNext = player::next,
                    onPrevious = player::previous,
                    onSeek = player::seekTo,
                    onToggleShuffle = player::toggleShuffle,
                    onCycleRepeat = player::cycleRepeat
                )
            }
            composable(Routes.QUEUE) {
                QueueScreen(
                    state = playerState,
                    onBack = { navController.popBackStack() },
                    onPlayIndex = player::skipToQueueIndex,
                    onRemove = player::removeQueueItem,
                    onMove = player::moveQueueItem,
                    onClear = player::clearQueue
                )
            }
        }

        if (showMiniPlayer) {
            MiniPlayer(
                state = playerState,
                onClick = { navController.navigate(Routes.NOW_PLAYING) },
                onTogglePlayPause = player::togglePlayPause,
                onNext = player::next
            )
        }
    }
}

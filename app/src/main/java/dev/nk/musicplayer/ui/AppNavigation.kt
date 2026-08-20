package dev.nk.musicplayer.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import dev.nk.musicplayer.playback.PlaySource
import dev.nk.musicplayer.ui.components.MiniPlayer
import dev.nk.musicplayer.ui.components.TrackActionsSheet
import dev.nk.musicplayer.ui.library.AlbumDetailScreen
import dev.nk.musicplayer.ui.library.ArtistDetailScreen
import dev.nk.musicplayer.ui.library.LibraryScreen
import dev.nk.musicplayer.ui.library.LibraryViewModel
import dev.nk.musicplayer.ui.nowplaying.NowPlayingScreen
import dev.nk.musicplayer.ui.nowplaying.QueueScreen
import dev.nk.musicplayer.ui.permission.RequestNotificationPermissionOnce
import dev.nk.musicplayer.ui.playlists.PlaylistDetailScreen
import dev.nk.musicplayer.ui.playlists.PlaylistsScreen

object Routes {
    const val LIBRARY = "library"
    const val PLAYLISTS = "playlists"
    const val ARTIST = "artist/{artist}"
    const val ALBUM = "album/{albumId}"
    const val PLAYLIST = "playlist/{playlistId}"
    const val NOW_PLAYING = "nowPlaying"
    const val QUEUE = "queue"

    fun artist(name: String) = "artist/${Uri.encode(name)}"
    fun album(id: Long) = "album/$id"
    fun playlist(id: Long) = "playlist/$id"
}

private enum class TopLevel(val route: String, val label: String, val icon: ImageVector) {
    Library(Routes.LIBRARY, "Library", Icons.Rounded.LibraryMusic),
    Playlists(Routes.PLAYLISTS, "Playlists", Icons.Rounded.PlaylistPlay)
}

/** Routes that own the full screen and therefore hide the mini player and the tab bar. */
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

    var actionTrack by remember { mutableStateOf<Track?>(null) }

    val onPlay: (List<Track>, Int, String) -> Unit = { tracks, index, source ->
        player.play(tracks, index, source)
    }
    val onTrackMenu: (Track) -> Unit = { actionTrack = it }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val fullScreen = currentRoute in FULL_SCREEN_ROUTES
    val showMiniPlayer = playerState.current != null && !fullScreen

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
                    onTrackMenu = onTrackMenu,
                    onOpenArtist = { navController.navigate(Routes.artist(it)) },
                    onOpenAlbum = { navController.navigate(Routes.album(it)) },
                    contentPadding = PaddingValues(bottom = 8.dp)
                )
            }
            composable(Routes.PLAYLISTS) {
                PlaylistsScreen(
                    onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
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
                    onTrackMenu = onTrackMenu,
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
                    onTrackMenu = onTrackMenu,
                    onBack = { navController.popBackStack() },
                    contentPadding = PaddingValues(bottom = 8.dp)
                )
            }
            composable(
                route = Routes.PLAYLIST,
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
            ) { entry ->
                PlaylistDetailScreen(
                    playlistId = entry.arguments?.getLong("playlistId") ?: 0L,
                    onBack = { navController.popBackStack() },
                    onPlay = onPlay,
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

        if (!fullScreen) {
            NavigationBar {
                TopLevel.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(Routes.LIBRARY) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    }

    actionTrack?.let { track ->
        TrackActionsSheet(
            track = track,
            onDismiss = { actionTrack = null },
            onAddToQueue = { player.addToQueue(listOf(it), PlaySource.LIBRARY) }
        )
    }
}

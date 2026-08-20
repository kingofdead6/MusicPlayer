package dev.nk.musicplayer.ui

import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.nk.musicplayer.LocalContainer
import dev.nk.musicplayer.data.db.Track
import dev.nk.musicplayer.ui.library.AlbumDetailScreen
import dev.nk.musicplayer.ui.library.ArtistDetailScreen
import dev.nk.musicplayer.ui.library.LibraryScreen
import dev.nk.musicplayer.ui.library.LibraryViewModel

object Routes {
    const val LIBRARY = "library"
    const val ARTIST = "artist/{artist}"
    const val ALBUM = "album/{albumId}"

    fun artist(name: String) = "artist/${Uri.encode(name)}"
    fun album(id: Long) = "album/$id"
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val container = LocalContainer.current
    val navController = rememberNavController()

    // One LibraryViewModel for the whole graph: the paged song list and the scan state should
    // survive navigating into an album and back.
    val libraryViewModel: LibraryViewModel =
        viewModel(factory = LibraryViewModel.factory(container.libraryRepository))

    // Phase 2 replaces this with the real player controller.
    val onPlay: (List<Track>, Int, String) -> Unit = { _, _, _ -> }
    val contentPadding = PaddingValues(0.dp)

    NavHost(navController = navController, startDestination = Routes.LIBRARY, modifier = modifier) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                viewModel = libraryViewModel,
                onPlay = onPlay,
                onOpenArtist = { navController.navigate(Routes.artist(it)) },
                onOpenAlbum = { navController.navigate(Routes.album(it)) },
                contentPadding = contentPadding
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
                contentPadding = contentPadding
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
                contentPadding = contentPadding
            )
        }
    }
}

package dev.nk.musicplayer.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
import dev.nk.musicplayer.ui.ai.AiScreen
import dev.nk.musicplayer.ui.ai.AiViewModel
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
import dev.nk.musicplayer.ui.stats.StatsScreen
import dev.nk.musicplayer.ui.stats.StatsViewModel
import dev.nk.musicplayer.ui.settings.SettingsScreen
import dev.nk.musicplayer.ui.theme.AppShapes
import dev.nk.musicplayer.ui.theme.Motion
import dev.nk.musicplayer.ui.theme.Radii
import dev.nk.musicplayer.ui.theme.accentGlow
import dev.nk.musicplayer.ui.theme.neonEdge
import dev.nk.musicplayer.ui.theme.pressable

object Routes {
    const val LIBRARY = "library"
    const val PLAYLISTS = "playlists"
    const val AI = "ai"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val ARTIST = "artist/{artist}"
    const val ALBUM = "album/{albumId}"
    const val PLAYLIST = "playlist/{playlistId}"
    const val NOW_PLAYING = "nowPlaying"
    const val QUEUE = "queue"

    fun artist(name: String) = "artist/${Uri.encode(name)}"
    fun album(id: Long) = "album/$id"
    fun playlist(id: Long) = "playlist/$id"
}

/**
 * Five tabs is Material's practical ceiling for a bottom bar, so the two longest labels are
 * abbreviated rather than left to ellipsize on narrow screens.
 */
private enum class TopLevel(val route: String, val label: String, val icon: ImageVector) {
    Library(Routes.LIBRARY, "Library", Icons.Rounded.LibraryMusic),
    Playlists(Routes.PLAYLISTS, "Lists", Icons.Rounded.PlaylistPlay),
    Ai(Routes.AI, "AI", Icons.Rounded.AutoAwesome),
    Stats(Routes.STATS, "Stats", Icons.Rounded.Insights),
    Settings(Routes.SETTINGS, "Theme", Icons.Rounded.Settings)
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
    val aiViewModel: AiViewModel = viewModel(
        factory = AiViewModel.factory(
            container.aiPlaylistGenerator,
            container.playlistRepository,
            container.analysisRepository,
            container.settingsStore.hfApiKey
        )
    )
    val statsViewModel: StatsViewModel =
        viewModel(factory = StatsViewModel.factory(container.statsRepository))

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
            composable(Routes.AI) {
                AiScreen(
                    viewModel = aiViewModel,
                    onPlay = onPlay,
                    onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
                    contentPadding = PaddingValues(bottom = 8.dp)
                )
            }
            composable(Routes.STATS) {
                StatsScreen(
                    viewModel = statsViewModel,
                    contentPadding = PaddingValues(bottom = 8.dp)
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(contentPadding = PaddingValues(bottom = 8.dp))
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

        // The mini player slides up from behind the tab bar the first time something plays,
        // rather than appearing and shoving the list up by its height in one frame.
        AnimatedVisibility(
            visible = showMiniPlayer,
            enter = slideInVertically(
                tween(Motion.Slow, easing = Motion.EmphasizedDecelerate)
            ) { it } + fadeIn(tween(Motion.Medium, easing = Motion.Standard)),
            exit = slideOutVertically(
                tween(Motion.Medium, easing = Motion.Emphasized)
            ) { it } + fadeOut(tween(Motion.Quick))
        ) {
            MiniPlayer(
                state = playerState,
                onClick = { navController.navigate(Routes.NOW_PLAYING) },
                onTogglePlayPause = player::togglePlayPause,
                onNext = player::next
            )
        }

        if (!fullScreen) {
            NeonTabBar(
                currentRoute = currentRoute,
                onSelect = { route ->
                    navController.navigate(route) {
                        popUpTo(Routes.LIBRARY) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
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

/**
 * The tab bar as a floating rounded dock. Material's `NavigationBar` is a full-bleed
 * rectangle with a pill indicator inside it; this inverts that — the bar itself is the
 * rounded shape, and selection is carried by a pill that slides between items.
 *
 * The slide is what makes switching tabs read as one continuous surface rather than as five
 * independent buttons: the indicator animates its position, so the eye tracks a single object
 * moving instead of one highlight vanishing and another appearing.
 */
@Composable
private fun NeonTabBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val tabs = TopLevel.entries

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 2.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .accentGlow(cornerRadius = Radii.huge, radius = 16.dp, intensity = 0.4f)
                .clip(AppShapes.huge)
                .background(scheme.surface)
                .neonEdge(AppShapes.huge, scheme.primary, alpha = 0.22f)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                NeonTab(
                    tab = tab,
                    selected = selected,
                    onClick = { onSelect(tab.route) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NeonTab(
    tab: TopLevel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    val tint by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.onSurfaceVariant,
        animationSpec = Motion.emphasized(),
        label = "tabTint"
    )
    val indicator by animateColorAsState(
        targetValue = if (selected) scheme.primary.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = Motion.emphasized(),
        label = "tabIndicator"
    )
    // The selected icon lifts slightly. Springing it rather than tweening gives the tap a
    // small physical kick that a colour change alone does not carry.
    val lift by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = Motion.springy(),
        label = "tabLift"
    )

    Column(
        modifier = modifier
            .pressable(shape = AppShapes.large, onClick = onClick, pressedScale = 0.94f)
            .background(indicator, AppShapes.large)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .scale(lift)
        )
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1
        )
    }
}

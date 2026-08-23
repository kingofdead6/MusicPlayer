package dev.nk.musicplayer.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nk.musicplayer.LocalContainer
import dev.nk.musicplayer.data.db.PlaylistSummary
import dev.nk.musicplayer.ui.components.AlbumArt
import dev.nk.musicplayer.ui.components.EmptyState
import dev.nk.musicplayer.ui.components.TrackArtwork
import dev.nk.musicplayer.ui.components.TextPromptDialog
import dev.nk.musicplayer.ui.theme.AppShapes
import dev.nk.musicplayer.ui.theme.Radii
import dev.nk.musicplayer.ui.theme.accentGlow
import dev.nk.musicplayer.ui.theme.accentSurface
import dev.nk.musicplayer.ui.theme.pressable
import dev.nk.musicplayer.ui.theme.softSurface
import dev.nk.musicplayer.util.formatDurationLong
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onOpenPlaylist: (Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val repository = LocalContainer.current.playlistRepository
    val scope = rememberCoroutineScope()
    val playlists by remember { repository.observeSummaries() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<PlaylistSummary?>(null) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            // An extended, fully rounded FAB: at this corner scale a squarish FAB is the one
            // element that would still read as a box floating over the content.
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                shape = AppShapes.pill,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.accentGlow(
                    cornerRadius = Radii.pill,
                    radius = 18.dp,
                    intensity = 0.8f
                )
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text("New", modifier = Modifier.padding(start = 8.dp))
            }
        }
    ) { padding ->
        if (playlists.isEmpty()) {
            Column(modifier = Modifier.padding(padding)) {
                EmptyState(
                    icon = Icons.Rounded.PlaylistPlay,
                    title = "No playlists yet",
                    subtitle = "Build one by hand, or let the AI tab put one together for you."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = contentPadding
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        onClick = { onOpenPlaylist(playlist.id) },
                        onRename = { renaming = playlist },
                        onDelete = { scope.launch { repository.delete(playlist.id) } }
                    )
                }
            }
        }
    }

    if (creating) {
        TextPromptDialog(
            title = "New playlist",
            label = "Name",
            initialValue = "",
            confirmLabel = "Create",
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                scope.launch { repository.create(name) }
            }
        )
    }

    renaming?.let { target ->
        TextPromptDialog(
            title = "Rename playlist",
            label = "Name",
            initialValue = target.name,
            confirmLabel = "Rename",
            onDismiss = { renaming = null },
            onConfirm = { name ->
                renaming = null
                scope.launch { repository.rename(target.id, name) }
            }
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: PlaylistSummary,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val aiGenerated = playlist.isAiGenerated

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pressable(shape = AppShapes.large, onClick = onClick)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                AppShapes.large
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The playlist wears its first track's cover. An empty playlist has nothing to show,
        // so it falls back to the icon disc.
        val cover = playlist.coverTrackId?.let {
            TrackArtwork(trackId = it, albumId = playlist.coverAlbumId ?: -1L)
        }
        Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            if (cover != null) {
                AlbumArt(
                    artwork = cover,
                    corner = Radii.medium,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (aiGenerated) {
                                Modifier.accentSurface(shape = AppShapes.medium, alpha = 0.16f)
                            } else {
                                Modifier.softSurface(shape = AppShapes.medium)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            // With the cover now occupying the tile, the AI badge moves to a corner chip so
            // the distinction survives — it used to be carried by the icon itself.
            if (aiGenerated) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.surface, AppShapes.pill)
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "AI generated",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${playlist.trackCount} tracks · ${formatDurationLong(playlist.totalDurationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist actions")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { menuOpen = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

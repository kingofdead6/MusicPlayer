package dev.nk.musicplayer.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.FloatingActionButton
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
import dev.nk.musicplayer.ui.components.EmptyState
import dev.nk.musicplayer.ui.components.TextPromptDialog
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
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "New playlist")
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (playlist.isAiGenerated) Icons.Rounded.AutoAwesome else Icons.Rounded.PlaylistPlay,
            contentDescription = null,
            tint = if (playlist.isAiGenerated) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
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

package dev.nk.musicplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import dev.nk.musicplayer.data.db.Track
import dev.nk.musicplayer.ui.theme.AppShapes
import dev.nk.musicplayer.ui.theme.pressable
import kotlinx.coroutines.launch

/**
 * The "⋮" sheet on a track: queue it, or file it into a playlist (creating one on the spot).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsSheet(
    track: Track,
    onDismiss: () -> Unit,
    onAddToQueue: (Track) -> Unit
) {
    val repository = LocalContainer.current.playlistRepository
    val scope = rememberCoroutineScope()
    val playlists by remember { repository.observeSummaries() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var choosingPlaylist by remember { mutableStateOf(false) }
    var creatingPlaylist by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = AppShapes.bottomDock,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            if (!choosingPlaylist) {
                SheetRow(Icons.Rounded.QueueMusic, "Add to queue") {
                    onAddToQueue(track)
                    onDismiss()
                }
                SheetRow(Icons.Rounded.PlaylistAdd, "Add to playlist") {
                    choosingPlaylist = true
                }
            } else {
                SheetRow(Icons.Rounded.Add, "New playlist…") { creatingPlaylist = true }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        SheetRow(Icons.Rounded.PlaylistAdd, playlist.name) {
                            scope.launch { repository.addTracks(playlist.id, listOf(track.id)) }
                            onDismiss()
                        }
                    }
                }
            }
        }
    }

    if (creatingPlaylist) {
        TextPromptDialog(
            title = "New playlist",
            label = "Name",
            initialValue = "",
            confirmLabel = "Create",
            onDismiss = { creatingPlaylist = false },
            onConfirm = { name ->
                creatingPlaylist = false
                scope.launch {
                    val id = repository.create(name)
                    repository.addTracks(id, listOf(track.id))
                }
                onDismiss()
            }
        )
    }
}

@Composable
private fun SheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .pressable(shape = AppShapes.large, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            label,
            modifier = Modifier.padding(start = 16.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

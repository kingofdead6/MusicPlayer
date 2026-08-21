package dev.nk.musicplayer.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nk.musicplayer.LocalContainer
import dev.nk.musicplayer.data.db.Track
import dev.nk.musicplayer.playback.PlaySource
import dev.nk.musicplayer.ui.components.AlbumArt
import dev.nk.musicplayer.ui.components.EmptyState
import dev.nk.musicplayer.ui.components.rememberReorderState
import dev.nk.musicplayer.ui.components.reorderable
import dev.nk.musicplayer.util.formatDuration
import dev.nk.musicplayer.util.formatDurationLong
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    onPlay: (List<Track>, Int, String) -> Unit,
    contentPadding: PaddingValues
) {
    val container = LocalContainer.current
    val repository = container.playlistRepository
    val exporter = container.m3uExporter
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val playlist by remember(playlistId) { repository.observePlaylist(playlistId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val storedTracks by remember(playlistId) { repository.observeTracks(playlistId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // A drag mutates this local copy immediately and the new order is written to Room when
    // the finger lifts; otherwise every swap would round-trip through the database.
    var tracks by remember(playlistId) { mutableStateOf(storedTracks) }
    var dragging by remember(playlistId) { mutableStateOf(false) }
    LaunchedEffect(storedTracks) {
        if (!dragging) tracks = storedTracks
    }

    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState) { from, to ->
        dragging = true
        val header = 1 // the header item occupies list index 0
        val fromIndex = from - header
        val toIndex = to - header
        if (fromIndex in tracks.indices && toIndex in tracks.indices) {
            tracks = tracks.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(playlist?.name ?: "Playlist", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = tracks.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                val name = playlist?.name ?: "playlist"
                                exporter.export(name, tracks)
                                    .onSuccess { snackbar.showSnackbar("Exported to $it") }
                                    .onFailure { snackbar.showSnackbar("Export failed: ${it.message}") }
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.IosShare, contentDescription = "Export as .m3u8")
                    }
                }
            )
        }
    ) { padding ->
        if (tracks.isEmpty()) {
            Column(modifier = Modifier.padding(padding)) {
                EmptyState(
                    icon = Icons.Rounded.PlaylistPlay,
                    title = "This playlist is empty",
                    subtitle = "Add tracks from the library with the ⋮ menu on any song."
                )
            }
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .reorderable(reorder)
        ) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "${tracks.size} tracks · ${formatDurationLong(tracks.sumOf { it.durationMs })}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (playlist?.sourcePrompt != null) {
                        Text(
                            "“${playlist?.sourcePrompt}”",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(onClick = { onPlay(tracks, 0, PlaySource.playlist(playlistId)) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Play", modifier = Modifier.padding(start = 6.dp))
                        }
                        FilledTonalButton(onClick = {
                            onPlay(tracks.shuffled(), 0, PlaySource.playlist(playlistId))
                        }) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Shuffle", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }

            itemsIndexed(tracks, key = { index, track -> "${track.id}@$index" }) { index, track ->
                val listIndex = index + 1
                val isDragging = reorder.draggingIndex == listIndex
                Row(
                    modifier = Modifier
                        .graphicsLayer { translationY = if (isDragging) reorder.dragOffset else 0f }
                        .then(if (isDragging) Modifier.shadow(6.dp) else Modifier)
                        .fillMaxWidth()
                        .background(
                            if (isDragging) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickable { onPlay(tracks, index, PlaySource.playlist(playlistId)) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.DragHandle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AlbumArt(
                        albumId = track.albumId,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(44.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${track.artist} · ${formatDuration(track.durationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = {
                        scope.launch { repository.removeTrack(playlistId, track.id) }
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove from playlist")
                    }
                }
            }
        }
    }

    // Persist the new order once the drag finishes.
    LaunchedEffect(reorder.draggingIndex) {
        if (reorder.draggingIndex == null && dragging) {
            dragging = false
            repository.reorder(playlistId, tracks.map { it.id })
        }
    }
}

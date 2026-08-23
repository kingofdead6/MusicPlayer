package dev.nk.musicplayer.ui.nowplaying

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nk.musicplayer.playback.PlayerUiState
import dev.nk.musicplayer.ui.components.AlbumArt
import dev.nk.musicplayer.ui.components.TrackArtwork
import dev.nk.musicplayer.ui.components.EmptyState
import dev.nk.musicplayer.ui.components.rememberReorderState
import dev.nk.musicplayer.ui.components.reorderable
import dev.nk.musicplayer.ui.theme.AppShapes
import dev.nk.musicplayer.ui.theme.Motion
import dev.nk.musicplayer.ui.theme.Radii
import dev.nk.musicplayer.ui.theme.pressable
import dev.nk.musicplayer.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    state: PlayerUiState,
    onBack: () -> Unit,
    onPlayIndex: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit
) {
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState, onMove)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Queue") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.hasQueue) {
                        TextButton(onClick = onClear, shape = AppShapes.pill) { Text("Clear") }
                    }
                }
            )
        }
    ) { padding ->
        if (!state.hasQueue) {
            Column(modifier = Modifier.padding(padding)) {
                EmptyState(
                    icon = Icons.Rounded.QueueMusic,
                    title = "The queue is empty",
                    subtitle = "Play something from the library and it will show up here."
                )
            }
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .reorderable(reorder)
        ) {
            // Index-for-index with state.queue: the reorder helper maps list positions
            // straight onto queue positions, so nothing else may live in this list.
            itemsIndexed(state.queue, key = { index, entry -> "${entry.trackId}@$index" }) { index, entry ->
                val dragging = reorder.draggingIndex == index
                val playing = index == state.currentIndex

                // A lifted tile while dragging: the elevation and the brighter fill together
                // make the dragged row read as picked up off the list rather than as a row
                // that merely changed colour.
                val lift by animateDpAsState(
                    targetValue = if (dragging) 10.dp else 0.dp,
                    animationSpec = Motion.emphasized(Motion.Quick),
                    label = "queueLift"
                )
                val fill by animateColorAsState(
                    targetValue = when {
                        dragging -> MaterialTheme.colorScheme.surfaceVariant
                        playing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    animationSpec = Motion.emphasized(),
                    label = "queueFill"
                )

                Row(
                    modifier = Modifier
                        .graphicsLayer {
                            translationY = if (dragging) reorder.dragOffset else 0f
                        }
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                        .shadow(lift, AppShapes.large)
                        .pressable(shape = AppShapes.large, onClick = { onPlayIndex(index) })
                        .background(fill, AppShapes.large)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.DragHandle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AlbumArt(
                        artwork = TrackArtwork(entry.trackId, entry.albumId),
                        corner = Radii.medium,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(46.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (playing) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${entry.artist} · ${formatDuration(entry.durationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove from queue")
                    }
                }
            }
        }
    }
}

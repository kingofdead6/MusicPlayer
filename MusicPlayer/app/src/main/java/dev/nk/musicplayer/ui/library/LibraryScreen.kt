package dev.nk.musicplayer.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.nk.musicplayer.data.db.Track
import dev.nk.musicplayer.playback.PlaySource
import dev.nk.musicplayer.ui.components.AlbumArt
import dev.nk.musicplayer.ui.components.EmptyState
import dev.nk.musicplayer.ui.components.SegmentedTabs
import dev.nk.musicplayer.ui.components.TrackRow
import dev.nk.musicplayer.ui.theme.AppShapes
import dev.nk.musicplayer.ui.theme.Motion
import dev.nk.musicplayer.ui.theme.accentSurface
import dev.nk.musicplayer.ui.theme.pressable
import kotlinx.coroutines.launch

private enum class LibraryTab(val label: String) { Songs("Songs"), Artists("Artists"), Albums("Albums") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onPlay: (tracks: List<Track>, index: Int, source: String) -> Unit,
    onTrackMenu: (Track) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableIntStateOf(0) }
    val query by viewModel.query.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val trackCount by viewModel.trackCount.collectAsStateWithLifecycle()
    val lastScan by viewModel.lastScan.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        // A pill search field with the border suppressed: at this radius a visible outline
        // reads as a hard capsule outline rather than as a soft input, so the fill carries
        // the shape instead.
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            placeholder = { Text("Search title, artist, album") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn(tween(Motion.Quick)) + scaleIn(Motion.springy()),
                    exit = fadeOut(tween(Motion.Quick)) + scaleOut(tween(Motion.Quick))
                ) {
                    IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = AppShapes.pill,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                unfocusedBorderColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        SegmentedTabs(
            labels = LibraryTab.entries.map { it.label },
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (lastScan != null) {
            Text(
                text = lastScan!!,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::rescan,
            modifier = Modifier.fillMaxSize()
        ) {
            when (LibraryTab.entries[tab]) {
                LibraryTab.Songs -> SongsTab(viewModel, trackCount, query, contentPadding, onPlay, onTrackMenu)
                LibraryTab.Artists -> ArtistsTab(viewModel, contentPadding, onOpenArtist)
                LibraryTab.Albums -> AlbumsTab(viewModel, contentPadding, onOpenAlbum)
            }
        }
    }
}

@Composable
private fun SongsTab(
    viewModel: LibraryViewModel,
    trackCount: Int,
    query: String,
    contentPadding: PaddingValues,
    onPlay: (List<Track>, Int, String) -> Unit,
    onTrackMenu: (Track) -> Unit
) {
    val items = viewModel.pagedTracks.collectAsLazyPagingItems()
    val scope = rememberCoroutineScope()

    // Don't flash "no music" while the first page is still being fetched.
    if (items.loadState.refresh is LoadState.Loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (items.itemCount == 0) {
        EmptyState(
            icon = Icons.Rounded.LibraryMusic,
            title = if (query.isBlank()) "No music found" else "Nothing matches \"$query\"",
            subtitle = if (query.isBlank()) {
                "Nothing on this device looks like a music file longer than 30 seconds. " +
                    "Pull down to scan again."
            } else null
        )
        return
    }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$trackCount tracks",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(shape = AppShapes.pill, onClick = {
                    scope.launch {
                        val all = viewModel.shuffleQueue()
                        if (all.isNotEmpty()) onPlay(all, 0, PlaySource.SHUFFLE)
                    }
                }) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Shuffle all", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
            val track = items[index]
            if (track != null) {
                TrackRow(
                    track = track,
                    onClick = {
                        scope.launch {
                            val (queue, start) = viewModel.queueForSongsList(track.id)
                            if (queue.isNotEmpty()) onPlay(queue, start, PlaySource.LIBRARY)
                        }
                    },
                    trailing = {
                        IconButton(onClick = { onTrackMenu(track) }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Track actions")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ArtistsTab(
    viewModel: LibraryViewModel,
    contentPadding: PaddingValues,
    onOpenArtist: (String) -> Unit
) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    if (artists.isEmpty()) {
        EmptyState(icon = Icons.Rounded.Person, title = "No artists yet")
        return
    }
    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        items(artists, key = { it.artist }) { artist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 3.dp)
                    .pressable(shape = AppShapes.large, onClick = { onOpenArtist(artist.artist) })
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The initial in a lit disc gives artists the same visual weight as the album
                // tiles beside them, instead of a bare glyph floating in the row.
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .accentSurface(shape = AppShapes.pill, alpha = 0.12f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = artist.artist.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(artist.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${artist.trackCount} tracks · ${artist.albumCount} albums",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumsTab(
    viewModel: LibraryViewModel,
    contentPadding: PaddingValues,
    onOpenAlbum: (Long) -> Unit
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    if (albums.isEmpty()) {
        EmptyState(icon = Icons.Rounded.Album, title = "No albums yet")
        return
    }
    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        items(albums, key = { it.albumId }) { album ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 3.dp)
                    .pressable(shape = AppShapes.large, onClick = { onOpenAlbum(album.albumId) })
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArt(albumId = album.albumId, modifier = Modifier.size(52.dp))
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(album.album, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${album.artist} · ${album.trackCount} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

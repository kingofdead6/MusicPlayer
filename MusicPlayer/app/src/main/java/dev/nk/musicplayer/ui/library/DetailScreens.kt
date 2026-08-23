package dev.nk.musicplayer.ui.library

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nk.musicplayer.data.db.Track
import dev.nk.musicplayer.playback.PlaySource
import dev.nk.musicplayer.ui.components.AlbumArt
import dev.nk.musicplayer.ui.components.TrackArtwork
import dev.nk.musicplayer.ui.components.albumArtwork
import dev.nk.musicplayer.ui.components.TrackRow
import dev.nk.musicplayer.ui.theme.AppShapes
import dev.nk.musicplayer.ui.theme.Radii
import dev.nk.musicplayer.ui.theme.accentGlow
import dev.nk.musicplayer.ui.theme.softSurface
import dev.nk.musicplayer.util.formatDurationLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artist: String,
    viewModel: LibraryViewModel,
    onPlay: (List<Track>, Int, String) -> Unit,
    onTrackMenu: (Track) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues
) {
    val tracks by produceState(initialValue = emptyList<Track>(), artist) {
        value = viewModel.tracksByArtist(artist)
    }
    TrackListDetail(
        title = artist,
        subtitle = "${tracks.size} tracks · ${formatDurationLong(tracks.sumOf { it.durationMs })}",
        artworkAlbumId = tracks.firstOrNull()?.albumId,
        tracks = tracks,
        onPlay = onPlay,
        onTrackMenu = onTrackMenu,
        onBack = onBack,
        contentPadding = contentPadding
    )
}

@Composable
fun AlbumDetailScreen(
    albumId: Long,
    viewModel: LibraryViewModel,
    onPlay: (List<Track>, Int, String) -> Unit,
    onTrackMenu: (Track) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues
) {
    val tracks by produceState(initialValue = emptyList<Track>(), albumId) {
        value = viewModel.tracksByAlbum(albumId)
    }
    val first = tracks.firstOrNull()
    TrackListDetail(
        title = first?.album ?: "Album",
        subtitle = listOfNotNull(
            first?.artist,
            first?.year?.toString(),
            "${tracks.size} tracks"
        ).joinToString(" · "),
        artworkAlbumId = albumId,
        tracks = tracks,
        onPlay = onPlay,
        onTrackMenu = onTrackMenu,
        onBack = onBack,
        contentPadding = contentPadding
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackListDetail(
    title: String,
    subtitle: String,
    artworkAlbumId: Long?,
    tracks: List<Track>,
    onPlay: (List<Track>, Int, String) -> Unit,
    onTrackMenu: (Track) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = inner.calculateTopPadding())
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .softSurface(shape = AppShapes.xlarge)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val headerArt = tracks.firstOrNull()?.let(TrackArtwork::of)
                        ?: artworkAlbumId?.let(::albumArtwork)
                    if (headerArt != null) {
                        AlbumArt(
                            artwork = headerArt,
                            corner = Radii.large,
                            modifier = Modifier
                                .size(92.dp)
                                .accentGlow(cornerRadius = Radii.large, radius = 18.dp, intensity = 0.5f)
                        )
                    }
                    Column(
                        modifier = Modifier.padding(start = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { if (tracks.isNotEmpty()) onPlay(tracks, 0, PlaySource.LIBRARY) },
                                enabled = tracks.isNotEmpty(),
                                shape = AppShapes.pill
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                                Text("Play", modifier = Modifier.padding(start = 6.dp))
                            }
                            FilledTonalButton(
                                onClick = {
                                    if (tracks.isNotEmpty()) onPlay(tracks.shuffled(), 0, PlaySource.SHUFFLE)
                                },
                                enabled = tracks.isNotEmpty(),
                                shape = AppShapes.pill
                            ) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                                Text("Shuffle", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            }
            items(tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    onClick = { onPlay(tracks, tracks.indexOf(track), PlaySource.LIBRARY) },
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

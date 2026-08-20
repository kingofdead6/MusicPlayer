package dev.nk.musicplayer.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nk.musicplayer.data.db.Track
import dev.nk.musicplayer.playback.PlaySource
import dev.nk.musicplayer.util.formatDuration
import dev.nk.musicplayer.util.formatDurationLong

private val PRESETS = listOf(30 to "30m", 60 to "1h", 120 to "2h", 180 to "3h")

@Composable
fun AiScreen(
    viewModel: AiViewModel,
    onPlay: (List<Track>, Int, String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Describe the playlist", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Built only from tracks already on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = viewModel::setPrompt,
                    placeholder = { Text("e.g. traveling playlist, calm, nothing too heavy") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PRESETS.forEach { (minutes, label) ->
                        FilterChip(
                            selected = !state.customSelected && state.presetMinutes == minutes,
                            onClick = { viewModel.selectPreset(minutes) },
                            label = { Text(label) }
                        )
                    }
                    FilterChip(
                        selected = state.customSelected,
                        onClick = { viewModel.selectCustom() },
                        label = { Text("Custom") }
                    )
                    if (state.customSelected) {
                        OutlinedTextField(
                            value = state.customMinutes,
                            onValueChange = viewModel::setCustomMinutes,
                            label = { Text("min") },
                            singleLine = true,
                            modifier = Modifier.width(110.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = viewModel::generate,
                    enabled = state.canGenerate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text("Curating…", modifier = Modifier.padding(start = 12.dp))
                    } else {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Text("Generate", modifier = Modifier.padding(start = 8.dp))
                    }
                }

                if (!state.configured) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No model configured. Add LLM_BASE_URL, LLM_API_KEY and LLM_MODEL to " +
                            "local.properties and rebuild. Everything else in the app works " +
                            "without it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                state.error?.let { error ->
                    Spacer(Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }

        val preview = state.preview
        if (preview != null) {
            item {
                PreviewHeader(
                    preview = preview,
                    onShufflePlay = { onPlay(preview.tracks.shuffled(), 0, PlaySource.SHUFFLE) },
                    onRegenerate = viewModel::generate,
                    onSave = viewModel::save,
                    onOpenSaved = { preview.savedPlaylistId?.let(onOpenPlaylist) },
                    regenerating = state.loading
                )
            }
            itemsIndexed(preview.tracks, key = { index, track -> "${track.id}@$index" }) { index, track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(28.dp)
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
                }
            }
        }
    }
}

@Composable
private fun PreviewHeader(
    preview: AiPreview,
    onShufflePlay: () -> Unit,
    onRegenerate: () -> Unit,
    onSave: () -> Unit,
    onOpenSaved: () -> Unit,
    regenerating: Boolean
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(preview.name, style = MaterialTheme.typography.titleLarge)
        if (preview.reasoning.isNotBlank()) {
            Text(
                preview.reasoning,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "${preview.tracks.size} tracks · ${formatDurationLong(preview.totalDurationMs)} · not saved yet",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onShufflePlay) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Shuffle & play", modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = onRegenerate, enabled = !regenerating) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Regenerate", modifier = Modifier.padding(start = 6.dp))
            }
        }

        if (preview.savedPlaylistId == null) {
            OutlinedButton(onClick = onSave, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Save as playlist", modifier = Modifier.padding(start = 6.dp))
            }
        } else {
            OutlinedButton(onClick = onOpenSaved, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Saved — open playlist", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

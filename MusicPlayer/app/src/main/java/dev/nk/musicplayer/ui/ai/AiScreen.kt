package dev.nk.musicplayer.ui.ai

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nk.musicplayer.data.analysis.AnalysisRepository
import dev.nk.musicplayer.data.db.SongAnalysis
import dev.nk.musicplayer.data.db.Track
import dev.nk.musicplayer.playback.PlaySource
import dev.nk.musicplayer.ui.theme.AppShapes
import dev.nk.musicplayer.ui.theme.Motion
import dev.nk.musicplayer.ui.theme.Radii
import dev.nk.musicplayer.ui.theme.accentGlow
import dev.nk.musicplayer.ui.theme.accentSurface
import dev.nk.musicplayer.ui.theme.glassPanel
import dev.nk.musicplayer.ui.theme.pressable
import dev.nk.musicplayer.ui.theme.softSurface
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
    val coverage by viewModel.coverage.collectAsStateWithLifecycle()
    val analysisProgress by viewModel.analysisProgress.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .glassPanel(corner = Radii.xlarge, intensity = 0.45f)
                    .padding(18.dp)
            ) {
                Text("Describe the playlist", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Built only from tracks already on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = viewModel::setPrompt,
                    placeholder = { Text("e.g. traveling playlist, calm, nothing too heavy") },
                    minLines = 3,
                    shape = AppShapes.large,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.Transparent
                    ),
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
                        DurationChip(
                            label = label,
                            selected = !state.customSelected && state.presetMinutes == minutes,
                            onClick = { viewModel.selectPreset(minutes) }
                        )
                    }
                    DurationChip(
                        label = "Custom",
                        selected = state.customSelected,
                        onClick = viewModel::selectCustom
                    )
                    if (state.customSelected) {
                        OutlinedTextField(
                            value = state.customMinutes,
                            onValueChange = viewModel::setCustomMinutes,
                            label = { Text("min") },
                            singleLine = true,
                            shape = AppShapes.pill,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.width(118.dp)
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = viewModel::generate,
                    enabled = state.canGenerate,
                    shape = AppShapes.pill,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .accentGlow(
                            cornerRadius = Radii.pill,
                            radius = 18.dp,
                            intensity = if (state.canGenerate) 0.8f else 0f
                        )
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
                        "No Hugging Face API key set. Open Settings and paste your token to " +
                            "turn on AI playlists. Everything else in the app works without it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                state.error?.let { error ->
                    Spacer(Modifier.height(12.dp))
                    Card(
                        shape = AppShapes.large,
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

        item {
            AnalysisCard(
                coverage = coverage,
                progress = analysisProgress,
                enabled = state.configured,
                onAnalyze = viewModel::analyzeLibrary,
                onStop = viewModel::stopAnalysis,
                onDismissMessage = viewModel::dismissAnalysisMessage
            )
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
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                        .softSurface(shape = AppShapes.large)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The position sits in its own disc so the numbers form a clean column
                    // down the left edge instead of ragging with the track titles.
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .accentSurface(shape = AppShapes.pill, alpha = 0.12f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
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
                        // Why this song is here: what the app heard in it.
                        preview.analyses[track.id]?.let { analysis ->
                            Spacer(Modifier.height(4.dp))
                            AnalysisChips(analysis)
                        }
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
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .softSurface(shape = AppShapes.xlarge)
            .padding(16.dp)
    ) {
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
            Button(onClick = onShufflePlay, shape = AppShapes.pill) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Shuffle & play", modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(
                onClick = onRegenerate,
                enabled = !regenerating,
                shape = AppShapes.pill
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Regenerate", modifier = Modifier.padding(start = 6.dp))
            }
        }

        if (preview.savedPlaylistId == null) {
            OutlinedButton(
                onClick = onSave,
                shape = AppShapes.pill,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Save as playlist", modifier = Modifier.padding(start = 6.dp))
            }
        } else {
            OutlinedButton(
                onClick = onOpenSaved,
                shape = AppShapes.pill,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Saved — open playlist", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/**
 * A duration preset as a pill. Material's `FilterChip` brings its own container shape and a
 * leading check that shifts the label sideways when selected; both fight the corner scale and
 * the fixed-width row of chips, so selection is carried here by fill and weight alone.
 */
@Composable
private fun DurationChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        targetValue = if (selected) scheme.primary.copy(alpha = 0.18f)
        else scheme.surfaceVariant.copy(alpha = 0.45f),
        animationSpec = Motion.emphasized(),
        label = "chipFill"
    )
    val content by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.onSurfaceVariant,
        animationSpec = Motion.emphasized(),
        label = "chipContent"
    )

    Box(
        modifier = Modifier
            .pressable(shape = AppShapes.pill, onClick = onClick, pressedScale = 0.94f)
            .background(fill, AppShapes.pill)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = content,
            maxLines = 1
        )
    }
}

/**
 * The listening panel: how much of the library the app has actually heard, and the control to
 * hear more of it.
 *
 * Analysis is deliberately a manual, resumable batch rather than something that happens on
 * scan. It costs two network calls per song, so the user decides when to spend them, can stop
 * mid-run, and never loses finished work.
 */
@Composable
private fun AnalysisCard(
    coverage: AnalysisRepository.Coverage,
    progress: AnalysisRepository.Progress,
    enabled: Boolean,
    onAnalyze: () -> Unit,
    onStop: () -> Unit,
    onDismissMessage: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .softSurface(shape = AppShapes.xlarge)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                "Song understanding",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Text(
            "Each song is sampled, its words are transcribed, and what it is about — mood, " +
                "sentiment, energy, subject — is worked out once and remembered. Playlists are " +
                "then built from that instead of from titles.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(12.dp))
        Text(
            when {
                coverage.total == 0 -> "No songs in the library yet."
                coverage.complete -> "All ${coverage.total} songs analysed."
                else -> "${coverage.analyzed} of ${coverage.total} songs analysed."
            },
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (progress.running) progress.fraction else coverage.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        if (progress.running) {
            Text(
                progress.currentTitle?.let { "Listening to $it" } ?: "Starting…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "${progress.done} of ${progress.total} done" +
                    if (progress.failed > 0) " · ${progress.failed} failed" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onStop,
                shape = AppShapes.pill,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Text("Stop")
            }
        } else {
            Button(
                onClick = onAnalyze,
                enabled = enabled && coverage.total > 0 && !coverage.complete,
                shape = AppShapes.pill,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Text(
                    if (coverage.analyzed == 0) "Analyse my songs" else "Analyse the rest",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        progress.message?.let { message ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismissMessage) { Text("OK") }
            }
        }
    }
}

/** Mood, what it suits, and the strongest theme — the short version of a verdict. */
@Composable
private fun AnalysisChips(analysis: SongAnalysis) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        VerdictChip(analysis.mood)
        VerdictChip(analysis.category)
        analysis.themeList.firstOrNull()?.let { VerdictChip(it) }
    }
}

@Composable
private fun VerdictChip(label: String) {
    if (label.isBlank() || label == "unknown") return
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .accentSurface(shape = AppShapes.pill, alpha = 0.12f)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

package dev.nk.musicplayer.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nk.musicplayer.data.db.AiPlaylistCompletion
import dev.nk.musicplayer.data.db.HourBucket
import dev.nk.musicplayer.data.db.TrackCount
import dev.nk.musicplayer.data.db.ratio
import dev.nk.musicplayer.data.stats.StatsWindow
import dev.nk.musicplayer.ui.components.EmptyState
import dev.nk.musicplayer.ui.components.SegmentedTabs
import dev.nk.musicplayer.ui.theme.AppShapes
import dev.nk.musicplayer.ui.theme.Motion
import dev.nk.musicplayer.ui.theme.Radii
import dev.nk.musicplayer.ui.theme.glassPanel
import dev.nk.musicplayer.ui.theme.softSurface
import dev.nk.musicplayer.util.formatDurationLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val window by viewModel.window.collectAsStateWithLifecycle()
    val listeningTime by viewModel.listeningTime.collectAsStateWithLifecycle()
    val topArtists by viewModel.topArtists.collectAsStateWithLifecycle()
    val topTracks by viewModel.topTracks.collectAsStateWithLifecycle()
    val mostSkipped by viewModel.mostSkipped.collectAsStateWithLifecycle()
    val byHour by viewModel.byHour.collectAsStateWithLifecycle()
    val aiCompletion by viewModel.aiCompletion.collectAsStateWithLifecycle()
    val eventCount by viewModel.eventCount.collectAsStateWithLifecycle()

    if (eventCount == 0) {
        EmptyState(
            icon = Icons.Rounded.Insights,
            title = "Nothing listened to yet",
            subtitle = "Play some music and this fills up on its own.",
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                SegmentedTabs(
                    labels = StatsWindow.entries.map { it.label },
                    selected = StatsWindow.entries.indexOf(window),
                    onSelect = { viewModel.setWindow(StatsWindow.entries[it]) }
                )
                Spacer(Modifier.height(16.dp))

                // The headline number gets its own lit panel: it is the one figure on this
                // screen worth reading from across the room, and a panel gives it somewhere
                // to sit rather than floating against the ambient light.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassPanel(corner = Radii.xlarge, intensity = 0.5f)
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    Text(
                        formatDurationLong(listeningTime),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "listened, ${window.label.lowercase()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        section("Top artists · ${window.label.lowercase()}") {
            if (topArtists.isEmpty()) {
                Hint("Nothing in this window yet.")
            } else {
                val max = topArtists.maxOf { it.count }
                topArtists.forEach { artist ->
                    RankedBar(artist.label, "${artist.count} plays", artist.count.toFloat() / max)
                }
            }
        }

        section("Top tracks · ${window.label.lowercase()}") {
            if (topTracks.isEmpty()) {
                Hint("Nothing in this window yet.")
            } else {
                val max = topTracks.maxOf { it.count }
                topTracks.forEach { track ->
                    RankedBar(track.label(), "${track.count} plays", track.count.toFloat() / max)
                }
            }
        }

        section("Most skipped · all time") {
            if (mostSkipped.isEmpty()) {
                Hint("You haven't skipped anything yet.")
            } else {
                val max = mostSkipped.maxOf { it.count }
                mostSkipped.forEach { track ->
                    RankedBar(track.label(), "${track.count} skips", track.count.toFloat() / max)
                }
            }
        }

        section("By hour of day · all time") {
            HourChart(byHour)
        }

        section("AI playlist completion") {
            if (aiCompletion.isEmpty()) {
                Hint("Generate and save a playlist on the AI tab to see this.")
            } else {
                aiCompletion.forEach { AiCompletionRow(it) }
            }
        }
    }
}

private fun TrackCount.label(): String = "$title — $artist"

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    content: @Composable () -> Unit
) {
    item {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
                .softSurface(shape = AppShapes.large)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            content()
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun RankedBar(label: String, value: String, fraction: Float) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        PillBar(fraction = fraction, modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * A progress rail with fully rounded ends. Material's `LinearProgressIndicator` clips its
 * track square at the ends; drawing the rail and the fill as two nested pills is the only way
 * to get a bar whose fill is rounded too, which is what stops a nearly-empty bar from looking
 * like a stray tick.
 *
 * The fill runs primary to secondary so a long bar carries both accents of the active theme.
 */
@Composable
private fun PillBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = Motion.emphasized(Motion.Slow),
        label = "pillBar"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(AppShapes.pill)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(AppShapes.pill)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
        )
    }
}

/** 24 bars, one per local hour, scaled to the busiest hour. */
@Composable
private fun HourChart(buckets: List<HourBucket>) {
    val byHour = buckets.associateBy { it.hour }
    val max = buckets.maxOfOrNull { it.totalMs }?.takeIf { it > 0 } ?: 1L

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            (0..23).forEach { hour ->
                val value = byHour[hour]?.totalMs ?: 0L
                val fraction = (value.toFloat() / max).coerceIn(0f, 1f)
                // Each column animates to its height, so switching the stats window makes
                // the chart grow into its new shape instead of redrawing instantly.
                val animated by animateFloatAsState(
                    targetValue = fraction.coerceAtLeast(0.02f),
                    animationSpec = Motion.emphasized(Motion.Slow),
                    label = "hourBar"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(animated)
                        .clip(AppShapes.pill)
                        .background(
                            if (value > 0) {
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    )
                                )
                            } else {
                                SolidColor(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                                )
                            }
                        )
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("00", "06", "12", "18", "23").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AiCompletionRow(item: AiPlaylistCompletion) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${(item.ratio * 100).toInt()}% · ${item.completedTrackCount}/${item.trackCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        PillBar(fraction = item.ratio, modifier = Modifier.padding(top = 6.dp))
    }
}

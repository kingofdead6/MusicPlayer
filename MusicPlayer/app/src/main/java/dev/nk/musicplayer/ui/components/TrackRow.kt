package dev.nk.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nk.musicplayer.data.db.Track
import dev.nk.musicplayer.ui.theme.AppShapes
import dev.nk.musicplayer.ui.theme.Motion
import dev.nk.musicplayer.ui.theme.Radii
import dev.nk.musicplayer.ui.theme.neonEdge
import dev.nk.musicplayer.ui.theme.pressable
import dev.nk.musicplayer.util.formatDuration

/**
 * A track as a rounded tile rather than a full-bleed row. Tiles are inset from the screen
 * edge so the curve is actually visible — a rounded shape that touches both margins reads as
 * a plain row with two dents in it.
 *
 * The highlighted state (this is the playing track) tints the whole tile and lights its edge,
 * so the current track is findable in a long list at a glance instead of only by its title
 * colour. Both transitions are animated, because the highlight moves on every track change
 * and a hard swap draws the eye to the wrong thing.
 */
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme

    val container by animateColorAsState(
        targetValue = if (highlighted) {
            scheme.primary.copy(alpha = 0.13f)
        } else {
            Color.Transparent
        },
        animationSpec = Motion.emphasized(),
        label = "rowContainer"
    )
    val titleColor by animateColorAsState(
        targetValue = if (highlighted) scheme.primary else LocalContentColor.current,
        animationSpec = Motion.emphasized(),
        label = "rowTitle"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressable(shape = AppShapes.large, onClick = onClick)
                .background(container, AppShapes.large)
                .then(
                    if (highlighted) {
                        Modifier.neonEdge(AppShapes.large, scheme.primary, alpha = 0.4f)
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(
                track = track,
                corner = Radii.medium,
                modifier = Modifier.size(52.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} · ${formatDuration(track.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            trailing?.invoke()
        }
    }
}

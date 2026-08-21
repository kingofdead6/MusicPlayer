package dev.nk.musicplayer.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nk.musicplayer.playback.PlayerUiState
import dev.nk.musicplayer.ui.theme.LocalGlowEnabled
import dev.nk.musicplayer.ui.theme.neonEdge

@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = state.current ?: return
    val scheme = MaterialTheme.colorScheme
    val glow = LocalGlowEnabled.current

    // While playing, the top edge breathes: a slow alpha ramp on the accent line. It is the
    // one always-visible piece of chrome, so it doubles as the "is it playing" indicator.
    val transition = rememberInfiniteTransition(label = "miniPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "pulse"
    )
    val edgeAlpha = if (glow && state.isPlaying) pulse else 0.35f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .neonEdge(RectangleShape, scheme.primary, alpha = edgeAlpha * 0.5f)
            .clickable(onClick = onClick)
    ) {
        LinearProgressIndicator(
            progress = {
                if (state.durationMs > 0) {
                    (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                } else 0f
            },
            color = scheme.primary,
            trackColor = scheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(albumId = track.albumId, modifier = Modifier.size(40.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onTogglePlayPause) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = scheme.primary
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next")
            }
        }
    }
}

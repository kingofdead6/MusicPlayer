package dev.nk.musicplayer.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nk.musicplayer.playback.PlayerUiState
import dev.nk.musicplayer.ui.theme.AppShapes
import dev.nk.musicplayer.ui.theme.LocalGlowEnabled
import dev.nk.musicplayer.ui.theme.Motion
import dev.nk.musicplayer.ui.theme.Radii
import dev.nk.musicplayer.ui.theme.accentGlow
import dev.nk.musicplayer.ui.theme.neonEdge
import dev.nk.musicplayer.ui.theme.pressable

/**
 * The mini player as a floating rounded slab rather than a docked strip. Insetting it from
 * all four sides is what lets it be fully rounded — and the gap underneath separates it from
 * the tab bar, so the two stop reading as one block of chrome.
 */
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

    // While playing, the shell breathes: a slow ramp driving both the lit edge and the bloom
    // around it. It is the one always-visible piece of chrome, so it doubles as the "is it
    // playing" indicator without needing a separate badge.
    val transition = rememberInfiniteTransition(label = "miniPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = Motion.Standard),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val active = glow && state.isPlaying
    val edgeAlpha = if (active) pulse * 0.55f else 0.28f
    val glowIntensity = if (active) pulse * 0.75f else if (glow) 0.3f else 0f

    // Progress eases toward each new position instead of stepping, so the sliver of accent
    // under the artwork slides rather than ticks.
    val target = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else 0f
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(Motion.Slow, easing = Motion.Standard),
        label = "miniProgress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .accentGlow(
                    cornerRadius = Radii.xlarge,
                    radius = 20.dp,
                    intensity = glowIntensity,
                    color = scheme.primary
                )
                .pressable(shape = AppShapes.xlarge, onClick = onClick)
                .background(scheme.surface, AppShapes.xlarge)
                .neonEdge(AppShapes.xlarge, scheme.primary, alpha = edgeAlpha)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArt(
                    artwork = TrackArtwork(track.trackId, track.albumId),
                    corner = Radii.medium,
                    modifier = Modifier.size(46.dp)
                )
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
                        color = scheme.onSurfaceVariant,
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

            // The progress rail sits inside the slab's padding as its own pill, so the bar
            // has rounded ends instead of being clipped square by the shell.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 10.dp)
                    .height(3.dp)
                    .clip(AppShapes.pill)
                    .background(scheme.onSurfaceVariant.copy(alpha = 0.18f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .clip(AppShapes.pill)
                        .background(scheme.primary)
                )
            }
        }
    }
}

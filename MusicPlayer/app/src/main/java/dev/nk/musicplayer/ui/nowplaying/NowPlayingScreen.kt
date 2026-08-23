package dev.nk.musicplayer.ui.nowplaying

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nk.musicplayer.LocalContainer
import dev.nk.musicplayer.playback.PlayerUiState
import dev.nk.musicplayer.ui.components.AlbumArt
import dev.nk.musicplayer.ui.components.TrackArtwork
import dev.nk.musicplayer.ui.components.albumArtwork
import dev.nk.musicplayer.ui.theme.AppShapes
import dev.nk.musicplayer.ui.theme.LocalGlowEnabled
import dev.nk.musicplayer.ui.theme.Motion
import dev.nk.musicplayer.ui.theme.Radii
import dev.nk.musicplayer.ui.theme.accentGlow
import dev.nk.musicplayer.ui.theme.pressable
import dev.nk.musicplayer.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit
) {
    val track = state.current

    // While the thumb is held, show where the finger is, not where the player still is.
    var scrubOverride by remember { mutableStateOf<Long?>(null) }
    val seekStyle by LocalContainer.current.settingsStore.seekBarStyle.collectAsStateWithLifecycle()

    val scheme = MaterialTheme.colorScheme
    val glow = LocalGlowEnabled.current

    // The art glows brighter while playing and settles when paused, so the screen has a
    // visible heartbeat without any audio analysis.
    val transition = rememberInfiniteTransition(label = "artPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse),
        label = "artPulse"
    )
    val artIntensity = when {
        !glow -> 0f
        state.isPlaying -> pulse
        else -> 0.4f
    }
    // The artwork itself swells by a fraction of a percent on the same ramp. It is far too
    // small to notice as motion, which is the point — it reads as the sleeve breathing rather
    // than as an animation, and it keeps the screen alive even with glow switched off.
    val artScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0.965f,
        animationSpec = Motion.springy(),
        label = "artScale"
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = { Text("Now playing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenQueue) {
                        Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            AlbumArt(
                artwork = track?.let { TrackArtwork(it.trackId, it.albumId) }
                    ?: albumArtwork(-1L),
                corner = Radii.huge,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .scale(artScale)
                    .accentGlow(
                        cornerRadius = Radii.huge,
                        radius = 48.dp,
                        intensity = artIntensity
                    )
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = track?.title ?: "Nothing playing",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track?.artist.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(24.dp))
            // While scrubbing, the elapsed label follows the finger; SeekBar reports the
            // in-flight position and clears it on release.
            val displayed = scrubOverride?.toFloat() ?: state.positionMs.toFloat()
            SeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                isPlaying = state.isPlaying,
                style = seekStyle,
                onSeek = onSeek,
                enabled = track != null,
                onScrubChange = { scrubOverride = it }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(displayed.toLong()), style = MaterialTheme.typography.labelMedium)
                Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToggleControl(
                    icon = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    active = state.shuffleEnabled,
                    onClick = onToggleShuffle
                )
                FilledIconButton(
                    onClick = onPrevious,
                    enabled = state.hasQueue,
                    shape = AppShapes.pill,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = scheme.surfaceVariant.copy(alpha = 0.6f),
                        contentColor = scheme.onSurface
                    ),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous",
                        modifier = Modifier.size(30.dp))
                }
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    enabled = state.hasQueue,
                    shape = AppShapes.pill,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary
                    ),
                    modifier = Modifier
                        .size(72.dp)
                        .accentGlow(
                            cornerRadius = 36.dp,
                            radius = 26.dp,
                            intensity = if (glow && state.hasQueue) 1f else 0f
                        )
                ) {
                    // Crossfading the glyph keeps the button from flickering on every toggle;
                    // the two icons occupy the same box so nothing shifts as they swap.
                    Crossfade(
                        targetState = state.isPlaying,
                        animationSpec = tween(Motion.Quick),
                        label = "playPause"
                    ) { playing ->
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
                FilledIconButton(
                    onClick = onNext,
                    enabled = state.hasQueue,
                    shape = AppShapes.pill,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = scheme.surfaceVariant.copy(alpha = 0.6f),
                        contentColor = scheme.onSurface
                    ),
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next",
                        modifier = Modifier.size(30.dp))
                }
                ToggleControl(
                    icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne
                    else Icons.Rounded.Repeat,
                    contentDescription = "Repeat",
                    active = state.repeatMode != Player.REPEAT_MODE_OFF,
                    onClick = onCycleRepeat
                )
            }
        }
    }
}

/**
 * Shuffle and repeat. Colour alone is a weak "on" signal at icon size, so the active state
 * also fills a pill behind the glyph — a lit chip is legible in peripheral vision in a way a
 * differently tinted outline is not.
 */
@Composable
private fun ToggleControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val tint by animateColorAsState(
        targetValue = if (active) scheme.primary else scheme.onSurfaceVariant,
        animationSpec = Motion.emphasized(),
        label = "toggleTint"
    )
    val fill by animateColorAsState(
        targetValue = if (active) scheme.primary.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = Motion.emphasized(),
        label = "toggleFill"
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .pressable(shape = AppShapes.pill, onClick = onClick, pressedScale = 0.9f)
            .background(fill, AppShapes.pill),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint,
            modifier = Modifier.size(22.dp))
    }
}

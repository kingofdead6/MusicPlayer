package dev.nk.musicplayer.ui.nowplaying

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nk.musicplayer.LocalContainer
import dev.nk.musicplayer.playback.PlayerUiState
import dev.nk.musicplayer.ui.components.AlbumArt
import dev.nk.musicplayer.ui.theme.LocalGlowEnabled
import dev.nk.musicplayer.ui.theme.accentGlow
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
            Spacer(Modifier.height(16.dp))
            AlbumArt(
                albumId = track?.albumId ?: -1L,
                corner = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .accentGlow(cornerRadius = 16.dp, radius = 40.dp, intensity = artIntensity)
            )
            Spacer(Modifier.height(24.dp))
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
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onPrevious, enabled = state.hasQueue) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous",
                        modifier = Modifier.size(36.dp))
                }
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    enabled = state.hasQueue,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary
                    ),
                    modifier = Modifier
                        .size(64.dp)
                        .accentGlow(
                            cornerRadius = 32.dp,
                            radius = 22.dp,
                            intensity = if (glow && state.hasQueue) 1f else 0f
                        )
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = onNext, enabled = state.hasQueue) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next",
                        modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = onCycleRepeat) {
                    val active = state.repeatMode != Player.REPEAT_MODE_OFF
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne
                        else Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        tint = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

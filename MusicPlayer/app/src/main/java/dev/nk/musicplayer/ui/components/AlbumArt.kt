package dev.nk.musicplayer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import dev.nk.musicplayer.data.db.Track
import dev.nk.musicplayer.ui.theme.Motion
import dev.nk.musicplayer.ui.theme.Radii

/**
 * Cover art on the app's corner scale. The default is [Radii.medium] rather than a token 6dp:
 * at the 48-56dp sizes list rows use, anything smaller reads as a square with the edges filed
 * off instead of as a rounded tile.
 *
 * Resolution goes through [TrackArtworkFetcher], which is why this takes a [TrackArtwork]
 * rather than a bare album id — the reliable sources key off the *track*, not the album.
 *
 * The image is always composed at full size and fades in via alpha once it loads. It must not
 * be made conditional on having loaded: Coil sizes its request from the layout constraints, so
 * an image that is not laid out at its real size never resolves, and the condition can never
 * become true.
 */
@Composable
fun AlbumArt(
    artwork: TrackArtwork,
    modifier: Modifier = Modifier,
    corner: Dp = Radii.medium
) {
    var loaded by remember(artwork) { mutableStateOf(false) }

    // Fading rather than swapping keeps a scrolling list from flickering tile by tile as each
    // request lands. A cache hit still fades, but over a single frame or two.
    val imageAlpha by animateFloatAsState(
        targetValue = if (loaded) 1f else 0f,
        animationSpec = tween(Motion.Medium, easing = Motion.Standard),
        label = "artFade"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(placeholderBrush()),
        contentAlignment = Alignment.Center
    ) {
        // The note sits underneath the whole time: it is the placeholder while the request is
        // in flight and the permanent fallback when there is no art to find.
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxSize(0.45f)
        )
        AsyncImage(
            model = artwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = { loaded = true },
            onError = { loaded = false },
            modifier = Modifier
                .fillMaxSize()
                .alpha(imageAlpha)
        )
    }
}

/** Convenience for the common case of drawing a track's own cover. */
@Composable
fun AlbumArt(
    track: Track,
    modifier: Modifier = Modifier,
    corner: Dp = Radii.medium
) = AlbumArt(TrackArtwork.of(track), modifier, corner)

/**
 * Album-only artwork, for rows that know an album but no particular track. This can only fall
 * back to the legacy provider, so it is strictly less reliable than the track-aware overloads
 * — prefer those wherever a [Track] is at hand.
 */
@Composable
fun AlbumArt(
    albumId: Long,
    modifier: Modifier = Modifier,
    corner: Dp = Radii.medium
) = AlbumArt(albumArtwork(albumId), modifier, corner)

/**
 * A faint accent wash behind the placeholder. A flat grey tile repeated down a list of
 * artless tracks reads as broken; a tinted one reads as intentional.
 */
@Composable
private fun placeholderBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    return Brush.linearGradient(
        listOf(
            scheme.primary.copy(alpha = 0.16f),
            scheme.surfaceVariant
        )
    )
}

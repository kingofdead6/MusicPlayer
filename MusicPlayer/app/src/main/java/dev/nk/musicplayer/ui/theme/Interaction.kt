package dev.nk.musicplayer.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * A row or card that visibly gives under the finger. The scale is small on purpose — 2% is
 * enough to register as physical without looking like the element is being squashed — and it
 * springs back rather than tweening, so a quick tap feels crisp and a long press settles.
 *
 * The ripple is clipped to [shape] first, so on a heavily rounded surface it can never bleed
 * past the curve and re-square the corner.
 */
fun Modifier.pressable(
    shape: Shape,
    onClick: () -> Unit,
    enabled: Boolean = true,
    pressedScale: Float = 0.98f
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = Motion.springy(),
        label = "pressScale"
    )
    this
        .scale(scale)
        .clip(shape)
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * The standard content surface: a slightly lifted fill inside a rounded clip. Unlike
 * [neonPanel] this carries no glow or lit edge — it is the quiet container used for the many
 * rows that would otherwise stack up into a wall of light.
 */
@Composable
fun Modifier.softSurface(
    shape: Shape = AppShapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
): Modifier = this
    .clip(shape)
    .background(color, shape)

/**
 * [softSurface] plus a tap target that presses. The pairing is common enough — every list row
 * in the app is one — that keeping it as a single call avoids the two drifting apart.
 */
@Composable
fun Modifier.softCard(
    onClick: (() -> Unit)? = null,
    shape: Shape = AppShapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    enabled: Boolean = true
): Modifier {
    val base = if (onClick != null) {
        this.pressable(shape = shape, onClick = onClick, enabled = enabled)
    } else {
        this.clip(shape)
    }
    return base.background(color, shape)
}

/**
 * A selected/active surface: the accent at low alpha, so state reads as the element being lit
 * from within rather than as a different-coloured element.
 */
@Composable
fun Modifier.accentSurface(
    shape: Shape = AppShapes.large,
    alpha: Float = 0.14f
): Modifier = this
    .clip(shape)
    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), shape)

/**
 * The full neon treatment on a rounded shape, with the glow radius already tied to the corner
 * so a bigger curve throws a correspondingly wider bloom. Wraps [neonPanel] purely to keep
 * the corner and the glow from being specified independently at each call site.
 */
@Composable
fun Modifier.glassPanel(
    corner: Dp = Radii.large,
    fill: Color = MaterialTheme.colorScheme.surface,
    accent: Color = LocalAccents.current.primary,
    intensity: Float = 0.55f
): Modifier = this.neonPanel(
    fill = fill,
    accent = accent,
    corner = corner,
    glowRadius = corner * 0.7f,
    intensity = intensity
)

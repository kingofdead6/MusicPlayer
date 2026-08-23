package dev.nk.musicplayer.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Whether the decorative light layers should draw at all. Read by every glow helper so the
 * settings toggle (and the light theme) can switch them off in one place, without each call
 * site having to know about the preference.
 */
val LocalGlowEnabled = compositionLocalOf { true }

/** Accents for the active theme, so glows do not have to re-derive them per call site. */
val LocalAccents = compositionLocalOf { AppTheme.NeonCyan.accents() }

@Composable
fun ProvideGlow(enabled: Boolean, accents: NeonAccents, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalGlowEnabled provides enabled,
        LocalAccents provides accents,
        content = content
    )
}

/**
 * A soft coloured bloom bleeding outward from the composable's bounds — the neon-tube look.
 * Drawn as a few concentric rounded rects of decreasing alpha rather than a real blur:
 * `Modifier.blur` costs a render-node pass per element and is wasted on a static halo, while
 * this is a handful of draw ops inside the existing layer.
 */
fun Modifier.neonGlow(
    color: Color,
    cornerRadius: Dp = Radii.large,
    radius: Dp = 18.dp,
    intensity: Float = 1f
): Modifier = this.drawBehind {
    if (intensity <= 0f) return@drawBehind
    val steps = 4
    val spread = radius.toPx()
    val corner = cornerRadius.toPx()
    for (i in steps downTo 1) {
        val frac = i / steps.toFloat()
        val inset = spread * frac
        // Alpha falls off quadratically so the outermost ring is a hint rather than a band.
        val alpha = 0.10f * intensity * (1f - frac) * (1f - frac) + 0.03f * intensity * (1f - frac)
        drawRoundRect(
            color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
            topLeft = Offset(-inset, -inset),
            size = Size(size.width + inset * 2, size.height + inset * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner + inset, corner + inset)
        )
    }
}

/** [neonGlow] with the theme accent and the glow preference already applied. */
@Composable
fun Modifier.accentGlow(
    cornerRadius: Dp = Radii.large,
    radius: Dp = 18.dp,
    intensity: Float = 1f,
    color: Color = LocalAccents.current.primary
): Modifier = if (!LocalGlowEnabled.current) this
else neonGlow(color, cornerRadius, radius, intensity)

/**
 * The 1px lit edge that makes a surface read as a panel of glass in a dark room. Pairs with
 * [accentGlow]: the border is the tube, the glow is the light it throws.
 */
@Composable
fun Modifier.neonEdge(
    shape: Shape = AppShapes.large,
    color: Color = LocalAccents.current.primary,
    alpha: Float = 0.45f,
    width: Dp = 1.dp
): Modifier = if (!LocalGlowEnabled.current) this
else border(width, color.copy(alpha = alpha), shape)

/**
 * A card-shaped surface: dark fill, lit edge, bloom around it. Used everywhere a Material
 * `Card` would otherwise sit, so panels are consistent across screens.
 */
@Composable
fun Modifier.neonPanel(
    fill: Color,
    accent: Color = LocalAccents.current.primary,
    corner: Dp = Radii.large,
    glowRadius: Dp = 14.dp,
    intensity: Float = 0.7f
): Modifier = this
    .accentGlow(cornerRadius = corner, radius = glowRadius, intensity = intensity, color = accent)
    .background(fill, RoundedCornerShape(corner))
    .neonEdge(RoundedCornerShape(corner), accent)

/**
 * Slow drifting orbs of accent light behind the whole app. This is the "dynamic light all
 * around" layer: three radial gradients on long, mutually prime periods so the composition
 * never visibly loops, at alphas low enough to stay behind text.
 *
 * Sits in its own Box behind content and never intercepts touches.
 */
@Composable
fun AmbientLight(
    accents: NeonAccents,
    enabled: Boolean,
    intensity: Float = 1f,
    modifier: Modifier = Modifier
) {
    if (!enabled || intensity <= 0f) return
    val transition = rememberInfiniteTransition(label = "ambient")
    // Three phases, deliberately non-harmonic periods.
    val t1 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(19_000, easing = LinearEasing), RepeatMode.Restart),
        label = "t1"
    )
    val t2 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(27_000, easing = LinearEasing), RepeatMode.Restart),
        label = "t2"
    )
    val t3 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(23_000, easing = LinearEasing), RepeatMode.Restart),
        label = "t3"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawOrb(accents.primary, t1, 0.30f * intensity, 0.95f)
                drawOrb(accents.secondary, t2, 0.26f * intensity, 0.80f, phase = 2.1f)
                drawOrb(accents.primary, t3, 0.16f * intensity, 0.65f, phase = 4.2f)
            }
    )
}

/**
 * One drifting light. [t] is a 0..1 ramp; the orb travels a Lissajous path so its x and y
 * cycles complete at different times and the motion never reads as a circle.
 */
private fun DrawScope.drawOrb(
    color: Color,
    t: Float,
    alpha: Float,
    sizeFraction: Float,
    phase: Float = 0f
) {
    val angle = t * 2f * Math.PI.toFloat() + phase
    val cx = size.width * (0.5f + 0.42f * cos(angle))
    val cy = size.height * (0.5f + 0.38f * sin(angle * 1.37f + phase))
    val r = size.minDimension * sizeFraction
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = Offset(cx, cy),
            radius = r
        ),
        radius = r,
        center = Offset(cx, cy)
    )
}

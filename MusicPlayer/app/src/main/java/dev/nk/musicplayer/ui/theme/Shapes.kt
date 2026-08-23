package dev.nk.musicplayer.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The corner scale. The app has one rule — nothing is square — and these are the only radii
 * allowed to express it. Sizes are deliberately generous: a radius has to be a visible
 * fraction of the shorter side to read as "rounded" rather than as a softened corner, so the
 * scale grows with the element instead of staying at a constant 4-8dp the way stock Material
 * does.
 *
 * Steps are roughly 1.5x apart so two adjacent surfaces never look accidentally alike, and
 * a nested shape can always drop exactly one step and stay concentric with its parent.
 */
object Radii {
    /** Progress bars, tick marks, chart columns — things a few dp tall. */
    val hairline: Dp = 4.dp
    /** Chips, small badges, inline affordances. */
    val small: Dp = 12.dp
    /** List-row artwork, dense controls. */
    val medium: Dp = 18.dp
    /** The default panel: cards, sheets, list rows, text fields. */
    val large: Dp = 26.dp
    /** Hero surfaces — the now-playing artwork, the mini player shell. */
    val xlarge: Dp = 34.dp
    /** Full-bleed containers that still need a visible curve at the top. */
    val huge: Dp = 44.dp

    /** Effectively a stadium/circle for any realistically sized element. */
    val pill: Dp = 1000.dp
}

/** Ready-made shapes, so call sites read as intent rather than as numbers. */
object AppShapes {
    val hairline = RoundedCornerShape(Radii.hairline)
    val small = RoundedCornerShape(Radii.small)
    val medium = RoundedCornerShape(Radii.medium)
    val large = RoundedCornerShape(Radii.large)
    val xlarge = RoundedCornerShape(Radii.xlarge)
    val huge = RoundedCornerShape(Radii.huge)
    val pill = RoundedCornerShape(Radii.pill)

    /** For bars docked to the bottom of the screen: curved on top, flush below. */
    val bottomDock = RoundedCornerShape(topStart = Radii.huge, topEnd = Radii.huge)

    /** For bars docked to the top: flush above, curved below. */
    val topDock = RoundedCornerShape(bottomStart = Radii.huge, bottomEnd = Radii.huge)
}

/**
 * Material's own shape slots, remapped onto [Radii]. Every stock component the app does not
 * style by hand — dialogs, menus, chips, buttons, sheets, text fields — picks its corners up
 * from here, which is what keeps "no square corners" true for parts of the UI this codebase
 * never touches directly.
 */
val MusicShapes = Shapes(
    extraSmall = AppShapes.small,
    small = AppShapes.medium,
    medium = AppShapes.large,
    large = AppShapes.xlarge,
    extraLarge = AppShapes.huge
)

/**
 * Motion. One place for durations and curves so transitions across screens feel like the
 * same hand made them.
 *
 * [Emphasized] is Material 3's emphasized easing: a slow start and a long, decelerating tail.
 * It is the curve that makes movement read as weighted rather than as linear interpolation.
 */
object Motion {
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Micro-feedback: press states, icon swaps, colour ticks. */
    const val Quick = 140

    /** The default: anything appearing, resizing, or re-colouring in place. */
    const val Medium = 280

    /** Screen-level movement — enter/exit transitions, expanding panels. */
    const val Slow = 420

    /** A settle with a little give, for things that grow or pop into place. */
    fun <T> springy(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMediumLow
    )

    /** The general-purpose eased tween. */
    fun <T> emphasized(durationMillis: Int = Medium): FiniteAnimationSpec<T> =
        tween(durationMillis, easing = Emphasized)
}

package dev.nk.musicplayer.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Neon-dark by default. The theme is an explicit user choice rather than a system-dark
 * follow: five of the six palettes only make sense dark, so tracking the system setting
 * would just flip people out of a theme they picked on purpose.
 *
 * Colour changes are animated so switching themes in Settings washes across the app instead
 * of snapping — the palette swap is the one moment the design shows itself off.
 */
@Composable
fun MusicPlayerTheme(
    theme: AppTheme = AppTheme.NeonCyan,
    glowEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val target = theme.colorScheme()
    val spec = tween<androidx.compose.ui.graphics.Color>(
        durationMillis = Motion.Slow,
        easing = Motion.Emphasized
    )

    val background by animateColorAsState(target.background, spec, label = "background")
    val surface by animateColorAsState(target.surface, spec, label = "surface")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, spec, label = "surfaceVariant")
    val primary by animateColorAsState(target.primary, spec, label = "primary")
    val secondary by animateColorAsState(target.secondary, spec, label = "secondary")
    val onSurface by animateColorAsState(target.onSurface, spec, label = "onSurface")

    val colorScheme = target.copy(
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        primary = primary,
        secondary = secondary,
        onSurface = onSurface,
        onBackground = onSurface
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Transparent bars let the ambient light run edge to edge under them.
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = theme.isLight
                isAppearanceLightNavigationBars = theme.isLight
            }
        }
    }

    // Glow is meaningless on white: force it off for the light theme regardless of the toggle.
    ProvideGlow(
        enabled = glowEnabled && !theme.isLight,
        accents = NeonAccents(target.primary, target.secondary)
    ) {
        // Shapes are provided here rather than per-component so every stock Material
        // surface the app never touches by hand still lands on the rounded scale.
        MaterialTheme(colorScheme = colorScheme, shapes = MusicShapes, content = content)
    }
}

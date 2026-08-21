package dev.nk.musicplayer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The neon palettes. Every theme is built on near-black so the accents read as emitted light
 * rather than as tinted paint: backgrounds stay under ~8% luminance and only the accent pair
 * (`primary`/`secondary`) carries saturation. Those two colours are also what the glow
 * helpers in [Glow.kt] bloom, which is why each theme declares them explicitly instead of
 * letting Material derive a tonal palette.
 */
enum class AppTheme(val displayName: String, val description: String) {
    NeonCyan("Neon Cyan", "Cyan and magenta on black"),
    NeonMagenta("Synthwave", "Hot pink and violet dusk"),
    NeonLime("Acid Lime", "Toxic green on charcoal"),
    NeonEmber("Ember", "Amber and deep orange"),
    NeonIce("Ice", "Pale blue and white heat"),
    MidnightLight("Daylight", "The one light theme");

    val isLight: Boolean get() = this == MidnightLight
}

/** Accent pair pulled out of the scheme so glows and visualisers can use them directly. */
data class NeonAccents(val primary: Color, val secondary: Color)

private fun neonScheme(
    primary: Color,
    secondary: Color,
    background: Color,
    surface: Color,
    surfaceVariant: Color
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = Color(0xFF04070A),
    primaryContainer = primary.copy(alpha = 0.22f).compositeOverBlack(),
    onPrimaryContainer = primary,
    secondary = secondary,
    onSecondary = Color(0xFF04070A),
    secondaryContainer = secondary.copy(alpha = 0.20f).compositeOverBlack(),
    onSecondaryContainer = secondary,
    tertiary = secondary,
    background = background,
    onBackground = Color(0xFFE8F0F5),
    surface = surface,
    onSurface = Color(0xFFE8F0F5),
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = Color(0xFF9BA8B4),
    outline = Color(0xFF2A323C),
    outlineVariant = Color(0xFF1A2028),
    error = Color(0xFFFF5C7A),
    onError = Color(0xFF14040A),
    scrim = Color(0xFF000000)
)

/**
 * Flattens a translucent accent onto black. Container colours have to be opaque — Material
 * draws them behind text — but authoring them as "accent at 20%" keeps every theme's
 * containers consistently related to its accent.
 */
private fun Color.compositeOverBlack(): Color =
    Color(red = red * alpha, green = green * alpha, blue = blue * alpha, alpha = 1f)

private val CyanScheme = neonScheme(
    primary = Color(0xFF00E5FF),
    secondary = Color(0xFFFF2E97),
    background = Color(0xFF000305),
    surface = Color(0xFF05090D),
    surfaceVariant = Color(0xFF0D141B)
)

private val MagentaScheme = neonScheme(
    primary = Color(0xFFFF2E97),
    secondary = Color(0xFF9D4EDD),
    background = Color(0xFF050107),
    surface = Color(0xFF0A040F),
    surfaceVariant = Color(0xFF150A1C)
)

private val LimeScheme = neonScheme(
    primary = Color(0xFFB4FF39),
    secondary = Color(0xFF00E5FF),
    background = Color(0xFF030502),
    surface = Color(0xFF070B05),
    surfaceVariant = Color(0xFF10170C)
)

private val EmberScheme = neonScheme(
    primary = Color(0xFFFFA23A),
    secondary = Color(0xFFFF4D3D),
    background = Color(0xFF060301),
    surface = Color(0xFF0C0703),
    surfaceVariant = Color(0xFF1A1008)
)

private val IceScheme = neonScheme(
    primary = Color(0xFF8AD8FF),
    secondary = Color(0xFFE0F7FF),
    background = Color(0xFF010406),
    surface = Color(0xFF060B10),
    surfaceVariant = Color(0xFF10181F)
)

/**
 * The escape hatch for bright rooms. Deliberately restrained: the glow layers key off the
 * accents and would smear on white, so [AppTheme.isLight] disables them at the source.
 */
private val DaylightScheme = lightColorScheme(
    primary = Color(0xFF00658F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC7E7FF),
    onPrimaryContainer = Color(0xFF001E2E),
    secondary = Color(0xFF9C27B0),
    background = Color(0xFFFBFCFE),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFFBFCFE),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFEDF1F5),
    onSurfaceVariant = Color(0xFF41484D),
    outline = Color(0xFFC5CDD3),
    error = Color(0xFFBA1A1A)
)

fun AppTheme.colorScheme(): ColorScheme = when (this) {
    AppTheme.NeonCyan -> CyanScheme
    AppTheme.NeonMagenta -> MagentaScheme
    AppTheme.NeonLime -> LimeScheme
    AppTheme.NeonEmber -> EmberScheme
    AppTheme.NeonIce -> IceScheme
    AppTheme.MidnightLight -> DaylightScheme
}

fun AppTheme.accents(): NeonAccents = colorScheme().let { NeonAccents(it.primary, it.secondary) }

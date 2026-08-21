package dev.nk.musicplayer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nk.musicplayer.LocalContainer
import dev.nk.musicplayer.ui.nowplaying.SeekBar
import dev.nk.musicplayer.ui.nowplaying.SeekBarStyle
import dev.nk.musicplayer.ui.theme.AppTheme
import dev.nk.musicplayer.ui.theme.accentGlow
import dev.nk.musicplayer.ui.theme.accents
import dev.nk.musicplayer.ui.theme.colorScheme
import dev.nk.musicplayer.ui.theme.neonEdge
import dev.nk.musicplayer.ui.theme.neonPanel

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val settings = LocalContainer.current.settingsStore
    val theme by settings.theme.collectAsStateWithLifecycle()
    val glow by settings.glowEnabled.collectAsStateWithLifecycle()
    val pulse by settings.pulseWithPlayback.collectAsStateWithLifecycle()
    val seekStyle by settings.seekBarStyle.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 16.dp,
            bottom = 16.dp + contentPadding.calculateBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Appearance is saved and applies immediately.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }

        item { SectionLabel("THEME") }

        items(AppTheme.entries.size) { index ->
            val entry = AppTheme.entries[index]
            ThemeRow(
                theme = entry,
                selected = entry == theme,
                onClick = { settings.setTheme(entry) }
            )
        }

        item { SectionLabel("PROGRESS BAR") }

        items(SeekBarStyle.entries.size) { index ->
            val entry = SeekBarStyle.entries[index]
            SeekStyleRow(
                style = entry,
                selected = entry == seekStyle,
                onClick = { settings.setSeekBarStyle(entry) }
            )
        }

        item { SectionLabel("LIGHT EFFECTS") }

        item {
            ToggleRow(
                title = "Neon glow",
                subtitle = "Halos and lit edges around panels and controls.",
                checked = glow,
                enabled = !theme.isLight,
                onCheckedChange = settings::setGlowEnabled
            )
        }

        item {
            ToggleRow(
                title = "Pulse with playback",
                subtitle = "Ambient light breathes while music is playing.",
                checked = pulse,
                enabled = !theme.isLight && glow,
                onCheckedChange = settings::setPulseWithPlayback
            )
        }

        if (theme.isLight) {
            item {
                Text(
                    "Light effects are off in Daylight - glow needs a dark background to read.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
    )
}

/**
 * One theme option. The swatch previews the actual palette - its own background and accent
 * pair - so the list is legible without having to apply each theme to find out what it is.
 */
@Composable
private fun ThemeRow(
    theme: AppTheme,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val preview = theme.colorScheme()
    val accents = theme.accents()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .neonPanel(
                fill = scheme.surface,
                accent = if (selected) scheme.primary else scheme.outline,
                intensity = if (selected) 0.9f else 0f
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(preview.background)
                .neonEdge(CircleShape, accents.primary, alpha = 0.6f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(accents.primary, accents.secondary))
                    )
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            Text(
                theme.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) scheme.primary else scheme.onSurface
            )
            Text(
                theme.description,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = scheme.primary,
                modifier = Modifier.accentGlow(cornerRadius = 12.dp, radius = 10.dp)
            )
        }
    }
}

/**
 * A style option that previews itself. It renders the real [SeekBar] at a fixed 45% position
 * with playback animation on, so what you see here is exactly what Now Playing draws —
 * seeking is disabled so tapping the card selects rather than scrubs.
 */
@Composable
private fun SeekStyleRow(
    style: SeekBarStyle,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .neonPanel(
                fill = scheme.surface,
                accent = if (selected) scheme.primary else scheme.outline,
                intensity = if (selected) 0.9f else 0f
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    style.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) scheme.primary else scheme.onSurface
                )
                Text(
                    style.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = scheme.primary,
                    modifier = Modifier.accentGlow(cornerRadius = 12.dp, radius = 10.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        SeekBar(
            positionMs = 45_000L,
            durationMs = 100_000L,
            isPlaying = true,
            style = style,
            onSeek = {},
            enabled = false
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .neonPanel(fill = scheme.surface, accent = scheme.outline, intensity = 0f)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Switch(checked = checked && enabled, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

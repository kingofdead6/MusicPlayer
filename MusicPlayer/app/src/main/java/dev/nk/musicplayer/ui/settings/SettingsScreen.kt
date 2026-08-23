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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nk.musicplayer.LocalContainer
import dev.nk.musicplayer.ui.nowplaying.SeekBar
import dev.nk.musicplayer.ui.nowplaying.SeekBarStyle
import dev.nk.musicplayer.ui.theme.AppShapes
import dev.nk.musicplayer.ui.theme.AppTheme
import dev.nk.musicplayer.ui.theme.accentGlow
import dev.nk.musicplayer.ui.theme.accents
import dev.nk.musicplayer.ui.theme.colorScheme
import dev.nk.musicplayer.ui.theme.neonEdge
import dev.nk.musicplayer.ui.theme.Radii
import dev.nk.musicplayer.ui.theme.neonPanel
import dev.nk.musicplayer.ui.theme.pressable

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
    val storedKey by settings.hfApiKey.collectAsStateWithLifecycle()
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

        // Two per row. At thirteen themes a one-per-row list would push every other setting
        // off the bottom of the screen, and the swatch is the part worth seeing anyway — it
        // survives being half as wide, the description does not.
        items(AppTheme.entries.chunked(2)) { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { entry ->
                    ThemeCard(
                        theme = entry,
                        selected = entry == theme,
                        onClick = { settings.setTheme(entry) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // An odd count would otherwise stretch the last card to full width.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
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

        item { SectionLabel("AI PLAYLISTS") }

        item {
            ApiKeyCard(
                storedKey = storedKey,
                onSave = settings::setHfApiKey
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
private fun ThemeCard(
    theme: AppTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val preview = theme.colorScheme()
    val accents = theme.accents()

    Column(
        modifier = modifier
            .neonPanel(
                fill = scheme.surface,
                accent = if (selected) scheme.primary else scheme.outline,
                corner = Radii.large,
                glowRadius = 16.dp,
                intensity = if (selected) 0.9f else 0f
            )
            .pressable(shape = AppShapes.large, onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The swatch is a slice of the real palette: the theme's own background behind
            // its two accents, so what you pick is what you get.
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(preview.background)
                    .neonEdge(CircleShape, accents.primary, alpha = 0.6f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(accents.primary, accents.secondary))
                        )
                )
            }
            Spacer(Modifier.weight(1f))
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
        Text(
            theme.displayName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) scheme.primary else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            theme.description,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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
                corner = Radii.large,
                glowRadius = 16.dp,
                intensity = if (selected) 0.9f else 0f
            )
            .pressable(shape = AppShapes.large, onClick = onClick)
            .padding(16.dp)
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

/**
 * Hugging Face token entry. The field holds a draft so a half-typed key never reaches the
 * client mid-edit; Save commits it, Clear wipes both the draft and what is on disk. The key
 * is masked by default because Settings is the kind of screen people show other people.
 */
@Composable
private fun ApiKeyCard(
    storedKey: String,
    onSave: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    // Keyed on the stored value so an external change (or Clear) resets the draft.
    var draft by remember(storedKey) { mutableStateOf(storedKey) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    val saved = draft == storedKey

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .neonPanel(
                fill = scheme.surface,
                accent = if (storedKey.isNotBlank()) scheme.primary else scheme.outline,
                intensity = if (storedKey.isNotBlank()) 0.9f else 0f
            )
            .padding(14.dp)
    ) {
        Text(
            "Hugging Face API key",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface
        )
        Text(
            "Needed for AI playlist generation. Create a free token at " +
                "huggingface.co/settings/tokens. It is stored on this device only.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("hf_...") },
            visualTransformation =
                if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            trailingIcon = {
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (revealed) "Hide key" else "Show key"
                    )
                }
            },
            shape = AppShapes.large
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when {
                    storedKey.isBlank() -> "No key saved — AI playlists are off."
                    saved -> "Key saved. AI playlists are ready."
                    else -> "Unsaved changes."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (storedKey.isNotBlank() && saved) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (storedKey.isNotBlank() || draft.isNotBlank()) {
                TextButton(onClick = { draft = ""; onSave("") }) { Text("Clear") }
            }
            TextButton(
                onClick = { onSave(draft) },
                enabled = !saved
            ) { Text("Save") }
        }
    }
}

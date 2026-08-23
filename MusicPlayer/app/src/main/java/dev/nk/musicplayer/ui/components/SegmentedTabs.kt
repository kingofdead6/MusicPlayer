package dev.nk.musicplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.nk.musicplayer.ui.theme.AppShapes
import dev.nk.musicplayer.ui.theme.Motion
import dev.nk.musicplayer.ui.theme.pressable

/**
 * A pill-in-a-pill tab switcher, replacing Material's `TabRow`. TabRow's selection indicator
 * is a hard underline on a square strip — two straight edges in a UI whose whole premise is
 * that nothing is square. Here the track is a capsule and the selected tab is a filled
 * capsule inside it, one step down the corner scale so the two stay concentric.
 *
 * Colours animate rather than snap so switching tabs reads as the fill sliding across.
 */
@Composable
fun SegmentedTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.pill)
            .background(scheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            val fill by animateColorAsState(
                targetValue = if (active) {
                    scheme.primary.copy(alpha = 0.18f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
                animationSpec = Motion.emphasized(),
                label = "tabFill"
            )
            val content by animateColorAsState(
                targetValue = if (active) scheme.primary else scheme.onSurfaceVariant,
                animationSpec = Motion.emphasized(),
                label = "tabContent"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .pressable(
                        shape = AppShapes.pill,
                        onClick = { onSelect(index) },
                        pressedScale = 0.96f
                    )
                    .background(fill, AppShapes.pill)
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = content,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

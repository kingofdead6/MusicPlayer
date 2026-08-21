package dev.nk.musicplayer.ui.nowplaying

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.nk.musicplayer.ui.theme.LocalAccents
import dev.nk.musicplayer.ui.theme.LocalGlowEnabled
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The available seek-bar looks. Each is a full visual treatment of the same interaction, so
 * switching between them never changes what a drag or a tap does.
 */
enum class SeekBarStyle(val displayName: String, val description: String) {
    NeonTube("Neon Tube", "A lit glass rod with a glowing bead"),
    Waveform("Waveform", "Bars that light up as the track plays"),
    Pulse("Pulse Line", "A travelling wave that rides the playhead"),
    Comet("Comet", "A head with a fading tail behind it"),
    Segments("Segments", "Discrete ticks filling one by one"),
    Minimal("Hairline", "A thin, quiet line");
}

/**
 * Shared seek behaviour for every style.
 *
 * All styles are drawn on a Canvas rather than composed from a Material `Slider`, because the
 * fills, tails and per-bar animations do not map onto the slider's track/thumb slots. That
 * means the drag handling lives here once: tap to seek, drag to scrub, and commit on release.
 *
 * [onScrubChange] reports the in-flight position so the caller can show the scrubbed time,
 * and [onSeek] fires once on release.
 */
@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    style: SeekBarStyle,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onScrubChange: (Long?) -> Unit = {}
) {
    val duration = durationMs.coerceAtLeast(1L)
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }

    val playFraction = (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val fraction = if (scrubbing) scrubFraction else playFraction

    // Snap to the finger while scrubbing; ease when following the player, so the ~200ms
    // position ticks do not read as stepping.
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = if (scrubbing) spring(stiffness = 1400f) else tween(220, easing = LinearEasing),
        label = "seekFraction"
    )

    val accents = LocalAccents.current
    val glow = LocalGlowEnabled.current

    fun commit(f: Float) {
        onSeek((f.coerceIn(0f, 1f) * duration).toLong())
    }

    val height = when (style) {
        SeekBarStyle.Waveform -> 44.dp
        SeekBarStyle.Comet, SeekBarStyle.Pulse -> 28.dp
        SeekBarStyle.NeonTube -> 24.dp
        SeekBarStyle.Segments -> 22.dp
        SeekBarStyle.Minimal -> 16.dp
    }

    val interaction = if (!enabled) Modifier else Modifier
        .pointerInput(duration) {
            detectTapGestures { offset ->
                val f = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                commit(f)
            }
        }
        .pointerInput(duration) {
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    scrubbing = true
                    scrubFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onScrubChange((scrubFraction * duration).toLong())
                },
                onHorizontalDrag = { change, _ ->
                    scrubFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onScrubChange((scrubFraction * duration).toLong())
                },
                onDragEnd = {
                    scrubbing = false
                    onScrubChange(null)
                    commit(scrubFraction)
                },
                onDragCancel = {
                    scrubbing = false
                    onScrubChange(null)
                }
            )
        }

    // A single infinite clock drives the animated styles. Styles that ignore it cost nothing
    // extra, and sharing one transition avoids several independent animation loops.
    val transition = rememberInfiniteTransition(label = "seek")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "breathe"
    )

    val active = if (isPlaying) 1f else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(interaction)
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            widthPx = size.width
            val ctx = SeekDraw(
                fraction = animated.coerceIn(0f, 1f),
                primary = accents.primary,
                secondary = accents.secondary,
                track = Color.White.copy(alpha = 0.10f),
                glow = glow,
                phase = phase,
                breathe = breathe,
                playing = active > 0f,
                scrubbing = scrubbing
            )
            when (style) {
                SeekBarStyle.NeonTube -> drawNeonTube(ctx)
                SeekBarStyle.Waveform -> drawWaveform(ctx)
                SeekBarStyle.Pulse -> drawPulseLine(ctx)
                SeekBarStyle.Comet -> drawComet(ctx)
                SeekBarStyle.Segments -> drawSegments(ctx)
                SeekBarStyle.Minimal -> drawMinimal(ctx)
            }
        }
    }
}

/** Everything a style needs to draw one frame. */
private data class SeekDraw(
    val fraction: Float,
    val primary: Color,
    val secondary: Color,
    val track: Color,
    val glow: Boolean,
    val phase: Float,
    val breathe: Float,
    val playing: Boolean,
    val scrubbing: Boolean
)

/** A glass rod: dim track, bright fill, and a bead at the playhead that blooms while playing. */
private fun DrawScope.drawNeonTube(c: SeekDraw) {
    val cy = size.height / 2f
    val thickness = 6.dp.toPx()
    val x = size.width * c.fraction

    drawLine(
        color = c.track,
        start = Offset(0f, cy),
        end = Offset(size.width, cy),
        strokeWidth = thickness,
        cap = StrokeCap.Round
    )
    if (c.glow) {
        // Two wide, faint passes under the fill read as light bleeding through glass.
        drawLine(
            color = c.primary.copy(alpha = 0.18f),
            start = Offset(0f, cy), end = Offset(x, cy),
            strokeWidth = thickness * 3.2f, cap = StrokeCap.Round
        )
        drawLine(
            color = c.primary.copy(alpha = 0.28f),
            start = Offset(0f, cy), end = Offset(x, cy),
            strokeWidth = thickness * 1.9f, cap = StrokeCap.Round
        )
    }
    drawLine(
        brush = Brush.horizontalGradient(listOf(c.secondary, c.primary), endX = size.width),
        start = Offset(0f, cy), end = Offset(x.coerceAtLeast(0.01f), cy),
        strokeWidth = thickness, cap = StrokeCap.Round
    )

    val beadScale = if (c.scrubbing) 1.35f else if (c.playing) c.breathe else 0.85f
    if (c.glow) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(c.primary.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(x, cy),
                radius = 16.dp.toPx() * beadScale
            ),
            radius = 16.dp.toPx() * beadScale,
            center = Offset(x, cy)
        )
    }
    drawCircle(Color.White, radius = 4.dp.toPx() * beadScale.coerceAtMost(1.2f), center = Offset(x, cy))
}

/**
 * Static pseudo-waveform. The bar heights come from a fixed sine mix rather than real audio:
 * decoding the PCM just to draw a seek bar would be disproportionate, and a deterministic
 * shape still gives the eye something to aim at when scrubbing.
 */
private fun DrawScope.drawWaveform(c: SeekDraw) {
    val barCount = 56
    val gap = 2.dp.toPx()
    val barWidth = (size.width - gap * (barCount - 1)) / barCount
    val cy = size.height / 2f
    val headIndex = (c.fraction * barCount).toInt()

    for (i in 0 until barCount) {
        val t = i / barCount.toFloat()
        // Three incommensurate sines: varied, repeatable, no allocation.
        val base = 0.30f + 0.34f * abs(sin(t * 11.0f)) + 0.22f * abs(sin(t * 4.3f + 1.1f)) +
            0.14f * abs(sin(t * 23.0f + 0.4f))
        val played = i <= headIndex
        // The bar under the playhead lifts while playing.
        val lift = if (played && c.playing && abs(i - headIndex) <= 1) 0.22f * c.breathe else 0f
        val h = (base + lift).coerceIn(0.12f, 1f) * size.height
        val x = i * (barWidth + gap)
        val color = when {
            played -> c.primary
            else -> c.track
        }
        if (played && c.glow && abs(i - headIndex) <= 2) {
            drawRoundRect(
                color = c.primary.copy(alpha = 0.35f),
                topLeft = Offset(x - 2.dp.toPx(), cy - h / 2 - 2.dp.toPx()),
                size = Size(barWidth + 4.dp.toPx(), h + 4.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth)
            )
        }
        drawRoundRect(
            color = color,
            topLeft = Offset(x, cy - h / 2),
            size = Size(barWidth, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2)
        )
    }
}

/**
 * A sine that travels along the played portion and flattens into a line past the playhead.
 * The wave only animates while playing, so a paused track reads as still.
 */
private fun DrawScope.drawPulseLine(c: SeekDraw) {
    val cy = size.height / 2f
    val x = size.width * c.fraction
    val amp = if (c.playing) 5.dp.toPx() * c.breathe else 1.5.dp.toPx()
    val steps = 120

    drawLine(
        color = c.track,
        start = Offset(0f, cy), end = Offset(size.width, cy),
        strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
    )

    val path = androidx.compose.ui.graphics.Path()
    var started = false
    for (i in 0..steps) {
        val px = size.width * (i / steps.toFloat())
        if (px > x) break
        // Waves are tallest at the playhead and settle towards the start of the track.
        val falloff = if (x <= 0f) 0f else (px / x)
        val py = cy + sin((px / size.width) * 34f - c.phase * 2f * Math.PI.toFloat()) *
            amp * falloff
        if (!started) { path.moveTo(px, py); started = true } else path.lineTo(px, py)
    }
    if (started) {
        if (c.glow) {
            drawPath(path, color = c.primary.copy(alpha = 0.30f), style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round))
        }
        drawPath(
            path,
            brush = Brush.horizontalGradient(listOf(c.secondary, c.primary), endX = size.width),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
    if (c.glow) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(c.primary.copy(alpha = 0.5f), Color.Transparent),
                center = Offset(x, cy), radius = 14.dp.toPx()
            ),
            radius = 14.dp.toPx(), center = Offset(x, cy)
        )
    }
    drawCircle(Color.White, radius = 3.5.dp.toPx(), center = Offset(x, cy))
}

/** A bright head with a gradient tail — the fill fades out behind the playhead. */
private fun DrawScope.drawComet(c: SeekDraw) {
    val cy = size.height / 2f
    val x = size.width * c.fraction
    val thickness = 4.dp.toPx()

    drawLine(
        color = c.track,
        start = Offset(0f, cy), end = Offset(size.width, cy),
        strokeWidth = thickness, cap = StrokeCap.Round
    )
    if (x > 0f) {
        // Tail length scales with progress so it never overruns the start of the bar.
        val tail = (size.width * 0.28f).coerceAtMost(x)
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, c.secondary.copy(alpha = 0.65f), c.primary),
                startX = x - tail, endX = x
            ),
            start = Offset(x - tail, cy), end = Offset(x, cy),
            strokeWidth = thickness * 1.6f, cap = StrokeCap.Round
        )
        drawLine(
            color = c.primary.copy(alpha = 0.30f),
            start = Offset(0f, cy), end = Offset((x - tail).coerceAtLeast(0f), cy),
            strokeWidth = thickness, cap = StrokeCap.Round
        )
    }
    val headScale = if (c.playing) c.breathe else 0.8f
    if (c.glow) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(c.primary.copy(alpha = 0.7f), Color.Transparent),
                center = Offset(x, cy), radius = 18.dp.toPx() * headScale
            ),
            radius = 18.dp.toPx() * headScale, center = Offset(x, cy)
        )
    }
    drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(x, cy))
}

/** Discrete ticks. The one at the playhead is taller, giving a clear scrub target. */
private fun DrawScope.drawSegments(c: SeekDraw) {
    val count = 40
    val gap = 3.dp.toPx()
    val w = (size.width - gap * (count - 1)) / count
    val cy = size.height / 2f
    val head = (c.fraction * count).roundToInt().coerceIn(0, count)

    for (i in 0 until count) {
        val filled = i < head
        val isHead = i == head - 1
        val h = when {
            isHead -> size.height * (if (c.playing) 0.85f * c.breathe + 0.15f else 0.9f)
            filled -> size.height * 0.55f
            else -> size.height * 0.35f
        }
        val x = i * (w + gap)
        val color = if (filled) c.primary else c.track
        if (filled && isHead && c.glow) {
            drawRoundRect(
                color = c.primary.copy(alpha = 0.4f),
                topLeft = Offset(x - 3.dp.toPx(), cy - h / 2 - 3.dp.toPx()),
                size = Size(w + 6.dp.toPx(), h + 6.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w)
            )
        }
        drawRoundRect(
            color = color,
            topLeft = Offset(x, cy - h / 2),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2)
        )
    }
}

/** Hairline: a dotted remainder and a solid played portion. Quiet by design. */
private fun DrawScope.drawMinimal(c: SeekDraw) {
    val cy = size.height / 2f
    val x = size.width * c.fraction
    drawLine(
        color = c.track,
        start = Offset(0f, cy), end = Offset(size.width, cy),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
    )
    drawLine(
        color = c.primary,
        start = Offset(0f, cy), end = Offset(x.coerceAtLeast(0.01f), cy),
        strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round
    )
    drawCircle(c.primary, radius = if (c.scrubbing) 5.dp.toPx() else 3.dp.toPx(), center = Offset(x, cy))
}

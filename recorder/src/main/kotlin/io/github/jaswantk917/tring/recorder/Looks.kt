package io.github.jaswantk917.tring.recorder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.sin

/** The shape of a call: taller where someone was talking. Pure, so it can be tested. */
object Timeline {
    private const val REST = 0.1f

    fun bars(turns: List<Turn>?, lengthMs: Int, count: Int): FloatArray {
        if (turns.isNullOrEmpty() || lengthMs <= 0) return FloatArray(count) { REST }
        val each = lengthMs.toFloat() / count
        return FloatArray(count) { i ->
            val from = i * each
            val to = from + each
            val spoken = turns.sumOf { turn -> (minOf(to, turn.endMs.toFloat()) - maxOf(from, turn.startMs.toFloat())).coerceAtLeast(0f).toDouble() }.toFloat() / each
            // speech has a rhythm; the same call always draws the same shape
            val rhythm = 0.72f + 0.28f * abs(sin(i * 1.7f) * sin(i * 0.61f + 1f))
            (REST + (1f - REST) * spoken.coerceIn(0f, 1f) * rhythm).coerceIn(REST, 1f)
        }
    }
}

/** A person's mark: their initial, or a figure for a bare number. */
@Composable
internal fun Avatar(who: String, size: Dp, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (fill, ink) = when (abs(who.hashCode() % 3)) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    val shape = MaterialShapes.Cookie9Sided.toShape()
    Box(modifier.size(size).clip(shape).background(fill), contentAlignment = Alignment.Center) {
        val initial = who.firstOrNull { it.isLetter() }
        if (initial != null) {
            Text(initial.uppercase(), color = ink, fontSize = (size.value * 0.4f).sp)
        } else {
            Icon(Icons.Rounded.Person, null, tint = ink, modifier = Modifier.size(size * 0.55f))
        }
    }
}

/** The call as a row of bars you can tap or drag through. */
@Composable
internal fun VoiceTimeline(turns: List<Turn>?, lengthMs: Int, positionMs: Int, enabled: Boolean, onSeek: (Int) -> Unit, modifier: Modifier = Modifier) {
    val bars = remember(turns, lengthMs) { Timeline.bars(turns, lengthMs, BARS) }
    val played = MaterialTheme.colorScheme.primary
    val waiting = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val done = if (lengthMs > 0) (positionMs.toFloat() / lengthMs).coerceIn(0f, 1f) else 0f
    Canvas(
        modifier.fillMaxWidth().height(56.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(done, 0f..1f)
                if (enabled) setProgress { to -> onSeek((to * lengthMs).toInt()); true }
            }
            .pointerInput(enabled, lengthMs) {
                if (enabled) detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f).times(lengthMs).toInt()) }
            }
            .pointerInput(enabled, lengthMs) {
                if (enabled) detectHorizontalDragGestures { change, _ -> onSeek((change.position.x / size.width).coerceIn(0f, 1f).times(lengthMs).toInt()) }
            },
    ) {
        val step = size.width / BARS
        val width = step * 0.56f
        bars.forEachIndexed { i, bar ->
            val height = size.height * bar
            drawRoundRect(
                color = if ((i + 0.5f) / BARS <= done) played else waiting,
                topLeft = Offset(i * step + (step - width) / 2, (size.height - height) / 2),
                size = Size(width, height),
                cornerRadius = CornerRadius(width / 2),
            )
        }
    }
}

private const val BARS = 44

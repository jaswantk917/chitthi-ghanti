package io.github.jaswantk917.ghanti.ui.incall

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.ghanti.ui.util.Haptic
import io.github.jaswantk917.ghanti.ui.util.rememberHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Answering and declining on a locked phone: the handle slides right to answer and left to decline,
 * so a pocket or a stray touch can't do either. Short of the end it springs back; now and then it
 * nudges toward answer, to show it moves. The handshake turns into hanging up as it goes left.
 */
@Composable
internal fun SwipeToAnswer(onAnswer: () -> Unit, onDecline: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // where the finger has the handle while it's held; the animation takes over once it's let go
    var drag by remember { mutableFloatStateOf(0f) }
    val settle = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    // -1 past the decline mark, 1 past the answer mark: crossing one ticks once
    var armed by remember { mutableIntStateOf(0) }
    var travel by remember { mutableFloatStateOf(1f) }
    // the hint nudges move the handle without colouring the track: only a finger does that
    var hinting by remember { mutableStateOf(false) }

    fun commit(answer: Boolean) {
        if (done) return
        done = true
        haptics.play(if (answer) Haptic.Success else Haptic.Delete)
        if (answer) onAnswer() else onDecline()
        scope.launch { settle.animateTo(if (answer) travel else -travel, motion.fastSpatialSpec()) }
    }

    // a nudge toward answer now and then, while nobody's touching it
    LaunchedEffect(dragging, done) {
        if (dragging || done) return@LaunchedEffect
        val nudge = with(density) { 18.dp.toPx() }
        while (true) {
            delay(1800)
            // a tap's own nudge cuts this one short; the loop carries on after it
            try {
                hinting = true
                settle.animateTo(nudge, motion.fastSpatialSpec())
                settle.animateTo(0f, motion.defaultSpatialSpec())
            } catch (e: CancellationException) {
                if (!isActive) throw e
            } finally {
                hinting = false
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(TRACK)
            .clearAndSetSemantics {
                contentDescription = "Incoming call. Slide right to answer, left to decline"
                customActions = listOf(
                    CustomAccessibilityAction("Answer") { commit(true); true },
                    CustomAccessibilityAction("Decline") { commit(false); true },
                )
            },
    ) {
        travel = with(density) { ((maxWidth - HANDLE) / 2 - INSET).toPx() }.coerceAtLeast(1f)
        val x = if (dragging) drag else settle.value
        val p = if (hinting && !dragging) 0f else (x / travel).coerceIn(-1f, 1f)
        val state = rememberDraggableState { delta ->
            drag = (drag + delta).coerceIn(-travel, travel)
            val now = when {
                drag >= travel * MARK -> 1
                drag <= -travel * MARK -> -1
                else -> 0
            }
            if (now != armed) {
                if (now != 0) haptics.play(Haptic.Threshold)
                armed = now
            }
        }

        Box(Modifier.fillMaxSize().clip(CircleShape).background(scheme.surfaceContainerHigh))
        // the side being slid toward fills in its colour, from the middle out to the handle
        Canvas(Modifier.fillMaxSize()) {
            if (x == 0f || p == 0f) return@Canvas
            val tint = if (x > 0) AnswerGreen else DeclineRed
            val middle = size.width / 2f
            val r = size.height / 2f
            val left = min(middle, middle + x) - r
            val right = max(middle, middle + x) + r
            drawRoundRect(tint.copy(alpha = 0.12f + 0.24f * abs(p)), Offset(left, 0f), Size(right - left, size.height), CornerRadius(r))
        }
        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            End(Icons.Rounded.CallEnd, "Decline", DeclineRed, leading = true, fade = max(p, 0f), grow = max(-p, 0f))
            End(Icons.Rounded.Call, "Answer", AnswerGreen, leading = false, fade = max(-p, 0f), grow = max(p, 0f))
        }
        val handle = when {
            p > 0f -> lerp(scheme.primary, AnswerGreen, p)
            p < 0f -> lerp(scheme.primary, DeclineRed, -p)
            else -> scheme.primary
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .offset { IntOffset(x.roundToInt(), 0) }
                .size(HANDLE)
                .clip(CircleShape)
                .background(handle)
                .draggable(
                    state,
                    Orientation.Horizontal,
                    enabled = !done,
                    onDragStarted = {
                        hinting = false
                        settle.stop()
                        drag = settle.value
                        dragging = true
                    },
                    onDragStopped = { velocity ->
                        val fling = with(density) { 1400.dp.toPx() }
                        val at = drag / travel
                        settle.snapTo(drag)
                        dragging = false
                        when {
                            at >= MARK || (at > 0.4f && velocity > fling) -> commit(true)
                            at <= -MARK || (at < -0.4f && velocity < -fling) -> commit(false)
                            else -> {
                                armed = 0
                                settle.animateTo(0f, motion.defaultSpatialSpec(), initialVelocity = velocity)
                            }
                        }
                    },
                )
                // a tap shows which way it goes
                .clickable(remember { MutableInteractionSource() }, indication = null, enabled = !done) {
                    haptics.play(Haptic.Tap)
                    scope.launch {
                        hinting = true
                        try {
                            settle.animateTo(with(density) { 28.dp.toPx() }, motion.fastSpatialSpec())
                            settle.animateTo(0f, motion.defaultSpatialSpec())
                        } finally {
                            hinting = false
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Call,
                null,
                tint = lerp(scheme.onPrimary, Color.White, abs(p)),
                // going left, the handset turns down into hanging up; going right it tips up a little
                modifier = Modifier.size(32.dp).graphicsLayer { rotationZ = if (p < 0f) p * -135f else p * -18f },
            )
        }
    }
}

/** one end of the track: its icon and word, fading as the handle heads the other way */
@Composable
private fun End(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, leading: Boolean, fade: Float, grow: Float) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.graphicsLayer {
            alpha = (1f - fade * 1.6f).coerceIn(0f, 1f)
            val s = 1f + 0.08f * grow
            scaleX = s
            scaleY = s
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1)
        if (!leading) {
            Spacer(Modifier.width(6.dp))
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

private val TRACK = 88.dp
private val HANDLE = 72.dp
private val INSET = 8.dp
/** how far toward an end counts, as a share of the way */
private const val MARK = 0.72f

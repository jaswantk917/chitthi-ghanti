package io.github.jaswantk917.chitthi.ui.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.graphics.blur.BlurStop
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Squishes while held and springs back with a little overshoot. */
@Composable
fun Modifier.pressScale(interaction: InteractionSource, pressed: Float = 0.96f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = if (isPressed) spring(stiffness = Spring.StiffnessHigh)
        else spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Rises into place the first time it's composed, staggered by [index]. [seen] remembers what has
 * already arrived, so scrolling back up doesn't replay it.
 */
@Composable
fun Modifier.arrive(key: Any, index: Int, seen: MutableSet<Any>): Modifier {
    val first = remember(key) { seen.add(key) }
    if (!first) return this
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay((index.coerceAtMost(MAX_STAGGER) * STAGGER_MS))
        launch { progress.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow)) }
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 36.dp.toPx()
        scaleX = 0.94f + 0.06f * p
        scaleY = 0.94f + 0.06f * p
    }
}

/**
 * Content melts into a progressive blur where it slides under the header and the bottom edge.
 * Only Android 13+ can vary the radius; older versions draw it sharp.
 */
@Composable
fun Modifier.edgeBlur(top: Dp, bottom: Dp): Modifier = edgeBlur({ top }, bottom)

/** [top] is read while drawing, so a collapsing header moves the blur without recomposing */
@Composable
fun Modifier.edgeBlur(top: () -> Dp, bottom: Dp): Modifier = if (LocalBlur.current) progressiveBlur(top, bottom) else this

/** whether the phone's GPU gets the progressive blur; see Settings.blur */
val LocalBlur = staticCompositionLocalOf { false }

/**
 * The bottom band's blur, eased in: a straight climb from zero turned text soft within a few dp,
 * which read as a line where the blur began. Cubic, so it stays faint for the first half and does
 * its work near the edge, under the gesture bar.
 */
private val bottomEase = listOf(0.2f, 0.4f, 0.6f, 0.8f, 1f).map { f -> f to (24f * f * f * f).dp }

private fun Modifier.progressiveBlur(top: () -> Dp, bottom: Dp) = blur {
    val height = size.height.value
    if (height > 0f) {
        val topEnd = (top().value / height).coerceIn(0.01f, 0.4f)
        val bottomStart = (1f - bottom.value / height).coerceIn(0.6f, 0.99f)
        blurRadiusSpec = BlurRadiusSpec.verticalGradient(
            listOf(
                BlurStop(0f, 18.dp),
                BlurStop(topEnd, 0.dp),
                BlurStop(bottomStart, 0.dp),
            ) + bottomEase.map { (f, radius) -> BlurStop(bottomStart + (1f - bottomStart) * f, radius) }
        )
    }
}

private const val STAGGER_MS = 32L
private const val MAX_STAGGER = 12

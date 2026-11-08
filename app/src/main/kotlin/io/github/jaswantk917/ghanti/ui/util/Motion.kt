package io.github.jaswantk917.ghanti.ui.util

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
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

private fun Modifier.progressiveBlur(top: () -> Dp, bottom: Dp) = blur {
    val height = size.height.value
    if (height > 0f) {
        val topEnd = (top().value / height).coerceIn(0.01f, 0.4f)
        val bottomStart = (1f - bottom.value / height).coerceIn(0.6f, 0.99f)
        blurRadiusSpec = BlurRadiusSpec.verticalGradient(
            listOf(
                BlurStop(0f, 18.dp),
                // hold most of it until close to the edge, so rows just under the bars are soft, not merely hazy
                BlurStop(topEnd * 0.75f, 8.dp),
                BlurStop(topEnd, 0.dp),
                BlurStop(bottomStart, 0.dp),
                BlurStop(1f, 24.dp),
            )
        )
    }
}


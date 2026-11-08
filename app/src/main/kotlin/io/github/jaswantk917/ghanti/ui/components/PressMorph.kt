package io.github.jaswantk917.ghanti.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/** one frame of a morph; a fresh instance per progress value is what tells Compose the outline changed */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress, Path())
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

/**
 * Melts [rest] into [pressed] while the button is held. M3's own `shapes` only animates rounded
 * corners, so two different polygons there swap in a single frame.
 */
@Composable
fun pressMorph(rest: RoundedPolygon, pressed: RoundedPolygon, interaction: InteractionSource): Shape {
    val morph = remember(rest, pressed) { Morph(rest, pressed) }
    val isPressed by interaction.collectIsPressedAsState()
    val progress by animateFloatAsState(
        if (isPressed) 1f else 0f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "pressMorph",
    )
    return remember(morph, progress) { MorphShape(morph, progress) }
}

/** Melts [off] into [on] as [checked] flips, with a little overshoot. */
@Composable
fun stateMorph(off: RoundedPolygon, on: RoundedPolygon, checked: Boolean): Shape {
    val morph = remember(off, on) { Morph(off, on) }
    val progress by animateFloatAsState(
        if (checked) 1f else 0f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "stateMorph",
    )
    return remember(morph, progress) { MorphShape(morph, progress) }
}

package io.github.jaswantk917.tring.recorder

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.graphics.blur.BlurStop
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/*
 * Ghanti's header, in Tring++: the list slides under a see-through header and melts into a
 * progressive blur there and at the bottom edge; scrolling down tucks the title away, scrolling up
 * brings it back. Copied from Ghanti (ui/home/HomeScreen.kt Header, ui/util/Motion.kt edgeBlur,
 * ui/components/PressMorph.kt) rather than shared: the two apps ship separately.
 */

/** the progressive blur is heavy for Mali GPUs; Ghanti turns it on by default only on Pixels, so does this */
val blurs = Build.MANUFACTURER.equals("Google", ignoreCase = true)

/** content melts into a progressive blur where it slides under the header ([top]) and the bottom edge */
fun Modifier.edgeBlur(top: () -> Dp, bottom: Dp): Modifier = if (!blurs) this else blur {
    val height = size.height.value
    if (height > 0f) {
        val topEnd = (top().value / height).coerceIn(0.01f, 0.4f)
        val bottomStart = (1f - bottom.value / height).coerceIn(0.6f, 0.99f)
        blurRadiusSpec = BlurRadiusSpec.verticalGradient(
            listOf(
                BlurStop(0f, 18.dp),
                // hold most of it until close to the edge, so rows just under the header are soft, not merely hazy
                BlurStop(topEnd * 0.75f, 8.dp),
                BlurStop(topEnd, 0.dp),
                BlurStop(bottomStart, 0.dp),
            ) + bottomEase.map { (f, radius) -> BlurStop(bottomStart + (1f - bottomStart) * f, radius) },
        )
    }
}

/** eased in, so the bottom band stays faint for its first half and does its work by the gesture bar */
private val bottomEase = listOf(0.2f, 0.4f, 0.6f, 0.8f, 1f).map { f -> f to (24f * f * f * f).dp }

/** the list page's header: the title and a line under it, and the settings button, over a see-through wash */
@Composable
fun ListHeader(
    title: String,
    line: String,
    lineColor: Color,
    onSettings: () -> Unit,
    /** how far the list has tucked the title away, in px */
    collapse: () -> Float,
    onTitleHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surface
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = -collapse() }
            .drawBehind {
                drawRect(Brush.verticalGradient(0f to surface.copy(alpha = 0.85f), 0.65f to surface.copy(alpha = 0.65f), 1f to surface.copy(alpha = 0f)))
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(bottom = 10.dp),
    ) {
        Row(
            Modifier
                .onSizeChanged { onTitleHeight(it.height) }
                .graphicsLayer {
                    val p = if (size.height > 0f) collapse() / size.height else 0f
                    alpha = 1f - p
                    scaleX = 1f - 0.08f * p
                    scaleY = 1f - 0.08f * p
                    transformOrigin = TransformOrigin(0f, 1f)
                }
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.displaySmallEmphasized)
                Text(line, style = MaterialTheme.typography.labelLarge, color = lineColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val press = remember { MutableInteractionSource() }
            FilledTonalIconButton(
                onClick = onSettings,
                shape = pressMorph(MaterialShapes.Clover4Leaf, MaterialShapes.Cookie4Sided, press),
                interactionSource = press,
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.Rounded.Tune, "Settings") }
        }
    }
}

/** the status bar's own wash, so the clock stays readable over the list */
@Composable
fun StatusBarWash() {
    val surface = MaterialTheme.colorScheme.surface
    Box(
        Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
            .background(Brush.verticalGradient(listOf(surface.copy(alpha = 0.75f), surface.copy(alpha = 0f)))),
    )
}

/** one frame of a morph; a fresh instance per progress value is what tells Compose the outline changed */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress, Path())
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

/** melts [rest] into [pressed] while the button is held, as Ghanti's settings button does */
@Composable
fun pressMorph(rest: RoundedPolygon, pressed: RoundedPolygon, interaction: InteractionSource): Shape {
    val morph = remember(rest, pressed) { Morph(rest, pressed) }
    val isPressed by interaction.collectIsPressedAsState()
    val progress by animateFloatAsState(if (isPressed) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "pressMorph")
    return remember(morph, progress) { MorphShape(morph, progress) }
}

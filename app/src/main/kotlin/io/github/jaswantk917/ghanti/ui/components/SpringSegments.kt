package io.github.jaswantk917.ghanti.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.ghanti.ui.util.pressScale
import kotlinx.coroutines.launch

/**
 * A row of joined choices with one picked. The picked one's pill springs across to the new choice,
 * stretching as it goes (the edge in front runs ahead, the one behind catches up), and the words
 * turn light exactly where the pill passes under them. [selected] null shows no pill; a tap on any
 * choice, the picked one too, goes to [onSelect], so a caller can let a second tap mean "off".
 */
@Composable
fun SpringSegments(
    labels: List<String>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        val n = labels.size
        val gap = ButtonGroupDefaults.ConnectedSpaceBetween
        val each = (maxWidth - gap * (n - 1)) / n
        fun left(i: Int) = with(density) { ((each + gap) * i).toPx() }
        val width = with(density) { each.toPx() }

        val start = remember { Animatable(left(selected ?: 0)) }
        val end = remember { Animatable(left(selected ?: 0) + width) }
        val shown = remember { Animatable(if (selected != null) 1f else 0f) }
        var last by remember { mutableStateOf(selected) }
        LaunchedEffect(selected, width) {
            val target = selected ?: last ?: 0
            val toRight = left(target) > start.value
            // Material's spatial springs: the edge in front on the fast one, the edge behind on the default one,
            // so the pill stretches, then settles with the expressive scheme's bounce
            val quick = motion.fastSpatialSpec<Float>()
            val lazy = motion.defaultSpatialSpec<Float>()
            launch { start.animateTo(left(target), if (toRight) lazy else quick) }
            launch { end.animateTo(left(target) + width, if (toRight) quick else lazy) }
            launch { shown.animateTo(if (selected != null) 1f else 0f, motion.fastSpatialSpec()) }
            if (selected != null) last = selected
        }

        // the choices, each its own joined piece: round on the row's two ends, small corners where they meet
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, label ->
                if (i > 0) Box(Modifier.width(gap))
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .width(each)
                        .fillMaxHeight()
                        .pressScale(interaction, 0.94f)
                        .clip(piece(i, n))
                        .background(scheme.surfaceContainer)
                        .selectable(selected = i == selected, interactionSource = interaction, indication = null, role = Role.RadioButton) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
        // the pill, and the same words in light, shown only inside the pill
        Box(
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics {}
                .drawWithContent {
                    val s = shown.value
                    if (s <= 0.01f) return@drawWithContent
                    val inset = (1f - s) * size.height / 2f
                    val rect = RoundRect(start.value + inset, inset, end.value - inset, size.height - inset, CornerRadius(size.height / 2f))
                    val path = Path().apply { addRoundRect(rect) }
                    drawPath(path, scheme.primary)
                    clipPath(path) { this@drawWithContent.drawContent() }
                }
                .graphicsLayer {},
        ) {
            Row(Modifier.fillMaxSize()) {
                labels.forEachIndexed { i, label ->
                    if (i > 0) Box(Modifier.width(gap))
                    Box(Modifier.width(each).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        }
    }
}

private fun piece(i: Int, n: Int): RoundedCornerShape {
    val round = CornerSize(50)
    val small = CornerSize(8.dp)
    return when {
        n == 1 -> RoundedCornerShape(round)
        i == 0 -> RoundedCornerShape(topStart = round, bottomStart = round, topEnd = small, bottomEnd = small)
        i == n - 1 -> RoundedCornerShape(topStart = small, bottomStart = small, topEnd = round, bottomEnd = round)
        else -> RoundedCornerShape(small)
    }
}

/** a toggle that springs when it's picked: a quick squeeze, then a bounce back to size */
@Composable
fun Modifier.springPick(checked: Boolean): Modifier {
    val motion = MaterialTheme.motionScheme
    val scale = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(checked) {
        // not on first showing: only a change bounces
        if (!seen) {
            seen = true
            return@LaunchedEffect
        }
        if (checked) {
            // squeezed at once, then Material's fast spatial spring brings it back past size and settles
            scale.snapTo(0.9f)
            scale.animateTo(1f, motion.fastSpatialSpec())
        }
    }
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

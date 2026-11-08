package io.github.jaswantk917.ghanti.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import io.github.jaswantk917.ghanti.ui.util.Haptic
import io.github.jaswantk917.ghanti.ui.util.rememberHaptics
import kotlinx.coroutines.delay

private val welcomeShapes = listOf(
    MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny,
    MaterialShapes.Flower, MaterialShapes.SoftBurst, MaterialShapes.Cookie6Sided,
)

@Composable
fun GhantiWelcome(declined: Boolean, onStart: () -> Unit) {
    val haptics = rememberHaptics()
    var index by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1400)
            progress.snapTo(0f)
            index += 1
            progress.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 120f))
        }
    }
    val morph = remember(index) { Morph(welcomeShapes[index % welcomeShapes.size], welcomeShapes[(index + 1) % welcomeShapes.size]) }
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart), label = "spin",
    )
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 90f)) }
    val path = remember { Path() }
    val matrix = remember { Matrix() }
    val blob = MaterialTheme.colorScheme.primaryContainer
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxSize().background(scheme.surface).windowInsetsPadding(WindowInsets.safeDrawing).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(220.dp).graphicsLayer { val e = entrance.value; scaleX = e; scaleY = e; alpha = e.coerceIn(0f, 1f) },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val side = size.minDimension
                path.rewind()
                morph.toPath(progress.value, path)
                matrix.reset()
                matrix.scale(side, side)
                path.transform(matrix)
                rotate(spin, Offset(side / 2f, side / 2f)) { drawPath(path, blob) }
            }
            Icon(Icons.Rounded.Call, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(84.dp))
        }
        Spacer(Modifier.height(40.dp))
        Text("Calls that know who's calling.", style = MaterialTheme.typography.displaySmallEmphasized, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Strangers ring quietly. The delivery you're waiting for rings through, and Ghanti tells you who it probably is. Everything stays on this phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        AnimatedVisibility(declined, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Text(
                "Ghanti works as your phone app. Tap below and choose Ghanti; you can switch back any time in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = { haptics.play(Haptic.Success); onStart() },
            contentPadding = ButtonDefaults.contentPaddingFor(64.dp),
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) { Text("Let's go", style = MaterialTheme.typography.titleMediumEmphasized) }
    }
}

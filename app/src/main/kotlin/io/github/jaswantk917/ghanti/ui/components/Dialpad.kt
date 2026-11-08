package io.github.jaswantk917.ghanti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.ghanti.ui.util.Haptic
import io.github.jaswantk917.ghanti.ui.util.onLongPress
import io.github.jaswantk917.ghanti.ui.util.pressScale
import io.github.jaswantk917.ghanti.ui.util.rememberHaptics

private val rows = listOf(
    listOf('1' to "", '2' to "ABC", '3' to "DEF"),
    listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
    listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
    listOf('*' to "", '0' to "+", '#' to ""),
)

/** The twelve keys. Holding 0 types +. */
@Composable
fun Dialpad(
    onKey: (Char) -> Unit,
    modifier: Modifier = Modifier,
    keyColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { (digit, letters) ->
                    Key(digit, letters, keyColor, contentColor, onKey)
                }
            }
        }
    }
}

@Composable
private fun Key(digit: Char, letters: String, keyColor: Color, contentColor: Color, onKey: (Char) -> Unit) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(76.dp)
            .pressScale(interaction, 0.92f)
            .clip(pressMorph(MaterialShapes.Circle, MaterialShapes.Square, interaction))
            .background(keyColor)
            .onLongPress { if (digit == '0') { haptics.play(Haptic.Hold); onKey('+') } }
            .clickable(interactionSource = interaction, indication = null) { haptics.play(Haptic.Tap); onKey(digit) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                digit.toString(),
                style = if (digit == '*' || digit == '#') MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineMediumEmphasized,
                color = contentColor,
            )
            if (letters.isNotEmpty()) {
                Text(letters, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.6f))
            }
        }
    }
}

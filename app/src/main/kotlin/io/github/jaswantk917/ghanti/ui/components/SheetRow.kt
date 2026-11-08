package io.github.jaswantk917.ghanti.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.ghanti.ui.util.pressScale
import kotlinx.coroutines.delay

/** a row of an Expressive segmented list: big outer corners on the group, small ones between rows */
@Composable
fun SheetRow(
    icon: ImageVector,
    title: String,
    supporting: String?,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val big = 20.dp
    val small = 4.dp
    val shape = RoundedCornerShape(
        topStart = if (index == 0) big else small,
        topEnd = if (index == 0) big else small,
        bottomStart = if (index == count - 1) big else small,
        bottomEnd = if (index == count - 1) big else small,
    )
    val interaction = remember { MutableInteractionSource() }
    val arrival = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 45L)
        arrival.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 380f))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = arrival.value
                alpha = p.coerceIn(0f, 1f)
                translationY = (1f - p) * 24.dp.toPx()
            }
            .pressScale(interaction, 0.97f)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (tint == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.primary else tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = tint)
            supporting?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

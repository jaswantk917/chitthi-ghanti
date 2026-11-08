package io.github.jaswantk917.ghanti.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.abs

private val avatarShapes = listOf(
    MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny,
    MaterialShapes.Cookie6Sided, MaterialShapes.Flower, MaterialShapes.Pentagon,
    MaterialShapes.Gem, MaterialShapes.SoftBurst, MaterialShapes.Cookie7Sided,
)

enum class AvatarHint { None, Delivery, Ride }

/** A contact's photo, or an initial on a playful shape; a stranger with a delivery guess shows a rider instead. */
@Composable
fun Avatar(name: String?, photo: Uri?, seed: String, modifier: Modifier = Modifier, size: Dp = 48.dp, hint: AvatarHint = AvatarHint.None) {
    var failed by remember(photo) { mutableStateOf(false) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (photo != null && !failed) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Monogram(name, seed, size, hint)
        }
    }
}

@Composable
private fun Monogram(name: String?, seed: String, size: Dp, hint: AvatarHint) {
    val hash = abs(seed.hashCode())
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (hint) {
        AvatarHint.None -> listOf(
            scheme.primaryContainer to scheme.onPrimaryContainer,
            scheme.secondaryContainer to scheme.onSecondaryContainer,
            scheme.tertiaryContainer to scheme.onTertiaryContainer,
            scheme.surfaceContainerHighest to scheme.onSurfaceVariant,
        )[hash % 4]
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    Box(
        Modifier.fillMaxSize().clip(avatarShapes[hash % avatarShapes.size].toShape()).background(container),
        contentAlignment = Alignment.Center,
    ) {
        val initial = name?.firstOrNull { it.isLetter() }
        when {
            hint == AvatarHint.Delivery -> Icon(Icons.Rounded.DeliveryDining, null, tint = content, modifier = Modifier.size(size * 0.55f))
            hint == AvatarHint.Ride -> Icon(Icons.Rounded.LocalTaxi, null, tint = content, modifier = Modifier.size(size * 0.5f))
            initial == null -> Icon(Icons.Rounded.Person, null, tint = content, modifier = Modifier.size(size * 0.5f))
            else -> Text(
                initial.uppercase(),
                color = content,
                style = MaterialTheme.typography.titleLargeEmphasized,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.42f).sp,
            )
        }
    }
}

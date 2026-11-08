package io.github.jaswantk917.ghanti.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette as PaletteIcon
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.ghanti.data.Settings
import io.github.jaswantk917.ghanti.signals.DeliveryRules
import io.github.jaswantk917.ghanti.signals.Signal
import io.github.jaswantk917.ghanti.signals.Strength
import io.github.jaswantk917.ghanti.ui.theme.Palette
import io.github.jaswantk917.ghanti.ui.util.Haptic
import io.github.jaswantk917.ghanti.ui.util.rememberHaptics
import io.github.jaswantk917.ghanti.ui.util.clockTime

@Composable
fun SettingsSheet(
    settings: Settings,
    setup: Setup,
    onChange: ((Settings) -> Settings) -> Unit,
    onMakeDefault: () -> Unit,
    onAllowNotifications: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    fun toggle(on: Boolean) = haptics.play(if (on) Haptic.ToggleOn else Haptic.ToggleOff)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineMediumEmphasized)
            Spacer(Modifier.height(16.dp))
            Section(Icons.Rounded.Call, "Phone app", if (setup.isPhoneApp) "Ghanti answers your calls" else "Another app handles calls") {
                if (setup.isPhoneApp) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                else Button(onClick = onMakeDefault) { Text("Switch") }
            }
            if (setup.isPhoneApp && !setup.screensCalls) {
                Section(Icons.Rounded.NotificationsOff, "Caller ID & spam app", "Ghanti needs this too, to decide which calls ring") {
                    Button(onClick = onMakeDefault) { Text("Allow") }
                }
            }
            Section(Icons.Rounded.NotificationsOff, "Silence strangers", "Numbers not in your contacts ring without a sound. They still show, so you can answer") {
                Switch(settings.screen.silenceUnknown, onCheckedChange = { on -> toggle(on); onChange { it.copy(screen = it.screen.copy(silenceUnknown = on)) } })
            }
            Section(
                Icons.Rounded.DeliveryDining, "Let delivery calls ring",
                if (setup.readsNotifications) "While an order or ride is on its way, strangers ring out loud"
                else "Needs notification access, to see what's on its way",
            ) {
                if (setup.readsNotifications) {
                    Switch(settings.screen.letDeliveries, enabled = settings.screen.silenceUnknown, onCheckedChange = { on ->
                        toggle(on); onChange { it.copy(screen = it.screen.copy(letDeliveries = on)) }
                    })
                } else {
                    Button(onClick = onAllowNotifications) { Text("Allow") }
                }
            }
            Section(Icons.Rounded.Replay, "Ring if they call again", "A second call within 3 minutes usually matters") {
                Switch(settings.screen.repeatCallers, enabled = settings.screen.silenceUnknown, onCheckedChange = { on ->
                    toggle(on); onChange { it.copy(screen = it.screen.copy(repeatCallers = on)) }
                })
            }
            Section(Icons.Rounded.PaletteIcon, "Colours", "Messages blue, or Material You from your wallpaper") {}
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                Palette.entries.forEachIndexed { i, palette ->
                    ToggleButton(
                        checked = settings.palette == palette,
                        onCheckedChange = { haptics.play(Haptic.Segment); onChange { it.copy(palette = palette) } },
                        shapes = if (i == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        modifier = Modifier.weight(1f),
                    ) { Text(if (palette == Palette.Ghanti) "Ghanti" else "Wallpaper", maxLines = 1) }
                }
            }
            Section(Icons.Rounded.DarkMode, "AMOLED black", "Pure black backgrounds in dark mode") {
                Switch(settings.amoled, onCheckedChange = { on -> toggle(on); onChange { it.copy(amoled = on) } })
            }
            Section(Icons.Rounded.BlurOn, "Blur under the bars", "Lists melt into the edges. Heavy on some phones") {
                Switch(settings.blur, onCheckedChange = { on -> toggle(on); onChange { it.copy(blur = on) } })
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Everything is decided on this phone. Notifications are only checked for words like \"on the way\"; nothing is uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(icon: ImageVector, title: String, line: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** what's on its way, and so who gets to ring */
@Composable
fun ExpectingSheet(expecting: List<Signal>, setup: Setup, onAllowNotifications: () -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    val byBrand = expecting.sortedByDescending { it.at }.distinctBy { it.brand }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(
                if (byBrand.isEmpty()) "Nothing on its way" else "On its way",
                style = MaterialTheme.typography.headlineMediumEmphasized,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (byBrand.isEmpty()) "Strangers ring silently. When an order or a ride is coming, their calls ring out loud."
                else "Until these arrive, strangers ring out loud: it's probably the rider.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                byBrand.forEachIndexed { i, s ->
                    val big = 20.dp
                    val small = 4.dp
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(if (i == 0) big else small, if (i == 0) big else small, if (i == byBrand.lastIndex) big else small, if (i == byBrand.lastIndex) big else small))
                            .background(scheme.surfaceContainerHigh)
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(scheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                            Icon(
                                when {
                                    s.strength == Strength.Today -> Icons.Rounded.Today
                                    s.brand in setOf("Uber", "Ola", "Rapido", "BluSmart", "Namma Yatri", "inDrive") -> Icons.Rounded.LocalTaxi
                                    else -> Icons.Rounded.DeliveryDining
                                },
                                null, tint = scheme.onTertiaryContainer,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(if (s.brand == DeliveryRules.DELIVERY) "A delivery" else s.brand, style = MaterialTheme.typography.titleMedium)
                            Text(
                                (if (s.strength == Strength.Live) "On the way" else "Arriving today") + " · " + seen(s.at),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (!setup.readsNotifications) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { haptics.play(Haptic.Tap); onAllowNotifications() }) { Text("Let Ghanti see delivery notifications") }
            }
        }
    }
}

private fun seen(at: Long): String {
    val minutes = (System.currentTimeMillis() - at) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        else -> "since " + clockTime(at)
    }
}

package io.github.jaswantk917.chitthi.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Palette as PaletteIcon
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.chitthi.ai.NanoStatus
import io.github.jaswantk917.chitthi.data.Settings
import io.github.jaswantk917.chitthi.ui.theme.Palette
import io.github.jaswantk917.chitthi.ui.util.Haptic
import io.github.jaswantk917.chitthi.ui.util.rememberHaptics
import io.github.jaswantk917.sheet.SheetColumn
import kotlinx.coroutines.launch

private val deleteChoices = listOf(0 to "Never", 15 to "15 min", 60 to "1 hr", 1440 to "1 day")

@Composable
fun SettingsSheet(
    settings: Settings,
    isDefault: Boolean,
    nanoStatus: NanoStatus,
    onChange: ((Settings) -> Settings) -> Unit,
    onMakeDefault: () -> Unit,
    /** how many old codes a choice would delete right now */
    countExpired: suspend (Int) -> Int,
    onOpenBlocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SheetColumn(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMediumEmphasized)
            Spacer(Modifier.height(16.dp))

            Section(Icons.Rounded.PaletteIcon, "Colours", "Messages blue, or Material You from your wallpaper") {}
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                Palette.entries.forEachIndexed { i, palette ->
                    ToggleButton(
                        checked = settings.palette == palette,
                        onCheckedChange = { haptics.play(Haptic.Segment); onChange { it.copy(palette = palette) } },
                        shapes = if (i == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        modifier = Modifier.weight(1f),
                    ) { Text(if (palette == Palette.Chitthi) "Chitthi" else "Wallpaper", maxLines = 1) }
                }
            }
            Section(Icons.Rounded.DarkMode, "AMOLED black", "Pure black backgrounds in dark mode") {
                Switch(settings.amoled, onCheckedChange = { on ->
                    haptics.play(if (on) Haptic.ToggleOn else Haptic.ToggleOff)
                    onChange { it.copy(amoled = on) }
                })
            }
            Section(Icons.Rounded.Sms, "SMS app", if (isDefault) "Chitthi is your SMS app" else "Reading only. Another app handles new messages.") {
                if (isDefault) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                else Button(onClick = onMakeDefault) { Text("Switch") }
            }
            Section(Icons.Rounded.ContentCopy, "Copy codes as they arrive", "The OTP is on your clipboard before you open anything") {
                Switch(settings.otpAutoCopy, onCheckedChange = { on ->
                    haptics.play(if (on) Haptic.ToggleOn else Haptic.ToggleOff)
                    onChange { it.copy(otpAutoCopy = on) }
                })
            }
            Section(
                Icons.Rounded.DeleteSweep, "Delete old codes",
                if (isDefault) "Codes from businesses go away once they're this old" else "Needs Chitthi as your SMS app",
            ) {}
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                deleteChoices.forEachIndexed { i, (minutes, label) ->
                    ToggleButton(
                        checked = settings.otpDeleteAfterMinutes == minutes,
                        enabled = isDefault,
                        onCheckedChange = {
                            haptics.play(Haptic.Segment)
                            if (minutes == 0) {
                                onChange { it.copy(otpDeleteAfterMinutes = 0) }
                            } else {
                                scope.launch { confirm = minutes to countExpired(minutes) }
                            }
                        },
                        shapes = when (i) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            deleteChoices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text(label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge) }
                }
            }
            Section(Icons.Rounded.NotificationsOff, "Notify for promos", "Off: promos arrive silently and wait in their tab") {
                Switch(settings.promoNotifications, onCheckedChange = { on ->
                    haptics.play(if (on) Haptic.ToggleOn else Haptic.ToggleOff)
                    onChange { it.copy(promoNotifications = on) }
                })
            }
            Section(Icons.Rounded.Block, "Blocked", "Numbers, senders and words you never want to see") {
                TextButton(onClick = { haptics.play(Haptic.Tap); onOpenBlocked() }) { Text("Open") }
            }
            Section(Icons.Rounded.BlurOn, "Blur under the bars", "Lists melt into the top and bottom edges. Heavy on some phones") {
                Switch(settings.blur, onCheckedChange = { on ->
                    haptics.play(if (on) Haptic.ToggleOn else Haptic.ToggleOff)
                    onChange { it.copy(blur = on) }
                })
            }
            // only phones that have it see it; a row that can't be switched on is just noise
            AnimatedVisibility(
                nanoStatus == NanoStatus.Ready || nanoStatus == NanoStatus.Downloading,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Section(
                    Icons.Rounded.AutoAwesome, "Gemini Nano",
                    if (nanoStatus == NanoStatus.Ready) "On this phone: re-sorts unsure messages, names unknown senders, suggests replies"
                    else "Getting the model ready; sorting uses rules meanwhile",
                ) {
                    Switch(settings.useNano, onCheckedChange = { on ->
                        haptics.play(if (on) Haptic.ToggleOn else Haptic.ToggleOff)
                        onChange { it.copy(useNano = on) }
                    })
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Everything is sorted on this phone. Only brand logos are fetched, by website name.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    confirm?.let { (minutes, count) ->
        val label = deleteChoices.first { it.first == minutes }.second
        AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(Icons.Rounded.DeleteSweep, null) },
            title = { Text("Delete codes after $label?") },
            text = {
                Text(
                    if (count == 0) "Nothing to delete yet. From now on, codes from businesses go away once they're $label old."
                    else "$count old code messages go now, and new ones once they're $label old. Messages from people are never touched."
                )
            },
            confirmButton = {
                Button(onClick = {
                    haptics.play(Haptic.Success)
                    onChange { it.copy(otpDeleteAfterMinutes = minutes) }
                    confirm = null
                }) { Text(if (count == 0) "Turn on" else "Delete $count") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
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

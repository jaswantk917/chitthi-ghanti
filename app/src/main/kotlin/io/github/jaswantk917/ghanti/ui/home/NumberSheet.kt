package io.github.jaswantk917.ghanti.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.ghanti.data.Contact
import io.github.jaswantk917.ghanti.data.PhoneNumber
import io.github.jaswantk917.ghanti.signals.DeliveryRules
import io.github.jaswantk917.ghanti.ui.components.Avatar
import io.github.jaswantk917.ghanti.ui.components.SheetRow
import io.github.jaswantk917.ghanti.ui.components.pressMorph
import io.github.jaswantk917.ghanti.ui.util.Haptic
import io.github.jaswantk917.ghanti.ui.util.pressScale
import io.github.jaswantk917.ghanti.ui.util.rememberHaptics
import kotlinx.coroutines.launch

/** "Which number?" for someone with several, with a switch to stop asking. */
@Composable
fun PickNumberSheet(contact: Contact, onCall: (PhoneNumber, always: Boolean) -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var always by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp)) {
            Heading(contact, "Call ${contact.name.substringBefore(' ')} on…")
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                contact.numbers.forEachIndexed { i, n ->
                    SheetRow(Icons.Rounded.Call, n.number, n.label, i, contact.numbers.size, onClick = {
                        haptics.play(Haptic.Send)
                        scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss(); onCall(n, always) }
                    })
                }
            }
            Spacer(Modifier.height(12.dp))
            val interaction = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(interactionSource = interaction, indication = null) {
                        haptics.play(if (always) Haptic.ToggleOff else Haptic.ToggleOn); always = !always
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Always call this number", style = MaterialTheme.typography.titleMedium)
                    Text("Hold their face to change it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = always, onCheckedChange = { haptics.play(if (it) Haptic.ToggleOn else Haptic.ToggleOff); always = it })
            }
        }
    }
}

/** everything about a favourite: each number to call or text, the usual one marked, and how to stop using it */
@Composable
fun FavoriteSheet(
    contact: Contact,
    usual: String?,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit,
    onForget: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    fun close(then: () -> Unit) { scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss(); then() } }
    val usualKey = usual?.let(DeliveryRules::last10)
    val remembered = contact.numbers.size > 1 && contact.numbers.any { DeliveryRules.last10(it.number) == usualKey }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp)) {
            Heading(contact, null)
            Column(Modifier.clip(RoundedCornerShape(20.dp)).background(scheme.surfaceContainerHigh).padding(vertical = 4.dp)) {
                contact.numbers.forEach { n ->
                    val isUsual = remembered && DeliveryRules.last10(n.number) == usualKey
                    val interaction = remember { MutableInteractionSource() }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressScale(interaction, 0.98f)
                            .clickable(interactionSource = interaction, indication = null) { haptics.play(Haptic.Send); close { onCall(n.number) } }
                            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(n.number, style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(n.label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                                AnimatedVisibility(isUsual, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.Rounded.Star, null, tint = scheme.primary, modifier = Modifier.size(14.dp))
                                        Text(" Tap calls this", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                                    }
                                }
                            }
                        }
                        IconButton(onClick = { haptics.play(Haptic.Tap); close { onMessage(n.number) } }) { Icon(Icons.Rounded.Sms, "Message") }
                        val press = remember { MutableInteractionSource() }
                        FilledTonalIconButton(
                            onClick = { haptics.play(Haptic.Send); close { onCall(n.number) } },
                            shape = pressMorph(MaterialShapes.Circle, MaterialShapes.Cookie6Sided, press),
                            interactionSource = press,
                        ) { Icon(Icons.Rounded.Call, "Call") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val options = buildList {
                if (remembered) add(Triple(Icons.Rounded.SwapVert, "Ask which number every time") { haptics.play(Haptic.ToggleOff); onForget(); close {} })
                add(Triple(Icons.Rounded.Person, "Open contact") { haptics.play(Haptic.Tap); close(onOpen) })
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                options.forEachIndexed { i, (icon, title, action) -> SheetRow(icon, title, null, i, options.size, onClick = action) }
            }
        }
    }
}

@Composable
private fun Heading(contact: Contact, line: String?) {
    Row(Modifier.padding(start = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(contact.name, contact.photo, contact.id.toString(), size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(line ?: contact.name, style = MaterialTheme.typography.headlineSmallEmphasized)
            if (line == null && contact.numbers.size > 1) {
                Text("${contact.numbers.size} numbers", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

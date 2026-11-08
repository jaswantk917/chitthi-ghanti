package io.github.jaswantk917.ghanti.ui.home

import android.content.ClipboardManager
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.ghanti.data.Contact
import io.github.jaswantk917.ghanti.data.Kind
import io.github.jaswantk917.ghanti.data.RecentRow
import io.github.jaswantk917.ghanti.data.T9
import io.github.jaswantk917.ghanti.signals.DeliveryRules
import io.github.jaswantk917.ghanti.ui.components.Avatar
import io.github.jaswantk917.ghanti.ui.components.Dialpad
import io.github.jaswantk917.ghanti.ui.components.pressMorph
import io.github.jaswantk917.ghanti.ui.util.Haptic
import io.github.jaswantk917.ghanti.ui.util.onLongPress
import io.github.jaswantk917.ghanti.ui.util.pressScale
import io.github.jaswantk917.ghanti.ui.util.rememberHaptics

private data class Match(val name: String?, val number: String, val photo: android.net.Uri?, val score: Int)

@Composable
fun KeypadSheet(initial: String, contacts: List<Contact>, recents: List<RecentRow>, onCall: (String) -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val context = LocalContext.current
    var typed by rememberSaveable { mutableStateOf(initial) }
    val digits = typed.filter { it.isDigit() }
    val matches = remember(digits, contacts, recents) {
        if (digits.isEmpty()) emptyList()
        else {
            val people = contacts.flatMap { c -> c.numbers.map { Match(c.name, it.number, c.photo, T9.score(digits, c.name, it.number)) } }
            val strangers = recents.filter { it.name == null }.map { Match(null, it.group.number, null, T9.score(digits, "", it.group.number)) }
            (people + strangers).filter { it.score > 0 }
                .distinctBy { DeliveryRules.last10(it.number) }
                .sortedByDescending { it.score }
                .take(3)
        }
    }
    val lastDialled = remember(recents) { recents.firstOrNull { it.group.latest.kind == Kind.Outgoing }?.group?.number }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.fillMaxWidth().heightIn(min = 168.dp).padding(horizontal = 16.dp), verticalArrangement = Arrangement.Bottom) {
                matches.forEach { m ->
                    Suggestion(m, onClick = { haptics.play(Haptic.Send); onCall(m.number) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(48.dp))
                Text(
                    typed.ifEmpty { " " },
                    style = if (typed.length > 13) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        // holding the number pastes whatever number is on the clipboard
                        .combinedClickable(onClick = {}, onLongClick = {
                            val clip = context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                            val number = clip.filter { it.isDigit() || it == '+' }
                            if (number.isNotEmpty()) { haptics.play(Haptic.Copy); typed = number }
                        }),
                )
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedVisibility(typed.isNotEmpty(), enter = scaleIn(), exit = scaleOut()) {
                        val interaction = remember { MutableInteractionSource() }
                        Box(
                            Modifier
                                .size(48.dp)
                                .pressScale(interaction, 0.85f)
                                .clip(RoundedCornerShape(24.dp))
                                // holding it clears everything
                                .onLongPress { haptics.play(Haptic.Delete); typed = "" }
                                .clickable(interactionSource = interaction, indication = null) { haptics.play(Haptic.Tap); typed = typed.dropLast(1) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Dialpad(onKey = { typed += it })
            Spacer(Modifier.height(20.dp))
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(76.dp)
                    .pressScale(interaction, 0.9f)
                    .clip(pressMorph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided, interaction))
                    .background(Color(0xFF1E8E3E))
                    .clickable(interactionSource = interaction, indication = null) {
                        when {
                            typed.isNotBlank() -> { haptics.play(Haptic.Send); onCall(typed) }
                            // like the iPhone: an empty pad brings back the last number you dialled
                            lastDialled != null -> { haptics.play(Haptic.Segment); typed = lastDialled }
                            else -> haptics.play(Haptic.Error)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Call, "Call", tint = Color.White, modifier = Modifier.size(32.dp)) }
        }
    }
}

@Composable
private fun Suggestion(m: Match, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.97f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(m.name, m.photo, m.number, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.name ?: m.number, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (m.name != null) Text(m.number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.Call, null, tint = MaterialTheme.colorScheme.primary)
    }
}

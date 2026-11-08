package io.github.jaswantk917.chitthi.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.chitthi.model.Category
import io.github.jaswantk917.chitthi.ui.util.Haptic
import io.github.jaswantk917.chitthi.ui.util.rememberHaptics
import kotlinx.coroutines.launch

@Composable
fun ConversationActionsSheet(
    title: String,
    pinned: Boolean,
    current: Category,
    hasUnread: Boolean,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onMarkRead: () -> Unit,
    onMove: (Category) -> Unit,
    onDelete: () -> Unit,
    /** null when there's nothing to block, like an RCS business chat */
    blockLabel: String? = null,
    onBlock: () -> Unit = {},
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    fun close(then: () -> Unit = {}) {
        scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss(); then() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { haptics.play(if (pinned) Haptic.ToggleOff else Haptic.ToggleOn); close(onPin) }) {
                    Icon(Icons.Rounded.PushPin, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (pinned) "Unpin" else "Pin")
                }
                if (hasUnread) {
                    FilledTonalButton(onClick = { haptics.play(Haptic.Tap); close(onMarkRead) }) {
                        Icon(Icons.Rounded.MarkEmailRead, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Mark read")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Belongs in", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                val entries = Category.entries
                entries.forEachIndexed { i, category ->
                    ToggleButton(
                        checked = category == current,
                        onCheckedChange = {
                            if (category != current) {
                                haptics.play(Haptic.Segment)
                                close { onMove(category) }
                            }
                        },
                        shapes = when (i) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    ) { Text(category.label, maxLines = 1) }
                }
            }
            Text(
                "Everything from this sender goes there from now on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(24.dp))
            if (blockLabel != null) {
                OutlinedButton(onClick = { haptics.play(Haptic.ToggleOn); close(onBlock) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Block, null)
                    Spacer(Modifier.width(8.dp))
                    Text(blockLabel)
                }
                Spacer(Modifier.height(8.dp))
            }
            OutlinedButton(
                onClick = { close(onDelete) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Delete, null)
                Spacer(Modifier.width(8.dp))
                Text("Delete these messages")
            }
        }
    }
}

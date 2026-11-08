package io.github.jaswantk917.ghanti.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.ghanti.data.Memo
import io.github.jaswantk917.ghanti.ui.util.Haptic
import io.github.jaswantk917.ghanti.ui.util.rememberHaptics
import kotlinx.coroutines.launch

/**
 * Who a stranger turned out to be, and a note, for one call. Everything saves as you type, so
 * the call ending or the sheet being swiped away never loses a word.
 */
@Composable
fun MemoSheet(
    subtitle: String,
    stranger: Boolean,
    suggestions: List<String>,
    memo: Memo?,
    onSave: (label: String?, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var label by remember { mutableStateOf(memo?.label.orEmpty()) }
    var note by remember { mutableStateOf(memo?.note.orEmpty()) }
    val noteFocus = remember { FocusRequester() }
    val fieldColors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    fun save() = onSave(label.ifBlank { null }, note)
    LaunchedEffect(Unit) { runCatching { noteFocus.requestFocus() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            Text("About this call", style = MaterialTheme.typography.headlineSmallEmphasized)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            if (stranger) {
                TextField(
                    value = label,
                    onValueChange = { label = it; save() },
                    placeholder = { Text("Who was it?") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    shape = RoundedCornerShape(20.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { s ->
                            val on = label == s
                            FilterChip(
                                selected = on,
                                onClick = {
                                    haptics.play(if (on) Haptic.ToggleOff else Haptic.ToggleOn)
                                    label = if (on) "" else s
                                    save()
                                },
                                label = { Text(s) },
                                leadingIcon = if (on) {
                                    { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                                } else null,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            TextField(
                value = note,
                onValueChange = { note = it; save() },
                placeholder = { Text("Note: what they said, an order number, a time…") },
                minLines = 3,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(20.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth().focusRequester(noteFocus),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { haptics.play(Haptic.Tap); scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() } },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Done") }
        }
    }
}

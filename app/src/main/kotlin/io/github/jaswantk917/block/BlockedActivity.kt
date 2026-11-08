package io.github.jaswantk917.block

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.jaswantk917.chitthi.ChitthiApp
import io.github.jaswantk917.chitthi.data.SmsStore
import io.github.jaswantk917.chitthi.ui.components.pressMorph
import io.github.jaswantk917.chitthi.ui.theme.ChitthiTheme
import io.github.jaswantk917.chitthi.ui.util.Haptic
import io.github.jaswantk917.chitthi.ui.util.pressScale
import io.github.jaswantk917.chitthi.ui.util.rememberHaptics
import io.github.jaswantk917.chitthi.ui.util.shortTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Numbers on Android's block list, the rules on top of it, and the SMS those rules stopped. Opened from either side. */
class BlockedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by ChitthiApp.instance.prefs.settings.collectAsState()
            ChitthiTheme(settings.palette, settings.amoled) { BlockedScreen(onBack = ::finish) }
        }
    }
}

@Composable
private fun BlockedScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val store = ChitthiApp.instance.blocks
    val rules by store.rules.collectAsState()
    val stopped by store.blockedSms.collectAsState()
    var numbers by remember { mutableStateOf<List<String>?>(null) }
    fun reloadNumbers() = scope.launch { numbers = withContext(Dispatchers.IO) { SystemBlocks.list(context) } }
    LifecycleResumeEffect(Unit) {
        reloadNumbers()
        onPauseOrDispose {}
    }
    var adding by remember { mutableStateOf(false) }
    var addingNumber by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<BlockedSms?>(null) }
    val scheme = MaterialTheme.colorScheme
    val bars = WindowInsets.safeDrawing.asPaddingValues()

    LazyColumn(
        Modifier.fillMaxSize().background(scheme.surface),
        contentPadding = PaddingValues(top = bars.calculateTopPadding() + 8.dp, bottom = bars.calculateBottomPadding() + 24.dp),
    ) {
        item(key = "top") {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val press = remember { MutableInteractionSource() }
                FilledTonalIconButton(
                    onClick = { haptics.play(Haptic.Tap); onBack() },
                    shape = pressMorph(MaterialShapes.Cookie9Sided, MaterialShapes.Cookie4Sided, press),
                    interactionSource = press,
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            }
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp)) {
                Text("Blocked", style = MaterialTheme.typography.displaySmallEmphasized)
                Text("Calls and SMS that never reach you. Saved contacts are never caught by a rule.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }

        item(key = "rules-head") { Heading("Rules", "Add rule") { haptics.play(Haptic.Tap); adding = true } }
        if (rules.isEmpty()) {
            item(key = "quick") {
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(scheme.surfaceContainer).padding(16.dp)) {
                    Text("Good places to start", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SuggestionChip(onClick = { haptics.play(Haptic.ToggleOn); store.add(RuleKind.StartsWith, "140", Applies.Calls) }, label = { Text("Telemarketing · 140…") })
                        SuggestionChip(onClick = { haptics.play(Haptic.ToggleOn); store.add(RuleKind.SenderId, "*-P", Applies.Sms) }, label = { Text("Promo SMS · *-P") })
                    }
                }
            }
        }
        items(rules, key = { "r${it.id}" }) { rule ->
            Row(
                Modifier.animateItem().padding(start = 24.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(iconOf(rule.kind))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(rule.pattern, style = MaterialTheme.typography.titleMedium, fontFamily = if (rule.kind == RuleKind.Regex) FontFamily.Monospace else null, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${rule.kind.label} · ${rule.applies.label}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                IconButton(onClick = { haptics.play(Haptic.Delete); store.remove(rule.id) }) { Icon(Icons.Rounded.Close, "Remove rule") }
            }
        }

        item(key = "numbers-head") { Heading("Numbers", if (numbers != null) "Add number" else null) { haptics.play(Haptic.Tap); addingNumber = true } }
        when {
            numbers == null -> item(key = "numbers-off") {
                Note("Android keeps this list for the default SMS or phone app. Make Chitthi or Ghanti one of them to see it here.")
            }
            numbers!!.isEmpty() -> item(key = "numbers-empty") { Note("No blocked numbers. Hold a chat, or open a call in Recents, to block one.") }
            else -> items(numbers!!, key = { "n$it" }) { number ->
                Row(Modifier.animateItem().padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(Icons.Rounded.Block)
                    Spacer(Modifier.width(14.dp))
                    Text(number, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        haptics.play(Haptic.ToggleOff)
                        scope.launch { withContext(Dispatchers.IO) { SystemBlocks.unblock(context, number) }; reloadNumbers() }
                    }) { Text("Unblock") }
                }
            }
        }

        if (stopped.isNotEmpty()) {
            item(key = "sms-head") { Heading("Stopped messages", "Clear") { haptics.play(Haptic.Delete); store.forgetAllSms() } }
            items(stopped.asReversed(), key = { "s${it.at}${it.address}" }) { sms ->
                val interaction = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .pressScale(interaction, 0.98f)
                        .clickable(interactionSource = interaction, indication = null) { haptics.play(Haptic.Tap); opened = sms }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Glyph(Icons.Rounded.Sms)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(sms.address, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(shortTime(sms.at), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                        }
                        Text(sms.body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    if (adding) RuleSheet(onDismiss = { adding = false }, onAdd = { kind, pattern, applies -> store.add(kind, pattern, applies) })
    if (addingNumber) {
        var number by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingNumber = false },
            title = { Text("Block a number") },
            text = {
                TextField(number, { number = it }, placeholder = { Text("+91 98765 43210") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            },
            confirmButton = {
                TextButton(enabled = number.any { it.isDigit() }, onClick = {
                    haptics.play(Haptic.ToggleOn)
                    val n = number.trim()
                    addingNumber = false
                    scope.launch { withContext(Dispatchers.IO) { SystemBlocks.block(context, n) }; reloadNumbers() }
                }) { Text("Block") }
            },
            dismissButton = { TextButton(onClick = { addingNumber = false }) { Text("Cancel") } },
        )
    }
    opened?.let { sms ->
        StoppedSheet(
            sms,
            onRestore = {
                scope.launch {
                    withContext(Dispatchers.IO) { SmsStore.insertIncoming(context, sms.address, sms.body, sms.at, sms.subId) }
                    store.forget(sms)
                    haptics.play(Haptic.Success)
                }
            },
            onDelete = { haptics.play(Haptic.Delete); store.forget(sms) },
            onDismiss = { opened = null },
        )
    }
}

private fun iconOf(kind: RuleKind) = when (kind) {
    RuleKind.StartsWith -> Icons.Rounded.Dialpad
    RuleKind.SenderId -> Icons.Rounded.Sms
    RuleKind.Words -> Icons.Rounded.TextFields
    RuleKind.Regex -> Icons.Rounded.Code
}

@Composable
private fun Glyph(icon: ImageVector) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Heading(title: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        if (action != null) {
            TextButton(onClick = onAction) {
                if (action.startsWith("Add")) { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
                Text(action)
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
}

private fun hintFor(kind: RuleKind) = when (kind) {
    RuleKind.StartsWith -> "140" to "Numbers beginning with this. 140 is India's telemarketing series; use + for a country, like +92."
    RuleKind.SenderId -> "*-P" to "Business sender names. * matches anything: *-P is every promotional sender, *-AMAZON* one company."
    RuleKind.Words -> "loan approved, lottery" to "SMS containing any of these words, separated by commas."
    RuleKind.Regex -> "^\\+9[23]" to "A regular expression, checked against the number and the message."
}

/** a new rule: what it looks at, the pattern, and whether it's for SMS, calls or both */
@Composable
private fun RuleSheet(onDismiss: () -> Unit, onAdd: (RuleKind, String, Applies) -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var kind by remember { mutableStateOf(RuleKind.StartsWith) }
    var pattern by remember { mutableStateOf("") }
    var applies by remember { mutableStateOf(Applies.Both) }
    val problem = BlockRules.problem(kind, pattern)
    val short = mapOf(RuleKind.StartsWith to "Starts with", RuleKind.SenderId to "Sender", RuleKind.Words to "Words", RuleKind.Regex to "Regex")
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            Text("New rule", style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.padding(start = 4.dp, bottom = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                RuleKind.entries.forEachIndexed { i, k ->
                    ToggleButton(
                        checked = k == kind,
                        onCheckedChange = { if (k != kind) { haptics.play(Haptic.Segment); kind = k } },
                        shapes = when (i) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            RuleKind.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(short.getValue(k), maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                }
            }
            Spacer(Modifier.height(16.dp))
            val (example, help) = hintFor(kind)
            TextField(
                value = pattern,
                onValueChange = { pattern = it },
                placeholder = { Text(example) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (kind == RuleKind.StartsWith) KeyboardType.Phone else KeyboardType.Text),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = if (kind == RuleKind.Regex) FontFamily.Monospace else null),
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedContent(help, label = "help") { shown ->
                Text(shown, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            }
            AnimatedVisibility(pattern.isNotBlank() && problem != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Text(problem.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            }
            AnimatedVisibility(!kind.smsOnly, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    Applies.entries.forEachIndexed { i, a ->
                        ToggleButton(
                            checked = a == applies,
                            onCheckedChange = { if (a != applies) { haptics.play(Haptic.Segment); applies = a } },
                            shapes = when (i) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                Applies.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(if (a == Applies.Both) "Both" else a.label, maxLines = 1) }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                enabled = problem == null,
                onClick = {
                    haptics.play(Haptic.Success)
                    onAdd(kind, pattern, applies)
                    scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }
                },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Add rule") }
        }
    }
}

@Composable
private fun StoppedSheet(sms: BlockedSms, onRestore: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun close(then: () -> Unit) { scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss(); then() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(sms.address, style = MaterialTheme.typography.headlineSmallEmphasized)
            Text(shortTime(sms.at), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(sms.body, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { close(onDelete) }, modifier = Modifier.weight(1f)) { Text("Delete") }
                Button(onClick = { close(onRestore) }, modifier = Modifier.weight(1f)) { Text("Move to inbox") }
            }
        }
    }
}

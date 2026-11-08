package io.github.jaswantk917.ghanti.ui.incall

import android.content.ClipData
import android.content.ClipboardManager
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.SwapCalls
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import io.github.jaswantk917.ghanti.GhantiApp
import io.github.jaswantk917.ghanti.data.MemoList
import io.github.jaswantk917.ghanti.incall.CallUi
import io.github.jaswantk917.ghanti.incall.Calls
import io.github.jaswantk917.ghanti.incall.Phase
import io.github.jaswantk917.ghanti.signals.DeliveryRules
import io.github.jaswantk917.ghanti.signals.Guess
import io.github.jaswantk917.ghanti.ui.components.Avatar
import io.github.jaswantk917.ghanti.ui.components.AvatarHint
import io.github.jaswantk917.ghanti.ui.components.Dialpad
import io.github.jaswantk917.ghanti.ui.components.MemoSheet
import io.github.jaswantk917.ghanti.ui.components.SheetRow
import io.github.jaswantk917.ghanti.ui.components.pressMorph
import io.github.jaswantk917.ghanti.ui.components.stateMorph
import io.github.jaswantk917.ghanti.ui.util.Haptic
import io.github.jaswantk917.ghanti.ui.util.clockTime
import io.github.jaswantk917.ghanti.ui.util.pressScale
import io.github.jaswantk917.ghanti.ui.util.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// answer and decline keep the colours every phone has taught us
private val AnswerGreen = Color(0xFF1E8E3E)
private val DeclineRed = Color(0xFFD93025)

/** [ended] freezes the last call on screen for its "Call ended" beat. */
@Composable
fun InCallScreen(
    call: CallUi,
    others: List<CallUi>,
    audio: CallAudioState?,
    endedAt: Long?,
    noting: Boolean,
    onNoting: (Boolean) -> Unit,
    onAddCall: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberHaptics()
    val memos by GhantiApp.instance.memos.all.collectAsState()
    val memo = remember(memos, call.number, call.createdAt) { MemoList.match(memos, call.number, call.createdAt) }
    val before = remember(memos, call.number, call.createdAt) { MemoList.previousLabel(memos, call.number, call.createdAt) }
    val stranger = call.person == null && call.number.isNotBlank()
    fun confirm(tag: String) {
        haptics.play(Haptic.ToggleOn)
        GhantiApp.instance.memos.save(call.number, call.createdAt, tag, memo?.note.orEmpty())
    }
    var keypad by remember(call.call) { mutableStateOf(false) }
    var tones by remember(call.call) { mutableStateOf("") }
    var replies by remember { mutableStateOf(false) }
    var routes by remember { mutableStateOf(false) }
    val ended = endedAt != null
    // notes are for a call that's happening or just happened, and the keypad needs the room
    val canNote = !keypad && (ended || call.phase == Phase.Active || call.phase == Phase.Holding || call.phase == Phase.Dialing)
    // back closes the keypad before it leaves the call screen
    BackHandler(enabled = keypad) { keypad = false }

    BoxWithConstraints(Modifier.fillMaxSize().background(scheme.surface)) {
        // the face gives up room on short screens and when a note is showing
        val backdrop = (maxHeight - 680.dp - if (canNote) 56.dp else 0.dp).coerceIn(150.dp, 240.dp)
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(others.isNotEmpty() && !ended, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                others.firstOrNull()?.let { OtherCall(it, Modifier.padding(top = 12.dp)) }
            }
            Spacer(Modifier.height(36.dp))
            Status(call, endedAt)
            Spacer(Modifier.height(28.dp))
            // the keypad needs the room, so the face steps aside while it's open
            AnimatedVisibility(
                visible = !keypad || ended,
                enter = expandVertically() + fadeIn() + scaleIn(initialScale = 0.8f),
                exit = shrinkVertically() + fadeOut() + scaleOut(targetScale = 0.8f),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        LivingBackdrop(ringing = call.phase == Phase.Ringing && !ended, size = backdrop)
                        Avatar(
                            name = call.person?.name,
                            photo = call.person?.photo,
                            seed = call.number,
                            size = backdrop * 0.55f,
                            hint = call.outcome?.guess?.let { if (it.ride) AvatarHint.Ride else AvatarHint.Delivery } ?: AvatarHint.None,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
            Text(
                call.title,
                style = MaterialTheme.typography.displaySmallEmphasized,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Secondary(call, tones)
            Spacer(Modifier.height(14.dp))
            Hints(
                call, ended,
                label = memo?.label?.takeIf { stranger },
                before = before?.takeIf { stranger },
                canConfirm = stranger && call.phase != Phase.Ringing,
                onConfirm = ::confirm,
                onPick = { onNoting(true) },
            )
            AnimatedVisibility(canNote, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                NoteEntry(memo?.note.orEmpty(), onClick = { haptics.play(Haptic.Tap); onNoting(true) }, modifier = Modifier.padding(top = 14.dp))
            }
            Spacer(Modifier.weight(1f))

            AnimatedContent(
                targetState = when {
                    ended -> Mode.Ended
                    call.phase == Phase.ChooseSim -> Mode.Sim
                    call.phase == Phase.Ringing -> Mode.Incoming
                    keypad -> Mode.Keypad
                    else -> Mode.Controls
                },
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 4 } + scaleIn(initialScale = 0.92f)) togetherWith
                        (fadeOut() + slideOutVertically { it / 4 } + scaleOut(targetScale = 0.92f))
                },
                label = "mode",
            ) { mode ->
                when (mode) {
                    Mode.Incoming -> Incoming(call, onMessage = { replies = true })
                    Mode.Controls -> Controls(call, audio, others, onKeypad = { keypad = true }, onRoutes = { routes = true }, onAddCall = onAddCall)
                    Mode.Keypad -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Dialpad(onKey = { c -> tones += c; Calls.tone(call.call, c) })
                        Spacer(Modifier.height(20.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(96.dp))
                            EndButton(call)
                            TextButton(onClick = { keypad = false }, modifier = Modifier.width(96.dp)) { Text("Hide") }
                        }
                    }
                    Mode.Sim -> SimChooser(call)
                    Mode.Ended -> Spacer(Modifier.height(1.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (noting) {
        MemoSheet(
            subtitle = listOf(call.title, clockTime(call.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis())).joinToString(" · "),
            stranger = stranger,
            suggestions = (call.outcome?.guess?.brands.orEmpty().map(DeliveryRules::tagFor) + listOfNotNull(before)).distinct(),
            memo = memo,
            onSave = { label, note -> GhantiApp.instance.memos.save(call.number, call.createdAt, label, note) },
            onDismiss = { onNoting(false) },
        )
    }
    if (replies) QuickReplies(call, call.outcome?.guess, onDismiss = { replies = false })
    if (routes && audio != null) RouteSheet(audio, onDismiss = { routes = false })
}

private enum class Mode { Incoming, Controls, Keypad, Sim, Ended }

@Composable
private fun Status(call: CallUi, endedAt: Long?) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(call.phase, endedAt) {
        while (endedAt == null && call.phase == Phase.Active) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000)
        }
    }
    // animate between states, not on every tick of the timer
    val state = when {
        endedAt != null -> -1
        else -> call.phase.ordinal
    }
    AnimatedContent(state, label = "status") { shown ->
        val text = when {
            shown == -1 -> "Call ended" + (if (call.connectedAt > 0 && endedAt != null) " · " + duration(endedAt - call.connectedAt) else "")
            call.phase == Phase.Ringing -> if (call.silenced) "Ringing silently" else "Incoming call"
            call.phase == Phase.Dialing -> "Calling…"
            call.phase == Phase.Holding -> "On hold"
            call.phase == Phase.ChooseSim -> "Call with which SIM?"
            call.isConference -> "Conference · " + duration(now - call.connectedAt)
            call.connectedAt > 0 -> duration(now - call.connectedAt)
            else -> ""
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun duration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** the number under a name; holding it copies, handy for masked delivery numbers */
@Composable
private fun Secondary(call: CallUi, tones: String) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val line = when {
        tones.isNotEmpty() -> tones
        call.person != null || call.networkName != null -> call.number
        call.number.isNotBlank() -> "Not in your contacts"
        else -> null
    } ?: return
    Text(
        line,
        style = if (tones.isNotEmpty()) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.StartEllipsis,
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    if (call.number.isBlank()) return@combinedClickable
                    haptics.play(Haptic.Copy)
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("number", call.number))
                    Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                },
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Why it rang, or didn't, in a few words. Once you're talking, tapping the guess says "yes, it was
 * them" and the call log remembers it.
 */
@Composable
private fun Hints(
    call: CallUi,
    ended: Boolean,
    label: String?,
    before: String?,
    canConfirm: Boolean,
    onConfirm: (String) -> Unit,
    onPick: () -> Unit,
) {
    val guess = call.outcome?.guess
    val candidates = guess?.brands.orEmpty().map(DeliveryRules::tagFor)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedContent(label, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.9f)) }, label = "who") { said ->
            when {
                said != null -> Pill(Icons.Rounded.Verified, said, strong = true)
                guess != null -> Pill(
                    icon = when {
                        guess.confident -> Icons.Rounded.Verified
                        guess.ride -> Icons.Rounded.LocalTaxi
                        else -> Icons.Rounded.DeliveryDining
                    },
                    text = guess.title + (guess.detail?.let { " · $it" } ?: ""),
                    strong = guess.confident,
                    action = if (canConfirm && candidates.isNotEmpty()) "Yes?" else null,
                    onClick = if (canConfirm && candidates.isNotEmpty()) {
                        { if (candidates.size == 1) onConfirm(candidates.single()) else onPick() }
                    } else null,
                )
                before != null -> Pill(
                    Icons.Rounded.History, "$before last time", strong = false,
                    action = if (canConfirm) "Again?" else null,
                    onClick = if (canConfirm) {
                        { onConfirm(before) }
                    } else null,
                )
                else -> Spacer(Modifier.height(0.dp))
            }
        }
        if (call.silenced && call.phase == Phase.Ringing && !ended) {
            Pill(Icons.Rounded.NotificationsOff, "Not ringing out loud", strong = false)
        }
        if (call.outcome?.repeat == true && call.phase == Phase.Ringing) {
            Pill(Icons.Rounded.Replay, "Called again, so it rang", strong = false)
        }
    }
}

/** [action] is the short question a tap answers, e.g. "Yes?" */
@Composable
private fun Pill(icon: ImageVector, text: String, strong: Boolean, action: String? = null, onClick: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = if (strong) scheme.primary to scheme.onPrimary else scheme.tertiaryContainer to scheme.onTertiaryContainer
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .pressScale(interaction, 0.94f)
            .clip(CircleShape)
            .background(container)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
            .padding(start = 14.dp, end = if (action != null) 6.dp else 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = container,
                modifier = Modifier.clip(CircleShape).background(content).padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

/** the note for this call: a quiet button until there's something, then the words themselves */
@Composable
private fun NoteEntry(note: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(interaction, 0.95f)
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.EditNote, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        AnimatedContent(note.isBlank(), label = "note") { empty ->
            Text(
                if (empty) "Add a note" else note,
                style = if (empty) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                color = if (empty) scheme.onSurfaceVariant else scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** a slow, breathing shape behind the face; ripples go out while it rings */
@Composable
private fun LivingBackdrop(ringing: Boolean, size: androidx.compose.ui.unit.Dp) {
    val scheme = MaterialTheme.colorScheme
    val loop = rememberInfiniteTransition(label = "backdrop")
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "spin")
    val breathe by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(3_200), RepeatMode.Reverse), label = "breathe")
    val ripple by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1_800, easing = LinearEasing)), label = "ripple")
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Cookie12Sided) }
    val path = remember { Path() }
    val matrix = remember { Matrix() }
    Canvas(Modifier.size(size)) {
        val side = this.size.minDimension
        if (ringing) {
            for (i in 0..1) {
                val p = (ripple + i * 0.5f) % 1f
                drawCircle(
                    color = scheme.primary.copy(alpha = 0.28f * (1f - p)),
                    radius = side * (0.3f + 0.22f * p),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }
        path.rewind()
        morph.toPath(breathe, path)
        matrix.reset()
        val blob = side * 0.82f
        matrix.translate((side - blob) / 2f, (side - blob) / 2f)
        matrix.scale(blob, blob)
        path.transform(matrix)
        rotate(spin, Offset(side / 2f, side / 2f)) { drawPath(path, scheme.primaryContainer.copy(alpha = 0.55f)) }
    }
}

@Composable
private fun Incoming(call: CallUi, onMessage: () -> Unit) {
    val haptics = rememberHaptics()
    val bob = rememberInfiniteTransition(label = "bob").animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bob")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (call.number.isNotBlank()) {
            FilledTonalButton(onClick = { haptics.play(Haptic.Tap); onMessage() }) {
                Icon(Icons.Rounded.Sms, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reply with a message")
            }
            Spacer(Modifier.height(28.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundAction(Icons.Rounded.CallEnd, "Decline", DeclineRed) { haptics.play(Haptic.Delete); Calls.decline(call.call) }
            RoundAction(
                Icons.Rounded.Call, "Answer", AnswerGreen,
                modifier = Modifier.graphicsLayer { translationY = -6.dp.toPx() * bob.value },
            ) { haptics.play(Haptic.Success); Calls.answer(call.call) }
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(80.dp)
                .pressScale(interaction, 0.9f)
                .clip(pressMorph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided, interaction))
                .background(color)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Controls(
    call: CallUi,
    audio: CallAudioState?,
    others: List<CallUi>,
    onKeypad: () -> Unit,
    onRoutes: () -> Unit,
    onAddCall: () -> Unit,
) {
    val haptics = rememberHaptics()
    val muted = audio?.isMuted == true
    val route = audio?.route ?: CallAudioState.ROUTE_EARPIECE
    val hasBluetooth = audio != null && audio.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0
    val held = call.phase == Phase.Holding
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Control(if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, if (muted) "Muted" else "Mute", muted) {
                haptics.play(if (muted) Haptic.ToggleOff else Haptic.ToggleOn); Calls.mute(!muted)
            }
            Control(Icons.Rounded.Dialpad, "Keypad", false) { haptics.play(Haptic.Tap); onKeypad() }
            // a tap flips speaker and phone; a watch or earbuds are a long press away
            val speaker = route == CallAudioState.ROUTE_SPEAKER
            val elsewhere = route == CallAudioState.ROUTE_BLUETOOTH || route == CallAudioState.ROUTE_WIRED_HEADSET
            Control(
                icon = when (route) {
                    CallAudioState.ROUTE_BLUETOOTH -> Icons.Rounded.Bluetooth
                    CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Rounded.Headphones
                    else -> Icons.AutoMirrored.Rounded.VolumeUp
                },
                label = if (elsewhere) routeName(audio) else "Speaker",
                checked = speaker || elsewhere,
                onLongClick = if (hasBluetooth || audio?.supportedRouteMask?.and(CallAudioState.ROUTE_WIRED_HEADSET) != 0) {
                    { haptics.play(Haptic.Hold); onRoutes() }
                } else null,
            ) {
                haptics.play(if (speaker) Haptic.ToggleOff else Haptic.ToggleOn)
                Calls.route(if (speaker) CallAudioState.ROUTE_EARPIECE else CallAudioState.ROUTE_SPEAKER)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Control(Icons.Rounded.Pause, if (held) "On hold" else "Hold", held, enabled = call.canHold && others.none { it.phase == Phase.Holding }) {
                haptics.play(if (held) Haptic.ToggleOff else Haptic.ToggleOn); Calls.hold(call.call, !held)
            }
            Control(Icons.Rounded.PersonAdd, "Add call", false, enabled = others.isEmpty()) { haptics.play(Haptic.Tap); onAddCall() }
            when {
                call.canMerge && others.isNotEmpty() -> Control(Icons.Rounded.CallMerge, "Merge", false) { haptics.play(Haptic.Success); Calls.merge(call.call) }
                else -> Control(Icons.Rounded.SwapCalls, "Swap", false, enabled = others.any { it.phase == Phase.Holding }) {
                    haptics.play(Haptic.Segment); Calls.swap()
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        EndButton(call)
    }
}

private fun routeName(audio: CallAudioState?): String = when (audio?.route) {
    CallAudioState.ROUTE_WIRED_HEADSET -> "Headphones"
    else -> audio?.activeBluetoothDevice?.let { runCatching { it.name }.getOrNull() } ?: "Bluetooth"
}

/** a round toggle that melts into a cookie when it's on */
@Composable
private fun Control(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val alpha = if (enabled) 1f else 0.38f
    Column(Modifier.width(88.dp).graphicsLayer { this.alpha = alpha }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(72.dp)
                .pressScale(interaction, 0.9f)
                .clip(stateMorph(MaterialShapes.Circle, MaterialShapes.Cookie4Sided, checked))
                .background(if (checked) scheme.primary else scheme.surfaceContainerHigh)
                .combinedClickable(interactionSource = interaction, indication = null, enabled = enabled, onLongClick = onLongClick, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (checked) scheme.onPrimary else scheme.onSurface, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EndButton(call: CallUi) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .width(120.dp)
            .height(72.dp)
            .pressScale(interaction, 0.92f)
            .clip(CircleShape)
            .background(DeclineRed)
            .clickable(interactionSource = interaction, indication = null) { haptics.play(Haptic.Delete); Calls.hangUp(call.call) },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.CallEnd, "End call", tint = Color.White, modifier = Modifier.size(34.dp)) }
}

/** a second call: one ringing while you talk, or one waiting on hold */
@Composable
private fun OtherCall(other: CallUi, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(scheme.surfaceContainerHigh).padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(other.person?.name, other.person?.photo, other.number, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(other.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (other.phase == Phase.Ringing) other.outcome?.guess?.title ?: "Also calling" else "On hold",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
        if (other.phase == Phase.Ringing) {
            SmallRound(Icons.Rounded.CallEnd, DeclineRed) { haptics.play(Haptic.Delete); Calls.decline(other.call) }
            Spacer(Modifier.width(8.dp))
            // answering a second call puts the first on hold
            SmallRound(Icons.Rounded.Call, AnswerGreen) { haptics.play(Haptic.Success); Calls.answer(other.call) }
        } else {
            TextButton(onClick = { haptics.play(Haptic.Segment); Calls.swap() }) { Text("Swap") }
        }
    }
}

@Composable
private fun SmallRound(icon: ImageVector, color: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.size(48.dp).pressScale(interaction, 0.9f).clip(CircleShape).background(color)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun SimChooser(call: CallUi) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val telecom = context.getSystemService(TelecomManager::class.java)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        call.sims.forEachIndexed { i, sim ->
            val label = runCatching { telecom.getPhoneAccount(sim)?.label?.toString() }.getOrNull() ?: "SIM ${i + 1}"
            SheetRow(Icons.Rounded.SimCard, label, null, i, call.sims.size, onClick = { haptics.play(Haptic.Success); Calls.chooseSim(call.call, sim) })
        }
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { EndButton(call) }
    }
}

/** declining with a text; when it looks like a delivery, the replies are about the door, not a meeting */
@Composable
private fun QuickReplies(call: CallUi, guess: Guess?, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val replies = when {
        guess?.ride == true -> listOf("Coming in 2 minutes", "Please wait, I'm on my way down", "Please come to the main gate")
        guess != null -> listOf("Please leave it at the door", "Coming down in a minute", "Please hand it to the guard")
        else -> listOf("Can't talk now. Call you later?", "I'll call you back in a bit", "In a meeting. Can you text me?")
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp)) {
            Text("Decline with a message", style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.padding(start = 4.dp, bottom = 16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                replies.forEachIndexed { i, text ->
                    SheetRow(Icons.Rounded.Sms, text, null, i, replies.size, onClick = {
                        haptics.play(Haptic.Send)
                        scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss(); Calls.declineWith(call.call, text) }
                    })
                }
            }
        }
    }
}

@Composable
private fun RouteSheet(audio: CallAudioState, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val options = buildList {
        if (audio.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0) {
            add(Triple(CallAudioState.ROUTE_BLUETOOTH, Icons.Rounded.Bluetooth, audio.activeBluetoothDevice?.let { runCatching { it.name }.getOrNull() } ?: "Bluetooth"))
        }
        if (audio.supportedRouteMask and CallAudioState.ROUTE_WIRED_HEADSET != 0) add(Triple(CallAudioState.ROUTE_WIRED_HEADSET, Icons.Rounded.Headphones, "Headphones"))
        if (audio.supportedRouteMask and CallAudioState.ROUTE_EARPIECE != 0) add(Triple(CallAudioState.ROUTE_EARPIECE, Icons.Rounded.PhoneInTalk, "Phone"))
        add(Triple(CallAudioState.ROUTE_SPEAKER, Icons.AutoMirrored.Rounded.VolumeUp, "Speaker"))
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp)) {
            Text("Audio", style = MaterialTheme.typography.headlineSmallEmphasized, modifier = Modifier.padding(start = 4.dp, bottom = 16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                options.forEachIndexed { i, (route, icon, label) ->
                    SheetRow(
                        icon, label, if (audio.route == route) "In use" else null, i, options.size,
                        onClick = {
                            haptics.play(Haptic.Segment)
                            Calls.route(route)
                            scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }
                        },
                    )
                }
            }
        }
    }
}

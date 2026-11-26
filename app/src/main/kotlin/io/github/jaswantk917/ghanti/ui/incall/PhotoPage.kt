package io.github.jaswantk917.ghanti.ui.incall

import android.telecom.CallAudioState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.blur.BlurRadiusSpec
import androidx.compose.ui.graphics.blur.BlurStop
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import io.github.jaswantk917.ghanti.incall.CallUi
import io.github.jaswantk917.ghanti.incall.Calls
import io.github.jaswantk917.ghanti.incall.Phase
import io.github.jaswantk917.ghanti.ui.components.pressMorph
import io.github.jaswantk917.ghanti.ui.util.Haptic
import io.github.jaswantk917.ghanti.ui.util.LocalBlur
import io.github.jaswantk917.ghanti.ui.util.pressScale
import io.github.jaswantk917.ghanti.ui.util.rememberHaptics
import kotlin.math.min
import kotlinx.coroutines.launch

/**
 * The photo page: the contact's own photo across the top, fading into a colour taken from it, their
 * name large on the left, and the buttons in the photo's colours with one big round one on the right,
 * where the thumb is. The same calls, sheets and actions as the classic page; only the look differs.
 */
@Composable
internal fun PhotoPage(
    photo: CallPhoto,
    call: CallUi,
    others: List<CallUi>,
    audio: CallAudioState?,
    endedAt: Long?,
    locked: Boolean,
    mode: Mode,
    tones: String,
    hints: @Composable () -> Unit,
    onKeypad: (Boolean) -> Unit,
    onTone: (Char) -> Unit,
    onReplies: () -> Unit,
    onRoutes: () -> Unit,
    onAddCall: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val ended = endedAt != null
    val speaker = audio?.route == CallAudioState.ROUTE_SPEAKER && !ended
    val waiting = (call.phase == Phase.Ringing || call.phase == Phase.Dialing) && !ended

    // the page settles in: the photo eases back from a little close and the words rise into place on Material's
    // slow spatial spring (a full-screen move); both fade in on the slow effects spring, which doesn't overshoot
    val enter = remember { Animatable(0f) }
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { enter.animateTo(1f, motion.slowSpatialSpec()) }
        launch { shown.animateTo(1f, motion.slowEffectsSpec()) }
    }
    // while it rings or connects the photo breathes: a slow loop in the background, so on a timer rather than a
    // spring (springs answer a touch); once someone picks up it settles on the slow spatial spring
    val breath = remember { Animatable(1f) }
    LaunchedEffect(waiting) {
        if (waiting) {
            while (true) {
                breath.animateTo(1.035f, tween(2400, easing = FastOutSlowInEasing))
                breath.animateTo(1f, tween(2400, easing = FastOutSlowInEasing))
            }
        } else {
            breath.animateTo(1f, motion.slowSpatialSpec())
        }
    }
    // on speaker the phone is away from your face, so the photo comes forward, as the classic face does
    val zoom by animateFloatAsState(if (speaker) 1.07f else 1f, motion.slowSpatialSpec(), label = "zoom")
    // on hold the photo keeps a little colour; once the call ends it has none
    val colour by animateFloatAsState(
        when {
            ended -> 0f
            call.phase == Phase.Holding -> 0.3f
            else -> 1f
        },
        motion.slowEffectsSpec(), label = "colour",
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(scheme.surface)) {
        // the button block's width: the page less its side margins
        val width = maxWidth - 48.dp
        Layout(
            contents = listOf(
                { Hero(photo, scale = zoom * breath.value * (1.06f - 0.06f * enter.value), colour = colour, alpha = shown.value.coerceIn(0f, 1f)) },
                {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp)
                            .graphicsLayer {
                                alpha = shown.value.coerceIn(0f, 1f)
                                translationY = (1f - enter.value) * 28.dp.toPx()
                            },
                    ) {
                        Status(call, endedAt)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            call.title,
                            style = MaterialTheme.typography.displayMediumEmphasized.copy(lineHeight = 1.06.em),
                            color = scheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            // a long name gets smaller to fit two lines instead of being cut
                            autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 52.sp, stepSize = 2.sp),
                        )
                        // the number's tap target is padded, so pull it back to line up under the name
                        Secondary(call, tones, Modifier.graphicsLayer { translationX = -6.dp.toPx() })
                        Spacer(Modifier.height(14.dp))
                        // the note's row keeps its height while it rings (no note yet; "Add a note" stands 44 dp), and the buttons' area is
                        // the same height ringing, talking and ended: answering doesn't move the name
                        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) { hints() }
                        Spacer(Modifier.height(24.dp))
                        val reserve = max(sizes(width).big, LOCKED_HEIGHT)
                        AnimatedContent(
                            targetState = mode,
                            modifier = Modifier.fillMaxWidth().heightIn(min = reserve),
                            contentAlignment = Alignment.BottomStart,
                            transitionSpec = {
                                (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 4 } + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.92f)) togetherWith
                                    (fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.defaultSpatialSpec()) { it / 4 } + scaleOut(motion.defaultSpatialSpec(), targetScale = 0.92f))
                            },
                            label = "mode",
                        ) { shown ->
                            when (shown) {
                                Mode.Incoming -> PhotoIncoming(call, locked, onMessage = onReplies)
                                Mode.Controls -> PhotoControls(call, callActions(call, audio, others, { onKeypad(true) }, onRoutes, onAddCall))
                                Mode.Keypad -> KeypadArea(call, onTone, onHide = { onKeypad(false) })
                                Mode.Sim -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SimChooser(call) }
                                Mode.Ended -> Spacer(Modifier.height(1.dp))
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                },
            ),
            modifier = Modifier.fillMaxSize(),
        ) { (heroes, contents), constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val content = contents.first().measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
            val top = height - content.height
            // the photo is square at the top; when the words climb higher (a short phone, the keypad open),
            // it slides up with them so its faded edge stays behind the name instead of the name sitting on the photo
            val side = width
            val heroTop = min(0, top + OVERLAP.roundToPx() - side)
            val hero = heroes.first().measure(Constraints.fixed(width, side))
            layout(width, height) {
                hero.place(0, heroTop)
                content.place(0, top)
            }
        }
        // a second call shows over the photo, under the status bar
        AnimatedVisibility(
            others.isNotEmpty() && !ended,
            modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 12.dp),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            others.firstOrNull()?.let { OtherCall(it) }
        }
    }
}

/** how far the photo's faded edge reaches under the top of the words */
private val OVERLAP = 96.dp

/** the locked ringing block: the reply pill, its gap and the slider */
private val LOCKED_HEIGHT = 48.dp + 20.dp + 88.dp

@Composable
private fun Hero(photo: CallPhoto, scale: Float, colour: Float, alpha: Float) {
    val page = MaterialTheme.colorScheme.surface
    // where the phone can blur (Pixels, by default), the photo's lower half goes soft before it turns into the
    // page, the way the lists melt under Ghanti's header; elsewhere the colour alone fades it
    val blur = LocalBlur.current
    Box(Modifier.fillMaxSize().graphicsLayer { clip = true; this.alpha = alpha }) {
        Box(if (blur) Modifier.fillMaxSize().melt() else Modifier.fillMaxSize()) {
            Image(
                photo.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = if (colour < 1f) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(colour) }) else null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    // faces sit a little above the middle of a contact photo
                    transformOrigin = TransformOrigin(0.5f, 0.4f)
                },
            )
        }
        // the photo melts into the page; most of the change happens late, so the face stays clear. With the blur
        // doing part of the work, the colour can come in lighter, so more of the soft photo shows through
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.Transparent,
                    0.72f to page.copy(alpha = if (blur) 0.3f else 0.45f),
                    0.88f to page.copy(alpha = if (blur) 0.78f else 0.86f),
                    1f to page,
                ),
            ),
        )
        // the clock and battery stay readable over a busy photo
        Box(
            Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(page.copy(alpha = 0.6f), Color.Transparent))),
        )
    }
}

/** sharp down past the middle, then softer and softer: eased in, so the chin stays crisp and the bottom is a haze */
private fun Modifier.melt() = blur {
    blurRadiusSpec = BlurRadiusSpec.verticalGradient(
        listOf(BlurStop(0f, 0.dp), BlurStop(MELT_FROM, 0.dp)) +
            listOf(0.2f, 0.4f, 0.6f, 0.8f, 1f).map { f -> BlurStop(MELT_FROM + (1f - MELT_FROM) * f, (MELT_RADIUS * f * f * f).dp) },
    )
}

// the face sits around 40% down a contact photo and the chin near 65%: the blur starts at half and is
// still under 2 dp at the chin
private const val MELT_FROM = 0.5f
private const val MELT_RADIUS = 36f

/** the button block's sizes from its width: three columns of pills on the left, a big circle on the right */
private class Sizes(val big: Dp, val pill: Dp, val tall: Dp, val small: Dp, val gap: Dp, val column: Dp)

private fun sizes(width: Dp): Sizes {
    val gap = 10.dp
    val big = (width * 0.42f).coerceIn(128.dp, 168.dp)
    val column = width - big - SPLIT
    val pill = (column - gap * 2) / 3
    val small = 52.dp
    return Sizes(big = big, pill = pill, tall = big - small - gap, small = small, gap = gap, column = column)
}

private val SPLIT = 16.dp

@Composable
private fun PhotoControls(call: CallUi, actions: List<CallAction>) {
    val haptics = rememberHaptics()
    val byKind = actions.associateBy { it.kind }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val s = sizes(maxWidth)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(s.gap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(s.gap)) {
                    listOf(CallAction.Kind.Mute, CallAction.Kind.Speaker, CallAction.Kind.Keypad).forEach {
                        PillButton(byKind.getValue(it), s.pill, s.tall, tall = true)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(s.gap)) {
                    listOf(CallAction.Kind.Hold, CallAction.Kind.Add, CallAction.Kind.Extra).forEach {
                        PillButton(byKind.getValue(it), s.pill, s.small, tall = false)
                    }
                }
            }
            Spacer(Modifier.width(SPLIT))
            BigRound(Icons.Rounded.CallEnd, "End call", DeclineRed, s.big) { haptics.play(Haptic.Delete); Calls.hangUp(call.call) }
        }
    }
}

@Composable
private fun PhotoIncoming(call: CallUi, locked: Boolean, onMessage: () -> Unit) {
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    // unlocking with a finger mid-ring brings the buttons back
    AnimatedContent(
        locked,
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f)) togetherWith
                (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.defaultSpatialSpec(), targetScale = 0.94f))
        },
        label = "lock",
    ) { swipe ->
        if (swipe) {
            Column {
                if (call.number.isNotBlank()) {
                    WidePill(Icons.Rounded.Sms, "Reply with a message", scheme.surfaceContainerHigh, scheme.onSurface, height = 48.dp) {
                        haptics.play(Haptic.Tap); onMessage()
                    }
                    Spacer(Modifier.height(20.dp))
                }
                SwipeToAnswer(onAnswer = { Calls.answer(call.call) }, onDecline = { Calls.decline(call.call) })
            }
        } else {
            val bob = rememberInfiniteTransition(label = "bob").animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bob")
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val s = sizes(maxWidth)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(s.column), verticalArrangement = Arrangement.spacedBy(s.gap)) {
                        val message = call.number.isNotBlank()
                        WidePill(Icons.Rounded.CallEnd, "Decline", DeclineRed, Color.White, height = if (message) s.tall else s.big) {
                            haptics.play(Haptic.Delete); Calls.decline(call.call)
                        }
                        if (message) {
                            WidePill(Icons.Rounded.Sms, "Message", scheme.surfaceContainerHigh, scheme.onSurface, height = s.small) {
                                haptics.play(Haptic.Tap); onMessage()
                            }
                        }
                    }
                    Spacer(Modifier.width(SPLIT))
                    BigRound(
                        Icons.Rounded.Call, "Answer", AnswerGreen, s.big,
                        Modifier.graphicsLayer { translationY = -6.dp.toPx() * bob.value },
                    ) { haptics.play(Haptic.Success); Calls.answer(call.call) }
                }
            }
        }
    }
}

/** the big round button: end while talking, answer while it rings */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BigRound(icon: ImageVector, label: String, color: Color, size: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .pressScale(interaction, 0.94f)
            .clip(pressMorph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided, interaction))
            .background(color)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(size * 0.3f)) }
}

/** a pill as wide as the left column, with its words: decline, or a message */
@Composable
private fun WidePill(icon: ImageVector, label: String, container: Color, content: Color, height: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(min(height / 2, 36.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .pressScale(interaction, 0.95f)
            .clip(shape)
            .background(container)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A call button as a pill: round while off; on, it squares up and fills with the photo's colour, as the
 * selected button does on the music player this page follows. Icons only, as there: the app's heavy
 * type cut "Speaker" short in a pill this narrow. Each still says its name to a screen reader.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PillButton(action: CallAction, width: Dp, height: Dp, tall: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val round = min(width, height) / 2
    val corner by animateDpAsState(if (action.checked) round * 0.45f else round, MaterialTheme.motionScheme.fastSpatialSpec(), label = "corner")
    val container by animateColorAsState(if (action.checked) scheme.primary else scheme.surfaceContainerHigh, MaterialTheme.motionScheme.fastEffectsSpec(), label = "fill")
    val content by animateColorAsState(if (action.checked) scheme.onPrimary else scheme.onSurface, MaterialTheme.motionScheme.fastEffectsSpec(), label = "ink")
    val edge by animateColorAsState(
        if (tall && !action.checked) scheme.outline.copy(alpha = 0.4f) else Color.Transparent,
        MaterialTheme.motionScheme.fastEffectsSpec(), label = "edge",
    )
    val shape = RoundedCornerShape(corner)
    Box(
        Modifier
            .size(width, height)
            .graphicsLayer { alpha = if (action.enabled) 1f else 0.38f }
            .pressScale(interaction, 0.9f)
            .clip(shape)
            .background(container)
            .border(1.dp, edge, shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = action.enabled,
                role = Role.Button,
                onLongClick = action.onLongClick,
                onClick = action.onClick,
            )
            .semantics { if (action.checked) stateDescription = "On" },
        contentAlignment = Alignment.Center,
    ) {
        // only the pulsing one runs a clock, so idle buttons don't redraw every frame
        val beat = if (action.pulse) {
            rememberInfiniteTransition(label = "pulse").animateFloat(1f, 0.72f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse").value
        } else 1f
        Icon(
            action.icon,
            action.label,
            tint = content,
            modifier = Modifier.size(if (tall) 30.dp else 24.dp).graphicsLayer { scaleX = beat; scaleY = beat },
        )
    }
}

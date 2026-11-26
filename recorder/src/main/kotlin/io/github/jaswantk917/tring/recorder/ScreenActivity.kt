package io.github.jaswantk917.tring.recorder

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The call being screened, full screen and over the lock screen: what they're saying, and your say. */
class ScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            RecorderTheme {
                val live by Screening.live.collectAsState()
                LaunchedEffect(live == null, live?.stage) {
                    when (live?.stage) {
                        null, Screening.Stage.Connected -> finishAndRemoveTask()
                        Screening.Stage.Ended -> {
                            delay(2_400)
                            finishAndRemoveTask()
                        }
                        else -> Unit
                    }
                }
                live?.let { ScreenPage(it, onPickUp = Screening::pickUp, onHangUp = Screening::hangUp) }
            }
        }
    }
}

private fun title(live: Screening.Live) = when (live.stage) {
    Screening.Stage.Greeting -> "Asking who it is"
    Screening.Stage.Listening -> "Listening"
    Screening.Stage.Deciding -> "Thinking it over"
    Screening.Stage.Ringing -> "This one's for you"
    Screening.Stage.Message -> "Taking a message"
    Screening.Stage.Connected -> "Connected"
    Screening.Stage.Ended -> if (live.saved) "Message saved" else "Call ended"
}

private fun hint(live: Screening.Live) = when (live.stage) {
    Screening.Stage.Greeting -> "Your phone answered quietly. They can't hear you."
    Screening.Stage.Listening -> "Their words appear here as they speak."
    Screening.Stage.Deciding -> "Deciding if it's worth ringing you."
    Screening.Stage.Ringing -> live.reason ?: "Pick up whenever you like."
    Screening.Stage.Message -> live.reason ?: "They're leaving a message."
    Screening.Stage.Connected -> ""
    Screening.Stage.Ended -> if (live.saved) "It's in your recordings." else "Nothing was said."
}

@Composable
internal fun ScreenPage(live: Screening.Live, onPickUp: () -> Unit, onHangUp: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val ringing = live.stage == Screening.Stage.Ringing
    val over = live.stage == Screening.Stage.Ended
    val motion = MaterialTheme.motionScheme
    val glow by animateColorAsState(if (ringing) scheme.primary.copy(alpha = 0.3f) else scheme.surfaceContainerHigh, motion.slowEffectsSpec(), label = "glow")
    Surface(Modifier.fillMaxSize(), color = scheme.surface) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to glow, 0.55f to scheme.surface))) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pulse(active = !over)
                    Spacer(Modifier.width(10.dp))
                    Text(live.who, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant, maxLines = 1)
                }
                AnimatedContent(
                    title(live),
                    transitionSpec = {
                        (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 4 }) togetherWith
                            (fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.fastSpatialSpec()) { -it / 4 })
                    },
                    label = "stage",
                    modifier = Modifier.padding(top = 20.dp),
                ) { Text(it, style = MaterialTheme.typography.displaySmall, color = scheme.onSurface) }
                AnimatedContent(hint(live), transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) }, label = "hint", modifier = Modifier.padding(top = 8.dp)) {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.TopStart) {
                    if (live.words.isBlank()) {
                        if (!over) VoiceBars(Modifier.align(Alignment.Center))
                    } else {
                        Spoken(live.words)
                    }
                }
                AnimatedVisibility(!over) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val press = remember { MutableInteractionSource() }
                        val pressed by press.collectIsPressedAsState()
                        val size by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                onPickUp()
                            },
                            interactionSource = press,
                            colors = ButtonDefaults.buttonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).scale(size),
                        ) {
                            Icon(Icons.Rounded.Call, null)
                            Spacer(Modifier.width(10.dp))
                            Text("Pick up", style = MaterialTheme.typography.titleMedium)
                        }
                        FilledTonalButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onHangUp()
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        ) {
                            Icon(Icons.Rounded.CallEnd, null)
                            Spacer(Modifier.width(10.dp))
                            Text("Hang up", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

/** what they're saying, kept scrolled to the latest words */
@Composable
private fun Spoken(words: String) {
    val scroll = rememberScrollState()
    LaunchedEffect(words, scroll.maxValue) { scroll.animateScrollTo(scroll.maxValue) }
    Text(
        words,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // lines running out of the space fade instead of being cut through
                val edge = (32.dp.toPx() / size.height).coerceAtMost(0.4f)
                val top = Color.Black.copy(alpha = if (scroll.canScrollBackward) 0f else 1f)
                val bottom = Color.Black.copy(alpha = if (scroll.canScrollForward) 0f else 1f)
                drawRect(Brush.verticalGradient(0f to top, edge to Color.Black, 1f - edge to Color.Black, 1f to bottom), blendMode = BlendMode.DstIn)
            }
            .verticalScroll(scroll)
            .animateContentSize(),
    )
}

@Composable
private fun Pulse(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(1f, 0.3f, infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "alpha")
    Box(
        Modifier.size(10.dp).graphicsLayer { this.alpha = if (active) alpha else 1f }.clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}

/** a living voice meter while there are no words yet */
@Composable
private fun VoiceBars(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "voice")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1_400, easing = LinearEasing)), label = "phase")
    val color = MaterialTheme.colorScheme.primary
    Row(modifier.height(72.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(7) { bar ->
            val wave = kotlin.math.sin(2 * Math.PI * (phase + bar / 7.0)).toFloat()
            Box(
                Modifier.width(7.dp).fillMaxHeight(0.28f + 0.62f * (wave + 1f) / 2f).clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = 0.45f + 0.5f * (wave + 1f) / 2f)),
            )
        }
    }
}

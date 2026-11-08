package io.github.jaswantk917.ghanti.ui.util

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Every haptic moment in Ghanti. Each has a short composition of vibration primitives for phones
 * with a good actuator, and a [View] haptic constant to fall back on everywhere else.
 */
enum class Haptic {
    /** a light tap on a secondary control */
    Tap,
    /** a switch turned on */
    ToggleOn,
    /** a switch turned off */
    ToggleOff,
    /** a swipe crosses the line where letting go acts */
    Threshold,
    /** a long press opens a menu */
    Hold,
    /** a code copied: a bright double tick */
    Copy,
    /** a message leaves: a quick swell and a click */
    Send,
    /** a message arrives in the open chat: a soft double tap */
    Arrive,
    /** a big finish, e.g. Ghanti becomes the phone app */
    Success,
    /** something went wrong or was rejected */
    Error,
    /** gone: a low thud */
    Delete,
    /** a step while scrolling or paging */
    Segment,
}

class Haptics(private val view: View) {
    private val vibrator: Vibrator? = view.context.vibrator()

    fun play(haptic: Haptic) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && playComposition(haptic)) {
            return
        }
        view.performHapticFeedback(fallbackConstant(haptic))
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun playComposition(haptic: Haptic): Boolean {
        val vibrator = vibrator ?: return false
        // below Android 13 the vibrator ignores the user's touch feedback setting, so honor it here
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && !touchFeedbackEnabled()) {
            return true
        }
        val steps = compositionSteps(haptic) ?: return false
        if (!vibrator.areAllPrimitivesSupported(*steps.map { it.primitive }.toIntArray())) {
            return false
        }
        val effect = steps
            .fold(VibrationEffect.startComposition()) { composition, step ->
                composition.addPrimitive(step.primitive, step.scale, step.delayMs)
            }
            .compose()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            vibrator.vibrate(effect)
        }
        return true
    }

    private fun touchFeedbackEnabled(): Boolean =
        Settings.System.getInt(
            view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1
        ) != 0

    private data class Step(val primitive: Int, val scale: Float, val delayMs: Int = 0)

    @RequiresApi(Build.VERSION_CODES.R)
    private fun compositionSteps(haptic: Haptic): List<Step>? = when (haptic) {
        Haptic.Tap -> listOf(Step(Composition.PRIMITIVE_TICK, 0.5f))
        Haptic.ToggleOn -> listOf(
            Step(Composition.PRIMITIVE_QUICK_RISE, 0.35f),
            Step(Composition.PRIMITIVE_CLICK, 0.6f, 20),
        )
        Haptic.ToggleOff -> listOf(Step(Composition.PRIMITIVE_QUICK_FALL, 0.4f))
        Haptic.Threshold -> listOf(Step(Composition.PRIMITIVE_CLICK, 0.7f))
        Haptic.Hold -> listOf(
            Step(Composition.PRIMITIVE_QUICK_RISE, 0.5f),
            Step(Composition.PRIMITIVE_TICK, 0.8f, 25),
        )
        Haptic.Copy -> listOf(
            Step(Composition.PRIMITIVE_CLICK, 0.6f),
            Step(Composition.PRIMITIVE_TICK, 1f, 60),
        )
        Haptic.Send -> listOf(
            Step(Composition.PRIMITIVE_QUICK_RISE, 0.6f),
            Step(Composition.PRIMITIVE_CLICK, 0.8f, 30),
        )
        Haptic.Arrive -> listOf(
            Step(Composition.PRIMITIVE_TICK, 0.45f),
            Step(Composition.PRIMITIVE_TICK, 0.8f, 70),
        )
        Haptic.Success -> listOf(
            Step(Composition.PRIMITIVE_SLOW_RISE, 0.5f),
            Step(Composition.PRIMITIVE_CLICK, 1f, 40),
            Step(Composition.PRIMITIVE_TICK, 0.6f, 90),
        )
        Haptic.Error -> listOf(
            Step(Composition.PRIMITIVE_CLICK, 0.8f),
            Step(Composition.PRIMITIVE_CLICK, 0.8f, 90),
        )
        Haptic.Delete -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Step(Composition.PRIMITIVE_THUD, 0.7f))
        } else {
            null
        }
        Haptic.Segment -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Step(Composition.PRIMITIVE_LOW_TICK, 0.6f))
        } else {
            null
        }
    }

    private fun fallbackConstant(haptic: Haptic): Int = when (haptic) {
        Haptic.Tap, Haptic.Arrive -> HapticFeedbackConstants.CLOCK_TICK
        Haptic.ToggleOn -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CONTEXT_CLICK
        Haptic.ToggleOff -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.CLOCK_TICK
        Haptic.Threshold -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CONTEXT_CLICK
        Haptic.Hold -> HapticFeedbackConstants.LONG_PRESS
        Haptic.Copy, Haptic.Success, Haptic.Send -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        Haptic.Error -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        Haptic.Delete -> HapticFeedbackConstants.LONG_PRESS
        Haptic.Segment -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
    }
}

private fun Context.vibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }?.takeIf { it.hasVibrator() }

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

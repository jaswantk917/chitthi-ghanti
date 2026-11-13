package io.github.jaswantk917.ghanti.ui.incall

import android.graphics.Bitmap
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.ghanti.GhantiApp
import io.github.jaswantk917.ghanti.data.Person
import io.github.jaswantk917.ghanti.incall.CallUi
import io.github.jaswantk917.ghanti.incall.Phase
import io.github.jaswantk917.ghanti.screen.Outcome
import io.github.jaswantk917.ghanti.signals.Guess
import io.github.jaswantk917.ghanti.ui.theme.GhantiTheme
import io.github.jaswantk917.ghanti.ui.util.LocalBlur
import org.junit.Rule
import org.junit.Test
import java.io.File

/** The call screen on a real phone, with made-up calls; screenshots land in the debug app's files/screens. */
class InCallScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = System.currentTimeMillis()
    private val bistro = call(Phase.Active, "+918031731453", null, "Bistro", Outcome("+918031731453", now, rang = true, Guess("Bistro delivery", confident = true, brands = listOf("Bistro"))))
    private val friend = call(Phase.Holding, "+919876500001", Person("Ashish"), null, null)
    private val speaker = CallAudioState(false, CallAudioState.ROUTE_SPEAKER, CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER)

    private fun call(phase: Phase, number: String, person: Person?, networkName: String?, outcome: Outcome?) = CallUi(
        call = blankCall(), phase = phase, number = number, person = person, networkName = networkName,
        connectedAt = now - 9_000, createdAt = now - 12_000, outcome = outcome,
        canHold = true, canMerge = true, canSwap = true, isConference = false, sims = emptyList(),
    )

    // Call is final with a hidden constructor, and mockk's agent crashes on Android 17; the screen only holds it
    private fun blankCall(): Call {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, Call::class.java) as Call
    }

    private fun show(call: CallUi, others: List<CallUi>, fontScale: Float = 1f) {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GhantiTheme(amoled = false) {
                    CompositionLocalProvider(LocalBlur provides GhantiApp.instance.prefs.settings.value.blur) {
                        InCallScreen(call, others, speaker, endedAt = null, noting = false, onNoting = {}, onAddCall = {})
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(3_000)
    }

    private fun snap(name: String) {
        val dir = File(rule.activity.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun endButtonKeepsItsSizeWithACallOnHold() {
        show(bistro, listOf(friend))
        snap("on-hold")
        rule.onNodeWithContentDescription("End call").assertHeightIsAtLeast(72.dp)
    }

    @Test
    fun endButtonKeepsItsSizeWithACallOnHoldAtLargeText() {
        show(bistro, listOf(friend), fontScale = 1.3f)
        snap("on-hold-text-1.3")
        rule.onNodeWithContentDescription("End call").assertHeightIsAtLeast(72.dp)
    }

    @Test
    fun oneCallLooksAsBefore() {
        show(bistro, emptyList())
        snap("one-call")
        rule.onNodeWithContentDescription("End call").assertHeightIsAtLeast(72.dp)
    }
}

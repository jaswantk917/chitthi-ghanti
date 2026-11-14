package io.github.jaswantk917.ghanti.ui.incall

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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

    private val earpiece = CallAudioState(false, CallAudioState.ROUTE_EARPIECE, CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER)

    /** a stand-in contact photo: sky, a head and shoulders, edge to edge like a real one */
    private fun photo(): Uri {
        val bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(120, 190, 220))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.rgb(170, 30, 40); drawCircle(200f, 470f, 190f, paint)
            paint.color = Color.rgb(150, 100, 70); drawCircle(200f, 190f, 95f, paint)
            paint.color = Color.rgb(40, 30, 30); drawCircle(200f, 120f, 70f, paint)
        }
        val file = File(rule.activity.cacheDir, "face.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file)
    }

    private fun show(call: CallUi, others: List<CallUi>, fontScale: Float = 1f, audio: CallAudioState = speaker) {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GhantiTheme(amoled = false) {
                    CompositionLocalProvider(LocalBlur provides GhantiApp.instance.prefs.settings.value.blur) {
                        InCallScreen(call, others, audio, endedAt = null, noting = false, onNoting = {}, onAddCall = {})
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(3_000)
        // the photo loads off the main thread
        repeat(10) { Thread.sleep(100); rule.mainClock.advanceTimeBy(100) }
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

    @Test
    fun aPhotoFillsTheShapeOnTheEarpiece() {
        show(call(Phase.Active, "+919876500002", Person("Ravi", photo()), null, null), emptyList(), audio = earpiece)
        snap("photo-earpiece")
        rule.onNodeWithContentDescription("End call").assertHeightIsAtLeast(72.dp)
    }

    @Test
    fun aPhotoFillsTheSunburstOnSpeaker() {
        show(call(Phase.Active, "+919876500002", Person("Ravi", photo()), null, null), emptyList())
        snap("photo-speaker")
    }

    @Test
    fun aNumberWithAWaitAsksBeforeSendingTheRest() {
        show(call(Phase.Active, "+911800123456", null, null, null).copy(waitingTones = "1234"), emptyList(), audio = earpiece)
        rule.onNodeWithText("Send tones?").assertIsDisplayed()
        rule.onNodeWithText("1234").assertIsDisplayed()
        val dir = File(rule.activity.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(dir, "tones.png").outputStream().use { rule.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

package io.github.jaswantk917.ghanti.ui.incall

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry
import io.github.jaswantk917.ghanti.data.CallStyle
import io.github.jaswantk917.ghanti.data.Person
import io.github.jaswantk917.ghanti.incall.CallUi
import io.github.jaswantk917.ghanti.incall.Phase
import io.github.jaswantk917.ghanti.ui.theme.GhantiTheme
import io.github.jaswantk917.ghanti.ui.util.LocalBlur
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * The photo call page on a real phone, with made-up calls and a drawn stand-in photo; every state is
 * saved to the debug app's files/screens (whole screen, status and navigation bars included) to read.
 */
class PhotoCallScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val now = System.currentTimeMillis()
    private val routes = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER
    private val earpiece = CallAudioState(false, CallAudioState.ROUTE_EARPIECE, routes)
    private val speakerMuted = CallAudioState(true, CallAudioState.ROUTE_SPEAKER, routes)

    // Call is final with a hidden constructor; the screen only holds it
    private fun blankCall(): Call {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, Call::class.java) as Call
    }

    /** a stand-in portrait at a contact's full size: a lit wall, shoulders in a shirt, a face, hair */
    private fun portrait(name: String, top: Int, bottom: Int, side: Int = 720): Uri {
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val s = side / 720f
        Canvas(bitmap).apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = LinearGradient(0f, 0f, 0f, side.toFloat(), top, bottom, Shader.TileMode.CLAMP)
            drawRect(0f, 0f, side.toFloat(), side.toFloat(), paint)
            paint.shader = null
            paint.color = android.graphics.Color.rgb(132, 104, 82); drawOval(RectF(90 * s, 520 * s, 630 * s, 900 * s), paint)
            paint.color = android.graphics.Color.rgb(196, 146, 108); drawRect(320 * s, 400 * s, 400 * s, 560 * s, paint)
            paint.color = android.graphics.Color.rgb(206, 156, 116); drawOval(RectF(250 * s, 190 * s, 470 * s, 470 * s), paint)
            paint.color = android.graphics.Color.rgb(44, 32, 26); drawOval(RectF(236 * s, 150 * s, 484 * s, 300 * s), paint)
        }
        val file = File(rule.activity.cacheDir, "$name-$side.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file)
    }

    private fun warm() = portrait("warm", android.graphics.Color.rgb(36, 74, 82), android.graphics.Color.rgb(236, 204, 120))
    private fun blue() = portrait("blue", android.graphics.Color.rgb(40, 70, 140), android.graphics.Color.rgb(150, 190, 235))
    private fun grey() = portrait("grey", android.graphics.Color.rgb(70, 70, 72), android.graphics.Color.rgb(200, 200, 202))

    private fun call(phase: Phase, photo: Uri?, name: String = "Ravi Kumar") = CallUi(
        call = blankCall(), phase = phase, number = "+919876500002", person = Person(name, null, photo), networkName = null,
        connectedAt = if (phase == Phase.Ringing) 0 else now - 74_000, createdAt = now - 80_000, outcome = null,
        canHold = true, canMerge = false, canSwap = false, isConference = false, sims = emptyList(),
    )

    private fun show(
        call: CallUi,
        others: List<CallUi> = emptyList(),
        audio: CallAudioState = earpiece,
        endedAt: Long? = null,
        locked: Boolean = false,
        dark: Boolean = false,
        width: Dp? = null,
        style: CallStyle = CallStyle.Photo,
        blur: Boolean = false,
    ) {
        rule.runOnUiThread {
            // the phone may lock mid-run; the test screen shows over it and keeps the screen on while it's up
            rule.activity.setShowWhenLocked(true)
            rule.activity.setTurnScreenOn(true)
            rule.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            rule.activity.enableEdgeToEdge()
        }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val base = LocalConfiguration.current
            val config = if (!dark) base else Configuration(base).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                GhantiTheme(amoled = false) {
                    CompositionLocalProvider(LocalBlur provides blur) {
                        Box(if (width != null) Modifier.requiredWidth(width) else Modifier) {
                            InCallScreen(call, others, audio, endedAt, noting = false, onNoting = {}, onAddCall = {}, style = style, locked = locked)
                        }
                    }
                }
            }
        }
        // the photo is read off the main thread
        repeat(20) { Thread.sleep(40); rule.mainClock.advanceTimeBy(40) }
        // past the page settling in
        rule.mainClock.advanceTimeBy(1_200)
    }

    private fun snap(name: String) {
        rule.mainClock.advanceTimeBy(32)
        Thread.sleep(300)
        val dir = File(rule.activity.getExternalFilesDir(null), "screens").apply { mkdirs() }
        val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun insets(type: Int) = rule.activity.window.decorView.rootWindowInsets.getInsets(type)

    /** the button's whole box sits above the navigation bar */
    private fun SemanticsNodeInteraction.clearOfNavigationBar() {
        val density = rule.activity.resources.displayMetrics.density
        val rootBottom = rule.onRoot().getUnclippedBoundsInRoot().bottom
        val bar = (insets(WindowInsets.Type.navigationBars()).bottom / density).dp
        val bottom = getUnclippedBoundsInRoot().bottom
        assertTrue("bottom $bottom is under the navigation bar (root $rootBottom, bar $bar)", bottom <= rootBottom - bar)
    }

    /** the box's top is below the status bar */
    private fun SemanticsNodeInteraction.clearOfStatusBar() {
        val density = rule.activity.resources.displayMetrics.density
        val bar = (insets(WindowInsets.Type.statusBars()).top / density).dp
        val top = getUnclippedBoundsInRoot().top
        assertTrue("top $top is under the status bar ($bar)", top >= bar)
    }

    private fun notCut(text: String, lines: Int) {
        val layout = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        val l = layout.first()
        assertFalse("\"$text\" is cut: ${l.lineCount} lines", l.lineCount > lines || (0 until l.lineCount).any { l.isLineEllipsized(it) })
    }

    @Test
    fun ringingShowsTheBigAnswerOnTheRight() {
        show(call(Phase.Ringing, warm()))
        snap("photo-incoming")
        rule.onNodeWithContentDescription("Answer").assertHeightIsAtLeast(128.dp).clearOfNavigationBar()
        rule.onNodeWithText("Decline").assertIsDisplayed()
        rule.onNodeWithText("Message").assertIsDisplayed()
        rule.onNodeWithText("Incoming call").clearOfStatusBar()
        notCut("Ravi Kumar", 1)
    }

    @Test
    fun lockedItTakesASwipeAndTheHandleFollows() {
        show(call(Phase.Ringing, warm()), locked = true)
        snap("photo-incoming-locked")
        rule.onAllNodesWithContentDescription("Answer").assertCountEquals(0)
        val track = rule.onNodeWithContentDescription("Incoming call. Slide right to answer, left to decline")
        track.assertIsDisplayed().clearOfNavigationBar()
        // halfway each way: short of the mark, so letting go springs back and nothing happens
        track.performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(-width * 0.2f, 0f)) }
        rule.mainClock.advanceTimeBy(100)
        snap("photo-swipe-left")
        track.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(width * 0.4f, 0f)) }
        rule.mainClock.advanceTimeBy(100)
        snap("photo-swipe-right")
        track.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(-width * 0.2f, 0f)); up() }
        rule.mainClock.advanceTimeBy(1_000)
        track.assertIsDisplayed()
    }

    @Test
    fun talkingShowsTheButtonsInThePhotosColours() {
        show(call(Phase.Active, warm()))
        snap("photo-active")
        rule.onNodeWithContentDescription("End call").assertHeightIsAtLeast(128.dp).clearOfNavigationBar()
        listOf("Mute", "Speaker", "Keypad", "Hold", "Add call").forEach { rule.onNodeWithContentDescription(it).assertIsDisplayed().clearOfNavigationBar() }
    }

    @Test
    fun onSpeakerAndMutedThePillsSquareUp() {
        show(call(Phase.Active, warm()), audio = speakerMuted)
        snap("photo-speaker-muted")
        rule.onNodeWithContentDescription("Muted").assertIsDisplayed()
    }

    @Test
    fun onHoldThePhotoGreysOut() {
        show(call(Phase.Holding, warm()))
        snap("photo-hold")
        rule.onNodeWithText("On hold").assertIsDisplayed()
    }

    @Test
    fun theKeypadPushesThePhotoUpAndKeepsTheEndButton() {
        show(call(Phase.Active, warm()))
        rule.onNodeWithContentDescription("Keypad").performClick()
        rule.mainClock.advanceTimeBy(1_500)
        snap("photo-keypad")
        rule.onNodeWithText("Hide").assertIsDisplayed().clearOfNavigationBar()
        rule.onNodeWithContentDescription("End call").assertIsDisplayed().clearOfNavigationBar()
        notCut("Ravi Kumar", 1)
        rule.onNodeWithText("Ravi Kumar").clearOfStatusBar()
    }

    @Test
    fun endedTheColourDrainsAway() {
        show(call(Phase.Active, warm()), endedAt = now)
        snap("photo-ended")
        rule.onNodeWithText("Call ended", substring = true).assertIsDisplayed()
    }

    @Test
    fun aLongNameGetsSmallerToFitTwoLines() {
        val name = "Ramachandran Venkataraman Iyer"
        show(call(Phase.Ringing, warm(), name))
        snap("photo-long-name")
        notCut(name, 2)
        rule.onNodeWithText("Incoming call").clearOfStatusBar()
    }

    @Test
    fun aSecondCallWaitsOverThePhoto() {
        val other = call(Phase.Ringing, null, "Bistro delivery").copy(number = "+918031731453", person = null)
        show(call(Phase.Active, warm()), others = listOf(other))
        snap("photo-second-call")
        rule.onNodeWithText("Also calling").clearOfStatusBar()
        rule.onNodeWithContentDescription("End call").clearOfNavigationBar()
    }

    @Test
    fun aNarrowPhoneStillFitsEverything() {
        show(call(Phase.Active, warm()), width = 360.dp)
        snap("photo-active-360")
        rule.onNodeWithContentDescription("End call").assertHeightIsAtLeast(128.dp).clearOfNavigationBar()
        // every pill keeps a full finger's width
        listOf("Mute", "Speaker", "Keypad", "Hold", "Add call").forEach {
            rule.onNodeWithContentDescription(it).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).clearOfNavigationBar()
        }
    }

    @Test
    fun aNarrowPhoneRinging() {
        show(call(Phase.Ringing, warm()), width = 360.dp)
        snap("photo-incoming-360")
        notCut("Decline", 1)
        notCut("Message", 1)
    }

    @Test
    fun atNight() {
        show(call(Phase.Active, warm()), dark = true)
        snap("photo-active-dark")
    }

    @Test
    fun atNightRingingLocked() {
        show(call(Phase.Ringing, blue()), dark = true, locked = true)
        snap("photo-incoming-locked-dark")
    }

    @Test
    fun aBluePhoto() {
        show(call(Phase.Active, blue()), audio = speakerMuted)
        snap("photo-active-blue")
    }

    @Test
    fun aGreyPhotoGivesAQuietPage() {
        show(call(Phase.Ringing, grey()))
        snap("photo-incoming-grey")
    }

    @Test
    fun onAPixelThePhotoGoesSoftBeforeThePage() {
        show(call(Phase.Active, warm()), blur = true)
        rule.onNodeWithText("Ravi Kumar").assertIsDisplayed()
        snap("photo-blur-talking")
    }

    @Test
    fun onAPixelAtNightRinging() {
        show(call(Phase.Ringing, warm()), dark = true, blur = true)
        snap("photo-blur-night-ringing")
    }

    @Test
    fun answeringAndHangingUpDontMoveTheName() {
        val photo = warm()
        val ringing = call(Phase.Ringing, photo)
        var current by mutableStateOf(ringing)
        var ended by mutableStateOf<Long?>(null)
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            GhantiTheme(amoled = false) {
                CompositionLocalProvider(LocalBlur provides false) {
                    InCallScreen(current, emptyList(), earpiece, ended, noting = false, onNoting = {}, onAddCall = {}, style = CallStyle.Photo)
                }
            }
        }
        repeat(20) { Thread.sleep(40); rule.mainClock.advanceTimeBy(40) }
        rule.mainClock.advanceTimeBy(1_500)
        val ring = rule.onNodeWithText("Ravi Kumar").getUnclippedBoundsInRoot().top
        current = ringing.copy(phase = Phase.Active, connectedAt = now - 5_000)
        rule.mainClock.advanceTimeBy(1_500)
        snap("photo-answered")
        val talk = rule.onNodeWithText("Ravi Kumar").getUnclippedBoundsInRoot().top
        ended = now
        rule.mainClock.advanceTimeBy(1_500)
        val done = rule.onNodeWithText("Ravi Kumar").getUnclippedBoundsInRoot().top
        assertTrue("name moved on answer: $ring -> $talk", kotlin.math.abs((talk - ring).value) <= 1f)
        assertTrue("name moved on hang up: $talk -> $done", kotlin.math.abs((done - talk).value) <= 1f)
    }

    @Test
    fun aSmallPhotoStaysOnTheClassicPage() {
        show(call(Phase.Active, portrait("small", android.graphics.Color.rgb(36, 74, 82), android.graphics.Color.rgb(236, 204, 120), side = 400)))
        rule.onNodeWithContentDescription("End call").assertHeightIsEqualTo(72.dp)
    }

    @Test
    fun theClassicPageTakesASwipeWhenLockedToo() {
        show(call(Phase.Ringing, null), style = CallStyle.Classic, locked = true)
        snap("classic-incoming-locked")
        rule.onNodeWithContentDescription("Incoming call. Slide right to answer, left to decline").assertIsDisplayed().clearOfNavigationBar()
        rule.onAllNodesWithContentDescription("Answer").assertCountEquals(0)
    }

    @Test
    fun thePhotosColoursKeepTheirContrast() {
        fun ratio(a: Color, b: Color) = ColorUtils.calculateContrast(a.toArgb(), b.toArgb())
        fun check(scheme: ColorScheme, what: String) {
            assertTrue("$what text ${ratio(scheme.onSurface, scheme.surface)}", ratio(scheme.onSurface, scheme.surface) >= 7.0)
            assertTrue("$what quiet text", ratio(scheme.onSurfaceVariant, scheme.surface) >= 4.5)
            assertTrue("$what on a pill", ratio(scheme.onSurface, scheme.surfaceContainerHigh) >= 4.5)
            assertTrue("$what on a filled pill", ratio(scheme.onPrimary, scheme.primary) >= 4.5)
            // the filled pill stands out from the page it sits on
            assertTrue("$what filled pill on the page", ratio(scheme.primary, scheme.surface) >= 3.0)
        }
        for (hue in 0 until 360 step 15) for (chroma in listOf(4f, 14f, 30f, 60f, 100f)) for (dark in listOf(false, true)) {
            check(PhotoColors.scheme(PhotoColors.Seed(hue.toFloat(), chroma), dark), "hue $hue chroma $chroma dark $dark")
        }
    }
}

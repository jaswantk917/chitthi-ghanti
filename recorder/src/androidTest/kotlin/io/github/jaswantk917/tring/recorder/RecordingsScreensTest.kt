package io.github.jaswantk917.tring.recorder

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** The recordings screens at 360 dp and text size 1.3, from saved notes; screenshots land in files/screens. */
class RecordingsScreensTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val hour = 3_600_000L
    private val now = System.currentTimeMillis()
    private val calls = listOf(
        Recording(900_001, Uri.parse("content://none/1"), "Asha Didi", now - hour, 7 * 60_000L + 12_000),
        Recording(900_002, Uri.parse("content://none/2"), "+91 98765 43210", now - 3 * hour, 48_000),
        Recording(900_003, Uri.parse("content://none/3"), "Ravi from the electrician's shop, Sector 14", now - 27 * hour, 3 * 60_000L),
        Recording(900_004, Uri.parse("content://none/4"), "Mummy", now - 50 * hour, 21 * 60_000L),
    )
    private lateinit var notes: Notes

    @Before
    fun seed() {
        notes = Notes(rule.activity)
        calls.forEach { notes.forget(it.id) }
        notes.saveTranscript(
            900_001,
            listOf(
                Line(true, 0, "हाँ बोलो, कल शाम को आ रहे हो ना?"),
                Line(false, 3_000, "हाँ दीदी, छह बजे तक पहुँच जाऊँगा। मिठाई भी ले आऊँगा।"),
                Line(true, 8_000, "ठीक है, और वो बिजली का बिल भी भर देना, आखिरी तारीख परसों है। ऑनलाइन हो जाता है ना?"),
                Line(false, 15_000, "हाँ हो जाएगा, मैं आज रात ही कर दूँगा।"),
            ),
        )
        notes.saveSummary(900_001, "• Asha Didi expects you tomorrow by 6 pm; you'll bring sweets.\n• You promised to pay the electricity bill online tonight; it's due the day after tomorrow.")
        notes.saveSummary(900_004, "• Mummy asked about Diwali plans and reminded you to call Mama ji.")
        notes.saveLately(
            "• Family plans: visiting Asha Didi tomorrow at 6 pm, Diwali with Mummy.\n• Open tasks: pay the electricity bill tonight, call Mama ji.\n• An unknown number called twice about a delivery.",
            calls.first().at,
        )
    }

    private fun content(body: @Composable () -> Unit) {
        rule.runOnUiThread {
            // the phone may lock mid-run; the test screen shows over it and keeps the screen on while it's up
            rule.activity.setShowWhenLocked(true)
            rule.activity.setTurnScreenOn(true)
            rule.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            rule.activity.enableEdgeToEdge()
        }
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                RecorderTheme { Box(Modifier.requiredWidth(360.dp)) { body() } }
            }
        }
    }

    private fun app(recordings: List<Recording>, setupDone: Boolean = true, update: Release? = null, opened: Long? = null) {
        val processor = Processor(notes, Transcriber(rule.activity), Summarizer(), rule.activity.lifecycleScope)
        content {
            var open by mutableStateOf(opened)
            RecorderApp(
                recordings = recordings, setupDone = setupDone, update = update, open = open, notes = notes, processor = processor,
                onOpen = { open = it }, onSetup = {}, onUpdate = {}, onShare = {}, onDelete = {},
            )
        }
    }

    private fun snap(name: String, dialog: Boolean = false) {
        rule.waitForIdle()
        val dir = File(rule.activity.getExternalFilesDir(null), "screens").apply { mkdirs() }
        val node = if (dialog) rule.onNode(isDialog()) else rule.onRoot()
        File(dir, "$name.png").outputStream().use { node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun notCut(text: String) {
        val layout = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        val l = layout.first()
        assertFalse("$text is cut off", l.isLineEllipsized(0) || l.getLineRight(0) > l.size.width + 0.5f)
        assertEquals("$text wraps", 1, l.lineCount)
    }

    @Test
    fun theListShowsTheUpdateLatelyAndEveryCall() {
        app(calls, update = Release("1.8", "- Summaries for every call\n- Faster transcripts", null, Updates.REPO))
        rule.onNodeWithText("Version 1.8 is out").assertIsDisplayed()
        rule.onNodeWithText("Summaries for every call").assertIsDisplayed()
        rule.onNodeWithText("Lately").assertIsDisplayed()
        notCut("Your calls")
        notCut("Get it")
        snap("list-top")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Mummy"))
        rule.onNodeWithText("Mummy").assertIsDisplayed()
        snap("list-bottom")
    }

    @Test
    fun aCallShowsItsPlayerSummaryAndTranscript() {
        app(calls, opened = 900_001)
        rule.onNodeWithText("In short").assertIsDisplayed()
        rule.onNodeWithContentDescription("Play").assertIsDisplayed()
        notCut("What was said")
        snap("call-top")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("हाँ हो जाएगा", substring = true))
        rule.onNodeWithText("हाँ हो जाएगा", substring = true).assertIsDisplayed()
        snap("call-bottom")
        rule.onNodeWithContentDescription("Delete").performClick()
        rule.onNodeWithText("Delete this recording?").assertIsDisplayed()
        snap("call-delete", dialog = true)
    }

    @Test
    fun aLongNameAndNoSummaryStillFit() {
        app(calls, opened = 900_003)
        rule.onNodeWithContentDescription("Back").assertIsDisplayed()
        snap("call-long-name")
    }

    @Test
    fun beforeSetupAndBeforeAnyCall() {
        app(emptyList(), setupDone = false)
        rule.onNodeWithText("One step left").assertIsDisplayed()
        rule.onNodeWithText("Your calls will land here").assertIsDisplayed()
        notCut("Finish")
        snap("empty-setup")
    }

    @Test
    fun aScreenedCallKeepsItsActionsInReachAtEveryStage() {
        // the screen's pulse never settles, so the clock is stepped by hand
        rule.mainClock.autoAdvance = false
        var live by mutableStateOf(Screening.Live("+91 98765 43210", Screening.Stage.Greeting))
        content { ScreenPage(live, onPickUp = {}, onHangUp = {}) }
        val long = "नमस्ते मैं बैंक से बोल रहा हूँ आपके क्रेडिट कार्ड के बारे में बात करनी थी ".repeat(9).trim()
        listOf(
            live,
            live.copy(stage = Screening.Stage.Listening, words = "नमस्ते, मैं कूरियर से बोल रहा हूँ"),
            live.copy(stage = Screening.Stage.Listening, words = long),
            live.copy(stage = Screening.Stage.Deciding, words = long),
            live.copy(stage = Screening.Stage.Ringing, words = "आपका पार्सल गेट पर है", reason = "Sounds like a delivery"),
            live.copy(stage = Screening.Stage.Message, words = long, reason = "Sounds like a sales call"),
        ).forEachIndexed { i, state ->
            live = state
            rule.mainClock.advanceTimeBy(900)
            rule.onNodeWithText("Pick up").assertIsDisplayed()
            rule.onNodeWithText("Hang up").assertIsDisplayed()
            snap("screen-$i-${state.stage}")
        }
        live = live.copy(stage = Screening.Stage.Ended, saved = true)
        rule.mainClock.advanceTimeBy(900)
        rule.onNodeWithText("Message saved").assertIsDisplayed()
        snap("screen-ended")
    }

    @Test
    fun theVoicemailSwitchFits() {
        var on by mutableStateOf(true)
        content { VoicemailRow(on) { on = it } }
        rule.onNodeWithText("Take a message when you don't pick up").assertIsDisplayed()
        rule.onNode(isToggleable()).assertIsOn().performClick()
        rule.onNode(isToggleable()).assertIsOff()
        snap("settings-voicemail")
    }

    @Test
    fun settingsShowRecordingAndScreening() {
        content { SetupPage(0, onBack = {}, ask = {}) }
        rule.onNodeWithText("Recording").assertIsDisplayed()
        snap("settings-top")
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Guide and source"))
        rule.onNodeWithText("Guide and source").assertIsDisplayed()
        snap("settings-bottom")
    }
}

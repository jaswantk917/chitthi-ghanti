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
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.flow.MutableStateFlow

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
            Transcriber.Engine.Whisper,
        )
        notes.saveSummary(900_001, "• Asha Didi expects you tomorrow by 6 pm; you'll bring sweets.\n• You promised to pay the electricity bill online tonight; it's due the day after tomorrow.")
        notes.saveSummary(900_004, "• Mummy asked about Diwali plans and reminded you to call Mama ji.")
        notes.saveLately(
            "• Family plans: visiting Asha Didi tomorrow at 6 pm, Diwali with Mummy.\n• Open tasks: pay the electricity bill tonight, call Mama ji.\n• An unknown number called twice about a delivery.",
            calls.first().at,
        )
    }

    private fun content(body: @Composable () -> Unit) {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                RecorderTheme { Box(Modifier.requiredWidth(360.dp)) { body() } }
            }
        }
    }

    @After
    fun noFakeWhisper() {
        WhisperModel.file(rule.activity).delete()
        WhisperModel.refresh(rule.activity)
    }

    /** Whisper "downloaded": an empty file of its size, so nothing ever runs it */
    private fun fakeWhisper() {
        RandomAccessFile(WhisperModel.file(rule.activity), "rw").use { it.setLength(WhisperModel.SIZE) }
        WhisperModel.refresh(rule.activity)
    }

    private fun app(recordings: List<Recording>, setupDone: Boolean = true, update: Release? = null, opened: Long? = null, resting: Boolean = false) {
        val processor = Processor(notes, Transcriber(rule.activity), Summarizer(), rule.activity.lifecycleScope, MutableStateFlow(resting))
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
        notCut("Recordings")
        notCut("Download")
        snap("list-top")
        rule.onNode(hasScrollAction()).performScrollToIndex(8)
        rule.onNodeWithText("Mummy").assertIsDisplayed()
        snap("list-bottom")
    }

    @Test
    fun aCallShowsItsPlayerSummaryAndTranscript() {
        app(calls, opened = 900_001)
        rule.onNodeWithText("Summary").assertIsDisplayed()
        rule.onNodeWithContentDescription("Play").assertIsDisplayed()
        notCut("Transcript")
        snap("call-top")
        rule.onNode(hasScrollAction()).performScrollToIndex(7)
        rule.onNodeWithText("हाँ हो जाएगा, मैं आज रात ही कर दूँगा।").assertIsDisplayed()
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
    fun whisperIsOfferedBesideTheTranscript() {
        app(calls, opened = 900_003)
        rule.onNode(hasScrollAction()).performScrollToIndex(4)
        rule.onNodeWithText("Clearer transcripts").assertIsDisplayed()
        notCut("Download")
        snap("whisper-offer")
    }

    @Test
    fun theWhisperDownloadAtEachStep() {
        var state by mutableStateOf<WhisperModel.State>(WhisperModel.State.Downloading(234_000_000))
        content { WhisperCard(state, onDownload = {}, onCancel = {}) }
        rule.onNodeWithText("234 of 574 MB").assertIsDisplayed()
        notCut("Cancel")
        snap("whisper-downloading")
        rule.runOnIdle { state = WhisperModel.State.Checking }
        rule.onNodeWithText("Checking it arrived whole…").assertIsDisplayed()
        snap("whisper-checking")
        rule.runOnIdle { state = WhisperModel.State.Failed("The download stopped early. Try again to pick it up.") }
        notCut("Try again")
        snap("whisper-failed")
        rule.runOnIdle { state = WhisperModel.State.Downloading(574_000_000) }
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { state = WhisperModel.State.Ready }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Whisper is in").assertIsDisplayed()
        snap("whisper-arrived")
        // says so for a moment, then folds away
        rule.mainClock.advanceTimeBy(6_000)
        rule.onNodeWithText("Whisper is in").assertDoesNotExist()
    }

    @Test
    fun aCallWaitsForThePhoneToRestOnceWhisperIsIn() {
        fakeWhisper()
        app(calls, opened = 900_002)
        rule.onNode(hasScrollAction()).performScrollToIndex(4)
        rule.onNodeWithText("Whisper writes this while the phone charges with its screen off.").assertIsDisplayed()
        rule.onNodeWithText("Clearer transcripts").assertDoesNotExist()
        notCut("Now")
        snap("whisper-waiting")
    }

    @Test
    fun aTranscriptFromTheBuiltInModelCanBeRedone() {
        fakeWhisper()
        notes.saveTranscript(900_001, notes.transcript(900_001)!!, Transcriber.Engine.Shruti)
        app(calls, opened = 900_001)
        rule.onNode(hasScrollAction()).performScrollToIndex(3)
        rule.onNodeWithText("Redo with Whisper").assertIsDisplayed()
        notCut("Redo with Whisper")
        snap("whisper-redo")
    }

    @Test
    fun beforeSetupAndBeforeAnyCall() {
        app(emptyList(), setupDone = false)
        rule.onNodeWithText("Finish setting up").assertIsDisplayed()
        rule.onNodeWithText("No recordings yet").assertIsDisplayed()
        notCut("Set up")
        snap("empty-setup")
    }
}

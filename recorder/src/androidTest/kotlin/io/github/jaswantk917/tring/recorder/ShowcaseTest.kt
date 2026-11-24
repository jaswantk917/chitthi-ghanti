package io.github.jaswantk917.tring.recorder

import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.lifecycle.lifecycleScope
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * README screenshots of Tring++: the real screens, with made-up people named like film characters
 * and numbers that aren't anyone's. 1080 x 2160, dark, text size 1.0; they land in files/showcase.
 */
class ShowcaseTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val minute = 60_000L
    private val hour = 60 * minute
    // the same quarter-hour grid and calls as Tring's showcase, so the two sets of pictures agree
    private val clock = System.currentTimeMillis() / (15 * minute) * (15 * minute)
    private val babuLength = 6 * minute + 52_000
    // a real file, so the player knows the call's length; silence is enough for a picture
    private val babuSound by lazy { File(rule.activity.cacheDir, "showcase-babu.wav").apply { writeSilence(this, babuLength) } }
    private val calls by lazy {
        listOf(
            Recording(910_001, Uri.fromFile(babuSound), "Babu Bhaiya", clock - 3 * hour - 38 * minute + 4_000, babuLength),
            Recording(910_002, Uri.parse("content://none/2"), "Simran", clock - 5 * hour - 12 * minute, 10 * minute + 10_000),
            Recording(910_003, Uri.parse("content://none/3"), "+91 90000 41234", clock - 2 * hour - 6 * minute, 48_000),
            Recording(910_004, Uri.parse("content://none/4"), "Anjali", clock - 2 * 24 * hour - 47 * minute, 5 * minute),
        ).sortedByDescending { it.at }
    }
    private lateinit var notes: Notes

    @Before
    fun seed() {
        notes = Notes(rule.activity)
        calls.forEach { notes.forget(it.id) }
        notes.saveTranscript(
            910_001,
            listOf(
                Line(false, 0, "हाँ बोलो बाबू भैया, सुबह सुबह क्या हुआ?"),
                Line(true, 5_000, "अरे राजू, कल दुकान सात बजे खोलनी है, सेठ जी का माल आने वाला है।"),
                Line(false, 13_000, "सात बजे? ठीक है, मैं पहुँच जाऊँगा। श्याम को भी बोल दूँ?"),
                Line(true, 21_000, "हाँ, उसको बोलना ठेला लेकर आए। और किराए के पैसे साथ लाना, कल आखिरी तारीख है।"),
                Line(false, 34_000, "पैसों का इंतज़ाम हो जाएगा भैया, आप टेंशन मत लो।"),
                Line(true, 47_000, "और सुनो, फ़ोन उठाया करो। तीन बार लगाया तब जाके उठाया।"),
                Line(false, 63_000, "हाँ हाँ, अब से पहली घंटी पे उठाऊँगा।"),
                Line(true, 71_000, "चलो ठीक है, कल सुबह मिलते हैं।"),
            ),
        )
        // who spoke when, across the whole call, as the recorder splits it
        val lengths = listOf(4_200, 7_100, 6_400, 11_800, 5_300, 14_600, 3_900, 9_200)
        val turns = ArrayList<Turn>()
        var at = 0
        while (at < babuLength) {
            val length = lengths[turns.size % lengths.size]
            turns += Turn(at, minOf(at + length, babuLength.toInt()), them = turns.size % 2 == 1)
            at += length + 600
        }
        Turns.write(turns, notes.turnsFile(910_001))
        notes.saveSummary(910_001, "• Shop opens at 7 tomorrow; Seth ji's goods arrive.\n• Ask Shyam to bring the cart.\n• Bring the rent; it's due tomorrow.")
        notes.saveSummary(910_002, "• Train at 9:15 pm, platform 4.\n• Simran will wait at gate 2.\n• Don't be late.")
        notes.saveSummary(910_003, "• A courier for Munna, waiting at the gate.")
        notes.saveSummary(910_004, "• Basketball match on Sunday.\n• Anjali needs a lift.")
        notes.saveLately(
            "• Shop opens at 7 tomorrow, and the rent is due.\n• Simran's train leaves at 9:15 from platform 4.\n• Anjali's match is on Sunday.",
            calls.maxOf { it.at },
        )
    }

    @After
    fun clear() {
        calls.forEach { notes.forget(it.id) }
        babuSound.delete()
    }

    private fun writeSilence(file: File, lengthMs: Long) {
        val rate = 8_000
        val bytes = (rate * lengthMs / 1_000).toInt() * 2
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + bytes); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(bytes)
        }
        file.outputStream().use { out ->
            out.write(header.array())
            val chunk = ByteArray(64 * 1024)
            var left = bytes
            while (left > 0) { val n = minOf(left, chunk.size); out.write(chunk, 0, n); left -= n }
        }
    }

    private fun shot(body: @Composable () -> Unit) {
        rule.runOnUiThread {
            // the phone may lock mid-run; the test screen shows over it and keeps the screen on while it's up
            rule.activity.setShowWhenLocked(true)
            rule.activity.setTurnScreenOn(true)
            rule.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            rule.activity.enableEdgeToEdge()
        }
        rule.setContent {
            val density = LocalDensity.current
            val base = LocalConfiguration.current
            val night = remember(base) { Configuration(base).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES } }
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1f), LocalConfiguration provides night) {
                val size = with(LocalDensity.current) { Modifier.requiredSize(WIDTH.toDp(), HEIGHT.toDp()) }
                RecorderTheme { Box(size.testTag(SHOT)) { body() } }
            }
        }
    }

    private fun settle() {
        rule.mainClock.advanceTimeBy(2_000)
        repeat(10) { Thread.sleep(100); rule.mainClock.advanceTimeBy(100) }
    }

    private fun save(name: String) {
        val dir = File(rule.activity.getExternalFilesDir(null), "showcase").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { rule.onNodeWithTag(SHOT).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun app(opened: Long?) {
        val processor = Processor(notes, Transcriber(rule.activity), Summarizer(), rule.activity.lifecycleScope)
        shot {
            var open by remember { mutableStateOf(opened) }
            RecorderApp(
                recordings = calls, setupDone = true, update = null, open = open, notes = notes, processor = processor,
                onOpen = { open = it }, onSetup = {}, onUpdate = {}, onShare = {}, onDelete = {},
            )
        }
    }

    @Test
    fun yourCalls() {
        app(opened = null)
        settle()
        save("github-3-tringpp-calls")
    }

    @Test
    fun aCallReadBack() {
        app(opened = 910_001)
        settle()
        save("github-4-tringpp-call")
    }

    @Test
    fun aStrangerScreened() {
        // the screen's pulse never settles, so the clock is stepped by hand
        rule.mainClock.autoAdvance = false
        val who = "+91 90000 41234"
        var live by mutableStateOf(Screening.Live(who, Screening.Stage.Listening, words = "नमस्ते, मैं सर्किट बोल रहा हूँ। मुन्ना भाई का पार्सल लेकर आपके गेट पर खड़ा हूँ।"))
        shot { ScreenPage(live, onPickUp = {}, onHangUp = {}) }
        settle()
        save("github-5-tringpp-screening")
        live = live.copy(stage = Screening.Stage.Ringing, reason = "Sounds like a delivery")
        settle()
        save("github-6-tringpp-screening-rings")
    }

    private companion object {
        const val SHOT = "shot"
        const val WIDTH = 1080
        const val HEIGHT = 2160
    }
}

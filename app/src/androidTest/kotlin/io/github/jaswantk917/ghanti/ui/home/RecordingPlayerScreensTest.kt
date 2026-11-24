package io.github.jaswantk917.ghanti.ui.home

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.ghanti.data.CallRecording
import io.github.jaswantk917.ghanti.ui.theme.GhantiTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** A recording under its call in Recents at 360 dp and text size 1.3; screenshots land in the debug app's files/screens. */
class RecordingPlayerScreensTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(recording: CallRecording) {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                GhantiTheme(amoled = false) {
                    Box(Modifier.requiredWidth(360.dp)) { RecordingPlayer(recording, rememberRecentsPlayback()) }
                }
            }
        }
    }

    private fun snap(name: String) {
        rule.waitForIdle()
        val dir = File(rule.activity.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun aLongTranscriptShowsFourLinesAndOpensTheRest() {
        val excerpt = "… बिजली का बिल आखिरी तारीख परसों है ऑनलाइन हो जाता है ना हाँ हो जाएगा मैं आज रात ही कर दूँगा और मिठाई भी ले आऊँगा शाम को छह बजे तक पहुँच जाऊँगा फिर बैठकर बात करेंगे घर के काम की और दीवाली की तैयारी की …"
        show(CallRecording(1, 0, 180_000, excerpt, more = true))
        val layout = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(excerpt).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        assertTrue("shows ${layout.first().lineCount} lines", layout.first().lineCount <= 4)
        rule.onNodeWithText("Read the whole call").assertIsDisplayed()
        snap("peek-long")
    }

    @Test
    fun aShortTranscriptHasNothingToOpen() {
        show(CallRecording(2, 0, 12_000, "हाँ बोलो, पहुँच गया", more = false))
        rule.onNodeWithText("हाँ बोलो, पहुँच गया").assertIsDisplayed()
        rule.onNodeWithText("Read the whole call").assertDoesNotExist()
        snap("peek-short")
    }

    @Test
    fun noTranscriptYetStillOffersIt() {
        show(CallRecording(3, 0, 40_000))
        rule.onNodeWithText("Transcript").assertIsDisplayed()
        snap("peek-none")
    }
}

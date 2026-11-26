package io.github.jaswantk917.ghanti.ui.home

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.jaswantk917.ghanti.data.AutoRecord
import io.github.jaswantk917.ghanti.data.CallStyle
import io.github.jaswantk917.ghanti.data.Settings
import io.github.jaswantk917.ghanti.ui.theme.GhantiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Settings: the stranger options fold into "Silence strangers", and picking a choice doesn't push the rows under it */
class SettingsSheetTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var settings by mutableStateOf(Settings(callStyle = CallStyle.Classic))

    private fun show() {
        rule.runOnUiThread {
            rule.activity.setShowWhenLocked(true)
            rule.activity.setTurnScreenOn(true)
            rule.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            rule.activity.enableEdgeToEdge()
        }
        rule.setContent {
            GhantiTheme(amoled = false) {
                SettingsSheet(
                    settings = settings,
                    setup = Setup(isPhoneApp = true, screensCalls = true, readsNotifications = true, silencing = true),
                    onChange = { change -> settings = change(settings) },
                    onMakeDefault = {}, onAllowNotifications = {}, onOpenBlocked = {}, onDismiss = {},
                    hasRecorder = true,
                )
            }
        }
        rule.waitForIdle()
    }

    private fun snap(name: String) {
        rule.waitForIdle()
        Thread.sleep(200)
        val dir = File(rule.activity.getExternalFilesDir(null), "screens").apply { mkdirs() }
        val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun top(text: String) = rule.onNodeWithText(text).getUnclippedBoundsInRoot().top

    @Test
    fun strangerOptionsFoldIntoSilenceStrangers() {
        show()
        rule.onNodeWithText("Let delivery calls ring").assertIsDisplayed()
        rule.onNodeWithText("Ring if they call again").assertIsDisplayed()
        snap("settings-strangers-on")
        settings = settings.copy(screen = settings.screen.copy(silenceUnknown = false))
        rule.waitForIdle()
        rule.onNodeWithText("Let delivery calls ring").assertDoesNotExist()
        rule.onNodeWithText("Ring if they call again").assertDoesNotExist()
        snap("settings-strangers-off")
        settings = settings.copy(screen = settings.screen.copy(silenceUnknown = true))
        rule.waitForIdle()
        rule.onNodeWithText("Let delivery calls ring").assertIsDisplayed()
    }

    @Test
    fun pickingACallScreenDoesntMoveTheRowsBelow() {
        show()
        // the row sits below the sheet's first screen
        rule.onNodeWithText("Photo").performScrollTo()
        rule.waitForIdle()
        val before = top("Colours")
        rule.onNodeWithText("Photo").performClick()
        rule.waitForIdle()
        assertEquals(CallStyle.Photo, settings.callStyle)
        assertEquals("Colours moved", before.value, top("Colours").value, 0.5f)
        snap("settings-call-screen-photo")
        rule.onNodeWithText("Classic").performClick()
        rule.waitForIdle()
        assertEquals(before.value, top("Colours").value, 0.5f)
    }

    @Test
    fun pickingHowCallsRecordDoesntMoveTheRowsBelow() {
        show()
        rule.onNodeWithText("Every call").performScrollTo()
        rule.waitForIdle()
        val before = top("Blocked")
        listOf("People I pick" to AutoRecord.Chosen, "Strangers" to AutoRecord.Strangers, "Every call" to AutoRecord.Everyone, "Off" to AutoRecord.Off).forEach { (label, mode) ->
            rule.onNodeWithText(label).performClick()
            rule.waitForIdle()
            assertEquals(mode, settings.autoRecord)
            assertEquals("Blocked moved after $label", before.value, top("Blocked").value, 0.5f)
        }
    }

    @Test
    fun theBlurSettingSaysWhatItCovers() {
        show()
        // the line under the title is the row's lowest part: scrolled to, the whole row is on screen
        rule.onNodeWithText("Lists melt into the edges", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Use blur").assertIsDisplayed()
        snap("settings-use-blur")
    }

    @Test
    fun theColourPillSpringsAcross() {
        show()
        rule.onNodeWithText("Wallpaper").performScrollTo()
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Wallpaper").performClick()
        // mid-flight: the pill stretched between the two
        rule.mainClock.advanceTimeBy(80)
        snap("settings-colours-spring")
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertTrue(settings.palette.name != "Ghanti")
        snap("settings-colours-wallpaper")
    }
}

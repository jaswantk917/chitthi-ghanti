package io.github.jaswantk917.ghanti.ui.home

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.jaswantk917.ghanti.data.HomeState
import io.github.jaswantk917.ghanti.data.Prefs
import io.github.jaswantk917.ghanti.signals.StrangersRing
import io.github.jaswantk917.ghanti.ui.theme.GhantiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.io.File

/** "Expecting a new number?" at 360 dp: one tap lets strangers ring, the status line says until when, one tap stops it */
class NewNumberScreensTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val hour = 60 * 60_000L

    private fun prepare() = rule.runOnUiThread {
        // the phone may lock mid-run; the test screen shows over it and keeps the screen on while it's up
        rule.activity.setShowWhenLocked(true)
        rule.activity.setTurnScreenOn(true)
        rule.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        rule.activity.enableEdgeToEdge()
    }

    private fun snap(name: String) {
        rule.waitForIdle()
        val dir = File(rule.activity.getExternalFilesDir(null), "screens").apply { mkdirs() }
        // the whole screen: a bottom sheet draws in a window of its own
        val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(dir, "$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun notCut(text: String) {
        val layout = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        val l = layout.first()
        // the line's own width against the box it got: the paragraph's width is only the room it was offered
        val line = l.getLineRight(0) - l.getLineLeft(0)
        assertFalse("\"$text\" is cut: line=$line box=${l.size.width} lines=${l.lineCount}", l.lineCount != 1 || l.isLineEllipsized(0) || line > l.size.width + 1)
    }

    @Test
    fun oneTapLetsStrangersRingAndTheSameTapStops() {
        prepare()
        var strangers by mutableStateOf(Prefs.StrangersRing())
        var picked: Int? = -1
        rule.setContent {
            GhantiTheme(amoled = false) {
                Box(Modifier.requiredWidth(360.dp)) {
                    ExpectingSheet(
                        expecting = emptyList(),
                        setup = Setup(isPhoneApp = true, screensCalls = true, readsNotifications = true, silencing = true),
                        strangers = strangers,
                        onStrangersRing = { hours ->
                            picked = hours
                            strangers = if (hours == null) Prefs.StrangersRing() else Prefs.StrangersRing(System.currentTimeMillis() + hours * hour, hours)
                        },
                        onAllowNotifications = {},
                        onClear = {},
                        onDismiss = {},
                    )
                }
            }
        }
        rule.onNodeWithText("Let strangers ring out loud for a while.").assertIsDisplayed()
        snap("new-number-off")
        StrangersRing.choices.forEach { (_, label) -> notCut(label) }
        snap("new-number-off")

        rule.onNodeWithText("4 hr").performClick()
        rule.waitForIdle()
        assertEquals(4, picked)
        rule.onNodeWithText("Strangers ring out loud ${StrangersRing.until(strangers.until, System.currentTimeMillis())}.").assertIsDisplayed()
        rule.onNodeWithText("Stop, silence strangers").assertIsDisplayed()
        snap("new-number-on")

        rule.onNodeWithText("4 hr").performClick()
        rule.waitForIdle()
        assertEquals("the same tap stops it", null, picked)
        rule.onNodeWithText("Let strangers ring out loud for a while.").assertIsDisplayed()

        rule.onNodeWithText("8 hr").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Stop, silence strangers").performClick()
        rule.waitForIdle()
        assertEquals(null, picked)
    }

    @Test
    fun theStatusLineSaysUntilWhen() {
        prepare()
        val now = System.currentTimeMillis()
        var until by mutableStateOf(now + 4 * hour)
        rule.setContent {
            GhantiTheme(amoled = false) {
                Box(Modifier.requiredWidth(360.dp)) {
                    HomeScreen(
                        HomeState(loaded = true), Setup(isPhoneApp = true, screensCalls = true, readsNotifications = true, silencing = true),
                        emptyList(), emptyMap(), NoActions, strangersRingUntil = until,
                    )
                }
            }
        }
        // today, tomorrow, a weekday: each fits beside the settings button
        listOf(4 * hour, 24 * hour, 48 * hour).forEachIndexed { i, length ->
            until = now + length
            rule.waitForIdle()
            rule.mainClock.advanceTimeBy(1_000)
            if (i == 0) snap("new-number-status")
            notCut(StrangersRing.status(until, System.currentTimeMillis()))
        }
    }

    private object NoActions : HomeActions {
        override fun call(number: String) {}
        override fun message(number: String) {}
        override fun copy(number: String) {}
        override fun block(number: String) {}
        override fun addContact(number: String) {}
        override fun openContact(id: Long) {}
        override fun setUsual(contactId: Long, number: String?) {}
        override fun setRecord(numbers: List<String>, on: Boolean) {}
        override fun saveMemo(number: String, at: Long, label: String?, note: String) {}
        override fun delete(row: io.github.jaswantk917.ghanti.data.RecentRow) {}
        override fun keypad() {}
        override fun settings() {}
        override fun expecting() {}
        override fun makeDefault() {}
        override fun allowNotifications() {}
        override fun allowFullScreen() {}
        override fun recentsSeen() {}
    }
}

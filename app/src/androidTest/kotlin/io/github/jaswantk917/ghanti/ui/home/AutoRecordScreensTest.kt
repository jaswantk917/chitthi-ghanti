package io.github.jaswantk917.ghanti.ui.home

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.ghanti.data.AutoRecord
import io.github.jaswantk917.ghanti.data.Contact
import io.github.jaswantk917.ghanti.data.HomeState
import io.github.jaswantk917.ghanti.data.PhoneNumber
import io.github.jaswantk917.ghanti.data.RecentRow
import io.github.jaswantk917.ghanti.data.Settings
import io.github.jaswantk917.ghanti.ui.theme.GhantiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Auto-record's two screens at 360 dp and text size 1.3; screenshots land in the debug app's files/screens. */
class AutoRecordScreensTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun content(body: @androidx.compose.runtime.Composable () -> Unit) {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                GhantiTheme(amoled = false) { Box(Modifier.requiredWidth(360.dp)) { body() } }
            }
        }
    }

    private fun snap(name: String, dialog: Boolean = false) {
        val dir = File(rule.activity.getExternalFilesDir(null), "screens").apply { mkdirs() }
        val node = if (dialog) rule.onNode(isDialog()) else rule.onRoot()
        File(dir, "$name.png").outputStream().use { node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun notCut(text: String) {
        val layout = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        // the line's own right edge against the box: didOverflowWidth compares with the widest allowed, not the text
        val l = layout.first()
        assertFalse("$text is cut off", l.isLineEllipsized(0) || l.getLineRight(0) > l.size.width + 0.5f)
        assertEquals("$text wraps", 1, l.lineCount)
    }

    @Test
    fun theFourChoicesFitAndSwitch() {
        var settings by mutableStateOf(Settings())
        content {
            SettingsSheet(settings, Setup(true, true, true, false), onChange = { settings = it(settings) }, onMakeDefault = {}, onAllowNotifications = {}, onOpenBlocked = {}, onDismiss = {}, hasRecorder = true)
        }
        rule.onNodeWithText("Every call").performScrollTo().assertIsDisplayed()
        listOf("Off", "People I pick", "Strangers", "Every call").forEach(::notCut)
        rule.onNodeWithText("People I pick").performClick()
        rule.waitForIdle()
        assertEquals(AutoRecord.Chosen, settings.autoRecord)
        rule.onNodeWithText("Open someone in Contacts and switch on Record calls").assertIsDisplayed()
        snap("auto-record-settings", dialog = true)
    }

    @Test
    fun withoutTheRecorderSettingsSayNothingOfIt() {
        content { SettingsSheet(Settings(), Setup(true, true, true, false), onChange = {}, onMakeDefault = {}, onAllowNotifications = {}, onOpenBlocked = {}, onDismiss = {}, hasRecorder = false) }
        rule.onNodeWithText("Everything is decided", substring = true).performScrollTo().assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("record", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("GitHub", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun aContactCanBeSwitchedOn() {
        val contact = Contact(1, "Ashish Kumar Venkataraman", null, false, listOf(PhoneNumber("+91 98765 43210", "Mobile")))
        var recording by mutableStateOf(setOf<String>())
        content {
            HomeScreen(HomeState(loaded = true, contacts = listOf(contact)), Setup(true, true, true, false), emptyList(), emptyMap(), actions(onRecord = { on -> recording = if (on) setOf("9876543210") else emptySet() }), recording)
        }
        rule.onNodeWithText("Contacts").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Ashish Kumar Venkataraman").performClick()
        rule.waitForIdle()
        notCut("Record calls")
        rule.onNode(isSwitch).performClick()
        rule.waitForIdle()
        assertEquals(setOf("9876543210"), recording)
        rule.onNode(isSwitch).assertIsOn()
        snap("auto-record-contact")
    }

    private val isSwitch = isToggleable() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    private fun actions(onRecord: (Boolean) -> Unit) = object : HomeActions {
        override fun call(number: String) {}
        override fun message(number: String) {}
        override fun copy(number: String) {}
        override fun block(number: String) {}
        override fun addContact(number: String) {}
        override fun openContact(id: Long) {}
        override fun setUsual(contactId: Long, number: String?) {}
        override fun setRecord(numbers: List<String>, on: Boolean) = onRecord(on)
        override fun saveMemo(number: String, at: Long, label: String?, note: String) {}
        override fun delete(row: RecentRow) {}
        override fun keypad() {}
        override fun settings() {}
        override fun expecting() {}
        override fun makeDefault() {}
        override fun allowNotifications() {}
        override fun allowFullScreen() {}
        override fun recentsSeen() {}
    }
}

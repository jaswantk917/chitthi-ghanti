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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.ghanti.data.CallEntry
import io.github.jaswantk917.ghanti.data.Contact
import io.github.jaswantk917.ghanti.data.HomeState
import io.github.jaswantk917.ghanti.data.Kind
import io.github.jaswantk917.ghanti.data.PhoneNumber
import io.github.jaswantk917.ghanti.data.RecentRow
import io.github.jaswantk917.ghanti.data.Recents
import io.github.jaswantk917.ghanti.ui.theme.GhantiTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import io.github.jaswantk917.ghanti.screen.Outcome
import io.github.jaswantk917.ghanti.signals.Guess
import io.github.jaswantk917.ghanti.ui.util.clockTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.ZoneId

/** The faces above Recents at 360 dp: never someone already on screen, and steady while you scroll. */
class RecentsFacesTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.systemDefault()
    private val now = System.currentTimeMillis()
    private val hour = 60 * 60_000L
    private val day = 24 * hour
    private var nextId = 1L

    private fun person(id: Long, name: String, number: String, starred: Boolean = false) = Contact(id, name, null, starred, listOf(PhoneNumber(number, "Mobile")))
    private fun call(number: String, name: String, at: Long) = CallEntry(nextId++, number, name, Kind.Outgoing, at, 90, false)

    private val mummy = person(1, "Mummy", "9000000001")
    private val asha = person(2, "Asha Didi", "9000000002", starred = true)
    private val ravi = person(3, "Ravi", "9000000003")
    private val kavya = person(4, "Kavya", "9000000004", starred = true)
    private val others = listOf("Neha", "Arjun", "Kabir", "Meera", "Rohan").mapIndexed { i, name -> person(10L + i, name, "90000001${i}0") }
    private val calls = listOf(0L, 1L, 2L, 3L).map { call(mummy.numbers[0].number, "Mummy", now - 20 * 60_000L - it * day) } +
        others.mapIndexed { i, c -> call(c.numbers[0].number, c.name, now - (i + 1) * hour) } +
        call(ravi.numbers[0].number, "Ravi", now - 9 * day)
    private val recents = Recents.group(calls.sortedByDescending { it.date }, zone).map { RecentRow(it, it.latest.cachedName, null, null, null) }

    private val isFace = SemanticsMatcher("a face above Recents") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Call" }

    // each face's text is its avatar's letter, then the name
    private fun faces() = rule.onAllNodes(isFace).fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].last().text }

    private fun show(fontScale: Float, rows: List<RecentRow> = recents) {
        rule.runOnUiThread {
            // the phone may lock mid-run; the test screen shows over it and keeps the screen on while it's up
            rule.activity.setShowWhenLocked(true)
            rule.activity.setTurnScreenOn(true)
            rule.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            rule.activity.enableEdgeToEdge()
        }
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GhantiTheme(amoled = false) {
                    Box(Modifier.requiredWidth(360.dp)) {
                        HomeScreen(HomeState(loaded = true, recents = rows, contacts = listOf(mummy, asha, ravi) + others + kavya), Setup(true, true, true, false), emptyList(), emptyMap(), NoActions)
                    }
                }
            }
        }
    }

    private fun settle() {
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
    }

    /** where the top row of Recents sits, not counting a face with the same name */
    private fun topRow() = rule.onAllNodes(hasText("Mummy") and !isFace).fetchSemanticsNodes().minOf { it.boundsInRoot.top }

    private fun snap(name: String) {
        val dir = File(rule.activity.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun check(fontScale: Float, name: String) {
        rule.mainClock.autoAdvance = false
        show(fontScale)
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val before = topRow()
        settle()
        assertEquals("the list doesn't move when the faces arrive", before, topRow(), 1f)
        rule.mainClock.autoAdvance = true
        val first = faces()
        snap(name)
        assertEquals("three faces or none: $first", 3, first.size)
        assertTrue("a favourite you haven't called is offered: $first", "Asha Didi" in first)
        assertTrue("someone far down the list is offered: $first", "Ravi" in first)
        assertFalse("the top row is never offered again: $first", "Mummy" in first)
        assertFalse("a row whose name shows, even behind the keypad button, isn't offered: $first", "Rohan" in first)
        // the list itself, not a finger: a test swipe resumes the header's sync of the hidden Contacts list mid-layout
        val listId = rule.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyDescendant(hasText("Today"))).fetchSemanticsNode().id
        val recentsList = rule.onNode(SemanticsMatcher("the Recents list") { it.id == listId })
        recentsList.performScrollToIndex(12)
        rule.waitForIdle()
        recentsList.performScrollToIndex(0)
        rule.waitForIdle()
        settle()
        assertEquals("the faces stay put through a scroll", first, faces())
        rule.onAllNodes(hasText("Neha") and !isFace).onFirst().performClick()
        settle()
        assertEquals("opening a row pushes others down but keeps the faces", first, faces())
    }

    @Test
    fun aLongGuessGivesWayToTheTime() {
        val rider = "+91 90000 77123"
        val at = now - 23 * 60_000L
        val guess = Outcome(rider, at, rang = true, Guess("Probably Bistro or Swiggy delivery", brands = listOf("Bistro", "Swiggy")))
        val rows = Recents.group(listOf(CallEntry(nextId++, rider, null, Kind.Incoming, at, 34, false)), zone).map { RecentRow(it, null, null, null, guess) }
        show(1.3f, rows)
        val time = " · ${clockTime(at)}"
        val layout = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(time).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        assertFalse("the time is cut", layout.first().isLineEllipsized(0) || layout.first().lineCount != 1)
        snap("recents-long-guess-1.3")
    }

    /** rows going up under the header, on both pages, as a thumb scrolls them: the title tucks away, the tabs stay */
    @Test
    fun rowsPassUnderTheHeader() {
        show(1.0f)
        settle()
        val before = rule.onAllNodesWithText("Ghanti").fetchSemanticsNodes().single().boundsInRoot
        fun page(name: String) {
            val listId = rule.onAllNodes(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .fetchSemanticsNodes().filter { it.boundsInRoot.left >= 0f && it.boundsInRoot.left < 10f }.maxBy { it.boundsInRoot.height }.id
            rule.onNode(SemanticsMatcher("the list on screen") { it.id == listId }).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 700f) }
            settle()
            snap(name)
            val title = rule.onAllNodesWithText("Ghanti").fetchSemanticsNodes().single().boundsInRoot
            assertTrue("the title tucked up by its own height: $before → $title", title.bottom <= before.top + 1f)
            rule.onAllNodes(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .fetchSemanticsNodes().first { it.id == listId }.let { list ->
                    assertTrue("rows went up under the tabs", list.config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
                }
        }
        page("header-over-recents")
        rule.onNodeWithText("Contacts").performClick()
        settle()
        page("header-over-contacts")
    }

    @Test
    fun atTextSize13() = check(1.3f, "recents-faces-1.3")

    @Test
    fun atTextSize10() = check(1.0f, "recents-faces-1.0")

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

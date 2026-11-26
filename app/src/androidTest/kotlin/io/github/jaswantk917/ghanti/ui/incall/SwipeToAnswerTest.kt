package io.github.jaswantk917.ghanti.ui.incall

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import io.github.jaswantk917.ghanti.ui.theme.GhantiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The locked phone's answer slider: all the way right answers, all the way left declines, short of that nothing. */
class SwipeToAnswerTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var answered = 0
    private var declined = 0
    private val track get() = rule.onNodeWithContentDescription("Incoming call. Slide right to answer, left to decline")

    private fun show() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            GhantiTheme(amoled = false) {
                Box(Modifier.width(380.dp).padding(16.dp)) {
                    SwipeToAnswer(onAnswer = { answered++ }, onDecline = { declined++ })
                }
            }
        }
        rule.mainClock.advanceTimeBy(500)
    }

    private fun slide(share: Float) {
        track.performTouchInput { swipe(center, Offset(centerX + (width / 2f) * share, centerY), durationMillis = 400) }
        rule.mainClock.advanceTimeBy(800)
    }

    @Test
    fun allTheWayRightAnswers() {
        show()
        slide(1f)
        assertEquals(1, answered)
        assertEquals(0, declined)
    }

    @Test
    fun allTheWayLeftDeclines() {
        show()
        slide(-1f)
        assertEquals(0, answered)
        assertEquals(1, declined)
    }

    @Test
    fun shortOfTheEndNothingHappens() {
        show()
        slide(0.35f)
        slide(-0.35f)
        assertEquals(0, answered + declined)
    }

    @Test
    fun onceAnsweredItStaysAnswered() {
        show()
        slide(1f)
        slide(-1f)
        assertEquals(1, answered)
        assertEquals(0, declined)
    }

    @Test
    fun aScreenReaderCanAnswerOrDecline() {
        show()
        val actions = track.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Answer", "Decline"), actions.map { it.label })
        actions.first { it.label == "Answer" }.action()
        assertEquals(1, answered)
    }
}

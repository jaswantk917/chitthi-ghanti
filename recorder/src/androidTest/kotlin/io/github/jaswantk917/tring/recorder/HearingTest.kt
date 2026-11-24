package io.github.jaswantk917.tring.recorder

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** A caller who pauses keeps what they said before it: the phone's recognizer starts afresh after one. */
class HearingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun wordsBeforeAPauseStay() = runBlocking {
        val sound = Pcm.bytes(Pcm.silence(500) + TestSpeech.say(context, TestSpeech.FIRST) + Pcm.silence(2_500) + TestSpeech.say(context, TestSpeech.SECOND) + Pcm.silence(3_000))
        val heard = mutableListOf<String>()
        val hearing = Hearing(context) { synchronized(heard) { heard += it } }
        instrumentation.runOnMainSync { hearing.start() }
        TestSpeech.feed(sound) { hearing.feed(it, it.size) }
        Thread.sleep(2_000)
        hearing.stop()
        val last = synchronized(heard) { heard.lastOrNull().orEmpty() }
        Log.i(TAG, "heard: ${heard.joinToString(" | ")}")
        assertTrue("last: $last", TestSpeech.FIRST_WORDS.any { it in last } && TestSpeech.SECOND_WORDS.any { it in last })
    }

    private companion object {
        const val TAG = "HearingTest"
    }
}
